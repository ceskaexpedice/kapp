package cz.inovatika.workflow;

public enum ParentState {

    CONTROLLED("CONTROLLED"),
    TO_CONTROL("TO_CONTROL");


    String value;

    ParentState(String value) {
        this.value = value;
    }

    @Override
    public String toString() {
        return this.value;
    }


}
