package me.whereareiam.attache.platform.standalone;

import me.whereareiam.attache.LoggingHelper;
import me.whereareiam.attache.type.Level;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.assertTrue;

class StandaloneDescriptorAutoloadTest {
	@TempDir
	Path tempDir;

	@Test
	void loadDescriptorsLoadsPackagedDescriptors() throws Exception {
		Path sourceJar = createSourceJar();
		writeDescriptor(sourceJar);

		try (URLClassLoader classLoader = new URLClassLoader(new URL[]{tempDir.toUri().toURL()}, null);
		     StandaloneLibraryManager manager = new StandaloneLibraryManager(
				     new NoopLoggingHelper(),
				     tempDir.resolve("runtime"),
				     "lib",
				     classLoader
		     )) {
			manager.loadDescriptors();

			Path loadedJar = manager.getSaveDirectory()
					.resolve("example/test/alpha/1.0.0/alpha-1.0.0.jar");

			assertTrue(Files.exists(loadedJar));
		}
	}

	private void writeDescriptor(@NotNull Path sourceJar) throws IOException {
		Path descriptor = tempDir.resolve("META-INF/attache/standalone-test/attache.xml");
		Files.createDirectories(descriptor.getParent());
		Files.writeString(descriptor, """
				<?xml version="1.0" encoding="UTF-8"?>
				<attache-descriptor project-path=":standalone-test" project-name="standalone-test" add-maven-central="true">
				  <libraries>
				    <library group-id="example.test" artifact-id="alpha" version="1.0.0">
				      <urls>
				        <url>%s</url>
				      </urls>
				    </library>
				  </libraries>
				</attache-descriptor>
				""".formatted(sourceJar.toUri()), StandardCharsets.UTF_8);
	}

	@NotNull
	private Path createSourceJar() throws IOException {
		Path sourceJar = tempDir.resolve("alpha-source.jar");
		try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(sourceJar))) {
			out.putNextEntry(new java.util.jar.JarEntry("example.txt"));
			out.write("alpha".getBytes(StandardCharsets.UTF_8));
			out.closeEntry();
		}
		return sourceJar;
	}

	private static final class NoopLoggingHelper implements LoggingHelper {
		@Override
		public void log(@NotNull Level level, @NotNull String message) {
		}

		@Override
		public void log(@NotNull Level level, @NotNull String message, @NotNull Throwable throwable) {
		}
	}
}
