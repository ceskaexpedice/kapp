package cz.inovatika;
import cz.inovatika.conf.ApplicationConfig;

import cz.inovatika.impl.UpdaterImpl;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobExecutionContext;


import java.util.logging.Level;
import java.util.logging.Logger;

@DisallowConcurrentExecution
public class UpdaterJob implements Job {
    public static final Logger LOGGER = Logger.getLogger(UpdaterJob.class.getName());
    @Override
    public void execute(JobExecutionContext context) {
        System.out.println("Running scheduled task: "+ UpdaterJob.class.getName() +", at time: " + System.currentTimeMillis());
        performTask();


    }

    private void performTask() {

        try {
            WorkflowUpdateCoordinator coordinator = SchedulerMain.workflowUpdateCoordinator();
            if (coordinator != null) {
                coordinator.runFullUpdate().join();
            } else {
                ApplicationConfig applicationConfig = SchedulerMain.LoadConfiguration(LOGGER);
                Updater updater = new UpdaterImpl();
                updater.start(applicationConfig);
            }
            LOGGER.info("Updating Completed.");


        } catch (final Exception e) {
            LOGGER.log(Level.SEVERE, UpdaterJob.class.getName() +" failed", e);
            e.printStackTrace(System.err);
            throw new RuntimeException(e);
        }
    }
    public static void main(String[] args){
        UpdaterJob job = new UpdaterJob();
        job.execute(null);
    }
}
