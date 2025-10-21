package cz.inovatika.model;


import java.util.Arrays;
import java.util.Objects;

public class HarvestingItem {

    private String rootPid;
    private String pid;
    private String model;
    private String titles;

    public Instance instance;


    public String getPid() {
        return pid;
    }

    public void setPid(String pid) {
        this.pid = pid;
    }

    public String getRootPid() {
        return this.rootPid;
    }

    public void setRootPid(String rootPid) {
        this.rootPid = rootPid;
    }

    public String getTitles() {
        return titles;
    }

    public void setTitles(String titles) {
        this.titles = titles;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public Instance getInstance() {
        return instance;
    }

    public void setInstance(Instance instance) {
        this.instance = instance;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        HarvestingItem that = (HarvestingItem) o;
        return Objects.equals(rootPid, that.rootPid) && Objects.equals(pid, that.pid) && Objects.equals(model, that.model) && Objects.equals(titles, that.titles) && Objects.equals(instance, that.instance);
    }

    @Override
    public int hashCode() {
        return Objects.hash(rootPid, pid, model, titles, instance);
    }

    @Override
    public String toString() {
        return "HarvestingItem{" +
                "rootPid='" + rootPid + '\'' +
                ", pid='" + pid + '\'' +
                ", model='" + model + '\'' +
                ", title='" + (titles != null ? Arrays.asList(titles) : "null") + '\'' +
                ", instance=" + instance +
                '}';
    }
}
