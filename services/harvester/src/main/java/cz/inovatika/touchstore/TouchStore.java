package cz.inovatika.touchstore;

import java.io.IOException;
import java.util.List;

public interface TouchStore {

    void touch(String pid, String md5, String source, String rootPid, List<String> physicalLocations) throws IOException;

    void storeMods(String pid, String biblioMods) throws IOException;

    boolean exists(String pid);

    boolean isModsChanged(String pid, String md5) throws IOException;

    List<String> getPhysicalLocations(String pid) throws IOException;

    String getRootPid(String pid) throws IOException;

    String getMd5(String pid) throws IOException;
}
