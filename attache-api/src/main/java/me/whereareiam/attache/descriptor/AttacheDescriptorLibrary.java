package me.whereareiam.attache.descriptor;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import me.whereareiam.attache.model.ExcludedDependency;
import me.whereareiam.attache.model.LibraryRequest;
import me.whereareiam.attache.model.RelocationRule;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Descriptor entry for a single Attache-managed library.
 */
@Getter
@Setter
@EqualsAndHashCode
public class AttacheDescriptorLibrary {
	private final Set<String> urls = new LinkedHashSet<>();
	private final Set<String> repositories = new LinkedHashSet<>();
	private final Set<String> fallbackRepositories = new LinkedHashSet<>();
	private final List<RelocationRule> relocations = new ArrayList<>();
	private final List<ExcludedDependency> excludedTransitiveDependencies = new ArrayList<>();

	private String groupId;
	private String artifactId;
	private String version;
	private String classifier;
	private String checksum;
	private boolean skipIfPresent = true;
	private boolean isolated;
	private String loader;
	private boolean resolveTransitiveDependencies;

	@NotNull
	public LibraryRequest toLibraryRequest() {
		LibraryRequest.LibraryRequestBuilder builder = LibraryRequest.builder()
				.groupId(Objects.requireNonNull(groupId, "groupId"))
				.artifactId(Objects.requireNonNull(artifactId, "artifactId"))
				.version(Objects.requireNonNull(version, "version"))
				.skipIfPresent(skipIfPresent)
				.isolated(isolated)
				.loader(loader)
				.resolveTransitiveDependencies(resolveTransitiveDependencies);

		urls.forEach(builder::url);
		repositories.forEach(builder::repository);
		fallbackRepositories.forEach(builder::fallbackRepository);
		relocations.forEach(builder::relocation);
		excludedTransitiveDependencies.forEach(builder::excludedTransitiveDependency);

		if (classifier != null && !classifier.isBlank()) {
			builder.classifier(classifier);
		}

		byte[] checksumBytes = parseChecksum(checksum);
		if (checksumBytes != null) {
			builder.checksum(checksumBytes);
		}

		return builder.build();
	}

	@NotNull
	public String coordinatesKey() {
		StringBuilder key = new StringBuilder()
				.append(groupId)
				.append(':')
				.append(artifactId)
				.append(':')
				.append(version);
		if (classifier != null && !classifier.isBlank()) {
			key.append(':').append(classifier);
		}
		return key.toString();
	}

	public boolean sameDefinition(@NotNull AttacheDescriptorLibrary other) {
		return Objects.equals(groupId, other.groupId)
				&& Objects.equals(artifactId, other.artifactId)
				&& Objects.equals(version, other.version)
				&& Objects.equals(classifier, other.classifier)
				&& Objects.equals(checksum, other.checksum)
				&& skipIfPresent == other.skipIfPresent
				&& isolated == other.isolated
				&& Objects.equals(loader, other.loader)
				&& resolveTransitiveDependencies == other.resolveTransitiveDependencies
				&& Objects.equals(urls, other.urls)
				&& Objects.equals(repositories, other.repositories)
				&& Objects.equals(fallbackRepositories, other.fallbackRepositories)
				&& Objects.equals(relocations, other.relocations)
				&& Objects.equals(excludedTransitiveDependencies, other.excludedTransitiveDependencies);
	}

	private byte @Nullable [] parseChecksum(@Nullable String value) {
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
