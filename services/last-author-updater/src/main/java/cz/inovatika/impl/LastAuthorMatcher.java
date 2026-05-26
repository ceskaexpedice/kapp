package cz.inovatika.impl;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.Normalizer;
import java.time.Year;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

public class LastAuthorMatcher {
    private static final Pattern DIACRITICS = Pattern.compile("\\p{InCombiningDiacriticalMarks}+");
    private static final Pattern DATE_QUALIFIER_WITHOUT_DOT = Pattern.compile("(?i)\\b(?:ca|asi)\\s+");
    private static final Pattern YEAR_RANGE = Pattern.compile("\\b\\d{3,4}\\s*\\p{Pd}\\s*\\d{0,4}");
    private static final Pattern YEAR = Pattern.compile("\\b\\d{3,4}\\b");
    private static final Pattern NON_LETTER = Pattern.compile("[^\\p{L}]+");
    private static final double NAME_SIMILARITY_THRESHOLD = 0.82;
    private static final double AMBIGUOUS_MATCH_DELTA = 0.03;

    public UpdatePlan prepareUpdate(
            List<String> lastAuthorsExcel,
            JSONArray authorsKapp,
            JSONArray authorsKramerius,
            Integer currentLastYearDeath
    ) {
        List<String> normalizedExcelAuthors = lastAuthorsExcel.stream()
                .map(LastAuthorMatcher::normalizeExcelAuthorName)
                .filter(value -> !value.isBlank())
                .distinct()
                .toList();

        if (normalizedExcelAuthors.isEmpty()) {
            return UpdatePlan.noMatch("No usable last_author_excel value");
        }

        List<AuthorCandidate> candidates = mergeCandidates(authorsKapp, authorsKramerius);

        if (candidates.isEmpty()) {
            return UpdatePlan.noMatch("No authors found in authors_kapp or authors_kramerius");
        }

        if (candidates.stream().anyMatch(candidate -> candidate.yearDeath() == null)) {
            return UpdatePlan.noMatch("At least one author has no usable year_death");
        }

        List<Match> matches = new ArrayList<>();
        for (String excelAuthor : normalizedExcelAuthors) {
            Optional<Match> match = bestMatch(excelAuthor, candidates);
            if (match.isEmpty()) {
                return UpdatePlan.noMatch("No author matched last_author_excel: " + excelAuthor);
            }
            matches.add(match.get());
        }

        AuthorCandidate selectedCandidate = latestDeathCandidate(candidates).orElse(null);
        if (selectedCandidate == null) {
            return UpdatePlan.noMatch("No unique latest author death year");
        }

        Match selectedMatch = matches.stream()
                .filter(match -> sameAuthor(match.candidate().author(), selectedCandidate.author()))
                .findFirst()
                .orElse(new Match("", selectedCandidate, 1.0, "latest year_death among all authors"));

        JSONArray updatedAuthorsKapp = buildUpdatedAuthorsKapp(authorsKapp, selectedCandidate);
        TitleAuthorFields titleAuthorFields = buildTitleAuthorFields(
                updatedAuthorsKapp,
                authorsKramerius,
                selectedCandidate.author().optString("old_id", null)
        );
        return new UpdatePlan(
                true,
                true,
                selectedMatch.matchedAuthor(),
                selectedCandidate.author(),
                updatedAuthorsKapp,
                titleAuthorFields,
                selectedCandidate.yearDeath(),
                selectedMatch.reason()
        );
    }

    private static Optional<AuthorCandidate> latestDeathCandidate(List<AuthorCandidate> candidates) {
        int latestYearDeath = candidates.stream()
                .map(AuthorCandidate::yearDeath)
                .max(Integer::compareTo)
                .orElseThrow();
        List<AuthorCandidate> latestCandidates = candidates.stream()
                .filter(candidate -> latestYearDeath == candidate.yearDeath())
                .toList();
        if (latestCandidates.size() != 1) {
            return Optional.empty();
        }
        return Optional.of(latestCandidates.getFirst());
    }

