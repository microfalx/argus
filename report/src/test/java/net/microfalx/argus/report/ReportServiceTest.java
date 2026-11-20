package net.microfalx.argus.report;

import net.lingala.zip4j.ZipFile;
import net.lingala.zip4j.model.FileHeader;
import net.microfalx.argus.report.fragment.LoggerProvider;
import net.microfalx.lang.JvmUtils;
import net.microfalx.lang.annotation.Provider;
import net.microfalx.resource.MimeType;
import net.microfalx.resource.Resource;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ReportServiceTest extends AbstractReportServiceTestCase {

    @Test
    void initialize() {
        assertEquals(6, reportService.getProviders().size());
    }

    @Test
    void createTemplate() throws IOException {
        LoggerProvider provider = new LoggerProvider();
        Template template = reportService.createTemplate("logger");
        assertNotNull(template);
        template.addVariable("report", new Report(reportService));
        provider.update(template);
        Resource resource = Resource.memory();
        template.render(resource);
        Assertions.assertThat(resource.loadAsString()).contains("This system");
    }

    @Test
    void sendReport() throws IOException {
        Report report = reportService.send(Duration.ofHours(1));
        Report.Content content = report.getContent().orElseThrow();
        assertNotNull(content.getId());
        assertNotNull(content.getCreatedAt());
        assertEquals(report.getName(), content.getName());
        Assertions.assertThat(content.getName()).contains("System Report - Test System");
        Assertions.assertThat(content.getSummary()).isNotBlank().contains("<html");
        Assertions.assertThat(content.getContent()).isNotBlank().contains("<html");
        Assertions.assertThat(content.getSummaryResource().loadAsString()).isEqualTo(content.getSummary());
        Assertions.assertThat(content.getContentResource().loadAsString()).isEqualTo(content.getContent());
        assertTrue(content.hasArchive());
        // the archive is attached right after send
        assertNotNull(content.getArchive());
        assertEquals(MimeType.APPLICATION_ZIP.getValue(), content.getArchive().getMimeType());
    }

    @Test
    void reportWithoutContent() {
        assertTrue(reportService.createReport().getContent().isEmpty());
    }

    @Test
    void getContent() {
        Report.Content content = sendAndGetContent();
        Optional<Report.Content> found = reportService.getContent(content.getId());
        assertTrue(found.isPresent());
        assertEquals(content.getId(), found.get().getId());
        assertEquals(content.getName(), found.get().getName());
        assertEquals(content.getCreatedAt(), found.get().getCreatedAt());
        assertEquals(content.getSummary(), found.get().getSummary());
        assertEquals(content.getContent(), found.get().getContent());
        assertEquals(content.getArchiveId(), found.get().getArchiveId());
        // the archive resource is not serialized, it is only available by lookup
        assertNull(found.get().getArchive());
    }

    @Test
    void getContents() {
        Report.Content content = sendAndGetContent();
        Assertions.assertThat(reportService.getContents()).extracting(Report.Content::getId).contains(content.getId());
        LocalDateTime createdAt = content.getCreatedAt();
        Assertions.assertThat(reportService.getContents(createdAt.minusSeconds(1), createdAt.plusSeconds(1)))
                .extracting(Report.Content::getId).contains(content.getId());
        Assertions.assertThat(reportService.getContents(createdAt.minusDays(2), createdAt.minusDays(1)))
                .extracting(Report.Content::getId).doesNotContain(content.getId());
    }

    @Test
    void getContentNotFound() {
        assertTrue(reportService.getContent("missing").isEmpty());
        assertTrue(reportService.getArchive("missing").isEmpty());
    }

    @Test
    void getEncryptedArchive() throws IOException {
        Report.Content content = sendAndGetContent();
        Optional<Resource> archive = reportService.getArchive(content.getId());
        assertTrue(archive.isPresent());
        assertArrayEquals(content.getArchive().loadAsBytes(), archive.get().loadAsBytes());
        assertEquals(MimeType.APPLICATION_ZIP.getValue(), archive.get().getMimeType());
        Assertions.assertThat(archive.get().getFileName()).startsWith("support_report_").endsWith(".zip");
        File file = JvmUtils.getTemporaryFile("report_archive", ".zip");
        Resource.file(file).copyFrom(archive.get());
        try (ZipFile zipFile = new ZipFile(file, reportService.getSettings().getPassword().toCharArray())) {
            assertTrue(zipFile.isEncrypted());
            List<FileHeader> headers = zipFile.getFileHeaders();
            Assertions.assertThat(headers).extracting(FileHeader::getFileName)
                    .anyMatch(name -> name.startsWith("support_report_") && name.endsWith(".html"));
            FileHeader report = headers.stream().filter(header -> header.getFileName().endsWith(".html"))
                    .findFirst().orElseThrow();
            try (InputStream inputStream = zipFile.getInputStream(report)) {
                Assertions.assertThat(new String(inputStream.readAllBytes(), StandardCharsets.UTF_8)).contains("<html");
            }
        } finally {
            file.delete();
        }
    }

    @Test
    void getPlainArchive() throws IOException {
        reportService.setSettings(reportService.getSettings().withPassword(null));
        Report.Content content = sendAndGetContent();
        assertEquals(MimeType.TEXT_HTML.getValue(), content.getArchive().getMimeType());
        Optional<Resource> archive = reportService.getArchive(content.getId());
        assertTrue(archive.isPresent());
        assertEquals(MimeType.TEXT_HTML.getValue(), archive.get().getMimeType());
        Assertions.assertThat(archive.get().loadAsString()).isEqualTo(content.getContent());
    }

    private Report.Content sendAndGetContent() {
        return reportService.send(Duration.ofHours(1)).getContent().orElseThrow();
    }

    @Test
    void createReport() throws IOException {
        Report report = reportService.createReport();
        assertNotNull(report);
        assertEquals(6, report.getFragments().size());
    }

    @Provider
    public static class TestReportingListener implements ReportingListener {

        @Override
        public void update(Template template) {
            template.addVariable("test", "test");
        }

        @Override
        public boolean send(Set<String> destinations, String title,
                            Resource summary, Resource fullReport,
                            Optional<Resource> attachment) {
            ReportTestUtils.openInBrowser(summary);
            ReportTestUtils.openInBrowser(fullReport);
            return false;
        }
    }


}