package me.whereareiam.attache.common;

import me.whereareiam.attache.LoggingHelper;
import me.whereareiam.attache.common.util.ArtifactIntegrity;
import me.whereareiam.attache.model.LibraryRequest;
import me.whereareiam.attache.model.RelocationRule;
import me.whereareiam.attache.type.Level;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class CacheValidationTest {
	@TempDir Path temporary;

	@Test
	void validatesChecksummedCachedArtifactsBeforeReuse() throws Exception {
		Path source = temporary.resolve("source.jar");
		Files.writeString(source, "verified");
		try (Manager manager = new Manager(temporary, getClass().getClassLoader())) {
			LibraryRequest request = LibraryRequest.builder().groupId("test").artifactId("library").version("1")
					.url(source.toUri().toString()).checksum(ArtifactIntegrity.sha256(source)).build();
			Path cached = manager.downloadLibrary(request);
			Files.writeString(cached, "corrupt");
			assertEquals("verified", Files.readString(manager.downloadLibrary(request)));
			Files.delete(source);
			assertEquals(cached, manager.downloadLibrary(request), "An intact cached artifact needs no download");
		}
	}

	@Test
	void findsStandardMavenMetadataInTheTargetClassLoader() throws Exception {
		Path classes = temporary.resolve("target");
		Path metadata = classes.resolve("META-INF/maven/com.example/library/pom.properties");
		Files.createDirectories(metadata.getParent());
		Files.writeString(metadata, "groupId=com.example\nartifactId=library\nversion=1\n");
		try (URLClassLoader target = new URLClassLoader(new URL[]{classes.toUri().toURL()}, null);
		     Manager manager = new Manager(temporary, target)) {
			LibraryRequest request = LibraryRequest.builder().groupId("com{}example").artifactId("library").version("1").build();
			assertNotNull(manager.findSkipReason(request));
			assertNull(manager.findSkipReason(request.toBuilder().skipIfPresent(false).build()));
			assertNull(manager.findSkipReason(request.toBuilder().relocation(RelocationRule.builder()
					.pattern("com.example").relocatedPattern("other.example").build()).build()));
		}
	}

	@Test
	void rebuildsRelocationsWhenTheSourceChanges() throws Exception {
		Path source = temporary.resolve("source.jar");
		Files.write(source, jar("first"));
		try (Manager manager = new Manager(temporary, getClass().getClassLoader())) {
			manager.addMavenLocal();
			var coordinator = new RelocationCoordinator(manager, temporary);
			var rules = List.of(RelocationRule.builder().pattern("original").relocatedPattern("moved").build());
			Path relocated = coordinator.relocate(source, "relocated.jar", rules);
			assertEquals("first", content(relocated));
			Files.write(source, jar("second"));
			assertEquals("second", content(coordinator.relocate(source, "relocated.jar", rules)));
			var timestamp = Files.getLastModifiedTime(relocated);
			coordinator.relocate(source, "relocated.jar", rules);
			assertEquals(timestamp, Files.getLastModifiedTime(relocated));
			coordinator.close();
		}
	}

	private byte[] jar(String text) throws Exception {
		var bytes = new ByteArrayOutputStream();
		try (var jar = new JarOutputStream(bytes)) {
			jar.putNextEntry(new JarEntry("value.txt"));
			jar.write(text.getBytes(StandardCharsets.UTF_8));
			jar.closeEntry();
		}
		return bytes.toByteArray();
	}

	private String content(Path path) throws Exception {
		try (var jar = new JarFile(path.toFile()); var input = jar.getInputStream(jar.getJarEntry("value.txt"))) {
			return new String(input.readAllBytes(), StandardCharsets.UTF_8);
		}
	}

	private static final class Manager extends BaseLibraryManager {
		private final ClassLoader target;
		Manager(Path directory, ClassLoader target) {
			super(new LoggingHelper() {
				@Override public void log(@NotNull Level level, @NotNull String message) { }
				@Override public void log(@NotNull Level level, @NotNull String message, @NotNull Throwable throwable) { }
			}, directory, "libraries");
			this.target = target;
		}
		@Override protected void addToClasspath(@NotNull Path path) { }
		@Override protected @NotNull ClassLoader getDescriptorClassLoader() { return target; }
	}
}
