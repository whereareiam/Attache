package me.whereareiam.attache.common;

import me.whereareiam.attache.LibraryAdapter;
import me.whereareiam.attache.LibraryManager;
import me.whereareiam.attache.LoggingHelper;
import me.whereareiam.attache.Repositories;
import me.whereareiam.attache.common.classloader.IsolatedClassLoader;
import me.whereareiam.attache.common.logging.Logger;
import me.whereareiam.attache.common.transitive.TransitiveDependencyHelper;
import me.whereareiam.attache.common.util.LibraryHelper;
import me.whereareiam.attache.common.util.RelocationHelper;
import me.whereareiam.attache.model.LibraryRequest;
import me.whereareiam.attache.model.RelocationRule;
import me.whereareiam.attache.type.Level;
import me.whereareiam.attache.type.ResolutionMode;
import me.whereareiam.attache.type.VerbosityMode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.net.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.Objects.requireNonNull;

/**
 * Base implementation of a runtime dependency manager.
 * Platform-specific implementations should extend this class and implement
 * the abstract methods.
 * <p>
 * This class implements {@link AutoCloseable} to properly clean up isolated classloaders.
 * Users should call {@link #close()} when done, or use try-with-resources.
 */
public abstract class BaseLibraryManager implements LibraryManager, AutoCloseable {
	protected static final String USER_AGENT = "Attache/1.0";

	/**
	 * Wrapped logger
	 */
	protected final Logger logger;

	/**
	 * Log adapter for the current platform
	 */
	protected final LoggingHelper loggingHelper;

	/**
	 * Directory where downloaded library jars are saved to
	 */
	protected final Path saveDirectory;

	/**
	 * Maven repositories used to resolve artifacts
	 */
	protected final Set<String> repositories = new LinkedHashSet<>();

	/**
	 * Lazily initialized relocation helper
	 */
	protected RelocationHelper relocator;

	/**
	 * Lazily initialized helper for transitive dependencies resolution
	 */
	protected TransitiveDependencyHelper transitiveDependencyHelper;

	/**
	 * Global isolated class loader for libraries
	 */
	protected final IsolatedClassLoader globalIsolatedClassLoader = new IsolatedClassLoader();

	/**
	 * Map of isolated class loaders and their IDs
	 */
	protected final Map<String, IsolatedClassLoader> isolatedLibraries = new HashMap<>();

	/**
	 * Repository resolution mode for libraries
	 */
	protected ResolutionMode resolutionMode = ResolutionMode.DEFAULT;

	/**
	 * Logging verbosity mode
	 */
	protected VerbosityMode verbosityMode = VerbosityMode.VERBOSE;

	/**
	 * Loaded libraries tracking for summary
	 */
	protected final List<LibraryRequest> loadedLibraries = new ArrayList<>();

	/**
	 * Failed libraries tracking for summary
	 */
	protected final Map<LibraryRequest, Exception> failedLibraries = new HashMap<>();

	/**
	 * Library adapter registry keyed by model type.
	 */
	protected final Map<Class<?>, LibraryAdapter<?>> libraryAdapters = new LinkedHashMap<>();

	/**
	 * Creates a new library manager.
	 *
	 * @param loggingHelper logging adapter
	 * @param dataDirectory data directory
	 * @param directoryName download directory name
	 */
	protected BaseLibraryManager(
			@NotNull LoggingHelper loggingHelper,
			@NotNull Path dataDirectory,
			@NotNull String directoryName
	) {
		this.loggingHelper = requireNonNull(loggingHelper, "loggingHelper");
		this.saveDirectory = requireNonNull(dataDirectory, "dataDirectory").toAbsolutePath().resolve(requireNonNull(directoryName, "directoryName"));
		this.logger = new Logger(loggingHelper);
		registerLibraryAdapter(LibraryRequest.class, request -> request);
	}

	/**
	 * Adds a file to the classloader classpath.
	 *
	 * @param file the file to add
	 */
	protected abstract void addToClasspath(@NotNull Path file);

