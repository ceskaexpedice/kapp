package cz.inovatika.dates.decorators;

public class AsiDecorator implements DateDecorator {

    //        value = value.replaceAll("asi\\s*", "?").trim();

    /*
            if (value.contains("asi")) {
            String year = value.replaceAll("[^0-9]", "");
            if (year.length() == 4) {
                value = year + "?";
            } else {
                dateFormatError(recordNumber, Originalvalue, value);
            }

     */

    @Override
    public String decorate(String dateText) {
        return  dateText.replaceAll("asi\\s*", "?").trim();
    }
}
