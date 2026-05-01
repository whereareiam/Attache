package me.whereareiam.attache.descriptor;

import me.whereareiam.attache.model.ExcludedDependency;
import me.whereareiam.attache.model.RelocationRule;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static java.util.Objects.requireNonNull;

/**
 * JDK-only codec for Attache descriptor fragments.
 */
public final class AttacheDescriptorCodec {
	private static final String ROOT = "attache-descriptor";

	@NotNull
	public static String encode(@NotNull AttacheDescriptorFragment fragment) {
		requireNonNull(fragment, "fragment");

		StringBuilder xml = new StringBuilder(512);
		xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
		xml.append("<").append(ROOT);
		appendAttribute(xml, "project-path", fragment.getProjectPath());
		appendAttribute(xml, "project-name", fragment.getProjectName());
		appendAttribute(xml, "add-maven-central", Boolean.toString(fragment.isAddMavenCentral()));

		if (fragment.getRepositories().isEmpty() && fragment.getLibraries().isEmpty()) {
			xml.append("/>\n");
			return xml.toString();
		}

		xml.append(">\n");
		appendValueElements(xml, "repositories", "repository", fragment.getRepositories(), 1);
		appendLibraries(xml, fragment.getLibraries());
		xml.append("</").append(ROOT).append(">\n");
		return xml.toString();
	}

	@NotNull
	public static AttacheDescriptorFragment decode(@NotNull InputStream inputStream) throws IOException {
		requireNonNull(inputStream, "inputStream");
		try (Reader reader = new InputStreamReader(inputStream, StandardCharsets.UTF_8)) {
			return decode(reader);
		}
	}

	@NotNull
	public static AttacheDescriptorFragment decode(@NotNull Reader reader) throws IOException {
		requireNonNull(reader, "reader");

		DocumentBuilderFactory factory = createDocumentBuilderFactory();
		Document document;
		try {
			document = factory.newDocumentBuilder().parse(new InputSource(reader));
		} catch (ParserConfigurationException | SAXException e) {
			throw new IOException("Failed to parse Attache descriptor XML", e);
		}

		Element root = document.getDocumentElement();
		if (root == null || !ROOT.equals(root.getTagName())) {
			throw new IllegalStateException("Descriptor root element must be <" + ROOT + ">");
		}

		AttacheDescriptorFragment fragment = new AttacheDescriptorFragment();
		fragment.setProjectPath(readAttribute(root, "project-path"));
		fragment.setProjectName(readAttribute(root, "project-name"));
		fragment.setAddMavenCentral(readBooleanAttribute(root, "add-maven-central", true));
		readValueElements(root, "repositories", "repository", fragment.getRepositories());
		readLibraries(root, fragment);
		return fragment;
	}

	@NotNull
	private static DocumentBuilderFactory createDocumentBuilderFactory() {
		try {
			DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
			factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
			factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
			factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
			factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
			factory.setExpandEntityReferences(false);
			factory.setNamespaceAware(false);
			return factory;
		} catch (ParserConfigurationException e) {
			throw new IllegalStateException("Failed to configure XML parser", e);
		}
	}

	private static void appendLibraries(
			@NotNull StringBuilder xml,
			@NotNull Collection<AttacheDescriptorLibrary> libraries
	) {
		if (libraries.isEmpty()) {
			return;
		}

		indent(xml, 1).append("<libraries>\n");
		for (AttacheDescriptorLibrary library : libraries) {
			appendLibrary(xml, library);
		}
		indent(xml, 1).append("</libraries>\n");
	}

	private static void appendLibrary(
			@NotNull StringBuilder xml,
			@NotNull AttacheDescriptorLibrary library
	) {
		indent(xml, 2).append("<library");
		appendRequiredAttribute(xml, "group-id", library.getGroupId());
		appendRequiredAttribute(xml, "artifact-id", library.getArtifactId());
		appendRequiredAttribute(xml, "version", library.getVersion());
		appendAttribute(xml, "classifier", library.getClassifier());
		appendAttribute(xml, "checksum", library.getChecksum());
		appendAttribute(xml, "skip-if-present", Boolean.toString(library.isSkipIfPresent()));
		appendAttribute(xml, "isolated", Boolean.toString(library.isIsolated()));
		appendAttribute(xml, "loader", library.getLoader());
		appendAttribute(xml, "resolve-transitive-dependencies", Boolean.toString(library.isResolveTransitiveDependencies()));

		if (library.getUrls().isEmpty()
				&& library.getRepositories().isEmpty()
				&& library.getFallbackRepositories().isEmpty()
				&& library.getRelocations().isEmpty()
				&& library.getExcludedTransitiveDependencies().isEmpty()) {
			xml.append("/>\n");
			return;
		}

		xml.append(">\n");
		appendValueElements(xml, "urls", "url", library.getUrls(), 3);
		appendValueElements(xml, "repositories", "repository", library.getRepositories(), 3);
		appendValueElements(xml, "fallback-repositories", "repository", library.getFallbackRepositories(), 3);
		appendRelocations(xml, library.getRelocations(), 3);
		appendExcludedDependencies(xml, library.getExcludedTransitiveDependencies(), 3);
		indent(xml, 2).append("</library>\n");
	}

