package me.whereareiam.attache.platform.spring.autoconfigure;

import me.whereareiam.attache.platform.standalone.StandaloneLibraryManager;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class AttacheAutoConfigurationTest {
	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
			.withConfiguration(AutoConfigurations.of(AttacheAutoConfiguration.class));

	@Test
	void createsLibraryManagerWithConfiguredPath() {
		contextRunner
				.withPropertyValues("attache.library-path=.libraries-test")
				.run(context -> {
					assertTrue(context.containsBean("attacheLibraryManager"));
					StandaloneLibraryManager manager = context.getBean(StandaloneLibraryManager.class);
					Path expected = Path.of(".libraries-test").toAbsolutePath().normalize();
					assertEquals(expected, manager.getSaveDirectory().normalize());
				});
	}

	@Test
	void autoLoadRunnerHonorsProperty() {
		contextRunner
				.withPropertyValues("attache.auto-load=false")
				.run(context -> assertFalse(context.containsBean("attacheLibraryLoader")));

		contextRunner
				.withPropertyValues("attache.auto-load=true")
				.run(context -> assertTrue(context.containsBean("attacheLibraryLoader")));
	}
}
