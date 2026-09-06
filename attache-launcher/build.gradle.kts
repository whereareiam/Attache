import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    id("library")
}

description = "Attache resolver download and initialization"
architecture { kind = assembly }

dependencies {
    api(projects.attacheApi)
    api(projects.attacheCommon)
}

val resolverMetadata by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
    isTransitive = false
}
val resolverTestRepository by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
    isTransitive = false
}
val consumerLibraries by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
}

dependencies {
    add(resolverMetadata.name, project(mapOf("path" to ":attache-resolver", "configuration" to "resolverMetadata")))
    add(resolverTestRepository.name, project(mapOf("path" to ":attache-resolver", "configuration" to "resolverTestRepository")))
    add(consumerLibraries.name, projects.attacheCommon)
}

sourceSets.main { resources.srcDir(resolverMetadata) }

val shadedConsumerFixture by tasks.registering(ShadowJar::class) {
    archiveClassifier.set("shaded-test-fixture")
    configurations = listOf(consumerLibraries)
    from(sourceSets.main.get().output)
    from(sourceSets.test.get().output)
    relocate("me.whereareiam.attache", "test.shaded.attache")
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}

tasks.test {
    dependsOn(resolverTestRepository, shadedConsumerFixture)
    inputs.files(resolverTestRepository).withPropertyName("resolverFixtures")
    inputs.file(shadedConsumerFixture.flatMap { it.archiveFile }).withPropertyName("shadedConsumerFixture")
    doFirst {
        systemProperty("maven.repo.local", resolverTestRepository.singleFile.absolutePath)
        systemProperty("attache.test.shadedJar", shadedConsumerFixture.get().archiveFile.get().asFile.absolutePath)
    }
}
