package me.whereareiam.attache.platform.spring.autoconfigure;

import me.whereareiam.attache.LoggingHelper;
import me.whereareiam.attache.model.Library;
import me.whereareiam.attache.platform.spring.config.AttacheProperties;
import me.whereareiam.attache.platform.spring.config.library.LibraryProperties;
import me.whereareiam.attache.platform.spring.logging.SpringLoggingHelper;
import me.whereareiam.attache.platform.standalone.StandaloneLibraryManager;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import java.nio.file.Paths;
import java.util.List;

@AutoConfiguration
@EnableConfigurationProperties(AttacheProperties.class)
@ConditionalOnClass({StandaloneLibraryManager.class, ApplicationRunner.class})
@ConditionalOnProperty(prefix = "attache", name = "enabled", havingValue = "true", matchIfMissing = true)
public class AttacheAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean
    public LoggingHelper attacheLoggingHelper() {
        return new SpringLoggingHelper();
    }

    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean
    public StandaloneLibraryManager attacheLibraryManager(LoggingHelper loggingHelper, AttacheProperties properties) {
        // Store everything directly under the configured path (directoryName is fixed to ".")
        StandaloneLibraryManager manager = new StandaloneLibraryManager(
                loggingHelper,
                Paths.get(properties.getLibraryPath()),
                "."
        );

        manager.setLogLevel(properties.getLogLevel());
        manager.setRepositoryResolutionMode(properties.getResolutionMode());
        manager.setVerbosityMode(properties.getVerbosityMode());

        if (properties.isAddMavenCentral()) {
            manager.addMavenCentral();
        }
        properties.getRepositories().forEach(manager::addRepository);

        return manager;
    }

    @Bean
    @ConditionalOnProperty(prefix = "attache", name = "auto-load", havingValue = "true", matchIfMissing = true)
    public ApplicationRunner attacheLibraryLoader(StandaloneLibraryManager libraryManager, AttacheProperties properties) {
        return args -> {
            if (properties.getLibraries().isEmpty()) {
                return;
            }

            List<Library> libraries = properties.getLibraries()
                    .stream()
                    .map(LibraryProperties::toLibrary)
                    .toList();

            try {
                libraryManager.loadLibraries(libraries);
            } catch (RuntimeException ex) {
                libraryManager.getLogger().error("Failed to load Attache libraries", ex);
                throw ex;
            }
        };
    }
}
