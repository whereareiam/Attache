package me.whereareiam.attache.common;

import me.whereareiam.attache.common.util.ArtifactIntegrity;
import me.whereareiam.attache.common.util.RelocationHelper;
import me.whereareiam.attache.model.RelocationRule;
import me.whereareiam.attache.type.VerbosityMode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collection;
import java.util.HexFormat;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import static java.util.Objects.requireNonNull;

final class RelocationCoordinator implements AutoCloseable {
	private final BaseLibraryManager libraryManager;
	private final Path saveDirectory;
	private final ConcurrentMap<Path, CompletableFuture<Path>> inFlightRelocations = new ConcurrentHashMap<>();

	private RelocationHelper relocator;

	RelocationCoordinator(@NotNull BaseLibraryManager libraryManager, @NotNull Path saveDirectory) {
		this.libraryManager = requireNonNull(libraryManager, "libraryManager");
		this.saveDirectory = requireNonNull(saveDirectory, "saveDirectory");
	}

	@NotNull
	Path relocate(@NotNull Path in, @NotNull String out, @NotNull Collection<RelocationRule> relocations) {
		requireNonNull(in, "in");
		requireNonNull(out, "out");
		requireNonNull(relocations, "relocations");

		Path file = saveDirectory.resolve(out);
		CompletableFuture<Path> future = new CompletableFuture<>();
		CompletableFuture<Path> existing = inFlightRelocations.putIfAbsent(file, future);
		if (existing != null)
			return awaitRelocation(existing);

		try {
			Path relocated = relocateNow(in, file, relocations);
			future.complete(relocated);
			return relocated;
		} catch (RuntimeException e) {
			future.completeExceptionally(e);
			throw e;
		} finally {
			inFlightRelocations.remove(file, future);
		}
	}

	@NotNull
	private Path relocateNow(@NotNull Path in, @NotNull Path file, @NotNull Collection<RelocationRule> relocations) {
		Path tmpOut = null;
		try {
			String fingerprint = HexFormat.of().formatHex(ArtifactIntegrity.sha256(in));
			Path marker = file.resolveSibling(file.getFileName() + ".source.sha256");
			if (Files.exists(file) && Files.isRegularFile(marker)
					&& Files.readString(marker).equals(fingerprint))
				return file;

			Files.createDirectories(file.getParent());
			tmpOut = Files.createTempFile(file.getParent(), file.getFileName().toString(), ".tmp");

			getRelocator().relocate(in, tmpOut, relocations);
			Files.move(tmpOut, file, StandardCopyOption.REPLACE_EXISTING);
			Files.writeString(marker, fingerprint);

			if (libraryManager.getVerbosityMode() == VerbosityMode.VERBOSE)
				libraryManager.getLogger().info("Relocations applied to " + in.getFileName());

			return file;
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		} finally {
			deleteTemporaryOutput(tmpOut);
		}
	}

	private void deleteTemporaryOutput(@Nullable Path file) {
		if (file == null)
			return;

		try {
			Files.deleteIfExists(file);
		} catch (IOException failure) {
			libraryManager.getLogger().debug("Could not remove temporary relocation output " + file);
		}
	}

	@NotNull
	private RelocationHelper getRelocator() {
		synchronized (this) {
			if (relocator == null)
				relocator = new RelocationHelper(libraryManager);

			return relocator;
		}
	}

	@NotNull
	private Path awaitRelocation(@NotNull CompletableFuture<Path> future) {
		try {
			return future.join();
		} catch (CompletionException e) {
			Throwable cause = e.getCause();
			if (cause instanceof RuntimeException runtimeException)
				throw runtimeException;

			throw e;
		}
	}

	@Override
	public void close() {
		if (relocator == null)
			return;

		try {
			relocator.close();
		} catch (Exception e) {
			libraryManager.getLogger().error("Failed to close relocator", e);
		}
	}
}
