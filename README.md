# Attache

A runtime dependency management library for Java applications, particularly suited for Minecraft server plugins and
standalone applications.

## Overview

Attache allows you to download, cache, relocate, and load dependencies at runtime. This is particularly useful for:

- Reducing plugin file sizes by not bundling dependencies
- Avoiding conflicts between different versions of the same library
- Managing dependencies dynamically

## Features

- **Runtime Dependency Loading**: Download and load Maven artifacts at runtime
- **Relocation Support**: Relocate packages to avoid conflicts using jar-relocator
- **Multiple Platforms**: Support for standalone Java applications and Paper plugins
- **Repository Management**: Support for Maven Central, custom repositories, and more
- **Isolated Class Loading**: Load libraries in isolated class loaders when needed
- **Checksum Verification**: Verify downloaded libraries with SHA-256 checksums

## Installation

### Gradle

Add the repository:

```gradle
repositories {
    maven("https://maven.whereareiam.me/release")
}
```

Add the dependency for your platform:

**For Standalone Applications:**

```gradle
dependencies {
    implementation("me.whereareiam:attache-standalone:VERSION")
}
```

**For Spring Boot (auto-configures a StandaloneLibraryManager):**

```gradle
dependencies {
	implementation("me.whereareiam:attache-spring:VERSION")
}
```

**For Paper Plugins:**

```gradle
dependencies {
    implementation("me.whereareiam:attache-paper:VERSION")
}
```

**Remember to shade and relocate Attache to avoid conflicts:**

```gradle
plugins {
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

shadowJar {
    relocate("me.whereareiam.attache", "your.package.libs.attache")
}
```

### Gradle Plugin

Attache can also generate runtime descriptor fragments directly from your build.

```gradle
plugins {
    id("me.whereareiam.attache") version "VERSION"
}
```

Declare Attache-managed libraries through dependency buckets:

```kotlin
dependencies {
    attache(libs.guice)
    attache(libs.configura)
    attacheOnly(libs.jedis)
}
```

Use the `attacheMetadata {}` block only for Attache-specific metadata:

```kotlin
attacheMetadata {
    repository("https://maven.whereareiam.me/release")
    repository("https://maven.whereareiam.me/development")

    library(libs.guice) {
        transitive.set(true)
        relocate("com{}google{}inject", "me.whereareiam.identica.library.guice")
        relocate("com{}google{}common", "me.whereareiam.identica.library.guava")
    }
}
```

The plugin writes one descriptor fragment per Gradle project to:

```text
META-INF/attache/<project-path>/attache.json
```

Examples:

```text
project(":identica-common")
-> META-INF/attache/identica-common/attache.json

project(":identica-provider:provider-premium:premium")
-> META-INF/attache/identica-provider/provider-premium/premium/attache.json
```

At runtime, Attache managers automatically scan `META-INF/attache/**/attache.json`, merge all discovered fragments, and load the declared libraries during construction.

## Usage

### Basic Example (Standalone)

```java
import me.whereareiam.attache.model.Library;
import me.whereareiam.attache.Repositories;
import me.whereareiam.attache.platform.standalone.StandaloneLibraryManager;
import me.whereareiam.attache.common.logging.adapter.JDKLoggingHelper;

import java.nio.file.Paths;
import java.util.logging.Logger;

public class Example {
	public static void main(String[] args) {
		// Create library manager
		StandaloneLibraryManager libraryManager = new StandaloneLibraryManager(
				new JDKLogAdapter(Logger.getLogger("Attache")),
				Paths.get(".")
		);

		// Add Maven Central
		libraryManager.addMavenCentral();

		// Define and load a library
		Library library = Library.builder()
				.groupId("com{}google{}code{}gson")  // "{}" is replaced with "."
				.artifactId("gson")
				.version("2.10.1")
				.relocate("com{}google{}gson", "your{}package{}libs{}gson")
				.build();

		// Load the library
		libraryManager.loadLibrary(library);
	}
}
```

