package cz.inovatika.workflow;

import cz.inovatika.Constants;
import cz.inovatika.conf.ApplicationConfig;
import cz.inovatika.conf.WorkflowConfig;
import cz.inovatika.model.HarvesterContext;

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
        public States calculateNextState(String rootPid, SolrClient solrClient, ApplicationConfig config, WorkflowDoc currentDoc) {
            if (!currentDoc.isHierarchical()) {
                WorkflowConfig wConfig = config.workflow();
                if (wConfig != null && currentDoc.dateRangeEndYear() != null) {
                    int year = currentDoc.dateRangeEndYear();
                    if (wConfig.isPublicYear(year)) {
                        return NONCONTROLLED_PUBLIC;
                    }
                    if (wConfig.isOnsiteYear(year)) {
                        return NONCONTROLLED_PRIVATE;
                    }
                }
                if (currentDoc.model().equals("monograph") || currentDoc.model().equals("monographunit")) {
                    return TO_CONTROL;
                } else if (currentDoc.model().equals("periodicalvolume")) {
                    return NONCONTROLLED_PRIVATE;
                } else {
                    return NONCONTROLLED_PRIVATE;
                }
            } else {
                return IMPORTED;
                //return checkChildren(ctx);
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
        public States calculateNextState(String rootPid, SolrClient solrClient, ApplicationConfig config, WorkflowDoc currentDoc) {
            return null;
        }

        @Override
        public States transitionByCurator(String rootPid, SolrClient solrClient, WorkflowDoc currentDoc, States desiredState) {
            return NONCONTROLLED_PRIVATE;
        }
    },
    /**
     * controlled < 110 let
     */
    NONCONTROLLED_PUBLIC("noncontrolled_public", LicensesAssignation.PUBLIC, false) {
        @Override
        public States calculateNextState(String rootPid, SolrClient solrClient, ApplicationConfig config, WorkflowDoc currentDoc) {
            return null;
        }

        @Override
        public States transitionByCurator(String rootPid, SolrClient solrClient, WorkflowDoc currentDoc, States desiredState) {
            return NONCONTROLLED_PUBLIC;
        }
    },
    /**
     * to control , curator decision
     */
    TO_CONTROL("to_control", LicensesAssignation.NONE, false) {
        @Override
        public States calculateNextState(String rootPid, SolrClient solrClient, ApplicationConfig config, WorkflowDoc currentDoc) {
            return null;
        }

        @Override
        public States transitionByCurator(String rootPid, SolrClient solrClient, WorkflowDoc currentDoc, States desiredState) {
            switch (desiredState) {
                case CONTROLLED_PRIVATE, CONTROLLED_PUBLIC, CONTROLLED_UNKNOWN -> {
                    return desiredState;
                }
                default -> {
                    return null;
                }
            }
        }
    },
    CONTROLLED_PUBLIC("controlled_public", LicensesAssignation.PUBLIC, false) {
        @Override
        public States calculateNextState(String rootPid, SolrClient solrClient, ApplicationConfig config, WorkflowDoc currentDoc) {
            return null;
        }

        @Override
        public States transitionByCurator(String rootPid, SolrClient solrClient, WorkflowDoc currentDoc, States desiredState) {
            return CONTROLLED_PUBLIC;
        }
    },
    CONTROLLED_PRIVATE("controlled_private", LicensesAssignation.ONSITE, false) {
        @Override
        public States transitionByCurator(String rootPid, SolrClient solrClient, WorkflowDoc currentDoc, States desiredState) {
            return CONTROLLED_PRIVATE;
        }

        @Override
        public States calculateNextState(String rootPid, SolrClient solrClient, ApplicationConfig config, WorkflowDoc currentDoc) {
            return null;
        }
    },

    CONTROLLED_UNKNOWN("controlled_unknown", LicensesAssignation.NONE, false) {
        @Override
        public States transitionByCurator(String rootPid, SolrClient solrClient, WorkflowDoc currentDoc, States desiredState) {
            return CONTROLLED_UNKNOWN;
        }

        @Override
        public States calculateNextState(String rootPid, SolrClient solrClient, ApplicationConfig config, WorkflowDoc currentDoc) {
            return null;
        }

    },
    CONTROLLED_WO_RIGHTS("controlled_unknown", LicensesAssignation.PUBLIC, false) {
        @Override
        public States transitionByCurator(String rootPid, SolrClient solrClient, WorkflowDoc currentDoc, States desiredState) {
            return CONTROLLED_WO_RIGHTS;
        }

        @Override
        public States calculateNextState(String rootPid, SolrClient solrClient, ApplicationConfig config, WorkflowDoc currentDoc) {
            return null;
        }
    },
    CONTROLLED_UNKNOWN_PUBLIC("controlled_unknown", LicensesAssignation.PUBLIC, false) {
        @Override
        public States transitionByCurator(String rootPid, SolrClient solrClient, WorkflowDoc currentDoc, States desiredState) {
            return CONTROLLED_UNKNOWN_PUBLIC;
        }

        @Override
        public States calculateNextState(String rootPid, SolrClient solrClient, ApplicationConfig config, WorkflowDoc currentDoc) {
            return null;
        }
    },
    CONTROLLED_BLOCKED("controlled_unknown", LicensesAssignation.NONE, false) {
        @Override
        public States transitionByCurator(String rootPid, SolrClient solrClient, WorkflowDoc currentDoc, States desiredState) {
            return CONTROLLED_BLOCKED;
        }

        @Override
        public States calculateNextState(String rootPid, SolrClient solrClient, ApplicationConfig config, WorkflowDoc currentDoc) {
            return null;
        }
    },
    PARENT_OPEN("parent_open", LicensesAssignation.NONE, true) {
        @Override
        public States calculateNextState(String rootPid, SolrClient solrClient, ApplicationConfig config, WorkflowDoc currentDoc) {
            return checkChildren(rootPid, solrClient);
        }

        @Override
        public States transitionByCurator(String rootPid, SolrClient solrClient, WorkflowDoc currentDoc, States desiredState) {
            return null;
        }
    },
    PARENT_CLOSED("parent_closed", LicensesAssignation.NONE, true) {
        @Override
        public States calculateNextState(String rootPid, SolrClient solrClient, ApplicationConfig config, WorkflowDoc currentDoc) {
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
                wrapper.addField(Constants.TitlesCollectionFields.AppComputedFields.STATE_FIELD, PARENT_OPEN.name());
                solrClient.add(Constants.Collection.TITLES_COLLECTION, wrapper.getDocument());
            } else {

                SolrInputDocumentWrapper wrapper = new SolrInputDocumentWrapper(
                        Constants.TitlesCollectionFields.AppDataFields.PID_FIELD,
                        SolrInputDocumentWrapper.TypeOfChange.ATOMIC_CHANGE);

                wrapper.setField(Constants.TitlesCollectionFields.AppDataFields.PID_FIELD, rootPid);
                wrapper.addField(Constants.TitlesCollectionFields.AppComputedFields.STATE_FIELD, PARENT_CLOSED.name());
                solrClient.add(Constants.Collection.TITLES_COLLECTION, wrapper.getDocument());
            }
        } catch (SolrServerException | IOException e) {
            LOGGER.log(Level.SEVERE, "Error checking children states for item " + rootPid, e);
        }
    }

    public abstract States calculateNextState(String rootPid, SolrClient solrClient, ApplicationConfig config, WorkflowDoc currentDoc);

    public abstract States transitionByCurator(String rootPid, SolrClient solrClient, WorkflowDoc currentDoc, States desiredState);

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
