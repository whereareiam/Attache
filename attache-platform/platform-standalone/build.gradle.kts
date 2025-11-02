publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
            artifactId = "attache-standalone"
            pom {
                name.set("attache-standalone")
                description.set("Attache runtime dependency manager for standalone Java applications")
            }
        }
    }
}

