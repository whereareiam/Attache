package me.whereareiam.attache.common.classloader;

import me.whereareiam.attache.LoggingHelper;
import me.whereareiam.attache.common.BaseLibraryManager;
import me.whereareiam.attache.model.LibraryRequest;
import me.whereareiam.attache.type.Level;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for {@link IsolatedClassLoader} functionality.
 * These tests require network access to download test libraries.
 */
class IsolatedClassLoaderTest {
	@TempDir
	Path tempDir;

	private TestLibraryManager libraryManager;

	// Test library - Apache Commons Text (small and stable)
	private static final String TEST_GROUP_ID = "org.apache.commons";
	private static final String TEST_ARTIFACT_ID = "commons-text";
	private static final String TEST_VERSION = "1.10.0";
	private static final String TEST_CLASS = "org.apache.commons.text.StringEscapeUtils";

	@BeforeEach
	void setUp() {
		libraryManager = new TestLibraryManager(tempDir);
		libraryManager.addMavenCentral();
	}

	@AfterEach
	void tearDown() throws Exception {
		// Close all isolated classloaders to release file locks on Windows
		if (libraryManager != null) libraryManager.closeAllClassLoaders();
	}

	@Test
	void testIsolatedClassLoaderBasic() {
		IsolatedClassLoader classLoader = new IsolatedClassLoader();

		assertNotNull(classLoader);
		assertNotNull(classLoader.getParent());

		// Should not be able to load our test class initially
		assertThrows(ClassNotFoundException.class, () -> classLoader.loadClass(TEST_CLASS));
	}

	@Test
	void testLoadLibraryInIsolatedClassLoader() throws Exception {
		LibraryRequest library = LibraryRequest.builder()
				.groupId(TEST_GROUP_ID)
				.artifactId(TEST_ARTIFACT_ID)
				.version(TEST_VERSION)
				.isolated(true)
				.build();

		// Load library into isolated classloader
		libraryManager.loadLibrary(library);

		// Get the global isolated classloader
		IsolatedClassLoader isolatedClassLoader = libraryManager.getGlobalIsolatedClassLoader();
		assertNotNull(isolatedClassLoader);

		// Should be able to load the class from isolated classloader
		Class<?> loadedClass = isolatedClassLoader.loadClass(TEST_CLASS);
		assertNotNull(loadedClass);
		assertEquals(TEST_CLASS, loadedClass.getName());

		// Should NOT be able to load from system classloader
		assertThrows(ClassNotFoundException.class, () ->
				ClassLoader.getSystemClassLoader().loadClass(TEST_CLASS));

		// Close classloader BEFORE test ends to release file locks
		libraryManager.closeAllClassLoaders();
	}

	@Test
	void testIsolatedClassLoaderWithCustomId() throws Exception {
		String loaderId = "test-loader-id";

		LibraryRequest library = LibraryRequest.builder()
				.groupId(TEST_GROUP_ID)
				.artifactId(TEST_ARTIFACT_ID)
				.version(TEST_VERSION)
				.isolated(true)
				.loader(loaderId)
				.build();

		libraryManager.loadLibrary(library);

		// Get the custom isolated classloader by ID
		IsolatedClassLoader customClassLoader = libraryManager.getIsolatedClassLoaderById(loaderId);
		assertNotNull(customClassLoader);

		// Should be able to load the class from custom isolated classloader
		Class<?> loadedClass = customClassLoader.loadClass(TEST_CLASS);
		assertNotNull(loadedClass);
		assertEquals(TEST_CLASS, loadedClass.getName());

		// Global isolated classloader should NOT have this library
		IsolatedClassLoader globalClassLoader = libraryManager.getGlobalIsolatedClassLoader();
		assertThrows(ClassNotFoundException.class, () ->
				globalClassLoader.loadClass(TEST_CLASS));

		// Close classloader BEFORE test ends to release file locks
		libraryManager.closeAllClassLoaders();
	}

	@Test
	void testMultipleIsolatedClassLoadersWithDifferentIds() throws Exception {
		String loaderId1 = "loader-1";
		String loaderId2 = "loader-2";

		LibraryRequest library1 = LibraryRequest.builder()
				.groupId(TEST_GROUP_ID)
				.artifactId(TEST_ARTIFACT_ID)
				.version(TEST_VERSION)
				.isolated(true)
				.loader(loaderId1)
				.build();

		LibraryRequest library2 = LibraryRequest.builder()
				.groupId(TEST_GROUP_ID)
				.artifactId(TEST_ARTIFACT_ID)
				.version(TEST_VERSION)
				.isolated(true)
				.loader(loaderId2)
				.build();

		libraryManager.loadLibrary(library1);
		libraryManager.loadLibrary(library2);

		// Get both classloaders
		IsolatedClassLoader classLoader1 = libraryManager.getIsolatedClassLoaderById(loaderId1);
		IsolatedClassLoader classLoader2 = libraryManager.getIsolatedClassLoaderById(loaderId2);

		assertNotNull(classLoader1);
		assertNotNull(classLoader2);
		assertNotSame(classLoader1, classLoader2, "Different IDs should create different classloaders");

		// Both should be able to load the class
		Class<?> class1 = classLoader1.loadClass(TEST_CLASS);
		Class<?> class2 = classLoader2.loadClass(TEST_CLASS);

		assertNotNull(class1);
		assertNotNull(class2);

		// Classes loaded from different classloaders should be different instances
		assertNotSame(class1, class2);

		// Close classloaders BEFORE test ends to release file locks
		libraryManager.closeAllClassLoaders();
	}

