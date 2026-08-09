package net.microfalx.argus.logger;

import net.microfalx.argus.api.LoggerSettings;
import net.microfalx.configuration.Configuration;
import net.microfalx.configuration.ConfigurationService;
import net.microfalx.lang.service.ServiceLocator;
import net.microfalx.resource.Resource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LoggerManagerTest {

    @Mock Configuration configuration;
    @Mock ConfigurationService configurationService;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setup() {
        ServiceLocator.register(configurationService);
        when(configurationService.getConfiguration()).thenReturn(configuration);
    }

    @Test
    void usesDirectoryFromSettingsWhenConfigured() {
        LoggerManager loggerManager = createManager(tempDir);

        assertTrue(loggerManager.hasLogsDirectory());
        assertEquals(tempDir.toFile().getAbsolutePath(), loggerManager.getLogsDirectory().getAbsolutePath());
    }

    @Test
    void archiveLogsKeepsBootFilesAndRemovesOtherLogs() throws IOException {
        Path bootLog = createFile(tempDir, "boot.log");
        Path rollingBootLog = createFile(tempDir, "boot.20260101.log");
        Path appLog = createFile(tempDir, "application.log");
        LoggerManager loggerManager = createManager(tempDir);

        File archive = loggerManager.archiveLogs();

        assertTrue(archive.exists(), "Expected archive file to be created");
        assertTrue(Files.exists(bootLog), "boot.log should be preserved");
        assertTrue(Files.exists(rollingBootLog), "rolling boot logs should be preserved");
        assertFalse(Files.exists(appLog), "non-boot logs should be removed after archiving");
        assertEquals(Set.of("boot.log", "boot.20260101.log", "application.log"), zipEntryNames(archive));
    }

    @Test
    void archiveLogsDeletesEmptyArchiveWhenNoLogFilesExist() {
        LoggerManager loggerManager = createManager(tempDir);

        File archive = loggerManager.archiveLogs();

        assertFalse(archive.exists(), "Archive should be removed when there are no log files");
    }

    @Test
    void moveTransfersZipArchivesOnly() throws IOException {
        Path sourceArchive = createFile(tempDir, "old-logs.zip");
        Path sourceLog = createFile(tempDir, "application.log");
        Path destinationPath = Files.createDirectory(tempDir.resolve("destination"));
        Resource destination = Resource.directory(destinationPath.toFile());
        LoggerManager loggerManager = createManager(tempDir);

        loggerManager.move(destination);

        assertFalse(Files.exists(sourceArchive), "Source zip should be moved and deleted");
        assertTrue(Files.exists(sourceLog), "Non-archive files should remain in source directory");
        assertTrue(Files.exists(destinationPath.resolve("old-logs.zip")), "Zip archive should exist at destination");
    }

    private LoggerManager createManager(Path directory) {
        LoggerServiceImpl loggerService = new LoggerServiceImpl();
        loggerService.setSettings(LoggerSettings.create(directory.toFile()));
        return new LoggerManager(loggerService);
    }

    private Path createFile(Path directory, String name) throws IOException {
        Path file = directory.resolve(name);
        Files.writeString(file, "content-" + name, StandardCharsets.UTF_8);
        return file;
    }

    private Set<String> zipEntryNames(File archive) throws IOException {
        Set<String> names = new HashSet<>();
        try (ZipFile zipFile = new ZipFile(archive)) {
            Enumeration<? extends ZipEntry> entries = zipFile.entries();
            while (entries.hasMoreElements()) {
                names.add(entries.nextElement().getName());
            }
        }
        return names;
    }

}