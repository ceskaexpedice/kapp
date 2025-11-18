package cz.inovatika.model;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

public class InstanceTest {


    @Test
    public void testVersions() {
        Instance instanceV7 = new Instance();
        instanceV7.setApiVersion(APIVersion.v7);
        instanceV7.setAcronym("mzk");

        String[] fieldListV7 = instanceV7.getApiVersion().fieldList();

        List<String> expectationsV7 = List.of("pid", "own_pid_path", "root.pid", "model","titles.search", "licenses_of_ancestors", "licenses");
        List<String> realValueV7 = Arrays.asList(fieldListV7);

        Assertions.assertEquals(expectationsV7,  realValueV7);

        String queryV7 = instanceV7.getApiVersion().iterationQuery();
        //Assertions.assertEquals("model:(monograph+OR+monographunit+OR+periodical+OR+periodicalvolume)",queryV7);

        Instance instanceV5 = new Instance();
        instanceV5.setApiVersion(APIVersion.v5);
        instanceV5.setAcronym("nkp");

        String[] fieldListV5 = instanceV5.getApiVersion().fieldList();

        List<String> expecationsV5 = List.of("PID","pid_path","root_pid","fedora.model","dc.title", "dnnt-labels", "dostupnost");
        List<String> realValueV5 = Arrays.asList(fieldListV5);

        Assertions.assertEquals(expecationsV5, realValueV5);

        String queryV5 = instanceV5.getApiVersion().iterationQuery();
        Assertions.assertEquals("fedora.model:(monograph+OR+monographunit)",queryV5);
    }
}
