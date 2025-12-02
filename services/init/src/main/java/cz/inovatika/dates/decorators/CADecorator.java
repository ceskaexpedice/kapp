package cz.inovatika.dates.decorators;

public class CADecorator implements DateDecorator {

    @Override
    public String decorate(String dateText) {
        return dateText.replaceAll("ca\\.?\\s*", "?");
    }
}
