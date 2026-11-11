package net.microfalx.argus.diagnostics;

import net.microfalx.resource.Resource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HistogramTest {

    private static final String HISTOGRAM_HEADER = "#instances";

    @TempDir
    private Path directory;

    @Mock
    private DiagnosticsService diagnosticsService;

    @Test
    void defaultOptions() {
        Histogram histogram = new Histogram("Test");
        assertEquals(Diagnostics.Type.HISTOGRAM, histogram.getType());
        assertTrue(histogram.getOptions().isLive());
    }

    @Test
    void customOptions() {
        Histogram.Options options = Histogram.Options.builder().live(false).build();
        Histogram histogram = new Histogram("Test", options);
        assertSame(options, histogram.getOptions());
        assertFalse(histogram.getOptions().isLive());
    }

    @Test
    void notStarted() {
        Histogram histogram = new Histogram("Test");
        assertNull(histogram.getDuration());
        assertNull(histogram.getUri());
        assertThrows(DiagnosticsNotFoundException.class, histogram::getResource);
    }

    @Test
    void liveObjects() throws IOException {
        Histogram histogram = collect(Histogram.Options.create());
        assertHistogram(histogram);
    }

    @Test
    void allObjects() throws IOException {
        Histogram histogram = collect(Histogram.Options.builder().live(false).build());
        assertHistogram(histogram);
    }

    @Test
    void resource() throws IOException {
        Histogram histogram = collect(Histogram.Options.create());
        assertHistogram(histogram);
        Resource resource = histogram.getResource();
        assertTrue(resource.exists());
        assertTrue(resource.loadAsString().contains(HISTOGRAM_HEADER));
    }

    @Test
    void mbean() {
        String histogram = new Histogram("Test").extractWithMBean(true);
        assertTrue(histogram.contains(HISTOGRAM_HEADER), histogram);
        assertTrue(histogram.contains("java.lang.String"), histogram);
    }

    @Test
    void tool() {
        assumeTrue(new DiagnosticsTool("jattach").isAvailable(), "jattach is not installed");
        String histogram = new Histogram("Test").extractWithTool(true);
        assertTrue(histogram.contains(HISTOGRAM_HEADER), histogram);
        assertFalse(histogram.contains("JVM response code"), histogram);
    }

    @Test
    void toolNotAvailable() {
        DiagnosticsTool tool = new DiagnosticsTool("missing_tool_" + System.nanoTime());
        assertFalse(tool.isAvailable());
        assertThrows(DiagnosticsException.class, () -> tool.execute("1"));
    }

    @Test
    void stripToolHeader() {
        assertNull(Histogram.stripToolHeader(null));
        assertEquals("histogram", Histogram.stripToolHeader("histogram"));
        assertEquals(" num     #instances\n", Histogram.stripToolHeader("Connected to remote JVM\n"
                + "JVM response code = 0\n num     #instances\n"));
        assertEquals("", Histogram.stripToolHeader("Connected to remote JVM\nJVM response code = 0"));
    }

    private Histogram collect(Histogram.Options options) {
        when(diagnosticsService.getResources(Diagnostics.Type.HISTOGRAM)).thenReturn(Resource.directory(directory.toFile()));
        Histogram histogram = new Histogram("Test", options);
        histogram.diagnosticsService = diagnosticsService;
        histogram.collect();
        return histogram;
    }

    private void assertHistogram(Histogram histogram) throws IOException {
        assertNull(histogram.getFailure(), () -> "Histogram failed: " + histogram.getFailure());
        assertNotNull(histogram.getStartTime());
        assertNotNull(histogram.getEndTime());
        assertNotNull(histogram.getDuration());

        assertNotNull(histogram.getUri());
        File file = new File(histogram.getUri());
        assertTrue(file.isFile());
        assertEquals(directory.toFile().getCanonicalFile(), file.getParentFile().getCanonicalFile());
        assertTrue(file.getName().matches("histogram_\\d{8}_\\d{6}_" + histogram.getId() + "\\.txt"), file.getName());

        String content = Files.readString(file.toPath(), StandardCharsets.UTF_8);
        assertTrue(content.contains(HISTOGRAM_HEADER), content);
        assertTrue(content.contains("java.lang.String"), content);
        assertNotNull(histogram.getData());
    }
}
