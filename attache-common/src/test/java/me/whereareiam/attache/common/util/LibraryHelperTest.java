package me.whereareiam.attache.common.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import me.whereareiam.attache.descriptor.AttacheDescriptorFragment;
import me.whereareiam.attache.model.LibraryRequest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LibraryHelperTest {
	private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

	@Test
	void normalizeHandlesDescriptorRelocationsWithoutIncludeOrExcludeLists() {
		String json = """
				{
				  "projectName": "fixture",
				  "projectPath": ":fixture",
				  "libraries": [
				    {
				      "groupId": "example{}test",
				      "artifactId": "fixture",
				      "version": "1.0.0",
				      "relocations": [
				        {
				          "pattern": "com{}example{}source",
				          "relocatedPattern": "me{}example{}target"
				        }
				      ]
				    }
				  ]
				}
				""";

		AttacheDescriptorFragment fragment = GSON.fromJson(json, AttacheDescriptorFragment.class);
		LibraryRequest request = fragment.getLibraries().getFirst().toLibraryRequest();

		LibraryRequest normalized = assertDoesNotThrow(() -> LibraryHelper.normalize(request));
		assertTrue(normalized.getRelocations().iterator().next().getIncludes().isEmpty());
		assertTrue(normalized.getRelocations().iterator().next().getExcludes().isEmpty());
	}
}
