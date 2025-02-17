package org.codehaus.jackson.map.jsontype.impl;

import org.codehaus.jackson.JsonGenerator;
import org.codehaus.jackson.JsonProcessingException;
import org.codehaus.jackson.annotate.JsonTypeInfo;
import org.codehaus.jackson.map.jsontype.TypeIdResolver;

import java.io.IOException;

/**
 * Type serializer that will embed type information in an array,
 * as the first element, and actual value as the second element.
 *
 * @author tatu
 * @since 1.5
 */
public class AsArrayTypeSerializer
        extends TypeSerializerBase {
    public AsArrayTypeSerializer(TypeIdResolver idRes) {
        super(idRes);
    }

    @Override
    public JsonTypeInfo.As getTypeInclusion() {
        return JsonTypeInfo.As.WRAPPER_ARRAY;
    }

    @Override
    public void writeTypePrefixForObject(Object value, JsonGenerator jgen)
            throws IOException, JsonProcessingException {
        jgen.writeStartArray();
        jgen.writeString(_idResolver.idFromValue(value));
        jgen.writeStartObject();
    }

    @Override
    public void writeTypePrefixForArray(Object value, JsonGenerator jgen)
            throws IOException, JsonProcessingException {
        jgen.writeStartArray();
        jgen.writeString(_idResolver.idFromValue(value));
        jgen.writeStartArray();
    }

    @Override
    public void writeTypePrefixForScalar(Object value, JsonGenerator jgen)
            throws IOException, JsonProcessingException {
        // only need the wrapper array
        jgen.writeStartArray();
        jgen.writeString(_idResolver.idFromValue(value));
    }

    @Override
    public void writeTypeSuffixForObject(Object value, JsonGenerator jgen)
            throws IOException, JsonProcessingException {
        jgen.writeEndObject();
        jgen.writeEndArray();
    }

    @Override
    public void writeTypeSuffixForArray(Object value, JsonGenerator jgen)
            throws IOException, JsonProcessingException {
        // wrapper array first, and then array caller needs to close
        jgen.writeEndArray();
        jgen.writeEndArray();
    }

    @Override
    public void writeTypeSuffixForScalar(Object value, JsonGenerator jgen)
            throws IOException, JsonProcessingException {
        // just the wrapper array to close
        jgen.writeEndArray();
    }
}
