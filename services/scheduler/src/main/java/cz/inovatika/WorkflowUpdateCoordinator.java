package cz.inovatika;

import cz.inovatika.Updater.TitleUpdateResult;
import cz.inovatika.conf.ApplicationConfig;
import cz.inovatika.impl.UpdaterImpl;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Serializes scheduled and UI-triggered workflow updates in one process.
 */
public final class WorkflowUpdateCoordinator implements AutoCloseable {

    private final ApplicationConfig config;
    private final Updater updater;
    private final ExecutorService executor;

    public WorkflowUpdateCoordinator(ApplicationConfig config) {
        this(config, new UpdaterImpl());
    }

    WorkflowUpdateCoordinator(ApplicationConfig config, Updater updater) {
        this.config = config;
        this.updater = updater;
        this.executor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "workflow-update-worker");
            thread.setDaemon(false);
            return thread;
        });
    }

    public CompletableFuture<List<TitleUpdateResult>> updatePids(Collection<String> pids) {
        return CompletableFuture.supplyAsync(() -> updater.updatePids(config, pids), executor);
    }

    public CompletableFuture<Void> runFullUpdate() {
        return CompletableFuture.runAsync(() -> updater.start(config), executor);
    }

    @Override
    public void close() {
        executor.shutdownNow();
    }
}
