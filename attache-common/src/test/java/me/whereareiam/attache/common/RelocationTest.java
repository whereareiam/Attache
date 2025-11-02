package me.whereareiam.attache.common;

import me.whereareiam.attache.model.Relocation;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link Relocation} class.
 */
class RelocationTest {

	@Test
	void testBasicRelocationWithConstructor() {
		Relocation relocation = new Relocation("com.example", "me.myapp.libs.example",
				Collections.emptySet(), Collections.emptySet());

		assertEquals("com.example", relocation.getPattern());
		assertEquals("me.myapp.libs.example", relocation.getRelocatedPattern());
		assertTrue(relocation.getIncludes().isEmpty());
		assertTrue(relocation.getExcludes().isEmpty());
	}

	@Test
	void testBasicRelocationWithBuilder() {
		Relocation relocation = Relocation.builder()
				.pattern("com.example")
				.relocatedPattern("me.myapp.libs.example")
				.build();

		assertEquals("com.example", relocation.getPattern());
		assertEquals("me.myapp.libs.example", relocation.getRelocatedPattern());
		assertTrue(relocation.getIncludes().isEmpty());
		assertTrue(relocation.getExcludes().isEmpty());
	}

	@Test
	void testRelocationWithIncludesAndExcludes() {
		Collection<String> includes = Arrays.asList("com.example.include1", "com.example.include2");
		Collection<String> excludes = Arrays.asList("com.example.exclude1", "com.example.exclude2");

		Relocation relocation = Relocation.builder()
				.pattern("com.example")
				.relocatedPattern("me.myapp.libs.example")
				.includes(includes)
				.excludes(excludes)
				.build();

		assertEquals(2, relocation.getIncludes().size());
		assertEquals(2, relocation.getExcludes().size());
		assertTrue(relocation.getIncludes().contains("com.example.include1"));
		assertTrue(relocation.getIncludes().contains("com.example.include2"));
		assertTrue(relocation.getExcludes().contains("com.example.exclude1"));
		assertTrue(relocation.getExcludes().contains("com.example.exclude2"));
	}

	@Test
	void testRelocationEquality() {
		Relocation relocation1 = Relocation.builder()
				.pattern("com.example")
				.relocatedPattern("me.myapp.libs")
				.build();

		Relocation relocation2 = Relocation.builder()
				.pattern("com.example")
				.relocatedPattern("me.myapp.libs")
				.build();

		Relocation relocation3 = Relocation.builder()
				.pattern("com.example")
				.relocatedPattern("me.myapp.other")
				.build();

		assertEquals(relocation1, relocation2);
		assertNotEquals(relocation1, relocation3);
		assertEquals(relocation1.hashCode(), relocation2.hashCode());
	}

	@Test
	void testRelocationEqualityWithIncludesExcludes() {
		Collection<String> includes = Arrays.asList("inc1", "inc2");
		Collection<String> excludes = Arrays.asList("exc1", "exc2");

		Relocation relocation1 = Relocation.builder()
				.pattern("com.example")
				.relocatedPattern("me.myapp")
				.includes(includes)
				.excludes(excludes)
				.build();

		Relocation relocation2 = Relocation.builder()
				.pattern("com.example")
				.relocatedPattern("me.myapp")
				.includes(includes)
				.excludes(excludes)
				.build();

		Relocation relocation3 = Relocation.builder()
				.pattern("com.example")
				.relocatedPattern("me.myapp")
				.includes(includes)
				.exclude("different")
				.build();

		assertEquals(relocation1, relocation2);
		assertNotEquals(relocation1, relocation3);
	}

	@Test
	void testRelocationToBuilder() {
		Relocation original = Relocation.builder()
				.pattern("com.example")
				.relocatedPattern("me.myapp")
				.build();

		Relocation modified = original.toBuilder()
				.relocatedPattern("me.myapp.different")
				.build();

		assertEquals("com.example", modified.getPattern());
		assertEquals("me.myapp.different", modified.getRelocatedPattern());
		assertNotEquals(original, modified);
	}

	@Test
	void testRelocationDefaultCollections() {
		Relocation relocation = Relocation.builder()
				.pattern("com.example")
				.relocatedPattern("me.myapp")
				.build();

		assertNotNull(relocation.getIncludes());
		assertNotNull(relocation.getExcludes());
		assertTrue(relocation.getIncludes().isEmpty());
		assertTrue(relocation.getExcludes().isEmpty());
	}
}
