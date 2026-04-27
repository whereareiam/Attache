package me.whereareiam.attache.common.loader;

import me.whereareiam.attache.model.LibraryRequest;
import me.whereareiam.attache.type.LibraryLoadMode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SequentialLibraryLoaderTest extends LibraryLoaderTestSupport {
	@TempDir
	Path tempDir;

	@Test
	void loadLibrariesCanBeForcedToSequentialMode() throws Exception {
		String previousLoadMode = System.getProperty(LibraryLoadMode.SYSTEM_PROPERTY);
		setLoadModeProperty("sequential");

		BlockingTestLibraryManager libraryManager = new BlockingTestLibraryManager(tempDir);
		LibraryRequest firstLibrary = library("alpha");
		LibraryRequest secondLibrary = library("beta");
		ExecutorService executor = Executors.newSingleThreadExecutor();

		libraryManager.blockDownloads(1);

		try {
			assertEquals(LibraryLoadMode.SEQUENTIAL, libraryManager.getLibraryLoadMode());

			Future<?> load = executor.submit(() -> libraryManager.loadLibraries(List.of(firstLibrary, secondLibrary)));
			assertTrue(libraryManager.awaitDownloadStarts(1, TimeUnit.SECONDS));
			TimeUnit.MILLISECONDS.sleep(150);

			assertEquals(1, libraryManager.getDownloadCount());
			assertEquals(1, libraryManager.getMaxConcurrentDownloads());

			libraryManager.releaseDownloads();
			load.get(2, TimeUnit.SECONDS);

			assertEquals(2, libraryManager.getDownloadCount());
			assertEquals(1, libraryManager.getMaxConcurrentDownloads());
			assertEquals(List.of("alpha-1.0.0.jar", "beta-1.0.0.jar"), libraryManager.getLoadedFiles());
		} finally {
			executor.shutdownNow();
			libraryManager.close();
			restoreLoadModeProperty(previousLoadMode);
		}
	}
}
