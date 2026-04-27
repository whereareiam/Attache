plugins {
    id("attache.java-common")
}

dependencies {
    api(projects.attacheApi)
    api(projects.attacheCommon)
}

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
