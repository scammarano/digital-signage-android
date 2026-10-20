plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }

// Only the explicitly requested release build can read the permanent P5 signer.
// Development builds NEVER act as a customer update and do not share that key.
val usePermanentP5Signer = providers.gradleProperty("dsPermanentSigning").orNull == "true"
fun p5Secret(name: String): String =
    System.getenv(name)?.takeIf { it.isNotBlank() }
        ?: throw GradleException("Missing required protected P5 signing configuration: $name")

android {
    namespace = "com.digitalsignage.tv"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.digitalsignage.tv"
        minSdk = 19
        targetSdk = 35
        versionCode = 423
        versionName = "0.4.23-debug"
        multiDexEnabled = true
        testInstrumentationRunner = "com.digitalsignage.tv.SportsInstrumentation"
    }
    buildFeatures { buildConfig = true }
    signingConfigs {
        if (usePermanentP5Signer) {
            create("p5Permanent") {
                storeFile = file(p5Secret("DS_P5_KEYSTORE_PATH"))
                storePassword = p5Secret("DS_P5_STORE_PASSWORD")
                keyAlias = p5Secret("DS_P5_KEY_ALIAS")
                keyPassword = p5Secret("DS_P5_KEY_PASSWORD")
                enableV1Signing = true // Android 4.4 / Fire OS 5
                enableV2Signing = true
            }
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            if (usePermanentP5Signer) {
                signingConfig = signingConfigs.getByName("p5Permanent")
            }
        }
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17; isCoreLibraryDesugaringEnabled = true }
    kotlinOptions { jvmTarget = "17" }
}
dependencies {
    implementation(project(":sports-core"))
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.3")
    implementation("androidx.multidex:multidex:2.0.1")
}
