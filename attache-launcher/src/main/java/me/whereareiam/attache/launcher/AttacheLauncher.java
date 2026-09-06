package me.whereareiam.attache.launcher;

import me.whereareiam.attache.LibraryManager;
import me.whereareiam.attache.resolution.DependencyResolver;
import me.whereareiam.attache.resolution.DependencyResolverFactory;
import org.jetbrains.annotations.NotNull;

/**
 * Supplies the downloadable, isolated Maven resolver to a library manager.
 * Construction performs no downloads; initialization occurs when the manager requests resolution.
 */
public final class AttacheLauncher implements DependencyResolverFactory {
	@Override
	public @NotNull DependencyResolver create(@NotNull LibraryManager manager) {
		return new IsolatedDependencyResolver(manager, manager.getSaveDirectory());
	}
}
