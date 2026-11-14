import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Release signing comes from keystore.properties (never committed):
//   storeFile=/path/to/release.jks
//   storePassword=…   keyAlias=…   keyPassword=…
// Without it, release builds are produced unsigned.
val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    namespace = "app.sidepeek"
    compileSdk = 36
    buildToolsVersion = "36.0.0"
    defaultConfig {
        applicationId = "app.sidepeek"
        minSdk = 26
        targetSdk = 36
        versionCode = 14
        versionName = "1.2.0"
    }
    signingConfigs {
        if (keystoreProps.getProperty("storeFile") != null) {
            create("release") {
                storeFile = file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }
    buildTypes {
        debug {
            applicationIdSuffix = ".test"
            versionNameSuffix = "-test"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            isDebuggable = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfigs.findByName("release")?.let { signingConfig = it }
        }
    }
    // github：GitHub Releases 的 APK（行為和以前一樣）。
    // play：Google Play 上架版，拿掉 Play 政策會擋的做法（見 src/play/AndroidManifest.xml）。
    // 指令：assembleGithubDebug / assemblePlayDebug、assembleGithubRelease、bundlePlayRelease。
    flavorDimensions += "store"
    productFlavors {
        create("github") {
            dimension = "store"
            // 直接跳出系統的「停用電池最佳化」視窗（需要 REQUEST_IGNORE_BATTERY_OPTIMIZATIONS）。
            buildConfigField("boolean", "DIRECT_BATTERY_REQUEST", "true")
            // 打開 App 時向 GitHub 查新版本，有新版就提示去下載 APK（UpdateChecker）。
            buildConfigField("boolean", "SELF_UPDATE_CHECK", "true")
        }
        create("play") {
            dimension = "store"
            // Play 只允許少數類型直接請求豁免，所以改成打開 App 資訊頁並提示「電池 → 不受限制」。
            buildConfigField("boolean", "DIRECT_BATTERY_REQUEST", "false")
            // Play 規定只能經 Play 更新，不能叫使用者去別處下載；Play 會自己更新，所以整個關掉。
            buildConfigField("boolean", "SELF_UPDATE_CHECK", "false")
        }
    }
    buildFeatures { buildConfig = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    testImplementation("junit:junit:4.13.2")
}
