repositories {
    maven("https://hub.spigotmc.org/nexus/content/groups/public/")
}

dependencies {
    "compileOnly"(libs.bukkit)
}

tasks.withType<JavaCompile> {
    sourceCompatibility = JavaVersion.VERSION_17.toString()
    targetCompatibility = JavaVersion.VERSION_17.toString()
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