    private static TitleAuthorFields buildTitleAuthorFields(JSONArray authorsKapp, JSONArray authorsKramerius, String selectedOldId) {
        List<String> ids = new ArrayList<>();
        List<String> nameIdentifiers = new ArrayList<>();
        List<String> names = new ArrayList<>();

        for (int i = 0; i < authorsKapp.length(); i++) {
            JSONObject author = authorsKapp.getJSONObject(i);
            addDistinct(ids, author.optString("id", null));
            addDistinct(nameIdentifiers, author.optString("nameIdentifier", null));
            addDistinct(names, author.optString("full", null));
        }

        Set<String> kappIds = new HashSet<>(ids);
        Set<String> kappNameIdentifiers = new HashSet<>(nameIdentifiers);
        Set<String> kappNames = new HashSet<>(names);
        for (int i = 0; i < authorsKramerius.length(); i++) {
            JSONObject author = authorsKramerius.getJSONObject(i);
            String id = author.optString("id", null);
            String nameIdentifier = author.optString("nameIdentifier", null);
            String full = author.optString("full", null);

            if (isPresent(id) && !kappIds.contains(id) && !id.equals(selectedOldId)) {
                addDistinct(ids, id);
            }
            if (isPresent(nameIdentifier) && !kappNameIdentifiers.contains(nameIdentifier)) {
                addDistinct(nameIdentifiers, nameIdentifier);
            }
            if (isPresent(full) && !kappNames.contains(full)) {
                addDistinct(names, full);
            }
        }

        return new TitleAuthorFields(List.copyOf(ids), List.copyOf(nameIdentifiers), List.copyOf(names));
    }

    private static void addDistinct(List<String> values, String value) {
        if (isPresent(value) && !values.contains(value)) {
            values.add(value);
        }
    }

    private static boolean isPresent(String value) {
        return value != null && !value.isBlank() && !"null".equals(value.trim());
    }

    private static Optional<Match> bestMatch(String excelAuthor, List<AuthorCandidate> candidates) {
        List<Match> matches = candidates.stream()
                .map(candidate -> match(excelAuthor, candidate))
                .filter(match -> match.score() >= NAME_SIMILARITY_THRESHOLD)
                .sorted(Comparator.comparingDouble(Match::score).reversed())
                .toList();

        if (matches.isEmpty()) {
            return Optional.empty();
        }

        Match best = matches.getFirst();
        if (matches.size() > 1 && best.score() - matches.get(1).score() <= AMBIGUOUS_MATCH_DELTA) {
            return Optional.empty();
        }

        return Optional.of(best);
    }

    private static Match match(String excelAuthor, AuthorCandidate candidate) {
        NameParts excel = NameParts.of(excelAuthor);
        double bestScore = 0;
        String reason = "below threshold";

        for (String candidateName : candidate.namesForMatching()) {
            NameParts current = NameParts.of(candidateName);
            if (current.normalized().isBlank()) {
                continue;
            }

            if (excel.compact().equals(current.compact())) {
                return new Match(excelAuthor, candidate, 1.0, "exact compact name");
            }
            if (excel.sortedCompact().equals(current.sortedCompact())) {
                return new Match(excelAuthor, candidate, 0.98, "same name tokens");
            }

            if (excel.tokens().size() >= 2 && current.tokens().containsAll(excel.tokens())) {
                bestScore = Math.max(bestScore, 0.94);
                reason = "excel tokens are contained in author name";
            }

            if (excel.tokens().size() == 1 && current.tokens().containsAll(excel.tokens())) {
                bestScore = Math.max(bestScore, 0.88);
                reason = "single excel token found in author name";
            }

            double compactSimilarity = similarity(excel.compact(), current.compact());
            if (compactSimilarity > bestScore) {
                bestScore = compactSimilarity;
                reason = "compact Levenshtein similarity";
            }

            double sortedSimilarity = similarity(excel.sortedCompact(), current.sortedCompact());
            if (sortedSimilarity > bestScore) {
                bestScore = sortedSimilarity;
                reason = "token-sorted Levenshtein similarity";
            }
        }

        return new Match(excelAuthor, candidate, bestScore, reason);
    }

    private static JSONArray buildUpdatedAuthorsKapp(JSONArray authorsKapp, AuthorCandidate selected) {
        JSONArray updated = copyArray(authorsKapp);
        for (int i = 0; i < updated.length(); i++) {
            updated.getJSONObject(i).put("isLast", false);
        }

        if (selected.kappIndex() >= 0) {
            updated.getJSONObject(selected.kappIndex()).put("isLast", true);
            return updated;
        }

        JSONObject author = copyObject(selected.author());
        author.put("isLast", true);
        int duplicateIndex = findSameAuthor(updated, author);
        if (duplicateIndex >= 0) {
            updated.put(duplicateIndex, author);
        } else {
            updated.put(author);
        }
        return updated;
    }