	/**
	 * Adds a file to the isolated class loader.
	 * <p>
	 * Note: The IsolatedClassLoader instances are managed by this class and will be
	 * properly closed in the {@link #close()} method.
	 *
	 * @param library the library to add
	 * @param file    the file to add
	 */
	@SuppressWarnings("resource") // Classloaders are closed in close() method
	protected void addToIsolatedClasspath(@NotNull LibraryRequest library, @NotNull Path file) {
		IsolatedClassLoader classLoader;
		String loaderId = library.getLoader();
		if (loaderId != null) {
			classLoader = isolatedLibraries.computeIfAbsent(loaderId, s -> new IsolatedClassLoader());
		} else {
			classLoader = globalIsolatedClassLoader;
		}
		classLoader.addPath(file);
	}

	@Override
	@NotNull
	public LoggingHelper getLoggingHelper() {
		return loggingHelper;
	}

	@Override
	public void setLogLevel(@NotNull Level level) {
		logger.setLevel(level);
	}

	@Override
	@NotNull
	public Level getLogLevel() {
		return logger.getLevel();
	}

	@Override
	@NotNull
	public Path getSaveDirectory() {
		return saveDirectory;
	}

	@Override
	public <T> void registerLibraryAdapter(@NotNull Class<T> type, @NotNull LibraryAdapter<? super T> adapter) {
		requireNonNull(type, "type");
		requireNonNull(adapter, "adapter");
		synchronized (libraryAdapters) {
			libraryAdapters.put(type, adapter);
		}
	}

	@Override
	public void unregisterLibraryAdapter(@NotNull Class<?> type) {
		requireNonNull(type, "type");
		synchronized (libraryAdapters) {
			libraryAdapters.remove(type);
		}
	}

	@Override
	public boolean hasLibraryAdapter(@NotNull Class<?> type) {
		requireNonNull(type, "type");
		synchronized (libraryAdapters) {
			return libraryAdapters.containsKey(type);
		}
	}

	@Override
	public void addRepository(@NotNull String url) {
		String repo = requireNonNull(url, "url").endsWith("/") ? url : url + '/';
		synchronized (repositories) {
			repositories.add(repo);
		}
	}

	@Override
	public void addMavenCentral() {
		addRepository(Repositories.MAVEN_CENTRAL);
	}

	@Override
	public void addSonatype() {
		addRepository(Repositories.SONATYPE);
	}

	@Override
	public void addJitPack() {
		addRepository(Repositories.JITPACK);
	}

	@Override
	@NotNull
	public Collection<String> getRepositories() {
		List<String> urls;
		synchronized (repositories) {
			urls = new LinkedList<>(repositories);
		}

		return Collections.unmodifiableList(urls);
	}

	@Override
	public void setRepositoryResolutionMode(@NotNull ResolutionMode mode) {
		this.resolutionMode = requireNonNull(mode, "mode");
	}

	@Override
	@NotNull
	public ResolutionMode getRepositoryResolutionMode() {
		return resolutionMode;
	}

	@Override
	public void setVerbosityMode(@NotNull VerbosityMode mode) {
		this.verbosityMode = requireNonNull(mode, "mode");
	}

	@Override
	@NotNull
	public VerbosityMode getVerbosityMode() {
		return verbosityMode;
	}

	@Override
	public void printLoadedLibrariesSummary() {
		int loaded = loadedLibraries.size();
		int failed = failedLibraries.size();
		int total = loaded + failed;

		if (total == 0) {
			logger.info("No libraries were loaded.");
			return;
		}

		// Single-line summary
		String message = failed == 0
				? "Loaded " + loaded + " libraries successfully"
				: loaded == 0
				? "Failed to load " + failed + " libraries"
				: "Loaded " + loaded + " and failed" + failed + " libraries";

		if (failed > 0)
			logger.warn(message);
		else
			logger.info(message);

		// Show failed libraries for troubleshooting
		if (!failedLibraries.isEmpty() && verbosityMode != VerbosityMode.QUIET) {
			for (Map.Entry<LibraryRequest, Exception> entry : failedLibraries.entrySet()) {
				logger.warn("  Failed: " + entry.getKey() + " - " + entry.getValue().getMessage());
			}
		}
	}

