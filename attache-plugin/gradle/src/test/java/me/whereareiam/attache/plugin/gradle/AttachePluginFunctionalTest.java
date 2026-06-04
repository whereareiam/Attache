package me.whereareiam.attache.plugin.gradle;

import me.whereareiam.attache.Repositories;
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

		Path descriptor = tempDir.resolve("build/generated/resources/attache/META-INF/attache/fixture/attache.xml");
		String xml = Files.readString(descriptor);
		Path pom = tempDir.resolve("build/publications/mavenJava/pom-default.xml");
		String pomXml = Files.readString(pom);
		assertTrue(Files.exists(descriptor));
		assertTrue(xml.contains("artifact-id=\"gson\""));
		assertTrue(xml.contains("artifact-id=\"commons-lang3\""));
		assertTrue(libraryBlock(xml, "gson").contains("resolve-transitive-dependencies=\"true\""));
		assertTrue(xml.contains("pattern=\"com.google.gson\""));
		assertTrue(xml.contains("relocated-pattern=\"example.libs.gson\""));
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

		Path descriptor = tempDir.resolve("build/generated/resources/attache/META-INF/attache/fixture/attache.xml");
		String xml = Files.readString(descriptor);
		assertTrue(libraryBlock(xml, "gson").contains("resolve-transitive-dependencies=\"true\""));
		assertTrue(libraryBlock(xml, "commons-lang3").contains("resolve-transitive-dependencies=\"false\""));
	}

	@Test
	void addsMavenLocalRepositoryToDescriptor() throws Exception {
		Path localRepository = tempDir.resolve("custom-m2/repository");

		writeFile("settings.gradle.kts", """
				rootProject.name = "fixture"
				
				dependencyResolutionManagement {
				    repositories {
				        mavenCentral()
				    }
				
				    versionCatalogs {
				        create("libs") {
				            library("gson", "com.google.code.gson:gson:2.13.2")
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
				}
				
				extensions.configure<AttacheExtension>("attache") {
				    mavenLocal()
				}
				""");

		BuildResult result = GradleRunner.create()
				.withProjectDir(tempDir.toFile())
				.withPluginClasspath()
				.withArguments("generateAttacheDescriptor", "-Dmaven.repo.local=" + localRepository)
				.build();

		assertEquals(SUCCESS, result.task(":generateAttacheDescriptor").getOutcome());

		Path descriptor = tempDir.resolve("build/generated/resources/attache/META-INF/attache/fixture/attache.xml");
		String xml = Files.readString(descriptor);
		assertTrue(xml.contains("<repository>" + Repositories.mavenLocal(localRepository) + "</repository>"));
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

		Path descriptor = tempDir.resolve("build/generated/resources/attache/META-INF/attache/fixture/attache.xml");
		String xml = Files.readString(descriptor);
		assertTrue(xml.contains("version=\"3.17.0\""));
		assertFalse(xml.contains("version=\"3.14.0\""));
	}

	@Test
	void usesNestedProjectDirectoryForDescriptorPathWhenGradlePathIsFlattened() throws Exception {
		writeFile("settings.gradle.kts", """
				rootProject.name = "fixture"
				
				include(":platform-velocity-bootstrap")
				project(":platform-velocity-bootstrap").projectDir = file("platform/velocity/bootstrap")
				
				dependencyResolutionManagement {
				    repositories {
				        mavenCentral()
				    }
				
				    versionCatalogs {
				        create("libs") {
				            library("commonsLang", "org.apache.commons:commons-lang3:3.17.0")
				        }
				    }
				}
				""");
		writeFile("platform/velocity/bootstrap/build.gradle.kts", """
				plugins {
				    java
				    id("me.whereareiam.attache")
				}
				
				dependencies {
				    attache(libs.commonsLang)
				}
				""");

		BuildResult result = GradleRunner.create()
				.withProjectDir(tempDir.toFile())
				.withPluginClasspath()
				.withArguments(":platform-velocity-bootstrap:generateAttacheDescriptor")
				.build();

		assertEquals(SUCCESS, result.task(":platform-velocity-bootstrap:generateAttacheDescriptor").getOutcome());
		assertTrue(Files.exists(
				tempDir.resolve("platform/velocity/bootstrap/build/generated/resources/attache/META-INF/attache/platform/velocity/bootstrap/attache.xml")
		));
		assertFalse(Files.exists(
				tempDir.resolve("platform/velocity/bootstrap/build/generated/resources/attache/META-INF/attache/platform-velocity-bootstrap/attache.xml")
		));
	}

	@Test
	void inheritsRootProjectDefaultsAndAllowsSubprojectOverrides() throws Exception {
		writeFile("settings.gradle.kts", """
				rootProject.name = "fixture"
				include(":module")
				
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
				    id("me.whereareiam.attache")
				}
				
				extensions.configure<AttacheExtension>("attache") {
				    addMavenCentral.set(false)
				    transitive.set(true)
				    repository("https://repo.example.com/shared")
				}
				""");
		writeFile("module/build.gradle.kts", """
				import me.whereareiam.attache.plugin.gradle.extension.AttacheExtension
				
				plugins {
				    java
				    id("me.whereareiam.attache")
				}
				
				dependencies {
				    attache(libs.gson)
				    attache(libs.commonsLang)
				}
				
				extensions.configure<AttacheExtension>("attache") {
				    library(libs.commonsLang) {
				        transitive.set(false)
				    }
				
				    repository("https://repo.example.com/module")
				}
				""");

		BuildResult result = GradleRunner.create()
				.withProjectDir(tempDir.toFile())
				.withPluginClasspath()
				.withArguments(":module:generateAttacheDescriptor")
				.build();

		assertEquals(SUCCESS, result.task(":module:generateAttacheDescriptor").getOutcome());

		Path descriptor = tempDir.resolve("module/build/generated/resources/attache/META-INF/attache/module/attache.xml");
		String xml = Files.readString(descriptor);
		assertTrue(xml.contains("<repository>https://repo.example.com/shared</repository>"));
		assertTrue(xml.contains("<repository>https://repo.example.com/module</repository>"));
		assertTrue(xml.contains("add-maven-central=\"false\""));
		assertFalse(xml.contains("<repository>https://repo1.maven.org/maven2/</repository>"));
		assertTrue(libraryBlock(xml, "gson").contains("resolve-transitive-dependencies=\"true\""));
		assertTrue(libraryBlock(xml, "commons-lang3").contains("resolve-transitive-dependencies=\"false\""));
	}

	private void writeFile(String relativePath, String content) throws Exception {
		Path file = tempDir.resolve(relativePath);
		Files.createDirectories(file.getParent());
		Files.writeString(file, content);
	}

	private String libraryBlock(String xml, String artifactId) {
		String marker = "<library";
		int start = xml.indexOf(marker);
		while (start >= 0) {
			int tagEnd = xml.indexOf('>', start);
			assertTrue(tagEnd >= 0, "Missing end of library start tag for " + artifactId);
			String header = xml.substring(start, tagEnd);
			if (header.contains("artifact-id=\"" + artifactId + '"')) {
				if (header.endsWith("/")) {
					return header;
				}

				int end = xml.indexOf("</library>", tagEnd);
				assertTrue(end >= 0, "Missing end of artifact block for " + artifactId);
				return xml.substring(start, end);
			}
			start = xml.indexOf(marker, tagEnd);
		}

		assertTrue(start >= 0, "Missing artifact block for " + artifactId);
		return "";
	}
}