	@Test
	void testGlobalIsolatedClassLoader() throws Exception {
		LibraryRequest library = LibraryRequest.builder()
				.groupId(TEST_GROUP_ID)
				.artifactId(TEST_ARTIFACT_ID)
				.version(TEST_VERSION)
				.isolated(true)
				// No loaderId specified - should use global isolated classloader
				.build();

		libraryManager.loadLibrary(library);

		IsolatedClassLoader globalClassLoader = libraryManager.getGlobalIsolatedClassLoader();
		assertNotNull(globalClassLoader);

		Class<?> loadedClass = globalClassLoader.loadClass(TEST_CLASS);
		assertNotNull(loadedClass);

		// Close classloader BEFORE test ends to release file locks
		libraryManager.closeAllClassLoaders();
	}

	@Test
	void testNonIsolatedLoadDoesNotUseIsolatedClassLoader() {
		LibraryRequest library = LibraryRequest.builder()
				.groupId(TEST_GROUP_ID)
				.artifactId(TEST_ARTIFACT_ID)
				.version(TEST_VERSION)
				.isolated(false) // Not isolated
				.build();

		libraryManager.loadLibrary(library);

		// Library was added to regular classpath, verify it's tracked
		assertTrue(libraryManager.hasLoadedToClasspath());

		// Global isolated classloader should NOT have this library
		IsolatedClassLoader globalClassLoader = libraryManager.getGlobalIsolatedClassLoader();
		assertThrows(ClassNotFoundException.class, () ->
				globalClassLoader.loadClass(TEST_CLASS));
	}

	@Test
	void testGetNonExistentIsolatedClassLoaderReturnsNull() {
		IsolatedClassLoader classLoader = libraryManager.getIsolatedClassLoaderById("non-existent-id");
		assertNull(classLoader);
	}

	@Test
	void testSameLoaderIdReusesClassLoader() {
		String loaderId = "shared-loader";

		LibraryRequest library1 = LibraryRequest.builder()
				.groupId(TEST_GROUP_ID)
				.artifactId(TEST_ARTIFACT_ID)
				.version(TEST_VERSION)
				.isolated(true)
				.loader(loaderId)
				.build();

		// Load first library
		libraryManager.loadLibrary(library1);
		IsolatedClassLoader classLoader1 = libraryManager.getIsolatedClassLoaderById(loaderId);

		// Load second library with same ID
		LibraryRequest library2 = LibraryRequest.builder()
				.groupId("org.apache.commons")
				.artifactId("commons-lang3")
				.version("3.12.0")
				.isolated(true)
				.loader(loaderId)
				.build();

		libraryManager.loadLibrary(library2);
		IsolatedClassLoader classLoader2 = libraryManager.getIsolatedClassLoaderById(loaderId);

		// Should be the same classloader instance
		assertSame(classLoader1, classLoader2, "Same loader ID should reuse the same classloader");
	}

	/**
	 * Test implementation of BaseLibraryManager for testing purposes.
	 */
	private static class TestLibraryManager extends BaseLibraryManager {
		private boolean loadedToClasspath = false;

		public TestLibraryManager(Path tempDir) {
			super(new TestLoggingHelper(), tempDir, "lib");
		}

		@Override
		protected void addToClasspath(@NotNull Path file) {
			loadedToClasspath = true;
		}

		public boolean hasLoadedToClasspath() {
			return loadedToClasspath;
		}

		public void closeAllClassLoaders() throws Exception {
			close();
		}
	}

	/**
	 * Simple log adapter for testing.
	 */
	private static class TestLoggingHelper implements LoggingHelper {
		@Override
		public void log(@NotNull Level level, @NotNull String message) {
			System.out.println("[" + level + "] " + message);
		}

		@Override
		public void log(@NotNull Level level, @NotNull String message, @NotNull Throwable throwable) {
			System.out.println("[" + level + "] " + message);
			throwable.printStackTrace(System.out);
		}
	}
}
