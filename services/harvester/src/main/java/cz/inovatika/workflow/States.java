package cz.inovatika.workflow;

import cz.inovatika.conf.WorkflowConfig;
import cz.inovatika.model.HarvesterContext;
import org.json.JSONObject;

public enum States {

    // https://docs.google.com/document/d/1zMXdaKiAhshUANmxTD1MHnBibjituskJnGEq-LHz7jU/edit?tab=t.0


    /** new imported documents */
    IMPORTED("imported", LicensesAssignation.NONE, true) {

        @Override
        public States calculateNextState(HarvesterContext ctx, JSONObject oldDoc, JSONObject indexingDoc) {
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

        @Override
        public States transitionByCurator(HarvesterContext ctx, JSONObject oldDoc, JSONObject indexingDoc, States desiredState) {
            return null;
        }
    },

    /** non controlled private > 70 let */
    NONCONTROLLED_PRIVATE("noncontrolled_private", LicensesAssignation.ONSITE, false) {
        @Override
        public States calculateNextState(HarvesterContext ctx, JSONObject oldDoc, JSONObject indexingDoc) {
            return null;
        }

        @Override
        public States transitionByCurator(HarvesterContext ctx, JSONObject oldDoc, JSONObject indexingDoc, States desiredState) {
            return NONCONTROLLED_PRIVATE;
        }
    },

    /** controlled < 110 let */
    NONCONTROLLED_PUBLIC("noncontrolled_public", LicensesAssignation.PUBLIC, false) {
        @Override
        public States calculateNextState(HarvesterContext ctx, JSONObject oldDoc, JSONObject indexingDoc) {
            return null;
        }

        @Override
        public States transitionByCurator(HarvesterContext ctx, JSONObject oldDoc, JSONObject indexingDoc, States desiredState) {
            return NONCONTROLLED_PUBLIC;
        }
    },

    /** to control , curator decision */
    TO_CONTROL("to_control", LicensesAssignation.NONE ,false) {
        @Override
        public States calculateNextState(HarvesterContext ctx, JSONObject oldDoc, JSONObject indexingDoc) {
            return null;
        }

        @Override
        public States transitionByCurator(HarvesterContext ctx, JSONObject oldDoc, JSONObject indexingDoc, States desiredState) {
            switch (desiredState) {
                case CONTROLLED_PRIVATE, CONTROLLED_PUBLIC, CONTROLLED_UNKNOWN -> {
                    return desiredState;
                }
            }
            return null;
        }
    },
    CONTROLLED_PUBLIC("controlled_public", LicensesAssignation.PUBLIC, false) {
        @Override
        public States calculateNextState(HarvesterContext ctx, JSONObject oldDoc, JSONObject indexingDoc) {
            return null;
        }

        @Override
        public States transitionByCurator(HarvesterContext ctx, JSONObject oldDoc, JSONObject indexingDoc, States desiredState) {
            return CONTROLLED_PUBLIC;
        }
    },
    CONTROLLED_PRIVATE("controlled_private", LicensesAssignation.ONSITE, false) {
        @Override
        public States transitionByCurator(HarvesterContext ctx, JSONObject oldDoc, JSONObject indexingDoc, States desiredState) {
            return CONTROLLED_PRIVATE;
        }

        @Override
        public States calculateNextState(HarvesterContext ctx, JSONObject oldDoc, JSONObject indexingDoc) {
            return null;
        }
    },

    CONTROLLED_UNKNOWN("controlled_unknown", LicensesAssignation.NONE, false) {
        @Override
        public States transitionByCurator(HarvesterContext ctx, JSONObject oldDoc, JSONObject indexingDoc, States desiredState) {
            return CONTROLLED_UNKNOWN;
        }

        @Override
        public States calculateNextState(HarvesterContext ctx, JSONObject oldDoc, JSONObject indexingDoc) {
            return null;
        }
    },
    CONTROLLED_WO_RIGHTS("controlled_unknown", LicensesAssignation.PUBLIC, false) {
        @Override
        public States transitionByCurator(HarvesterContext ctx, JSONObject oldDoc, JSONObject indexingDoc, States desiredState) {
            return CONTROLLED_WO_RIGHTS;
        }

        @Override
        public States calculateNextState(HarvesterContext ctx, JSONObject oldDoc, JSONObject indexingDoc) {
            return null;
        }
    },
    CONTROLLED_UNKNOWN_PUBLIC("controlled_unknown", LicensesAssignation.PUBLIC, false) {
        @Override
        public States transitionByCurator(HarvesterContext ctx, JSONObject oldDoc, JSONObject indexingDoc, States desiredState) {
            return CONTROLLED_UNKNOWN_PUBLIC;
        }

        @Override
        public States calculateNextState(HarvesterContext ctx, JSONObject oldDoc, JSONObject indexingDoc) {
            return null;
        }
    },
    CONTROLLED_BLOCKED("controlled_unknown", LicensesAssignation.NONE, false) {
        @Override
        public States transitionByCurator(HarvesterContext ctx, JSONObject oldDoc, JSONObject indexingDoc, States desiredState) {
            return CONTROLLED_BLOCKED;
        }

        @Override
        public States calculateNextState(HarvesterContext ctx, JSONObject oldDoc, JSONObject indexingDoc) {
            return null;
        }
    },
    PARENT_TO_CONTROLL("parent_to_controll",LicensesAssignation.NONE, true) {
        @Override
        public States calculateNextState(HarvesterContext ctx, JSONObject oldDoc, JSONObject indexingDoc) {
            return PARENT_CONTROLLED;
        }

        @Override
        public States transitionByCurator(HarvesterContext ctx, JSONObject oldDoc, JSONObject indexingDoc, States desiredState) {
            return null;
        }
    },

    PARENT_CONTROLLED("parent_to_controll",LicensesAssignation.NONE, true) {
        @Override
        public States calculateNextState(HarvesterContext ctx, JSONObject oldDoc, JSONObject indexingDoc) {
            return PARENT_CONTROLLED;
        }

        @Override
        public States transitionByCurator(HarvesterContext ctx, JSONObject oldDoc, JSONObject indexingDoc, States desiredState) {
            return null;
        }
    };

    String value;
    boolean automaticSwitch;
    LicensesAssignation licensesAssignation;

    States(String value, LicensesAssignation licensesAssignation, boolean automaticSwitch) {
        this.value = value;
        this.automaticSwitch = automaticSwitch;
        this.licensesAssignation = licensesAssignation;
    }

    public abstract States calculateNextState(HarvesterContext ctx, JSONObject oldDoc, JSONObject indexingDoc);

    public abstract States transitionByCurator(HarvesterContext ctx, JSONObject oldDoc, JSONObject indexingDoc, States desiredState);

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
