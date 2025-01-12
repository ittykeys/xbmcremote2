package org.codehaus.jackson.map;

import org.codehaus.jackson.JsonGenerator;
import org.codehaus.jackson.JsonProcessingException;

import java.io.IOException;

/**
 * Interface that is to replace {@link JsonSerializable} to
 * allow for dynamic type information embedding.
 *
 * @author tatu
 * @since 1.5
 */
@SuppressWarnings("deprecation")
public interface JsonSerializableWithType
        extends JsonSerializable {
    public void serializeWithType(JsonGenerator jgen, SerializerProvider provider,
                                  TypeSerializer typeSer)
            throws IOException, JsonProcessingException;
}
