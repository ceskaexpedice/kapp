package cz.inovatika;

import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class LastAuthorExcelParserTest {

    @Test
    void parsesStandardNameAndLifeYears() {
        List<JSONObject> authors = parse("Čapek, Karel, 1890-1938");

        assertEquals(1, authors.size());
        assertEquals("Karel Čapek", authors.getFirst().getString("name"));
        assertEquals(1890, authors.getFirst().getInt("birthyear"));
        assertEquals(1938, authors.getFirst().getInt("deathyear"));
    }

    @Test
    void omitsUnparsableYearSide() {
        List<JSONObject> authors = parse("Kumprecht, Karel, 1888-?");

        assertEquals(1, authors.size());
        assertEquals("Karel Kumprecht", authors.getFirst().getString("name"));
        assertEquals(1888, authors.getFirst().getInt("birthyear"));
        assertFalse(authors.getFirst().has("deathyear"));
    }

    @Test
    void parsesApproximateYearWithoutKeepingQualifier() {
        List<JSONObject> authors = parse("Dobrylovs'kyj, Mykola, 1888-asi 1971");

        assertEquals(1, authors.size());
        assertEquals("Mykola Dobrylovs'kyj", authors.getFirst().getString("name"));
        assertEquals(1888, authors.getFirst().getInt("birthyear"));
        assertEquals(1971, authors.getFirst().getInt("deathyear"));
    }

    @Test
    void parsesBirthYearFromTextNote() {
        List<JSONObject> authors = parse("Šíma, Zdeněk (nar. 1883)");

        assertEquals(1, authors.size());
        assertEquals("Zdeněk Šíma", authors.getFirst().getString("name"));
        assertEquals(1883, authors.getFirst().getInt("birthyear"));
        assertFalse(authors.getFirst().has("deathyear"));
    }

    @Test
    void parsesDeathYearFromTextNote() {
        List<JSONObject> authors = parse("Valenta-Alfa, Václav, 1887-1954-překl.;Grigorev, Sergej-zemřel 1953");

        assertEquals(2, authors.size());
        assertEquals("Václav Valenta-Alfa", authors.get(0).getString("name"));
        assertEquals(1887, authors.get(0).getInt("birthyear"));
        assertEquals(1954, authors.get(0).getInt("deathyear"));
        assertEquals("Sergej Grigorev", authors.get(1).getString("name"));
        assertFalse(authors.get(1).has("birthyear"));
        assertEquals(1953, authors.get(1).getInt("deathyear"));
    }

    @Test
    void parsesYearRangeWithoutCommaBeforeYears() {
        List<JSONObject> authors = parse("Wagner, Jan Eduard 1835-1904 (kartograf)");

        assertEquals(1, authors.size());
        assertEquals("Jan Eduard Wagner", authors.getFirst().getString("name"));
        assertEquals(1835, authors.getFirst().getInt("birthyear"));
        assertEquals(1904, authors.getFirst().getInt("deathyear"));
    }

    @Test
    void parsesMultipleCommaSeparatedAuthors() {
        List<JSONObject> authors = parse("autoři: Kolář, Václav, 1927-?, Eliáš, Václav, ?-?, Hrbek, Jaroslav, ?-? a další");

        assertEquals(3, authors.size());
        assertEquals("Václav Kolář", authors.get(0).getString("name"));
        assertEquals(1927, authors.get(0).getInt("birthyear"));
        assertFalse(authors.get(0).has("deathyear"));
        assertEquals("Václav Eliáš", authors.get(1).getString("name"));
        assertEquals("Jaroslav Hrbek", authors.get(2).getString("name"));
    }

    @Test
    void keepsSemicolonsInsideParenthesesOutOfAuthorSplitting() {
        List<JSONObject> authors = parse("Tuš, Ferdinand (sestavovatel; nar. 1884)");

        assertEquals(1, authors.size());
        assertEquals("Ferdinand Tuš", authors.getFirst().getString("name"));
        assertEquals(1884, authors.getFirst().getInt("birthyear"));
    }

    @Test
    void skipsCollectiveValues() {
        assertEquals(List.of(), LastAuthorExcelParser.parse("kolektivní"));
    }

    private static List<JSONObject> parse(String value) {
        return LastAuthorExcelParser.parse(value).stream()
                .map(JSONObject::new)
                .toList();
    }
}
