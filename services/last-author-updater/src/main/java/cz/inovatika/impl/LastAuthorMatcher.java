package cz.inovatika.impl;

import cz.inovatika.Constants;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.Normalizer;
import java.time.Year;
import java.util.*;
import java.util.regex.Pattern;

//TODO: ADD labels for error states
public class LastAuthorMatcher {

    private static final Pattern DIACRITICS = Pattern.compile("\\p{InCombiningDiacriticalMarks}+");
    private static final Pattern DATE_QUALIFIER_WITHOUT_DOT = Pattern.compile("(?i)\\b(?:ca|asi)\\s+");
    private static final Pattern YEAR_RANGE = Pattern.compile("\\b\\d{3,4}\\s*\\p{Pd}\\s*\\d{0,4}");
    private static final Pattern YEAR_RANGE_VALUE = Pattern.compile("\\b(\\d{3,4})\\s*\\p{Pd}\\s*(?:(\\d{3,4})|[?x])?");
    private static final Pattern YEAR = Pattern.compile("\\b\\d{3,4}\\b");
    private static final Pattern NON_LETTER = Pattern.compile("[^\\p{L}]+");
    private static final double NAME_SIMILARITY_THRESHOLD = 0.82;
    private static final double AMBIGUOUS_MATCH_DELTA = 0.03;

    public UpdatePlan prepareUpdate(
            List<String> lastAuthorsExcel,
            JSONArray authorsKapp,
            JSONArray authorsKramerius,
            JSONArray authorsCollection
    ) {
        List<ExcelAuthor> excelAuthors = parseExcelAuthors(lastAuthorsExcel);

        if (excelAuthors.isEmpty()) {
            return UpdatePlan.noMatch("No usable last_author_excel value");
        }

        List<AuthorCandidate> candidates = mergeCandidates(authorsKapp, authorsKramerius, authorsCollection);
        List<Match> matches = new ArrayList<>();
        List<AuthorCandidate> createdCandidates = new ArrayList<>();
        List<AuthorCandidate> additionalKappCandidates = new ArrayList<>();
        for (ExcelAuthor excelAuthor : excelAuthors) {
            Optional<Match> match = bestMatch(excelAuthor.normalizedName(), candidates);
            if (match.isPresent()) {
                if (!match.get().candidate().titleAuthor()) {
                    addCandidateDistinct(additionalKappCandidates, match.get().candidate());
                }
                matches.add(match.get());
                continue;
            }

            if (hasAmbiguousMatch(excelAuthor.normalizedName(), candidates)) {
                return UpdatePlan.noMatch("Ambiguous author matched last_author_excel: " + excelAuthor.name());
            }

            Optional<AuthorCandidate> createdCandidate = createCandidateFromExcel(excelAuthor);
            if (createdCandidate.isEmpty()) {
                return UpdatePlan.noMatch(
                        "No author matched last_author_excel and it cannot create a usable author: " + excelAuthor.name()
                );
            }

            AuthorCandidate candidate = createdCandidate.get();
            candidates.add(candidate);
            createdCandidates.add(candidate);
            addCandidateDistinct(additionalKappCandidates, candidate);
            matches.add(new Match(excelAuthor.normalizedName(), candidate, 1.0, "created from last_author_excel"));
        }

        if (candidates.isEmpty()) {
            return UpdatePlan.noMatch("No authors found in authors_kapp, authors_kramerius or last_author_excel");
        }

        AuthorCandidate selectedCandidate = latestDeathCandidate(candidates).orElse(null);
        if (selectedCandidate == null && additionalKappCandidates.isEmpty()) {
            return UpdatePlan.noMatch("No unique latest author death year and no title author additions are needed");
        }

        Match selectedMatch;
        if (selectedCandidate != null) {
            selectedMatch = matches.stream()
                    .filter(match -> sameAuthor(match.candidate().author(), selectedCandidate.author()))
                    .findFirst()
                    .orElse(new Match("", selectedCandidate, 1.0, "latest year_death among all authors"));
        } else {
            selectedMatch = matches.getFirst();
        }

        JSONArray updatedAuthorsKapp = buildUpdatedAuthorsKapp(authorsKapp, selectedCandidate, additionalKappCandidates);
        TitleAuthorFields titleAuthorFields = buildTitleAuthorFields(
                updatedAuthorsKapp,
                authorsKramerius
        );
        return new UpdatePlan(
                true,
                true,
                selectedMatch.matchedAuthor(),
                selectedMatch.candidate().author(),
                updatedAuthorsKapp,
                titleAuthorFields,
                selectedCandidate == null ? null : selectedCandidate.yearDeath(),
                createdCandidates.stream()
                        .map(AuthorCandidate::author)
                        .map(LastAuthorMatcher::copyObject)
                        .toList(),
                selectedMatch.reason()
        );
    }

