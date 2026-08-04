package cz.inovatika;

import cz.inovatika.conf.ApplicationConfig;
import cz.inovatika.impl.UpdaterImpl;
import org.quartz.*;
import org.quartz.impl.StdSchedulerFactory;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.Logger;

public class SchedulerMain {
    public static Scheduler scheduler;
    private static WorkflowUpdateCoordinator workflowUpdateCoordinator;
    private static WorkflowUpdateServer workflowUpdateServer;
    private static final Logger LOGGER = Logger.getLogger(SchedulerMain.class.getName());
    private static final String HARVESTERGROUP = "group1";
    private static final String UPDATERGROUP = "group2";

    public static void main(String[] args) throws SchedulerException {
        ScheduledService service = ScheduledService.fromArgs(args);

        if (service == ScheduledService.UPDATER_ONCE) {
            runUpdaterOnce();
            return;
        }

        scheduler = StdSchedulerFactory.getDefaultScheduler();
        scheduler.start();

        switch (service) {
            case HARVESTER -> initHarvestJob();
            case UPDATER -> {
                initWorkflowUpdateService();
                // Full updates run immediately after the scheduled harvest.
                // initUpdateJob();
            }
            case ALL -> {
                initWorkflowUpdateService();
                initHarvestJob();
                // Full updates run immediately after the scheduled harvest.
                // initUpdateJob();
            }
        }

        LOGGER.log(Level.INFO, "Started scheduled service: {0}", service.serviceName);
    }

    private static void runUpdaterOnce() {
        ApplicationConfig config = LoadConfiguration(LOGGER);
        try (UpdaterImpl updater = new UpdaterImpl()) {
            updater.start(config);
        }
        LOGGER.info("One-off full update completed.");
    }

    private static void initWorkflowUpdateService() {
        ApplicationConfig config = LoadConfiguration(LOGGER);
        workflowUpdateCoordinator = new WorkflowUpdateCoordinator(config);
        try {
            workflowUpdateServer = new WorkflowUpdateServer(
                    workflowUpdateCoordinator,
                    config.workflowUpdateOrDefault()
            );
            workflowUpdateServer.start();
        } catch (Exception ex) {
            workflowUpdateCoordinator.close();
            workflowUpdateCoordinator = null;
            throw new IllegalStateException("Unable to start workflow update HTTP server.", ex);
        }
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (workflowUpdateServer != null) {
                workflowUpdateServer.close();
            }
            if (workflowUpdateCoordinator != null) {
                workflowUpdateCoordinator.close();
            }
        }, "workflow-update-shutdown"));
    }

    static WorkflowUpdateCoordinator workflowUpdateCoordinator() {
        return workflowUpdateCoordinator;
    }

    static void initUpdateJob() throws SchedulerException {
        // Parameterize the interval (in hours)
        final int hoursInterval = 365*24;
        // Define the job and tie it to our Job class
        JobDetail job = JobBuilder.newJob(UpdaterJob.class)
                .withIdentity(UpdaterJob.class.getName(), UPDATERGROUP)
                .build();

        // Trigger the job to run every 'hoursInterval' hours
        Trigger trigger = TriggerBuilder.newTrigger()
                .withIdentity("MidnightTrigger", UPDATERGROUP)
                .startNow()
                .withSchedule(SimpleScheduleBuilder.simpleSchedule()
                        .withIntervalInHours(hoursInterval)
                        .repeatForever())
                .build();
        // Schedule the job
        scheduler.scheduleJob(job, trigger);

    }
    static void initHarvestJob() throws SchedulerException {
        // Parameterize the interval (in hours)
        final int hoursInterval = 24;

        // Define the job and tie it to our Job class
        JobDetail job = JobBuilder.newJob(HarvesterJob.class)
                .withIdentity(HarvesterJob.class.getName(), HARVESTERGROUP)
                .usingJobData("runType", "INCREMENTAL")
                .build();

        // Trigger the job to run every 'hoursInterval' hours
        Trigger trigger = TriggerBuilder.newTrigger()
                .withIdentity("MidnightTrigger", HARVESTERGROUP)
                .startNow()
                .withSchedule(SimpleScheduleBuilder.simpleSchedule()
                        .withIntervalInHours(hoursInterval)
                        .repeatForever())
                .build();
        // Schedule the job
        scheduler.scheduleJob(job, trigger);

    }
    /*
    static void fullHarvest() throws SchedulerException {
        if(scheduler==null){
            throw new SchedulerException("Scheduler is null (main did not run to set things up)");
        }
        // Fire the job on demand with extra parameters
        JobKey jobKey = JobKey.jobKey(HarvesterJob.class.getName(), HARVESTERGROUP);

        JobDataMap onDemandParams = new JobDataMap();
        onDemandParams.put("runType", "FULL");
        //onDemandParams.put("requestedBy", "admin");
        //onDemandParams.put("force", true);

        scheduler.triggerJob(jobKey, onDemandParams);

    }
    
    static void incrementalHarvest() throws SchedulerException {
        if(scheduler==null){
            throw new SchedulerException("Scheduler is null (main did not run to set things up)");
        }
        // Fire the job on demand with extra parameters
        JobKey jobKey = JobKey.jobKey("HarvesterJob", "group1");

        JobDataMap onDemandParams = new JobDataMap();
        onDemandParams.put("runType", "INCREMENTAL");
        //onDemandParams.put("requestedBy", "admin");
        //onDemandParams.put("force", true);

        scheduler.triggerJob(jobKey, onDemandParams);

    }*/

    static ApplicationConfig LoadConfiguration(Logger logger) {
        InputStream resourceAsStream;
        try {
            resourceAsStream = new FileInputStream(System.getProperty("user.home") + "/.kapp/app.conf");
        } catch (FileNotFoundException e) {
            logger.log(Level.SEVERE, "Failed to load configuration", e);
            e.printStackTrace(System.err);
            throw new RuntimeException(e);
        }
        return ApplicationConfig.loadFromStream(resourceAsStream);
    }

    enum ScheduledService {
        HARVESTER("harvester"),
        UPDATER("updater"),
        UPDATER_ONCE("updater-once"),
        ALL("all");

        private final String serviceName;

        ScheduledService(String serviceName) {
            this.serviceName = serviceName;
        }

        static ScheduledService fromArgs(String[] args) {
            if (args.length != 1) {
                throw new IllegalArgumentException(
                        "Expected exactly one scheduler service argument: "
                                + serviceNames()
                                + ". Received: "
                                + Arrays.toString(args)
                );
            }

            String serviceName = args[0].trim().toLowerCase(Locale.ROOT);
            for (ScheduledService service : values()) {
                if (service.serviceName.equals(serviceName)) {
                    return service;
                }
            }

            throw new IllegalArgumentException(
                    "Unsupported scheduler service: "
                            + args[0]
                            + ". Expected one of: "
                            + serviceNames()
            );
        }

        private static String serviceNames() {
            return Arrays.stream(values())
                    .map(service -> service.serviceName)
                    .reduce((left, right) -> left + ", " + right)
                    .orElse("");
        }
    }
}
