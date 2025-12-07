package cz.inovatika.dates;

import cz.inovatika.Constants;
import cz.inovatika.solr.SolrInputDocumentWrapper;

import java.time.Year;
import java.util.logging.Level;
import java.util.logging.Logger;

import static cz.inovatika.Constants.TitlesCollectionFields.*;

public record DateRange(
    String startYear,
    String endYear,
    boolean startConfirmed,
    boolean endConfirmed
) {

    public static final Logger LOGGER = Logger.getLogger(DateRange.class.getName());

    public void mapToSolrDocument(SolrInputDocumentWrapper docWrapper) throws NumberFormatException {

        int yearStart = Integer.parseInt(this.startYear());
        int yearEnd = Integer.parseInt(this.endYear());

        if (yearStart > yearEnd || yearEnd > Year.now().getValue()) {
            LOGGER.log(Level.SEVERE, "Suspicious year range: " + this.startYear() + "-" + this.endYear());
        }

        docWrapper.setField(ApplFields.DATE_RANGE_START_YEAR, yearStart);
        docWrapper.setField(ApplFields.DATE_RANGE_END_YEAR, yearEnd);

        docWrapper.setField(ApplFields.DATE_MIN, String.format("%04d-01-01T00:00:00Z", yearStart));
        docWrapper.setField(ApplFields.DATE_MAX, String.format("%04d-12-31T00:00:00Z", yearEnd));

        docWrapper.setField(ApplFields.CONFIRMED_START_YEAR, this.startConfirmed());
        docWrapper.setField(ApplFields.CONFIRMED_END_YEAR, this.endConfirmed());
    }
}