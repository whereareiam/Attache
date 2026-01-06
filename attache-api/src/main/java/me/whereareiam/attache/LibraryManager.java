package me.whereareiam.attache;

import me.whereareiam.attache.type.Level;
import me.whereareiam.attache.type.ResolutionMode;
import me.whereareiam.attache.type.VerbosityMode;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;

/**
 * A runtime dependency manager for java applications.
 * <p>
 * The library manager can resolve a dependency jar through the configured
 * Maven repositories, download it into a local cache, relocate it and then
 * load it into the classloader classpath.
 * <p>
 * Transitive dependencies for a library can be automatically resolved and downloaded
 * by setting resolveTransitiveDependencies in the adapted request. When enabled,
 * all transitive dependencies will be loaded before the main library.
 * <p>
 * It's recommended that libraries are relocated to prevent any namespace
 * conflicts with different versions of the same library bundled with other
 * java applications or maybe even bundled with the server itself.
 *
 */
@SuppressWarnings("unused")
public interface LibraryManager {
	/**
	 * Registers a library adapter for the given model class.
	 *
	 * @param type    the model class to adapt
	 * @param adapter the adapter implementation
	 * @param <T>     the model type
	 */
	<T> void registerLibraryAdapter(@NotNull Class<T> type, @NotNull LibraryAdapter<? super T> adapter);

	/**
	 * Removes a previously registered library adapter.
	 *
	 * @param type the model class
	 */
	void unregisterLibraryAdapter(@NotNull Class<?> type);

	/**
	 * Checks whether a library adapter exists for the given model type.
	 *
	 * @param type the model class
	 * @return true if an adapter is registered
	 */
	boolean hasLibraryAdapter(@NotNull Class<?> type);

	/**
	 * Gets the configured log adapter.
	 *
	 * @return the log adapter
	 */
	@NotNull
	LoggingHelper getLoggingHelper();

	/**
	 * Sets the log level for this library manager.
	 *
	 * @param level the log level to set
	 */
	void setLogLevel(@NotNull Level level);

	/**
	 * Gets the current log level.
	 *
	 * @return the current log level
	 */
	@NotNull
	Level getLogLevel();

	/**
	 * Gets the directory where library jars are saved to.
	 *
	 * @return the save directory
	 */
	@NotNull
	Path getSaveDirectory();

	/**
	 * Adds a repository URL to this library manager.
	 * <p>
	 * Repositories are searched in the order they are added.
	 * <p>
	 * Common repository URLs can be found in the {@link Repositories} class as constants.
	 *
	 * @param url the repository URL
	 */
	void addRepository(@NotNull String url);

	/**
	 * Adds Maven Central repository.
	 */
	void addMavenCentral();

	/**
	 * Adds Sonatype OSS repository.
	 */
	void addSonatype();

	/**
	 * Adds JitPack repository.
	 */
	void addJitPack();

	/**
	 * Gets all configured repositories.
	 *
	 * @return collection of repository URLs
	 */
	@NotNull
	Collection<String> getRepositories();

	/**
	 * Downloads a library jar and caches it in the save directory if it doesn't already exist.
	 *
	 * @param library the library model to download
	 * @return the path to the downloaded library jar
	 * @throws IOException              if an I/O error occurs
	 * @throws URISyntaxException       if the download URL is malformed
	 * @throws NoSuchAlgorithmException if SHA-256 algorithm is not available
	 */
	@NotNull
	<T> Path downloadLibrary(@NotNull T library) throws IOException, URISyntaxException, NoSuchAlgorithmException;

	/**
	 * Loads a library jar into the plugin's classpath.
	 *
	 * @param library the library model to load
	 * @param file    the path to the library jar
	 */
	<T> void loadLibrary(@NotNull T library, @NotNull Path file);

	/**
	 * Downloads and loads a library into the plugin's classpath.
	 *
	 * @param library the library model to download and load
	 */
	<T> void loadLibrary(@NotNull T library);

	/**
	 * Downloads and loads all provided libraries in parallel.
	 *
	 * @param libraries the library models to download and load
	 */
	<T> void loadLibraries(@NotNull T... libraries);

	/**
	 * Downloads and loads all provided libraries in parallel.
	 *
	 * @param libraries the library models to download and load
	 */
	<T> void loadLibraries(@NotNull Collection<? extends T> libraries);

	/**
	 * Sets the repository resolution mode.
	 *
	 * @param mode the resolution mode
	 */
	void setRepositoryResolutionMode(@NotNull ResolutionMode mode);

	/**
	 * Gets the current repository resolution mode.
	 *
	 * @return the current resolution mode
	 */
	@NotNull
	ResolutionMode getRepositoryResolutionMode();

	/**
	 * Sets the logging verbosity mode.
	 * <p>
	 * Controls how much information is logged during library operations.
	 *
	 * @param mode the verbosity mode
	 */
	void setVerbosityMode(@NotNull VerbosityMode mode);

	/**
	 * Gets the current logging verbosity mode.
	 *
	 * @return the current verbosity mode
	 */
	@NotNull
	VerbosityMode getVerbosityMode();

	/**
	 * Prints a summary of all loaded libraries.
	 * <p>
	 * Useful when using SUMMARY or QUIET verbosity modes.
	 */
	void printLoadedLibrariesSummary();
}

