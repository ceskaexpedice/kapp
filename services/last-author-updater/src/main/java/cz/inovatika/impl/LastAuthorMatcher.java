package cz.inovatika.impl;

import cz.inovatika.Constants;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.Normalizer;
import java.time.Year;
import java.util.*;
import java.util.regex.Pattern;

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
            JSONArray authorsCollection,
            Integer currentLastYearDeath
    ) {
        ParsedExcelAuthors parsedExcelAuthors = parseExcelAuthorValues(lastAuthorsExcel);
        List<ExcelAuthor> excelAuthors = parsedExcelAuthors.authors();
        List<String> labels = new ArrayList<>();
        if (parsedExcelAuthors.parsingIssue()) {
            addLabel(labels, Constants.TitlesCollectionFields.Labels.LAST_AUTHOR_PARSING_ISSUE);
        }
        Integer excelLastYearDeath = latestExcelDeathYear(excelAuthors);
        boolean incompleteExcelDeathYears = !excelAuthors.isEmpty()
                && excelAuthors.stream().map(ExcelAuthor::deathYear).anyMatch(Objects::isNull);

        if (excelAuthors.isEmpty()) {
            return UpdatePlan.noAuthorUpdate(
                    "No usable last_author_excel value",
                    lastYearDeathChange(currentLastYearDeath, excelLastYearDeath, null, false, labels),
                    labels
            );
        }

        List<AuthorCandidate> candidates = mergeCandidates(authorsKapp, authorsKramerius, authorsCollection);
        List<Match> matches = new ArrayList<>();
        List<AuthorCandidate> matchedCandidates = new ArrayList<>();
        List<AuthorCandidate> createdCandidates = new ArrayList<>();
        List<AuthorCandidate> additionalKappCandidates = new ArrayList<>();
        for (ExcelAuthor excelAuthor : excelAuthors) {
            Optional<Match> match = bestMatch(excelAuthor.normalizedName(), candidates);
            if (match.isPresent()) {
                Match foundMatch = match.get();
                if (applyExcelDeathYear(foundMatch.candidate(), excelAuthor)) {
                    addLabel(labels, Constants.TitlesCollectionFields.Labels.DIFFERING_AUTHOR_DEATH_YEAR);
                    System.out.println(Constants.TitlesCollectionFields.Labels.DIFFERING_AUTHOR_DEATH_YEAR + " - Candidate year death: " + foundMatch.candidate().yearDeath()+"; excel Author death year: " + excelAuthor.deathYear());
                }
                if (!foundMatch.candidate().titleAuthor()) {
                    addCandidateDistinct(additionalKappCandidates, foundMatch.candidate());
                }
                addCandidateDistinct(matchedCandidates, foundMatch.candidate());
                matches.add(foundMatch);
                continue;
            }

            if (hasAmbiguousMatch(excelAuthor.normalizedName(), candidates)) {
                return UpdatePlan.noAuthorUpdate(
                        "Ambiguous author matched last_author_excel: " + excelAuthor.name(),
                        lastYearDeathChange(currentLastYearDeath, excelLastYearDeath, null, false, labels),
                        labels
                );
            }

            Optional<AuthorCandidate> createdCandidate = createCandidateFromExcel(excelAuthor);
            if (createdCandidate.isEmpty()) {
                return UpdatePlan.noAuthorUpdate(
                        "No author matched last_author_excel and it cannot create a usable author: " + excelAuthor.name(),
                        lastYearDeathChange(currentLastYearDeath, excelLastYearDeath, null, false, labels),
                        labels
                );
            }

            AuthorCandidate candidate = createdCandidate.get();
            candidates.add(candidate);
            createdCandidates.add(candidate);
            addCandidateDistinct(matchedCandidates, candidate);
            addCandidateDistinct(additionalKappCandidates, candidate);
            matches.add(new Match(excelAuthor.normalizedName(), candidate, 1.0, "created from last_author_excel"));
        }

        if (candidates.isEmpty()) {
            return UpdatePlan.noAuthorUpdate(
                    "No authors found in authors_kapp, authors_kramerius or last_author_excel",
                    lastYearDeathChange(currentLastYearDeath, excelLastYearDeath, null, false, labels),
                    labels
            );
        }

        AuthorCandidate selectedCandidate = latestDeathCandidate(matchedCandidates)
                .or(() -> latestDeathCandidate(candidates))
                .orElse(null);
        LastYearDeathChange lastYearDeathChange = lastYearDeathChange(
                currentLastYearDeath,
                excelLastYearDeath,
                selectedCandidate == null ? null : selectedCandidate.yearDeath(),
                incompleteExcelDeathYears && selectedCandidate == null,
                labels
        );
        if (selectedCandidate == null && additionalKappCandidates.isEmpty()) {
            return UpdatePlan.noAuthorUpdate(
                    "No unique latest author death year and no title author additions are needed",
                    lastYearDeathChange,
                    labels
            );
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
                true,
                selectedMatch.matchedAuthor(),
                selectedMatch.candidate().author(),
                updatedAuthorsKapp,
                titleAuthorFields,
                lastYearDeathChange.value(),
                lastYearDeathChange.clear(),
                createdCandidates.stream()
                        .map(AuthorCandidate::author)
                        .map(LastAuthorMatcher::copyObject)
                        .toList(),
                List.copyOf(labels),
                selectedMatch.reason()
        );
    }

    private static Integer latestExcelDeathYear(List<ExcelAuthor> excelAuthors) {
        if (excelAuthors.isEmpty() || excelAuthors.stream().map(ExcelAuthor::deathYear).anyMatch(Objects::isNull)) {
            return null;
        }
        return excelAuthors.stream()
                .map(ExcelAuthor::deathYear)
                .filter(LastAuthorMatcher::isUsableDeathYear)
                .max(Integer::compareTo)
                .orElse(null);
    }

    private static LastYearDeathChange lastYearDeathChange(
            Integer currentLastYearDeath,
            Integer excelLastYearDeath,
            Integer selectedAuthorDeathYear,
            boolean clearWhenMissingDeathYear,
            List<String> labels
    ) {
        Integer desiredLastYearDeath = excelLastYearDeath != null ? excelLastYearDeath : selectedAuthorDeathYear;
        if (desiredLastYearDeath == null || Objects.equals(currentLastYearDeath, desiredLastYearDeath)) {
            boolean shouldClear = desiredLastYearDeath == null
                    && clearWhenMissingDeathYear
                    && currentLastYearDeath != null;
            return new LastYearDeathChange(null, shouldClear);
        }
        if (currentLastYearDeath != null && excelLastYearDeath != null) {
            addLabel(labels, Constants.TitlesCollectionFields.Labels.DIFFERING_AUTHOR_DEATH_YEAR);
        }
        if (currentLastYearDeath == null || excelLastYearDeath != null) {
            return new LastYearDeathChange(desiredLastYearDeath, false);
        }
        return LastYearDeathChange.none();
    }

    private record LastYearDeathChange(Integer value, boolean clear) {

        static LastYearDeathChange none() {
            return new LastYearDeathChange(null, false);
        }

        boolean shouldUpdate() {
            return value != null || clear;
        }
    }

    private static void addLabel(List<String> labels, String label) {
        if (label != null && !label.isBlank() && !labels.contains(label)) {
            labels.add(label);
        }
    }

    private static boolean applyExcelDeathYear(AuthorCandidate candidate, ExcelAuthor excelAuthor) {
        if (excelAuthor.deathYear() == null || !isUsableDeathYear(excelAuthor.deathYear())) {
            return false;
        }

        JSONObject author = candidate.author();
        Integer existingDeathYear = asInteger(author.opt("year_death"));
        if (Objects.equals(existingDeathYear, excelAuthor.deathYear())) {
            if (!author.has("confirmed_end_year")) {
                author.put("confirmed_end_year", true);
            }
            return false;
        }

        author.put("year_death", excelAuthor.deathYear());
        author.put("confirmed_end_year", true);
        return existingDeathYear != null;
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

    private static boolean isUsableDeathYear(Integer yearDeath) {
        return yearDeath != null && yearDeath > 0 && yearDeath < 9999 && yearDeath <= Year.now().getValue();
    }

    private static String normalizeId(String value) {
        String normalized = Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFD);
        normalized = DIACRITICS.matcher(normalized).replaceAll("");
        return normalized.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    public static List<ExcelAuthor> parseExcelAuthors(List<String> lastAuthorsExcel) {
        return parseExcelAuthorValues(lastAuthorsExcel).authors();
    }

    private static ParsedExcelAuthors parseExcelAuthorValues(List<String> lastAuthorsExcel) {
        List<ExcelAuthor> authors = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        boolean parsingIssue = false;
        for (String value : lastAuthorsExcel) {
            ExcelAuthor author = ExcelAuthor.parse(value);
            parsingIssue = parsingIssue || author.parsingIssue();
            if (author.normalizedName().isBlank()) {
                continue;
            }
            String key = author.normalizedName() + "|" + author.birthYear() + "|" + author.deathYear();
            if (seen.add(key)) {
                authors.add(author);
            }
        }
        return new ParsedExcelAuthors(List.copyOf(authors), parsingIssue);
    }

    private record ParsedExcelAuthors(List<ExcelAuthor> authors, boolean parsingIssue) {
    }

    public record ExcelAuthor(String name, String normalizedName, Integer birthYear, Integer deathYear, boolean parsingIssue) {

        static ExcelAuthor parse(String value) {
            if (value == null) {
                return new ExcelAuthor("", "", null, null, false);
            }

            String text = value.trim();
            if (text.isBlank()) {
                return new ExcelAuthor("", "", null, null, false);
            }

            if (text.startsWith("{")) {
                try {
                    JSONObject json = new JSONObject(text);
                    String name = json.optString("name", "");
                    Integer birthYear = asInteger(json.opt("birthyear"));
                    Integer deathYear = asInteger(json.opt("deathyear"));
                    boolean parsingIssue = normalizeExcelAuthorName(name).isBlank()
                            || hasUnparseableInteger(json, "birthyear", birthYear)
                            || hasUnparseableInteger(json, "deathyear", deathYear)
                            || (deathYear != null && !isUsableDeathYear(deathYear));
                    return new ExcelAuthor(
                            name,
                            normalizeExcelAuthorName(name),
                            birthYear,
                            deathYear,
                            parsingIssue
                    );
                } catch (JSONException e) {
                    YearPair years = extractYearPair(text);
                    String name = cleanExcelAuthorName(text);
                    return new ExcelAuthor(name, normalizeExcelAuthorName(name), years.birthYear(), years.deathYear(), true);
                }
            }

            YearPair years = extractYearPair(text);
            String name = cleanExcelAuthorName(text);
            String normalizedName = normalizeExcelAuthorName(name);
            return new ExcelAuthor(name, normalizedName, years.birthYear(), years.deathYear(), normalizedName.isBlank());
        }
    }

    private static boolean hasUnparseableInteger(JSONObject json, String fieldName, Integer parsedValue) {
        return json.has(fieldName) && !json.isNull(fieldName) && parsedValue == null;
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
            boolean updateAuthors,
            String matchedExcelAuthor,
            JSONObject matchedAuthor,
            JSONArray updatedAuthorsKapp,
            TitleAuthorFields titleAuthorFields,
            Integer lastYearDeath,
            boolean clearLastYearDeath,
            List<JSONObject> createdAuthors,
            List<String> labels,
            String reason
            ) {

        static UpdatePlan noAuthorUpdate(String reason, LastYearDeathChange lastYearDeathChange, List<String> labels) {
            return new UpdatePlan(
                    false,
                    lastYearDeathChange.shouldUpdate() || !labels.isEmpty(),
                    false,
                    "",
                    new JSONObject(),
                    new JSONArray(),
                    TitleAuthorFields.empty(),
                    lastYearDeathChange.value(),
                    lastYearDeathChange.clear(),
                    List.of(),
                    List.copyOf(labels),
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
