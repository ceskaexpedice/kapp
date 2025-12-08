package cz.inovatika;

public class Constants {

    /** Collections */
    public static class  Collection {
        public static final String TITLES_COLLECTION = "titles";
        public static final String AUTHORS_COLLECTION = "authors";
    }

    public static class TitlesCollectionFields {

        public static class FromKramFields {

           public static final String AUTHORS_KRAMERIUS_FIELD = "authors_kramerius";
            public static final String NAME_IDENTIFIER_FIELD = "nameIdentifier";
            public static final String AUTHORS_FIELD = "authors";
            public static final String BIBLIO_MODS_FIELD = "biblio_mods";
            public static final String BIBLIO_MODS_MD_5_FIELD = "biblio_mods_md5";
            public static final String DATE_STR_FIELD = "date.str";
            public static final String PHYSICAL_LOCATIONS_FACET_FIELD = "physical_locations.facet";
            public static final String PID_FIELD = "pid";
            public static final String MODEL_FIELD = "model";
            public static final String ROOT_TITLE_FIELD = "root.title";
            public static final String ROOT_PID_FIELD = "root.pid";
            public static final String TITLE_FIELD = "title";
            public static final String LICENSES_FIELD = "licenses";
            public static final String LICENSES_DL_FIELD = "licenses_";

        }

        public static class ApplFields {

            //assigned_license
            public static final String ASSIGNED_LICENSE = "assigned_license";

            // application fields
            public static final String IS_HIERARCHICAL_FIELD = "is_hierarchical";
            public static final String STATE_FIELD = "state";
            public static final String STATE_DATE_FIELD = "state_date";
            public static final String STATE_USER_FIELD = "state_user";
            public static final String STATE_REASON_FIELD = "state_reason";
            public static final String STATE_HISTORY_FIELD = "state_history";
            public static final String DATE_RANGE_START_YEAR = "date_range_start.year";
            public static final String DATE_RANGE_END_YEAR = "date_range_end.year";
            public static final String DATE_MIN = "date.min";
            public static final String DATE_MAX = "date.max";
            public static final String CONFIRMED_START_YEAR = "confirmed_start_year";
            public static final String CONFIRMED_END_YEAR = "confirmed_end_year";
            public static final String DIGITAL_LIBRARY = "digital_library";

            public static final String DECISION_DATE = "decision_date";
        }
    }


}