	private static void appendRelocations(
			@NotNull StringBuilder xml,
			@NotNull Collection<RelocationRule> relocations,
			int indentLevel
	) {
		if (relocations.isEmpty()) {
			return;
		}

		indent(xml, indentLevel).append("<relocations>\n");
		for (RelocationRule relocation : relocations) {
			indent(xml, indentLevel + 1).append("<relocation");
			appendRequiredAttribute(xml, "pattern", relocation.getPattern());
			appendRequiredAttribute(xml, "relocated-pattern", relocation.getRelocatedPattern());

			if (relocation.getIncludes().isEmpty() && relocation.getExcludes().isEmpty()) {
				xml.append("/>\n");
				continue;
			}

			xml.append(">\n");
			appendValueElements(xml, "includes", "include", relocation.getIncludes(), indentLevel + 2);
			appendValueElements(xml, "excludes", "exclude", relocation.getExcludes(), indentLevel + 2);
			indent(xml, indentLevel + 1).append("</relocation>\n");
		}
		indent(xml, indentLevel).append("</relocations>\n");
	}

	private static void appendExcludedDependencies(
			@NotNull StringBuilder xml,
			@NotNull Collection<ExcludedDependency> excludedDependencies,
			int indentLevel
	) {
		if (excludedDependencies.isEmpty()) {
			return;
		}

		indent(xml, indentLevel).append("<excluded-transitive-dependencies>\n");
		for (ExcludedDependency excludedDependency : excludedDependencies) {
			indent(xml, indentLevel + 1).append("<dependency");
			appendRequiredAttribute(xml, "group-id", excludedDependency.getGroupId());
			appendRequiredAttribute(xml, "artifact-id", excludedDependency.getArtifactId());
			xml.append("/>\n");
		}
		indent(xml, indentLevel).append("</excluded-transitive-dependencies>\n");
	}

	private static void appendValueElements(
			@NotNull StringBuilder xml,
			@NotNull String container,
			@NotNull String entryTag,
			@NotNull Collection<String> values,
			int indentLevel
	) {
		if (values.isEmpty()) {
			return;
		}

		indent(xml, indentLevel).append('<').append(container).append(">\n");
		for (String value : values) {
			indent(xml, indentLevel + 1).append('<').append(entryTag).append('>')
					.append(escape(value))
					.append("</").append(entryTag).append(">\n");
		}
		indent(xml, indentLevel).append("</").append(container).append(">\n");
	}

	private static void readLibraries(@NotNull Element root, @NotNull AttacheDescriptorFragment fragment) {
		Element libraries = child(root, "libraries");
		if (libraries == null) {
			return;
		}

		for (Element libraryElement : children(libraries, "library")) {
			AttacheDescriptorLibrary library = new AttacheDescriptorLibrary();
			library.setGroupId(requiredAttribute(libraryElement, "group-id"));
			library.setArtifactId(requiredAttribute(libraryElement, "artifact-id"));
			library.setVersion(requiredAttribute(libraryElement, "version"));
			library.setClassifier(readAttribute(libraryElement, "classifier"));
			library.setChecksum(readAttribute(libraryElement, "checksum"));
			library.setSkipIfPresent(readBooleanAttribute(libraryElement, "skip-if-present", true));
			library.setIsolated(readBooleanAttribute(libraryElement, "isolated", false));
			library.setLoader(readAttribute(libraryElement, "loader"));
			library.setResolveTransitiveDependencies(readBooleanAttribute(libraryElement, "resolve-transitive-dependencies", false));

			readValueElements(libraryElement, "urls", "url", library.getUrls());
			readValueElements(libraryElement, "repositories", "repository", library.getRepositories());
			readValueElements(libraryElement, "fallback-repositories", "repository", library.getFallbackRepositories());
			readRelocations(libraryElement, library);
			readExcludedDependencies(libraryElement, library);

			fragment.getLibraries().add(library);
		}
	}

