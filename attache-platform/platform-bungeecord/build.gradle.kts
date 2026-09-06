plugins {
    id("platform")
}

description = "Attache runtime dependency manager for BungeeCord plugins"

dependencies {
    api(projects.attacheApi)

    implementation(projects.attacheLauncher)

    compileOnly(libs.bungee)
}
