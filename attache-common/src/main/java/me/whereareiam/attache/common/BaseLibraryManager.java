package me.whereareiam.attache.common;

import me.whereareiam.attache.LibraryAdapter;
import me.whereareiam.attache.LibraryBatchLoader;
import me.whereareiam.attache.LibraryManager;
import me.whereareiam.attache.LoggingHelper;
import me.whereareiam.attache.Repositories;
import me.whereareiam.attache.common.classloader.IsolatedClassLoader;
import me.whereareiam.attache.common.descriptor.ClasspathDescriptorLoader;
import me.whereareiam.attache.common.loader.ParallelLibraryLoader;
import me.whereareiam.attache.common.loader.SequentialLibraryLoader;
import me.whereareiam.attache.common.logging.Logger;
import me.whereareiam.attache.common.transitive.TransitiveDependencyHelper;
import me.whereareiam.attache.common.util.LibraryHelper;
import me.whereareiam.attache.descriptor.AttacheDescriptorLibrary;
import me.whereareiam.attache.model.LibraryRequest;
import me.whereareiam.attache.model.RelocationRule;
import me.whereareiam.attache.type.Level;
import me.whereareiam.attache.type.LibraryLoadMode;
import me.whereareiam.attache.type.ResolutionMode;
import me.whereareiam.attache.type.VerbosityMode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.lang.reflect.Method;
import java.net.*;
import java.nio.file.Path;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
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

	private final ArtifactDownloadCoordinator artifactDownloadCoordinator;
	private final RelocationCoordinator relocationCoordinator;
	private final LibraryBatchLoader libraryBatchLoader;
	private final LibraryLoadMode libraryLoadMode;
	private final ClasspathDescriptorLoader classpathDescriptorLoader = new ClasspathDescriptorLoader();
	private boolean classpathDescriptorsLoaded;

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
		this.artifactDownloadCoordinator = new ArtifactDownloadCoordinator(this, this.saveDirectory);
		this.relocationCoordinator = new RelocationCoordinator(this, this.saveDirectory);
		this.libraryLoadMode = resolveLibraryLoadMode();
		this.libraryBatchLoader = createLibraryBatchLoader(this.libraryLoadMode);
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
	public LibraryRequest adaptLibrary(@NotNull Object library) {
		requireNonNull(library, "library");

		LibraryAdapter<Object> adapter = resolveAdapter(library.getClass());
		if (adapter == null) {
			throw new IllegalArgumentException("No LibraryAdapter registered for " + library.getClass().getName());
		}

		return adapter.adapt(library);
	}

	@Nullable
	public String findSkipReason(@NotNull LibraryRequest request) {
		String mavenMetadata = findPresentMavenMetadata(request);
		if (mavenMetadata != null) {
			return "maven metadata present: " + mavenMetadata;
		}

		String classpathJar = findPresentClasspathJar(request);
		if (classpathJar != null) {
			return "classpath jar present: " + classpathJar;
		}

		return null;
	}

	@Nullable
	private String findPresentMavenMetadata(@NotNull LibraryRequest request) {
		if (!request.isSkipIfPresent() || request.hasRelocations()) {
			return null;
		}

		String groupId = LibraryHelper.replaceWithDots(request.getGroupId());
		String artifactId = LibraryHelper.replaceWithDots(request.getArtifactId());
		String resource = "META-INF/maven/" + groupId.replace('.', '/') + "/" + artifactId + "/pom.properties";

		ClassLoader classLoader = getClass().getClassLoader();
		return isResourcePresent(classLoader, resource) ? resource : null;
	}

	@Nullable
	private String findPresentClasspathJar(@NotNull LibraryRequest request) {
		if (!request.isSkipIfPresent() || request.hasRelocations())
			return null;

		String fileName = buildExpectedJarName(request);
		if (fileName == null)
			return null;

		ClassLoader classLoader = getClass().getClassLoader();
		String jarPath = findMatchingJarPath(classLoader, fileName);
		if (jarPath != null)
			return jarPath;

		String classPath = System.getProperty("java.class.path");
		if (classPath == null || classPath.isBlank())
			return null;

		for (String entry : classPath.split(File.pathSeparator))
			if (entry.endsWith(fileName))
				return entry;

		return null;
	}

	@Nullable
	private String buildExpectedJarName(@NotNull LibraryRequest request) {
		String artifactId = LibraryHelper.replaceWithDots(request.getArtifactId());
		String version = LibraryHelper.replaceWithDots(request.getVersion());
		if (artifactId.isBlank() || version.isBlank())
			return null;

		StringBuilder name = new StringBuilder(artifactId).append('-').append(version);
		if (request.hasClassifier()) name.append('-').append(request.getClassifier());

		return name.append(".jar").toString();
	}

	@Nullable
	private String findMatchingJarPath(@Nullable ClassLoader classLoader, @NotNull String fileName) {
		if (classLoader == null) return null;

		for (ClassLoader current = classLoader; current != null; current = current.getParent()) {
			if (current instanceof URLClassLoader urlClassLoader) {
				String found = matchJarUrl(urlClassLoader.getURLs(), fileName);
				if (found != null) return found;
			}

			URL[] urls = reflectUrls(current);
			String found = matchJarUrl(urls, fileName);
			if (found != null) return found;
		}

		return null;
	}

	@Nullable
	private String matchJarUrl(@Nullable URL[] urls, @NotNull String fileName) {
		if (urls == null) return null;

		for (URL url : urls) {
			if (url == null)
				continue;

			String path = url.getPath();
			if (path != null && path.endsWith(fileName))
				return path;
		}

		return null;
	}

	@Nullable
	private URL[] reflectUrls(@NotNull ClassLoader classLoader) {
		try {
			Method method = classLoader.getClass().getMethod("getURLs");
			Object result = method.invoke(classLoader);

			return (URL[]) result;
		} catch (ReflectiveOperationException | ClassCastException ignored) {
			return null;
		}
	}

	private boolean isResourcePresent(@Nullable ClassLoader classLoader, @NotNull String resource) {
		String normalized = resource.startsWith("/") ? resource.substring(1) : resource;
		if (classLoader != null) {
			for (ClassLoader current = classLoader; current != null; current = current.getParent()) {
				if (current.getResource(normalized) != null)
					return true;
			}
		}

		return ClassLoader.getSystemResource(normalized) != null;
	}

	public void logSkippedLibrary(@NotNull LibraryRequest request, @NotNull String reason) {
		String message = "Skipping library " + request + " (" + reason + ")";
		switch (verbosityMode) {
			case VERBOSE, NORMAL -> logger.info(message);
			case SUMMARY -> logger.debug(message);
		}
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

	@NotNull
	DownloadAttempt downloadLibraryAttempt(@NotNull String url) {
		try {
			URLConnection connection = java.net.URI.create(requireNonNull(url, "url")).toURL().openConnection();

			connection.setConnectTimeout(5000);
			connection.setReadTimeout(5000);
			connection.setRequestProperty("User-Agent", USER_AGENT);

			if (connection instanceof HttpURLConnection httpConnection) {
				int responseCode = httpConnection.getResponseCode();
				if (responseCode >= HttpURLConnection.HTTP_BAD_REQUEST) {
					return createHttpDownloadFailure(httpConnection, responseCode);
				}
			}

			try (InputStream in = connection.getInputStream()) {
				int len;
				byte[] buf = new byte[8192];
				ByteArrayOutputStream out = new ByteArrayOutputStream();

				try {
					while ((len = in.read(buf)) != -1) {
						out.write(buf, 0, len);
					}
				} catch (SocketTimeoutException e) {
					return DownloadAttempt.failure(Level.WARN, "Download timed out: " + connection.getURL(), false);
				}

				if (verbosityMode == VerbosityMode.VERBOSE)
					logger.info("Downloaded library " + connection.getURL());

				return DownloadAttempt.success(out.toByteArray());
			}
		} catch (MalformedURLException e) {
			throw new IllegalArgumentException(e);
		} catch (FileNotFoundException e) {
			return DownloadAttempt.failure(Level.INFO, "File not found: " + url, true);
		} catch (SocketTimeoutException e) {
			return DownloadAttempt.failure(Level.WARN, "Download timed out: " + url, false);
		} catch (UnknownHostException e) {
			return DownloadAttempt.failure(Level.WARN, "Unknown host: " + url, false);
		} catch (IOException e) {
			return DownloadAttempt.failure(Level.WARN, "Download failed: " + url + " (" + e.getMessage() + ")", false);
		}
	}

	/**
	 * Downloads a library jar and returns the contents as a byte array.
	 *
	 * @param url the URL to the library jar
	 * @return downloaded jar as byte array or null if nothing was downloaded
	 */
	protected byte[] downloadLibraryBytes(@NotNull String url) {
		return downloadLibraryAttempt(url).getBytes();
	}

	@NotNull
	private DownloadAttempt createHttpDownloadFailure(@NotNull HttpURLConnection connection, int responseCode) throws IOException {
		String url = connection.getURL().toString();
		String responseMessage = connection.getResponseMessage();
		String suffix = responseMessage == null || responseMessage.isBlank()
				? ""
				: " " + responseMessage;

		return responseCode == HttpURLConnection.HTTP_NOT_FOUND
				? DownloadAttempt.failure(Level.INFO, "File not found (" + responseCode + suffix + "): " + url, true)
				: DownloadAttempt.failure(Level.WARN, "Download failed (" + responseCode + suffix + "): " + url, false);
	}

	@Override
	@NotNull
	public <T> Path downloadLibrary(@NotNull T library) throws IOException, URISyntaxException, NoSuchAlgorithmException {
		LibraryRequest adaptedLibrary = adaptLibrary(library);
		LibraryRequest normalizedLibrary = LibraryHelper.normalize(adaptedLibrary);
		Path file = artifactDownloadCoordinator.download(normalizedLibrary);

		if (!normalizedLibrary.hasRelocations())
			return file;

		String relocatedPath = LibraryHelper.getRelocatedPath(normalizedLibrary);
		if (relocatedPath == null)
			return file;

		return relocate(file, relocatedPath, normalizedLibrary.getRelocations());
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
		return relocationCoordinator.relocate(in, out, relocations);
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

		String skipReason = findSkipReason(request);
		if (skipReason != null) {
			logSkippedLibrary(request, skipReason);
			return;
		}

		if (request.isIsolated()) {
			addToIsolatedClasspath(request, file);
		} else {
			addToClasspath(file);
		}
	}

	@Override
	public <T> void loadLibrary(@NotNull T library) {
		LibraryRequest request = adaptLibrary(library);

		String skipReason = findSkipReason(request);
		if (skipReason != null) {
			logSkippedLibrary(request, skipReason);
			return;
		}

		// Log based on verbosity mode
		switch (verbosityMode) {
			case VERBOSE, NORMAL -> logger.info("Loading library " + request);
			case SUMMARY -> logger.debug("Loading library " + request);
			case QUIET -> { /* No logging */ }
		}

		try {
			Path file = downloadLibrary(request);
			loadDownloadedLibrary(request, file);
		} catch (IOException | URISyntaxException | NoSuchAlgorithmException e) {
			recordLoadFailure(request, e);
			throw new RuntimeException("Failed to load library " + request, e);
		} catch (RuntimeException e) {
			recordLoadFailure(request, e);
			throw e;
		}
	}

    @Override
	public final <T> void loadLibraries(@NotNull T... libraries) {
		loadLibraries(Arrays.asList(libraries));
	}

	@Override
	public <T> void loadLibraries(@NotNull Collection<? extends T> libraries) {
		libraryBatchLoader.loadLibraries(libraries);
	}

	@NotNull
	public LibraryLoadMode getLibraryLoadMode() {
		return libraryLoadMode;
	}

	public void logLibraryLoadStart(@NotNull LibraryRequest request) {
		switch (verbosityMode) {
			case VERBOSE, NORMAL -> logger.info("Loading library " + request);
			case SUMMARY -> logger.debug("Loading library " + request);
			case QUIET -> { /* No logging */ }
		}
	}

	public void loadDownloadedLibrary(@NotNull LibraryRequest request, @NotNull Path file) {
		if (request.isResolveTransitiveDependencies())
			resolveTransitiveLibraries(request);

		loadLibrary(request, file);
		loadedLibraries.add(request);

		if (verbosityMode == VerbosityMode.VERBOSE)
			logger.info("Successfully loaded " + request);
	}

	public void recordLoadFailure(@NotNull LibraryRequest request, @NotNull Exception exception) {
		failedLibraries.put(request, exception);
	}

	@NotNull
	public <T> T awaitFuture(
			@NotNull CompletableFuture<T> future,
			@NotNull String interruptedMessage
	) throws IOException, URISyntaxException, NoSuchAlgorithmException {
		try {
			return future.get();
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new RuntimeException(interruptedMessage, e);
		} catch (ExecutionException e) {
			throw rethrowAsyncCause(e.getCause());
		}
	}

	@NotNull
	private LibraryBatchLoader createLibraryBatchLoader(@NotNull LibraryLoadMode loadMode) {
		return switch (loadMode) {
			case PARALLEL -> new ParallelLibraryLoader(this);
			case SEQUENTIAL -> new SequentialLibraryLoader(this);
		};
	}

	@NotNull
	private LibraryLoadMode resolveLibraryLoadMode() {
		String configuredValue = System.getProperty(LibraryLoadMode.SYSTEM_PROPERTY);
		if (configuredValue == null || configuredValue.isBlank())
			configuredValue = System.getenv(LibraryLoadMode.ENV_VARIABLE);

		try {
			return LibraryLoadMode.resolve(configuredValue);
		} catch (IllegalArgumentException e) {
			logger.warn("Invalid load mode '" + configuredValue + "', defaulting to PARALLEL");
			return LibraryLoadMode.PARALLEL;
		}
	}

	@NotNull
	private RuntimeException rethrowAsyncCause(Throwable cause) throws IOException, URISyntaxException, NoSuchAlgorithmException {
		Throwable unwrapped = unwrapAsyncCause(cause);
		if (unwrapped instanceof IOException ioException)
			throw ioException;
		if (unwrapped instanceof URISyntaxException uriSyntaxException)
			throw uriSyntaxException;
		if (unwrapped instanceof NoSuchAlgorithmException noSuchAlgorithmException)
			throw noSuchAlgorithmException;
		if (unwrapped instanceof RuntimeException runtimeException)
			throw runtimeException;

		throw new RuntimeException("Unexpected asynchronous failure", unwrapped);
	}

	@NotNull
	private Throwable unwrapAsyncCause(Throwable cause) {
		Throwable current = cause;
		while (current instanceof CompletionException completionException && completionException.getCause() != null)
			current = completionException.getCause();

		return current;
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

	@NotNull
	protected ClassLoader getDescriptorClassLoader() {
		return getClass().getClassLoader();
	}

	@Override
	public final synchronized void loadDescriptors() {
		if (classpathDescriptorsLoaded) {
			return;
		}

		List<ClasspathDescriptorLoader.LoadedDescriptorFragment> fragments = classpathDescriptorLoader.load(
				getDescriptorClassLoader(),
				getClass()
		);
		if (fragments.isEmpty()) {
			classpathDescriptorsLoaded = true;
			return;
		}

		boolean addMavenCentral = false;
		LinkedHashSet<String> fragmentRepositories = new LinkedHashSet<>();
		LinkedHashMap<String, DescriptorLibraryOrigin> libraries = new LinkedHashMap<>();

		for (ClasspathDescriptorLoader.LoadedDescriptorFragment loaded : fragments) {
			if (loaded.fragment().isAddMavenCentral()) {
				addMavenCentral = true;
			}
			fragmentRepositories.addAll(loaded.fragment().getRepositories());

			for (AttacheDescriptorLibrary library : loaded.fragment().getLibraries()) {
				String key = library.coordinatesKey();
				DescriptorLibraryOrigin existing = libraries.get(key);
				if (existing == null) {
					libraries.put(key, new DescriptorLibraryOrigin(library, loaded.location()));
					continue;
				}

				if (!existing.library.sameDefinition(library)) {
					throw new IllegalStateException("Conflicting Attache descriptor definitions for " + key
							+ " in " + existing.location + " and " + loaded.location());
				}
			}
		}

		if (addMavenCentral) {
			addMavenCentral();
		}
		fragmentRepositories.forEach(this::addRepository);

		if (!libraries.isEmpty()) {
			List<LibraryRequest> requests = libraries.values().stream()
					.map(origin -> origin.library.toLibraryRequest())
					.toList();
			loadLibraries(requests);
		}

		classpathDescriptorsLoaded = true;
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
		relocationCoordinator.close();

		if (transitiveDependencyHelper != null) {
			try {
				transitiveDependencyHelper.close();
			} catch (Exception e) {
				logger.error("Failed to close transitive dependency helper", e);
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

	private static final class DescriptorLibraryOrigin {
		private final AttacheDescriptorLibrary library;
		private final String location;

		private DescriptorLibraryOrigin(@NotNull AttacheDescriptorLibrary library, @NotNull String location) {
			this.library = library;
			this.location = location;
		}
	}

	static final class DownloadAttempt {
		private final byte[] bytes;
		private final Level level;
		private final String message;
		private final boolean notFound;

		private DownloadAttempt(byte[] bytes, Level level, String message, boolean notFound) {
			this.bytes = bytes;
			this.level = level;
			this.message = message;
			this.notFound = notFound;
		}

		@NotNull
		private static DownloadAttempt success(byte @NotNull [] bytes) {
			return new DownloadAttempt(requireNonNull(bytes, "bytes"), null, null, false);
		}

		@NotNull
		private static DownloadAttempt failure(@NotNull Level level, @NotNull String message, boolean notFound) {
			return new DownloadAttempt(null, requireNonNull(level, "level"), requireNonNull(message, "message"), notFound);
		}

		byte @Nullable [] getBytes() {
			return bytes;
		}

		boolean isSuccess() {
			return bytes != null;
		}

		@Nullable
		Level getLevel() {
			return level;
		}

		@Nullable
		String getMessage() {
			return message;
		}

		boolean isNotFound() {
			return notFound;
		}
	}
}
