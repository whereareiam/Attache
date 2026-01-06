package me.whereareiam.attache.common;

import me.whereareiam.attache.Repositories;
import me.whereareiam.attache.common.util.LibraryHelper;
import me.whereareiam.attache.model.LibraryRequest;
import me.whereareiam.attache.model.RelocationRule;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link LibraryRequest} model.
 */
class LibraryTest {

	@Test
	void testBasicLibraryBuild() {
		LibraryRequest library = LibraryRequest.builder()
				.groupId("com.example")
				.artifactId("test-lib")
				.version("1.0.0")
				.build();

		assertEquals("com.example", library.getGroupId());
		assertEquals("test-lib", library.getArtifactId());
		assertEquals("1.0.0", library.getVersion());
		assertNull(library.getClassifier());
		assertFalse(library.hasChecksum());
		assertFalse(library.hasRelocations());
	}

	@Test
	void testLibraryWithBracesReplacement() {
		// Model should store values as-is
		LibraryRequest library = LibraryRequest.builder()
				.groupId("com{}example{}library")
				.artifactId("test{}lib")
				.version("1.0.0")
				.build();

		// Raw values should contain braces
		assertEquals("com{}example{}library", library.getGroupId());
		assertEquals("test{}lib", library.getArtifactId());

		// After normalization, braces should be replaced
		LibraryRequest normalized = LibraryHelper.normalize(library);
		assertEquals("com.example.library", normalized.getGroupId());
		assertEquals("test.lib", normalized.getArtifactId());
	}

	@Test
	void testLibraryWithClassifier() {
		LibraryRequest library = LibraryRequest.builder()
				.groupId("com.example")
				.artifactId("test-lib")
				.version("1.0.0")
				.classifier("sources")
				.build();

		assertEquals("sources", library.getClassifier());
		assertTrue(library.hasClassifier());
	}

	@Test
	void testLibraryWithChecksum() {
		byte[] checksum = new byte[32]; // SHA-256 = 32 bytes
		LibraryRequest library = LibraryRequest.builder()
				.groupId("com.example")
				.artifactId("test-lib")
				.version("1.0.0")
				.checksum(checksum)
				.build();

		assertTrue(library.hasChecksum());
		assertNotNull(library.getChecksum());
		assertEquals(32, library.getChecksum().length);
		assertArrayEquals(checksum, library.getChecksum());
	}

	@Test
	void testLibraryWithRelocations() {
		LibraryRequest library = LibraryRequest.builder()
				.groupId("com.example")
				.artifactId("test-lib")
				.version("1.0.0")
				.relocation(
						RelocationRule.builder()
								.pattern("com{}example")
								.relocatedPattern("me{}myapp{}libs{}example")
								.build())
				.build();

		assertTrue(library.hasRelocations());
		assertEquals(1, library.getRelocations().size());
		assertNotNull(LibraryHelper.getRelocatedPath(library));
	}

	@Test
	void testLibraryWithIsolatedLoad() {
		LibraryRequest library = LibraryRequest.builder()
				.groupId("com.example")
				.artifactId("test-lib")
				.version("1.0.0")
				.isolated(true)
				.loader("my-loader")
				.build();

		assertTrue(library.isIsolated());
		assertEquals("my-loader", library.getLoader());
	}

	@Test
	void testLibraryWithRepositories() {
		LibraryRequest library = LibraryRequest.builder()
				.groupId("com.example")
				.artifactId("test-lib")
				.version("1.0.0")
				.repository("https://repo.example.com/")
				.fallbackRepository(Repositories.MAVEN_CENTRAL)
				.build();

		assertEquals(1, library.getRepositories().size());
		assertEquals(1, library.getFallbackRepositories().size());
		assertTrue(library.getFallbackRepositories().contains(Repositories.MAVEN_CENTRAL));
	}

	@Test
	void testSnapshotDetection() {
		LibraryRequest snapshot = LibraryRequest.builder()
				.groupId("com.example")
				.artifactId("test-lib")
				.version("1.0.0-SNAPSHOT")
				.build();

		assertTrue(snapshot.isSnapshot());

		LibraryRequest release = LibraryRequest.builder()
				.groupId("com.example")
				.artifactId("test-lib")
				.version("1.0.0")
				.build();

		assertFalse(release.isSnapshot());
	}

	@Test
	void testToString() {
		LibraryRequest library = LibraryRequest.builder()
				.groupId("com.example")
				.artifactId("test-lib")
				.version("1.0.0")
				.build();

		assertEquals("com.example:test-lib:1.0.0", library.toString());

		LibraryRequest withClassifier = LibraryRequest.builder()
				.groupId("com.example")
				.artifactId("test-lib")
				.version("1.0.0")
				.classifier("sources")
				.build();

		assertEquals("com.example:test-lib:1.0.0:sources", withClassifier.toString());
	}

	@Test
	void testLibraryPath() {
		LibraryRequest library = LibraryRequest.builder()
				.groupId("com.example")
				.artifactId("test-lib")
				.version("1.0.0")
				.build();

		assertEquals("com/example/test-lib/1.0.0/", LibraryHelper.getPartialPath(library));
		assertEquals("com/example/test-lib/1.0.0/test-lib-1.0.0.jar", LibraryHelper.getPath(library));
	}
}

