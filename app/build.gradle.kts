import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.ksp)
}

// Release signing material is supplied out-of-band and is NEVER committed. It is read from either:
//   1. a local `keystore.properties` file (gitignored), or
//   2. environment variables fed by GitHub Secrets in the release workflow:
//      SIGNING_KEY_BASE64 (decoded to a keystore file before the build), KEY_ALIAS,
//      KEY_STORE_PASSWORD, KEY_PASSWORD.
// When neither is present (e.g. local `assembleDebug` or an unsigned release build), the release
// signingConfig is left unconfigured and Gradle produces an unsigned release artifact; debug and
// local/unsigned builds keep working with no keystore.
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties =
    Properties().apply {
        if (keystorePropertiesFile.exists()) {
            FileInputStream(keystorePropertiesFile).use { load(it) }
        }
    }

fun signingValue(
    propKey: String,
    envKey: String,
): String? = (keystoreProperties.getProperty(propKey) ?: System.getenv(envKey))?.takeIf { it.isNotBlank() }

// Keystore path: explicit `storeFile` property, else the workflow-decoded keystore env var
// (KEYSTORE_PATH), else the conventional `app/release.keystore`. Only used when it actually exists.
val releaseStoreFile: File? =
    (
        signingValue("storeFile", "KEYSTORE_PATH")?.let { rootProject.file(it) }
            ?: rootProject.file("app/release.keystore")
    ).takeIf { it.exists() }
val releaseStorePassword = signingValue("storePassword", "KEY_STORE_PASSWORD")
val releaseKeyAlias = signingValue("keyAlias", "KEY_ALIAS")
val releaseKeyPassword = signingValue("keyPassword", "KEY_PASSWORD")
val hasReleaseSigning =
    releaseStoreFile != null &&
        releaseStorePassword != null &&
        releaseKeyAlias != null &&
        releaseKeyPassword != null

// `appVersionName` is the single source of truth for the release version. SearchMob uses Ubuntu-style
// date versioning: YY.MM.VV (two-digit year, month, and per-month build), set manually each release.
// `versionCode` is derived as (YY*10000 + MM*100 + VV) so it always increases monotonically with the
// date (e.g. 26.05.00 -> 260500, 26.06.00 -> 260600, 27.01.00 -> 270100). Bump this on each release.
val appVersionName = "26.05.05"
val appVersionCode =
    appVersionName
        .split("-")[0]
        .split(".")
        .map { it.toIntOrNull() ?: 0 }
        .let { (it.getOrElse(0) { 0 } * 10000) + (it.getOrElse(1) { 0 } * 100) + it.getOrElse(2) { 0 } }

android {
    namespace = "org.searchmob"
    compileSdk = 36

    defaultConfig {
        applicationId = "org.searchmob"
        minSdk = 26
        targetSdk = 35
        versionCode = appVersionCode
        versionName = appVersionName

        testInstrumentationRunner = "org.searchmob.SearchMobTestRunner"
    }

    signingConfigs {
        // Only register the release signing config when full signing material is available, so that
        // local/unsigned builds (and any build with no keystore) configure cleanly and stay unsigned.
        if (hasReleaseSigning) {
            create("release") {
                storeFile = releaseStoreFile
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            // Apply the release signing config only when it was registered above; otherwise leave it
            // null so `assembleRelease` produces an unsigned APK rather than failing the build.
            signingConfig = signingConfigs.findByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            // Ktor + its transitive deps ship duplicate metadata files that break APK packaging.
            excludes +=
                setOf(
                    "/META-INF/{AL2.0,LGPL2.1}",
                    "/META-INF/INDEX.LIST",
                    "/META-INF/DEPENDENCIES",
                    "/META-INF/io.netty.versions.properties",
                )
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

// Room exports its schema here for migration tracking and instrumentation tests.
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.cio)
    implementation(libs.ktor.server.html.builder)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.okhttp)
    implementation(libs.jsoup)
    implementation(libs.commons.codec)

    // Home-screen widget (Jetpack Glance): Compose-based app widget + Material3 theming for day/night.
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.glance.material3)

    // Encrypted storage (Phase 5): DataStore + Room/SQLCipher + Argon2id + optional biometric.
    implementation(libs.androidx.datastore)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.sqlcipher.android)
    implementation(libs.androidx.sqlite)
    implementation(libs.argon2kt)
    implementation(libs.androidx.biometric)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.kotlinx.coroutines.test)

    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
