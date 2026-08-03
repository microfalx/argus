package net.microfalx.argus.report;

import net.microfalx.jvm.ServerMetrics;
import net.microfalx.jvm.VirtualMachineMetrics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public abstract class AbstractReportServiceTestCase {

    @InjectMocks protected ReportService reportService;

    @BeforeEach
    void setup() throws Exception {
        reportService.initialize();
        collectVMStats();
    }

    void collectVMStats() {
        VirtualMachineMetrics vmm = VirtualMachineMetrics.get();
        ServerMetrics sm = ServerMetrics.get();
        for (int i = 0; i < 5; i++) {
            vmm.scrape();
            sm.scrape();
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