	private static void readRelocations(@NotNull Element libraryElement, @NotNull AttacheDescriptorLibrary library) {
		Element relocations = child(libraryElement, "relocations");
		if (relocations == null) {
			return;
		}

		for (Element relocationElement : children(relocations, "relocation")) {
			RelocationRule.RelocationRuleBuilder builder = RelocationRule.builder()
					.pattern(requiredAttribute(relocationElement, "pattern"))
					.relocatedPattern(requiredAttribute(relocationElement, "relocated-pattern"));

			readValueElements(relocationElement, "includes", "include", builder::include);
			readValueElements(relocationElement, "excludes", "exclude", builder::exclude);
			library.getRelocations().add(builder.build());
		}
	}

	private static void readExcludedDependencies(@NotNull Element libraryElement, @NotNull AttacheDescriptorLibrary library) {
		Element excludedDependencies = child(libraryElement, "excluded-transitive-dependencies");
		if (excludedDependencies == null) {
			return;
		}

		for (Element dependencyElement : children(excludedDependencies, "dependency")) {
			library.getExcludedTransitiveDependencies().add(new ExcludedDependency(
					requiredAttribute(dependencyElement, "group-id"),
					requiredAttribute(dependencyElement, "artifact-id")
			));
		}
	}

	private static void readValueElements(
			@NotNull Element root,
			@NotNull String containerTag,
			@NotNull String entryTag,
			@NotNull Collection<String> target
	) {
		readValueElements(root, containerTag, entryTag, target::add);
	}

	private static void readValueElements(
			@NotNull Element root,
			@NotNull String containerTag,
			@NotNull String entryTag,
			@NotNull java.util.function.Consumer<String> consumer
	) {
		Element container = child(root, containerTag);
		if (container == null) {
			return;
		}

		for (Element element : children(container, entryTag)) {
			consumer.accept(element.getTextContent());
		}
	}

	@Nullable
	private static String readAttribute(@NotNull Element element, @NotNull String name) {
		String value = element.getAttribute(name);
		return value.isBlank() ? null : value;
	}

	private static boolean readBooleanAttribute(@NotNull Element element, @NotNull String name, boolean fallback) {
		String value = readAttribute(element, name);
		return value == null ? fallback : Boolean.parseBoolean(value);
	}

	@NotNull
	private static String requiredAttribute(@NotNull Element element, @NotNull String name) {
		String value = readAttribute(element, name);
		if (value == null) {
			throw new IllegalStateException("Missing required attribute '" + name + "' on <" + element.getTagName() + '>');
		}
		return value;
	}

	@Nullable
	private static Element child(@NotNull Element parent, @NotNull String tagName) {
		for (Element child : children(parent, tagName)) {
			return child;
		}
		return null;
	}

	@NotNull
	private static List<Element> children(@NotNull Element parent, @NotNull String tagName) {
		List<Element> children = new ArrayList<>();
		for (Node child = parent.getFirstChild(); child != null; child = child.getNextSibling()) {
			if (child.getNodeType() != Node.ELEMENT_NODE) {
				continue;
			}

			Element element = (Element) child;
			if (tagName.equals(element.getTagName())) {
				children.add(element);
			}
		}
		return children;
	}

	@NotNull
	private static StringBuilder indent(@NotNull StringBuilder builder, int level) {
		return builder.append("  ".repeat(Math.max(0, level)));
	}

	private static void appendRequiredAttribute(@NotNull StringBuilder xml, @NotNull String name, @Nullable String value) {
		if (value == null || value.isBlank()) {
			throw new IllegalStateException("Descriptor attribute '" + name + "' is required");
		}
		appendAttribute(xml, name, value);
	}

	private static void appendAttribute(@NotNull StringBuilder xml, @NotNull String name, @Nullable String value) {
		if (value == null) {
			return;
		}

		xml.append(' ')
				.append(name)
				.append("=\"")
				.append(escape(value))
				.append('"');
	}

	@NotNull
	private static String escape(@NotNull String value) {
		StringBuilder escaped = new StringBuilder(value.length());
		for (int i = 0; i < value.length(); i++) {
			char character = value.charAt(i);
			switch (character) {
				case '&' -> escaped.append("&amp;");
				case '<' -> escaped.append("&lt;");
				case '>' -> escaped.append("&gt;");
				case '"' -> escaped.append("&quot;");
				case '\'' -> escaped.append("&apos;");
				default -> escaped.append(character);
			}
		}
		return escaped.toString();
	}
}
