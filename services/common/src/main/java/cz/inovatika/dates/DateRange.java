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

    /// Does only check suspicious year ranges
    public void mapToSolrDocument(SolrInputDocumentWrapper docWrapper) throws NumberFormatException {

        int yearStart = Integer.parseInt(this.startYear());
        int yearEnd = Integer.parseInt(this.endYear());

        if (yearStart > yearEnd || yearEnd > Year.now().getValue()) {
            LOGGER.log(Level.SEVERE, "Suspicious year range: {0}-{1}", new Object[]{this.startYear(), this.endYear()});
        }
        docWrapper.setField(AppComputedFields.DATE_ISSUED_YEAR, yearEnd);
/*
        docWrapper.setField(AppDataFields.DATE_ISSUED_START_YEAR_FIELD, yearStart);

        docWrapper.setField(AppDataFields.DATE_ISSUED_START_YEAR_FIELD, String.format("%04d-01-01T00:00:00Z", yearStart));
        docWrapper.setField(AppDataFields.DATE_ISSUED_END_YEAR_FIELD, String.format("%04d-12-31T00:00:00Z", yearEnd));

        docWrapper.setField(AppComputedFields.CONFIRMED_START_YEAR, this.startConfirmed());
        docWrapper.setField(AppComputedFields.CONFIRMED_END_YEAR, this.endConfirmed());
        */
    }
}