plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

fun env(name: String): String? = System.getenv(name)?.takeIf { it.isNotBlank() }
val keystore = env("KS_PATH")?.let { file(it) }?.takeIf { it.exists() }
// pannello Kairos (lo stesso di KAIROS TV); la variabile KAIROS_SERVER_URL del repository, se c'è, lo sostituisce
val serverUrl = (env("KAIROS_SERVER_URL") ?: "https://kairos-panel.mariopiccolo8562.workers.dev").trimEnd('/').replace("\"", "")
// download e aggiornamenti: un solo indirizzo (oggi il pannello), da cambiare quando arriva il dominio dedicato ai download;
// l'app chiede <DL_BASE>/get/lite.json, poi panel-kairos.com (vedi UpdFeed.kt); la variabile KAIROS_DL_BASE, se c'è, lo sostituisce
val dlBase = (env("KAIROS_DL_BASE") ?: "https://panel-kairos.com").trimEnd('/').replace("\"", "")
// riserva degli aggiornamenti, solo se il pannello non risponde: kairos-lite.json dell'ultima release di questo repository
// (Kairos Lite è separata da KAIROS TV); con il repository privato fallisce senza danni
val updateFallback = "https://github.com/" + (env("GITHUB_REPOSITORY") ?: "mariopiccolo8562-lang/kairos-lite") + "/releases/latest/download/kairos-lite.json"

android {
    namespace = "com.kairos.lite"
    compileSdk = 35

    defaultConfig {
        // app separata da KAIROS TV: si installa accanto, con aggiornamenti suoi
        applicationId = env("LITE_APP_ID") ?: "com.kairos.lite"
        // 22 = Android 5.1: Fire TV Stick di 1ª e 2ª generazione (Fire OS 5) comprese
        minSdk = 22
        targetSdk = 35
        versionCode = env("VERSION_CODE")?.toInt() ?: 1
        versionName = env("VERSION_NAME") ?: "1.0.0"
        resValue("string", "app_name", env("LITE_APP_NAME") ?: "Kairos Lite")
        buildConfigField("String", "SERVER_URL", "\"$serverUrl\"")
        buildConfigField("String", "DL_BASE", "\"$dlBase\"")
        buildConfigField("String", "UPDATE_URL_FALLBACK", "\"$updateFallback\"")
        // Fire TV / Android TV / telefoni sono ARM: niente x86, APK più piccolo
        ndk { abiFilters += listOf("armeabi-v7a", "arm64-v8a") }
        // testi dell'app solo in italiano (scritti nel codice): le traduzioni delle librerie in decine di lingue
        // (media3 & co., mai mostrate) restano fuori dall'APK; le risorse senza lingua restano tutte
        resourceConfigurations += listOf("it")
    }

    signingConfigs {
        create("release") {
            if (keystore != null) {
                storeFile = keystore
                storePassword = env("KS_PASS")
                keyAlias = env("KEY_ALIAS")
                keyPassword = env("KEY_PASS")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = if (keystore != null) signingConfigs.getByName("release") else signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        // la libreria WireGuard usa java.time / Optional / stream: servono anche su Android 5-7
        isCoreLibraryDesugaringEnabled = true
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { buildConfig = true }
    lint { abortOnError = false; checkReleaseBuilds = false }
}

dependencies {
    // poche librerie, tutte compatibili con Android 5.1
    implementation("androidx.core:core:1.13.1")
    testImplementation("junit:junit:4.13.2")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.media3:media3-exoplayer:1.4.1")
    implementation("androidx.media3:media3-exoplayer-hls:1.4.1")
    implementation("androidx.media3:media3-ui:1.4.1")
    implementation("com.github.bumptech.glide:glide:4.16.0")
    // avvio più rapido: Android compila in anticipo le parti usate all'apertura e dal lettore (regole in src/main/baseline-prof.txt)
    implementation("androidx.profileinstaller:profileinstaller:1.3.1")
    // 1.0.20250531 = ultima con minSdk 21 (stesso motore wireguard-go della versione successiva)
    implementation("com.wireguard.android:tunnel:1.0.20250531")
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")
}
