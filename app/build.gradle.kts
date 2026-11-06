import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.livetranslator"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.neonlingua.translator"
        minSdk = 29
        targetSdk = 34
        versionCode = 3
        versionName = "1.1"
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }
    }

    // 公開用の署名鍵(signing/keystore.properties があればそれを使う。無ければデバッグ鍵)
    val keyProps = Properties().apply {
        val f = rootProject.file("signing/keystore.properties")
        if (f.exists()) f.inputStream().use { load(it) }
    }
    signingConfigs {
        if (keyProps.isNotEmpty()) create("release") {
            storeFile = rootProject.file("signing/" + keyProps.getProperty("storeFile"))
            storePassword = keyProps.getProperty("storePassword")
            keyAlias = keyProps.getProperty("keyAlias")
            keyPassword = keyProps.getProperty("keyPassword")
        }
    }
    buildFeatures { buildConfig = true }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    androidResources {
        noCompress += listOf("mdl", "fst", "int", "conf", "mat", "txt", "dubm", "ie", "stats")
    }
}

dependencies {
    implementation("com.alphacephei:vosk-android:0.3.47")
    implementation("net.java.dev.jna:jna:5.13.0@aar")
    implementation("com.google.mlkit:translate:17.0.3")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-ktx:1.9.1")
}

// ビルドしたAPKをデスクトップにコピーする
//   デバッグ版 → LiveTranslator.apk(自分の確認用)
//   リリース版 → NeonLingua-v(バージョン).apk(公開用)
val apkBuildDir = layout.buildDirectory.get().asFile
val appVersion = android.defaultConfig.versionName
fun copyApk(from: String, name: String) {
    val apk = File(apkBuildDir, from).takeIf { it.exists() } ?: return
    val dest = File(File(System.getProperty("user.home"), "Desktop"), name)
    apk.copyTo(dest, overwrite = true)
    println("APKをデスクトップにコピーしました: $dest")
}
tasks.register("copyApkToDesktop") {
    doLast { copyApk("outputs/apk/debug/app-debug.apk", "LiveTranslator.apk") }
}
tasks.register("copyReleaseApkToDesktop") {
    doLast { copyApk("outputs/apk/release/app-release.apk", "NeonLingua-v$appVersion.apk") }
}
tasks.matching { it.name == "assembleDebug" }.configureEach { finalizedBy("copyApkToDesktop") }
tasks.matching { it.name == "assembleRelease" }.configureEach { finalizedBy("copyReleaseApkToDesktop") }
