plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android { namespace = "com.digitalsignage.tv"; compileSdk = 35
    defaultConfig { applicationId = "com.digitalsignage.tv"; minSdk = 26; targetSdk = 35; versionCode = 400; versionName = "0.4.0" }
    buildTypes { release { isMinifyEnabled = false } }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
}
