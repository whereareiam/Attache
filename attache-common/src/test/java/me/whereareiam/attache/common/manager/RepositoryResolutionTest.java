package me.whereareiam.attache.common.manager;

import me.whereareiam.attache.LoggingHelper;
import me.whereareiam.attache.Repositories;
import me.whereareiam.attache.common.BaseLibraryManager;
import me.whereareiam.attache.model.LibraryRequest;
import me.whereareiam.attache.type.Level;
import me.whereareiam.attache.type.ResolutionMode;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for repository resolution logic in {@link BaseLibraryManager}.
 */
class RepositoryResolutionTest {
	private TestLibraryManager libraryManager;

	@BeforeEach
	void setUp() {
		libraryManager = new TestLibraryManager();
	}

	@Test
	void testDefaultModeResolution() {
		libraryManager.addRepository("https://global-repo.com/");
		libraryManager.setRepositoryResolutionMode(ResolutionMode.DEFAULT);

		LibraryRequest library = LibraryRequest.builder()
				.groupId("com.example")
				.artifactId("test")
				.version("1.0.0")
				.repositories(java.util.Collections.singleton("https://library-repo.com/"))
				.fallbackRepositories(java.util.Collections.singleton("https://fallback-repo.com/"))
				.build();

		Collection<String> repos = libraryManager.resolveRepositories(library);
		List<String> repoList = new ArrayList<>(repos);

		assertEquals(3, repoList.size());
		// DEFAULT: library repos -> global repos -> fallback repos
		assertEquals("https://library-repo.com/", repoList.get(0));
		assertEquals("https://global-repo.com/", repoList.get(1));
		assertEquals("https://fallback-repo.com/", repoList.get(2));
	}

	@Test
	void testGlobalFirstModeResolution() {
		libraryManager.addRepository("https://global-repo.com/");
		libraryManager.setRepositoryResolutionMode(ResolutionMode.GLOBAL_FIRST);

		LibraryRequest library = LibraryRequest.builder()
				.groupId("com.example")
				.artifactId("test")
				.version("1.0.0")
				.repositories(java.util.Collections.singleton("https://library-repo.com/"))
				.fallbackRepositories(java.util.Collections.singleton("https://fallback-repo.com/"))
				.build();

		Collection<String> repos = libraryManager.resolveRepositories(library);
		List<String> repoList = new ArrayList<>(repos);

		assertEquals(3, repoList.size());
		// GLOBAL_FIRST: global repos -> library repos -> fallback repos
		assertEquals("https://global-repo.com/", repoList.get(0));
		assertEquals("https://library-repo.com/", repoList.get(1));
		assertEquals("https://fallback-repo.com/", repoList.get(2));
	}

	@Test
	void testLibraryFirstModeResolution() {
		libraryManager.addRepository("https://global-repo.com/");
		libraryManager.setRepositoryResolutionMode(ResolutionMode.LIBRARY_FIRST);

		LibraryRequest library = LibraryRequest.builder()
				.groupId("com.example")
				.artifactId("test")
				.version("1.0.0")
				.repositories(java.util.Collections.singleton("https://library-repo.com/"))
				.fallbackRepositories(java.util.Collections.singleton("https://fallback-repo.com/"))
				.build();

		Collection<String> repos = libraryManager.resolveRepositories(library);
		List<String> repoList = new ArrayList<>(repos);

		assertEquals(3, repoList.size());
		// LIBRARY_FIRST: library repos -> fallback repos -> global repos
		assertEquals("https://library-repo.com/", repoList.get(0));
		assertEquals("https://fallback-repo.com/", repoList.get(1));
		assertEquals("https://global-repo.com/", repoList.get(2));
	}

	@Test
	void testRepositoryUrlNormalization() {
		// Without trailing slash
		libraryManager.addRepository("https://repo.example.com");

		// Should be normalized with trailing slash
		Collection<String> repos = libraryManager.getRepositories();
		assertTrue(repos.contains("https://repo.example.com/"));
		assertFalse(repos.contains("https://repo.example.com"));
	}

	@Test
	void testMultipleGlobalRepositories() {
		libraryManager.addRepository("https://repo1.com/");
		libraryManager.addRepository("https://repo2.com/");
		libraryManager.addRepository("https://repo3.com/");

		Collection<String> repos = libraryManager.getRepositories();
		assertEquals(3, repos.size());
		assertTrue(repos.contains("https://repo1.com/"));
		assertTrue(repos.contains("https://repo2.com/"));
		assertTrue(repos.contains("https://repo3.com/"));
	}

	@Test
	void testAddMavenCentral() {
		libraryManager.addMavenCentral();

		Collection<String> repos = libraryManager.getRepositories();
		assertTrue(repos.contains(Repositories.MAVEN_CENTRAL));
	}

	@Test
	void testAddSonatype() {
		libraryManager.addSonatype();

		Collection<String> repos = libraryManager.getRepositories();
		assertTrue(repos.contains(Repositories.SONATYPE));
	}

	@Test
	void testAddJitPack() {
		libraryManager.addJitPack();

		Collection<String> repos = libraryManager.getRepositories();
		assertTrue(repos.contains(Repositories.JITPACK));
	}

	@Test
	void testResolveLibraryUrls() {
		libraryManager.addMavenCentral();

		LibraryRequest library = LibraryRequest.builder()
				.groupId("com.example")
				.artifactId("test-lib")
				.version("1.0.0")
				.build();

		Collection<String> urls = libraryManager.resolveLibrary(library);

		assertFalse(urls.isEmpty());
		assertTrue(urls.stream().anyMatch(url ->
				url.contains("com/example/test-lib/1.0.0/test-lib-1.0.0.jar")));
	}

	@Test
	void testResolveLibraryWithDirectUrl() {
		LibraryRequest library = LibraryRequest.builder()
				.groupId("com.example")
				.artifactId("test-lib")
				.version("1.0.0")
				.urls(java.util.Collections.singleton("https://direct-download.com/test-lib.jar"))
				.build();

		Collection<String> urls = libraryManager.resolveLibrary(library);

		assertTrue(urls.contains("https://direct-download.com/test-lib.jar"));
	}

	/**
	 * Test implementation of BaseLibraryManager for testing purposes.
	 */
	private static class TestLibraryManager extends BaseLibraryManager {
		public TestLibraryManager() {
			super(new TestLoggingHelper(), Paths.get(System.getProperty("java.io.tmpdir")), "attache-test");
		}

		@Override
		protected void addToClasspath(@NotNull Path file) {
			// No-op for testing
		}

		// Expose protected methods for testing
		@Override
		public Collection<String> resolveRepositories(@NotNull LibraryRequest library) {
			return super.resolveRepositories(library);
		}

		@Override
		public Collection<String> resolveLibrary(@NotNull LibraryRequest library) {
			return super.resolveLibrary(library);
		}
	}

	/**
	 * Simple log adapter for testing.
	 */
	private static class TestLoggingHelper implements LoggingHelper {
		@Override
		public void log(@NotNull Level level, @NotNull String message) {
			// No-op for testing
		}

		@Override
		public void log(@NotNull Level level, @NotNull String message, @NotNull Throwable throwable) {
			// No-op for testing
		}
	}
}

