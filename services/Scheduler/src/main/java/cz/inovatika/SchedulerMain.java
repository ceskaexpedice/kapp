package cz.inovatika;

import org.quartz.*;
import org.quartz.impl.StdSchedulerFactory;

public class SchedulerMain {

    public static void main(String[] args) throws SchedulerException {

        // Parameterize the interval (in hours)
        int hoursInterval = 24;

        // Define the job and tie it to our Job class
        JobDetail job = JobBuilder.newJob(HarvesterJob.class)
                .withIdentity("HarvesterJob", "group1")
                .build();

        // Trigger the job to run every 'hoursInterval' hours
        Trigger trigger = TriggerBuilder.newTrigger()
                .withIdentity("MidnightTrigger", "group1")
                .startNow()
                .withSchedule(SimpleScheduleBuilder.simpleSchedule()
                        .withIntervalInHours(hoursInterval)
                        .repeatForever())
                .build();

        // Grab the Scheduler instance from the Factory
        Scheduler scheduler = StdSchedulerFactory.getDefaultScheduler();

        // Start it
        scheduler.start();

        // Schedule the job
        scheduler.scheduleJob(job, trigger);
    }
}
