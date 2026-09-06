plugins {
    java
}

val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")

dependencies {
    compileOnly(libs.findLibrary("jetbrains-annotations").get())
    compileOnly(libs.findLibrary("lombok").get())

    annotationProcessor(libs.findLibrary("lombok").get())

    testCompileOnly(libs.findLibrary("jetbrains-annotations").get())
    testCompileOnly(libs.findLibrary("lombok").get())

    testAnnotationProcessor(libs.findLibrary("lombok").get())
}
