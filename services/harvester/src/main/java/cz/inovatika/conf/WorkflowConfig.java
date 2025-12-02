package cz.inovatika.conf;

import java.time.Year;

public class WorkflowConfig {

    private int publicYear;
    private int onsiteYear;

    public WorkflowConfig() {
    }

    public void setOnsiteYear(int onsiteYear) {
        this.onsiteYear = onsiteYear;
    }

    public int getOnsiteYear() {
        return onsiteYear;
    }

    public int getPublicYear() {
        return publicYear;
    }

    public void setPublicYear(int publicYear) {
        this.publicYear = publicYear;
    }

    private int calculateYear(int s) {
        Year y = Year.now();
        y = y.minusYears(s);
        return y.getValue();
    }

    public int calculatePublicYear() {
        return calculateYear(publicYear);
    }

    public int calculateOnsiteYear() {
        return calculateYear(onsiteYear);
    }

    public boolean isPublicYear(int year) {
        return year <= calculatePublicYear();
    }

    public boolean isOnsiteYear(int year) {
        return year >= calculateOnsiteYear();
    }
}
