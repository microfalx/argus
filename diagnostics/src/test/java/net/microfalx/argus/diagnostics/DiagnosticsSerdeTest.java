package net.microfalx.argus.diagnostics;

import net.microfalx.lang.Serde;
import net.microfalx.resource.Resource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DiagnosticsSerdeTest {

    @TempDir
    private Path directory;

    @Mock
    private DiagnosticsService diagnosticsService;

    @Test
    void notCollected() {
        ThreadDump threadDump = new ThreadDump("Test");
        ThreadDump copy = roundTrip(threadDump);
        assertCommon(threadDump, copy);
        assertEquals(-1, copy.getSize());
        assertNull(copy.getUri());
        assertNull(copy.getData());
        assertNull(copy.getStartTime());
        assertNull(copy.getEndTime());
        assertNull(copy.getFailure());
        assertThrows(DiagnosticsNotFoundException.class, copy::getResource);
    }

    @Test
    void collected() throws IOException {
        Path file = directory.resolve("thread_dump.txt");
        Files.writeString(file, "Full thread dump\nline 2\n", StandardCharsets.UTF_8);
        TestDiagnostics diagnostics = new TestDiagnostics(file);
        diagnostics.updateDescription("JVM memory entered the red zone");
        diagnostics.diagnosticsService = diagnosticsService;
        when(diagnosticsService.getResources(Diagnostics.Type.THREAD_DUMP)).thenReturn(Resource.directory(directory.toFile()));
        diagnostics.collect();
        assertNull(diagnostics.getFailure());

        TestDiagnostics copy = roundTrip(diagnostics);
        assertCommon(diagnostics, copy);
        assertEquals(diagnostics.getUri(), copy.getUri());
        assertEquals(diagnostics.getSize(), copy.getSize());
        assertArrayEquals(diagnostics.getData(), copy.getData());
        assertEquals(diagnostics.getStartTime(), copy.getStartTime());
        assertEquals(diagnostics.getEndTime(), copy.getEndTime());
        assertEquals(diagnostics.getDuration(), copy.getDuration());
        assertEquals("Full thread dump", copy.getHead(1));
    }

    @Test
    void failed() {
        HeapDump heapDump = new HeapDump("Test");
        heapDump.diagnosticsService = diagnosticsService;
        when(diagnosticsService.getResources(Diagnostics.Type.HEAP_DUMP)).thenThrow(new IllegalStateException("Boom"));
        heapDump.collect();
        assertNotNull(heapDump.getFailure());

        HeapDump copy = roundTrip(heapDump);
        assertCommon(heapDump, copy);
        assertNotNull(copy.getFailure());
        assertEquals(heapDump.getFailure().getType(), copy.getFailure().getType());
        assertEquals(heapDump.getFailure().getSeverity(), copy.getFailure().getSeverity());
        assertInstanceOf(IllegalStateException.class, copy.getFailure().getThrowable());
        assertEquals("Boom", copy.getFailure().getThrowable().getMessage());
        assertEquals(heapDump.getFailure().getRootCauseDescription(), copy.getFailure().getRootCauseDescription());
    }

    @Test
    void options() {
        FlightRecording flightRecording = roundTrip(new FlightRecording("Test", FlightRecording.Options.builder()
                .duration(Duration.ofSeconds(5)).settings(FlightRecording.Settings.DEFAULT).build()));
        assertEquals(Duration.ofSeconds(5), flightRecording.getOptions().getDuration());
        assertEquals(FlightRecording.Settings.DEFAULT, flightRecording.getOptions().getSettings());

        HeapDump heapDump = roundTrip(new HeapDump("Test", HeapDump.Options.builder()
                .live(false).compressionLevel(9).build()));
        assertFalse(heapDump.getOptions().isLive());
        assertEquals(9, heapDump.getOptions().getCompressionLevel());

        Histogram histogram = roundTrip(new Histogram("Test", Histogram.Options.builder().live(false).build()));
        assertFalse(histogram.getOptions().isLive());

        Profile profile = roundTrip(new Profile("Test", Profile.Options.builder().duration(Duration.ofSeconds(10))
                .event(Profile.Event.ALLOC).format(Profile.Format.JFR).interval(1000).threads(true).build()));
        assertEquals(Duration.ofSeconds(10), profile.getOptions().getDuration());
        assertEquals(Profile.Event.ALLOC, profile.getOptions().getEvent());
        assertEquals(Profile.Format.JFR, profile.getOptions().getFormat());
        assertEquals(1000, profile.getOptions().getInterval());
        assertTrue(profile.getOptions().isThreads());

        ThreadDump threadDump = roundTrip(new ThreadDump("Test", ThreadDump.Options.builder()
                .format(ThreadDump.Format.JSON).lockedSynchronizers(false).build()));
        assertEquals(ThreadDump.Format.JSON, threadDump.getOptions().getFormat());
        assertFalse(threadDump.getOptions().isLockedSynchronizers());

        VmReport vmReport = roundTrip(new VmReport("Test", VmReport.Options.builder()
                .nativeMemory(VmReport.NativeMemory.DETAIL).scale(VmReport.Scale.KB).build()));
        assertEquals(VmReport.NativeMemory.DETAIL, vmReport.getOptions().getNativeMemory());
        assertEquals(VmReport.Scale.KB, vmReport.getOptions().getScale());
    }

    private void assertCommon(Diagnostics expected, Diagnostics actual) {
        assertNotSame(expected, actual);
        assertSame(expected.getClass(), actual.getClass());
        assertEquals(expected.getId(), actual.getId());
        assertEquals(expected.getName(), actual.getName());
        assertEquals(expected.getDescription(), actual.getDescription());
        assertEquals(expected.getType(), actual.getType());
        assertNotNull(actual.getOptions());
        assertSame(expected.getOptions().getClass(), actual.getOptions().getClass());
    }

    private static <T extends Diagnostics> T roundTrip(T diagnostics) {
        byte[] bytes = Serde.serialize(diagnostics);
        assertNotNull(bytes);
        return Serde.deserialize(bytes);
    }

    /**
     * Diagnostics which copies an existing file.
     */
    private static class TestDiagnostics extends Diagnostics {

        private transient Path source;

        private TestDiagnostics() {
        }

        private TestDiagnostics(Path source) {
            super("Test", Options.create());
            this.source = source;
        }

        @Override
        protected Type getType() {
            return Type.THREAD_DUMP;
        }

        @Override
        protected void collect(Resource diagnostics) throws Exception {
            Path target = getFile(diagnostics, "txt").toPath();
            Files.copy(source, target);
            updateResource(Resource.file(target.toFile()));
        }
    }
}
