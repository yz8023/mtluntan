package io.mtluntan.app.util

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * 密码托管：Android KeyStore 的 AES/GCM-256 加密。
 *
 * 与 Java 参考版 `util/CryptoUtils.java` 保持一致的三条原则：
 *  1. 密钥不出 TEE（安卓 9+ 部分机型支持 StrongBox，这里只用普通 TEE 保证兼容）
 *  2. 密文格式 `gcm:<base64(iv)><base64(ct)>`，解密失败一律返回空串，调用方按「无密码」处理
 *  3. 个别定制 ROM KeyStore 不可用时降级为 `ob:` 混淆存储（只防肉眼，不防逆向），
 *     但绝不静默丢失账号——宁可降级也要让自动重登能用
 */
object KeystoreCrypto {

    private const val TAG = "KeystoreCrypto"
    private const val KEYSTORE = "AndroidKeyStore"
    private const val ALIAS = "mtluntan_account_pwd"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_TAG_BITS = 128
    private const val PREFIX_GCM = "gcm:"
    private const val PREFIX_OBF = "ob:"

    fun encrypt(plain: String): String {
        if (plain.isEmpty()) return ""
        return try {
            val key = obtainKey() ?: return obfuscate(plain)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, key)
            val ct = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
            PREFIX_GCM + Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + ":" +
                Base64.encodeToString(ct, Base64.NO_WRAP)
        } catch (t: Throwable) {
            Log.w(TAG, "encrypt failed, degrade: ${t.message}")
            obfuscate(plain)
        }
    }

    fun decrypt(stored: String?): String {
        if (stored.isNullOrEmpty()) return ""
        if (stored.startsWith(PREFIX_OBF)) return deobfuscate(stored)
        if (!stored.startsWith(PREFIX_GCM)) return ""
        return try {
            val body = stored.removePrefix(PREFIX_GCM)
            val parts = body.split(":")
            if (parts.size != 2) return ""
            val iv = Base64.decode(parts[0], Base64.NO_WRAP)
            val ct = Base64.decode(parts[1], Base64.NO_WRAP)
            val key = obtainKey(create = false) ?: return ""
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
            String(cipher.doFinal(ct), Charsets.UTF_8)
        } catch (t: Throwable) {
            // 换机 / 清 KeyStore / 密文损坏：按「没有密码」处理，不抛异常打断签到流程
            Log.w(TAG, "decrypt failed: ${t.message}")
            ""
        }
    }

    fun isAvailable(): Boolean = try {
        obtainKey() != null
    } catch (t: Throwable) {
        false
    }

    private fun obtainKey(create: Boolean = true): SecretKey? {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (keyStore.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        if (!create) return null
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                // 不要求用户认证：后台自动签到必须能在锁屏状态下解密
                .setUserAuthenticationRequired(false)
                .build()
        )
        return generator.generateKey()
    }

    // ---- KeyStore 不可用时的兜底：固定盐 XOR + base64（仅防肉眼） ----

    private val SALT = "mtluntan-local-fallback-2026".toByteArray(Charsets.UTF_8)

    private fun obfuscate(plain: String): String {
        val src = plain.toByteArray(Charsets.UTF_8)
        val out = ByteArray(src.size)
        for (i in src.indices) out[i] = (src[i].toInt() xor SALT[i % SALT.size].toInt()).toByte()
        return PREFIX_OBF + Base64.encodeToString(out, Base64.NO_WRAP)
    }

    private fun deobfuscate(stored: String): String = try {
        val src = Base64.decode(stored.removePrefix(PREFIX_OBF), Base64.NO_WRAP)
        val out = ByteArray(src.size)
        for (i in src.indices) out[i] = (src[i].toInt() xor SALT[i % SALT.size].toInt()).toByte()
        String(out, Charsets.UTF_8)
    } catch (t: Throwable) {
        ""
    }
}
