package org.codehaus.jackson.map.ser;

import org.codehaus.jackson.JsonGenerationException;
import org.codehaus.jackson.JsonGenerator;
import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.map.JsonMappingException;
import org.codehaus.jackson.map.JsonSerializer;
import org.codehaus.jackson.map.ResolvableSerializer;
import org.codehaus.jackson.map.SerializerProvider;
import org.codehaus.jackson.map.TypeSerializer;
import org.codehaus.jackson.map.type.TypeFactory;
import org.codehaus.jackson.map.util.EnumValues;
import org.codehaus.jackson.node.JsonNodeFactory;
import org.codehaus.jackson.node.ObjectNode;
import org.codehaus.jackson.schema.JsonSchema;
import org.codehaus.jackson.schema.SchemaAware;
import org.codehaus.jackson.type.JavaType;

import java.io.IOException;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.EnumMap;
import java.util.Map;

/**
 * Specialized serializer for {@link EnumMap}s. Somewhat tricky to
 * implement because actual Enum value type may not be available;
 * and if not, it can only be gotten from actual instance.
 */
public class EnumMapSerializer
        extends ContainerSerializerBase<EnumMap<? extends Enum<?>, ?>>
        implements ResolvableSerializer {
    protected final boolean _staticTyping;

    /**
     * If we know enumeration used as key, this will contain
     * value set to use for serialization
     */
    protected final EnumValues _keyEnums;

    protected final JavaType _valueType;
    /**
     * Type serializer used for values, if any.
     */
    protected final TypeSerializer _valueTypeSerializer;
    /**
     * Value serializer to use, if it can be statically determined
     *
     * @since 1.5
     */
    protected JsonSerializer<Object> _valueSerializer;

    public EnumMapSerializer(JavaType valueType, boolean staticTyping, EnumValues keyEnums,
                             TypeSerializer vts) {
        super(EnumMap.class, false);
        _staticTyping = staticTyping || (valueType != null && valueType.isFinal());
        _valueType = valueType;
        _keyEnums = keyEnums;
        _valueTypeSerializer = vts;
    }

    @Override
    public ContainerSerializerBase<?> _withValueTypeSerializer(TypeSerializer vts) {
        return new EnumMapSerializer(_valueType, _staticTyping, _keyEnums, vts);
    }

    @Override
    public void serialize(EnumMap<? extends Enum<?>, ?> value, JsonGenerator jgen, SerializerProvider provider)
            throws IOException, JsonGenerationException {
        jgen.writeStartObject();
        if (!value.isEmpty()) {
            serializeContents(value, jgen, provider);
        }
        jgen.writeEndObject();
    }

    @Override
    public void serializeWithType(EnumMap<? extends Enum<?>, ?> value, JsonGenerator jgen, SerializerProvider provider,
                                  TypeSerializer typeSer)
            throws IOException, JsonGenerationException {
        typeSer.writeTypePrefixForObject(value, jgen);
        if (!value.isEmpty()) {
            serializeContents(value, jgen, provider);
        }
        typeSer.writeTypeSuffixForObject(value, jgen);
    }

    protected void serializeContents(EnumMap<? extends Enum<?>, ?> value, JsonGenerator jgen, SerializerProvider provider)
            throws IOException, JsonGenerationException {
        if (_valueSerializer != null) {
            serializeContentsUsing(value, jgen, provider, _valueSerializer);
            return;
        }
        JsonSerializer<Object> prevSerializer = null;
        Class<?> prevClass = null;
        EnumValues keyEnums = _keyEnums;

        for (Map.Entry<? extends Enum<?>, ?> entry : value.entrySet()) {
            // First, serialize key
            Enum<?> key = entry.getKey();
            if (keyEnums == null) {
                /* 15-Oct-2009, tatu: This is clumsy, but still the simplest efficient
                 * way to do it currently, as Serializers get cached. (it does assume we'll always use
                 * default serializer tho -- so ideally code should be rewritten)
                 */
                // ... and lovely two-step casting process too...
                SerializerBase<?> ser = (SerializerBase<?>) provider.findValueSerializer(key.getDeclaringClass());
                keyEnums = ((EnumSerializer) ser).getEnumValues();
            }
            jgen.writeFieldName(keyEnums.valueFor(key));
            // And then value
            Object valueElem = entry.getValue();
            if (valueElem == null) {
                provider.getNullValueSerializer().serialize(null, jgen, provider);
            } else {
                Class<?> cc = valueElem.getClass();
                JsonSerializer<Object> currSerializer;
                if (cc == prevClass) {
                    currSerializer = prevSerializer;
                } else {
                    currSerializer = provider.findValueSerializer(cc);
                    prevSerializer = currSerializer;
                    prevClass = cc;
                }
                try {
                    currSerializer.serialize(valueElem, jgen, provider);
                } catch (Exception e) {
                    // [JACKSON-55] Need to add reference information
                    wrapAndThrow(e, value, entry.getKey().name());
                }
            }
        }
    }

    protected void serializeContentsUsing(EnumMap<? extends Enum<?>, ?> value, JsonGenerator jgen, SerializerProvider provider,
                                          JsonSerializer<Object> valueSer)
            throws IOException, JsonGenerationException {
        EnumValues keyEnums = _keyEnums;
        for (Map.Entry<? extends Enum<?>, ?> entry : value.entrySet()) {
            Enum<?> key = entry.getKey();
            if (keyEnums == null) {
                // clumsy, but has to do for now:
                SerializerBase<?> ser = (SerializerBase<?>) provider.findValueSerializer(key.getDeclaringClass());
                keyEnums = ((EnumSerializer) ser).getEnumValues();
            }
            jgen.writeFieldName(keyEnums.valueFor(key));
            Object valueElem = entry.getValue();
            if (valueElem == null) {
                provider.getNullValueSerializer().serialize(null, jgen, provider);
            } else {
                try {
                    valueSer.serialize(valueElem, jgen, provider);
                } catch (Exception e) {
                    wrapAndThrow(e, value, entry.getKey().name());
                }
            }
        }
    }

    //@Override
    public void resolve(SerializerProvider provider)
            throws JsonMappingException {
        if (_staticTyping) {
            _valueSerializer = provider.findValueSerializer(_valueType);
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public JsonNode getSchema(SerializerProvider provider, Type typeHint)
            throws JsonMappingException {
        ObjectNode o = createSchemaNode("object", true);
        if (typeHint instanceof ParameterizedType) {
            Type[] typeArgs = ((ParameterizedType) typeHint).getActualTypeArguments();
            if (typeArgs.length == 2) {
                JavaType enumType = TypeFactory.type(typeArgs[0]);
                JavaType valueType = TypeFactory.type(typeArgs[1]);
                ObjectNode propsNode = JsonNodeFactory.instance.objectNode();
                Class<Enum<?>> enumClass = (Class<Enum<?>>) enumType.getRawClass();
                for (Enum<?> enumValue : enumClass.getEnumConstants()) {
                    JsonSerializer<Object> ser = provider.findValueSerializer(valueType.getRawClass());
                    JsonNode schemaNode = (ser instanceof SchemaAware) ?
                            ((SchemaAware) ser).getSchema(provider, null) :
                            JsonSchema.getDefaultSchemaNode();
                    propsNode.put(provider.getConfig().getAnnotationIntrospector().findEnumValue((Enum<?>) enumValue), schemaNode);
                }
                o.put("properties", propsNode);
            }
        }
        return o;
    }
}
