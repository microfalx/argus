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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VmReportTest {

    private static final String VM_INFO_SECTION = "VM Info";
    private static final String NATIVE_MEMORY_SECTION = "Native Memory";

    @TempDir
    private Path directory;

    @Mock
    private DiagnosticsService diagnosticsService;

    @Test
    void defaultOptions() {
        VmReport report = new VmReport("Test");
        assertEquals(Diagnostics.Type.VM_REPORT, report.getType());
        assertEquals(VmReport.NativeMemory.SUMMARY, report.getOptions().getNativeMemory());
        assertEquals(VmReport.Scale.MB, report.getOptions().getScale());
    }

    @Test
    void customOptions() {
        VmReport.Options options = VmReport.Options.builder().nativeMemory(VmReport.NativeMemory.DETAIL)
                .scale(VmReport.Scale.KB).build();
        VmReport report = new VmReport("Test", options);
        assertSame(options, report.getOptions());
    }

    @Test
    void invalidOptions() {
        assertThrows(IllegalArgumentException.class, () -> new VmReport("Test",
                VmReport.Options.builder().nativeMemory(null).build()));
        assertThrows(IllegalArgumentException.class, () -> new VmReport("Test",
                VmReport.Options.builder().scale(null).build()));
    }

    @Test
    void notStarted() {
        VmReport report = new VmReport("Test");
        assertNull(report.getDuration());
        assertNull(report.getUri());
        assertThrows(DiagnosticsNotFoundException.class, report::getResource);
    }

    @Test
    void report() throws IOException {
        VmReport report = collect(VmReport.Options.create());
        String content = assertReport(report);
        assertTrue(content.contains(NATIVE_MEMORY_SECTION), content);
        String tracking = VmReport.getNativeMemoryTracking();
        if (tracking == null || "off".equals(tracking)) {
            assertTrue(content.contains("Native memory tracking is not enabled"), content);
        } else {
            assertTrue(content.contains("Total: reserved="), content);
        }
    }

    @Test
    void withoutNativeMemory() throws IOException {
        VmReport report = collect(VmReport.Options.builder().nativeMemory(VmReport.NativeMemory.NONE).build());
        String content = assertReport(report);
        assertFalse(content.contains(NATIVE_MEMORY_SECTION), content);
    }

    @Test
    void nativeMemoryTracking() {
        String tracking = VmReport.getNativeMemoryTracking();
        assertNotNull(tracking);
        assertTrue(tracking.equals("off") || tracking.equals("summary") || tracking.equals("detail"), tracking);
    }

    @Test
    void head() throws IOException {
        VmReport report = collect(VmReport.Options.create());
        assertReport(report);
        String head = report.getHead(5);
        assertNotEquals(Diagnostics.BINARY_CONTENT, head);
        assertTrue(head.startsWith("VM Report 'Test'"), head);
        assertTrue(head.contains(VM_INFO_SECTION), head);
    }

    private VmReport collect(VmReport.Options options) {
        when(diagnosticsService.getResources(Diagnostics.Type.VM_REPORT)).thenReturn(Resource.directory(directory.toFile()));
        VmReport report = new VmReport("Test", options);
        report.diagnosticsService = diagnosticsService;
        report.collect();
        return report;
    }

    private String assertReport(VmReport report) throws IOException {
        assertNull(report.getFailure(), () -> "VM report failed: " + report.getFailure());
        assertNotNull(report.getDuration());
        assertNotNull(report.getUri());
        File file = new File(report.getUri());
        assertTrue(file.isFile());
        assertEquals(directory.toFile().getCanonicalFile(), file.getParentFile().getCanonicalFile());
        assertTrue(file.getName().matches("vm_report_\\d{8}_\\d{6}_" + report.getId() + "\\.txt"), file.getName());

        String content = Files.readString(file.toPath(), StandardCharsets.UTF_8);
        assertTrue(content.contains(VM_INFO_SECTION), content);
        assertTrue(content.contains("JRE version"), content);
        assertTrue(content.contains("Heap"), content);
        return content;
    }
}
