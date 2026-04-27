plugins {
    id("attache.java-common")
}

dependencies {
    api(projects.attacheApi)
    implementation(projects.attacheCommon)
    compileOnly(libs.velocity)
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
            artifactId = "attache-velocity"
            pom {
                name.set("attache-velocity")
                description.set("Attache runtime dependency manager for Velocity plugins")
            }
        }
    }
}
