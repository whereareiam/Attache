plugins {
    id("platform")
}

description = "Attache runtime dependency manager for Paper plugins"

dependencies {
    api(projects.attacheApi)

    implementation(projects.attacheLauncher)

    compileOnly(libs.paper)
}

tasks.withType<JavaCompile>().configureEach {
    sourceCompatibility = JavaVersion.VERSION_25.toString()
    targetCompatibility = JavaVersion.VERSION_25.toString()
}
