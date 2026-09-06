package me.whereareiam.attache.common.util;

import me.whereareiam.attache.LibraryManager;
import me.whereareiam.attache.common.classloader.IsolatedClassLoader;
import me.whereareiam.attache.model.LibraryRequest;
import me.whereareiam.attache.model.RelocationRule;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

import static java.util.Objects.requireNonNull;
import static me.whereareiam.attache.common.util.LibraryHelper.replaceWithDots;

/**
 * A reflection-based helper for relocating library jars. It automatically
 * downloads and invokes Luck's Jar Relocator to perform jar relocations.
 *
 * @see <a href="https://github.com/lucko/jar-relocator">Luck's Jar Relocator</a>
 */
public class RelocationHelper {
	/**
	 * me.lucko.jarrelocator.JarRelocator class name for reflections
	 */
	private static final String JAR_RELOCATOR_CLASS = replaceWithDots("me{}lucko{}jarrelocator{}JarRelocator");

	/**
	 * me.lucko.jarrelocator.Relocation class name for reflections
	 */
	private static final String RELOCATION_CLASS = replaceWithDots("me{}lucko{}jarrelocator{}Relocation");

	/**
	 * Reflected constructor for creating new jar relocator instances
	 */
	private final Constructor<?> jarRelocatorConstructor;

	/**
	 * Reflected method for running a jar relocator
	 */
	private final Method jarRelocatorRunMethod;

	/**
	 * Reflected constructor for creating relocation instances
	 */
	private final Constructor<?> relocationConstructor;

	/**
	 * Isolated classloader for jar-relocator dependencies
	 */
	private final IsolatedClassLoader classLoader;

	/**
	 * Creates a new relocation helper using the provided library manager to
	 * download the dependencies required for runtime relocation.
	 *
	 * @param libraryManager the library manager used to download dependencies
	 */
	public RelocationHelper(@NotNull LibraryManager libraryManager) {
		requireNonNull(libraryManager, "libraryManager");

		this.classLoader = new IsolatedClassLoader();

		try {
			// Load ASM library (required by jar-relocator)
			classLoader.addPath(libraryManager.downloadLibrary(
					LibraryRequest.builder()
							.groupId("org{}ow2{}asm")
							.artifactId("asm")
							.version("9.9")
							.fallbackRepository("https://repo1.maven.org/maven2/")
							.build()
			));

			classLoader.addPath(libraryManager.downloadLibrary(
					LibraryRequest.builder()
							.groupId("org{}ow2{}asm")
							.artifactId("asm-commons")
							.version("9.9")
							.fallbackRepository("https://repo1.maven.org/maven2/")
							.build()
			));

			// Load jar-relocator
			classLoader.addPath(libraryManager.downloadLibrary(
					LibraryRequest.builder()
							.groupId("me{}lucko")
							.artifactId("jar-relocator")
							.version("1.9")
							.fallbackRepository("https://registry.whereareiam.me/maven/packages/")
							.build()
			));

			Class<?> jarRelocatorClass = classLoader.loadClass(JAR_RELOCATOR_CLASS);
			Class<?> relocationClass = classLoader.loadClass(RELOCATION_CLASS);

			// me.lucko.jarrelocator.JarRelocator(File, File, Collection)
			jarRelocatorConstructor = jarRelocatorClass.getConstructor(File.class, File.class, Collection.class);

			// me.lucko.jarrelocator.JarRelocator#run()
			jarRelocatorRunMethod = jarRelocatorClass.getMethod("run");

			// me.lucko.jarrelocator.Relocation(String, String, Collection, Collection)
			relocationConstructor = relocationClass.getConstructor(String.class, String.class, Collection.class, Collection.class);
		} catch (ReflectiveOperationException | java.io.IOException | java.net.URISyntaxException |
		         java.security.NoSuchAlgorithmException e) {
			throw new RuntimeException("Failed to initialize RelocationHelper", e);
		}
	}

	/**
	 * Invokes the jar relocator to process the input jar and generate an
	 * output jar with the provided relocation rules applied.
	 *
	 * @param in          input jar
	 * @param out         output jar
	 * @param relocations relocations to apply
	 */
	public void relocate(@NotNull Path in, @NotNull Path out, @NotNull Collection<RelocationRule> relocations) {
		requireNonNull(in, "in");
		requireNonNull(out, "out");
		requireNonNull(relocations, "relocations");

		try {
			List<Object> rules = new LinkedList<>();
			for (RelocationRule relocation : relocations) {
				rules.add(relocationConstructor.newInstance(
						relocation.getPattern(),
						relocation.getRelocatedPattern(),
						relocation.getIncludes(),
						relocation.getExcludes()
				));
			}

			jarRelocatorRunMethod.invoke(jarRelocatorConstructor.newInstance(in.toFile(), out.toFile(), rules));
		} catch (ReflectiveOperationException e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * Closes the isolated classloader used for jar-relocator.
	 * This releases file locks on Windows.
	 *
	 * @throws Exception if closing fails
	 */
	public void close() throws Exception {
		if (classLoader != null) classLoader.close();
	}
}

