/*
 * Derived from Libby Maven Resolver, Copyright (c) 2024 AlessioDP.
 * Licensed under the MIT License; see META-INF/licenses/libby-maven-resolver.txt.
 */
package me.whereareiam.attache.resolver;

import me.whereareiam.attache.model.LibraryRequest;
import me.whereareiam.attache.resolution.DependencyResolver;
import me.whereareiam.attache.resolution.model.ResolvedArtifact;
import me.whereareiam.attache.resolution.model.ResolvedDependency;
import org.apache.maven.repository.internal.MavenRepositorySystemUtils;
import org.eclipse.aether.DefaultRepositoryCache;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.collection.DependencyCollectionException;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.DependencyFilter;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.graph.Exclusion;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.DependencyRequest;
import org.eclipse.aether.resolution.DependencyResolutionException;
import org.eclipse.aether.supplier.RepositorySystemSupplier;
import org.eclipse.aether.util.artifact.ArtifactIdUtils;
import org.eclipse.aether.util.artifact.JavaScopes;
import org.eclipse.aether.util.filter.ScopeDependencyFilter;
import org.eclipse.aether.util.graph.transformer.ConflictResolver;
import org.jetbrains.annotations.NotNull;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Attache's isolated Maven engine. Repository identities and session caches belong here.
 */
public final class MavenDependencyResolver implements DependencyResolver {
	private static final ScopeDependencyFilter RUNTIME_SCOPES = new ScopeDependencyFilter(List.of(JavaScopes.COMPILE, JavaScopes.RUNTIME), List.of());

	private final RepositorySystem system;
	private final RepositorySystemSession session;
	/**
	 * Keeps the versions that lose a conflict in the graph, so a graph shows every library that depends on an
	 * artifact, also where another version of it won.
	 */
	private final RepositorySystemSession graphSession;
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
			graphSession = createGraphSession(session);
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

