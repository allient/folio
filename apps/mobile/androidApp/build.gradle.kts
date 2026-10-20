import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeCompiler)
}

// Firebase (Google Analytics, Crashlytics). google-services.json comes from the Firebase console and
// is git-ignored; the plugins apply only when it is present, so builds without it still work and
// Firebase stays inactive. Crashlytics uploads the R8 mapping file with release builds.
val googleServices = file("google-services.json")
if (googleServices.exists()) {
    apply(plugin = "com.google.gms.google-services")
    apply(plugin = "com.google.firebase.crashlytics")
    // Dev builds (com.allient.folio.dev) never ship: no Firebase config is generated for them, so
    // Analytics and Crashlytics stay off and testing never reaches the real statistics.
    tasks.matching { it.name == "processDebugGoogleServices" }.configureEach { enabled = false }
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_11
    }
}
dependencies {
    implementation("com.google.mlkit:text-recognition:16.0.1")
    // Handwriting to text (lasso → Convert to text). Language models download on first use.
    implementation("com.google.mlkit:digital-ink-recognition:19.0.0")
    implementation("io.legere:pdfiumandroid:2.0.3")
    implementation("com.tom-roush:pdfbox-android:2.0.27.0")
    implementation("org.readium.kotlin-toolkit:readium-shared:3.4.0")
    implementation("org.readium.kotlin-toolkit:readium-streamer:3.4.0")
    implementation("org.readium.kotlin-toolkit:readium-navigator:3.4.0")
    implementation("androidx.fragment:fragment-ktx:1.8.9")
    // Live board: inject the saved board into the Excalidraw page before its scripts run.
    implementation("androidx.webkit:webkit:1.17.1")
    implementation(libs.androidx.appcompat)
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")
    androidTestImplementation(libs.androidx.testExt.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    implementation(project(":shared"))
    implementation("androidx.exifinterface:exifinterface:1.4.2")
    implementation("androidx.ink:ink-authoring:1.1.0-alpha09")
    implementation("androidx.ink:ink-brush:1.1.0-alpha09")
    implementation("androidx.ink:ink-strokes:1.1.0-alpha09")
    implementation("androidx.ink:ink-rendering:1.1.0-alpha09")
    implementation("com.github.luben:zstd-jni:1.5.7-23@aar")
    implementation("androidx.work:work-runtime-ktx:2.11.2")
    // Google Play in-app updates (talks to the Play Store app over IPC; no network permission).
    implementation("com.google.android.play:app-update-ktx:2.1.0")
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.analytics)
    implementation(libs.firebase.crashlytics)
    // Already on the classpath through WorkManager/Compose; declared because backups observe app background.
    implementation("androidx.lifecycle:lifecycle-process:2.11.0")

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui)

    implementation(libs.compose.uiToolingPreview)
    debugImplementation(libs.compose.uiTooling)
}

// Upload key for Google Play. Locally from apps/mobile/keystore.properties (git-ignored),
// in CI from FOLIO_UPLOAD_* environment variables. Without either, release stays unsigned.
val uploadKey = Properties().apply {
    rootProject.file("keystore.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}
fun uploadSetting(key: String, env: String): String? = uploadKey.getProperty(key) ?: System.getenv(env)

android {
    namespace = "com.allient.folio"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.allient.folio"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        // fastlane passes -PversionCode (next code on Play) and -PversionName.
        versionCode = (findProperty("versionCode") as String?)?.toInt() ?: 1
        versionName = (findProperty("versionName") as String?) ?: "1.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    signingConfigs {
        val storeFile = uploadSetting("storeFile", "FOLIO_UPLOAD_KEYSTORE")
        if (storeFile != null) {
            create("upload") {
                this.storeFile = rootProject.file(storeFile)
                storePassword = uploadSetting("storePassword", "FOLIO_UPLOAD_KEYSTORE_PASSWORD")
                keyAlias = uploadSetting("keyAlias", "FOLIO_UPLOAD_KEY_ALIAS")
                keyPassword = uploadSetting("keyPassword", "FOLIO_UPLOAD_KEY_PASSWORD")
            }
        }
    }
    buildTypes {
        // Debug builds install next to the store app: own ID, name "Folio Dev" (src/debug/res).
        debug {
            applicationIdSuffix = ".dev"
            versionNameSuffix = "-dev"
        }
        release {
            signingConfig = signingConfigs.findByName("upload")
            // R8: smaller download and faster startup. Keep rules for JNI/reflective libraries are in proguard-rules.pro.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}
