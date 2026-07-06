import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

// Load signing credentials from keystore.properties (gitignored). The file is
// expected at the repository root. CI populates it from GitHub Actions secrets
// before the build runs. If absent we fall back to AGP's default debug
// keystore so a checkout-and-build still works without configuration.
val keystoreProps = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

android {
    namespace = "dev.melcodes.kilometre"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "dev.melcodes.kilometre"
        minSdk = 30
        targetSdk = 36
        versionCode = 39
        versionName = "0.3.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        // Stable signing for debug builds. Android refuses to update an
        // installed app if the signing cert changes, so a per-machine debug
        // keystore is unworkable across the dev laptop + CI + two test
        // phones. Using one explicit keystore for both local and CI debug
        // builds keeps update flow seamless via Obtainium.
        getByName("debug") {
            if (keystoreProps.containsKey("storeFile")) {
                storeFile = file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
        // Release signs with the same keystore as debug so a release APK
        // installs as an in-place update over an existing debug install
        // (Android keys app-update identity on the signing cert, and both
        // test phones already carry this cert).
        create("release") {
            if (keystoreProps.containsKey("storeFile")) {
                storeFile = file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("debug")
        }
        release {
            // DEV-PHASE: release is debuggable so a developer can keep reaching
            // the on-device DB over adb/run-as (the DB backup workflow depends
            // on it). This costs ART optimization + Compose debug-check
            // stripping, so the map/replay is NOT 60fps on weaker devices (a
            // non-debuggable build measured ~50→60fps on a mid-range device) —
            // that tradeoff is accepted for now to keep DB access simple. MUST
            // flip to isDebuggable = false before the 1.0 public release.
            //
            // Minify stays off until proguard rules are verified against
            // MapLibre + Room.
            isDebuggable = true
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }

    // Room writes its generated schema JSON into this directory at build time.
    // The directory is committed (eventually) so schema migrations can be
    // diffed in PRs. Phase 1 starts at schema version 1; the file only appears
    // after the first build that compiles the Room entities.
    ksp {
        arg("room.schemaLocation", "$projectDir/schemas")
        arg("room.generateKotlin", "true")
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.play.services.location)

    // Phase 1 additions
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.datetime)
    implementation(libs.androidx.appcompat)

    // Phase 2: route map on the session detail screen.
    implementation(libs.maplibre.compose)
    implementation(libs.maplibre.compose.material3)

    // HSV colour wheel for the route gradient settings row (Apache-2.0).
    implementation(libs.colorpicker.compose)

    // Storage Access Framework helper for writing the GPX into the user's
    // chosen default save folder (Apache-2.0).
    implementation(libs.androidx.documentfile)

    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
