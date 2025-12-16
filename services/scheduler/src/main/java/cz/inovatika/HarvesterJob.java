package cz.inovatika;
import cz.inovatika.conf.ApplicationConfig;
import cz.inovatika.impl.HarvesterImpl;

import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;

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
    public void execute(JobExecutionContext context) throws JobExecutionException {
        System.out.println("Running scheduled task: " + System.currentTimeMillis());

        performTask();
    }

    private void performTask() {
        InputStream resourceAsStream = null;
        try {
            resourceAsStream = new FileInputStream(System.getProperty("user.home") + "/.kapp/app.conf");
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }
        ApplicationConfig applicationConfig = ApplicationConfig.loadFromStream(resourceAsStream);
        List<Instance> instances = applicationConfig.getInstances();

        // h.harvest(config,System.out::println);
        try {

            Harvester h = new HarvesterImpl();
            for (Instance i : instances) {
                LOGGER.log(Level.INFO, "Starting: {0}", i.getAcronym());
                h.harvestAndBatch(applicationConfig,i);
            }

        } catch (final IOException | SolrServerException e) {
            throw new RuntimeException(e);
        }
    }
}