package cz.inovatika.workflow;

import cz.inovatika.Constants;
import cz.inovatika.Constants.TitlesCollectionFields.ModelFields;
import cz.inovatika.Constants.TitlesCollectionFields.StateChangeReasons;
import cz.inovatika.conf.ApplicationConfig;
import cz.inovatika.conf.WorkflowConfig;

import cz.inovatika.solr.SolrInputDocumentWrapper;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

import static cz.inovatika.model.Instance.LOGGER;

public enum States {

    // https://docs.google.com/document/d/1zMXdaKiAhshUANmxTD1MHnBibjituskJnGEq-LHz7jU/edit?tab=t.0
    /**
     * new imported documents
     */
    IMPORTED("imported", LicensesAssignation.NONE, true) {
        @Override
        public States calculateNextState(String rootPid, SolrClient solrClient, ApplicationConfig config, WorkflowDoc currentDoc,StringWrapper reason) {
            if (!currentDoc.isHierarchical()) {
                switch (currentDoc.model()) {
                    case ModelFields.MONOGRAPH, ModelFields.MONOGRAPHUNIT -> {
                        States s = TO_CONTROL.calculateNextState(rootPid, solrClient, config, currentDoc, reason);
                        if (s == null) return TO_CONTROL;
                        return s;
                    }
                    case ModelFields.PERIODICALVOLUME -> {
                        reason.value = "periodical volume";
                        if (currentDoc.dateIssuedYear()==null) return TO_CONTROL;
                        return config.workflow().isPublicYear(currentDoc.dateIssuedYear())? NONCONTROLLED_PUBLIC :NONCONTROLLED_PRIVATE;
                    }
                    default -> {
                        reason.value = "not a monograph(unit) or periodical volume";
                        return NONCONTROLLED_PRIVATE;
                    }
                }
            } else {
                return checkChildren(rootPid,solrClient);
            }
        }

        @Override
        public States transitionByCurator(String rootPid, SolrClient solrClient, WorkflowDoc currentDoc, States desiredState) {
            return null;
        }
    },
    /**
     * non controlled private > 70 let
     */
    NONCONTROLLED_PRIVATE("noncontrolled_private", LicensesAssignation.ONSITE, false) {
        @Override
        public States calculateNextState(String rootPid, SolrClient solrClient, ApplicationConfig config, WorkflowDoc currentDoc,StringWrapper reason) {
            WorkflowConfig wConfig = config.workflow();
            if (wConfig==null){
                System.err.println("Workflow config is null");
                return null;}
            if (currentDoc.model().equals(ModelFields.PERIODICALVOLUME)){
                return  wConfig.isPublicYear(currentDoc.dateIssuedYear())? NONCONTROLLED_PUBLIC :NONCONTROLLED_PRIVATE;
            }
            if (currentDoc.last_year_death() != null) {
                int year = currentDoc.last_year_death();
                if (wConfig.isAuthorDeadEnoughYear(year,currentDoc.dateIssuedYear())){//it has been OnsiteYear years from the death of last author
                    reason.value = "it has been OnsiteYear years from the death of last author";
                    return TO_CONTROL;
                }
                else return null;
            }
            else if (currentDoc.dateIssuedYear() != null) {
                int year = currentDoc.dateIssuedYear();
                if  (wConfig.isPublicYear(year)){
                    reason.value = "Last author not specified and is PublicYear years already";
                    return NONCONTROLLED_PUBLIC;
                }
                else if(wConfig.isOnsiteYear(year)){//Still not ready
                    return null;
                }
                else {
                    reason.value = StateChangeReasons.INCONTRLOWINDOWISSUED;
                    return TO_CONTROL;}//in the TO_CONTROL window
            }
            LOGGER.log(Level.INFO,"State was prompted to change, but there is no info to go of off");
            return null;
        }
    },
    /**
     * controlled < 110 let
     */
    NONCONTROLLED_PUBLIC("noncontrolled_public", LicensesAssignation.PUBLIC, false) {
        @Override
        public States calculateNextState(String rootPid, SolrClient solrClient, ApplicationConfig config, WorkflowDoc currentDoc,StringWrapper reason) {
            return null;
        }
    },
    FOR_REVIEW("for_review", LicensesAssignation.NONE, false) {
        @Override
        public States calculateNextState(String rootPid, SolrClient solrClient, ApplicationConfig config, WorkflowDoc currentDoc,StringWrapper reason){
            return null;
        }
    }
    ,
    /**
     * to control , curator decision
     */
    TO_CONTROL("to_control", LicensesAssignation.NONE, false) {
        @Override
        public States calculateNextState(String rootPid, SolrClient solrClient, ApplicationConfig config, WorkflowDoc currentDoc,StringWrapper reason) {
            WorkflowConfig wConfig = config.workflow();
            if (wConfig==null)return null;
            if (currentDoc.model().equals(ModelFields.PERIODICALVOLUME)){
                return  wConfig.isPublicYear(currentDoc.dateIssuedYear())? NONCONTROLLED_PUBLIC :NONCONTROLLED_PRIVATE;
            }
            else{
                if (currentDoc.last_year_death() != null) {
                    int year = currentDoc.last_year_death();
                    if (wConfig.isAuthorDeadEnoughYear(year,currentDoc.dateIssuedYear())){
                        reason.value = StateChangeReasons.SurpassedAuthorDeath(year);//"it has been OnsiteYear years from the death of last author";
                        return NONCONTROLLED_PUBLIC;
                    }
                    else return null;
                }
                else if (currentDoc.dateIssuedYear() != null) {
                    int year = currentDoc.dateIssuedYear();
                    if  (wConfig.isPublicYear(year)){//Last author not specified and is PublicYear years already
                        reason.value = StateChangeReasons.SurpassedIssued(year);
                        return NONCONTROLLED_PUBLIC;
                    }
                    else if(wConfig.isOnsiteYear(year)){//Still not ready
                        reason.value = StateChangeReasons.NotYetIssued(year);
                        return NONCONTROLLED_PRIVATE;
                    }
                    else return null;//in the TO_CONTROL window
                }
                LOGGER.log(Level.INFO,"State was prompted to change, but there is no info to go of off");
                return null;
            }
        }
    },
    CONTROLLED_PUBLIC("controlled_public", LicensesAssignation.PUBLIC, false) {
        @Override
        public States calculateNextState(String rootPid, SolrClient solrClient, ApplicationConfig config, WorkflowDoc currentDoc,StringWrapper reason) {
            return null;
        }
    },
    CONTROLLED_PRIVATE("controlled_private", LicensesAssignation.ONSITE, false) {

        @Override
        public States calculateNextState(String rootPid, SolrClient solrClient, ApplicationConfig config, WorkflowDoc currentDoc,StringWrapper reason) {
            WorkflowConfig wConfig = config.workflow();
            if (wConfig==null)return null;
            if (currentDoc.model().equals(ModelFields.PERIODICALVOLUME)){
                return  wConfig.isPublicYear(currentDoc.dateIssuedYear())? NONCONTROLLED_PUBLIC :NONCONTROLLED_PRIVATE;
            }
            if (currentDoc.last_year_death() != null) {
                int year = currentDoc.last_year_death();
                if (wConfig.isAuthorDeadEnoughYear(year,currentDoc.dateIssuedYear())){//it has been OnsiteYear years from the death of last author
                    reason.value = StateChangeReasons.SurpassedAuthorDeath(year);
                    return FOR_REVIEW;
                }
                else return null;
            }
            else if (currentDoc.dateIssuedYear() != null) {
                int year = currentDoc.dateIssuedYear();
                if  (wConfig.isPublicYear(year)){//Last author not specified and was PublicYear years already
                    reason.value = StateChangeReasons.SurpassedIssued(year);
                    return FOR_REVIEW;
                }
                else return null;//Its either too early or was already Controlled.
            }
            LOGGER.log(Level.INFO,"State was prompted to change, but there is no info to go of off");
            return null;
        }
    },

