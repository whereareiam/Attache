package me.whereareiam.attache.buildlogic

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.security.MessageDigest
import java.util.HexFormat

@CacheableTask
abstract class GenerateResolverMetadata : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val resolverJar: RegularFileProperty

    @get:Input
    abstract val resolverVersion: Property<String>

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun generate() {
        val digest = MessageDigest.getInstance("SHA-256")
        resolverJar.get().asFile.inputStream().use { input ->
            val buffer = ByteArray(8192)
            var count = input.read(buffer)
            while (count != -1) {
                digest.update(buffer, 0, count)
                count = input.read(buffer)
            }
        }

        val checksum = HexFormat.of().formatHex(digest.digest())
        val target = outputDirectory.file("me/whereareiam/attache/launcher/resolver.properties").get().asFile
        target.parentFile.mkdirs()
        target.writeText("version=${resolverVersion.get()}\nsha256=$checksum\n")
    }
}
