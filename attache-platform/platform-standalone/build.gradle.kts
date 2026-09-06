plugins {
    id("platform")
}

description = "Attache runtime dependency manager for standalone Java applications"

dependencies {
    api(projects.attacheApi)
    api(projects.attacheLauncher)
}
