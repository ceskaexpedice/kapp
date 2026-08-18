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
import java.time.Year;
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
            RuleDecision priorityRuleDecision = priorityReleaseRuleState(currentDoc, wConfig, TO_CONTROL, null, reason);
            if (priorityRuleDecision.applicable()) {
                return priorityRuleDecision.state();
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
            WorkflowConfig wConfig = config.workflow();
            if (wConfig==null)return null;
            if (!currentDoc.model().equals(ModelFields.PERIODICALVOLUME)) {
                RuleDecision priorityRuleDecision = priorityReleaseRuleState(currentDoc, wConfig, null, NONCONTROLLED_PRIVATE, reason);
                if (priorityRuleDecision.applicable()) {
                    return priorityRuleDecision.state();
                }
            }
            return null;
        }
    },
    FOR_REVIEW("for_review", LicensesAssignation.ONSITE, false) {
        @Override
        public States calculateNextState(String rootPid, SolrClient solrClient, ApplicationConfig config, WorkflowDoc currentDoc,StringWrapper reason){
            return null;
        }
    }
    ,
    /**
     * to control , curator decision
     */
    TO_CONTROL("to_control", LicensesAssignation.ONSITE, false) {
        @Override
        public States calculateNextState(String rootPid, SolrClient solrClient, ApplicationConfig config, WorkflowDoc currentDoc,StringWrapper reason) {
            WorkflowConfig wConfig = config.workflow();
            if (wConfig==null)return null;
            if (currentDoc.model().equals(ModelFields.PERIODICALVOLUME)){
                reason.value="is periodical";
                return  wConfig.isPublicYear(currentDoc.dateIssuedYear())? NONCONTROLLED_PUBLIC :NONCONTROLLED_PRIVATE;
            }
            else{
                RuleDecision priorityRuleDecision = priorityReleaseRuleState(currentDoc, wConfig, NONCONTROLLED_PUBLIC, NONCONTROLLED_PRIVATE, reason);
                if (priorityRuleDecision.applicable()) {
                    return priorityRuleDecision.state();
                }
                if (currentDoc.last_year_death() != null) {
                    int year = currentDoc.last_year_death();
                    if (wConfig.isAuthorDeadEnoughYear(year,currentDoc.dateIssuedYear())){
                        reason.value = StateChangeReasons.SurpassedAuthorDeath(year);//"it has been OnsiteYear years from the death of last author";
                        return NONCONTROLLED_PUBLIC;
                    }
                    else {
                        reason.value = "Author death known but is not dead long enough or book was issued too recently";
                        return null;
                    }
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
                    else {
                        reason.value = StateChangeReasons.INCONTRLOWINDOWISSUED;
                        return null;
                    }
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
            RuleDecision priorityRuleDecision = priorityReleaseRuleState(currentDoc, wConfig, FOR_REVIEW, null, reason);
            if (priorityRuleDecision.applicable()) {
                return priorityRuleDecision.state();
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

    CONTROLLED_UNKNOWN("controlled_unknown", LicensesAssignation.ONSITE, false) {

        @Override
        public States calculateNextState(String rootPid, SolrClient solrClient, ApplicationConfig config, WorkflowDoc currentDoc,StringWrapper reason) {
            WorkflowConfig wConfig = config.workflow();
            if (wConfig==null)return null;
            if (currentDoc.model().equals(ModelFields.PERIODICALVOLUME)){
                return  wConfig.isPublicYear(currentDoc.dateIssuedYear())? NONCONTROLLED_PUBLIC :NONCONTROLLED_PRIVATE;
            }
            RuleDecision priorityRuleDecision = priorityReleaseRuleState(currentDoc, wConfig, FOR_REVIEW, NONCONTROLLED_PRIVATE, reason);
            if (priorityRuleDecision.applicable()) {
                return priorityRuleDecision.state();
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
    };

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

    private static RuleDecision priorityReleaseRuleState(
            WorkflowDoc currentDoc,
            WorkflowConfig workflowConfig,
            States releasedState,
            States unreleasedState,
            StringWrapper reason
    ) {
        if (!currentDoc.hasPriorityReleaseRule()) {
            return RuleDecision.notApplicable();
        }

        Integer releaseYear = currentDoc.priorityReleaseYear(workflowConfig);
        if (releaseYear == null) {
            reason.value = "Priority release rule cannot be evaluated without date issued";
            return RuleDecision.applicable(null);
        }

        if (releaseYear <= Year.now().getValue()) {
            reason.value = "Surpassed release year " + releaseYear;
            return RuleDecision.applicable(releasedState);
        }

        reason.value = "Was not yet release year " + releaseYear;
        return RuleDecision.applicable(unreleasedState);
    }

    private record RuleDecision(boolean applicable, States state) {

        static RuleDecision notApplicable() {
            return new RuleDecision(false, null);
        }

        static RuleDecision applicable(States state) {
            return new RuleDecision(true, state);
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
