plugins {
    id("attache.java-common")
}

dependencies {
    api(projects.attachePlatform.platformStandalone)

    compileOnly(libs.slf4j.api)
    compileOnly(libs.spring.boot.autoconfigure)
    annotationProcessor(libs.spring.boot.configuration.processor)

    testImplementation(libs.slf4j.api)
    testImplementation(libs.spring.boot.autoconfigure)
    testImplementation(libs.spring.boot.test)
    testImplementation(libs.assert4j.core)
}

tasks.withType<Test>().configureEach {
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
