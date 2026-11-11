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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.locks.ReentrantLock;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ThreadDumpTest {

    private static final String THREAD_DUMP_HEADER = "Full thread dump";
    private static final String TEST_METHOD = "ThreadDumpTest";

    @TempDir
    private Path directory;

    @Mock
    private DiagnosticsService diagnosticsService;

    @Test
    void defaultOptions() {
        ThreadDump threadDump = new ThreadDump("Test");
        assertEquals(Diagnostics.Type.THREAD_DUMP, threadDump.getType());
        assertTrue(threadDump.getOptions().isLockedSynchronizers());
    }

    @Test
    void customOptions() {
        ThreadDump.Options options = ThreadDump.Options.builder().lockedSynchronizers(false).build();
        ThreadDump threadDump = new ThreadDump("Test", options);
        assertSame(options, threadDump.getOptions());
        assertFalse(threadDump.getOptions().isLockedSynchronizers());
    }

    @Test
    void notStarted() {
        ThreadDump threadDump = new ThreadDump("Test");
        assertNull(threadDump.getDuration());
        assertNull(threadDump.getUri());
        assertThrows(DiagnosticsNotFoundException.class, threadDump::getResource);
    }

    @Test
    void withLockedSynchronizers() throws IOException {
        ThreadDump threadDump = collect(ThreadDump.Options.create());
        String content = assertThreadDump(threadDump);
        assertTrue(content.contains("Locked ownable synchronizers"), content);
    }

    @Test
    void withoutLockedSynchronizers() throws IOException {
        ThreadDump threadDump = collect(ThreadDump.Options.builder().lockedSynchronizers(false).build());
        String content = assertThreadDump(threadDump);
        assertFalse(content.contains("Locked ownable synchronizers"), content);
    }

    @Test
    void textFormat() throws IOException, InterruptedException {
        try (VirtualThread virtualThread = new VirtualThread()) {
            ThreadDump threadDump = collect(ThreadDump.Options.builder().format(ThreadDump.Format.TEXT).build());
            File file = assertFile(threadDump, "txt");
            String content = Files.readString(file.toPath(), StandardCharsets.UTF_8);
            assertTrue(content.contains(TEST_METHOD), content);
            assertTrue(content.contains(virtualThread.getName()), content);
            assertTrue(content.contains("virtual"), content);
        }
    }

    @Test
    void jsonFormat() throws IOException, InterruptedException {
        try (VirtualThread virtualThread = new VirtualThread()) {
            ThreadDump threadDump = collect(ThreadDump.Options.builder().format(ThreadDump.Format.JSON).build());
            File file = assertFile(threadDump, "json");
            String content = Files.readString(file.toPath(), StandardCharsets.UTF_8);
            assertTrue(content.trim().startsWith("{"), content);
            assertTrue(content.contains("\"threadDump\""), content);
            assertTrue(content.contains(TEST_METHOD), content);
            assertTrue(content.contains(virtualThread.getName()), content);
        }
    }

    @Test
    void invalidOptions() {
        assertThrows(IllegalArgumentException.class, () -> new ThreadDump("Test",
                ThreadDump.Options.builder().format(null).build()));
    }

    @Test
    void head() throws IOException {
        ThreadDump threadDump = collect(ThreadDump.Options.create());
        assertThreadDump(threadDump);
        String head = threadDump.getHead(5);
        assertNotEquals(Diagnostics.BINARY_CONTENT, head);
        assertTrue(head.contains(THREAD_DUMP_HEADER), head);
        assertTrue(head.split("\n").length <= 5, head);
    }

    @Test
    void diagnosticCommand() {
        String dump = new ThreadDump("Test").extractWithDiagnosticCommand(true);
        assertTrue(dump.contains(THREAD_DUMP_HEADER), dump);
        assertTrue(dump.contains("\"main\""), dump);
        assertTrue(dump.contains("java.lang.Thread.State:"), dump);
        assertTrue(dump.contains("Locked ownable synchronizers"), dump);
        assertTrue(dump.contains(TEST_METHOD), dump);
    }

    @Test
    void threadBean() throws InterruptedException {
        ReentrantLock lock = new ReentrantLock();
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        Thread holder = new Thread(() -> {
            lock.lock();
            try {
                locked.countDown();
                release.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                lock.unlock();
            }
        }, "ThreadDumpTest-Holder");
        holder.setDaemon(true);
        holder.start();
        try {
            locked.await();
            String dump = new ThreadDump("Test").extractWithThreadBean(true);
            assertTrue(dump.contains(THREAD_DUMP_HEADER), dump);
            assertTrue(dump.contains("\"main\""), dump);
            assertTrue(dump.contains("java.lang.Thread.State: RUNNABLE"), dump);
            assertTrue(dump.contains("\"ThreadDumpTest-Holder\""), dump);
            assertTrue(dump.contains("daemon prio="), dump);
            assertTrue(dump.contains("- parking to wait for"), dump);
            assertTrue(dump.contains("(a java.util.concurrent.locks.ReentrantLock$NonfairSync)"), dump);
            assertTrue(dump.contains(TEST_METHOD), dump);
        } finally {
            release.countDown();
            holder.join();
        }
    }

    private ThreadDump collect(ThreadDump.Options options) {
        when(diagnosticsService.getResources(Diagnostics.Type.THREAD_DUMP)).thenReturn(Resource.directory(directory.toFile()));
        ThreadDump threadDump = new ThreadDump("Test", options);
        threadDump.diagnosticsService = diagnosticsService;
        threadDump.collect();
        return threadDump;
    }

    private File assertFile(ThreadDump threadDump, String extension) throws IOException {
        assertNull(threadDump.getFailure(), () -> "Thread dump failed: " + threadDump.getFailure());
        assertNotNull(threadDump.getStartTime());
        assertNotNull(threadDump.getEndTime());
        assertNotNull(threadDump.getDuration());

        assertNotNull(threadDump.getUri());
        File file = new File(threadDump.getUri());
        assertTrue(file.isFile());
        assertEquals(directory.toFile().getCanonicalFile(), file.getParentFile().getCanonicalFile());
        assertTrue(file.getName().matches("thread_dump_\\d{8}_\\d{6}_" + threadDump.getId() + "\\." + extension),
                file.getName());
        return file;
    }

    private String assertThreadDump(ThreadDump threadDump) throws IOException {
        File file = assertFile(threadDump, "txt");
        String content = Files.readString(file.toPath(), StandardCharsets.UTF_8);
        assertTrue(content.contains(THREAD_DUMP_HEADER), content);
        assertTrue(content.contains(TEST_METHOD), content);
        assertNotNull(threadDump.getData());
        return content;
    }

    /**
     * A (named) virtual thread, parked until closed.
     */
    private static class VirtualThread implements AutoCloseable {

        private final CountDownLatch started = new CountDownLatch(1);
        private final CountDownLatch release = new CountDownLatch(1);
        private final Thread thread;

        private VirtualThread() throws InterruptedException {
            thread = Thread.ofVirtual().name("ThreadDumpTest-Virtual").start(() -> {
                started.countDown();
                try {
                    release.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
            started.await();
        }

        private String getName() {
            return thread.getName();
        }

        @Override
        public void close() throws InterruptedException {
            release.countDown();
            thread.join();
        }
    }
}
