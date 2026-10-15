plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android { namespace = "com.digitalsignage.tv"; compileSdk = 35
    defaultConfig { applicationId = "com.digitalsignage.tv"; minSdk = 19; targetSdk = 35; versionCode = 408; versionName = "0.4.8"; multiDexEnabled = true }
    buildTypes { release { isMinifyEnabled = false } }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17; isCoreLibraryDesugaringEnabled = true }
    kotlinOptions { jvmTarget = "17" }
}
dependencies {
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.3")
    implementation("androidx.multidex:multidex:2.0.1")
}
