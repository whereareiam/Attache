plugins {
    alias(libs.plugins.toolkit.architecture)
    alias(libs.plugins.toolkit.versioning)
}

allprojects {
    group = "me.whereareiam"
}

gradle.projectsEvaluated {
    allprojects { version = rootProject.version }
}

defaultTasks("build")
