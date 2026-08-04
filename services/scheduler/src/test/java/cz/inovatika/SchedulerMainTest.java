package cz.inovatika;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SchedulerMainTest {

    @Test
    void acceptsOneOffUpdaterMode() {
        assertEquals(
                SchedulerMain.ScheduledService.UPDATER_ONCE,
                SchedulerMain.ScheduledService.fromArgs(new String[]{"updater-once"})
        );
    }
}
