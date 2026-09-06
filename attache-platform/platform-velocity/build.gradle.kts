plugins {
    id("platform")
}

description = "Attache runtime dependency manager for Velocity plugins"

dependencies {
    api(projects.attacheApi)

    implementation(projects.attacheLauncher)

    compileOnly(libs.velocity)
}
