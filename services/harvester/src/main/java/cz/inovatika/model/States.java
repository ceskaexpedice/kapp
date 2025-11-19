package cz.inovatika.model;

public enum States {
    NONCONTROLLED_PRIVATE("noncontrolled_private"),
    NONCONTROLLED_PUBLIC("noncontrolled_public"),
    TO_CONTROL("to_control"),
    CONTROLLED_PUBLIC("controlled_public"),
    CONTROLLED_PRIVATE("controlled_private"),
    CONTROLLED_UNKNOWN("controlled_unknown");

    String value;

    States(String value) {
        this.value = value;
    }

    @Override
    public String toString() {
        return this.value;
    }
}
