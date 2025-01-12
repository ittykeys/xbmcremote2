package org.codehaus.jackson.impl;

import org.codehaus.jackson.JsonLocation;
import org.codehaus.jackson.JsonStreamContext;
import org.codehaus.jackson.util.CharTypes;

/**
 * Extension of {@link JsonStreamContext}, which implements
 * core methods needed, and also exposes
 * more complete API to parser implementation classes.
 */
public final class JsonReadContext
        extends JsonStreamContext {
    // // // Configuration

    protected final JsonReadContext _parent;

    // // // Location information (minus source reference)

    //long mTotalChars;

    protected int _lineNr;
    protected int _columnNr;

    protected String _currentName;

    /*
    //////////////////////////////////////////////////
    // Simple instance reuse slots; speeds up things
    // a bit (10-15%) for docs with lots of small
    // arrays/objects (for which allocation was
    // visible in profile stack frames)
    //////////////////////////////////////////////////
     */

    JsonReadContext _child = null;

    /*
    //////////////////////////////////////////////////
    // Instance construction, reuse
    //////////////////////////////////////////////////
     */

    public JsonReadContext(JsonReadContext parent,
                           int type, int lineNr, int colNr) {
        super(type);
        _parent = parent;
        _lineNr = lineNr;
        _columnNr = colNr;
    }

    public static JsonReadContext createRootContext(int lineNr, int colNr) {
        return new JsonReadContext(null, TYPE_ROOT, lineNr, colNr);
    }

    // // // Factory methods

    protected final void reset(int type, int lineNr, int colNr) {
        _type = type;
        _index = -1;
        _lineNr = lineNr;
        _columnNr = colNr;
        _currentName = null;
    }

    public final JsonReadContext createChildArrayContext(int lineNr, int colNr) {
        JsonReadContext ctxt = _child;
        if (ctxt == null) {
            return (_child = new JsonReadContext(this, TYPE_ARRAY, lineNr, colNr));
        }
        ctxt.reset(TYPE_ARRAY, lineNr, colNr);
        return ctxt;
    }

    public final JsonReadContext createChildObjectContext(int lineNr, int colNr) {
        JsonReadContext ctxt = _child;
        if (ctxt == null) {
            return (_child = new JsonReadContext(this, TYPE_OBJECT, lineNr, colNr));
        }
        ctxt.reset(TYPE_OBJECT, lineNr, colNr);
        return ctxt;
    }

    /*
    //////////////////////////////////////////////////
    // Abstract method implementation
    //////////////////////////////////////////////////
     */

    public final String getCurrentName() {
        return _currentName;
    }

    public void setCurrentName(String name) {
        _currentName = name;
    }

    /*
    //////////////////////////////////////////////////
    // Extended API
    //////////////////////////////////////////////////
     */

    public final JsonReadContext getParent() {
        return _parent;
    }

    /*
    //////////////////////////////////////////////////
    // State changes
    //////////////////////////////////////////////////
     */

    /**
     * @return Location pointing to the point where the context
     * start marker was found
     */
    public final JsonLocation getStartLocation(Object srcRef) {
        /* We don't keep track of offsets at this level (only
         * reader does)
         */
        long totalChars = -1L;

        return new JsonLocation(srcRef, totalChars, _lineNr, _columnNr);
    }

    public final boolean expectComma() {
        /* Assumption here is that we will be getting a value (at least
         * before calling this method again), and
         * so will auto-increment index to avoid having to do another call
         */
        int ix = ++_index; // starts from -1
        return (_type != TYPE_ROOT && ix > 0);
    }

    /*
    //////////////////////////////////////////////////
    // Overridden standard methods
    //////////////////////////////////////////////////
    */

    /**
     * Overridden to provide developer readable "JsonPath" representation
     * of the context.
     */
    public final String toString() {
        StringBuilder sb = new StringBuilder(64);
        switch (_type) {
            case TYPE_ROOT:
                sb.append("/");
                break;
            case TYPE_ARRAY:
                sb.append('[');
                sb.append(getCurrentIndex());
                sb.append(']');
                break;
            case TYPE_OBJECT:
                sb.append('{');
                if (_currentName != null) {
                    sb.append('"');
                    CharTypes.appendQuoted(sb, _currentName);
                    sb.append('"');
                } else {
                    sb.append('?');
                }
                sb.append(']');
                break;
        }
        return sb.toString();
    }
}
