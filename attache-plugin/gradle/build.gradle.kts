plugins {
    id("gradle-plugin")
}

description = "Attache descriptor generation for Gradle dependency declarations"

gradlePlugin {
    plugins {
        create("attache") {
            id = "me.whereareiam.attache"
            implementationClass = "me.whereareiam.attache.plugin.gradle.AttachePlugin"
            displayName = "Attache Gradle Plugin"
            description = "Generates Attache descriptor fragments from Gradle dependency declarations."
        }
    }
}

dependencies {
    implementation(projects.attacheApi)
}
