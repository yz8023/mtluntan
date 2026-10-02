import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
}

ksp {
    // 导出 Room schema：迁移脚本要按它生成，保证和 Room 期望的表结构一字不差
    arg("room.schemaLocation", "$projectDir/schemas")
}

val keystoreProperties = Properties()
val keystorePropertiesFile = rootProject.file("keystore.properties")
if (keystorePropertiesFile.exists()) {
    keystoreProperties.load(keystorePropertiesFile.inputStream())
}

/** 依次在「根目录 / app 模块」找 keystore.properties 里指定的 keystore。 */
val resolvedKeystore: File? = run {
    val configured = keystoreProperties.getProperty("storeFile")
    val candidates = listOfNotNull(
        configured?.let { rootProject.file(it) },
        configured?.let { file(it) },
        rootProject.file("mtluntan.keystore"),
        file("mtluntan.keystore"),
        file("Mtluntan.keystore"),
    )
    candidates.firstOrNull { it.exists() }
}

android {
    namespace = "io.mtluntan.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "io.mtluntan.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 2
        versionName = "1.1.0"

        multiDexEnabled = true
    }

    signingConfigs {
        create("release") {
            storeFile = resolvedKeystore ?: file(keystoreProperties.getProperty("storeFile") ?: "Mtluntan.keystore")
            storePassword = keystoreProperties.getProperty("storePassword") ?: "mtluntan2024"
            keyAlias = keystoreProperties.getProperty("keyAlias") ?: "mtluntan"
            keyPassword = keystoreProperties.getProperty("keyPassword") ?: "mtluntan2024"
        }
    }

    buildTypes {
        release {
            // 仓库里没有提交 mtluntan.keystore：这时退回 debug 签名，让 assembleRelease
            // 依然能产出可安装的包（官方发布时补上 keystore 就会自动改用正式签名）。
            signingConfig = if (resolvedKeystore != null) {
                signingConfigs.getByName("release")
            } else {
                logger.lifecycle("⚠️  未找到 mtluntan.keystore，release 将使用 debug 签名（仅供本机测试安装）")
                signingConfigs.getByName("debug")
            }
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        viewBinding = false
        buildConfig = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.8"
    }
    packaging {
        resources.excludes += listOf(
            "/META-INF/{AL2.0,LGPL2.1}",
            "META-INF/DEPENDENCIES",
            "META-INF/INDEX.LIST",
            "META-INF/NOTICE*",
        )
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.7.0")
    implementation("androidx.webkit:webkit:1.9.0")

    implementation(platform("androidx.compose:compose-bom:2024.02.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.navigation:navigation-compose:2.7.7")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    implementation("com.google.code.gson:gson:2.10.1")

    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation("org.jsoup:jsoup:1.17.2")
    implementation("io.coil-kt:coil-compose:2.5.0")
    implementation("org.mozilla:rhino:1.7.14")

    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    implementation("androidx.work:work-runtime-ktx:2.9.0")
    implementation("androidx.datastore:datastore-preferences:1.0.0")
    implementation("androidx.multidex:multidex:2.0.1")

    testImplementation("junit:junit:4.13.2")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.02.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
}