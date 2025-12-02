package cz.inovatika.workflow;

import cz.inovatika.conf.WorkflowConfig;
import cz.inovatika.model.HarvesterContext;
import cz.inovatika.model.HarvestingItem;
import org.apache.solr.common.SolrInputDocument;
import org.json.JSONObject;

public enum States {

    // https://docs.google.com/document/d/1zMXdaKiAhshUANmxTD1MHnBibjituskJnGEq-LHz7jU/edit?tab=t.0


    /** new imported documents */
    IMPORTED("imported", LicensesAssignation.NONE, true) {
        @Override
        public States nextState(HarvesterContext ctx, JSONObject oldDoc, JSONObject indexingDoc) {
            // monograph - zjisteni, zda se jedna o jednoduchou monografii 
            // is periodical
            // is monographunit
            // is monograph
            WorkflowConfig wConfig = ctx.applicationConfig().getWorkflow();
            if (wConfig != null && indexingDoc.has("date_range_end.year")) {
                int year = indexingDoc.getInt("date_range_end.year");
                if (wConfig.isPublicYear(year)) {
                    return NONCONTROLLED_PUBLIC;
                }
                if (wConfig.isOnsiteYear(year)) {
                    return NONCONTROLLED_PRIVATE;
                }
            }
            return TO_CONTROL;
        }
    },

    /** non controlled private > 70 let */
    NONCONTROLLED_PRIVATE("noncontrolled_private", LicensesAssignation.ONSITE, false) {
        @Override
        public States nextState(HarvesterContext ctx, JSONObject oldDoc, JSONObject indexingDoc) {
            return null;
        }
    },

    /** controlled < 110 let */
    NONCONTROLLED_PUBLIC("noncontrolled_public", LicensesAssignation.PUBLIC, false) {
        @Override
        public States nextState(HarvesterContext ctx, JSONObject oldDoc, JSONObject indexingDoc) {
            return null;
        }
    },

    /** to control , currator decession */
    TO_CONTROL("to_control", LicensesAssignation.NONE ,false) {
        @Override
        public States nextState(HarvesterContext ctx, JSONObject oldDoc, JSONObject indexingDoc) {
            return null;
        }
    },
    CONTROLLED_PUBLIC("controlled_public", LicensesAssignation.PUBLIC, false) {
        @Override
        public States nextState(HarvesterContext ctx, JSONObject oldDoc, JSONObject indexingDoc) {
            return null;
        }
    },
    CONTROLLED_PRIVATE("controlled_private", LicensesAssignation.ONSITE, false) {
        @Override
        public States nextState(HarvesterContext ctx, JSONObject oldDoc, JSONObject indexingDoc) {
            return null;
        }
    },

    CONTROLLED_UNKNOWN("controlled_unknown", LicensesAssignation.NONE, false) {
        @Override
        public States nextState(HarvesterContext ctx, JSONObject oldDoc, JSONObject indexingDoc) {
            return null;
        }
    };

    String value;
    boolean automaticSwitch;
    LicensesAssignation licensesAssignation;

    States(String value, LicensesAssignation licensesAssignation, boolean automaticSwicth) {
        this.value = value;
        this.automaticSwitch = automaticSwicth;
        this.licensesAssignation = licensesAssignation;
    }

    public abstract States nextState(HarvesterContext ctx, JSONObject oldDoc, JSONObject indexingDoc);

    public String getValue() {
        return value;
    }

    public LicensesAssignation getLicensesAssignation() {
        return licensesAssignation;

    }

    public boolean isAutomaticSwitch() {
        return automaticSwitch;
    }

    @Override
    public String toString() {
        return this.value;
    }
}