	@NotNull
	protected LibraryRequest adaptLibrary(@NotNull Object library) {
		requireNonNull(library, "library");

		LibraryAdapter<Object> adapter = resolveAdapter(library.getClass());
		if (adapter == null) {
			throw new IllegalArgumentException("No LibraryAdapter registered for " + library.getClass().getName());
		}

		return adapter.adapt(library);
	}

	@SuppressWarnings("unchecked")
	@Nullable
	private LibraryAdapter<Object> resolveAdapter(@NotNull Class<?> type) {
		synchronized (libraryAdapters) {
			LibraryAdapter<?> adapter = libraryAdapters.get(type);
			if (adapter != null) {
				return (LibraryAdapter<Object>) adapter;
			}

			for (Map.Entry<Class<?>, LibraryAdapter<?>> entry : libraryAdapters.entrySet()) {
				if (entry.getKey().isAssignableFrom(type)) {
					return (LibraryAdapter<Object>) entry.getValue();
				}
			}
		}

		return null;
	}

	/**
	 * Gets all the possible download URLs for this library.
	 *
	 * @param library the library to resolve
	 * @return download URLs
	 */
	protected Collection<String> resolveLibrary(@NotNull LibraryRequest library) {
		Set<String> urls = new LinkedHashSet<>(requireNonNull(library, "library").getUrls());
		Collection<String> repos = resolveRepositories(library);

		for (String repository : repos) {
			urls.add(repository + LibraryHelper.getPath(library));
		}

		return Collections.unmodifiableSet(urls);
	}

	/**
	 * Resolves the repository URLs for this library.
	 *
	 * @param library the library to resolve repositories for
	 * @return the resolved repositories
	 */
	public Collection<String> resolveRepositories(@NotNull LibraryRequest library) {
		return switch (getRepositoryResolutionMode()) {
			case GLOBAL_FIRST -> Stream.of(
							getRepositories(),
							library.getRepositories(),
							library.getFallbackRepositories())
					.flatMap(Collection::stream)
					.collect(Collectors.toCollection(LinkedHashSet::new));
			case LIBRARY_FIRST -> Stream.of(
							library.getRepositories(),
							library.getFallbackRepositories(),
							getRepositories())
					.flatMap(Collection::stream)
					.collect(Collectors.toCollection(LinkedHashSet::new));
			default -> Stream.of(
							library.getRepositories(),
							getRepositories(),
							library.getFallbackRepositories())
					.flatMap(Collection::stream)
					.collect(Collectors.toCollection(LinkedHashSet::new));
		};
	}

	/**
	 * Downloads a library jar and returns the contents as a byte array.
	 *
	 * @param url the URL to the library jar
	 * @return downloaded jar as byte array or null if nothing was downloaded
	 */
	protected byte[] downloadLibraryBytes(@NotNull String url) {
		try {
			URLConnection connection = java.net.URI.create(requireNonNull(url, "url")).toURL().openConnection();

			connection.setConnectTimeout(5000);
			connection.setReadTimeout(5000);
			connection.setRequestProperty("User-Agent", USER_AGENT);

			try (InputStream in = connection.getInputStream()) {
				int len;
				byte[] buf = new byte[8192];
				ByteArrayOutputStream out = new ByteArrayOutputStream();

				try {
					while ((len = in.read(buf)) != -1) {
						out.write(buf, 0, len);
					}
				} catch (SocketTimeoutException e) {
					logger.warn("Download timed out: " + connection.getURL());
					return null;
				}

				// Log based on verbosity mode
				if (verbosityMode == VerbosityMode.VERBOSE)
					logger.info("Downloaded library " + connection.getURL());

				return out.toByteArray();
			}
		} catch (MalformedURLException e) {
			throw new IllegalArgumentException(e);
		} catch (FileNotFoundException e) {
			logger.debug("File not found: " + url);
			return null;
		} catch (SocketTimeoutException e) {
			logger.debug("Connect timed out: " + url);
			return null;
		} catch (UnknownHostException e) {
			logger.debug("Unknown host: " + url);
			return null;
		} catch (IOException e) {
			logger.debug("Unexpected IOException: " + e.getMessage(), e);
			return null;
		}
	}

