import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Base64
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.google.services) apply false
    alias(libs.plugins.firebase.crashlytics) apply false
}

// Crash reports are optional. Without app/google-services.json the plugins and the SDK are left out, so a
// checkout with no Firebase project still builds. See docs/QUALITY.md "Crash reporting".
val crashlyticsConfigured = file("google-services.json").exists()
if (crashlyticsConfigured) {
    apply(plugin = "com.google.gms.google-services")
    apply(plugin = "com.google.firebase.crashlytics")
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

// Each build type has its own Supabase project. Release keeps the production keys (LUCENTO_SUPABASE_*); debug reads
// only LUCENTO_DEBUG_SUPABASE_* — the test project, lucento-test — and never falls back to production, so a developer or
// QA sign-in can't write to live data. Debug without those keys simply reports cloud sync as not set up.
val releaseSupabaseUrl = lucentoConfig("LUCENTO_SUPABASE_URL")
val releaseSupabaseKey = lucentoConfig("LUCENTO_SUPABASE_PUBLISHABLE_KEY")
val debugSupabaseUrl = lucentoConfig("LUCENTO_DEBUG_SUPABASE_URL")
val debugSupabaseKey = lucentoConfig("LUCENTO_DEBUG_SUPABASE_ANON_KEY")
if (debugSupabaseUrl.isNotBlank() && debugSupabaseUrl.trimEnd('/').equals(releaseSupabaseUrl.trimEnd('/'), ignoreCase = true)) {
    throw GradleException("LUCENTO_DEBUG_SUPABASE_URL is the production project. Debug builds must use the test project.")
}
// Only a publishable (or legacy anon) key may ship in an APK. A secret key, or a legacy JWT whose role is service_role,
// bypasses Row Level Security, so the build stops instead of packaging it. The key itself is never printed.
fun isServerOnlyKey(key: String): Boolean {
    if (key.startsWith("sb_secret_")) return true
    val payload = key.split('.').takeIf { it.size == 3 }?.get(1) ?: return false
    val json = runCatching { String(Base64.getUrlDecoder().decode(payload.padEnd((payload.length + 3) / 4 * 4, '='))) }
        .getOrDefault("")
    return json.contains("\"service_role\"")
}
listOf("LUCENTO_SUPABASE_PUBLISHABLE_KEY" to releaseSupabaseKey, "LUCENTO_DEBUG_SUPABASE_ANON_KEY" to debugSupabaseKey).forEach { (name, key) ->
    if (isServerOnlyKey(key)) throw GradleException("$name is a server-only (secret or service_role) key. Use the publishable key.")
}

// Smarter reading's proxy, per build type. Debug never falls back to the production proxy: it only accepts tokens of the
// production project and would receive test-build text. Without LUCENTO_DEBUG_AI_INTERPRET_URL debug simply has no
// Smarter reading (the composer reads on the phone).
val productionAiInterpretUrl = "https://www.lucentoapp.com/api/interpret"
val releaseAiInterpretUrl = lucentoConfig("LUCENTO_AI_INTERPRET_URL").ifBlank { productionAiInterpretUrl }
val debugAiInterpretUrl = lucentoConfig("LUCENTO_DEBUG_AI_INTERPRET_URL")
// Ask Lucento's AI answers live beside Smarter reading on the same proxy (/api/ask next to /api/interpret), with the same
// rules: debug only talks to a test proxy, and a build without one simply has no AI answers (reports still work).
fun askUrlFor(interpretUrl: String): String =
    if (interpretUrl.isBlank()) "" else interpretUrl.trimEnd('/').replace(Regex("/interpret$"), "") + "/ask"
// Lucento Pro (docs/monetization/SERVER_CONTRACT.md §3): purchases are checked by /api/billing/verify on the same site.
fun billingVerifyUrlFor(interpretUrl: String): String =
    if (interpretUrl.isBlank()) "" else interpretUrl.trimEnd('/').replace(Regex("/interpret$"), "") + "/billing/verify"
// Google Play's licence key (Play Console › Monetization setup › Licensing), public by design: the phone checks every
// purchase's signature with it. Without it a release doesn't bill (no prices; "not on sale yet"). Debug never bills.
val playLicenceKey = lucentoConfig("LUCENTO_PLAY_LICENSE_KEY")
if (debugAiInterpretUrl.trimEnd('/').equals(releaseAiInterpretUrl.trimEnd('/'), ignoreCase = true)) {
    throw GradleException("LUCENTO_DEBUG_AI_INTERPRET_URL is the production proxy. Debug builds must use a test proxy or none.")
}

// CI's release job sets LUCENTO_REQUIRE_RELEASE_CONFIG=true: a release built without the production Supabase settings
// would ship with cloud sync silently disabled (N-24), so it fails instead.
if (System.getenv("LUCENTO_REQUIRE_RELEASE_CONFIG") == "true") {
    // The Google web client id too: without it the release hides "Continue with Google" (QA note 12).
    val missing = listOf(
        "LUCENTO_SUPABASE_URL" to releaseSupabaseUrl,
        "LUCENTO_SUPABASE_PUBLISHABLE_KEY" to releaseSupabaseKey,
        "LUCENTO_GOOGLE_WEB_CLIENT_ID" to lucentoConfig("LUCENTO_GOOGLE_WEB_CLIENT_ID"),
    )
        .filter { (_, value) -> value.isBlank() }.map { it.first }
    if (missing.isNotEmpty()) throw GradleException("Release configuration missing: ${missing.joinToString()}. Add them as GitHub repository variables (CI) or in local.properties.")
}

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
        // SUPABASE_URL and SUPABASE_ANON_KEY are set per build type below.
        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"${lucentoConfig("LUCENTO_GOOGLE_WEB_CLIENT_ID")}\"")
        // Smarter reading (opt-in) goes through Lucento's own proxy, which holds the Gemini key; set per build type below.
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
            buildConfigField("String", "SUPABASE_URL", "\"$debugSupabaseUrl\"")
            buildConfigField("String", "SUPABASE_ANON_KEY", "\"$debugSupabaseKey\"")
            buildConfigField("String", "AI_INTERPRET_URL", "\"$debugAiInterpretUrl\"")
            buildConfigField("String", "AI_ASK_URL", "\"${askUrlFor(debugAiInterpretUrl)}\"")
            // The .debug application id can never bill: test builds use the plan simulator instead.
            buildConfigField("String", "PLAY_LICENSE_KEY", "\"\"")
            buildConfigField("String", "BILLING_VERIFY_URL", "\"\"")
        }
        release {
            buildConfigField("String", "SUPABASE_URL", "\"$releaseSupabaseUrl\"")
            buildConfigField("String", "SUPABASE_ANON_KEY", "\"$releaseSupabaseKey\"")
            buildConfigField("String", "AI_INTERPRET_URL", "\"$releaseAiInterpretUrl\"")
            buildConfigField("String", "AI_ASK_URL", "\"${askUrlFor(releaseAiInterpretUrl)}\"")
            buildConfigField("String", "PLAY_LICENSE_KEY", "\"$playLicenceKey\"")
            buildConfigField("String", "BILLING_VERIFY_URL", "\"${billingVerifyUrlFor(releaseAiInterpretUrl)}\"")
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
    implementation(libs.androidx.biometric)
    // Lucento Pro: Google Play Billing (release builds; debug and tests use FakePurchaseSource).
    implementation(libs.play.billing)
    implementation(libs.androidx.fragment.ktx)

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

    // Present only when app/google-services.json exists. CrashReporting no-ops otherwise.
    if (crashlyticsConfigured) {
        implementation(platform(libs.firebase.bom))
        implementation(libs.firebase.crashlytics)
    }

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
