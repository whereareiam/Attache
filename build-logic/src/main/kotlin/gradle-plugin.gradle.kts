plugins {
    `java-gradle-plugin`
    id("unit")
    id("publication")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(17))
}

dependencies {
    testImplementation(gradleTestKit())
}

publishing {
    publications.withType<MavenPublication>().configureEach {
        if (name == "pluginMaven")
            artifactId = "attache-gradle-plugin"
    }
}
