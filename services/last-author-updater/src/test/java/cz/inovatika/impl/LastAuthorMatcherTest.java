package cz.inovatika.impl;

import cz.inovatika.Constants.TitlesCollectionFields.Labels;
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
    void usesExcelDeathYearWhenExistingTitleDeathYearDiffers() {
        JSONArray authorsKapp = new JSONArray().put(new JSONObject()
                .put("id", "capek")
                .put("full", "Capek, Karel")
                .put("family", "Capek")
                .put("given", "Karel")
                .put("year_death", 1937));

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
        assertEquals(1938, plan.updatedAuthorsKapp().getJSONObject(0).getInt("year_death"));
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
