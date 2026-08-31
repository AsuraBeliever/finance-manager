import groovy.json.JsonSlurper

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

// Single source of truth for the version: package.json, same as the web app and
// the Tauri desktop shell. versionCode must increase monotonically, so pack the
// semver into an integer: 2.39.0 -> 23900 (major*10000 + minor*100 + patch).
@Suppress("UNCHECKED_CAST")
val packageJson = JsonSlurper().parse(rootProject.file("../package.json")) as Map<String, Any>
val appVersionName = packageJson["version"] as String
val appVersionCode = appVersionName.split(".").let { (major, minor, patch) ->
    major.toInt() * 10000 + minor.toInt() * 100 + patch.substringBefore("-").toInt()
}

// Release signing comes from the environment so the private keystore never lives
// in this (public) repository. CI decodes ANDROID_KEYSTORE_BASE64 to a file and
// sets these; locally, point BROKE_KEYSTORE at your own .jks. With no keystore
// the release APK still builds, just unsigned (useful for a quick check).
val keystorePath: String? = System.getenv("BROKE_KEYSTORE")
val keystoreFile = keystorePath?.let { file(it) }?.takeIf { it.exists() }

android {
    namespace = "com.asura.finanzas"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.asura.finanzas"
        minSdk = 26
        targetSdk = 35
        versionCode = appVersionCode
        versionName = appVersionName

        // The deployed worker. Override for a debug build with
        // `-PbrokeApiBase=http://<lan-ip>:8787` to hit `wrangler dev` instead, so
        // capture forms can be tested without writing to real finances.
        val apiBase = (project.findProperty("brokeApiBase") as String?)
            ?: "https://finanzas.aseth.workers.dev"
        buildConfigField("String", "API_BASE", "\"$apiBase\"")

        // The *web* client id, on purpose: Google issues the ID token with this
        // as its audience, which is exactly what the worker checks. Public by
        // design — it identifies the project, it is not a secret.
        buildConfigField(
            "String",
            "GOOGLE_SERVER_CLIENT_ID",
            "\"736484669055-ub19ovg5t6ccrmbjd5vlpi65pu0j8stn.apps.googleusercontent.com\"",
        )
    }

    signingConfigs {
        if (keystoreFile != null) {
            create("release") {
                storeFile = keystoreFile
                storePassword = System.getenv("BROKE_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("BROKE_KEY_ALIAS") ?: "broke"
                keyPassword = System.getenv("BROKE_KEY_PASSWORD")
                    ?: System.getenv("BROKE_KEYSTORE_PASSWORD")
                // v1+v2 are what minSdk 26 gets by default; v3 additionally
                // allows rotating the key later without orphaning installs.
                enableV3Signing = true
            }
        }
    }

    buildTypes {
        debug {
            // Side by side with the real app: a different applicationId means the
            // test build never replaces (or fails to install over) the signed one,
            // and the launcher shows which is which.
            applicationIdSuffix = ".debug"
            manifestPlaceholders["appLabel"] = "Broke dev"
        }
        release {
            manifestPlaceholders["appLabel"] = "@string/app_name"
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = if (keystoreFile != null) signingConfigs.getByName("release") else null
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
        // FlowRow is how the shared page header reproduces the web's wrapping
        // action bar; opting in per call site would mean annotating every screen.
        freeCompilerArgs += "-opt-in=androidx.compose.foundation.layout.ExperimentalLayoutApi"
    }

    lint {
        // AGP 8.7's lint worker throws NoClassDefFoundError (com/intellij/psi/*)
        // on the JDKs we build with, which would fail every release build. Lint
        // still runs on demand with `./gradlew lintDebug`; it just no longer
        // gates packaging.
        checkReleaseBuilds = false
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.10.01")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.navigation:navigation-compose:2.8.4")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.compose.ui:ui-tooling-preview")
    // Downloadable fonts: the appearance settings offer the same type pairings
    // as the web, which loads them from Google Fonts. Fetching them at runtime
    // keeps the APK from carrying eight more families it may never use.
    implementation("androidx.compose.ui:ui-text-google-fonts:1.7.5")

    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    // Sign in with Google. The redirect flow the web uses cannot work here (the
    // cookie would land in the browser), so the app gets an ID token natively
    // and trades it for a session at /api/auth/google/token.
    implementation("androidx.credentials:credentials:1.3.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.3.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")

    testImplementation("junit:junit:4.13.2")
}
