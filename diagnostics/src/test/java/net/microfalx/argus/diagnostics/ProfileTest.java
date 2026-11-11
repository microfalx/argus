package net.microfalx.argus.diagnostics;

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
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProfileTest {

    @TempDir
    private Path directory;

    @Mock
    private DiagnosticsService diagnosticsService;

    @Test
    void defaultOptions() {
        Profile profile = new Profile("Test");
        assertEquals(Diagnostics.Type.PROFILE, profile.getType());
        Profile.Options options = profile.getOptions();
        assertEquals(Duration.ofSeconds(60), options.getDuration());
        assertEquals(Profile.Event.CPU, options.getEvent());
        assertEquals(Profile.Format.FLAMEGRAPH, options.getFormat());
        assertEquals(0, options.getInterval());
        assertTrue(options.isAllUser());
        assertFalse(options.isThreads());
    }

    @Test
    void customOptions() {
        Profile.Options options = Profile.Options.builder().duration(Duration.ofSeconds(5)).event(Profile.Event.ALLOC)
                .format(Profile.Format.JFR).interval(1024).allUser(false).threads(true).build();
        Profile profile = new Profile("Test", options);
        assertSame(options, profile.getOptions());
    }

    @Test
    void invalidOptions() {
        assertThrows(IllegalArgumentException.class, () -> new Profile("Test",
                Profile.Options.builder().duration(Duration.ofMillis(500)).build()));
        assertThrows(IllegalArgumentException.class, () -> new Profile("Test",
                Profile.Options.builder().interval(-1).build()));
    }

    @Test
    void defaultArguments() {
        File file = new File(directory.toFile(), "profile.html");
        List<String> arguments = new Profile("Test").getArguments(file);
        assertEquals(List.of("-d", "60", "-e", "cpu", "--all-user", "-o", "flamegraph", "-f", file.getAbsolutePath()),
                arguments.subList(0, 9));
        assertEquals(Long.toString(ProcessHandle.current().pid()), arguments.get(arguments.size() - 1));
        assertEquals(Profile.getLibrary().isFile(), arguments.contains("--libpath"));
    }

    @Test
    void customArguments() {
        File file = new File(directory.toFile(), "profile.jfr");
        Profile.Options options = Profile.Options.builder().duration(Duration.ofSeconds(5)).event(Profile.Event.WALL)
                .format(Profile.Format.JFR).interval(10_000_000).allUser(false).threads(true).build();
        List<String> arguments = new Profile("Test", options).getArguments(file);
        assertEquals(List.of("-d", "5", "-e", "wall", "-i", "10000000", "-t", "-o", "jfr", "-f", file.getAbsolutePath()),
                arguments.subList(0, 11));
    }

    @Test
    void toolNotAvailable() {
        assumeFalse(new DiagnosticsTool("asprof").isAvailable(), "asprof is installed");
        Profile profile = collect(Profile.Options.builder().duration(Duration.ofSeconds(1)).build());
        assertNotNull(profile.getFailure());
        assertNotNull(profile.getDuration());
        assertNull(profile.getUri());
    }

    @Test
    void flamegraph() throws IOException {
        assumeTrue(new DiagnosticsTool("asprof").isAvailable(), "asprof is not installed");
        Profile profile = collect(Profile.Options.builder().duration(Duration.ofSeconds(2)).build());
        assertProfile(profile, "html");
        assertNotEquals(Diagnostics.BINARY_CONTENT, profile.getHead(5));
    }

    @Test
    void collapsed() throws IOException {
        assumeTrue(new DiagnosticsTool("asprof").isAvailable(), "asprof is not installed");
        Profile profile = collect(Profile.Options.builder().duration(Duration.ofSeconds(2))
                .event(Profile.Event.WALL).format(Profile.Format.COLLAPSED).build());
        assertProfile(profile, "txt");
    }

    private Profile collect(Profile.Options options) {
        when(diagnosticsService.getResources(Diagnostics.Type.PROFILE)).thenReturn(Resource.directory(directory.toFile()));
        Profile profile = new Profile("Test", options);
        profile.diagnosticsService = diagnosticsService;
        profile.collect();
        return profile;
    }

    private void assertProfile(Profile profile, String extension) throws IOException {
        assertNull(profile.getFailure(), () -> "Profile failed: " + profile.getFailure());
        assertNotNull(profile.getStartTime());
        assertNotNull(profile.getEndTime());
        assertNotNull(profile.getUri());
        File file = new File(profile.getUri());
        assertTrue(file.isFile());
        assertTrue(file.length() > 0);
        assertEquals(directory.toFile().getCanonicalFile(), file.getParentFile().getCanonicalFile());
        assertTrue(file.getName().matches("profile_\\d{8}_\\d{6}_" + profile.getId() + "\\." + extension), file.getName());
    }
}
