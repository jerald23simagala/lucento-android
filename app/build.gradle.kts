import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

// Optional cloud sync config (see docs/SYNC.md). Never committed: read from local.properties (a developer's
// own machine) with an environment-variable fallback (CI secrets), exactly like the release keystore below.
// The Supabase URL and publishable/anon key are not secret — they are meant to ship inside the APK and are
// protected by Postgres Row Level Security, not by being hidden — but they still live in config, not source,
// so every developer and CI job can point at their own project without touching a tracked file.
val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}
fun lucentoConfig(key: String): String = localProperties.getProperty(key) ?: System.getenv(key) ?: ""

android {
    namespace = "com.lucento.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.lucento.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 5
        versionName = "0.5.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }

        // Empty strings are a valid, safe default: the Settings screen detects them and tells the user cloud
        // sync setup is incomplete instead of showing a button that would fail. See docs/SYNC.md "Setup required".
        buildConfigField("String", "SUPABASE_URL", "\"${lucentoConfig("LUCENTO_SUPABASE_URL")}\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"${lucentoConfig("LUCENTO_SUPABASE_ANON_KEY")}\"")
        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"${lucentoConfig("LUCENTO_GOOGLE_WEB_CLIENT_ID")}\"")
        // Smarter reading (opt-in): Lucento's own proxy, which holds the Gemini key. Never a model API key here.
        buildConfigField(
            "String",
            "AI_INTERPRET_URL",
            "\"${lucentoConfig("LUCENTO_AI_INTERPRET_URL").ifBlank { "https://www.lucentoapp.com/api/interpret" }}\"",
        )
    }

    // Release signing comes only from the environment (CI secrets or a developer's shell); nothing is committed.
    // Without it, release builds are produced unsigned, which is enough to verify R8, resources and packaging.
    val releaseKeystore = System.getenv("LUCENTO_KEYSTORE_FILE")?.let { file(it) }?.takeIf { it.exists() }
    signingConfigs {
        if (releaseKeystore != null) {
            create("release") {
                storeFile = releaseKeystore
                storePassword = System.getenv("LUCENTO_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("LUCENTO_KEY_ALIAS")
                keyPassword = System.getenv("LUCENTO_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            if (releaseKeystore != null) signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    // Exported Room schemas feed MigrationTestHelper in instrumented migration tests.
    sourceSets["androidTest"].assets.srcDir("$projectDir/schemas")

    // Lint policy (see docs/QUALITY.md): errors and fatal issues block the build; warnings are reported (HTML/XML/SARIF
    // uploaded by CI) and triaged, not hidden. lint.xml promotes the checks that matter most for this app to errors.
    lint {
        abortOnError = true
        warningsAsErrors = false
        checkReleaseBuilds = true
        checkDependencies = false
        lintConfig = file("lint.xml")
        htmlReport = true
        xmlReport = true
        sarifReport = true
    }

    packaging {
        resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.generateKotlin", "true")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.animation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.datastore.preferences)

    // On-device receipt OCR (bundled model; works offline).
    implementation(libs.mlkit.text.recognition)

    // Optional cloud sync (see docs/SYNC.md). Nothing here is reachable unless the user explicitly opts in —
    // the app otherwise never calls any of it and the manifest's INTERNET permission stays unused.
    implementation(platform(libs.supabase.bom))
    implementation(libs.supabase.postgrest)
    implementation(libs.supabase.auth)
    implementation(libs.ktor.client.android)
    implementation(libs.kotlinx.datetime)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.google.id)

    // Bundled vector animations (res/raw); no runtime downloads, no network permission.
    implementation(libs.lottie.compose)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)

    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.room.testing)
    // Screenshot tour / UI workflow test (see ScreenshotTourTest); the manifest artifact hosts ComponentActivity.
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
