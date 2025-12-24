package me.whereareiam.attache.platform.spring.config.library;

import lombok.Getter;
import lombok.Setter;
import me.whereareiam.attache.model.Library;
import me.whereareiam.attache.platform.spring.config.RelocationProperties;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Objects;

/**
 * Configuration holder for a single library entry declared in Spring properties.
 */
@Setter
@Getter
public class LibraryProperties {
    private final List<String> urls = new ArrayList<>();
    private final List<String> repositories = new ArrayList<>();
    private final List<String> fallbackRepositories = new ArrayList<>();
    private final List<RelocationProperties> relocations = new ArrayList<>();
    private final List<ExcludedLibraryProperties> excludedTransitiveDependencies = new ArrayList<>();

    private String groupId;
    private String artifactId;
    private String version;
    private String classifier;
    private String checksum;
    private boolean isolated;
    private String loader;
    private boolean resolveTransitiveDependencies;

    /**
     * Converts this configuration block into a {@link Library} model.
     *
     * @return library model
     */
    public Library toLibrary() {
        Library.LibraryBuilder builder = Library.builder()
                .groupId(Objects.requireNonNull(groupId, "groupId"))
                .artifactId(Objects.requireNonNull(artifactId, "artifactId"))
                .version(Objects.requireNonNull(version, "version"))
                .isolated(isolated)
                .loader(loader)
                .resolveTransitiveDependencies(resolveTransitiveDependencies);

        urls.forEach(builder::url);
        repositories.forEach(builder::repository);
        fallbackRepositories.forEach(builder::fallbackRepository);
        relocations.stream().map(RelocationProperties::toRelocation).forEach(builder::relocation);
        excludedTransitiveDependencies.stream()
                .map(ExcludedLibraryProperties::toExcludedDependency)
                .forEach(builder::excludedTransitiveDependency);

        if (classifier != null && !classifier.isEmpty())
            builder.classifier(classifier);

        byte[] checksumBytes = parseChecksum(checksum);
        if (checksumBytes != null) {
            builder.checksum(checksumBytes);
        }

        return builder.build();
    }

    /**
     * Parses the configured checksum from hex or Base64 into bytes.
     *
     * @param value checksum string
     * @return decoded bytes or null when absent
     */
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
