package me.whereareiam.attache.common.util;

import me.whereareiam.attache.descriptor.AttacheDescriptorFragment;
import me.whereareiam.attache.descriptor.AttacheDescriptorLibrary;
import me.whereareiam.attache.model.LibraryRequest;
import me.whereareiam.attache.model.RelocationRule;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LibraryHelperTest {
	@Test
	void normalizeHandlesDescriptorRelocationsWithoutIncludeOrExcludeLists() {
		AttacheDescriptorFragment fragment = new AttacheDescriptorFragment();
		fragment.setProjectName("fixture");
		fragment.setProjectPath(":fixture");

		AttacheDescriptorLibrary library = new AttacheDescriptorLibrary();
		library.setGroupId("example{}test");
		library.setArtifactId("fixture");
		library.setVersion("1.0.0");
		library.getRelocations().add(RelocationRule.builder()
				.pattern("com{}example{}source")
				.relocatedPattern("me{}example{}target")
				.build());
		fragment.getLibraries().add(library);

		LibraryRequest request = fragment.getLibraries().getFirst().toLibraryRequest();

		LibraryRequest normalized = assertDoesNotThrow(() -> LibraryHelper.normalize(request));
		assertTrue(normalized.getRelocations().iterator().next().getIncludes().isEmpty());
		assertTrue(normalized.getRelocations().iterator().next().getExcludes().isEmpty());
	}
}