    private static Optional<AuthorCandidate> latestDeathCandidate(List<AuthorCandidate> candidates) {
        if(candidates.stream().map(AuthorCandidate::yearDeath).anyMatch(Objects::isNull))return Optional.empty();

        Optional<Integer> latestYearDeath = candidates.stream()
                .map(AuthorCandidate::yearDeath)
                .max(Integer::compareTo);
        if (latestYearDeath.isEmpty()) {
            return Optional.empty();
        }

        int latestYearDeathValue = latestYearDeath.get();
        List<AuthorCandidate> latestCandidates = candidates.stream()
                .filter(candidate -> {
                    Integer yearDeath = candidate.yearDeath();
                    return yearDeath != null && latestYearDeathValue == yearDeath;
                })
                .toList();
        if (latestCandidates.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(latestCandidates.getFirst());
    }

    private static TitleAuthorFields buildTitleAuthorFields(JSONArray authorsKapp, JSONArray authorsKramerius) {
        List<String> ids = new ArrayList<>();
        List<String> nameIdentifiers = new ArrayList<>();
        List<String> names = new ArrayList<>();
        Set<String> oldIds = new HashSet<>();

        for (int i = 0; i < authorsKapp.length(); i++) {
            JSONObject author = authorsKapp.getJSONObject(i);
            addDistinct(ids, author.optString("id", null));
            addDistinct(nameIdentifiers, author.optString("nameIdentifier", null));
            addDistinct(names, author.optString("full", null));
            addKey(oldIds, author.optString("old_id", null));
        }

        Set<String> kappIds = new HashSet<>(ids);
        Set<String> kappNameIdentifiers = new HashSet<>(nameIdentifiers);
        Set<String> kappNames = new HashSet<>(names);
        for (int i = 0; i < authorsKramerius.length(); i++) {
            JSONObject author = authorsKramerius.getJSONObject(i);
            String id = author.optString("id", null);
            String nameIdentifier = author.optString("nameIdentifier", null);
            String full = author.optString("full", null);

            if (isPresent(id) && !kappIds.contains(id) && !oldIds.contains(id)) {
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
        List<Match> matches = rankedMatches(excelAuthor, candidates);

        if (matches.isEmpty()) {
            return Optional.empty();
        }

        Match best = matches.getFirst();
        if (matches.size() > 1 && best.score() - matches.get(1).score() <= AMBIGUOUS_MATCH_DELTA) {
            return Optional.empty();
        }

        return Optional.of(best);
    }

    private static boolean hasAmbiguousMatch(String excelAuthor, List<AuthorCandidate> candidates) {
        List<Match> matches = rankedMatches(excelAuthor, candidates);
        return matches.size() > 1 && matches.getFirst().score() - matches.get(1).score() <= AMBIGUOUS_MATCH_DELTA;
    }

    private static List<Match> rankedMatches(String excelAuthor, List<AuthorCandidate> candidates) {
        return candidates.stream()
                .map(candidate -> match(excelAuthor, candidate))
                .filter(match -> match.score() >= NAME_SIMILARITY_THRESHOLD)
                .sorted(Comparator.comparingDouble(Match::score).reversed())
                .toList();
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

    private static void addCandidateDistinct(List<AuthorCandidate> candidates, AuthorCandidate author) {
        if (candidates.stream().noneMatch(candidate -> sameAuthor(candidate.author(), author.author()))) {
            candidates.add(author);
        }
    }

    private static JSONArray buildUpdatedAuthorsKapp(
            JSONArray authorsKapp,
            AuthorCandidate selected,
            List<AuthorCandidate> additionalKappCandidates
    ) {
        JSONArray updated = copyArray(authorsKapp);
        if (selected != null) {
            for (int i = 0; i < updated.length(); i++) {
                updated.getJSONObject(i).put("isLast", false);
            }
        }

        for (AuthorCandidate additionalCandidate : additionalKappCandidates) {
            upsertAuthor(updated, additionalCandidate.author(), false);
        }

        if (selected != null) {
            upsertAuthor(updated, selected.author(), true);
        }
        return updated;
    }

    private static void upsertAuthor(JSONArray authors, JSONObject authorToUpsert, boolean isLast) {
        JSONObject author = copyObject(authorToUpsert);
        author.put("isLast", isLast);
        int duplicateIndex = findSameAuthor(authors, author);
        if (duplicateIndex >= 0) {
            authors.put(duplicateIndex, author);
        } else {
            authors.put(author);
        }
    }

    private static Optional<AuthorCandidate> createCandidateFromExcel(ExcelAuthor excelAuthor) {
        if (excelAuthor.name().isBlank()) {
            return Optional.empty();
        }

        JSONObject author = buildAuthorFromExcel(excelAuthor);
        return Optional.of(new AuthorCandidate(author, -1, false));
    }

    private static JSONObject buildAuthorFromExcel(ExcelAuthor excelAuthor) {
        NameSplit name = NameSplit.from(excelAuthor.name());
        JSONObject author = new JSONObject();
        String date = buildDate(excelAuthor.birthYear(), excelAuthor.deathYear());
        String id = normalizeId(name.family() + name.given() + date);

        author.put("id", id);
        author.put("full", name.full());
        author.put("family", name.family());
        author.put("given", name.given());
        author.put("date", date);
        author.put("role", new JSONArray());
        author.put("labels", new JSONArray().put(Constants.TitlesCollectionFields.Labels.AUTHOR_MODIFIED));
        author.put("confirmed_start_year", excelAuthor.birthYear() != null);
        author.put("confirmed_end_year", excelAuthor.deathYear() != null);
        if (excelAuthor.birthYear() != null) {
            author.put("year_birth", excelAuthor.birthYear());
        }
        if (excelAuthor.deathYear() != null) {
            author.put("year_death", excelAuthor.deathYear());
        }
        author.put("isLast", false);
        return author;
    }

    private static String buildDate(Integer birthYear, Integer deathYear) {
        if (birthYear == null && deathYear == null) {
            return "";
        }
        return (birthYear == null ? "" : birthYear) + "-" + (deathYear == null ? "" : deathYear);
    }

    private static boolean isUsableDeathYear(int yearDeath) {
        return yearDeath > 0 && yearDeath < 9999 && yearDeath <= Year.now().getValue();
    }

    private static String normalizeId(String value) {
        String normalized = Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFD);
        normalized = DIACRITICS.matcher(normalized).replaceAll("");
        return normalized.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    public static List<ExcelAuthor> parseExcelAuthors(List<String> lastAuthorsExcel) {
        List<ExcelAuthor> authors = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (String value : lastAuthorsExcel) {
            ExcelAuthor author = ExcelAuthor.parse(value);
            if (author.normalizedName().isBlank()) {
                continue;
            }
            String key = author.normalizedName() + "|" + author.birthYear() + "|" + author.deathYear();
            if (seen.add(key)) {
                authors.add(author);
            }
        }
        return authors;
    }

    public record ExcelAuthor(String name, String normalizedName, Integer birthYear, Integer deathYear) {

        static ExcelAuthor parse(String value) {
            if (value == null) {
                return new ExcelAuthor("", "", null, null);
            }

            String text = value.trim();
            if (text.startsWith("{")) {
                try {
                    JSONObject json = new JSONObject(text);
                    String name = json.optString("name", "");
                    return new ExcelAuthor(
                            name,
                            normalizeExcelAuthorName(name),
                            asInteger(json.opt("birthyear")),
                            asInteger(json.opt("deathyear"))
                    );
                } catch (JSONException e) {
                    YearPair years = extractYearPair(text);
                    String name = cleanExcelAuthorName(text);
                    return new ExcelAuthor(name, normalizeExcelAuthorName(name), years.birthYear(), years.deathYear());
                }
            }

            YearPair years = extractYearPair(text);
            String name = cleanExcelAuthorName(text);
            return new ExcelAuthor(name, normalizeExcelAuthorName(name), years.birthYear(), years.deathYear());
        }
    }

    private record YearPair(Integer birthYear, Integer deathYear) {
    }

    private static YearPair extractYearPair(String value) {
        if (value == null) {
            return new YearPair(null, null);
        }
        java.util.regex.Matcher matcher = YEAR_RANGE_VALUE.matcher(value);
        if (!matcher.find()) {
            return new YearPair(null, null);
        }
        return new YearPair(asInteger(matcher.group(1)), asInteger(matcher.group(2)));
    }

    private static String cleanExcelAuthorName(String value) {
        if (value == null) {
            return "";
        }
        String result = value.replace("ca.", " ")
                .replace("Ca.", " ")
                .replace("CA.", " ")
                .replace("[", " ")
                .replace("]", " ")
                .replace("?", " ");
        result = DATE_QUALIFIER_WITHOUT_DOT.matcher(result).replaceAll(" ");
        result = YEAR_RANGE.matcher(result).replaceAll(" ");
        result = YEAR.matcher(result).replaceAll(" ");
        result = result.replaceAll("(?i)\\b(?:překlad|překl\\.?|překladatel(?:ka)?|autor(?:ka)?|editor(?:ka)?|sestavovatel)\\b", " ");
        result = result.replaceAll("[()]", " ");
        result = result.replaceAll("\\s+", " ");
        result = result.replaceAll("\\s*,\\s*", ", ");
        result = result.replaceAll("^[,\\s]+|[,\\s]+$", "");
        return result.trim();
    }

    private record NameSplit(String full, String family, String given) {

        static NameSplit from(String value) {
            String name = value == null ? "" : value.trim().replaceAll("\\s+", " ");
            if (name.isBlank()) {
                return new NameSplit("", "", "");
            }

            if (name.contains(",")) {
                String[] parts = name.split(",", 2);
                String family = parts[0].trim();
                String given = parts.length > 1 ? parts[1].trim() : "";
                return new NameSplit(formatFull(family, given), family, given);
            }

            String[] tokens = name.split("\\s+");
            if (tokens.length == 1) {
                return new NameSplit(tokens[0], tokens[0], "");
            }

            String family = tokens[tokens.length - 1];
            String given = String.join(" ", List.of(tokens).subList(0, tokens.length - 1));
            return new NameSplit(formatFull(family, given), family, given);
        }

        private static String formatFull(String family, String given) {
            if (family.isBlank()) {
                return given;
            }
            if (given.isBlank()) {
                return family;
            }
            return family + ", " + given;
        }
    }

    private static List<AuthorCandidate> mergeCandidates(
            JSONArray authorsKapp,
            JSONArray authorsKramerius,
            JSONArray authorsCollection
    ) {
        authorsKapp = authorsKapp == null ? new JSONArray() : authorsKapp;
        authorsKramerius = authorsKramerius == null ? new JSONArray() : authorsKramerius;
        authorsCollection = authorsCollection == null ? new JSONArray() : authorsCollection;

        List<AuthorCandidate> candidates = new ArrayList<>();
        for (int i = 0; i < authorsKapp.length(); i++) {
            candidates.add(new AuthorCandidate(copyObject(authorsKapp.getJSONObject(i)), i, true));
        }

        for (int i = 0; i < authorsKramerius.length(); i++) {
            JSONObject author = authorsKramerius.getJSONObject(i);
            if (candidates.stream().noneMatch(candidate -> sameAuthor(candidate.author(), author))) {
                candidates.add(new AuthorCandidate(copyObject(author), -1, true));
            }
        }

        for (int i = 0; i < authorsCollection.length(); i++) {
            JSONObject author = copyObject(authorsCollection.getJSONObject(i));
            Optional<AuthorCandidate> sameTitleAuthor = candidates.stream()
                    .filter(AuthorCandidate::titleAuthor)
                    .filter(candidate -> sameAuthorName(candidate.author(), author))
                    .findFirst();

            if (sameTitleAuthor.isPresent() && isPresent(author.optString("nameIdentifier", null))) {
                String oldId = sameTitleAuthor.get().author().optString("id", null);
                if (isPresent(oldId) && !author.has("old_id")) {
                    author.put("old_id", oldId);
                }
                candidates.removeIf(candidate -> candidate.titleAuthor() && sameAuthorName(candidate.author(), author));
                candidates.add(new AuthorCandidate(author, -1, false));
            } else if (candidates.stream().noneMatch(candidate -> sameAuthor(candidate.author(), author))) {
                candidates.add(new AuthorCandidate(author, -1, false));
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

    private static boolean sameAuthorName(JSONObject first, JSONObject second) {
        Set<String> firstNames = authorNameKeys(first);
        Set<String> secondNames = authorNameKeys(second);
        firstNames.retainAll(secondNames);
        return !firstNames.isEmpty();
    }

    private static Set<String> authorNameKeys(JSONObject author) {
        Set<String> keys = new HashSet<>();
        addNameKey(keys, author.optString("full", null));
        addNameKey(keys, joinNameParts(author.optString("given", null), author.optString("family", null)));
        addNameKey(keys, joinNameParts(author.optString("family", null), author.optString("given", null)));
        return keys;
    }

    private static void addNameKey(Set<String> keys, String value) {
        String normalized = normalizeName(value);
        if (!normalized.isBlank() && normalized.split("\\s+").length >= 2) {
            keys.add(normalized);
        }
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

        String result = value.replace("ca.", " ")
                .replace("Ca.", " ") 
                .replace("CA.", " ")
                .replace("[", " ")
                .replace("]", " ")
                .replace("(", " ")
                .replace(")", " ")
                .replace("?", " ");

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

    private record AuthorCandidate(JSONObject author, int kappIndex, boolean titleAuthor) {

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
            Integer lastYearDeath,
            List<JSONObject> createdAuthors,
            String reason
            ) {

        static UpdatePlan noMatch(String reason) {
            return new UpdatePlan(
                    false,
                    false,
                    "",
                    new JSONObject(),
                    new JSONArray(),
                    TitleAuthorFields.empty(),
                    null,
                    List.of(),
                    reason
            );
        }
    }

    public record TitleAuthorFields(List<String> ids, List<String> nameIdentifiers, List<String> names) {

        static TitleAuthorFields empty() {
            return new TitleAuthorFields(List.of(), List.of(), List.of());
        }
    }
}
