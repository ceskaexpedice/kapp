package cz.inovatika;
import cz.inovatika.conf.ApplicationConfig;
import cz.inovatika.impl.HarvesterImpl;
import cz.inovatika.impl.UpdaterImpl;

import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;

import java.util.logging.Level;
import java.util.logging.Logger;

@DisallowConcurrentExecution
public class HarvesterJob implements Job {
    public static final Logger LOGGER = Logger.getLogger(HarvesterJob.class.getName());
    @Override
    public void execute(JobExecutionContext context) {
        System.out.println("Running scheduled task: " + System.currentTimeMillis());
        JobDataMap dataMap = context.getMergedJobDataMap();
        String runType = dataMap.getString("runType");
        switch (runType) {
            case "FULL" -> {LOGGER.log(Level.INFO, "Full scheduled task: {0}", System.currentTimeMillis());
                throw new UnsupportedOperationException("Full harvest via scheduler is not supported yet.");
            }
            case "INCREMENTAL" -> performTask();
            default -> throw new IllegalArgumentException("Invalid runType: " + runType);
        }


    }

    private void performTask() {
        ApplicationConfig applicationConfig = SchedulerMain.LoadConfiguration(LOGGER);

        Harvester harvester = new HarvesterImpl();
        try {
            harvester.start(applicationConfig);
            LOGGER.info("Harvesting Completed.");

            WorkflowUpdateCoordinator coordinator = SchedulerMain.workflowUpdateCoordinator();
            if (coordinator != null) {
                coordinator.runFullUpdate().join();
            } else {
                try (UpdaterImpl updater = new UpdaterImpl()) {
                    updater.start(applicationConfig);
                }
            }
            LOGGER.info("Updating Completed.");
        } catch (final Exception e) {
            LOGGER.log(Level.SEVERE, "Scheduled task failed", e);
            e.printStackTrace(System.err);
            throw new RuntimeException(e);
        } finally {
            harvester.closeClients();
        }
    }
}
