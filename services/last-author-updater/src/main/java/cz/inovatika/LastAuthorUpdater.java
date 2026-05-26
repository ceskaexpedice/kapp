package cz.inovatika;

import cz.inovatika.conf.ApplicationConfig;
import cz.inovatika.impl.LastAuthorUpdaterOptions;

public interface LastAuthorUpdater {
    void start(ApplicationConfig applicationConfig, LastAuthorUpdaterOptions options);
}
