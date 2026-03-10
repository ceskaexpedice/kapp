package cz.inovatika;

public class Constants {

    /** Collections */
    public static class  Collection {
        public static final String TITLES_COLLECTION = "titles";
        public static final String AUTHORS_COLLECTION = "authors";
    }

    /** Authors fields */
    public static final class AuthorsCollectionFields {
        public static class AppComputedFields {
            public static final String AUTHOR_DATE_STR = "date";
            public static final String AUTHOR_YEAR_DEATH = "year_death";
            public static final String AUTHOR_YEAR_BIRTH = "year_birth";

            public static final String AUTHOR_DATE_DEATH = "date_death";
            public static final String AUTHOR_DATE_BIRTH = "date_birth";

            public static final String AUTHOR_CONFIRMED_YEAR_DEATH = "confirmed_end_year";
            public static final String AUTHOR_CONFIRMED_YEAR_BIRTH = "confirmed_start_year";
        }
    }

    /** Titles fields */
    public static class TitlesCollectionFields {

        public static final class KramFields {

            public static final String AUTHORS_KRAMERIUS_FIELD = "authors_kramerius";
            public static final String AUTHORS_IDS = "authors_ids";
            public static final String NAME_IDENTIFIER_FIELD = "nameIdentifier";

            public static final String AUTHORS_FIELD = "authors";
            public static final String BIBLIO_MODS_FIELD = "biblio_mods";
            public static final String BIBLIO_MODS_MD_5_FIELD = "biblio_mods_md5";
            public static final String DATE_STR_FIELD = "date_issued_str";
            public static final String PHYSICAL_LOCATIONS_FACET_FIELD = "physical_locations";
            public static final String PID_FIELD = "pid";
            public static final String MODEL_FIELD = "model";
            public static final String ROOT_TITLE_FIELD = "root_title";
            public static final String ROOT_PID_FIELD = "root_pid";
            public static final String TITLE_FIELD = "title";
            public static final String LICENSES_FIELD = "licenses";
            public static final String LICENSES_DL_FIELD = "licenses_";

        }


        /**
         * Pole, jejichz hodnota se ziskava z krameria !! Pozor, nazvy poli v solru nejsou shode s tim co ma K7 v solru
         */
        public static class AppDataFields {

            public static final String AUTHORS_KRAMERIUS_FIELD = "authors_kramerius";
            public static final String AUTHORS_IDS = "authors_ids";
            public static final String NAME_IDENTIFIER_FIELD = "nameIdentifier";

            public static final String AUTHORS_FIELD = "authors";
            public static final String BIBLIO_MODS_FIELD = "biblio_mods";
            public static final String BIBLIO_MODS_MD_5_FIELD = "biblio_mods_md5";
            public static final String DATE_ISSUED_STR = "date_issued_str";
            public static final String DATE_ISSUED_YEAR_FIELD = "date_issued_year";
            public static final String DATE_ISSUED_START_YEAR_FIELD = "date_issued_start_year";
            public static final String DATE_ISSUED_END_YEAR_FIELD = "date_issued_end_year";


            public static final String PHYSICAL_LOCATIONS_FACET_FIELD = "physical_locations";
            public static final String PID_FIELD = "pid";
            public static final String MODEL_FIELD = "model";
            public static final String ROOT_TITLE_FIELD = "root_title";
            public static final String ROOT_PID_FIELD = "root_pid";
            public static final String TITLE_FIELD = "title";
            public static final String LICENSES_FIELD = "licenses";
            public static final String LICENSES_DL_FIELD = "licenses_";

        }


        /**
         * Pole generovane aplikaci
         */
        public static class AppComputedFields {

            //assigned_license
            public static final String ASSIGNED_LICENSES = "assigned_licenses";

            // application fields
            public static final String IS_HIERARCHICAL_FIELD = "is_hierarchical";
            public static final String STATE_FIELD = "state";
            public static final String STATE_DATE_FIELD = "state_date";
            public static final String STATE_USER_FIELD = "state_user";
            public static final String STATE_REASON_FIELD = "state_reason";
            public static final String STATE_HISTORY_FIELD = "state_history";


            public static final String DATE_ISSUED_YEAR = "date_issued_year";
            public static final String DATE_ISSUED_STRING = "date_issued_str";

            public static final String DIGITAL_LIBRARY = "digital_library";

            public static final String DECISION_DATE = "decision_date";
            public static final String LABELS_FIELD = "labels";
        }

        public static class Labels {
            public static final String EXCEL = "excel";
            public static final String EXCEL_INCORRECT_LICENSE = "excel_incorrect_license";
            public static final String EXCEL_FAILED_TO_PARSE_DATE = "excel_failed_to_parse_date";
            public static final String EXCEL_SUSPICIOUS_YEAR_RANGE = "excel_suspicious_year_range";

        }
    }
}
