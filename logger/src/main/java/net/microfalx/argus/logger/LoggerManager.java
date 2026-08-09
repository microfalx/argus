package net.microfalx.argus.logger;

import net.microfalx.configuration.Configuration;
import net.microfalx.lang.ClassUtils;
import net.microfalx.lang.FileUtils;
import net.microfalx.lang.JvmUtils;
import net.microfalx.lang.ObjectUtils;
import net.microfalx.resource.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static net.microfalx.lang.ArgumentUtils.requireNonNull;
import static net.microfalx.lang.ExceptionUtils.getRootCauseDescription;
import static net.microfalx.lang.FileUtils.isDirectoryWritable;
import static net.microfalx.lang.IOUtils.*;
import static net.microfalx.lang.StringUtils.isEmpty;
import static net.microfalx.lang.StringUtils.isNotEmpty;

/**
 * A collection of appenders used to log everything to files.
 */
class LoggerManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(LoggerManager.class);

    private final LoggerServiceImpl loggerService;
    private final Configuration configuration = Configuration.get();
    private File logsDirectory;

    public LoggerManager(LoggerServiceImpl loggerService) {
        requireNonNull(loggerService);
        this.loggerService = loggerService;
    }

    boolean hasLogsDirectory() {
        File configuredLogsDirectory = getConfiguredLogsDirectory();
        return JvmUtils.hasLogsDirectory() || configuredLogsDirectory != null;
    }

    File getLogsDirectory() {
        if (logsDirectory == null) {
            File configuredLogsDirectory = getConfiguredLogsDirectory();
            logsDirectory = ObjectUtils.defaultIfNull(configuredLogsDirectory, JvmUtils.getLogsDirectory());
        }
        return logsDirectory;
    }

    void move(Resource resource) {
        File[] files = getLogsDirectory().listFiles(this::acceptArchives);
        if (files != null) {
            for (File file : files) {
                try {
                    Resource source = Resource.file(file);
                    Resource destination = resource.resolve(file.getName());
                    destination.copyFrom(source);
                    source.delete();
                } catch (IOException e) {
                    LOGGER.warn("Failed to move log archive file: {}, root cause: {}", file.getAbsolutePath(), getRootCauseDescription(e));
                }
            }
        }
    }

    File archiveLogs() {
        String archiveFileName = "logs_archive_" + FORMATTER.format(LocalDateTime.now()) + ".zip";
        File archiveFile = new File(getLogsDirectory(), archiveFileName);
        int fileCount = 0;
        try {
            try (ZipOutputStream zipFile = new ZipOutputStream(getBufferedOutputStream(archiveFile))) {
                File[] files = getLogsDirectory().listFiles(this::acceptLogs);
                if (files != null) {
                    for (File file : files) {
                        fileCount++;
                        add(zipFile, file);
                    }
                }
            }
            if (fileCount == 0) FileUtils.remove(archiveFile);
        } catch (IOException e) {
            LOGGER.warn("Failed to archive logs to file: {}, root cause: {}", archiveFile.getAbsolutePath(), getRootCauseDescription(e));
        }
        return archiveFile;
    }

    void register() {
        try {
            if (hasLogsDirectory()) {
                archiveLogs();
                updateAppenders();
            }
        } catch (Exception e) {
            LOGGER.atError().setCause(e).log("Error registering logger appenders");
        }
    }

    private void updateAppenders() {
        LOGGER.debug("Loaded {}", loggerService.getAppenders().size() + " from descriptors");
        Collection<LoggingLibrary> libraries = ClassUtils.resolveProviderInstances(LoggingLibrary.class);
        for (LoggingLibrary library : libraries) {
            if (library instanceof AbstractLoggingLibrary all) {
                all.directory = getLogsDirectory();
                all.appenders = loggerService.getAppenders();
                all.settings = loggerService.getSettings();
                all.listener = loggerService;
            }
            library.install();
        }
    }

    private File getConfiguredLogsDirectory() {
        String path = loggerService.getSettings().getDirectory();
        if (isEmpty(path)) path = configuration.get("argus.logger.directory");
        if (isEmpty(path)) {
            File logs = new File(JvmUtils.getWorkingDirectory(false), "logs");
            if (logs.exists() && isDirectoryWritable(logs)) path = logs.getAbsolutePath();
        }
        return isNotEmpty(path) && new File(path).exists() && isDirectoryWritable(new File(path)) ? new File(path) : null;
    }

    private void add(ZipOutputStream outputStream, File file) throws IOException {
        String name = file.getName();
        ZipEntry entry = new ZipEntry(name);
        entry.setSize(file.length());
        outputStream.putNextEntry(entry);
        appendStream(outputStream, getBufferedInputStream(file), false);
        outputStream.closeEntry();
        if (!("boot.log".equals(name) || RETAIN_LOG_PATTERN.matcher(name).matches())) {
            FileUtils.remove(file);
        }
    }

    private boolean acceptLogs(File file) {
        return file.isFile() && !file.getName().endsWith(".zip");
    }

    private boolean acceptArchives(File file) {
        return file.isFile() && file.getName().endsWith(".zip");
    }

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    private static final Pattern RETAIN_LOG_PATTERN = Pattern.compile("boot\\.(.*)\\.log");

}
