plugins {
    id("platform")
}

description = "Attache runtime dependency manager auto-configuration for Spring Boot applications"

dependencies {
    api(projects.attachePlatform.platformStandalone)

    compileOnly(libs.slf4j.api)
    compileOnly(libs.spring.boot.autoconfigure)

    annotationProcessor(libs.spring.boot.configuration.processor)

    testImplementation(libs.assert4j.core)
    testImplementation(libs.slf4j.api)
    testImplementation(libs.spring.boot.autoconfigure)
    testImplementation(libs.spring.boot.test)
}

tasks.withType<Test>().configureEach {
    jvmArgs("--add-opens", "java.base/jdk.internal.loader=ALL-UNNAMED")
}