    CONTROLLED_UNKNOWN("controlled_unknown", LicensesAssignation.NONE, false) {

        @Override
        public States calculateNextState(String rootPid, SolrClient solrClient, ApplicationConfig config, WorkflowDoc currentDoc,StringWrapper reason) {
            WorkflowConfig wConfig = config.workflow();
            if (wConfig==null)return null;
            if (currentDoc.model().equals(ModelFields.PERIODICALVOLUME)){
                return  wConfig.isPublicYear(currentDoc.dateIssuedYear())? NONCONTROLLED_PUBLIC :NONCONTROLLED_PRIVATE;
            }
            if (currentDoc.last_year_death() != null) {
                //We have new information and the authors are not sufficiently dead yet
                reason.value = "Probably new information on author";
                return FOR_REVIEW;
            }
            else if (currentDoc.dateIssuedYear() != null) {
                int year = currentDoc.dateIssuedYear();
                if  (wConfig.isOnsiteYear(year)){//We got date issued, and it's not even onsite years so we can wait until it makes sense to check it
                    reason.value = "Probably new information on date issued; "+StateChangeReasons.NotYetIssued(year);
                    return NONCONTROLLED_PRIVATE;
                }
                else if (wConfig.isPublicYear(year)){
                    reason.value = "Probably new information on date issued; "+StateChangeReasons.SurpassedIssued(year);
                    return FOR_REVIEW;//There is new information and it may be public now.
                }
                else return null; //It was flagged as Unknown likely due to lack of authors. Keep until public
            }
            LOGGER.log(Level.INFO,"State was prompted to change, but there is no info to go of off");
            return null;//Something prompted the change but there in no information to go of off.
        }

    },
    CONTROLLED_WO_RIGHTS("controlled_wo_rights", LicensesAssignation.PUBLIC, false) {

        @Override
        public States calculateNextState(String rootPid, SolrClient solrClient, ApplicationConfig config, WorkflowDoc currentDoc,StringWrapper reason) {
            return null;
        }
    },
    CONTROLLED_UNKNOWN_PUBLIC("controlled_unknown_public", LicensesAssignation.PUBLIC, false) {

        @Override
        public States calculateNextState(String rootPid, SolrClient solrClient, ApplicationConfig config, WorkflowDoc currentDoc,StringWrapper reason) {
            return null;
        }
    },
    CONTROLLED_BLOCKED("controlled_blocked", LicensesAssignation.NONE, false) {

        @Override
        public States calculateNextState(String rootPid, SolrClient solrClient, ApplicationConfig config, WorkflowDoc currentDoc,StringWrapper reason) {
            return null;
        }
    },
    PARENT_OPEN("parent_open", LicensesAssignation.NONE, true) {
        @Override
        public States calculateNextState(String rootPid, SolrClient solrClient, ApplicationConfig config, WorkflowDoc currentDoc,StringWrapper reason) {
            return checkChildren(rootPid, solrClient);
        }

        @Override
        public States transitionByCurator(String rootPid, SolrClient solrClient, WorkflowDoc currentDoc, States desiredState) {
            return null;
        }
    },
    PARENT_CLOSED("parent_closed", LicensesAssignation.NONE, true) {
        @Override
        public States calculateNextState(String rootPid, SolrClient solrClient, ApplicationConfig config, WorkflowDoc currentDoc,StringWrapper reason) {
            return checkChildren(rootPid, solrClient);
        }

        @Override
        public States transitionByCurator(String rootPid, SolrClient solrClient, WorkflowDoc currentDoc, States desiredState) {
            return null;
        }
    }
    /*,

    PARENT_CONTROLLED("parent_controlled",LicensesAssignation.NONE, true) {
        @Override
        public States calculateNextState(String rootPid,SolrClient solrClient,ApplicationConfig config, WorkflowDoc currentDoc) {
            return PARENT_CONTROLLED;
        }

        @Override
        public States transitionByCurator(String rootPid,SolrClient solrClient, WorkflowDoc currentDoc, States desiredState) {
            return null;
        }
    }*/;

