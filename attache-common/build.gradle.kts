plugins {
    id("library")
}

description = "Internal runtime for Attache - Runtime dependency management library"

dependencies {
    api(projects.attacheApi)
}
