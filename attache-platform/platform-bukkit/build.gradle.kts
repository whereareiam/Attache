plugins {
    id("platform")
}

description = "Attache runtime dependency manager for Bukkit plugins"

dependencies {
    api(projects.attacheApi)

    implementation(projects.attacheLauncher)

    compileOnly(libs.bukkit)
}
