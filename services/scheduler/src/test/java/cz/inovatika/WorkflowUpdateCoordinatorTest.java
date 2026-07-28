package cz.inovatika;

import cz.inovatika.conf.ApplicationConfig;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class WorkflowUpdateCoordinatorTest {

    @Test
    void serializesTargetedAndFullUpdates() {
        RecordingUpdater updater = new RecordingUpdater();
        ApplicationConfig config = new ApplicationConfig(
                null, null, null, null, null, null
        );

        try (WorkflowUpdateCoordinator coordinator
                = new WorkflowUpdateCoordinator(config, updater)) {
            var first = coordinator.updatePids(List.of("uuid:first"));
            var full = coordinator.runFullUpdate();
            var second = coordinator.updatePids(List.of("uuid:second"));

            first.join();
            full.join();
            second.join();
        }

        Assertions.assertEquals(1, updater.maximumConcurrentCalls.get());
        Assertions.assertEquals(
                List.of("target:uuid:first", "full", "target:uuid:second"),
                updater.calls
        );
    }

    private static final class RecordingUpdater implements Updater {

        private final AtomicInteger activeCalls = new AtomicInteger();
        private final AtomicInteger maximumConcurrentCalls = new AtomicInteger();
        private final List<String> calls = Collections.synchronizedList(new ArrayList<>());

        @Override
        public void start(ApplicationConfig applicationConfig) {
            recordCall("full");
        }

        @Override
        public List<TitleUpdateResult> updatePids(
                ApplicationConfig applicationConfig,
                Collection<String> pids
        ) {
            String pid = pids.iterator().next();
            recordCall("target:" + pid);
            return List.of(new TitleUpdateResult(pid, "unchanged", null, null, null));
        }

        private void recordCall(String value) {
            int active = activeCalls.incrementAndGet();
            maximumConcurrentCalls.accumulateAndGet(active, Math::max);
            try {
                calls.add(value);
                Thread.sleep(20);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(ex);
            } finally {
                activeCalls.decrementAndGet();
            }
        }
    }
}
