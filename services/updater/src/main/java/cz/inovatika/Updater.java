package cz.inovatika;

import cz.inovatika.conf.ApplicationConfig;
import java.util.Collection;
import java.util.List;

public interface Updater {
    void start(ApplicationConfig applicationConfig);

    List<TitleUpdateResult> updatePids(
            ApplicationConfig applicationConfig,
            Collection<String> pids
    );

    record TitleUpdateResult(
            String pid,
            String status,
            String previousState,
            String newState,
            String message
    ) {
    }
}
