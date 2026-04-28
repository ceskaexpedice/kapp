package cz.inovatika.dates.normalization.utils;

import cz.inovatika.dates.normalization.NormalizationStep;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class AuthorNormalizationUtils {

    private AuthorNormalizationUtils() {}

    public static final List<Character> CHARACTERS_TO_REMOVE = Arrays.asList(
            '"', '\'', '(', ')', '^', '!', ',', '>', '<', '[', ']'
    );

    public static final List<String> DEATH_KEYWORDS = Arrays.asList(
            "zemr.","zemr","zem.","zem", "umr.","umr", "um", "†"
    );

    public static final List<String> BIRTH_KEYWORDS = Arrays.asList(
            "narozen", "nar.","nar", "n.r.","nr","na"
    );

    public static final List<String> ACTIVE_KEYWORDS = Arrays.asList(
            "cinny",  "cinne", "mezi"
    );

    public static final List<String> STOLETI_KEYWORD = Arrays.asList(
            "stoleti",
            "století",
            "stol",
            "st",
            "stol."
    );

    public static final List<String> RANGE_KEYWORDS = Arrays.asList(
            "az", "a" ,"-"
    );

    public static final List<String> APPROX_YEAR_KEYWORDS = Arrays.asList(
            "cca",
            "ca",
            "asi"
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

    public static NormalizationStep removeDiacritics(NormalizationStep step) {
        String text = step.value();
        String nfdNormalizedString = java.text.Normalizer.normalize(text, java.text.Normalizer.Form.NFD);
        String result = nfdNormalizedString.replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
        return new NormalizationStep(result, false);
    }

//    /**
//     *
//     * @param step
//     * @return
//     */
//    public static NormalizationStep lifeData(NormalizationStep step) {
//        String text = step.value();
//        Scanner scanner = new Scanner(text);
//        while (scanner.hasNext()) {
//            String token = scanner.next();
//            // kontrola, zda je keyword
//            if (BIRTH_KEYWORDS.contains(token.toLowerCase()) || ...) {
//                // keyword token
//            } else {
//                // value token
//            }
//        }
//    }

    public static NormalizationStep extractLifeYears(NormalizationStep step) {
        String input = step.value().toLowerCase().trim();
        String birthYear = null;
        String deathYear = null;

        for (String keyword : BIRTH_KEYWORDS) {
            Pattern pattern = Pattern.compile(
                    "\\b" + Pattern.quote(keyword) + "\\.?\\s*.*?(\\d{3,4})\\b",
                    Pattern.CASE_INSENSITIVE
            );


            Matcher matcher = pattern.matcher(input);
            if (matcher.find()) {
                birthYear = matcher.group(1);
                break;
            }
        }

        for (String keyword : DEATH_KEYWORDS) {
            //Pattern pattern = Pattern.compile("\\b" + Pattern.quote(keyword) + "\\s*(\\d{3,4})\\b", Pattern.CASE_INSENSITIVE);
            Pattern pattern = Pattern.compile(
                    "\\b" + Pattern.quote(keyword) + "\\.?\\s*.*?(\\d{3,4})\\b",
                    Pattern.CASE_INSENSITIVE
            );
            Matcher matcher = pattern.matcher(input);
            if (matcher.find()) {
                deathYear = matcher.group(1);
                break;
            }
        }

        if (birthYear != null && deathYear != null) {
            return new NormalizationStep(birthYear + "-" + deathYear, true);
        } else if (birthYear != null) {
            return new NormalizationStep(birthYear + "-", true);
        } else if (deathYear != null) {
            return new NormalizationStep("-" + deathYear, true);
        }
        return step;
    }


    public static NormalizationStep extractActiveYears(NormalizationStep step) {
        String input = step.value().toLowerCase().trim();

        for (String keyword : ACTIVE_KEYWORDS) {
            int idx = input.indexOf(keyword);
            if (idx < 0) {
                continue;
            }

            String rest = input.substring(idx + keyword.length()).trim();
            NormalizationStep mAz = range(new NormalizationStep(rest, false));
            if (mAz.isDone()) return mAz;

            return yearOrEpoch(new NormalizationStep(rest, false));



//            // činný v 18.-19. století
//            for (String stol : STOLETI_KEYWORD) {
//                Pattern centuryRange = Pattern.compile(
//                        "(\\d{1,2})\\.\\s*-\\s*(\\d{1,2})\\.\\s*" + Pattern.quote(stol),
//                        Pattern.CASE_INSENSITIVE
//                );
//                Matcher mCenturyRange = centuryRange.matcher(input);
//                if (mCenturyRange.find()) {
//                    int c1 = Integer.parseInt(mCenturyRange.group(1));
//                    int c2 = Integer.parseInt(mCenturyRange.group(2));
//
//                    int start = (c1 - 1) * 100;
//                    int end = (c2 - 1) * 100 + 99;
//
//                    return new NormalizationStep(start + "-" + end, true);
//                }
//            }

//            // činný v 18. století
//            for (String stol : STOLETI_KEYWORD) {
//                Pattern century = Pattern.compile(
//                        "(\\d{1,2})\\.\\s*" + Pattern.quote(stol),
//                        Pattern.CASE_INSENSITIVE
//                );
//                Matcher mCentury = century.matcher(input);
//                if (mCentury.find()) {
//                    int c = Integer.parseInt(mCentury.group(1));
//
//                    int start = (c - 1) * 100;
//                    int end = start + 99;
//
//                    return new NormalizationStep(start + "-" + end, true);
//                }
//            }
        }

        return step;
    }

    public static NormalizationStep range(NormalizationStep step) {
        String value = step.value();
        String rangeKeywordPattern = RANGE_KEYWORDS.stream()
                .map(Pattern::quote)
                .collect(Collectors.joining("|"));

        String approxPattern = APPROX_YEAR_KEYWORDS.stream()
                .map(Pattern::quote)
                .collect(Collectors.joining("|"));

        Pattern pattern = Pattern.compile(
                "\\b(?:(?:" + approxPattern + ")\\s*)?(\\d{3,4})\\s*(?:" +
                        rangeKeywordPattern +
                        ")\\s*(?:(?:" + approxPattern + ")\\s*)?(\\d{3,4})\\b",
                Pattern.CASE_INSENSITIVE
        );

        Matcher matcher = pattern.matcher(value);

        if (matcher.find()) {
            String y1 = yearOrEpoch(new NormalizationStep(matcher.group(1), false)).value();
            String y2 = yearOrEpoch(new NormalizationStep(matcher.group(2), false)).value();
            return new NormalizationStep(y1 + "-" + y2, true);
        }
        return step;
    }


    public static NormalizationStep closeRange(NormalizationStep step) {
        String value = step.value().toLowerCase().trim();
       String approxPattern = APPROX_YEAR_KEYWORDS.stream()
                .map(Pattern::quote)
                .collect(Collectors.joining("|"));
        Pattern pattern = Pattern.compile(
                "-\\s*(?:(?:" + approxPattern + ")\\s*)?(\\d{2,4})\\b",
                Pattern.CASE_INSENSITIVE
        );
        Matcher matcher = pattern.matcher(value);
        if (matcher.find()) {
            String year = matcher.group(1);
            return new NormalizationStep("-"+ year, true);
        }

        return step;
    }

    public static NormalizationStep openRange(NormalizationStep step) {
        String value = step.value().toLowerCase().trim();
        Pattern pattern = Pattern.compile("(\\d{3,4})\\s*-", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(value);

        if (matcher.find()) {
            String year = matcher.group(1);
            return new NormalizationStep(year + "-", true);
        }
        return step;
    }


    public static NormalizationStep yearOrEpoch(NormalizationStep step) {
        String value = step.value();
        List<String> words = Arrays.asList(value.split("\\s+"));

        for(String st : words) {
            if (words.contains(st)) {
                String centuryKeywords = STOLETI_KEYWORD.stream()
                        .map(Pattern::quote)
                        .collect(Collectors.joining("|"));
                Pattern singleCentury = Pattern.compile(
                        "(\\d{1,2})\\.\\s*(?:" + centuryKeywords + ")",
                        Pattern.CASE_INSENSITIVE
                );

                Matcher mSingle = singleCentury.matcher(value);
                if (mSingle.find()) {

                    int century = Integer.parseInt(mSingle.group(1));

                    int start = (century - 1) * 100;
                    int end = century * 100 - 1;

                    return new NormalizationStep(start + "?-" + end+"?", true);
                }
                Pattern rangeCentury = Pattern.compile(
                        "(\\d{1,2})\\.\\s*(?:" + centuryKeywords + ")\\s*-\\s*(\\d{1,2})\\.\\s*(?:" + centuryKeywords + ")",
                        Pattern.CASE_INSENSITIVE
                );

                Matcher mRange = rangeCentury.matcher(value);
                if (mRange.find()) {
                    int c1 = Integer.parseInt(mRange.group(1));
                    int c2 = Integer.parseInt(mRange.group(2));
                    int start = (c1 - 1) * 100;
                    int end = c2 * 100 - 1;
                    return new NormalizationStep(start + "?-" + end+"?", true);
                }
            }

        }
        return year(step);
    }

    public static NormalizationStep year(NormalizationStep step) {
        String value = step.value();

        String approxPattern = APPROX_YEAR_KEYWORDS.stream()
                .map(Pattern::quote)
                .collect(Collectors.joining("|"));

        Pattern pattern = Pattern.compile("\\b(?:(?:" + approxPattern + ")\\s*)?(\\d{3,4})\\b");
        Matcher matcher = pattern.matcher(value);

        if (matcher.find()) {
            return new NormalizationStep(matcher.group(1), true);
        }
        return step;
    }

    public static NormalizationStep sanitizeFinalOutput(NormalizationStep step) {
        String text = step.value();
        Pattern pattern = Pattern.compile("\\b(\\d{3,4})\\b");
        Matcher matcher = pattern.matcher(text);
        if (matcher.find()) {
            return new NormalizationStep( matcher.group(1), true);
        }
        return new NormalizationStep("",false);
    }
    public static String normalize(String raw) {
        if (raw == null || raw.isEmpty()) return "";
        NormalizationStep finalStep = new NormalizationStep(raw.toLowerCase().trim(), false)
                .apply(AuthorNormalizationUtils::removeDiacritics)
                .apply(AuthorNormalizationUtils::removeUnnecessaryCharacters)
                .apply(AuthorNormalizationUtils::range)
                .apply(AuthorNormalizationUtils::openRange)
                .apply(AuthorNormalizationUtils::closeRange)
                .apply(AuthorNormalizationUtils::extractActiveYears)
                .apply(AuthorNormalizationUtils::extractLifeYears)
                .apply(AuthorNormalizationUtils::yearOrEpoch)
                .apply(AuthorNormalizationUtils::sanitizeFinalOutput);
        return finalStep.value().replaceAll("\\s+", " ").trim();
    }

}
