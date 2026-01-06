package me.whereareiam.attache.platform.spring.config;

import lombok.Getter;
import lombok.Setter;
import me.whereareiam.attache.model.RelocationRule;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Configuration holder for relocation rules declared in Spring properties.
 */
@Setter
@Getter
public class RelocationProperties {
    private String pattern;
    private String relocatedPattern;
    private final List<String> includes = new ArrayList<>();
    private final List<String> excludes = new ArrayList<>();

    /**
     * Converts the configured values into a {@link RelocationRule}.
     *
     * @return relocation rule instance
     */
    public RelocationRule toRelocationRule() {
        RelocationRule.RelocationRuleBuilder builder = RelocationRule.builder()
                .pattern(Objects.requireNonNull(pattern, "pattern"))
                .relocatedPattern(Objects.requireNonNull(relocatedPattern, "relocatedPattern"));

        includes.forEach(builder::include);
        excludes.forEach(builder::exclude);

        return builder.build();
    }
}
