import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import me.whereareiam.attache.buildlogic.GenerateResolverMetadata

plugins {
    id("bundle")
}

description = "Isolated Maven dependency resolver owned by Attache"

dependencies {
    implementation(libs.maven.resolver.impl)
    implementation(libs.maven.resolver.supplier)
    implementation(libs.resolver.slf4j.nop)

    compileOnly(projects.attacheApi)

    testImplementation(projects.attacheApi)
}

val resolverJar = tasks.named<ShadowJar>("shadowJar").flatMap { it.archiveFile }
val generateResolverMetadata by tasks.registering(GenerateResolverMetadata::class) {
    resolverJar.set(project.tasks.named<ShadowJar>("shadowJar").flatMap { it.archiveFile })
    resolverVersion.set(project.version.toString())
    outputDirectory.set(layout.buildDirectory.dir("generated/resolver-metadata"))
}
val resolverMetadata by configurations.creating {
    isCanBeConsumed = true
    isCanBeResolved = false
}
artifacts { add(resolverMetadata.name, generateResolverMetadata.flatMap { it.outputDirectory }) }

val relocationFixtures by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
}
dependencies { add(relocationFixtures.name, libs.jar.relocator) }

val fixturePaths = provider {
    relocationFixtures.resolvedConfiguration.resolvedArtifacts.associate { artifact ->
        val id = artifact.moduleVersion.id
        artifact.file.name to "${id.group.replace('.', '/')}/${id.name}/${id.version}/${artifact.file.name}"
    }
}
val prepareResolverRepository by tasks.registering(Sync::class) {
    into(layout.buildDirectory.dir("test-resolver-repository"))
    from(resolverJar) { into("me/whereareiam/attache-resolver/${project.version}") }
    from(relocationFixtures) {
        eachFile { relativePath = RelativePath(true, *fixturePaths.get().getValue(name).split('/').toTypedArray()) }
    }
}
val resolverTestRepository by configurations.creating {
    isCanBeConsumed = true
    isCanBeResolved = false
}
artifacts {
    add(resolverTestRepository.name, prepareResolverRepository.map { it.destinationDir }) {
        builtBy(prepareResolverRepository)
    }
}
