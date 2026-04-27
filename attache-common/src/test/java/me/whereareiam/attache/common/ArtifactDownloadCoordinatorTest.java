package me.whereareiam.attache.common;

import me.whereareiam.attache.common.loader.LibraryLoaderTestSupport;
import me.whereareiam.attache.model.LibraryRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ArtifactDownloadCoordinatorTest extends LibraryLoaderTestSupport {
	@TempDir
	Path tempDir;

	@Test
	void concurrentDownloadLibraryRequestsReuseSingleFlight() throws Exception {
		BlockingTestLibraryManager libraryManager = new BlockingTestLibraryManager(tempDir);
		LibraryRequest library = library("alpha");
		ExecutorService executor = Executors.newFixedThreadPool(2);

		libraryManager.blockDownloads(1);

		try {
			Future<Path> first = executor.submit(() -> libraryManager.downloadLibrary(library));
			assertTrue(libraryManager.awaitDownloadStarts(1, TimeUnit.SECONDS));

			Future<Path> second = executor.submit(() -> libraryManager.downloadLibrary(library));
			TimeUnit.MILLISECONDS.sleep(150);

			assertEquals(1, libraryManager.getDownloadCount());

			libraryManager.releaseDownloads();

			assertEquals(first.get(2, TimeUnit.SECONDS), second.get(2, TimeUnit.SECONDS));
			assertEquals(1, libraryManager.getDownloadCount());
		} finally {
			executor.shutdownNow();
			libraryManager.close();
		}
	}
}
