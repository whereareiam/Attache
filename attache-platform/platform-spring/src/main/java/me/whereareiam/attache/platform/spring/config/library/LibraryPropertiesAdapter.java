package me.whereareiam.attache.platform.spring.config.library;

import me.whereareiam.attache.LibraryAdapter;
import me.whereareiam.attache.model.LibraryRequest;
import me.whereareiam.attache.platform.spring.config.RelocationProperties;
import org.jetbrains.annotations.NotNull;

import java.util.Base64;
import java.util.Objects;

public class LibraryPropertiesAdapter implements LibraryAdapter<LibraryProperties> {
	@Override
	public @NotNull LibraryRequest adapt(@NotNull LibraryProperties library) {
		LibraryRequest.LibraryRequestBuilder builder = LibraryRequest.builder()
				.groupId(Objects.requireNonNull(library.getGroupId(), "groupId"))
				.artifactId(Objects.requireNonNull(library.getArtifactId(), "artifactId"))
				.version(Objects.requireNonNull(library.getVersion(), "version"))
				.isolated(library.isIsolated())
				.loader(library.getLoader())
				.resolveTransitiveDependencies(library.isResolveTransitiveDependencies());

		library.getUrls().forEach(builder::url);
		library.getRepositories().forEach(builder::repository);
		library.getFallbackRepositories().forEach(builder::fallbackRepository);
		library.getRelocations().stream()
				.map(RelocationProperties::toRelocationRule)
				.forEach(builder::relocation);
		library.getExcludedTransitiveDependencies().stream()
				.map(ExcludedLibraryProperties::toExcludedDependency)
				.forEach(builder::excludedTransitiveDependency);

		String classifier = library.getClassifier();
		if (classifier != null && !classifier.isEmpty()) {
			builder.classifier(classifier);
		}

		byte[] checksumBytes = parseChecksum(library.getChecksum());
		if (checksumBytes != null) {
			builder.checksum(checksumBytes);
		}

		return builder.build();
	}

	private byte[] parseChecksum(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}

		String normalized = value.replace(" ", "").trim();
		boolean looksHex = normalized.matches("^[0-9a-fA-F]+$");

		if (looksHex && normalized.length() % 2 == 0) {
			byte[] out = new byte[normalized.length() / 2];
			for (int i = 0; i < normalized.length(); i += 2) {
				String byteHex = normalized.substring(i, i + 2);
				out[i / 2] = (byte) Integer.parseInt(byteHex, 16);
			}

			return out;
		}

		try {
			return Base64.getDecoder().decode(normalized);
		} catch (IllegalArgumentException e) {
			throw new IllegalArgumentException("Invalid checksum format. Use hex or Base64.", e);
		}
	}
}
