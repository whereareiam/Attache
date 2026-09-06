import me.whereareiam.toolkit.architecture.model.ArchitectureExtension

plugins {
    id("library")
}

// Platform modules compose the common runtime with their host-specific classloader and lifecycle.
extensions.configure<ArchitectureExtension>("architecture") {
    kind = assembly
}

publishing {
    publications.named<MavenPublication>("mavenJava") {
        artifactId = "attache-${project.name.removePrefix("platform-")}"
    }
}
