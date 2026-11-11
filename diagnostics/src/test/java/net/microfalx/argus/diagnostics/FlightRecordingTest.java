package net.microfalx.argus.diagnostics;

import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordingFile;
import net.microfalx.resource.Resource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FlightRecordingTest {

    @TempDir
    private Path directory;

    @Mock
    private DiagnosticsService diagnosticsService;

    @Test
    void defaultOptions() {
        FlightRecording recording = new FlightRecording("Test");
        assertEquals(Diagnostics.Type.FLIGHT_RECORDING, recording.getType());
        assertEquals(Duration.ofSeconds(60), recording.getOptions().getDuration());
        assertEquals(FlightRecording.Settings.PROFILE, recording.getOptions().getSettings());
    }

    @Test
    void customOptions() {
        FlightRecording.Options options = FlightRecording.Options.builder().duration(Duration.ofSeconds(5))
                .settings(FlightRecording.Settings.DEFAULT).build();
        FlightRecording recording = new FlightRecording("Test", options);
        assertSame(options, recording.getOptions());
    }

    @Test
    void invalidOptions() {
        assertThrows(IllegalArgumentException.class, () -> new FlightRecording("Test",
                FlightRecording.Options.builder().duration(Duration.ofMillis(500)).build()));
        assertThrows(IllegalArgumentException.class, () -> new FlightRecording("Test",
                FlightRecording.Options.builder().settings(null).build()));
    }

    @Test
    void notStarted() {
        FlightRecording recording = new FlightRecording("Test");
        assertNull(recording.getDuration());
        assertNull(recording.getUri());
        assertThrows(DiagnosticsNotFoundException.class, recording::getResource);
    }

    @Test
    void profileSettings() throws IOException {
        assertRecording(collect(FlightRecording.Settings.PROFILE));
    }

    @Test
    void defaultSettings() throws IOException {
        assertRecording(collect(FlightRecording.Settings.DEFAULT));
    }

    @Test
    void head() throws IOException {
        FlightRecording recording = collect(FlightRecording.Settings.DEFAULT);
        assertRecording(recording);
        assertEquals(Diagnostics.BINARY_CONTENT, recording.getHead(5));
    }

    private FlightRecording collect(FlightRecording.Settings settings) {
        when(diagnosticsService.getResources(Diagnostics.Type.FLIGHT_RECORDING)).thenReturn(Resource.directory(directory.toFile()));
        FlightRecording.Options options = FlightRecording.Options.builder().duration(Duration.ofSeconds(1))
                .settings(settings).build();
        FlightRecording recording = new FlightRecording("Test", options);
        recording.diagnosticsService = diagnosticsService;
        recording.collect();
        return recording;
    }

    private void assertRecording(FlightRecording recording) throws IOException {
        assertNull(recording.getFailure(), () -> "Flight recording failed: " + recording.getFailure());
        assertNotNull(recording.getDuration());
        assertTrue(recording.getDuration().toMillis() >= 1000, recording.getDuration().toString());
        assertNotNull(recording.getUri());
        File file = new File(recording.getUri());
        assertTrue(file.isFile());
        assertEquals(directory.toFile().getCanonicalFile(), file.getParentFile().getCanonicalFile());
        assertTrue(file.getName().matches("flight_recording_\\d{8}_\\d{6}_" + recording.getId() + "\\.jfr"), file.getName());

        List<RecordedEvent> events = RecordingFile.readAllEvents(file.toPath());
        assertFalse(events.isEmpty());
        assertTrue(events.stream().anyMatch(event -> event.getEventType().getName().equals("jdk.JVMInformation")));
    }
}
