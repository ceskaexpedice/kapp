package cz.nkp.kuapp.kuappweb;

import java.util.Set;
import java.util.TreeSet;

final class ChangesQueryParameters {

    private static final Set<String> ALLOWED
            = Set.of("from", "to", "licenses", "source");

    private ChangesQueryParameters() {
    }

    static void validate(Set<String> parameterNames) {
        Set<String> unknownParameters = new TreeSet<>(parameterNames);
        unknownParameters.removeAll(ALLOWED);
        if (!unknownParameters.isEmpty()) {
            throw new IllegalArgumentException("Unknown query parameter(s): "
                    + String.join(", ", unknownParameters) + ". Allowed parameters are: "
                    + String.join(", ", new TreeSet<>(ALLOWED)) + ".");
        }
    }
}
