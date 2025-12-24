package me.whereareiam.attache.platform.spring.config.library;

import lombok.Getter;
import lombok.Setter;
import me.whereareiam.attache.model.ExcludedDependency;

import java.util.Objects;

/**
 * Configuration holder for a transitive dependency exclusion.
 */
@Setter
@Getter
public class ExcludedLibraryProperties {
    private String groupId;
    private String artifactId;

    /**
     * Converts this configuration block into an {@link ExcludedDependency}.
     *
     * @return excluded dependency model
     */
    public ExcludedDependency toExcludedDependency() {
        return new ExcludedDependency(
                Objects.requireNonNull(groupId, "groupId"),
                Objects.requireNonNull(artifactId, "artifactId")
        );
    }
}
