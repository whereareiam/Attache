package me.whereareiam.attache.common.descriptor;

import me.whereareiam.attache.common.util.LibraryHelper;
import me.whereareiam.attache.descriptor.AttacheDescriptorLibrary;
import me.whereareiam.attache.model.LibraryRequest;
import me.whereareiam.attache.model.RelocationRule;
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
 * belongs to the default group, which {@code loadDescriptors()} loads. The relocations a fragment declares for all
 * libraries apply to the libraries of every fragment.
 */
public final class DescriptorCatalog {
	private final boolean addMavenCentral;
	private final Set<String> repositories;
	private final List<RelocationRule> relocations;
	private final List<Entry> entries;

	private DescriptorCatalog(
			boolean addMavenCentral,
			@NotNull Set<String> repositories,
			@NotNull List<RelocationRule> relocations,
			@NotNull List<Entry> entries
	) {
		this.addMavenCentral = addMavenCentral;
		this.repositories = Collections.unmodifiableSet(repositories);
		this.relocations = List.copyOf(relocations);
		this.entries = List.copyOf(entries);
	}

	/**
	 * Merges descriptor fragments into one catalog.
	 *
	 * @param fragments the fragments in discovery order
	 * @return the merged catalog
	 * @throws IllegalStateException if two fragments define the same library differently, or relocate the same
	 *                               package differently
	 */
	@NotNull
	public static DescriptorCatalog merge(@NotNull List<ClasspathDescriptorLoader.LoadedDescriptorFragment> fragments) {
		boolean addMavenCentral = false;
		LinkedHashSet<String> repositories = new LinkedHashSet<>();
		LinkedHashMap<String, Relocation> relocations = new LinkedHashMap<>();
		LinkedHashMap<String, Entry> entries = new LinkedHashMap<>();

		for (ClasspathDescriptorLoader.LoadedDescriptorFragment loaded : fragments) {
			if (loaded.fragment().isAddMavenCentral()) addMavenCentral = true;
			repositories.addAll(loaded.fragment().getRepositories());

			for (RelocationRule rule : loaded.fragment().getRelocations()) {
				String pattern = LibraryHelper.replaceWithDots(rule.getPattern());
				Relocation existing = relocations.putIfAbsent(pattern, new Relocation(rule, loaded.location()));
				if (existing != null && !sameRelocation(existing.rule, rule)) {
					throw new IllegalStateException("Conflicting Attache descriptor relocations of " + pattern
							+ " in " + existing.location + " and " + loaded.location());
				}
			}

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

		List<RelocationRule> rules = relocations.values().stream().map(Relocation::rule).toList();
		return new DescriptorCatalog(addMavenCentral, repositories, rules, List.copyOf(entries.values()));
	}

	private static boolean sameRelocation(@NotNull RelocationRule first, @NotNull RelocationRule second) {
		return LibraryHelper.replaceWithDots(first.getRelocatedPattern()).equals(LibraryHelper.replaceWithDots(second.getRelocatedPattern()))
				&& first.getIncludes().equals(second.getIncludes())
				&& first.getExcludes().equals(second.getExcludes());
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
	 * Gets the relocations the fragments declare for all libraries.
	 *
	 * @return the relocations in discovery order
	 */
	@NotNull
	public List<RelocationRule> getRelocations() {
		return relocations;
	}

	/**
	 * Builds the request that loads a library of this catalog: its own definition plus the relocations declared
	 * for all libraries. A library's own relocation of a package takes precedence.
	 *
	 * @param library a library of this catalog
	 * @return the request to load it with
	 */
	@NotNull
	public LibraryRequest toLibraryRequest(@NotNull AttacheDescriptorLibrary library) {
		LibraryRequest request = library.toLibraryRequest();
		if (relocations.isEmpty()) return request;

		Set<String> ownPatterns = new LinkedHashSet<>();
		request.getRelocations().forEach(rule -> ownPatterns.add(LibraryHelper.replaceWithDots(rule.getPattern())));

		LibraryRequest.LibraryRequestBuilder builder = request.toBuilder();
		relocations.stream()
				.filter(rule -> !ownPatterns.contains(LibraryHelper.replaceWithDots(rule.getPattern())))
				.forEach(builder::relocation);
		return builder.build();
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

	private record Relocation(@NotNull RelocationRule rule, @NotNull String location) {
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
