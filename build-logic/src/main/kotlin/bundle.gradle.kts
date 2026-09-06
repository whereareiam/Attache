import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    id("unit")
    id("publication")
    id("com.gradleup.shadow")
}

tasks.jar {
    archiveClassifier.set("plain")
}

val bundleJar = tasks.named<ShadowJar>("shadowJar") {
    archiveClassifier.set("")
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
    mergeServiceFiles()
}

tasks.assemble { dependsOn(bundleJar) }

val bundleElements by configurations.creating {
    isCanBeConsumed = true
    isCanBeResolved = false
}
artifacts { add(bundleElements.name, bundleJar) }

publishing {
    publications.create<MavenPublication>("mavenJava") {
        artifact(bundleJar)
        artifactId = project.name
        pom.licenses {
            license {
                name.set("MIT License")
                url.set("https://opensource.org/license/mit/")
            }
        }
    }
}
