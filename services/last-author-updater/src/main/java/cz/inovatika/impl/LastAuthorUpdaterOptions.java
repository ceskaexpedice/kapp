package cz.inovatika.impl;

public record LastAuthorUpdaterOptions(
        int rows,
        int batchSize,
        int limit,
        boolean dryRun,
        boolean verbose
) {
    private static final int DEFAULT_ROWS = 200;
    private static final int DEFAULT_BATCH_SIZE = 200;

    public static LastAuthorUpdaterOptions defaults() {
        return new LastAuthorUpdaterOptions(DEFAULT_ROWS, DEFAULT_BATCH_SIZE, 0, false, false);
    }

    public static LastAuthorUpdaterOptions fromArgs(String[] args) {
        LastAuthorUpdaterOptions defaults = defaults();
        int rows = defaults.rows();
        int batchSize = defaults.batchSize();
        int limit = defaults.limit();
        boolean dryRun = defaults.dryRun();
        boolean verbose = defaults.verbose();

        for (String arg : args) {
            if ("--dry-run".equals(arg)) {
                dryRun = true;
            } else if ("--verbose".equals(arg)) {
                verbose = true;
            } else if (arg.startsWith("--rows=")) {
                rows = parsePositiveInt(arg, "--rows=");
            } else if (arg.startsWith("--batch-size=")) {
                batchSize = parsePositiveInt(arg, "--batch-size=");
            } else if (arg.startsWith("--limit=")) {
                limit = parseNonNegativeInt(arg, "--limit=");
            } else {
                throw new IllegalArgumentException("Unknown argument: " + arg);
            }
        }

        return new LastAuthorUpdaterOptions(rows, batchSize, limit, dryRun, verbose);
    }

    private static int parsePositiveInt(String arg, String prefix) {
        int value = parseNonNegativeInt(arg, prefix);
        if (value <= 0) {
            throw new IllegalArgumentException(prefix + " must be greater than 0");
        }
        return value;
    }

    private static int parseNonNegativeInt(String arg, String prefix) {
        try {
            int value = Integer.parseInt(arg.substring(prefix.length()));
            if (value < 0) {
                throw new IllegalArgumentException(prefix + " must not be negative");
            }
            return value;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid number in argument: " + arg, e);
        }
    }
}
