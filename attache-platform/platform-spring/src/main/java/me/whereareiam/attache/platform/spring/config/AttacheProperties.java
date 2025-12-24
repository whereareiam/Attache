package me.whereareiam.attache.platform.spring.config;

import lombok.Getter;
import lombok.Setter;
import me.whereareiam.attache.platform.spring.config.library.LibraryProperties;
import me.whereareiam.attache.type.Level;
import me.whereareiam.attache.type.ResolutionMode;
import me.whereareiam.attache.type.VerbosityMode;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Spring Boot configuration properties for Attache.
 */
@Getter
@ConfigurationProperties(prefix = "attache")
public class AttacheProperties {
    @Setter
    private boolean enabled = true;
    @Setter
    private boolean autoLoad = true;
    @Setter
    private boolean addMavenCentral = true;

    private String libraryPath = ".libraries";

    private Level logLevel = Level.INFO;
    private ResolutionMode resolutionMode = ResolutionMode.DEFAULT;
    private VerbosityMode verbosityMode = VerbosityMode.VERBOSE;

    private final List<String> repositories = new ArrayList<>();
    private final List<LibraryProperties> libraries = new ArrayList<>();

    /**
     * Sets the root path where libraries will be cached.
     *
     * @param libraryPath path to the cache directory
     */
	public void setLibraryPath(String libraryPath) {
        this.libraryPath = Objects.requireNonNull(libraryPath, "libraryPath");
    }

    /**
     * Sets the log level for Attache logs.
     *
     * @param logLevel desired log level
     */
	public void setLogLevel(Level logLevel) {
        this.logLevel = Objects.requireNonNull(logLevel, "logLevel");
    }

    /**
     * Sets the repository resolution strategy.
     *
     * @param resolutionMode resolution mode
     */
	public void setResolutionMode(ResolutionMode resolutionMode) {
        this.resolutionMode = Objects.requireNonNull(resolutionMode, "resolutionMode");
    }

    /**
     * Sets the verbosity mode for Attache output.
     *
     * @param verbosityMode verbosity mode
     */
	public void setVerbosityMode(VerbosityMode verbosityMode) {
        this.verbosityMode = Objects.requireNonNull(verbosityMode, "verbosityMode");
    }

}
