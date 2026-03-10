package cz.inovatika.dates.normalization.utils;

import cz.inovatika.dates.normalization.NormalizationStep;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TitleNormalizationUtils {

    private TitleNormalizationUtils() {}


    public static final List<String> CORRECTION_KEYWORDS_FIRST_YEAR = Arrays.asList(
            "nespr", "chybne"
    );


    public static final List<String> CORRECTION_KEYWORDS_SECOND_YEAR = Arrays.asList(
            "spr",
            "spravne",
            "tiraz",
            "tir",
            "i.e.",
            "obalce",
            "ob.",
            "pretisk",
            "dotisk"
    );

    public static final List<String> BETWEEN_KEYWORDS = Arrays.asList(
            "mezi"
    );

    private static final List<Character> CHARACTERS_TO_REMOVE = Arrays.asList(
            '"', '\'', '(', ')',  '^', '!', ',', '>', '<', '[',']'
    );

    public static NormalizationStep removeUnnecessaryCharacters(NormalizationStep step) {
        String input = step.value();
        StringBuilder result = new StringBuilder();
        input.chars().forEach(c -> {
            char character = (char) c;
            if (!CHARACTERS_TO_REMOVE.contains(character)) {
                result.append(character);
            }
        });
        return new NormalizationStep(result.toString(), false);
    }


    public static NormalizationStep betweenKeywords(NormalizationStep step) {
        String input = step.value();
        for (String keyword : BETWEEN_KEYWORDS) {
            if (input.contains(keyword)) {
                String regex = ".*"+keyword + ".*?(\\d+).*?(\\d+)\\b.*";
                Pattern pattern = Pattern.compile(regex);
                Matcher matcher = pattern.matcher(input);
                if (matcher.find()) {
                    return new NormalizationStep(matcher.group(1) + "-" + matcher.group(2), true);
                }
            }
        }
        return step;
    }


    public static NormalizationStep resolveCorrectedDatesSecondDate(NormalizationStep step) {
        String text = step.value();
        for (String keyword : CORRECTION_KEYWORDS_SECOND_YEAR) {
            String regex = "(\\d+).*?" + Pattern.quote(keyword) + ".*?(\\d+)";
            Pattern pattern = Pattern.compile(regex);
            Matcher matcher = pattern.matcher(text);
            if (matcher.find()) {
                String r = matcher.group(2);
                return new NormalizationStep(r, true);
//                String r = matcher.replaceAll("$2");
//                return new NormalizationStep(r, true);
            }
        }
        return step;
    }

    public static NormalizationStep resolveCorrectedDatesFirstDate(NormalizationStep step) {
        String text = step.value();
        for (String keyword : CORRECTION_KEYWORDS_FIRST_YEAR) {
            String regex = "(\\d+).*?" + Pattern.quote(keyword) + ".*?(\\d+)";
            Pattern pattern = Pattern.compile(regex);
            Matcher matcher = pattern.matcher(text);
            if (matcher.find()) {
                return new  NormalizationStep(matcher.replaceAll("$1"), true);
            }
        }
        return step;
    }

    public static NormalizationStep removeDiacritics(NormalizationStep input) {
        String text = input.value();
        String nfdNormalizedString = Normalizer.normalize(text, Normalizer.Form.NFD);
        Pattern pattern = Pattern.compile("\\p{InCombiningDiacriticalMarks}+");
        String result = pattern.matcher(nfdNormalizedString).replaceAll("");
        return new NormalizationStep(result, false);
    }


    private static String fillMaskWith(String val, String replacement) {
        if (val == null) return "";
        if (val.length() > 4 && val.endsWith("?")) {
            val = val.substring(0, 4);
        }
        return val.replaceAll("[ux?\\-]", replacement);
    }

    public static NormalizationStep modifyOpenRanges(NormalizationStep step) {
        boolean applied = false;
        String input = step.value();
        Pattern p = Pattern.compile("\\[?([0-9xu?-]{4})\\]?-(?!\\d)");
        Matcher m = p.matcher(input);
        StringBuilder sb = new StringBuilder();
        int lastEnd = 0;
        while (m.find()) {
            applied = true;
            sb.append(input, lastEnd, m.start());
            String raw = m.group(1);
            String startYear = fillMaskWith(raw, "0");
            String endYear = fillMaskWith(raw, "9");
            sb.append(startYear).append("-").append(endYear);
            lastEnd = m.end();
        }
        if (!applied) return step;
        sb.append(input.substring(lastEnd));
        return new NormalizationStep(sb.toString(), true);
    }


    public static NormalizationStep modifyRanges(NormalizationStep step) {
        boolean applied =  false;
        String input = step.value();
        //Pattern p = Pattern.compile("\\[?([0-9xu?-]{4})\\]?[/-]\\[?([0-9xu?-]{2,4})\\]?(?![0-9])");
        Pattern p = Pattern.compile("\\[?([0-9xu-]{4})\\??\\]?[/-]\\[?([0-9xu-]{2,4})\\??\\]?(?![0-9])");
        Matcher m = p.matcher(input);
        StringBuilder sb = new StringBuilder();
        int lastEnd = 0;
        while (m.find()) {
            applied = true;

            String firstRaw = m.group(1);
            String first = fillMaskWith(firstRaw,"0");

            String secondRaw = m.group(2);
            String secondFinal;

            if (secondRaw.length() == 2) {
                // 1965/66 nebo 1965/-6 -> 1965-1966
                secondFinal = first.substring(0, 2) + fillMaskWith(secondRaw,"9");
            } else if (secondRaw.length() == 3) {
                // 1480-482 -> 1480-1482
                secondFinal = first.substring(0, 1) + fillMaskWith(secondRaw,"9");
            } else {
                // 4 znaky: 19uu-20xx -> 1900-2000
                secondFinal = fillMaskWith(secondRaw,"9");
            }

            sb.append(first).append("-").append(secondFinal);
            lastEnd = m.end();
        }
        return applied ? new  NormalizationStep(sb.toString(), true) : step;
    }

    public static NormalizationStep copyRightYears(NormalizationStep step) {
        boolean applied = false;
        String input = step.value();

        Pattern pattern = Pattern.compile("\\bc(\\d{3,4}[\\d?u-]*)");
        Matcher matcher = pattern.matcher(input);

        StringBuilder sb = new StringBuilder();
        int lastEnd = 0;
        while (matcher.find()) {
            applied = true;
            String raw = matcher.group(1);
            if (sb.length() > 0) {
                sb.append(" ");
            }
            sb.append(raw);
        }
        return applied ? new NormalizationStep(sb.toString(), !sb.toString().endsWith("-")) : step;
    }

    public static NormalizationStep extractYearFromFullDate(NormalizationStep step) {
        String input = step.value();
        if (input.contains("-")) {
            return step;
        }
        String regex = "(?:\\b\\d{1,2}\\s*\\.\\s*)(?:[0-9]{1,2}|[ivxl]+\\.?)\\s*\\.\\s*(\\d{4})\\b";
        Pattern pattern = Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(input);
        if (matcher.find()) {
            return new NormalizationStep(matcher.group(1), true);
        }
        Pattern yearOnly = Pattern.compile("\\b(\\d{4})\\b");
        Matcher mYear = yearOnly.matcher(input);
        if (mYear.find()) {
            return new NormalizationStep(mYear.group(1), true);
        }
        return step;
    }

    //


    public static NormalizationStep sanitizeFinalOutput(NormalizationStep step) {
        String input = step.value();

        Pattern rangePattern = Pattern.compile(".*?(\\d{4})-(\\d{4}).*");
        Matcher rangeMatcher = rangePattern.matcher(input);

        if (rangeMatcher.find()) {
            return new NormalizationStep(rangeMatcher.group(1) + "-" + rangeMatcher.group(2), true);
        }

        Pattern singlePattern = Pattern.compile(".*?(\\d{4})\\s*-?.*");
        Matcher singleMatcher = singlePattern.matcher(input);

        if (singleMatcher.find()) {
            String val = singleMatcher.group(1);
            return new NormalizationStep(val, true);
        }
        return new NormalizationStep("",false);
    }

    public static String normalize(String raw) {
        if (raw == null || raw.isEmpty()) return "";

        NormalizationStep finalStep = new NormalizationStep(raw.toLowerCase().trim(), false)
                .apply(TitleNormalizationUtils::removeDiacritics)
                .apply(TitleNormalizationUtils::removeUnnecessaryCharacters)
                .apply(TitleNormalizationUtils::copyRightYears)
                .apply(TitleNormalizationUtils::betweenKeywords)
                .apply(TitleNormalizationUtils::resolveCorrectedDatesFirstDate)
                .apply(TitleNormalizationUtils::resolveCorrectedDatesSecondDate)
                .apply(TitleNormalizationUtils::modifyRanges)
                .apply(TitleNormalizationUtils::modifyOpenRanges)
                .apply(TitleNormalizationUtils::extractYearFromFullDate)
                .apply( TitleNormalizationUtils::sanitizeFinalOutput);
        return finalStep.value().replaceAll("\\s+", " ").trim();
    }
}



