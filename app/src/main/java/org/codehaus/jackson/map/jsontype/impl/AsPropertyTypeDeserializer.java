package org.codehaus.jackson.map.jsontype.impl;

import org.codehaus.jackson.JsonParser;
import org.codehaus.jackson.JsonProcessingException;
import org.codehaus.jackson.JsonToken;
import org.codehaus.jackson.annotate.JsonTypeInfo;
import org.codehaus.jackson.map.DeserializationContext;
import org.codehaus.jackson.map.JsonDeserializer;
import org.codehaus.jackson.map.jsontype.TypeIdResolver;
import org.codehaus.jackson.type.JavaType;
import org.codehaus.jackson.util.JsonParserSequence;
import org.codehaus.jackson.util.TokenBuffer;

import java.io.IOException;

/**
 * Type deserializer used with {@link JsonTypeInfo.As#PROPERTY}
 * inclusion mechanism.
 * Uses regular form (additional key/value entry before actual data)
 * when typed object is expressed as JSON Object; otherwise behaves similar to how
 * {@link JsonTypeInfo.As#WRAPPER_ARRAY} works.
 * Latter is used if JSON representation is polymorphic
 *
 * @author tatu
 * @since 1.5
 */
public class AsPropertyTypeDeserializer extends AsArrayTypeDeserializer {
    protected final String _propertyName;

    public AsPropertyTypeDeserializer(JavaType bt, TypeIdResolver idRes, String propName) {
        super(bt, idRes);
        _propertyName = propName;
    }

    @Override
    public JsonTypeInfo.As getTypeInclusion() {
        return JsonTypeInfo.As.PROPERTY;
    }

    @Override
    public String getPropertyName() {
        return _propertyName;
    }

    /**
     * This is the trickiest thing to handle, since property we are looking
     * for may be anywhere...
     */
    @Override
    public Object deserializeTypedFromObject(JsonParser jp, DeserializationContext ctxt)
            throws IOException, JsonProcessingException {
        // but first, sanity check to ensure we have START_OBJECT or FIELD_NAME
        JsonToken t = jp.getCurrentToken();
        if (t == JsonToken.START_OBJECT) {
            t = jp.nextToken();
        } else if (t != JsonToken.FIELD_NAME) {
            throw ctxt.wrongTokenException(jp, JsonToken.START_OBJECT,
                    "need JSON Object to contain As.PROPERTY type information (for class " + baseTypeName() + ")");
        }
        // Ok, let's try to find the property. But first, need token buffer...
        TokenBuffer tb = null;

        for (; t == JsonToken.FIELD_NAME; t = jp.nextToken()) {
            String name = jp.getCurrentName();
            jp.nextToken(); // to point to the value
            if (_propertyName.equals(name)) { // gotcha!
                JsonDeserializer<Object> deser = _findDeserializer(ctxt, jp.getText());
                // deserializer should take care of closing END_OBJECT as well
                if (tb != null) {
                    jp = JsonParserSequence.createFlattened(tb.asParser(jp), jp);
                }
                /* Must point to the next value; tb had no current, jp
                 * pointed to VALUE_STRING:
                 */
                jp.nextToken(); // to skip past String value
                // deserializer should take care of closing END_OBJECT as well
                return deser.deserialize(jp, ctxt);
            }
            if (tb == null) {
                tb = new TokenBuffer(null);
            }
            tb.writeFieldName(name);
            tb.copyCurrentStructure(jp);
        }
        // Error if we get here...
        throw ctxt.wrongTokenException(jp, JsonToken.FIELD_NAME,
                "missing property '" + _propertyName + "' that is to contain type id  (for class " + baseTypeName() + ")");
    }

    // These are fine from base class:
    //public Object deserializeTypedArray(JsonParser jp, DeserializationContext ctxt)
    //public Object deserializeTypedScalar(JsonParser jp, DeserializationContext ctxt)    
    //public Object deserializeTypedUnknown(JsonParser jp, DeserializationContext ctxt)    
}
