package cz.inovatika.model;

import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import java.io.IOException;
import java.time.Instant;

/**
 * A custom {@link com.google.gson.TypeAdapter} for handling the serialization
 * and deserialization of {@link java.time.Instant} objects using the Gson library.
 *
 * <p>This adapter ensures that {@code Instant} values are represented in JSON
 * using their standard ISO 8601 string format (e.g., "YYYY-MM-DDThh:mm:ss.sssZ"),
 * which is the default output of {@link Instant#toString()}.
 *
 * <p>It correctly handles {@code null} values for both reading and writing.
 */
public class InstantTypeAdapter extends TypeAdapter<Instant> {

    /**
     * Serializes a {@link java.time.Instant} object into a JSON string.
     *
     * <p>If the {@code Instant} value is {@code null}, it writes a JSON {@code null}.
     * Otherwise, it writes the Instant in its standard ISO 8601 string representation.
     *
     * @param out The JSON writer to which the value will be written.
     * @param value The {@code Instant} value to serialize.
     * @throws IOException If an error occurs during writing to the JSON stream.
     */
    @Override
    public void write(JsonWriter out, Instant value) throws IOException {
        if (value == null) {
            out.nullValue();
        } else {
            out.value(value.toString());
        }
    }

    /**
     * Deserializes a JSON string into a {@link java.time.Instant} object.
     *
     * <p>If the incoming JSON token is {@code null}, it consumes the null and returns {@code null}.
     * Otherwise, it expects a string conforming to the ISO 8601 format and uses
     * {@link Instant#parse(CharSequence)} to create the {@code Instant} object.
     *
     * @param in The JSON reader from which the value will be read.
     * @return The deserialized {@code Instant} object, or {@code null} if the JSON value was null.
     * @throws IOException If an error occurs during reading from the JSON stream or if the string format is invalid.
     */
    @Override
    public Instant read(JsonReader in) throws IOException {
        if (in.peek() == com.google.gson.stream.JsonToken.NULL) {
            in.nextNull();
            return null;
        }
        return Instant.parse(in.nextString());
    }
}