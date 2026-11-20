package net.microfalx.argus.report;

import net.microfalx.lang.Serde;
import net.microfalx.resource.MimeType;
import net.microfalx.resource.Resource;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

class ReportContentTest {

    @Test
    void create() {
        Report.Content content = Report.Content.create("Report");
        assertNotNull(content.getId());
        assertEquals("Report", content.getName());
        assertNotNull(content.getCreatedAt());
        assertEquals(content.getCreatedAt(), content.getModifiedAt());
        assertNull(content.getSummary());
        assertNull(content.getContent());
        assertFalse(content.hasArchive());
        assertNotEquals(content.getId(), Report.Content.create("Report").getId());
    }

    @Test
    void immutable() {
        Report.Content content = Report.Content.create("Report");
        Report.Content changed = content.withSummary("summary").withContent("content").withArchiveId("archive");
        assertNotSame(content, changed);
        assertNull(content.getSummary());
        assertNull(content.getContent());
        assertNull(content.getArchiveId());
        assertEquals(content.getId(), changed.getId());
        assertEquals("summary", changed.getSummary());
        assertEquals("content", changed.getContent());
        assertTrue(changed.hasArchive());
    }

    @Test
    void resources() throws IOException {
        Report.Content content = Report.Content.create("Report").withSummary("<html>summary</html>")
                .withContent("<html>content</html>");
        assertEquals("<html>summary</html>", content.getSummaryResource().loadAsString());
        assertEquals("<html>content</html>", content.getContentResource().loadAsString());
        assertEquals(MimeType.TEXT_HTML.getValue(), content.getContentResource().getMimeType());
        assertEquals("", Report.Content.create("Report").getContentResource().loadAsString());
    }

    @Test
    void serializeContent() {
        Report.Content content = Report.Content.create("Report").withSummary("summary").withContent("content")
                .withArchiveId("archive").withArchive(Resource.text("zip"));
        Report.Content deserialized = (Report.Content) Serde.deserialize(Serde.serialize(content));
        assertEquals(content.getId(), deserialized.getId());
        assertEquals(content.getName(), deserialized.getName());
        assertEquals(content.getCreatedAt(), deserialized.getCreatedAt());
        assertEquals("summary", deserialized.getSummary());
        assertEquals("content", deserialized.getContent());
        assertEquals("archive", deserialized.getArchiveId());
        // the archive resource is transient
        assertNotNull(content.getArchive());
        assertNull(deserialized.getArchive());
    }

    @Test
    void serializeArchive() throws IOException {
        byte[] data = {'P', 'K', 3, 4};
        Report.Archive archive = new Report.Archive("id", "report.zip", MimeType.APPLICATION_ZIP.getValue(), data);
        Report.Archive deserialized = (Report.Archive) Serde.deserialize(Serde.serialize(archive));
        assertEquals("id", deserialized.getId());
        assertEquals("report.zip", deserialized.getFileName());
        assertArrayEquals(data, deserialized.getData());
        Resource resource = deserialized.toResource();
        assertEquals("report.zip", resource.getFileName());
        assertEquals(MimeType.APPLICATION_ZIP.getValue(), resource.getMimeType());
        assertArrayEquals(data, resource.loadAsBytes());
    }
}
