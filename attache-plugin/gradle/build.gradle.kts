import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.tasks.testing.Test
import org.gradle.jvm.toolchain.JavaLanguageVersion

plugins {
    `java-gradle-plugin`
    `maven-publish`
}

val buildVersion = providers.environmentVariable("VERSION").orElse("dev")

group = "me.whereareiam"
version = buildVersion.get()

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

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
    implementation(libs.gson)
    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)

    testImplementation(gradleTestKit())
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

extensions.configure<PublishingExtension> {
    publications.withType<MavenPublication>().configureEach {
        if (name == "pluginMaven") {
            artifactId = "attache-gradle-plugin"
        }
    }

    repositories {
        maven {
            val realm = providers.environmentVariable("PUBLISH_REALM")
                .orElse(
                    buildVersion.map { versionString ->
                        if (versionString.contains("dev", ignoreCase = true)) "development" else "release"
                    }
                )
                .get()
                .lowercase()

            url = uri("https://maven.whereareiam.me/$realm")

            credentials {
                username = providers.environmentVariable("PUBLISH_USER").orNull.orEmpty()
                password = providers.environmentVariable("PUBLISH_TOKEN").orNull.orEmpty()
            }
        }
    }
}
