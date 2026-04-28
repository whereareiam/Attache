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
import java.util.Collection;

import static java.util.Objects.requireNonNull;

public final class SequentialLibraryLoader implements LibraryBatchLoader {
	private final BaseLibraryManager libraryManager;

	public SequentialLibraryLoader(@NotNull BaseLibraryManager libraryManager) {
		this.libraryManager = requireNonNull(libraryManager, "libraryManager");
	}

	@Override
	public <T> void loadLibraries(@NotNull Collection<? extends T> libraries) {
		for (T library : libraries) {
			LibraryRequest request = libraryManager.adaptLibrary(library);
			String skipReason = libraryManager.findSkipReason(request);
			if (skipReason != null) {
				libraryManager.logSkippedLibrary(request, skipReason);
				continue;
			}

			libraryManager.logLibraryLoadStart(request);

			try {
				Path file = libraryManager.downloadLibrary(request);
				libraryManager.loadDownloadedLibrary(request, file);
			} catch (IOException | URISyntaxException | NoSuchAlgorithmException e) {
				libraryManager.recordLoadFailure(request, e);
				throw new RuntimeException("Failed to load library " + request, e);
			} catch (RuntimeException e) {
				libraryManager.recordLoadFailure(request, e);
				throw e;
			}
		}

		VerbosityMode verbosityMode = libraryManager.getVerbosityMode();
		if (verbosityMode == VerbosityMode.SUMMARY)
			libraryManager.printLoadedLibrariesSummary();
	}
}
