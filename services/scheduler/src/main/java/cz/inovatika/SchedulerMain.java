package cz.inovatika;

import org.quartz.*;
import org.quartz.impl.StdSchedulerFactory;

public class SchedulerMain {
    public static Scheduler scheduler;
    public static void main(String[] args) throws SchedulerException {

        // Parameterize the interval (in hours)
        final int hoursInterval = 24;

        // Define the job and tie it to our Job class
        JobDetail job = JobBuilder.newJob(HarvesterJob.class)
                .withIdentity("HarvesterJob", "group1")
                .usingJobData("runType", "INCREMENTAL")
                .build();

        // Trigger the job to run every 'hoursInterval' hours
        Trigger trigger = TriggerBuilder.newTrigger()
                .withIdentity("MidnightTrigger", "group1")
                .startNow()
                .withSchedule(SimpleScheduleBuilder.simpleSchedule()
                        .withIntervalInHours(hoursInterval)
                        .repeatForever())
                .build();


        scheduler = StdSchedulerFactory.getDefaultScheduler();
        scheduler.start();


        // Schedule the job
        scheduler.scheduleJob(job, trigger);
    }
    static void fullHarvest() throws SchedulerException {
        if(scheduler==null){
            throw new  SchedulerException("Scheduler is null (main did not run to set things up)");
        }
        // Fire the job on demand with extra parameters
        JobKey jobKey = JobKey.jobKey("HarvesterJob", "group1");

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
}
