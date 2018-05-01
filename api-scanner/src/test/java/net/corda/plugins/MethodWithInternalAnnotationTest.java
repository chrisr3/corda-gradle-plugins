package net.corda.plugins;

import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.BuildTask;
import org.gradle.testkit.runner.GradleRunner;
import static org.gradle.testkit.runner.TaskOutcome.*;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

import static net.corda.plugins.CopyUtils.*;
import static org.assertj.core.api.Assertions.*;
import static org.junit.Assert.*;

public class MethodWithInternalAnnotationTest {
    @Rule
    public final TemporaryFolder testProjectDir = new TemporaryFolder();

    @Before
    public void setup() throws IOException {
        File buildFile = testProjectDir.newFile("build.gradle");
        copyResourceTo("method-internal-annotation/build.gradle", buildFile);
    }

    @Test
    public void testMethodWithInternalAnnotations() throws IOException {
        BuildResult result = GradleRunner.create()
            .withProjectDir(testProjectDir.getRoot())
            .withArguments(getGradleArgsForTasks("scanApi"))
            .withPluginClasspath()
            .build();
        String output = result.getOutput();
        System.out.println(output);

        BuildTask scanApi = result.task(":scanApi");
        assertNotNull(scanApi);
        assertEquals(SUCCESS, scanApi.getOutcome());

        assertThat(output)
            .contains("net.corda.example.method.InvisibleAnnotation")
            .contains("net.corda.example.method.LocalInvisibleAnnotation");

        Path api = pathOf(testProjectDir, "build", "api", "method-internal-annotation.txt");
        assertThat(api).isRegularFile();
        assertEquals("public class net.corda.example.method.HasVisibleMethod extends java.lang.Object\n" +
            "  public <init>()\n" +
            "  public void hasInvisibleAnnotations()\n" +
            "##", CopyUtils.toString(api));
    }
}