package org.codehaus.jackson.map.deser;

import org.codehaus.jackson.JsonParser;
import org.codehaus.jackson.map.DeserializationContext;

/**
 * Simple container used for temporarily buffering a set of
 * <code>PropertyValue</code>s.
 * Using during construction of beans (and Maps) that use Creators,
 * and hence need buffering before instance (that will have properties
 * to assign values to) is constructed.
 */
public final class PropertyValueBuffer {
    final JsonParser _parser;
    final DeserializationContext _context;

    /**
     * Buffer used for storing creator parameters for constructing
     * instance
     */
    final Object[] _creatorParameters;

    /**
     * Number of creator parameters we are still missing.
     * <p>
     * NOTE: assumes there are no duplicates, for now.
     */
    private int _paramsNeeded;

    /**
     * If we get non-creator parameters before or between
     * creator parameters, those need to be buffered. Buffer
     * is just a simple linked list
     */
    private PropertyValue _buffered;

    public PropertyValueBuffer(JsonParser jp, DeserializationContext ctxt,
                               int paramCount) {
        _parser = jp;
        _context = ctxt;
        _paramsNeeded = paramCount;
        _creatorParameters = new Object[paramCount];
    }

    protected final Object[] getParameters() {
        return _creatorParameters;
    }

    protected PropertyValue buffered() {
        return _buffered;
    }

    /**
     * @return True if we have received all creator parameters
     */
    public boolean assignParameter(int index, Object value) {
        _creatorParameters[index] = value;
        return --_paramsNeeded <= 0;
    }

    public void bufferProperty(SettableBeanProperty prop, Object value) {
        _buffered = new PropertyValue.Regular(_buffered, value, prop);
    }

    public void bufferAnyProperty(SettableAnyProperty prop, String propName, Object value) {
        _buffered = new PropertyValue.Any(_buffered, value, prop, propName);
    }

    public void bufferMapProperty(Object key, Object value) {
        _buffered = new PropertyValue.Map(_buffered, value, key);
    }
}

