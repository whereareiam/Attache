package me.whereareiam.attache.common.loader;

import me.whereareiam.attache.LibraryBatchLoader;
import me.whereareiam.attache.common.BaseLibraryManager;
import me.whereareiam.attache.model.LibraryRequest;
import me.whereareiam.attache.type.VerbosityMode;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static java.util.Objects.requireNonNull;

public final class ParallelLibraryLoader implements LibraryBatchLoader {
	private final BaseLibraryManager libraryManager;

	public ParallelLibraryLoader(@NotNull BaseLibraryManager libraryManager) {
		this.libraryManager = requireNonNull(libraryManager, "libraryManager");
	}

	@Override
	public <T> void loadLibraries(@NotNull Collection<? extends T> libraries) {
		List<T> orderedLibraries = new ArrayList<>(libraries);
		List<LibraryRequest> requests = new ArrayList<>(orderedLibraries.size());
		List<String> skipReasons = new ArrayList<>(orderedLibraries.size());

		for (T library : orderedLibraries) {
			LibraryRequest request = libraryManager.adaptLibrary(library);
			requests.add(request);
			skipReasons.add(libraryManager.findSkipReason(request));
		}

		Map<Integer, CompletableFuture<Path>> downloads = new HashMap<>();
		ExecutorService executor = Executors.newFixedThreadPool(getDownloadWorkerCount(requests.size()));

		try {
			for (int i = 0; i < requests.size(); i++) {
				LibraryRequest request = requests.get(i);
				if (skipReasons.get(i) != null)
					continue;

				libraryManager.logLibraryLoadStart(request);
				downloads.put(i, CompletableFuture.supplyAsync(() -> downloadLibrary(request), executor));
			}

			for (int i = 0; i < requests.size(); i++) {
				LibraryRequest request = requests.get(i);
				String skipReason = skipReasons.get(i);
				if (skipReason != null) {
					libraryManager.logSkippedLibrary(request, skipReason);
					continue;
				}

				try {
					Path file = awaitDownloadedPath(downloads.get(i));
					libraryManager.loadDownloadedLibrary(request, file);
				} catch (IOException | URISyntaxException | NoSuchAlgorithmException e) {
					libraryManager.recordLoadFailure(request, e);
					throw new RuntimeException("Failed to load library " + request, e);
				} catch (RuntimeException e) {
					libraryManager.recordLoadFailure(request, e);
					throw e;
				}
			}
		} finally {
			executor.shutdownNow();

			VerbosityMode verbosityMode = libraryManager.getVerbosityMode();
			if (verbosityMode == VerbosityMode.SUMMARY)
				libraryManager.printLoadedLibrariesSummary();
		}
	}

	private int getDownloadWorkerCount(int libraryCount) {
		if (libraryCount <= 1)
			return 1;

		return Math.min(libraryCount, Math.max(2, Runtime.getRuntime().availableProcessors()));
	}

	@NotNull
	private Path downloadLibrary(@NotNull LibraryRequest request) {
		try {
			return libraryManager.downloadLibrary(request);
		} catch (IOException | URISyntaxException | NoSuchAlgorithmException e) {
			throw new CompletionException(e);
		}
	}

	@NotNull
	private Path awaitDownloadedPath(@NotNull CompletableFuture<Path> future) throws IOException, URISyntaxException, NoSuchAlgorithmException {
		return libraryManager.awaitFuture(future, "Interrupted while waiting for library download");
	}
}