	@Override
	@NotNull
	public <T> Path downloadLibrary(@NotNull T library) throws IOException, URISyntaxException, NoSuchAlgorithmException {
		LibraryRequest adaptedLibrary = adaptLibrary(library);
		// Normalize the library first (replace {}, normalize repos, etc.)
		LibraryRequest normalizedLibrary = LibraryHelper.normalize(adaptedLibrary);
		Path file = saveDirectory.resolve(LibraryHelper.getPath(normalizedLibrary));
		if (Files.exists(file)) {
			if (!normalizedLibrary.isSnapshot()) {
				// Relocate the file if needed
				if (normalizedLibrary.hasRelocations()) {
					String relocatedPath = LibraryHelper.getRelocatedPath(normalizedLibrary);
					if (relocatedPath != null)
						file = relocate(file, relocatedPath, normalizedLibrary.getRelocations());
				}

				return file;
			}

			// Delete the file since the Files.move call down below will fail if it exists
			Files.delete(file);
		}

		Collection<String> urls = resolveLibrary(normalizedLibrary);
		if (urls.isEmpty())
			throw new RuntimeException("Library '" + normalizedLibrary + "' couldn't be resolved, add a repository");

		MessageDigest md = null;
		if (normalizedLibrary.hasChecksum())
			md = MessageDigest.getInstance("SHA-256");

		Path out = file.resolveSibling(file.getFileName() + ".tmp");
		out.toFile().deleteOnExit();

		try {
			Files.createDirectories(file.getParent());

			for (String url : urls) {
				byte[] bytes = downloadLibraryBytes(url);
				if (bytes == null) continue;

				if (md != null) {
					byte[] checksum = md.digest(bytes);
					if (!Arrays.equals(checksum, normalizedLibrary.getChecksum())) {
						logger.warn("*** INVALID CHECKSUM ***");
						logger.warn(" Library :  " + normalizedLibrary);
						logger.warn(" URL :  " + url);
						logger.warn(" Expected :  " + Base64.getEncoder().encodeToString(normalizedLibrary.getChecksum()));
						logger.warn(" Actual :  " + Base64.getEncoder().encodeToString(checksum));
						continue;
					}
				}

				Files.write(out, bytes);
				Files.move(out, file);

				// Relocate the file if needed
				if (normalizedLibrary.hasRelocations()) {
					String relocatedPath = LibraryHelper.getRelocatedPath(normalizedLibrary);
					if (relocatedPath != null)
						file = relocate(file, relocatedPath, normalizedLibrary.getRelocations());
				}

				return file;
			}
		} finally {
			Files.deleteIfExists(out);
		}

		throw new RuntimeException("Failed to download library '" + normalizedLibrary + "'");
	}

	/**
	 * Processes the input jar and generates an output jar with the provided
	 * relocation rules applied, then returns the path to the relocated jar.
	 *
	 * @param in          input jar
	 * @param out         output jar
	 * @param relocations relocations to apply
	 * @return the relocated file
	 */
	@NotNull
	protected Path relocate(@NotNull Path in, @NotNull String out, @NotNull Collection<RelocationRule> relocations) {
		requireNonNull(in, "in");
		requireNonNull(out, "out");
		requireNonNull(relocations, "relocations");

		Path file = saveDirectory.resolve(out);
		if (Files.exists(file)) {
			return file;
		}

		Path tmpOut = file.resolveSibling(file.getFileName() + ".tmp");
		tmpOut.toFile().deleteOnExit();

		synchronized (this) {
			if (relocator == null) {
				relocator = new RelocationHelper(this);
			}
		}

		try {
			relocator.relocate(in, tmpOut, relocations);
			Files.move(tmpOut, file);

			if (verbosityMode == VerbosityMode.VERBOSE)
				logger.info("Relocations applied to " + in.getFileName());

			return file;
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		} finally {
			try {
				Files.deleteIfExists(tmpOut);
			} catch (IOException ignored) {
			}
		}
	}

	/**
	 * Resolves and loads transitive libraries for a given library. This method ensures that
	 * all libraries on which the provided library depends are properly loaded.
	 *
	 * @param library the primary library for which transitive libraries need to be resolved and loaded.
	 * @throws NullPointerException if the provided library is null.
	 * @see #loadLibrary(Object)
	 */
	protected void resolveTransitiveLibraries(@NotNull LibraryRequest library) {
		requireNonNull(library, "library");

		synchronized (this) {
			if (transitiveDependencyHelper == null)
				transitiveDependencyHelper = new TransitiveDependencyHelper(this, saveDirectory);
		}

		for (LibraryRequest transitiveLibrary : transitiveDependencyHelper.findTransitiveLibraries(library))
			loadLibrary(transitiveLibrary);
	}

