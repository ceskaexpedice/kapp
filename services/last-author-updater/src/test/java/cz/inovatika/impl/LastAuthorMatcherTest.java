package cz.inovatika.impl;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LastAuthorMatcherTest {
    private final LastAuthorMatcher matcher = new LastAuthorMatcher();

    @Test
    void matchesKrameriusAuthorIgnoringExcelYearsAndDiacritics() {
        JSONArray kramerius = new JSONArray()
                .put(author("capek18901938", "Čapek, Karel", "Čapek", "Karel", 1938).put("nameIdentifier", "jk01021023"));

        LastAuthorMatcher.UpdatePlan update = matcher.prepareUpdate(
                List.of("Karel Capek 1890-1938"),
                new JSONArray(),
                kramerius,
                null
        );

        assertTrue(update.shouldUpdate());
        assertEquals(1938, update.lastYearDeath());
        assertEquals(1, update.updatedAuthorsKapp().length());
        assertTrue(update.updatedAuthorsKapp().getJSONObject(0).getBoolean("isLast"));
        assertEquals(List.of("capek18901938"), update.titleAuthorFields().ids());
        assertEquals(List.of("jk01021023"), update.titleAuthorFields().nameIdentifiers());
        assertEquals(List.of("Čapek, Karel"), update.titleAuthorFields().names());
    }

    @Test
    void matchesSwappedNameOrder() {
        JSONArray kramerius = new JSONArray()
                .put(author("capek18901938", "Čapek, Karel", "Čapek", "Karel", 1938));

        LastAuthorMatcher.UpdatePlan update = matcher.prepareUpdate(
                List.of("Capek Karel 1890-1938"),
                new JSONArray(),
                kramerius,
                null
        );

        assertTrue(update.shouldUpdate());
        assertEquals("Čapek, Karel", update.matchedAuthor().getString("full"));
    }

    @Test
    void matchesExcelAuthorWithReadableDateCleanupFormats() {
        JSONArray kramerius = new JSONArray()
                .put(author("capek18901938", "Čapek, Karel", "Čapek", "Karel", 1938));

        for (String excelAuthor : List.of(
                "Karel Capek ca. 1890-1938",
                "Karel Capek asi 1890-1938",
                "Karel Capek [1890]-[1938]",
                "Karel Capek (1890-1938)",
                "Karel Capek 1890-?"
        )) {
            LastAuthorMatcher.UpdatePlan update = matcher.prepareUpdate(
                    List.of(excelAuthor),
                    new JSONArray(),
                    kramerius,
                    null
            );

            assertTrue(update.shouldUpdate(), excelAuthor);
            assertEquals("Čapek, Karel", update.matchedAuthor().getString("full"), excelAuthor);
        }
    }

    @Test
    void matchesUniqueSurnameOnlyAuthor() {
        JSONArray kramerius = new JSONArray()
                .put(author("capek18901938", "Čapek, Karel", "Čapek", "Karel", 1938));

        LastAuthorMatcher.UpdatePlan update = matcher.prepareUpdate(
                List.of("Capek 1890-1938"),
                new JSONArray(),
                kramerius,
                null
        );

        assertTrue(update.shouldUpdate());
        assertEquals("Čapek, Karel", update.matchedAuthor().getString("full"));
    }

    @Test
    void skipsAmbiguousSurnameOnlyAuthor() {
        JSONArray kramerius = new JSONArray()
                .put(author("novakjan", "Novák, Jan", "Novák", "Jan", 1938))
                .put(author("novakjosef", "Novák, Josef", "Novák", "Josef", 1940));

        LastAuthorMatcher.UpdatePlan update = matcher.prepareUpdate(
                List.of("Novak 1900-1940"),
                new JSONArray(),
                kramerius,
                null
        );

        assertFalse(update.shouldUpdate());
    }

    @Test
    void prefersKappAuthorOverKrameriusBackup() {
        JSONArray authorsKapp = new JSONArray()
                .put(author("capek18901938", "Karel Čapek", "Čapek", "Karel", 1940));
        JSONArray kramerius = new JSONArray()
                .put(author("capek18901938", "Čapek, Karel", "Čapek", "Karel", 1938));

        LastAuthorMatcher.UpdatePlan update = matcher.prepareUpdate(
                List.of("Karel Capek 1890-1938"),
                authorsKapp,
                kramerius,
                null
        );

        assertTrue(update.shouldUpdate());
        assertEquals(1940, update.lastYearDeath());
        assertEquals(1, update.updatedAuthorsKapp().length());
        assertEquals("Karel Čapek", update.updatedAuthorsKapp().getJSONObject(0).getString("full"));
    }

    @Test
    void skipsMatchedAuthorWithoutDeathYear() {
        JSONObject withoutDeath = author("capek18901938", "Čapek, Karel", "Čapek", "Karel", 1938);
        withoutDeath.remove("year_death");

        LastAuthorMatcher.UpdatePlan update = matcher.prepareUpdate(
                List.of("Karel Capek 1890-1938"),
                new JSONArray(),
                new JSONArray().put(withoutDeath),
                null
        );

        assertFalse(update.shouldUpdate());
    }

    @Test
    void skipsWhenAnyMergedAuthorHasNoDeathYear() {
        JSONObject unknownAuthor = author("unknown", "Neznámý, Autor", "Neznámý", "Autor", 1900);
        unknownAuthor.remove("year_death");

        LastAuthorMatcher.UpdatePlan update = matcher.prepareUpdate(
                List.of("Karel Capek 1890-1938"),
                new JSONArray(),
                new JSONArray()
                        .put(author("capek18901938", "Čapek, Karel", "Čapek", "Karel", 1938))
                        .put(unknownAuthor),
                null
        );

        assertFalse(update.shouldUpdate());
    }

    @Test
    void selectsAuthorWithLatestDeathWhenAllAuthorsHaveDeathYear() {
        LastAuthorMatcher.UpdatePlan update = matcher.prepareUpdate(
                List.of("Karel Capek 1890-1938", "Jaroslav Durych 1886-1962"),
                new JSONArray(),
                new JSONArray()
                        .put(author("capek18901938", "Čapek, Karel", "Čapek", "Karel", 1938))
                        .put(author("durych18861962", "Durych, Jaroslav", "Durych", "Jaroslav", 1962)),
                null
        );

        assertTrue(update.shouldUpdate());
        assertEquals(1962, update.lastYearDeath());
        assertEquals("Durych, Jaroslav", update.matchedAuthor().getString("full"));
        assertTrue(update.updatedAuthorsKapp().getJSONObject(0).getBoolean("isLast"));
    }

    @Test
    void skipsWhenLatestDeathYearIsNotUnique() {
        LastAuthorMatcher.UpdatePlan update = matcher.prepareUpdate(
                List.of("Jan Novak 1900-1938", "Josef Novak 1901-1938"),
                new JSONArray(),
                new JSONArray()
                        .put(author("novakjan", "Novák, Jan", "Novák", "Jan", 1938))
                        .put(author("novakjosef", "Novák, Josef", "Novák", "Josef", 1938)),
                null
        );

        assertFalse(update.shouldUpdate());
    }

    @Test
    void stillUpdatesSecondaryFieldsWhenLastAuthorIsAlreadyStored() {
        JSONArray authorsKapp = new JSONArray()
                .put(author("capek18901938", "Čapek, Karel", "Čapek", "Karel", 1938).put("isLast", true));

        LastAuthorMatcher.UpdatePlan update = matcher.prepareUpdate(
                List.of("Karel Capek 1890-1938"),
                authorsKapp,
                new JSONArray(),
                1938
        );

        assertTrue(update.matched());
        assertTrue(update.shouldUpdate());
    }

    @Test
    void derivesWebFlowTitleFieldsFromKappThenKrameriusBackup() {
        JSONArray authorsKapp = new JSONArray()
                .put(author("capek18901938", "Čapek, Karel", "Čapek", "Karel", 1938)
                        .put("nameIdentifier", "jk01021023")
                        .put("isLast", true));
        JSONArray kramerius = new JSONArray()
                .put(author("capek18901938", "Čapek, Karel", "Čapek", "Karel", 1938)
                        .put("nameIdentifier", "jk01021023"))
                .put(author("nemcova18201862", "Němcová, Božena", "Němcová", "Božena", 1862)
                        .put("nameIdentifier", "jk01083016"));

        LastAuthorMatcher.UpdatePlan update = matcher.prepareUpdate(
                List.of("Karel Capek 1890-1938"),
                authorsKapp,
                kramerius,
                1938
        );

        assertEquals(List.of("capek18901938", "nemcova18201862"), update.titleAuthorFields().ids());
        assertEquals(List.of("jk01021023", "jk01083016"), update.titleAuthorFields().nameIdentifiers());
        assertEquals(List.of("Čapek, Karel", "Němcová, Božena"), update.titleAuthorFields().names());
    }

    private static JSONObject author(String id, String full, String family, String given, int yearDeath) {
        return new JSONObject()
                .put("id", id)
                .put("full", full)
                .put("family", family)
                .put("given", given)
                .put("year_death", yearDeath)
                .put("isLast", false);
    }
}