	private static @NotNull RepositorySystemSession createGraphSession(@NotNull RepositorySystemSession session) {
		var configured = new DefaultRepositorySystemSession(session);
		configured.setConfigProperty(ConflictResolver.CONFIG_PROP_VERBOSE, true);
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
			var graph = system.collectDependencies(session, new CollectRequest(dependency(library, true), remotes));

			// The caller already downloads the root.
			var request = new DependencyRequest(graph.getRoot(), (node, parents) -> !parents.isEmpty() && accepted(node, parents));

			return system.resolveDependencies(session, request).getArtifactResults().stream()
					.filter(result -> result.isResolved())
					.map(result -> resolvedArtifact(result.getArtifact()))
					.toList();
		} catch (DependencyCollectionException | DependencyResolutionException failure) {
			throw new IllegalStateException("Could not resolve dependencies for " + library, failure);
		} finally {
			lifecycle.readLock().unlock();
		}
	}

	@Override
	public @NotNull List<ResolvedDependency> resolve(
			@NotNull List<LibraryRequest> libraries,
			@NotNull Collection<String> repositories
	) {
		lifecycle.readLock().lock();
		try {
			if (closed) throw new IllegalStateException("Dependency resolver is closed");
			if (repositories.isEmpty()) throw new IllegalArgumentException("At least one repository is required");
			if (libraries.isEmpty()) return List.of();

			var remotes = repositories.stream().map(MavenDependencyResolver::repository).toList();
			var dependencies = libraries.stream()
					.map(library -> dependency(library, library.isResolveTransitiveDependencies()))
					.toList();
			var graph = system.collectDependencies(graphSession, new CollectRequest(dependencies, List.of(), remotes)).getRoot();
			var dependents = dependents(graph);

			// Versions that lost a conflict stay in a verbose graph; only the winners are resolved.
			DependencyFilter winners = (node, parents) -> !parents.isEmpty()
					&& node.getData().get(ConflictResolver.NODE_DATA_WINNER) == null
					&& accepted(node, parents);
			var results = system.resolveDependencies(graphSession, new DependencyRequest(graph, winners)).getArtifactResults();

			List<ResolvedDependency> resolved = new ArrayList<>(results.size());
			for (var result : results) {
				if (!result.isResolved()) continue;

				var artifact = result.getArtifact();
				var builder = ResolvedDependency.builder().artifact(resolvedArtifact(artifact));
				dependents.getOrDefault(ArtifactIdUtils.toVersionlessId(artifact), Set.of())
						.forEach(index -> builder.dependent(libraries.get(index)));
				resolved.add(builder.build());
			}

			return resolved;
		} catch (DependencyCollectionException | DependencyResolutionException failure) {
			throw new IllegalStateException("Could not resolve dependencies for " + libraries, failure);
		} finally {
			lifecycle.readLock().unlock();
		}
	}

	/**
	 * Finds, for every artifact of a verbose graph, the requested libraries that depend on it in any version. A
	 * library that reaches a losing version also depends on the winner's dependencies.
	 */
	private static @NotNull Map<String, Set<Integer>> dependents(@NotNull DependencyNode graph) {
		Map<String, DependencyNode> winners = new HashMap<>();
		collectWinners(graph, winners);

		Map<String, Set<Integer>> dependents = new LinkedHashMap<>();
		List<DependencyNode> libraries = graph.getChildren();
		for (int index = 0; index < libraries.size(); index++) {
			Set<String> visited = new HashSet<>();
			var queue = new ArrayDeque<DependencyNode>();
			queue.add(libraries.get(index));

			while (!queue.isEmpty()) {
				DependencyNode node = queue.poll();
				String key = ArtifactIdUtils.toVersionlessId(node.getArtifact());
				if (!visited.add(key)) continue;

				dependents.computeIfAbsent(key, ignored -> new TreeSet<>()).add(index);
				DependencyNode winner = winners.getOrDefault(key, node);
				for (DependencyNode child : winner.getChildren()) {
					if (accepted(child, List.of())) queue.add(child);
				}
			}
		}

		return dependents;
	}

	private static void collectWinners(@NotNull DependencyNode node, @NotNull Map<String, DependencyNode> winners) {
		for (DependencyNode child : node.getChildren()) {
			if (child.getData().get(ConflictResolver.NODE_DATA_WINNER) == null)
				winners.putIfAbsent(ArtifactIdUtils.toVersionlessId(child.getArtifact()), child);

			collectWinners(child, winners);
		}
	}

	/**
	 * Accepts the compile and runtime jars of a graph.
	 */
	private static boolean accepted(@NotNull DependencyNode node, @NotNull List<DependencyNode> parents) {
		return RUNTIME_SCOPES.accept(node, parents) && node.getArtifact().getExtension().equals("jar");
	}

	private static @NotNull Dependency dependency(@NotNull LibraryRequest library, boolean transitive) {
		var artifact = new DefaultArtifact(library.getGroupId(), library.getArtifactId(), library.getClassifier(), "jar", library.getVersion());
		// Exclusions apply while collecting, as Maven's do: an excluded artifact takes its own dependencies with it.
		var exclusions = transitive
				? library.getExcludedTransitiveDependencies().stream()
						.map(excluded -> new Exclusion(excluded.getGroupId(), excluded.getArtifactId(), "*", "*"))
						.toList()
				: List.of(new Exclusion("*", "*", "*", "*"));

		return new Dependency(artifact, JavaScopes.COMPILE, false, exclusions);
	}

	private static @NotNull ResolvedArtifact resolvedArtifact(@NotNull Artifact artifact) {
		return ResolvedArtifact.builder()
				.groupId(artifact.getGroupId())
				.artifactId(artifact.getArtifactId())
				.version(artifact.getVersion())
				.baseVersion(artifact.getBaseVersion())
				.classifier(artifact.getClassifier())
				.file(artifact.getFile().toPath())
				.build();
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
