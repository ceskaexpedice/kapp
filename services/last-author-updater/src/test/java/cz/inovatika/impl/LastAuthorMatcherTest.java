package cz.inovatika.impl;

import cz.inovatika.Constants.AuthorsCollectionFields.AuthComputedFields;
import cz.inovatika.Constants.AuthorsCollectionFields.AuthDataFields;
import cz.inovatika.Constants.TitlesCollectionFields.Labels;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LastAuthorMatcherTest {

    private final LastAuthorMatcher matcher = new LastAuthorMatcher();

    @Test
    void usesExcelDeathYearWhenExistingTitleDeathYearDiffers() {
        JSONArray authorsKapp = new JSONArray().put(new JSONObject()
                .put(AuthDataFields.ID_FIELD, "capek")
                .put(AuthDataFields.FULL_FIELD, "Capek, Karel")
                .put(AuthDataFields.FAMILY_FIELD, "Capek")
                .put(AuthDataFields.GIVEN_FIELD, "Karel")
                .put(AuthComputedFields.AUTHOR_YEAR_DEATH, 1937));

        LastAuthorMatcher.UpdatePlan plan = matcher.prepareUpdate(
                List.of(new JSONObject()
                        .put("name", "Capek, Karel")
                        .put("deathyear", 1938)
                        .toString()),
                authorsKapp,
                new JSONArray(),
                new JSONArray(),
                1937
        );

        assertTrue(plan.shouldUpdate());
        assertTrue(plan.updateAuthors());
        assertEquals(1938, plan.lastYearDeath());
        assertTrue(plan.labels().contains(Labels.DIFFERING_AUTHOR_DEATH_YEAR));
        assertEquals(1938, plan.updatedAuthorsKapp().getJSONObject(0).getInt(AuthComputedFields.AUTHOR_YEAR_DEATH));
    }

    @Test
    void clearsExistingTitleDeathYearWhenMatchedLastAuthorHasNoDeathYear() {
        JSONArray authorsKapp = new JSONArray().put(new JSONObject()
                .put(AuthDataFields.ID_FIELD, "johnovabozena1920")
                .put(AuthDataFields.FULL_FIELD, "Johnová, Božena")
                .put(AuthDataFields.FAMILY_FIELD, "Johnová")
                .put(AuthDataFields.GIVEN_FIELD, "Božena")
                .put(AuthComputedFields.AUTHOR_YEAR_BIRTH, 1920));
        JSONArray authorsKramerius = new JSONArray()
                .put(new JSONObject()
                        .put(AuthDataFields.ID_FIELD, "xx0095447")
                        .put(AuthDataFields.FULL_FIELD, "Šapošnikov, Vladimir Nikolajevič")
                        .put(AuthComputedFields.AUTHOR_YEAR_DEATH, 1968))
                .put(new JSONObject()
                        .put(AuthDataFields.ID_FIELD, "jk01060966")
                        .put(AuthDataFields.FULL_FIELD, "Kocková-Kratochvílová, Anna")
                        .put(AuthComputedFields.AUTHOR_YEAR_DEATH, 1992));

        LastAuthorMatcher.UpdatePlan plan = matcher.prepareUpdate(
                List.of(new JSONObject()
                        .put("name", "Božena Johnová")
                        .put("birthyear", 1920)
                        .toString()),
                authorsKapp,
                authorsKramerius,
                new JSONArray(),
                2018
        );

        assertTrue(plan.shouldUpdate());
        assertTrue(plan.clearLastYearDeath());
        assertNull(plan.lastYearDeath());
    }

    @Test
    void labelsUnusableExcelAuthorWithoutUpdatingAuthorFields() {
        LastAuthorMatcher.UpdatePlan plan = matcher.prepareUpdate(
                List.of("{}"),
                new JSONArray(),
                new JSONArray(),
                new JSONArray(),
                null
        );

        assertTrue(plan.shouldUpdate());
        assertFalse(plan.updateAuthors());
        assertTrue(plan.labels().contains(Labels.LAST_AUTHOR_PARSING_ISSUE));
    }
}