	@Override
	public <T> void loadLibrary(@NotNull T library, @NotNull Path file) {
		LibraryRequest request = adaptLibrary(library);
		requireNonNull(file, "file");

		if (request.isIsolated()) {
			addToIsolatedClasspath(request, file);
		} else {
			addToClasspath(file);
		}
	}

	@Override
	public <T> void loadLibrary(@NotNull T library) {
		LibraryRequest request = adaptLibrary(library);

		// Log based on verbosity mode
		switch (verbosityMode) {
			case VERBOSE, NORMAL -> logger.info("Loading library " + request);
			case SUMMARY -> logger.debug("Loading library " + request);
			case QUIET -> { /* No logging */ }
		}

		try {
			Path file = downloadLibrary(request);

			// Resolve transitive dependencies before loading the main library
			if (request.isResolveTransitiveDependencies())
				resolveTransitiveLibraries(request);

			loadLibrary(request, file);

			// Track successful load
			loadedLibraries.add(request);

			// Log success based on verbosity
			if (verbosityMode == VerbosityMode.VERBOSE)
				logger.info("Successfully loaded " + request);
		} catch (IOException | URISyntaxException | NoSuchAlgorithmException e) {
			// Always log errors, track failure
			failedLibraries.put(request, e);
			throw new RuntimeException("Failed to load library " + request, e);
		}
	}

	@Override
	public final <T> void loadLibraries(@NotNull T... libraries) {
		for (T library : libraries)
			loadLibrary(library);

		// Auto-print summary for SUMMARY and QUIET modes
		if (verbosityMode == VerbosityMode.SUMMARY || verbosityMode == VerbosityMode.QUIET)
			printLoadedLibrariesSummary();
	}

	@Override
	public <T> void loadLibraries(@NotNull Collection<? extends T> libraries) {
		for (T library : libraries)
			loadLibrary(library);

		// Auto-print summary for SUMMARY and QUIET modes
		if (verbosityMode == VerbosityMode.SUMMARY || verbosityMode == VerbosityMode.QUIET)
			printLoadedLibrariesSummary();
	}

	/**
	 * Gets the logger for this library manager.
	 *
	 * @return the logger
	 */
	@NotNull
	public Logger getLogger() {
		return logger;
	}

	/**
	 * Gets the global isolated class loader.
	 *
	 * @return the isolated class loader
	 */
	@NotNull
	public IsolatedClassLoader getGlobalIsolatedClassLoader() {
		return globalIsolatedClassLoader;
	}

	/**
	 * Gets an isolated class loader by ID.
	 *
	 * @param loaderId the loader ID
	 * @return the isolated class loader or null if not found
	 */
	@Nullable
	public IsolatedClassLoader getIsolatedClassLoaderById(@NotNull String loaderId) {
		return isolatedLibraries.get(loaderId);
	}

	/**
	 * Closes all isolated classloaders managed by this library manager.
	 * <p>
	 * This should be called when the library manager is no longer needed,
	 * or use try-with-resources for automatic cleanup.
	 * <p>
	 * This fixes resource leaks and Windows file locking issues.
	 */
	@Override
	public void close() {
		// Close relocator if initialized
		if (relocator != null) {
			try {
				relocator.close();
			} catch (Exception e) {
				logger.error("Failed to close relocator", e);
			}
		}

		// Close global isolated classloader
		try {
			globalIsolatedClassLoader.close();
		} catch (Exception e) {
			logger.error("Failed to close global isolated classloader", e);
		}

		// Close all named isolated classloaders
		for (Map.Entry<String, IsolatedClassLoader> entry : isolatedLibraries.entrySet()) {
			try {
				entry.getValue().close();
			} catch (Exception e) {
				logger.error("Failed to close isolated classloader '" + entry.getKey() + "'", e);
			}
		}

		isolatedLibraries.clear();
	}
}