    private static List<AuthorCandidate> mergeCandidates(JSONArray authorsKapp, JSONArray authorsKramerius) {
        List<AuthorCandidate> candidates = new ArrayList<>();
        for (int i = 0; i < authorsKapp.length(); i++) {
            candidates.add(new AuthorCandidate(copyObject(authorsKapp.getJSONObject(i)), i));
        }

        for (int i = 0; i < authorsKramerius.length(); i++) {
            JSONObject author = authorsKramerius.getJSONObject(i);
            if (candidates.stream().noneMatch(candidate -> sameAuthor(candidate.author(), author))) {
                candidates.add(new AuthorCandidate(copyObject(author), -1));
            }
        }

        return candidates;
    }

    private static int findSameAuthor(JSONArray authors, JSONObject author) {
        for (int i = 0; i < authors.length(); i++) {
            if (sameAuthor(authors.getJSONObject(i), author)) {
                return i;
            }
        }
        return -1;
    }

    private static boolean sameAuthor(JSONObject first, JSONObject second) {
        Set<String> firstKeys = authorIdentityKeys(first);
        Set<String> secondKeys = authorIdentityKeys(second);
        firstKeys.retainAll(secondKeys);
        return !firstKeys.isEmpty();
    }

    private static Set<String> authorIdentityKeys(JSONObject author) {
        Set<String> keys = new HashSet<>();
        addKey(keys, author.optString("id", null));
        addKey(keys, author.optString("old_id", null));
        addKey(keys, author.optString("nameIdentifier", null));
        return keys;
    }

    private static void addKey(Set<String> keys, String value) {
        if (value != null && !value.isBlank()) {
            keys.add(value.trim());
        }
    }

    private static String normalizeExcelAuthorName(String value) {
        return normalizeName(stripDateParts(value));
    }

    private static String stripDateParts(String value) {
        if (value == null) {
            return "";
        }

        String result = value;
        result = result.replace("ca.", " ");
        result = result.replace("Ca.", " ");
        result = result.replace("CA.", " ");
        result = result.replace("[", " ");
        result = result.replace("]", " ");
        result = result.replace("(", " ");
        result = result.replace(")", " ");
        result = result.replace("?", " ");

        result = DATE_QUALIFIER_WITHOUT_DOT.matcher(result).replaceAll(" ");
        result = YEAR_RANGE.matcher(result).replaceAll(" ");
        result = YEAR.matcher(result).replaceAll(" ");
        return result;
    }

