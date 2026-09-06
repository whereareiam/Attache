package me.whereareiam.attache.launcher;

import me.whereareiam.attache.LoggingHelper;
import me.whereareiam.attache.common.BaseLibraryManager;
import me.whereareiam.attache.model.LibraryRequest;
import me.whereareiam.attache.type.Level;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Consumer-shaped fixture relocated together with Attache's API and common implementation.
 */
public final class ShadedResolverProbe {
	public static String run(Path directory, String repository) {
		List<String> loaded = new ArrayList<>();
		try (BaseLibraryManager manager = new BaseLibraryManager(new LoggingHelper() {
			@Override public void log(@NotNull Level level, @NotNull String message) { }
			@Override public void log(@NotNull Level level, @NotNull String message, @NotNull Throwable failure) { }
		}, directory, "libraries", new AttacheLauncher()) {
			@Override protected void addToClasspath(@NotNull Path file) { loaded.add(file.getFileName().toString()); }
		}) {
			manager.addMavenLocal();
			manager.addMavenCentral();
			manager.loadLibrary(LibraryRequest.builder().groupId("test.cache").artifactId("first").version("1.0")
					.repository(repository).resolveTransitiveDependencies(true).skipIfPresent(false).build());
		}
		return String.join(",", loaded);
	}
}
