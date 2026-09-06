/*
 * Derived from Libby Maven Resolver, Copyright (c) 2024 AlessioDP.
 * Licensed under the MIT License; see META-INF/licenses/libby-maven-resolver.txt.
 */
package me.whereareiam.attache.resolver;

import me.whereareiam.attache.model.ExcludedDependency;
import me.whereareiam.attache.model.LibraryRequest;
import me.whereareiam.attache.resolution.DependencyResolver;
import me.whereareiam.attache.resolution.model.ResolvedArtifact;
import org.apache.maven.repository.internal.MavenRepositorySystemUtils;
import org.eclipse.aether.DefaultRepositoryCache;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.collection.DependencyCollectionException;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.DependencyRequest;
import org.eclipse.aether.resolution.DependencyResolutionException;
import org.eclipse.aether.supplier.RepositorySystemSupplier;
import org.eclipse.aether.util.artifact.JavaScopes;
import org.eclipse.aether.util.filter.ScopeDependencyFilter;
import org.jetbrains.annotations.NotNull;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.HexFormat;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Attache's isolated Maven engine. Repository identities and session caches belong here.
 */
public final class MavenDependencyResolver implements DependencyResolver {
	private final RepositorySystem system;
	private final RepositorySystemSession session;
	private final ReentrantReadWriteLock lifecycle = new ReentrantReadWriteLock();
	private boolean closed;

	/**
	 * Creates a resolver using the supplied Maven-layout cache directory.
	 *
	 * @param directory cache shared with Attache's artifact downloader
	 */
	public MavenDependencyResolver(@NotNull Path directory) {
		system = new RepositorySystemSupplier().get();
		try {
			session = createSession(directory);
		} catch (RuntimeException | Error failure) {
			shutdownAfterFailure(failure);
			throw failure;
		}
	}

	private @NotNull RepositorySystemSession createSession(@NotNull Path directory) {
		var configured = MavenRepositorySystemUtils.newSession();
		configured.setLocalRepositoryManager(system.newLocalRepositoryManager(configured,
				new LocalRepository(directory.toAbsolutePath().toFile())));

		Properties properties = new Properties();
		properties.putAll(System.getProperties());
		configured.setSystemProperties(properties);
		configured.setConfigProperties(properties);
		configured.setCache(new DefaultRepositoryCache());
		configured.setReadOnly();

		return configured;
	}

	private void shutdownAfterFailure(@NotNull Throwable failure) {
		try {
			system.shutdown();
		} catch (RuntimeException | Error cleanup) {
			failure.addSuppressed(cleanup);
		}
	}

	@Override
	public @NotNull Collection<ResolvedArtifact> resolve(
			@NotNull LibraryRequest library,
			@NotNull Collection<String> repositories
	) {
		lifecycle.readLock().lock();
		try {
			if (closed) throw new IllegalStateException("Dependency resolver is closed");
			if (repositories.isEmpty()) throw new IllegalArgumentException("At least one repository is required");

			var remotes = repositories.stream().map(MavenDependencyResolver::repository).toList();
			var root = new DefaultArtifact(library.getGroupId(), library.getArtifactId(), library.getClassifier(), "jar", library.getVersion());
			var scopes = new ScopeDependencyFilter(List.of(JavaScopes.COMPILE, JavaScopes.RUNTIME), List.of());
			var graph = system.collectDependencies(session, new CollectRequest(new Dependency(root, JavaScopes.COMPILE), remotes));

			// The caller already downloads the root. Exclusions prevent transfers, not just classpath additions.
			var request = new DependencyRequest(graph.getRoot(), (node, parents) -> !parents.isEmpty()
					&& scopes.accept(node, parents)
					&& node.getArtifact().getExtension().equals("jar")
					&& !library.getExcludedTransitiveDependencies().contains(new ExcludedDependency(
							node.getArtifact().getGroupId(), node.getArtifact().getArtifactId())));

			return system.resolveDependencies(session, request).getArtifactResults().stream()
					.filter(result -> result.isResolved())
					.map(result -> {
						var artifact = result.getArtifact();
						return ResolvedArtifact.builder()
								.groupId(artifact.getGroupId())
								.artifactId(artifact.getArtifactId())
								.version(artifact.getVersion())
								.baseVersion(artifact.getBaseVersion())
								.classifier(artifact.getClassifier())
								.file(artifact.getFile().toPath())
								.build();
					})
					.toList();
		} catch (DependencyCollectionException | DependencyResolutionException failure) {
			throw new IllegalStateException("Could not resolve dependencies for " + library, failure);
		} finally {
			lifecycle.readLock().unlock();
		}
	}

	private static @NotNull RemoteRepository repository(@NotNull String url) {
		try {
			String normalized = url.endsWith("/") ? url : url + '/';
			String id = "attache-" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
					.digest(normalized.getBytes(StandardCharsets.UTF_8)));

			return new RemoteRepository.Builder(id, "default", normalized).build();
		} catch (NoSuchAlgorithmException failure) {
			throw new IllegalStateException("SHA-256 is unavailable", failure);
		}
	}

	@Override
	public void close() {
		lifecycle.writeLock().lock();
		try {
			if (closed) return;

			closed = true;
			system.shutdown();
		} finally {
			lifecycle.writeLock().unlock();
		}
	}
}
