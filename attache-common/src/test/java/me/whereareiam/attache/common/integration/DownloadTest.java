package me.whereareiam.attache.common.integration;

import me.whereareiam.attache.LoggingHelper;
import me.whereareiam.attache.common.BaseLibraryManager;
import me.whereareiam.attache.model.LibraryRequest;
import me.whereareiam.attache.type.Level;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for downloading libraries from Maven Central.
 * These tests require network access.
 */
class DownloadTest {
	@TempDir
	Path tempDir;

	private TestLibraryManager libraryManager;

	// Small, stable library for testing (Apache Commons Text)
	private static final String TEST_GROUP_ID = "org.apache.commons";
	private static final String TEST_ARTIFACT_ID = "commons-text";
	private static final String TEST_VERSION = "1.10.0";
	// SHA-256 checksum for commons-text 1.10.0
	private static final String TEST_CHECKSUM_BASE64 = "dwzZA/p7YE0ffve6F/hBCGZylLK0eL6O0a87/7SuABg=";

	@BeforeEach
	void setUp() {
		libraryManager = new TestLibraryManager(tempDir);
		libraryManager.addMavenCentral();
	}

	@Test
	void testDownloadLibraryFromMavenCentral() throws Exception {
		LibraryRequest library = LibraryRequest.builder()
				.groupId(TEST_GROUP_ID)
				.artifactId(TEST_ARTIFACT_ID)
				.version(TEST_VERSION)
				.build();

		Path downloadedFile = libraryManager.downloadLibrary(library);

		assertNotNull(downloadedFile);
		assertTrue(Files.exists(downloadedFile));
		assertTrue(Files.isRegularFile(downloadedFile));
		assertTrue(Files.size(downloadedFile) > 0);
	}

	@Test
	void testDownloadLibraryWithChecksumValidation() throws Exception {
		LibraryRequest library = LibraryRequest.builder()
				.groupId(TEST_GROUP_ID)
				.artifactId(TEST_ARTIFACT_ID)
				.version(TEST_VERSION)
				.checksum(Base64.getDecoder().decode(TEST_CHECKSUM_BASE64))
				.build();

		Path downloadedFile = libraryManager.downloadLibrary(library);

		assertNotNull(downloadedFile);
		assertTrue(Files.exists(downloadedFile));

		// Verify the checksum matches
		byte[] fileBytes = Files.readAllBytes(downloadedFile);
		MessageDigest md = MessageDigest.getInstance("SHA-256");
		byte[] actualChecksum = md.digest(fileBytes);

		assertArrayEquals(Base64.getDecoder().decode(TEST_CHECKSUM_BASE64), actualChecksum);
	}

	@Test
	void testDownloadLibraryCaching() throws Exception {
		LibraryRequest library = LibraryRequest.builder()
				.groupId(TEST_GROUP_ID)
				.artifactId(TEST_ARTIFACT_ID)
				.version(TEST_VERSION)
				.build();

		// First download
		Path firstDownload = libraryManager.downloadLibrary(library);
		long firstModified = Files.getLastModifiedTime(firstDownload).toMillis();

		// Wait a bit to ensure time difference would be detectable
		Thread.sleep(100);

		// Second download - should use cached file
		Path secondDownload = libraryManager.downloadLibrary(library);
		long secondModified = Files.getLastModifiedTime(secondDownload).toMillis();

		assertEquals(firstDownload, secondDownload);
		assertEquals(firstModified, secondModified, "File should not have been re-downloaded");
	}

	@Test
	void testDownloadLibraryWithInvalidChecksum() {
		LibraryRequest library = LibraryRequest.builder()
				.groupId(TEST_GROUP_ID)
				.artifactId(TEST_ARTIFACT_ID)
				.version(TEST_VERSION)
				.checksum(java.util.Base64.getDecoder().decode("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=")) // Invalid checksum
				.build();

		// Should throw RuntimeException due to checksum mismatch
		assertThrows(RuntimeException.class, () -> libraryManager.downloadLibrary(library));
	}

	@Test
	void testDownloadNonExistentLibrary() {
		LibraryRequest library = LibraryRequest.builder()
				.groupId("com.nonexistent")
				.artifactId("fake-library")
				.version("999.999.999")
				.build();

		// Should throw RuntimeException when library cannot be downloaded
		assertThrows(RuntimeException.class, () -> libraryManager.downloadLibrary(library));
	}

	@Test
	void testDownloadLibraryWithClassifier() throws Exception {
		// commons-text has a "javadoc" classifier
		LibraryRequest library = LibraryRequest.builder()
				.groupId(TEST_GROUP_ID)
				.artifactId(TEST_ARTIFACT_ID)
				.version(TEST_VERSION)
				.classifier("javadoc")
				.build();

		Path downloadedFile = libraryManager.downloadLibrary(library);

		assertNotNull(downloadedFile);
		assertTrue(Files.exists(downloadedFile));
		assertTrue(downloadedFile.toString().contains("javadoc"));
	}

	@Test
	void testLibraryPath() throws Exception {
		LibraryRequest library = LibraryRequest.builder()
				.groupId(TEST_GROUP_ID)
				.artifactId(TEST_ARTIFACT_ID)
				.version(TEST_VERSION)
				.build();

		Path downloadedFile = libraryManager.downloadLibrary(library);

		// Verify the file is in the expected location (OS-agnostic)
		String pathStr = downloadedFile.toString();
		assertTrue(pathStr.contains("org") && pathStr.contains("apache") && pathStr.contains("commons"));
		assertTrue(pathStr.contains("commons-text"));
		assertTrue(pathStr.contains(TEST_VERSION));
	}

	/**
	 * Test implementation of BaseLibraryManager for integration testing.
	 */
	private static class TestLibraryManager extends BaseLibraryManager {
		public TestLibraryManager(Path tempDir) {
			super(new TestLoggingHelper(), tempDir, "lib");
		}

		@Override
		protected void addToClasspath(@NotNull Path file) {
			// No-op for testing
		}
	}

	/**
	 * Simple log adapter for testing that outputs to console.
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

