package me.whereareiam.attache.type;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LibraryLoadModeTest {
	@Test
	void defaultsToParallelWhenUnset() {
		assertEquals(LibraryLoadMode.PARALLEL, LibraryLoadMode.resolve(null));
		assertEquals(LibraryLoadMode.PARALLEL, LibraryLoadMode.resolve(" "));
	}

	@Test
	void resolvesNamedModesCaseInsensitively() {
		assertEquals(LibraryLoadMode.PARALLEL, LibraryLoadMode.resolve("parallel"));
		assertEquals(LibraryLoadMode.SEQUENTIAL, LibraryLoadMode.resolve("SEQUENTIAL"));
	}

	@Test
	void rejectsUnsupportedModes() {
		assertThrows(IllegalArgumentException.class, () -> LibraryLoadMode.resolve("bogus"));
	}
}
