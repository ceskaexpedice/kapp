package cz.inovatika.touchstore;

import java.io.IOException;
import java.util.List;

public interface TouchStore {

    public void touch(String pid, String md5, String source, List<String> physicalLocations) throws IOException;
    public void storeMods(String pid, String biblioMods) throws IOException;


    public boolean exists(String pid);

    public boolean isModsChanged(String pid, String md5) throws IOException;

    public List<String> getPhysicalLocations(String pid) throws IOException;

    public String getMd5(String pid) throws IOException;
}
