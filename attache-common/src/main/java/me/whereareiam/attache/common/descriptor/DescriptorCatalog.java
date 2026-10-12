package me.whereareiam.attache.common.descriptor;

import me.whereareiam.attache.descriptor.AttacheDescriptorLibrary;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * Merged view of the Attache descriptor fragments found on a classpath.
 * <p>
 * Each library appears once, with the groups that declare it. A library declared outside any group
 * belongs to the default group, which {@code loadDescriptors()} loads.
 */
public final class DescriptorCatalog {
	private final boolean addMavenCentral;
	private final Set<String> repositories;
	private final List<Entry> entries;

	private DescriptorCatalog(boolean addMavenCentral, @NotNull Set<String> repositories, @NotNull List<Entry> entries) {
		this.addMavenCentral = addMavenCentral;
		this.repositories = Collections.unmodifiableSet(repositories);
		this.entries = List.copyOf(entries);
	}

	/**
	 * Merges descriptor fragments into one catalog.
	 *
	 * @param fragments the fragments in discovery order
	 * @return the merged catalog
	 * @throws IllegalStateException if two fragments define the same library differently
	 */
	@NotNull
	public static DescriptorCatalog merge(@NotNull List<ClasspathDescriptorLoader.LoadedDescriptorFragment> fragments) {
		boolean addMavenCentral = false;
		LinkedHashSet<String> repositories = new LinkedHashSet<>();
		LinkedHashMap<String, Entry> entries = new LinkedHashMap<>();

		for (ClasspathDescriptorLoader.LoadedDescriptorFragment loaded : fragments) {
			if (loaded.fragment().isAddMavenCentral()) addMavenCentral = true;
			repositories.addAll(loaded.fragment().getRepositories());

			for (AttacheDescriptorLibrary library : loaded.fragment().getLibraries()) {
				String key = library.coordinatesKey();
				Entry existing = entries.get(key);
				if (existing == null) {
					Entry entry = new Entry(library, loaded.location());
					entry.include(library.getGroup());
					entries.put(key, entry);
					continue;
				}

				if (!existing.library.sameDefinition(library)) {
					throw new IllegalStateException("Conflicting Attache descriptor definitions for " + key
							+ " in " + existing.location + " and " + loaded.location());
				}
				existing.include(library.getGroup());
			}
		}

		return new DescriptorCatalog(addMavenCentral, repositories, List.copyOf(entries.values()));
	}

	public boolean isEmpty() {
		return entries.isEmpty();
	}

	public boolean isAddMavenCentral() {
		return addMavenCentral;
	}

	@NotNull
	public Set<String> getRepositories() {
		return repositories;
	}

	/**
	 * Gets the names of every group the fragments declare, sorted.
	 *
	 * @return the declared group names
	 */
	@NotNull
	public Set<String> getGroups() {
		TreeSet<String> groups = new TreeSet<>();
		entries.forEach(entry -> groups.addAll(entry.groups));
		return Collections.unmodifiableSet(groups);
	}

	/**
	 * Gets the libraries declared outside any group.
	 *
	 * @return the default libraries in discovery order
	 */
	@NotNull
	public List<AttacheDescriptorLibrary> getDefaultLibraries() {
		return entries.stream()
				.filter(entry -> entry.inDefaultGroup)
				.map(entry -> entry.library)
				.toList();
	}

	/**
	 * Gets the libraries declared in at least one of the given groups.
	 *
	 * @param groups the group names
	 * @return the matching libraries in discovery order
	 */
	@NotNull
	public List<AttacheDescriptorLibrary> getGroupLibraries(@NotNull Collection<String> groups) {
		Objects.requireNonNull(groups, "groups");

		return entries.stream()
				.filter(entry -> !Collections.disjoint(entry.groups, groups))
				.map(entry -> entry.library)
				.toList();
	}

	private static final class Entry {
		private final AttacheDescriptorLibrary library;
		private final String location;
		private final Set<String> groups = new LinkedHashSet<>();
		private boolean inDefaultGroup;

		private Entry(@NotNull AttacheDescriptorLibrary library, @NotNull String location) {
			this.library = library;
			this.location = location;
		}

		private void include(@Nullable String group) {
			if (group == null) {
				inDefaultGroup = true;
				return;
			}

			groups.add(group);
		}
	}
}
