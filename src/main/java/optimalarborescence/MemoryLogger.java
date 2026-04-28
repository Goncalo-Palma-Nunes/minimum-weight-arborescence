package optimalarborescence;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class MemoryLogger {
    
    private static final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private static final MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
    private static final long INTERVAL_SECONDS = 60 * 5; // Log 5 every minutes

    private static void logMemoryUsage(String fileLogName, long timestamp) {
        try {
            long heapUsed = memoryBean.getHeapMemoryUsage().getUsed();
            long nonHeapUsed = memoryBean.getNonHeapMemoryUsage().getUsed();
            long totalUsed = heapUsed + nonHeapUsed;
            System.out.printf("Memory Usage - Heap: %d bytes, Non-Heap: %d bytes, Total: %d bytes%n", heapUsed, nonHeapUsed, totalUsed);

            // Ensure parent directory exists (FileWriter will not create parent dirs)
            if (fileLogName == null || fileLogName.isEmpty()) {
                fileLogName = "memory_usage.csv";
            }
            java.io.File file = new java.io.File(fileLogName);
            java.io.File parent = file.getParentFile();
            if (parent != null && !parent.exists()) {
                boolean ok = parent.mkdirs();
                if (!ok && !parent.exists()) {
                    System.err.println("MemoryLogger: failed to create parent directories for " + fileLogName);
                }
            }

            // Append to file (exceptions handled so the scheduled task isn't suppressed)
            try (java.io.FileWriter fw = new java.io.FileWriter(file, true)) {
                fw.write(String.format("%d,%d,%d,%d%n", timestamp, heapUsed, nonHeapUsed, totalUsed));
            } catch (java.io.IOException e) {
                System.err.println("MemoryLogger: error writing to " + fileLogName + ": " + e.getMessage());
            }
        } catch (Throwable t) {
            // Catch everything to prevent the scheduled executor from cancelling future runs
            System.err.println("MemoryLogger: unexpected error: " + t.getMessage());
            t.printStackTrace();
        }
    }

    private static void startLogging(String fileLogName) {
        long[] timestamp = {0};
        scheduler.scheduleAtFixedRate(() -> {
            logMemoryUsage(fileLogName, timestamp[0]);
            timestamp[0] += INTERVAL_SECONDS;
        }, 0, INTERVAL_SECONDS, TimeUnit.SECONDS);
    }
    
    // Public API to start the logger from other classes
    public static void start(String fileLogName) {
        startLogging(fileLogName);
    }
    
    // Public API to stop the logger (call on shutdown)
    public static void stop() {
        scheduler.shutdownNow();
    }
}
