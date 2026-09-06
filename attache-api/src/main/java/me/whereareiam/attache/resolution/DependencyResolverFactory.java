package me.whereareiam.attache.resolution;

import me.whereareiam.attache.LibraryManager;
import org.jetbrains.annotations.NotNull;

/**
 * Creates a manager-owned resolver on its first transitive dependency request.
 * Implementations may initialize an embedded engine or download an isolated one.
 */
@FunctionalInterface
public interface DependencyResolverFactory {
	/**
	 * Creates a resolver using the manager's download facilities and cache directory.
	 * The manager closes the returned resolver when it is closed.
	 *
	 * @param manager owner of the resolver and its dependency cache
	 * @return a new resolver owned by this manager
	 */
	@NotNull DependencyResolver create(@NotNull LibraryManager manager);
}