    States checkChildren(String rootPid, SolrClient solrClient) {
        try {
            SolrQuery q = new SolrQuery()
                    .setQuery(Constants.TitlesCollectionFields.AppDataFields.ROOT_PID_FIELD+":\"" + rootPid + "\"")
                    .setRows(300).addFilterQuery("model:\"monographunit\" OR model:\"periodicalvolume\"");
            for (SolrDocument doc : solrClient.query(Constants.Collection.TITLES_COLLECTION, q).getResults()) {
                Object state = doc.get(Constants.TitlesCollectionFields.AppComputedFields.STATE_FIELD);
                if (TO_CONTROL.name().equals(state)) {
                    return PARENT_OPEN;
                }
            }
            //LOGGER.log(Level.INFO, "No children found for {0}", ctx.item().getRootPid());
            return PARENT_CLOSED;
        } catch (SolrServerException | IOException e) {
            LOGGER.log(Level.SEVERE, "Error checking children states for item " + rootPid, e);
            return PARENT_CLOSED;
        }
    }
    final String value;
    final boolean automaticSwitch;
    final LicensesAssignation licensesAssignation;

    States(String value, LicensesAssignation licensesAssignation, boolean automaticSwitch) {
        this.value = value;
        this.automaticSwitch = automaticSwitch;
        this.licensesAssignation = licensesAssignation;
    }

