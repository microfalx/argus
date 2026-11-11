package net.microfalx.argus.diagnostics;

import net.microfalx.resource.Resource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.CleanupMode;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.GZIPInputStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HeapDumpTest {

    private static final String HPROF_HEADER = "JAVA PROFILE";

    @TempDir(cleanup = CleanupMode.NEVER)
    private Path directory;

    @Mock
    private DiagnosticsService diagnosticsService;

    @Test
    void defaultOptions() {
        HeapDump heapDump = new HeapDump("Test");
        assertEquals(Diagnostics.Type.HEAP_DUMP, heapDump.getType());
        assertTrue(heapDump.getOptions().isLive());
        assertEquals(1, heapDump.getOptions().getCompressionLevel());
        assertTrue(heapDump.getOptions().isCompressed());
    }

    @Test
    void customOptions() {
        HeapDump.Options options = HeapDump.Options.builder().live(false).compressionLevel(0).build();
        HeapDump heapDump = new HeapDump("Test", options);
        assertSame(options, heapDump.getOptions());
        assertFalse(heapDump.getOptions().isLive());
        assertFalse(heapDump.getOptions().isCompressed());
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 10})
    void invalidCompressionLevel(int level) {
        HeapDump.Options options = HeapDump.Options.builder().compressionLevel(level).build();
        assertThrows(IllegalArgumentException.class, () -> new HeapDump("Test", options));
    }

    @Test
    void notStarted() {
        HeapDump heapDump = new HeapDump("Test");
        assertNull(heapDump.getDuration());
        assertNull(heapDump.getUri());
        assertThrows(DiagnosticsNotFoundException.class, heapDump::getResource);
    }

    @Test
    void dumpUncompressed() throws IOException {
        HeapDump heapDump = collect(HeapDump.Options.builder().compressionLevel(0).build());
        File file = assertDump(heapDump, ".hprof");
        assertTrue(readHeader(file, false).startsWith(HPROF_HEADER));
    }

    @Test
    void dumpCompressed() throws IOException {
        HeapDump heapDump = collect(HeapDump.Options.builder().compressionLevel(1).build());
        File file = assertDump(heapDump, ".hprof.gz");
        try (InputStream inputStream = Files.newInputStream(file.toPath())) {
            byte[] magic = inputStream.readNBytes(2);
            assertEquals((byte) 0x1f, magic[0]);
            assertEquals((byte) 0x8b, magic[1]);
        }
        assertTrue(readHeader(file, true).startsWith(HPROF_HEADER));
    }

    @Test
    void dumpAllObjects() throws IOException {
        HeapDump heapDump = collect(HeapDump.Options.builder().live(false).compressionLevel(0).build());
        File file = assertDump(heapDump, ".hprof");
        assertTrue(readHeader(file, false).startsWith(HPROF_HEADER));
    }

    @Test
    void resource() throws IOException {
        HeapDump heapDump = collect(HeapDump.Options.builder().compressionLevel(0).build());
        assertDump(heapDump, ".hprof");
        Resource resource = heapDump.getResource();
        assertTrue(resource.exists());
        assertTrue(resource.length() > 0);
    }

    private HeapDump collect(HeapDump.Options options) {
        when(diagnosticsService.getResources(Diagnostics.Type.HEAP_DUMP)).thenReturn(Resource.directory(directory.toFile()));
        HeapDump heapDump = new HeapDump("Test", options);
        heapDump.diagnosticsService = diagnosticsService;
        heapDump.collect();
        return heapDump;
    }

    private File assertDump(HeapDump heapDump, String extension) throws IOException {
        assertNull(heapDump.getFailure(), () -> "Heap dump failed: " + heapDump.getFailure());
        assertNotNull(heapDump.getStartTime());
        assertNotNull(heapDump.getEndTime());
        assertFalse(heapDump.getEndTime().isBefore(heapDump.getStartTime()));
        assertNotNull(heapDump.getDuration());

        assertNotNull(heapDump.getUri());
        File file = new File(heapDump.getUri());
        assertTrue(file.isFile());
        assertTrue(file.length() > 0);
        assertEquals(directory.toFile().getCanonicalFile(), file.getParentFile().getCanonicalFile());
        assertTrue(file.getName().matches("heap_dump_\\d{8}_\\d{6}_" + heapDump.getId()
                + extension.replace(".", "\\.")), file.getName());

        // only the final dump is left behind (the uncompressed dump is removed after compression)
        try (var files = Files.list(directory)) {
            assertEquals(1, files.count());
        }
        return file;
    }

    private static String readHeader(File file, boolean compressed) throws IOException {
        try (InputStream inputStream = compressed ? new GZIPInputStream(Files.newInputStream(file.toPath()))
                : Files.newInputStream(file.toPath())) {
            return new String(inputStream.readNBytes(HPROF_HEADER.length()), StandardCharsets.US_ASCII);
        }
    }
}
