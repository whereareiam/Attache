package me.whereareiam.attache.common.descriptor;

import me.whereareiam.attache.descriptor.AttacheDescriptorCodec;
import me.whereareiam.attache.descriptor.AttacheDescriptorFragment;
import me.whereareiam.attache.descriptor.AttacheDescriptorLibrary;
import me.whereareiam.attache.model.LibraryRequest;
import me.whereareiam.attache.model.RelocationRule;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DescriptorCatalogTest {
	@Test
	void appliesDescriptorRelocationsToTheLibrariesOfEveryFragment() {
		AttacheDescriptorFragment platform = fragment("platform", library("alpha"));
		platform.getRelocations().add(rule("net{}kyori{}adventure", "example.adventure"));

		DescriptorCatalog catalog = DescriptorCatalog.merge(List.of(
				loaded("platform", platform),
				loaded("common", fragment("common", library("beta")))
		));

		for (AttacheDescriptorLibrary library : catalog.getDefaultLibraries()) {
			LibraryRequest request = catalog.toLibraryRequest(library);
			assertEquals(List.of(rule("net{}kyori{}adventure", "example.adventure")), request.getRelocations());
		}
	}

	@Test
	void keepsTheLibrarysOwnRelocationOfAPackage() {
		AttacheDescriptorLibrary alpha = library("alpha");
		alpha.getRelocations().add(rule("com{}google{}gson", "alpha.gson"));

		AttacheDescriptorFragment fragment = fragment("platform", alpha);
		fragment.getRelocations().add(rule("com{}google{}gson", "example.gson"));
		fragment.getRelocations().add(rule("net{}kyori{}adventure", "example.adventure"));

		DescriptorCatalog catalog = DescriptorCatalog.merge(List.of(loaded("platform", fragment)));
		LibraryRequest request = catalog.toLibraryRequest(catalog.getDefaultLibraries().get(0));

		assertEquals(List.of(
				rule("com{}google{}gson", "alpha.gson"),
				rule("net{}kyori{}adventure", "example.adventure")
		), request.getRelocations());
	}

	@Test
	void mergesTheSameRelocationFromSeveralFragments() {
		AttacheDescriptorFragment first = fragment("first", library("alpha"));
		first.getRelocations().add(rule("net{}kyori{}adventure", "example.adventure"));
		AttacheDescriptorFragment second = fragment("second", library("beta"));
		second.getRelocations().add(rule("net.kyori.adventure", "example.adventure"));

		DescriptorCatalog catalog = DescriptorCatalog.merge(List.of(loaded("first", first), loaded("second", second)));

		assertEquals(1, catalog.getRelocations().size());
	}

	@Test
	void failsOnConflictingRelocationsOfAPackage() {
		AttacheDescriptorFragment first = fragment("first", library("alpha"));
		first.getRelocations().add(rule("net{}kyori{}adventure", "first.adventure"));
		AttacheDescriptorFragment second = fragment("second", library("beta"));
		second.getRelocations().add(rule("net{}kyori{}adventure", "second.adventure"));

		IllegalStateException exception = assertThrows(IllegalStateException.class, () ->
				DescriptorCatalog.merge(List.of(loaded("first", first), loaded("second", second))));
		assertTrue(exception.getMessage().contains("net.kyori.adventure"));
	}

	@Test
	void codecKeepsDescriptorRelocationsApartFromLibraryRelocations() throws Exception {
		AttacheDescriptorLibrary alpha = library("alpha");
		alpha.getRelocations().add(rule("com{}google{}gson", "alpha.gson"));
		AttacheDescriptorFragment fragment = fragment("platform", alpha);
		fragment.getRelocations().add(rule("net{}kyori{}adventure", "example.adventure"));

		AttacheDescriptorFragment decoded = AttacheDescriptorCodec.decode(new StringReader(AttacheDescriptorCodec.encode(fragment)));

		assertEquals(fragment, decoded);
	}

	@NotNull
	private static ClasspathDescriptorLoader.LoadedDescriptorFragment loaded(@NotNull String name, @NotNull AttacheDescriptorFragment fragment) {
		return new ClasspathDescriptorLoader.LoadedDescriptorFragment("META-INF/attache/" + name + "/attache.xml", fragment);
	}

	@NotNull
	private static AttacheDescriptorFragment fragment(@NotNull String projectName, @NotNull AttacheDescriptorLibrary... libraries) {
		AttacheDescriptorFragment fragment = new AttacheDescriptorFragment();
		fragment.setProjectName(projectName);
		fragment.setProjectPath(':' + projectName);
		fragment.getLibraries().addAll(List.of(libraries));
		return fragment;
	}

	@NotNull
	private static AttacheDescriptorLibrary library(@NotNull String artifactId) {
		AttacheDescriptorLibrary library = new AttacheDescriptorLibrary();
		library.setGroupId("example.test");
		library.setArtifactId(artifactId);
		library.setVersion("1.0.0");
		return library;
	}

	@NotNull
	private static RelocationRule rule(@NotNull String pattern, @NotNull String relocatedPattern) {
		return RelocationRule.builder().pattern(pattern).relocatedPattern(relocatedPattern).build();
	}
}
