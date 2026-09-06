plugins {
    alias(libs.plugins.toolkit.architecture)
}

allprojects {
    group = "me.whereareiam"
    version = providers.environmentVariable("VERSION").orElse("dev").get()
}

defaultTasks("build")
