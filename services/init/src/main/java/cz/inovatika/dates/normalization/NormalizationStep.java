package cz.inovatika.dates.normalization;

import java.util.function.UnaryOperator;

public  record NormalizationStep(String value, boolean isDone) {
    public NormalizationStep apply(UnaryOperator<NormalizationStep> operation) {
        if (this.isDone) {
            return this;
        }
        return operation.apply(this);
    }
}
