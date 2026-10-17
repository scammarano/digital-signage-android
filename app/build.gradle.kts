plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android { namespace = "com.digitalsignage.tv"; compileSdk = 35
    defaultConfig { applicationId = "com.digitalsignage.tv"; minSdk = 19; targetSdk = 35; versionCode = 417; versionName = "0.4.17-turnos-candidate"; multiDexEnabled = true; testInstrumentationRunner = "com.digitalsignage.tv.SportsInstrumentation" }
    buildFeatures { buildConfig = true }
    buildTypes { release { isMinifyEnabled = false } }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17; isCoreLibraryDesugaringEnabled = true }
    kotlinOptions { jvmTarget = "17" }
}
dependencies {
    implementation(project(":sports-core"))
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.3")
    implementation("androidx.multidex:multidex:2.0.1")
}
if(rootProject.findProject(":turnos-authority-fixture")!=null){
    dependencies { androidTestImplementation(project(":turnos-authority-fixture")) }
    val fixtureDir=layout.buildDirectory.dir("generated/turnosFixture")
    val copyFixture=tasks.register<Copy>("copyTurnosHttpFixture"){
        from(rootProject.file("../turnos-authority/android/app/src/main/java/services/twocam/turnos/LanServer.java"))
        into(fixtureDir.map{it.dir("services/twocam/turnos")})
    }
    android.sourceSets.getByName("androidTest").java.srcDir("src/turnosIntegrationTest/java")
    android.sourceSets.getByName("androidTest").java.srcDir(fixtureDir)
    tasks.matching{it.name.startsWith("compile")&&it.name.contains("AndroidTest")}.configureEach{dependsOn(copyFixture)}
}


