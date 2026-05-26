package cz.inovatika.impl;

import cz.inovatika.conf.ApplicationConfig;

import java.io.FileInputStream;
import java.io.InputStream;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class LastAuthorUpdaterMain {
    private static final Logger LOGGER = Logger.getLogger(LastAuthorUpdaterMain.class.getName());

    private LastAuthorUpdaterMain() {
    }

    public static void main(String[] args) {
        LastAuthorUpdaterOptions options = LastAuthorUpdaterOptions.fromArgs(args);
        try (InputStream configStream = new FileInputStream(System.getProperty("user.home") + "/.kapp/app.conf");
             LastAuthorUpdaterImpl updater = new LastAuthorUpdaterImpl()) {
            ApplicationConfig applicationConfig = ApplicationConfig.loadFromStream(configStream);
            updater.start(applicationConfig, options);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Last author update failed", e);
            throw new RuntimeException(e);
        }
    }
}
