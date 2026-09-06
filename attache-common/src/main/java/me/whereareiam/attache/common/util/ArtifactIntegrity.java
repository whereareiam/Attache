package me.whereareiam.attache.common.util;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Content fingerprints used to validate downloaded and relocated cache entries.
 */
public final class ArtifactIntegrity {
	/**
	 * Computes a file's SHA-256 without loading the entire file into memory.
	 *
	 * @param path file to fingerprint
	 * @return binary SHA-256 digest
	 * @throws IOException when the file cannot be read
	 */
	public static byte @NotNull [] sha256(@NotNull Path path) throws IOException {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			try (var input = Files.newInputStream(path)) {
				byte[] buffer = new byte[8192];
				int count;
				while ((count = input.read(buffer)) != -1) digest.update(buffer, 0, count);
			}

			return digest.digest();
		} catch (NoSuchAlgorithmException failure) {
			throw new IllegalStateException("SHA-256 is unavailable", failure);
		}
	}
}
