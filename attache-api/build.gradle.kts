plugins {
    id("attache.java-common")
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
            artifactId = "attache-api"
            pom {
                name.set("attache-api")
                description.set("Public API for Attache - Runtime dependency management library")
            }
        }
    }
}
