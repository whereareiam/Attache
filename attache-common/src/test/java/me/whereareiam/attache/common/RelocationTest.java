package me.whereareiam.attache.common;

import me.whereareiam.attache.model.RelocationRule;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link RelocationRule} class.
 */
class RelocationTest {

	@Test
	void testBasicRelocationWithConstructor() {
		RelocationRule relocation = new RelocationRule("com.example", "me.myapp.libs.example",
				Collections.emptySet(), Collections.emptySet());

		assertEquals("com.example", relocation.getPattern());
		assertEquals("me.myapp.libs.example", relocation.getRelocatedPattern());
		assertTrue(relocation.getIncludes().isEmpty());
		assertTrue(relocation.getExcludes().isEmpty());
	}

	@Test
	void testBasicRelocationWithBuilder() {
		RelocationRule relocation = RelocationRule.builder()
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

		RelocationRule relocation = RelocationRule.builder()
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
		RelocationRule relocation1 = RelocationRule.builder()
				.pattern("com.example")
				.relocatedPattern("me.myapp.libs")
				.build();

		RelocationRule relocation2 = RelocationRule.builder()
				.pattern("com.example")
				.relocatedPattern("me.myapp.libs")
				.build();

		RelocationRule relocation3 = RelocationRule.builder()
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

		RelocationRule relocation1 = RelocationRule.builder()
				.pattern("com.example")
				.relocatedPattern("me.myapp")
				.includes(includes)
				.excludes(excludes)
				.build();

		RelocationRule relocation2 = RelocationRule.builder()
				.pattern("com.example")
				.relocatedPattern("me.myapp")
				.includes(includes)
				.excludes(excludes)
				.build();

		RelocationRule relocation3 = RelocationRule.builder()
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
		RelocationRule original = RelocationRule.builder()
				.pattern("com.example")
				.relocatedPattern("me.myapp")
				.build();

		RelocationRule modified = original.toBuilder()
				.relocatedPattern("me.myapp.different")
				.build();

		assertEquals("com.example", modified.getPattern());
		assertEquals("me.myapp.different", modified.getRelocatedPattern());
		assertNotEquals(original, modified);
	}

	@Test
	void testRelocationDefaultCollections() {
		RelocationRule relocation = RelocationRule.builder()
				.pattern("com.example")
				.relocatedPattern("me.myapp")
				.build();

		assertNotNull(relocation.getIncludes());
		assertNotNull(relocation.getExcludes());
		assertTrue(relocation.getIncludes().isEmpty());
		assertTrue(relocation.getExcludes().isEmpty());
	}
}