    // update parent state
    public static void updateParent(String rootPid, SolrClient solrClient) {
        try {
            SolrQuery q = new SolrQuery()
                    .setQuery(Constants.TitlesCollectionFields.AppDataFields.ROOT_PID_FIELD+":\"" + rootPid + "\"")
                    .setRows(1000);
            SolrDocumentList results = solrClient.query(Constants.Collection.TITLES_COLLECTION, q).getResults();
            //SolrDocument parent = null;
            List<SolrDocument> children = new ArrayList<>();
            for (SolrDocument doc : results) {
                if (!doc.getFieldValue(Constants.TitlesCollectionFields.AppDataFields.PID_FIELD).equals(rootPid)) {
                    children.add(doc);
                } /*else {
                    //parent = doc;
                }*/
            }
            List<String> states = children.stream().map(sdoc -> {
                Object state_obj = sdoc.getFieldValue(Constants.TitlesCollectionFields.AppComputedFields.STATE_FIELD);
                return (state_obj!=null)?state_obj.toString(): States.TO_CONTROL.name();
            }
            ).toList();
            if (states.contains(TO_CONTROL.name())) {
                SolrInputDocumentWrapper wrapper = new SolrInputDocumentWrapper(
                        Constants.TitlesCollectionFields.AppDataFields.PID_FIELD,
                        SolrInputDocumentWrapper.TypeOfChange.ATOMIC_CHANGE);

                wrapper.setField(Constants.TitlesCollectionFields.AppDataFields.PID_FIELD, rootPid);
                wrapper.setField(Constants.TitlesCollectionFields.AppComputedFields.STATE_FIELD, PARENT_OPEN.name());
                solrClient.add(Constants.Collection.TITLES_COLLECTION, wrapper.getDocument());
            } else {

                SolrInputDocumentWrapper wrapper = new SolrInputDocumentWrapper(
                        Constants.TitlesCollectionFields.AppDataFields.PID_FIELD,
                        SolrInputDocumentWrapper.TypeOfChange.ATOMIC_CHANGE);

                wrapper.setField(Constants.TitlesCollectionFields.AppDataFields.PID_FIELD, rootPid);
                wrapper.setField(Constants.TitlesCollectionFields.AppComputedFields.STATE_FIELD, PARENT_CLOSED.name());
                solrClient.add(Constants.Collection.TITLES_COLLECTION, wrapper.getDocument());
            }
        } catch (SolrServerException | IOException e) {
            LOGGER.log(Level.SEVERE, "Error checking children states for item " + rootPid, e);
        }
    }

    public abstract States calculateNextState(String rootPid, SolrClient solrClient, ApplicationConfig config, WorkflowDoc currentDoc,StringWrapper reason);

    public States transitionByCurator(String rootPid, SolrClient solrClient, WorkflowDoc currentDoc, States desiredState){
        switch (desiredState) {
            case CONTROLLED_PRIVATE, CONTROLLED_PUBLIC, CONTROLLED_UNKNOWN,CONTROLLED_BLOCKED,CONTROLLED_WO_RIGHTS,CONTROLLED_UNKNOWN_PUBLIC,TO_CONTROL -> {
                return desiredState;
            }
            default -> {
                return null;
            }
        }
    }

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
