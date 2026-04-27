import org.gradle.api.initialization.resolve.RepositoriesMode

rootProject.name = "Attache"
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    includeBuild("build-logic")

    repositories {
        gradlePluginPortal()
        mavenCentral()
        maven("https://maven.whereareiam.me/release")
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)

    repositories {
        mavenCentral()
        maven("https://maven.whereareiam.me/release")
        maven("https://hub.spigotmc.org/nexus/content/groups/public/")
        maven("https://repo.papermc.io/repository/maven-public/")
    }
}

include(":attache-api")
include(":attache-common")
include(":attache-platform:platform-bukkit")
include(":attache-platform:platform-paper")
include(":attache-platform:platform-velocity")
include(":attache-platform:platform-standalone")
include(":attache-platform:platform-spring")
