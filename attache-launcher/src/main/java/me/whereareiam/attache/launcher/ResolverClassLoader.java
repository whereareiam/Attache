package me.whereareiam.attache.launcher;

import me.whereareiam.attache.common.classloader.IsolatedClassLoader;
import me.whereareiam.attache.model.LibraryRequest;
import me.whereareiam.attache.resolution.DependencyResolver;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

/**
 * Shares only Attache's contract types with the application. Maven and HTTP libraries stay isolated.
 */
final class ResolverClassLoader extends IsolatedClassLoader {
	@Override
	protected @NotNull Class<?> loadClass(@NotNull String name, boolean resolve) throws ClassNotFoundException {
		if (name.startsWith(DependencyResolver.class.getPackageName() + '.'))
			return DependencyResolver.class.getClassLoader().loadClass(name);
		if (name.startsWith(LibraryRequest.class.getPackageName() + '.'))
			return LibraryRequest.class.getClassLoader().loadClass(name);

		return super.loadClass(name, resolve);
	}

	void closeAfterFailure(@NotNull Throwable failure) {
		try {
			close();
		} catch (IOException cleanup) {
			failure.addSuppressed(cleanup);
		}
	}
}
