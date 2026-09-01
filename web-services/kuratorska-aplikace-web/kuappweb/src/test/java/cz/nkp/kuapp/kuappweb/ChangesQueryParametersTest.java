package cz.nkp.kuapp.kuappweb;

import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ChangesQueryParametersTest {

    @Test
    void acceptsSupportedQueryParameters() {
        assertDoesNotThrow(() -> ChangesQueryParameters.validate(
                Set.of("from", "to", "licenses", "source")));
    }

    @Test
    void rejectsUnknownQueryParameters() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> ChangesQueryParameters.validate(
                        Set.of("license", "startDate", "unexpected")));

        assertEquals("Unknown query parameter(s): license, startDate, unexpected. "
                + "Allowed parameters are: from, licenses, source, to.",
                exception.getMessage());
    }
}