    private static String normalizeName(String value) {
        String normalized = Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFD);
        normalized = DIACRITICS.matcher(normalized).replaceAll("");
        normalized = NON_LETTER.matcher(normalized.toLowerCase(Locale.ROOT)).replaceAll(" ");
        return normalized.trim().replaceAll("\\s+", " ");
    }

    private static double similarity(String first, String second) {
        if (first.isBlank() || second.isBlank()) {
            return 0;
        }
        if (first.equals(second)) {
            return 1;
        }
        int maxLength = Math.max(first.length(), second.length());
        if (maxLength == 0) {
            return 1;
        }
        return 1 - ((double) levenshteinDistance(first, second) / maxLength);
    }

    private static int levenshteinDistance(String first, String second) {
        int[] previous = new int[second.length() + 1];
        int[] current = new int[second.length() + 1];

        for (int j = 0; j <= second.length(); j++) {
            previous[j] = j;
        }

        for (int i = 1; i <= first.length(); i++) {
            current[0] = i;
            for (int j = 1; j <= second.length(); j++) {
                int cost = first.charAt(i - 1) == second.charAt(j - 1) ? 0 : 1;
                current[j] = Math.min(
                        Math.min(current[j - 1] + 1, previous[j] + 1),
                        previous[j - 1] + cost
                );
            }
            int[] temp = previous;
            previous = current;
            current = temp;
        }

        return previous[second.length()];
    }

    private static JSONArray copyArray(JSONArray array) {
        return new JSONArray(array.toString());
    }
    private static JSONObject copyObject(JSONObject object) {
        return new JSONObject(object.toString());
    }

    public static List<String> asStrings(Collection<Object> values) {
        if (values == null) {
            return List.of();
        }
        return values.stream()
                .filter(value -> value != null && !value.toString().isBlank())
                .map(Object::toString)
                .toList();
    }

    public static Integer asInteger(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.valueOf(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static JSONArray parseAuthorsKapp(Object value) {
        if (value == null || value.toString().isBlank()) {
            return new JSONArray();
        }
        if (value instanceof Collection<?> collection) {
            return parseJsonValues(collection);
        }
        return parseJsonValues(List.of(value));
    }

    public static JSONArray parseAuthorsKramerius(Collection<Object> values) {
        if (values == null || values.isEmpty()) {
            return new JSONArray();
        }
        return parseJsonValues(values);
    }

    private static JSONArray parseJsonValues(Collection<?> values) {
        JSONArray result = new JSONArray();
        for (Object value : values) {
            if (value == null || value.toString().isBlank()) {
                continue;
            }
            if (value instanceof JSONObject object) {
                result.put(copyObject(object));
                continue;
            }
            if (value instanceof JSONArray array) {
                appendArray(result, array);
                continue;
            }

            String text = value.toString().trim();
            try {
                if (text.startsWith("[")) {
                    appendArray(result, new JSONArray(text));
                } else if (text.startsWith("{")) {
                    result.put(new JSONObject(text));
                }
            } catch (JSONException e) {
                throw new IllegalArgumentException("Failed to parse author JSON: " + text, e);
            }
        }
        return result;
    }

    private static void appendArray(JSONArray target, JSONArray source) {
        for (int i = 0; i < source.length(); i++) {
            target.put(source.get(i));
        }
    }

    private record NameParts(String normalized, Set<String> tokens, String compact, String sortedCompact) {
        static NameParts of(String value) {
            String normalized = normalizeName(value);
            Set<String> tokens = new LinkedHashSet<>();
            if (!normalized.isBlank()) {
                tokens.addAll(List.of(normalized.split(" ")));
            }
            String compact = String.join("", tokens);
            String sortedCompact = tokens.stream().sorted().reduce("", String::concat);
            return new NameParts(normalized, tokens, compact, sortedCompact);
        }
    }

    private record AuthorCandidate(JSONObject author, int kappIndex) {
        Integer yearDeath() {
            Integer yearDeath = asInteger(author.opt("year_death"));
            if (yearDeath == null || yearDeath <= 0 || yearDeath >= 9999 || yearDeath > Year.now().getValue()) {
                return null;
            }
            return yearDeath;
        }

        List<String> namesForMatching() {
            List<String> names = new ArrayList<>();
            addName(names, author.optString("full", null));
            addName(names, joinNameParts(author.optString("given", null), author.optString("family", null)));
            addName(names, joinNameParts(author.optString("family", null), author.optString("given", null)));
            addName(names, author.optString("family", null));
            return names.stream().distinct().toList();
        }

        private static String joinNameParts(String first, String second) {
            if (first == null || first.isBlank()) {
                return second;
            }
            if (second == null || second.isBlank()) {
                return first;
            }
            return first + " " + second;
        }

        private static void addName(List<String> names, String value) {
            if (value != null && !value.isBlank() && !"null".equals(value.trim())) {
                names.add(value);
            }
        }
    }

    private record Match(String excelAuthor, AuthorCandidate candidate, double score, String reason) {
        String matchedAuthor() {
            return excelAuthor.isBlank() ? candidate.author().optString("full") : excelAuthor;
        }
    }

    public record UpdatePlan(
            boolean matched,
            boolean shouldUpdate,
            String matchedExcelAuthor,
            JSONObject matchedAuthor,
            JSONArray updatedAuthorsKapp,
            TitleAuthorFields titleAuthorFields,
            int lastYearDeath,
            String reason
    ) {
        static UpdatePlan noMatch(String reason) {
            return new UpdatePlan(false, false, "", new JSONObject(), new JSONArray(), TitleAuthorFields.empty(), 0, reason);
        }
    }

    public record TitleAuthorFields(List<String> ids, List<String> nameIdentifiers, List<String> names) {
        static TitleAuthorFields empty() {
            return new TitleAuthorFields(List.of(), List.of(), List.of());
        }
    }
}
