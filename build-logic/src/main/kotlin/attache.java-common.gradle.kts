import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.tasks.testing.Test

plugins {
    `java-library`
    `maven-publish`
}

val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")
val buildVersion = providers.environmentVariable("VERSION").orElse("dev")

group = "me.whereareiam"
version = buildVersion.get()

tasks.withType<JavaCompile>().configureEach {
    sourceCompatibility = JavaVersion.VERSION_17.toString()
    targetCompatibility = JavaVersion.VERSION_17.toString()
}

dependencies {
    add("compileOnly", libs.findLibrary("lombok").get())
    add("annotationProcessor", libs.findLibrary("lombok").get())
    add("testCompileOnly", libs.findLibrary("lombok").get())
    add("testAnnotationProcessor", libs.findLibrary("lombok").get())

    add("compileOnly", libs.findLibrary("jetbrains-annotations").get())
    add("testCompileOnly", libs.findLibrary("jetbrains-annotations").get())

    add("testImplementation", libs.findLibrary("junit-jupiter").get())
    add("testRuntimeOnly", libs.findLibrary("junit-platform-launcher").get())
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

extensions.configure<PublishingExtension> {
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
