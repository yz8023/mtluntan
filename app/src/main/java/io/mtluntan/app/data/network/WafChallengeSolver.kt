package io.mtluntan.app.data.network

import android.util.Log
import org.mozilla.javascript.BaseFunction
import org.mozilla.javascript.Context
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.ScriptableObject
import org.mozilla.javascript.Undefined

/**
 * Solves the anti-bot JavaScript challenge ("acw_sc__v2") that the site's CDN
 * (Alibaba ESA) serves in front of every dynamic page.
 *
 * A normal browser automatically executes this script and reloads; a native
 * HTTP stack does not. Instead of reverse-engineering the algorithm we run the
 * exact script the server sent, inside an embedded JS engine (Rhino), capture
 * the `acw_sc__v2` cookie it computes and reuse it for the request. This is
 * exactly what a browser does and it keeps working if the script changes.
 *
 * The whole computation is cheap and runs on a background thread (IO dispatcher)
 * from [WafInterceptor], so the UI is never blocked — the "verification" happens
 * invisibly in the background.
 */
object WafChallengeSolver {

    private const val TAG = "WafSolver"
    private const val ARG1_RE = "var\\s+arg1\\s*=\\s*'[0-9A-Fa-f]{40}'"
    private const val COOKIE_LINE =
        "document[L(0x121)]='acw_sc__v2='+v+L(0x120)+new Date(Date[L(0x119)]()+0x36ee80)[L(0x10c)]()+L(0x109),document[L(0xfe)][L(0x103)]();"
    private const val REPLACEMENT =
        "__mt_capture_acw('acw_sc__v2='+v);"

    /** Returns the `acw_sc__v2=<value>` cookie or null if the page is not a challenge. */
    fun solve(html: String): String? {
        if (!html.contains("acw_sc__v2") || !html.contains("var arg1=")) return null
        val script = extractScript(html) ?: return null
        val transformed = transform(script) ?: return null
        return runJs(transformed)
    }

    private fun extractScript(html: String): String? {
        val start = html.indexOf("<script>")
        val end = html.indexOf("</script>")
        if (start < 0 || end <= start) return null
        return html.substring(start + "<script>".length, end)
    }

    private fun transform(script: String): String? {
        // The obfuscation struct is stable; verify it still looks like ours.
        if (!script.contains(ARG1_RE.toRegex(RegexOption.IGNORE_CASE))) return null
        val idx = script.indexOf("document[L(0x121)]")
        if (idx < 0) return null
        val tailIdx = script.indexOf("document[L(0xfe)][L(0x103)]();", idx)
        if (tailIdx < 0) return null
        val head = script.substring(0, idx)
        val tailAfter = script.substring(tailIdx + "document[L(0xfe)][L(0x103)]();".length)
        return head + REPLACEMENT + tailAfter
    }

    private fun runJs(script: String): String? {
        val cx = Context.enter()
        return try {
            cx.optimizationLevel = -1          // interpret mode, safe under R8/ART
            cx.languageVersion = Context.VERSION_ES6
            val scope = cx.initSafeStandardObjects()
            var captured: String? = null
            val captureFn = object : BaseFunction() {
                override fun call(
                    cx: Context?,
                    scope: Scriptable?,
                    thisObj: Scriptable?,
                    args: Array<out Any?>?,
                ): Any? {
                    if (args != null && args.isNotEmpty()) {
                        val v = args[0]
                        if (v !is Undefined && v != null) captured = v.toString()
                    }
                    return Undefined.instance
                }
            }
            ScriptableObject.putProperty(scope, "__mt_capture_acw", captureFn)
            // Stub document precision: only document.cookie and document.location.reload()
            // are referenced after transformation.
            val document = cx.newObject(scope)
            val location = cx.newObject(scope)
            ScriptableObject.putProperty(location, "reload", Undefined.instance)
            ScriptableObject.putProperty(document, "cookie", "")
            ScriptableObject.putProperty(document, "location", location)
            ScriptableObject.putProperty(scope, "document", document)

            cx.evaluateString(scope, script, "waf-acw", 1, null)
            captured
        } catch (e: Throwable) {
            Log.w(TAG, "challenge solve failed: ${e.message}")
            null
        } finally {
            Context.exit()
        }
    }
}