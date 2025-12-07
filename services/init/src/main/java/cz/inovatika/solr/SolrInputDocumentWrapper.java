package cz.inovatika.solr;

import org.apache.solr.common.SolrInputDocument;

import java.util.HashMap;
import java.util.Map;

public class SolrInputDocumentWrapper {

    public enum TypeOfChange {
        SIMPLE,
        ATOMIC_CHANGE,
    }


    public enum TypeOfOperation {
        ATOMIC_ADD,
        ATOMIC_REMOVE,
        ATOMIC_ADD_DISTINCT,
        ATOMIC_SET
    }

    private String idKey;
    private SolrInputDocument document;
    private TypeOfChange typeOfChange;

    public SolrInputDocumentWrapper(String idKey, SolrInputDocument document, TypeOfChange typeOfChange) {
        this.idKey = idKey;
        this.document = document;
        this.typeOfChange = typeOfChange;
    }



    private static Map<String, Object> atomicOperation(String modifier, Object value) {
        Map<String, Object> updateMap = new HashMap<>();
        updateMap.put(modifier, value);
        return updateMap;
    }

    public void addField(String name, Object value) {
        changeField(name, value, TypeOfOperation.ATOMIC_ADD_DISTINCT);
    }

    public void addField(String name, Object value, TypeOfOperation operation) {
        changeField(name, value, operation);
    }

    public void setField(String name, Object value) {
        changeField(name, value,  TypeOfOperation.ATOMIC_SET);
    }

    public void removeField(String name) {
        changeField(name, null,  TypeOfOperation.ATOMIC_REMOVE);
    }

    public Object getFieldValue(String name) {
        if (typeOfChange == TypeOfChange.SIMPLE) {
            return document.getFieldValue(name);
        } else {
            if (name.equals(idKey)) {
                return document.getFieldValue(idKey);
            } else {
                Map<String, Object>  map = (Map<String, Object>) document.getFieldValue(name);
                String firstKey =  map.keySet().stream().findFirst().orElse(null);
                if (firstKey != null) {
                    return map.get(firstKey);
                } else return null;
            }
        }
    }

    private void changeField(String name, Object value, TypeOfOperation ofOperation) {
        switch (typeOfChange) {
            case SIMPLE:
                document.addField(name, value);
            break;
            case ATOMIC_CHANGE:
                switch (ofOperation) {
                    case ATOMIC_ADD:
                        if (name.equals(idKey)) {
                            document.setField(name, value);
                        } else {
                            document.addField(name, atomicOperation("add", value));
                        }
                        break;
                    case ATOMIC_ADD_DISTINCT:
                        if (name.equals(idKey)) {
                            document.setField(name, value);
                        } else {
                            document.addField(name, atomicOperation("add-distinct", value));
                        }
                        break;
                    case ATOMIC_SET:
                        if (name.equals(idKey)) {
                            document.setField(name, value);
                        } else {
                            document.addField(name, atomicOperation("set", value));
                        }
                        break;
                    case ATOMIC_REMOVE:
                        if (name.equals(idKey)) {
                            document.setField(name, value);
                        } else {
                            document.addField(name, atomicOperation("remove", value));
                        }
                        break;
                }
            break;

        }
    }


    public SolrInputDocument getDocument() {
        return document;
    }

    public String getIdKey() {
        return idKey;
    }

    public TypeOfChange getTypeOfChange() {
        return typeOfChange;
    }
}
