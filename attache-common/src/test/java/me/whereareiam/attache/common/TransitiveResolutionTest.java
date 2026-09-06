package me.whereareiam.attache.common;

import me.whereareiam.attache.LoggingHelper;
import me.whereareiam.attache.model.LibraryRequest;
import me.whereareiam.attache.resolution.DependencyResolver;
import me.whereareiam.attache.resolution.DependencyResolverFactory;
import me.whereareiam.attache.resolution.model.ResolvedArtifact;
import me.whereareiam.attache.type.Level;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class TransitiveResolutionTest {
	@TempDir Path directory;

	@Test
	void createsResolverLazilyAndMapsItsResultsThroughTheApi() {
		AtomicInteger creations = new AtomicInteger();
		FakeResolver resolver = new FakeResolver(directory.resolve("dependency.jar"));
		try (Manager manager = new Manager(directory, owner -> {
			creations.incrementAndGet();
			assertEquals(directory.resolve("libraries"), owner.getSaveDirectory());
			return resolver;
		})) {
			assertEquals(0, creations.get());
			manager.addRepository("https://example.invalid/");
			LibraryRequest root = LibraryRequest.builder()
					.groupId("test{}root")
					.artifactId("root")
					.version("1")
					.isolated(true)
					.loader("feature")
					.build();

			manager.resolve(root);
			manager.resolve(root);
			assertEquals(1, creations.get());
			assertEquals("test.root", resolver.request.getGroupId());
			assertEquals(List.of("https://example.invalid/"), List.copyOf(resolver.repositories));
			LibraryRequest mapped = manager.loaded.get(0);
			assertEquals("1-SNAPSHOT", mapped.getVersion());
			assertEquals("tests", mapped.getClassifier());
			assertEquals(List.of(resolver.file.toUri().toString()), List.copyOf(mapped.getUrls()));
			assertTrue(mapped.isIsolated());
			assertEquals("feature", mapped.getLoader());
			assertFalse(mapped.isResolveTransitiveDependencies());
			assertFalse(resolver.closed);
		}
		assertTrue(resolver.closed);
	}

	@Test
	void closingUnusedManagerDoesNotInitializeResolver() {
		AtomicInteger creations = new AtomicInteger();
		try (Manager ignored = new Manager(directory, owner -> {
			creations.incrementAndGet();
			return new FakeResolver(directory.resolve("dependency.jar"));
		})) { }
		assertEquals(0, creations.get());
	}

	private static final class Manager extends BaseLibraryManager {
		private final List<LibraryRequest> loaded = new ArrayList<>();

		Manager(Path directory, DependencyResolverFactory factory) {
			super(new LoggingHelper() {
				@Override public void log(@NotNull Level level, @NotNull String message) { }
				@Override public void log(@NotNull Level level, @NotNull String message, @NotNull Throwable failure) { }
			}, directory, "libraries", factory);
		}

		void resolve(LibraryRequest request) { resolveTransitiveLibraries(request); }
		@Override protected void addToClasspath(@NotNull Path file) { }
		@Override public <T> void loadLibrary(@NotNull T request) { loaded.add(adaptLibrary(request)); }
	}

	private static final class FakeResolver implements DependencyResolver {
		private final Path file;
		private LibraryRequest request;
		private Collection<String> repositories;
		private boolean closed;

		FakeResolver(Path file) { this.file = file; }

		@Override
		public @NotNull Collection<ResolvedArtifact> resolve(
				@NotNull LibraryRequest request,
				@NotNull Collection<String> repositories
		) {
			this.request = request;
			this.repositories = repositories;

			return List.of(ResolvedArtifact.builder()
					.groupId("test")
					.artifactId("dependency")
					.version("1-20260906.120000-1")
					.baseVersion("1-SNAPSHOT")
					.classifier("tests")
					.file(file)
					.build());
		}

		@Override public void close() { closed = true; }
	}
}
