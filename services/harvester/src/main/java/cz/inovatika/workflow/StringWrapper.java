package cz.inovatika.workflow;

public class StringWrapper {
    public String value;
    public StringWrapper(String value) {
        value = value == null ? "" : value;
        this.value = value;
    }
}
