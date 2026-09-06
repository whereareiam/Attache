plugins {
    id("unit")
    id("publication")
}

publishing {
    publications.create<MavenPublication>("mavenJava") {
        from(components["java"])
        artifactId = project.name
    }
}
