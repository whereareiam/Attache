plugins {
    id("attache.java-common")
}

dependencies {
    api(projects.attacheApi)
    implementation(projects.attacheCommon)
    compileOnly(libs.bukkit)
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
            artifactId = "attache-bukkit"
            pom {
                name.set("attache-bukkit")
                description.set("Attache runtime dependency manager for Bukkit plugins")
            }
        }
    }
}
