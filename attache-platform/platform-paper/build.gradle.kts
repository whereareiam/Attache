plugins {
    id("attache.java-common")
}

dependencies {
    api(projects.attacheApi)
    implementation(projects.attacheCommon)
    compileOnly(libs.paper)
}

tasks.withType<JavaCompile>().configureEach {
    sourceCompatibility = JavaVersion.VERSION_21.toString()
    targetCompatibility = JavaVersion.VERSION_21.toString()
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
            artifactId = "attache-paper"
            pom {
                name.set("attache-paper")
                description.set("Attache runtime dependency manager for Paper plugins")
            }
        }
    }
}
