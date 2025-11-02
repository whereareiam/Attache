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

