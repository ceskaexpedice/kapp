package cz.inovatika.conf;

import cz.inovatika.touchstore.TouchStoreFactory;

public record TouchStoreConfig(TouchStoreFactory factory, String baseDir) {
}
