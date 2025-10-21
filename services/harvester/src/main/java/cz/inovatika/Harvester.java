package cz.inovatika;

import cz.inovatika.model.HarvestingItem;
import cz.inovatika.model.Instance;

import java.util.function.Consumer;

public interface Harvester {

    public void harvest(Instance config, Consumer<HarvestingItem> consumer);
}
