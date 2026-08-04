package cz.inovatika.workflow;

public class StringWrapper {
    public String value;
    public StringWrapper(String value) {
        this.value = value == null ? "" : value;
    }
    @Override
    public String toString() {
        return value;
    }
}
