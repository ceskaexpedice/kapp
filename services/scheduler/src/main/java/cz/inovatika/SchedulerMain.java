package cz.inovatika;

import cz.inovatika.conf.ApplicationConfig;
import org.quartz.*;
import org.quartz.impl.StdSchedulerFactory;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.logging.Level;
import java.util.logging.Logger;

public class SchedulerMain {
    public static Scheduler scheduler;
    final static String HarvesterGroup = "group1";
    final static String UpdaterGroup = "group2";
    public static void main(String[] args) throws SchedulerException {



        scheduler = StdSchedulerFactory.getDefaultScheduler();
        scheduler.start();
        //initHarvestJob(scheduler);
        initUpdateJob(scheduler);


    }
    static void initUpdateJob(Scheduler scheduler) throws SchedulerException {
        // Parameterize the interval (in hours)
        final int hoursInterval = 365*24;
        System.out.println("XXX");
        // Define the job and tie it to our Job class
        JobDetail job = JobBuilder.newJob(UpdaterJob.class)
                .withIdentity(UpdaterJob.class.getName(), UpdaterGroup)
                .build();

        // Trigger the job to run every 'hoursInterval' hours
        Trigger trigger = TriggerBuilder.newTrigger()
                .withIdentity("MidnightTrigger", UpdaterGroup)
                .startNow()
                .withSchedule(SimpleScheduleBuilder.simpleSchedule()
                        .withIntervalInHours(hoursInterval)
                        .repeatForever())
                .build();
        // Schedule the job
        scheduler.scheduleJob(job, trigger);

    }
    static void initHarvestJob(Scheduler scheduler) throws SchedulerException {
        // Parameterize the interval (in hours)
        final int hoursInterval = 24;

        // Define the job and tie it to our Job class
        JobDetail job = JobBuilder.newJob(HarvesterJob.class)
                .withIdentity(HarvesterJob.class.getName(), HarvesterGroup)
                .usingJobData("runType", "INCREMENTAL")
                .build();

        // Trigger the job to run every 'hoursInterval' hours
        Trigger trigger = TriggerBuilder.newTrigger()
                .withIdentity("MidnightTrigger", HarvesterGroup)
                .startNow()
                .withSchedule(SimpleScheduleBuilder.simpleSchedule()
                        .withIntervalInHours(hoursInterval)
                        .repeatForever())
                .build();
        // Schedule the job
        scheduler.scheduleJob(job, trigger);

    }
    static void fullHarvest() throws SchedulerException {
        if(scheduler==null){
            throw new SchedulerException("Scheduler is null (main did not run to set things up)");
        }
        // Fire the job on demand with extra parameters
        JobKey jobKey = JobKey.jobKey(HarvesterJob.class.getName(), HarvesterGroup);

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

    }
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
}
