package cz.inovatika;

import java.util.List;

import org.json.JSONObject;

public class AutorData {

    String full;
    String family;
    String given;
    String date;
    String termsOfAddress;
    String nameIdentifier;
    List<String> role;
    boolean isLast;

    public AutorData(String full, String family, String given, String date, String termsOfAddress, String nameIdentifier, List<String> role) {
        this.full = full;
        this.family = family;
        this.given = given;
        this.date = date;
        this.termsOfAddress = termsOfAddress;
        this.nameIdentifier = nameIdentifier;
        this.role = role;
    }

    public String toJsonString() {
        JSONObject json = new JSONObject();
        json.put("full", full);
        json.put("family", family);
        json.put("given", given);
        json.put("date", date);
        json.put("role", role);
        json.put("termsOfAddress", termsOfAddress);
        json.put("nameIdentifier", nameIdentifier);
        json.put("isLast", isLast);
        return json.toString();
    }
}