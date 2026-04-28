package me.whereareiam.attache.common.descriptor;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import me.whereareiam.attache.LoggingHelper;
import me.whereareiam.attache.common.BaseLibraryManager;
import me.whereareiam.attache.descriptor.AttacheDescriptorFragment;
import me.whereareiam.attache.descriptor.AttacheDescriptorLibrary;
import me.whereareiam.attache.type.Level;
import me.whereareiam.attache.type.VerbosityMode;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DescriptorLoadingTest {
	private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();

	@TempDir
	Path tempDir;

	@Test
	void loadsMatchingFragmentsAndDeduplicatesEquivalentLibraries() throws Exception {
		AttacheDescriptorFragment common = fragment("common", library("alpha"));
		common.getRepositories().add("https://repo.example.com/releases");

		AttacheDescriptorFragment velocity = fragment("velocity",
				library("alpha"),
				library("beta"));

		writeDescriptor("common", common);
		writeDescriptor("nested/platform/velocity", velocity);

		try (URLClassLoader classLoader = new URLClassLoader(new URL[]{tempDir.toUri().toURL()}, null);
		     TestDescriptorLibraryManager manager = new TestDescriptorLibraryManager(tempDir, classLoader)) {
			manager.loadDescriptors();

			assertEquals(List.of("alpha-1.0.0.jar", "beta-1.0.0.jar"), manager.getLoadedArtifacts());
			assertTrue(manager.getRepositories().contains("https://repo1.maven.org/maven2/"));
			assertTrue(manager.getRepositories().contains("https://repo.example.com/releases/"));
		}
	}

	@Test
	void loadsAllFragmentsWithoutPlatformFiltering() throws Exception {
		writeDescriptor("common", fragment("common", library("alpha")));
		writeDescriptor("other", fragment("other", library("beta")));
		writeDescriptor("nested", fragment("nested", library("gamma")));

		try (URLClassLoader classLoader = new URLClassLoader(new URL[]{tempDir.toUri().toURL()}, null);
		     TestDescriptorLibraryManager manager = new TestDescriptorLibraryManager(tempDir, classLoader)) {
			manager.loadDescriptors();

			assertEquals(Set.of("alpha-1.0.0.jar", "beta-1.0.0.jar", "gamma-1.0.0.jar"), Set.copyOf(manager.getLoadedArtifacts()));
		}
	}

	@Test
	void failsOnConflictingDuplicateDefinitions() throws Exception {
		AttacheDescriptorLibrary alpha = library("alpha");
		AttacheDescriptorLibrary conflicting = library("alpha");
		conflicting.setResolveTransitiveDependencies(true);

		writeDescriptor("first", fragment("first", alpha));
		writeDescriptor("second", fragment("second", conflicting));

		try (URLClassLoader classLoader = new URLClassLoader(new URL[]{tempDir.toUri().toURL()}, null);
		     TestDescriptorLibraryManager manager = new TestDescriptorLibraryManager(tempDir, classLoader)) {
			IllegalStateException exception = assertThrows(IllegalStateException.class, manager::loadDescriptors);
			assertTrue(exception.getMessage().contains("first/attache.json"));
			assertTrue(exception.getMessage().contains("second/attache.json"));
		}
	}

	@Test
	void verboseModeLogsDescriptorLoadStartAndFinish() throws Exception {
		writeDescriptor("common", fragment("common", library("alpha")));
		RecordingLoggingHelper loggingHelper = new RecordingLoggingHelper();

		try (URLClassLoader classLoader = new URLClassLoader(new URL[]{tempDir.toUri().toURL()}, null);
		     TestDescriptorLibraryManager manager = new TestDescriptorLibraryManager(tempDir, classLoader, loggingHelper)) {
			manager.setVerbosityMode(VerbosityMode.VERBOSE);
			manager.loadDescriptors();

			assertTrue(loggingHelper.contains("Loading declared libraries"));
			assertTrue(loggingHelper.contains("Finished loading 1 libraries"));
		}
	}

	@Test
	void summaryModeLogsStartWithoutDuplicateFinish() throws Exception {
		writeDescriptor("common", fragment("common", library("alpha")));
		RecordingLoggingHelper loggingHelper = new RecordingLoggingHelper();

		try (URLClassLoader classLoader = new URLClassLoader(new URL[]{tempDir.toUri().toURL()}, null);
		     TestDescriptorLibraryManager manager = new TestDescriptorLibraryManager(tempDir, classLoader, loggingHelper)) {
			manager.setVerbosityMode(VerbosityMode.SUMMARY);
			manager.loadDescriptors();

			assertTrue(loggingHelper.contains("Loading declared libraries"));
			assertFalse(loggingHelper.contains("Finished loading 1 libraries"));
			assertTrue(loggingHelper.contains("Loaded 1 libraries successfully"));
		}
	}

	private void writeDescriptor(@NotNull String relativeDir, @NotNull AttacheDescriptorFragment fragment) throws Exception {
		Path file = tempDir.resolve("META-INF/attache").resolve(relativeDir).resolve("attache.json");
		Files.createDirectories(file.getParent());
		Files.writeString(file, GSON.toJson(fragment), StandardCharsets.UTF_8);
	}

	@NotNull
	private AttacheDescriptorFragment fragment(
			@NotNull String projectName,
			@NotNull AttacheDescriptorLibrary... libraries
	) {
		AttacheDescriptorFragment fragment = new AttacheDescriptorFragment();
		fragment.setProjectName(projectName);
		fragment.setProjectPath(':' + projectName);
		for (AttacheDescriptorLibrary library : libraries) {
			fragment.getLibraries().add(library);
		}
		return fragment;
	}

	@NotNull
	private AttacheDescriptorLibrary library(@NotNull String artifactId) {
		AttacheDescriptorLibrary library = new AttacheDescriptorLibrary();
		library.setGroupId("example.test");
		library.setArtifactId(artifactId);
		library.setVersion("1.0.0");
		library.getUrls().add("https://example.invalid/" + artifactId + "-1.0.0.jar");
		return library;
	}

	private static final class TestDescriptorLibraryManager extends BaseLibraryManager {
		private final URLClassLoader classLoader;
		private final List<String> loadedArtifacts = new ArrayList<>();

		private TestDescriptorLibraryManager(@NotNull Path tempDir, @NotNull URLClassLoader classLoader) {
			this(tempDir, classLoader, new NoopLoggingHelper());
		}

		private TestDescriptorLibraryManager(
				@NotNull Path tempDir,
				@NotNull URLClassLoader classLoader,
				@NotNull LoggingHelper loggingHelper
		) {
			super(loggingHelper, tempDir, "lib");
			this.classLoader = classLoader;
		}

		@Override
		protected void addToClasspath(@NotNull Path file) {
			loadedArtifacts.add(file.getFileName().toString());
		}

		@Override
		protected @NotNull DownloadAttempt downloadLibraryAttempt(@NotNull String url) {
			return DownloadAttempt.success(url.getBytes(StandardCharsets.UTF_8));
		}

		@Override
		protected @NotNull ClassLoader getDescriptorClassLoader() {
			return classLoader;
		}

		@NotNull
		private List<String> getLoadedArtifacts() {
			return loadedArtifacts;
		}
	}

	private static final class NoopLoggingHelper implements LoggingHelper {
		@Override
		public void log(@NotNull Level level, @NotNull String message) {
		}

		@Override
		public void log(@NotNull Level level, @NotNull String message, @NotNull Throwable throwable) {
		}
	}

	private static final class RecordingLoggingHelper implements LoggingHelper {
		private final List<LogEntry> entries = new CopyOnWriteArrayList<>();

		@Override
		public void log(@NotNull Level level, @NotNull String message) {
			entries.add(new LogEntry(level, message));
		}

		@Override
		public void log(@NotNull Level level, @NotNull String message, @NotNull Throwable throwable) {
			entries.add(new LogEntry(level, message));
		}

		private boolean contains(@NotNull String fragment) {
			return entries.stream().anyMatch(entry -> entry.level == Level.INFO && entry.message.contains(fragment));
		}

		private record LogEntry(@NotNull Level level, @NotNull String message) {
		}
	}
}
