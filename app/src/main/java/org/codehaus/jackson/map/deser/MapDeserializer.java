package org.codehaus.jackson.map.deser;

import org.codehaus.jackson.JsonParser;
import org.codehaus.jackson.JsonProcessingException;
import org.codehaus.jackson.JsonToken;
import org.codehaus.jackson.map.DeserializationConfig;
import org.codehaus.jackson.map.DeserializationContext;
import org.codehaus.jackson.map.DeserializerProvider;
import org.codehaus.jackson.map.JsonDeserializer;
import org.codehaus.jackson.map.JsonMappingException;
import org.codehaus.jackson.map.KeyDeserializer;
import org.codehaus.jackson.map.ResolvableDeserializer;
import org.codehaus.jackson.map.TypeDeserializer;
import org.codehaus.jackson.map.util.ArrayBuilders;
import org.codehaus.jackson.type.JavaType;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

/**
 * Basic serializer that can take Json "Object" structure and
 * construct a {@link java.util.Map} instance, with typed contents.
 * <p>
 * Note: for untyped content (one indicated by passing Object.class
 * as the type), {@link UntypedObjectDeserializer} is used instead.
 * It can also construct {@link java.util.Map}s, but not with specific
 * POJO types, only other containers and primitives/wrappers.
 */
public class MapDeserializer
        extends StdDeserializer<Map<Object, Object>>
        implements ResolvableDeserializer {
    // // Configuration: typing, deserializers

    final JavaType _mapType;

    /**
     * Key deserializer used, if not null. If null, String from json
     * content is used as is.
     */
    final KeyDeserializer _keyDeserializer;

    /**
     * Value deserializer.
     */
    final JsonDeserializer<Object> _valueDeserializer;

    /**
     * If value instances have polymorphic type information, this
     * is the type deserializer that can handle it
     */
    final TypeDeserializer _valueTypeDeserializer;

    // // Instance construction settings:

    final Constructor<Map<Object, Object>> _defaultCtor;

    /**
     * If the Map is to be instantiated using non-default constructor
     * or factory method
     * that takes one or more named properties as argument(s),
     * this creator is used for instantiation.
     */
    protected Creator.PropertyBased _propertyBasedCreator;

    // // Any properties to ignore if seen?

    protected HashSet<String> _ignorableProperties;
    
    /*
    ////////////////////////////////////////////////////////////
    // Life-cycle
    ////////////////////////////////////////////////////////////
     */

    public MapDeserializer(JavaType mapType, Constructor<Map<Object, Object>> defCtor,
                           KeyDeserializer keyDeser, JsonDeserializer<Object> valueDeser,
                           TypeDeserializer valueTypeDeser) {
        super(Map.class);
        _mapType = mapType;
        _defaultCtor = defCtor;
        _keyDeserializer = keyDeser;
        _valueDeserializer = valueDeser;
        _valueTypeDeserializer = valueTypeDeser;
    }

    /**
     * Method called to add constructor and/or factory method based
     * creators to be used with Map, instead of default constructor.
     */
    public void setCreators(CreatorContainer creators) {
        _propertyBasedCreator = creators.propertyBasedCreator();
    }

    public void setIgnorableProperties(String[] ignorable) {
        _ignorableProperties = (ignorable == null || ignorable.length == 0) ?
                null : ArrayBuilders.arrayToSet(ignorable);
    }

    /*
    /////////////////////////////////////////////////////////
    // Validation, post-processing
    /////////////////////////////////////////////////////////
     */

    /**
     * Method called to finalize setup of this deserializer,
     * after deserializer itself has been registered. This
     * is needed to handle recursive and transitive dependencies.
     */
    public void resolve(DeserializationConfig config, DeserializerProvider provider)
            throws JsonMappingException {
        // just need to worry about property-based one
        if (_propertyBasedCreator != null) {
            // Need to / should not create separate
            HashMap<JavaType, JsonDeserializer<Object>> seen = new HashMap<JavaType, JsonDeserializer<Object>>();
            for (SettableBeanProperty prop : _propertyBasedCreator.properties()) {
                prop.setValueDeserializer(findDeserializer(config, provider, prop.getType(), prop.getPropertyName(), seen));
            }
        }
    }

    /*
    ////////////////////////////////////////////////////////////
    // Deserializer API
    ////////////////////////////////////////////////////////////
     */

    @Override
    public Map<Object, Object> deserialize(JsonParser jp, DeserializationContext ctxt)
            throws IOException, JsonProcessingException {
        // Ok: must point to START_OBJECT, or FIELD_NAME
        JsonToken t = jp.getCurrentToken();
        if (t != JsonToken.START_OBJECT && t != JsonToken.FIELD_NAME) {
            throw ctxt.mappingException(getMapClass());
        }
        if (_propertyBasedCreator != null) {
            return _deserializeUsingCreator(jp, ctxt);
        }
        Map<Object, Object> result;
        if (_defaultCtor == null) {
            throw ctxt.instantiationException(getMapClass(), "No default constructor found");
        }
        try {
            result = _defaultCtor.newInstance();
        } catch (Exception e) {
            throw ctxt.instantiationException(getMapClass(), e);
        }
        _readAndBind(jp, ctxt, result);
        return result;
    }

    @Override
    public Map<Object, Object> deserialize(JsonParser jp, DeserializationContext ctxt,
                                           Map<Object, Object> result)
            throws IOException, JsonProcessingException {
        // Ok: must point to START_OBJECT or FIELD_NAME
        JsonToken t = jp.getCurrentToken();
        if (t != JsonToken.START_OBJECT && t != JsonToken.FIELD_NAME) {
            throw ctxt.mappingException(getMapClass());
        }
        _readAndBind(jp, ctxt, result);
        return result;
    }

    @Override
    public Object deserializeWithType(JsonParser jp, DeserializationContext ctxt,
                                      TypeDeserializer typeDeserializer)
            throws IOException, JsonProcessingException {
        // In future could check current token... for now this should be enough:
        return typeDeserializer.deserializeTypedFromObject(jp, ctxt);
    }
    
    /*
    /////////////////////////////////////////////////////////
    // Other public accessors
    /////////////////////////////////////////////////////////
     */

    @SuppressWarnings("unchecked")
    public final Class<?> getMapClass() {
        return (Class<Map<Object, Object>>) _mapType.getRawClass();
    }

    @Override
    public JavaType getValueType() {
        return _mapType;
    }

    /*
     *************************************************
     * Internal methods
     *************************************************
     */

    protected final void _readAndBind(JsonParser jp, DeserializationContext ctxt,
                                      Map<Object, Object> result)
            throws IOException, JsonProcessingException {
        JsonToken t = jp.getCurrentToken();
        if (t == JsonToken.START_OBJECT) {
            t = jp.nextToken();
        }
        final KeyDeserializer keyDes = _keyDeserializer;
        final JsonDeserializer<Object> valueDes = _valueDeserializer;
        final TypeDeserializer typeDeser = _valueTypeDeserializer;
        for (; t == JsonToken.FIELD_NAME; t = jp.nextToken()) {
            // Must point to field name
            String fieldName = jp.getCurrentName();
            Object key = (keyDes == null) ? fieldName : keyDes.deserializeKey(fieldName, ctxt);
            // And then the value...
            t = jp.nextToken();
            if (_ignorableProperties != null && _ignorableProperties.contains(fieldName)) {
                jp.skipChildren();
                continue;
            }
            // Note: must handle null explicitly here; value deserializers won't
            Object value;
            if (t == JsonToken.VALUE_NULL) {
                value = null;
            } else if (typeDeser == null) {
                value = valueDes.deserialize(jp, ctxt);
            } else {
                value = valueDes.deserializeWithType(jp, ctxt, typeDeser);
            }
            /* !!! 23-Dec-2008, tatu: should there be an option to verify
             *   that there are no duplicate field names? (and/or what
             *   to do, keep-first or keep-last)
             */
            result.put(key, value);
        }
    }

    @SuppressWarnings("unchecked")
    public Map<Object, Object> _deserializeUsingCreator(JsonParser jp, DeserializationContext ctxt)
            throws IOException, JsonProcessingException {
        final Creator.PropertyBased creator = _propertyBasedCreator;
        PropertyValueBuffer buffer = creator.startBuilding(jp, ctxt);

        JsonToken t = jp.getCurrentToken();
        if (t == JsonToken.START_OBJECT) {
            t = jp.nextToken();
        }
        final JsonDeserializer<Object> valueDes = _valueDeserializer;
        final TypeDeserializer typeDeser = _valueTypeDeserializer;
        for (; t == JsonToken.FIELD_NAME; t = jp.nextToken()) {
            String propName = jp.getCurrentName();
            t = jp.nextToken(); // to get to value
            if (_ignorableProperties != null && _ignorableProperties.contains(propName)) {
                jp.skipChildren(); // and skip it (in case of array/object)
                continue;
            }
            // creator property?
            SettableBeanProperty prop = creator.findCreatorProperty(propName);
            if (prop != null) {
                // Last property to set?
                Object value = prop.deserialize(jp, ctxt);
                if (buffer.assignParameter(prop.getCreatorIndex(), value)) {
                    jp.nextToken();
                    Map<Object, Object> result = (Map<Object, Object>) creator.build(buffer);
                    _readAndBind(jp, ctxt, result);
                    return result;
                }
                continue;
            }
            // other property? needs buffering
            String fieldName = jp.getCurrentName();
            Object key = (_keyDeserializer == null) ? fieldName : _keyDeserializer.deserializeKey(fieldName, ctxt);
            Object value;
            if (t == JsonToken.VALUE_NULL) {
                value = null;
            } else if (typeDeser == null) {
                value = valueDes.deserialize(jp, ctxt);
            } else {
                value = valueDes.deserializeWithType(jp, ctxt, typeDeser);
            }
            buffer.bufferMapProperty(key, value);
        }
        // end of JSON object?
        // if so, can just construct and leave...
        return (Map<Object, Object>) creator.build(buffer);
    }
}
