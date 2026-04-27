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

class ParallelLibraryLoaderTest extends LibraryLoaderTestSupport {
	@TempDir
	Path tempDir;

	@Test
	void loadLibrariesCanBeForcedToParallelMode() throws Exception {
		String previousLoadMode = System.getProperty(LibraryLoadMode.SYSTEM_PROPERTY);
		setLoadModeProperty("parallel");

		BlockingTestLibraryManager libraryManager = new BlockingTestLibraryManager(tempDir);
		LibraryRequest firstLibrary = library("alpha");
		LibraryRequest secondLibrary = library("beta");
		ExecutorService executor = Executors.newSingleThreadExecutor();

		libraryManager.blockDownloads(2);

		try {
			assertEquals(LibraryLoadMode.PARALLEL, libraryManager.getLibraryLoadMode());

			Future<?> load = executor.submit(() -> libraryManager.loadLibraries(List.of(firstLibrary, secondLibrary)));
			assertTrue(libraryManager.awaitDownloadStarts(1, TimeUnit.SECONDS));
			assertTrue(libraryManager.getMaxConcurrentDownloads() >= 2);

			libraryManager.releaseDownloads();
			load.get(2, TimeUnit.SECONDS);

			assertEquals(2, libraryManager.getDownloadCount());
			assertEquals(List.of("alpha-1.0.0.jar", "beta-1.0.0.jar"), libraryManager.getLoadedFiles());
		} finally {
			executor.shutdownNow();
			libraryManager.close();
			restoreLoadModeProperty(previousLoadMode);
		}
	}
}
