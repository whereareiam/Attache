plugins {
    id("attache.java-common")
}

dependencies {
    api(projects.attacheApi)
    implementation(libs.gson)
    compileOnly(libs.jar.relocator)
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
            artifactId = "attache-common"
            pom {
                name.set("attache-common")
                description.set("Internal runtime for Attache - Runtime dependency management library")
            }
        }
    }
}
