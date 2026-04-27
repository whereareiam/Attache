package me.whereareiam.attache.common;

import me.whereareiam.attache.common.util.LibraryHelper;
import me.whereareiam.attache.model.LibraryRequest;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collection;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import static java.util.Objects.requireNonNull;

final class ArtifactDownloadCoordinator {
	private final BaseLibraryManager libraryManager;
	private final Path saveDirectory;
	private final ConcurrentMap<Path, CompletableFuture<Path>> inFlightDownloads = new ConcurrentHashMap<>();

	ArtifactDownloadCoordinator(@NotNull BaseLibraryManager libraryManager, @NotNull Path saveDirectory) {
		this.libraryManager = requireNonNull(libraryManager, "libraryManager");
		this.saveDirectory = requireNonNull(saveDirectory, "saveDirectory");
	}

	@NotNull
	Path download(@NotNull LibraryRequest normalizedLibrary) throws IOException, URISyntaxException, NoSuchAlgorithmException {
		Path file = saveDirectory.resolve(LibraryHelper.getPath(normalizedLibrary));
		CompletableFuture<Path> future = new CompletableFuture<>();
		CompletableFuture<Path> existing = inFlightDownloads.putIfAbsent(file, future);
		if (existing != null)
			return awaitDownload(existing);

		try {
			Path downloaded = downloadNow(normalizedLibrary, file);
			future.complete(downloaded);
			return downloaded;
		} catch (IOException | NoSuchAlgorithmException | RuntimeException e) {
			future.completeExceptionally(e);
			throw e;
		} finally {
			inFlightDownloads.remove(file, future);
		}
	}

	@NotNull
	private Path downloadNow(@NotNull LibraryRequest normalizedLibrary, @NotNull Path file) throws IOException, NoSuchAlgorithmException {
		if (Files.exists(file)) {
			if (!normalizedLibrary.isSnapshot())
				return file;

			Files.delete(file);
		}

		Collection<String> urls = libraryManager.resolveLibrary(normalizedLibrary);
		if (urls.isEmpty())
			throw new RuntimeException("Library '" + normalizedLibrary + "' couldn't be resolved, add a repository");

		MessageDigest md = null;
		if (normalizedLibrary.hasChecksum())
			md = MessageDigest.getInstance("SHA-256");

		Files.createDirectories(file.getParent());
		Path out = Files.createTempFile(file.getParent(), file.getFileName().toString(), ".tmp");
		try {
			for (String url : urls) {
				byte[] bytes = libraryManager.downloadLibraryBytes(url);
				if (bytes == null) continue;

				if (md != null) {
					byte[] checksum = md.digest(bytes);
					if (!Arrays.equals(checksum, normalizedLibrary.getChecksum())) {
						libraryManager.getLogger().warn("*** INVALID CHECKSUM ***");
						libraryManager.getLogger().warn(" Library :  " + normalizedLibrary);
						libraryManager.getLogger().warn(" URL :  " + url);
						libraryManager.getLogger().warn(" Expected :  " + Base64.getEncoder().encodeToString(normalizedLibrary.getChecksum()));
						libraryManager.getLogger().warn(" Actual :  " + Base64.getEncoder().encodeToString(checksum));
						continue;
					}
				}

				Files.write(out, bytes);
				Files.move(out, file, StandardCopyOption.REPLACE_EXISTING);
				return file;
			}
		} finally {
			Files.deleteIfExists(out);
		}

		throw new RuntimeException("Failed to download library '" + normalizedLibrary + "'");
	}

	@NotNull
	private Path awaitDownload(@NotNull CompletableFuture<Path> future) throws IOException, URISyntaxException, NoSuchAlgorithmException {
		return libraryManager.awaitFuture(future, "Interrupted while waiting for library download");
	}
}
