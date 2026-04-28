package cz.inovatika.solr;

import org.apache.solr.common.SolrInputDocument;
import org.apache.solr.common.SolrInputField;

import java.util.HashMap;
import java.util.Map;

public record SolrInputDocumentWrapper(String idKey,SolrInputDocument document, TypeOfChange typeOfChange) {
    public SolrInputDocumentWrapper(String idKey, TypeOfChange typeOfChange){
        this(idKey,new SolrInputDocument(),typeOfChange);
    }
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
        changeField(name, value, TypeOfOperation.ATOMIC_SET);
    }

    public void removeField(String name,Object value) {
        changeField(name, value, TypeOfOperation.ATOMIC_REMOVE);
    }

    public Object getFieldValue(String name) {
        if (typeOfChange == TypeOfChange.SIMPLE) {
            return document.getFieldValue(name);
        }

        if (name.equals(idKey)) {
            return document.getFieldValue(idKey);
        }

        if (document.getFieldValue(name) instanceof Map<?, ?> m && m.keySet().stream().findFirst().orElse(null) instanceof String firstKey) {
            return m.get(firstKey);
        }

        return null;
    }

    private void changeField(String name, Object value, TypeOfOperation ofOperation) {
        switch (typeOfChange) {
            case SIMPLE -> {
                switch (ofOperation) {
                    case ATOMIC_ADD, ATOMIC_ADD_DISTINCT ->
                        document.addField(name, value);

                    case ATOMIC_SET ->
                        document.setField(name, value);

                }
            }
            case ATOMIC_CHANGE -> {
                switch (ofOperation) {
                    case ATOMIC_ADD -> {
                        if (name.equals(idKey)) {
                            document.setField(name, value);
                        } else {
                            document.addField(name, atomicOperation("add", value));
                        }
                    }
                    case ATOMIC_ADD_DISTINCT -> {
                        if (name.equals(idKey)) {
                            document.setField(name, value);
                        } else {
                            document.addField(name, atomicOperation("add-distinct", value));
                        }
                    }
                    case ATOMIC_SET -> {
                        if (name.equals(idKey)) {
                            document.setField(name, value);
                        } else {
                            document.addField(name, atomicOperation("set", value));
                        }
                    }
                    case ATOMIC_REMOVE -> {
                        if (name.equals(idKey)) {
                            throw new RuntimeException("Illegal attempt to remove field with name " + name);
                        } else {

                            document.addField(name, atomicOperation("remove", value));
                        }
                    }
                }
            }
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
