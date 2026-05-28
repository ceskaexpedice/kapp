package cz.inovatika;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class LastAuthorExcelParser {
    private static final Pattern YEAR_RANGE = Pattern.compile(
            "(?i)(?:\\[?\\??\\s*(?:ca\\.?|asi|kolem)?\\s*\\[?(\\d{3,4})\\]?\\??|[?x])\\s*[-\\u2010-\\u2015/]\\s*(?:\\[?\\??\\s*(?:ca\\.?|asi|kolem)?\\s*\\[?(\\d{3,4})\\]?\\??|[?x])?");
    private static final Pattern BIRTH_YEAR = Pattern.compile(
            "(?i)\\b(?:nar\\.?|narozen(?:ý|a)?|narodil(?:a)?|born)\\s*(?:ca\\.?|asi|kolem|možná)?\\s*(\\d{3,4})\\b");
    private static final Pattern DEATH_YEAR = Pattern.compile(
            "(?i)\\b(?:zemřel(?:a)?|zem\\.?|umřel(?:a)?|umr\\.?|dead)\\s*(?:ca\\.?|asi|kolem)?\\s*(\\d{3,4})\\b");
    private static final Pattern YEAR_PAIR_WITH_SPACE = Pattern.compile("\\b(\\d{3,4})\\s+(\\d{3,4})\\b");
    private static final Pattern DIGIT = Pattern.compile("\\d");
    private static final Pattern LETTER_DIGIT_BOUNDARY = Pattern.compile("(?<=\\p{L})(?=\\d{3,4})");
    private static final Pattern DIGIT_LETTER_BOUNDARY = Pattern.compile("(?<=\\d)(?=\\p{L})");
    private static final Pattern PARENTHETICAL = Pattern.compile("\\([^)]*\\)");
    private static final Pattern ROLE_WORDS = Pattern.compile(
            "(?i)\\b(?:autor(?:ka)?|autoři|překlad(?:atel(?:ka)?)?|překl\\.?|prekladateľ|ilustr(?:ace|átor(?:ka)?)|editor(?:ka)?|doslov|úvod|předmluva|redakce|kompilátor|sestavovatel|obálka|grafika|poznámky?)\\b");
    private static final Pattern TRAILING_NOISE = Pattern.compile("[\\s,.;:/\\-+?]+$");
    private static final Pattern LEADING_NOISE = Pattern.compile("^[\\s,.;:/\\-+?]+");

    private LastAuthorExcelParser() {
    }

    public static List<String> parse(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }

        List<String> result = new ArrayList<>();
        for (String segment : splitOutsideParentheses(normalize(value), ';')) {
            result.addAll(parseSegment(segment));
        }
        return result.stream().distinct().toList();
    }

    private static List<String> parseSegment(String segment) {
        String cleanedSegment = stripLeadingRolePrefix(segment);
        if (cleanedSegment.isBlank() || isSkippable(cleanedSegment)) {
            return List.of();
        }

        List<String> parts = splitOutsideParentheses(cleanedSegment, ',').stream()
                .map(String::trim)
                .filter(part -> !part.isBlank())
                .toList();

        if (parts.size() < 2) {
            return parseSingle(cleanedSegment);
        }

        List<String> result = new ArrayList<>();
        for (int i = 0; i < parts.size(); ) {
            String surname = stripLeadingRolePrefix(parts.get(i));
            if (isDateOnly(surname)) {
                i++;
                continue;
            }

            if (i + 1 >= parts.size()) {
                result.addAll(parseSingle(surname));
                i++;
                continue;
            }

            String given = parts.get(i + 1);
            YearInfo yearInfo = YearInfo.empty();
            int consumed = 2;

            YearInfo yearsInGiven = extractYears(given);
            if (yearsInGiven.hasAnyYear()) {
                yearInfo = yearsInGiven;
                given = removeYearText(given);
            } else if (i + 2 < parts.size() && (isDateOnly(parts.get(i + 2)) || extractYears(parts.get(i + 2)).hasAnyYear())) {
                yearInfo = extractYears(parts.get(i + 2));
                consumed = 3;
            }

            String name = formatName(surname, given);
            if (isUsableName(name)) {
                result.add(toJson(name, yearInfo));
            }
            i += consumed;
        }

        return result;
    }

    private static List<String> parseSingle(String value) {
        YearInfo yearInfo = extractYears(value);
        String name = cleanupName(removeYearText(value));
        if (!isUsableName(name)) {
            return List.of();
        }
        return List.of(toJson(name, yearInfo));
    }

    private static YearInfo extractYears(String value) {
        if (value == null || value.isBlank()) {
            return YearInfo.empty();
        }

        Matcher rangeMatcher = YEAR_RANGE.matcher(value);
        while (rangeMatcher.find()) {
            Integer birthYear = parseYear(rangeMatcher.group(1));
            Integer deathYear = parseYear(rangeMatcher.group(2));
            if (birthYear != null || deathYear != null) {
                return new YearInfo(birthYear, deathYear);
            }
        }

        Integer birthYear = firstYear(BIRTH_YEAR, value);
        Integer deathYear = firstYear(DEATH_YEAR, value);
        if (birthYear != null || deathYear != null) {
            return new YearInfo(birthYear, deathYear);
        }

        Matcher pairMatcher = YEAR_PAIR_WITH_SPACE.matcher(value);
        if (pairMatcher.find()) {
            return new YearInfo(parseYear(pairMatcher.group(1)), parseYear(pairMatcher.group(2)));
        }

        return YearInfo.empty();
    }

    private static String removeYearText(String value) {
        String result = YEAR_RANGE.matcher(value).replaceAll(" ");
        result = BIRTH_YEAR.matcher(result).replaceAll(" ");
        result = DEATH_YEAR.matcher(result).replaceAll(" ");
        result = YEAR_PAIR_WITH_SPACE.matcher(result).replaceAll(" ");
        return result;
    }

    private static Integer firstYear(Pattern pattern, String value) {
        Matcher matcher = pattern.matcher(value);
        if (matcher.find()) {
            return parseYear(matcher.group(1));
        }
        return null;
    }

    private static Integer parseYear(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Integer.valueOf(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String toJson(String name, YearInfo yearInfo) {
        JSONObject json = new JSONObject().put("name", name);
        if (yearInfo.birthYear() != null) {
            json.put("birthyear", yearInfo.birthYear());
        }
        if (yearInfo.deathYear() != null) {
            json.put("deathyear", yearInfo.deathYear());
        }
        return json.toString();
    }

    private static String normalize(String value) {
        String result = value.replace('–', '-').replace('—', '-');
        result = LETTER_DIGIT_BOUNDARY.matcher(result).replaceAll(" ");
        result = DIGIT_LETTER_BOUNDARY.matcher(result).replaceAll(" ");
        return result.trim();
    }

    private static List<String> splitOutsideParentheses(String value, char delimiter) {
        List<String> parts = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int depth = 0;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '(') {
                depth++;
            } else if (c == ')' && depth > 0) {
                depth--;
            }

            if (c == delimiter && depth == 0) {
                parts.add(current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        parts.add(current.toString());
        return parts;
    }

    private static String stripLeadingRolePrefix(String value) {
        String result = value.trim();
        int colon = result.indexOf(':');
        if (colon >= 0) {
            result = result.substring(colon + 1).trim();
        }
        result = result.replaceFirst("(?i)^(?:předmluva|doslov|úvod|editor|autoři?|překlad|jednotl\\.\\s*díla\\s*od)\\s*[-:]\\s*", "");
        return result;
    }

    private static String formatName(String surname, String given) {
        surname = cleanupName(surname);
        given = cleanupName(given);
        if (surname.isBlank()) {
            return given;
        }
        if (given.isBlank()) {
            return surname;
        }
        return cleanupName(given + " " + surname);
    }

    private static String cleanupName(String value) {
        String result = stripLeadingRolePrefix(value);
        result = removeYearText(result);
        result = PARENTHETICAL.matcher(result).replaceAll(" ");
        result = ROLE_WORDS.matcher(result).replaceAll(" ");
        result = result.replaceAll("(?i)\\ba\\s+(?:řada|mnoho)\\s+dalších\\b", " ");
        result = result.replaceAll("(?i)\\+\\s*(?:řada|mnoho)\\s+autorů.*$", " ");
        result = LEADING_NOISE.matcher(result).replaceAll("");
        result = TRAILING_NOISE.matcher(result).replaceAll("");
        return result.trim().replaceAll("\\s+", " ");
    }

    private static boolean isDateOnly(String value) {
        String withoutYears = cleanupName(removeYearText(value));
        return withoutYears.isBlank() && (extractYears(value).hasAnyYear() || value.contains("?") || value.toUpperCase(Locale.ROOT).contains("X"));
    }

    private static boolean isUsableName(String value) {
        if (value == null || value.isBlank() || DIGIT.matcher(value).find()) {
            return false;
        }
        return !isSkippable(value);
    }

    private static boolean isSkippable(String value) {
        String normalized = value.toLowerCase(Locale.ROOT).trim();
        return normalized.isBlank()
                || "-".equals(normalized)
                || normalized.contains("anonym")
                || normalized.contains("kolektiv")
                || normalized.contains("úřední")
                || normalized.contains("souborné dílo")
                || normalized.contains("autor neznámý")
                || normalized.contains("bez uvedení autora")
                || normalized.equals("řada autorů")
                || normalized.equals("a další")
                || normalized.equals("a řada dalších");
    }

    private record YearInfo(Integer birthYear, Integer deathYear) {
        static YearInfo empty() {
            return new YearInfo(null, null);
        }

        boolean hasAnyYear() {
            return birthYear != null || deathYear != null;
        }
    }
}
