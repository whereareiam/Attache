package me.whereareiam.attache.plugin.gradle;

import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.gradle.testkit.runner.TaskOutcome.SUCCESS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AttachePluginFunctionalTest {
	@TempDir
	Path tempDir;

	@Test
	void generatesDescriptorFromVersionCatalogAndAttacheConfigurations() throws Exception {
		writeFile("settings.gradle.kts", """
				rootProject.name = "fixture"
				
				dependencyResolutionManagement {
				    repositories {
				        mavenCentral()
				    }
				
				    versionCatalogs {
				        create("libs") {
				            library("gson", "com.google.code.gson:gson:2.13.2")
				            library("commonsLang", "org.apache.commons:commons-lang3:3.17.0")
				        }
				    }
				}
				""");

		writeFile("build.gradle.kts", """
					import me.whereareiam.attache.plugin.gradle.extension.AttacheMetadataExtension
					
					plugins {
					    java
					    id("me.whereareiam.attache")
					}
					
					dependencies {
					    attache(libs.gson)
					    attacheOnly(libs.commonsLang)
					}
					
					extensions.configure<AttacheMetadataExtension>("attacheMetadata") {
					    repository("https://repo.example.com/releases")
					
					    library(libs.gson) {
					        transitive.set(true)
					        relocate("com.google.gson", "example.libs.gson")
					    }
					}
					
					tasks.register("verifyAttacheCompileScopes") {
					    doLast {
					        val compileOnlyNames = configurations.compileOnly.get().allDependencies.map { it.name }.toSet()
					        check("gson" in compileOnlyNames)
					        check("commons-lang3" !in compileOnlyNames)
					    }
					}
					""");

		BuildResult result = GradleRunner.create()
				.withProjectDir(tempDir.toFile())
				.withPluginClasspath()
				.withArguments("generateAttacheDescriptor", "verifyAttacheCompileScopes")
				.build();

		assertEquals(SUCCESS, result.task(":generateAttacheDescriptor").getOutcome());
		assertEquals(SUCCESS, result.task(":verifyAttacheCompileScopes").getOutcome());

		Path descriptor = tempDir.resolve("build/generated/resources/attache/META-INF/attache/fixture/attache.json");
		String json = Files.readString(descriptor);
		assertTrue(Files.exists(descriptor));
		assertTrue(json.contains("\"artifactId\": \"gson\""));
		assertTrue(json.contains("\"artifactId\": \"commons-lang3\""));
		assertTrue(json.contains("\"resolveTransitiveDependencies\": true"));
		assertTrue(json.contains("\"pattern\": \"com.google.gson\""));
		assertTrue(json.contains("\"relocatedPattern\": \"example.libs.gson\""));
	}

	@Test
	void rejectsUnsupportedProjectDependencies() throws Exception {
		writeFile("settings.gradle.kts", """
				rootProject.name = "fixture"
				include(":child")
				""");
		writeFile("child/build.gradle.kts", "");
		writeFile("build.gradle.kts", """
				plugins {
				    java
				    id("me.whereareiam.attache")
				}
				
				dependencies {
				    attache(project(":child"))
				}
				""");

		BuildResult result = GradleRunner.create()
				.withProjectDir(tempDir.toFile())
				.withPluginClasspath()
				.withArguments("generateAttacheDescriptor")
				.buildAndFail();

		assertTrue(result.getOutput().contains("Attache configuration does not support project dependencies"));
	}

	private void writeFile(String relativePath, String content) throws Exception {
		Path file = tempDir.resolve(relativePath);
		Files.createDirectories(file.getParent());
		Files.writeString(file, content);
	}
}
