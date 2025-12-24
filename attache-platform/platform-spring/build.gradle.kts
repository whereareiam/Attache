dependencies {
    "api"(project(":attache-platform:platform-standalone"))

    "compileOnly"(libs.slf4j.api)
    "compileOnly"(libs.spring.boot.autoconfigure)
    "annotationProcessor"(libs.spring.boot.configuration.processor)

    // testing
    "testImplementation"(libs.slf4j.api)
    "testImplementation"(libs.spring.boot.autoconfigure)
    "testImplementation"(libs.spring.boot.test)
    "testImplementation"(libs.assert4j.core)
}

tasks.withType<Test> {
    // Needed because StandaloneLibraryManager uses reflection against AppClassLoader
    jvmArgs("--add-opens", "java.base/jdk.internal.loader=ALL-UNNAMED")
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
            artifactId = "attache-spring"
            pom {
                name.set("attache-spring")
                description.set("Attache runtime dependency manager auto-configuration for Spring Boot applications")
            }
        }
    }
}