### Paper Plugin Example

```java
import me.whereareiam.attache.model.Library;
import me.whereareiam.attache.platform.paper.PaperLibraryManager;
import org.bukkit.plugin.java.JavaPlugin;

public class MyPlugin extends JavaPlugin {
	@Override
	public void onEnable() {
		// Create library manager
		PaperLibraryManager libraryManager = new PaperLibraryManager(this);

		// Add repositories
		libraryManager.addMavenCentral();

		// Load dependencies
		Library library = Library.builder()
				.groupId("org{}slf4j")
				.artifactId("slf4j-api")
				.version("2.0.9")
				.relocate("org{}slf4j", "your{}plugin{}libs{}slf4j")
				.checksum("7a9b7c9d2c1a0e8a4b7f1c9d2e3f4a5b6c7d8e9f0a1b2c3d4e5f6a7b8c9d0e1f")
				.build();

		libraryManager.loadLibrary(library);
	}
}
```

### Zero-Config Runtime Loading

When your jar contains Gradle-generated Attache descriptors, creating a manager is enough:

```java
VelocityLibraryManager libraryManager = new VelocityLibraryManager(proxyServer, pluginContainer, logger, dataPath, ".libraries");
```

By default, the manager will:

- discover all `META-INF/attache/**/attache.json` fragments on the classpath
- merge repositories and library definitions
- download and load the libraries automatically

### Spring Boot Example (auto-configuration)

Configure in `application.yml`:

```yaml
attache:
  library-path: ".libraries" # optional; defaults to .libraries relative to working dir
  repositories:
    - https://repo.maven.apache.org/maven2/
  libraries:
    - groupId: org{}slf4j
      artifactId: slf4j-api
      version: 2.0.16
      isolated: true
```

Inject the managed `StandaloneLibraryManager` to add more libraries at runtime:

```java
import me.whereareiam.attache.model.Library;
import me.whereareiam.attache.platform.standalone.StandaloneLibraryManager;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
class ExtraLibrariesLoader {
	private final StandaloneLibraryManager attache;

	ExtraLibrariesLoader(StandaloneLibraryManager attache) {
		this.attache = attache;
	}

	@EventListener(ApplicationReadyEvent.class)
	public void loadExtra() {
		attache.loadLibrary(
				Library.builder()
						.groupId("org{}apache{}commons")
						.artifactId("commons-lang3")
						.version("3.14.0")
						.build()
		);
	}
}
```

### Advanced Features

#### Custom Repositories

```java
libraryManager.addRepository("https://repo.example.com/maven/");
```

#### Isolated Class Loading

```java
Library library = Library.builder()
		.groupId("com{}example")
		.artifactId("library")
		.version("1.0.0")
		.isolatedLoad(true)
		.loaderId("my-isolated-loader")
		.build();
```

#### Fallback Repositories

```java
Library library = Library.builder()
		.groupId("com{}example")
		.artifactId("library")
		.version("1.0.0")
		.repository("https://primary-repo.com/")
		.fallbackRepository(Repositories.MAVEN_CENTRAL)
		.build();
```

## Credits

**Attache is based on [Libby](https://github.com/AlessioDP/libby)** by AlessioDP and Byteflux.

We created Attache because Libby is no longer actively maintained, and we needed a maintained version with
customizations. Special thanks to:

- **[AlessioDP](https://github.com/AlessioDP)** and **[Byteflux](https://github.com/Byteflux)** for creating Libby
- **[Luck](https://github.com/lucko)** for [LuckPerms](https://github.com/lucko/LuckPerms)
  and [jar-relocator](https://github.com/lucko/jar-relocator), which inspired the dependency management approach

## License

Attache is licensed under the MIT License, maintaining compatibility with the original Libby license.

## Building

```bash
./gradlew build
```

## Contributing

Contributions are welcome! Please feel free to submit a Pull Request.
