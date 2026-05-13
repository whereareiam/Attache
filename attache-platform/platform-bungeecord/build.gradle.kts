plugins {
    id("attache.java-common")
}

dependencies {
    api(projects.attacheApi)
    implementation(projects.attacheCommon)
    compileOnly(libs.bungee)
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
            artifactId = "attache-bungeecord"
            pom {
                name.set("attache-bungeecord")
                description.set("Attache runtime dependency manager for BungeeCord plugins")
            }
        }
    }
}
