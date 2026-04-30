package me.whereareiam.attache.plugin.gradle;

import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.gradle.testkit.runner.TaskOutcome.SUCCESS;
import static org.gradle.testkit.runner.TaskOutcome.UP_TO_DATE;
import static org.junit.jupiter.api.Assertions.*;

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
					import me.whereareiam.attache.plugin.gradle.extension.AttacheExtension
					import org.gradle.api.publish.maven.MavenPublication
					
					plugins {
					    `java-library`
					    `maven-publish`
					    id("me.whereareiam.attache")
					}
					
					dependencies {
					    attacheApi(libs.gson)
					    attacheOnly(libs.commonsLang)
					}

					publishing {
					    publications {
					        create<MavenPublication>("mavenJava") {
					            from(components["java"])
					        }
					    }
					}
					
					extensions.configure<AttacheExtension>("attache") {
					    repository("https://repo.example.com/releases")
					
					    library(libs.gson) {
					        transitive.set(true)
					        relocate("com.google.gson", "example.libs.gson")
					    }
					}
					
					tasks.register("verifyAttacheCompileScopes") {
					    doLast {
					        val compileOnlyNames = configurations.compileOnly.get().allDependencies.map { it.name }.toSet()
					        val compileOnlyApiNames = configurations.compileOnlyApi.get().allDependencies.map { it.name }.toSet()
					        check("gson" in compileOnlyNames)
					        check("gson" in compileOnlyApiNames)
					        check("commons-lang3" !in compileOnlyNames)
					        check("commons-lang3" !in compileOnlyApiNames)
					    }
					}
					""");

		BuildResult result = GradleRunner.create()
				.withProjectDir(tempDir.toFile())
				.withPluginClasspath()
				.withArguments("generateAttacheDescriptor", "generatePomFileForMavenJavaPublication", "verifyAttacheCompileScopes")
				.build();

		assertEquals(SUCCESS, result.task(":generateAttacheDescriptor").getOutcome());
		assertEquals(SUCCESS, result.task(":generatePomFileForMavenJavaPublication").getOutcome());
		assertEquals(SUCCESS, result.task(":verifyAttacheCompileScopes").getOutcome());

		Path descriptor = tempDir.resolve("build/generated/resources/attache/META-INF/attache/fixture/attache.json");
		String json = Files.readString(descriptor);
		Path pom = tempDir.resolve("build/publications/mavenJava/pom-default.xml");
		String pomXml = Files.readString(pom);
		assertTrue(Files.exists(descriptor));
		assertTrue(json.contains("\"artifactId\": \"gson\""));
		assertTrue(json.contains("\"artifactId\": \"commons-lang3\""));
		assertTrue(json.contains("\"resolveTransitiveDependencies\": true"));
		assertTrue(json.contains("\"pattern\": \"com.google.gson\""));
		assertTrue(json.contains("\"relocatedPattern\": \"example.libs.gson\""));
		assertTrue(Files.exists(pom));
		assertTrue(pomXml.contains("<artifactId>gson</artifactId>"));
        assertFalse(pomXml.contains("<artifactId>commons-lang3</artifactId>"));
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

	@Test
	void appliesProjectLevelTransitiveDefaultAndAllowsLibraryOverride() throws Exception {
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
					import me.whereareiam.attache.plugin.gradle.extension.AttacheExtension
					
					plugins {
					    `java-library`
					    id("me.whereareiam.attache")
					}
					
					dependencies {
					    attache(libs.gson)
					    attache(libs.commonsLang)
					}
					
					extensions.configure<AttacheExtension>("attache") {
					    transitive.set(true)
					
					    library(libs.commonsLang) {
					        transitive.set(false)
					    }
					}
					""");

		BuildResult result = GradleRunner.create()
				.withProjectDir(tempDir.toFile())
				.withPluginClasspath()
				.withArguments("generateAttacheDescriptor")
				.build();

		assertEquals(SUCCESS, result.task(":generateAttacheDescriptor").getOutcome());

		Path descriptor = tempDir.resolve("build/generated/resources/attache/META-INF/attache/fixture/attache.json");
		String json = Files.readString(descriptor);
		assertTrue(libraryBlock(json, "gson").contains("\"resolveTransitiveDependencies\": true"));
		assertTrue(libraryBlock(json, "commons-lang3").contains("\"resolveTransitiveDependencies\": false"));
	}

	@Test
	void regeneratesDescriptorWhenVersionCatalogDependencyChanges() throws Exception {
		writeFile("settings.gradle.kts", """
				rootProject.name = "fixture"
				
				dependencyResolutionManagement {
				    repositories {
				        mavenCentral()
				    }
				}
				""");
		writeFile("gradle/libs.versions.toml", """
				[versions]
				commonsLang = "3.14.0"
				
				[libraries]
				commonsLang = { group = "org.apache.commons", name = "commons-lang3", version.ref = "commonsLang" }
				""");
		writeFile("build.gradle.kts", """
				plugins {
				    java
				    id("me.whereareiam.attache")
				}
				
				dependencies {
				    attache(libs.commonsLang)
				}
				""");

		BuildResult first = GradleRunner.create()
				.withProjectDir(tempDir.toFile())
				.withPluginClasspath()
				.withArguments("generateAttacheDescriptor")
				.build();

		assertEquals(SUCCESS, first.task(":generateAttacheDescriptor").getOutcome());

		writeFile("gradle/libs.versions.toml", """
				[versions]
				commonsLang = "3.17.0"
				
				[libraries]
				commonsLang = { group = "org.apache.commons", name = "commons-lang3", version.ref = "commonsLang" }
				""");

		BuildResult second = GradleRunner.create()
				.withProjectDir(tempDir.toFile())
				.withPluginClasspath()
				.withArguments("generateAttacheDescriptor")
				.build();

		assertNotEquals(UP_TO_DATE, second.task(":generateAttacheDescriptor").getOutcome());

		Path descriptor = tempDir.resolve("build/generated/resources/attache/META-INF/attache/fixture/attache.json");
		String json = Files.readString(descriptor);
		assertTrue(json.contains("\"version\": \"3.17.0\""));
		assertFalse(json.contains("\"version\": \"3.14.0\""));
	}

	private void writeFile(String relativePath, String content) throws Exception {
		Path file = tempDir.resolve(relativePath);
		Files.createDirectories(file.getParent());
		Files.writeString(file, content);
	}

	private String libraryBlock(String json, String artifactId) {
		String marker = "\"artifactId\": \"" + artifactId + '"';
		int start = json.indexOf(marker);
		assertTrue(start >= 0, "Missing artifact block for " + artifactId);
		int end = json.indexOf("\n    }", start);
		assertTrue(end >= 0, "Missing end of artifact block for " + artifactId);
		return json.substring(start, end);
	}
}
