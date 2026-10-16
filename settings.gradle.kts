pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }
dependencyResolutionManagement { repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS); repositories { google(); mavenCentral() } }
rootProject.name = "DigitalSignageTV"
include(":app")
include(":sports-core")
// Only integration tests read the authoritative Turnos module from its separate checkout.
if(file("../turnos-authority/native-core").isDirectory){
    include(":turnos-authority-fixture")
    project(":turnos-authority-fixture").projectDir=file("../turnos-authority/native-core")
}

