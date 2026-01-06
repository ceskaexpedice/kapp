package cz.inovatika;
import cz.inovatika.conf.ApplicationConfig;
import cz.inovatika.impl.HarvesterImpl;

import org.quartz.Job;
import org.quartz.JobExecutionContext;

import cz.inovatika.model.Instance;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import org.apache.solr.client.solrj.SolrServerException;

import java.util.logging.Level;
import java.util.logging.Logger;

public class HarvesterJob implements Job {
    public static final Logger LOGGER = Logger.getLogger(Instance.class.getName());
    @Override
    public void execute(JobExecutionContext context) {
        System.out.println("Running scheduled task: " + System.currentTimeMillis());

        performTask();
    }

    private void performTask() {
        InputStream resourceAsStream;
        try {
            resourceAsStream = new FileInputStream(System.getProperty("user.home") + "/.kapp/app.conf");
        } catch (FileNotFoundException e) {
            LOGGER.log(Level.SEVERE, "Scheduled task failed", e);
            e.printStackTrace(System.err);
            throw new RuntimeException(e);
        }
        ApplicationConfig applicationConfig = ApplicationConfig.loadFromStream(resourceAsStream);
        List<Instance> instances = applicationConfig.instances();

        // h.harvest(config,System.out::println);
        try {

            Harvester h = new HarvesterImpl();
            for (Instance i : instances) {
                LOGGER.log(Level.INFO, "Starting: {0}", i.getAcronym());
                h.harvestAndBatch(applicationConfig,i);
            }

        } catch (final IOException | SolrServerException e) {
            LOGGER.log(Level.SEVERE, "Scheduled task failed", e);
            e.printStackTrace(System.err);
            throw new RuntimeException(e);
        }
        catch (Exception e) {LOGGER.severe(e.getMessage());e.printStackTrace(System.err);throw new RuntimeException(e);}
    }
}