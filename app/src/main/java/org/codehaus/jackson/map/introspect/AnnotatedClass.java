package org.codehaus.jackson.map.introspect;

import org.codehaus.jackson.map.AnnotationIntrospector;
import org.codehaus.jackson.map.ClassIntrospector.MixInResolver;
import org.codehaus.jackson.map.util.ArrayBuilders;
import org.codehaus.jackson.map.util.ClassUtil;

import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class AnnotatedClass
        extends Annotated {
    /*
    /******************************************************
    /* Configuration
    /******************************************************
     */

    /**
     * Class for which annotations apply, and that owns other
     * components (constructors, methods)
     */
    final Class<?> _class;

    /**
     * Ordered set of super classes and interfaces of the
     * class itself: included in order of precedence
     */
    final Collection<Class<?>> _superTypes;

    /**
     * Filter used to determine which annotations to gather; used
     * to optimize things so that unnecessary annotations are
     * ignored.
     */
    final AnnotationIntrospector _annotationIntrospector;

    /**
     * Object that knows mapping of mix-in classes (ones that contain
     * annotations to add) with their target classes (ones that
     * get these additional annotations "mixed in").
     */
    final MixInResolver _mixInResolver;

    /**
     * Primary mix-in class; one to use for the annotated class
     * itself. Can be null.
     */
    final Class<?> _primaryMixIn;

    /*
    /******************************************************
    /* Gathered information
    /******************************************************
     */

    /**
     * Combined list of Jackson annotations that the class has,
     * including inheritable ones from super classes and interfaces
     */
    AnnotationMap _classAnnotations;

    /**
     * Default constructor of the annotated class, if it has one.
     */
    AnnotatedConstructor _defaultConstructor;

    /**
     * Single argument constructors the class has, if any.
     */
    List<AnnotatedConstructor> _constructors;

    /**
     * Single argument static methods that might be usable
     * as factory methods
     */
    List<AnnotatedMethod> _creatorMethods;

    /**
     * Member methods of interest; for now ones with 0 or 1 arguments
     * (just optimization, since others won't be used now)
     */
    AnnotatedMethodMap _memberMethods;

    /**
     * Member fields of interest: ones that are either public,
     * or have at least one annotation.
     */
    List<AnnotatedField> _fields;

    // // // Lists of explicitly ignored entries (optionally populated)

    /**
     * Optionally populated list that contains member methods that were
     * excluded from applicable methods due to explicit ignore annotation
     */
    List<AnnotatedMethod> _ignoredMethods;

    /**
     * Optionally populated list that contains fields that were
     * excluded from applicable fields due to explicit ignore annotation
     */
    List<AnnotatedField> _ignoredFields;
    
    /*
    /******************************************************
    /* Life-cycle
    /******************************************************
     */

    /**
     * Constructor will not do any initializations, to allow for
     * configuring instances differently depending on use cases
     */
    private AnnotatedClass(Class<?> cls, List<Class<?>> superTypes,
                           AnnotationIntrospector aintr,
                           MixInResolver mir) {
        _class = cls;
        _superTypes = superTypes;
        _annotationIntrospector = aintr;
        _mixInResolver = mir;
        _primaryMixIn = (_mixInResolver == null) ? null
                : _mixInResolver.findMixInClassFor(_class);
    }

    /**
     * Factory method that instantiates an instance. Returned instance
     * will only be initialized with class annotations, but not with
     * any method information.
     */
    public static AnnotatedClass construct(Class<?> cls,
                                           AnnotationIntrospector aintr,
                                           MixInResolver mir) {
        List<Class<?>> st = ClassUtil.findSuperTypes(cls, null);
        AnnotatedClass ac = new AnnotatedClass(cls, st, aintr, mir);
        ac.resolveClassAnnotations();
        return ac;
    }

    /**
     * Method similar to {@link #construct}, but that will NOT include
     * information from supertypes; only class itself and any direct
     * mix-ins it may have.
     */
    public static AnnotatedClass constructWithoutSuperTypes(Class<?> cls,
                                                            AnnotationIntrospector aintr,
                                                            MixInResolver mir) {
        List<Class<?>> empty = Collections.emptyList();
        AnnotatedClass ac = new AnnotatedClass(cls, empty, aintr, mir);
        ac.resolveClassAnnotations();
        return ac;
    }
    
    /*
    /******************************************************
    /* Annotated impl 
    /******************************************************
     */

    public Class<?> getAnnotated() {
        return _class;
    }

    public int getModifiers() {
        return _class.getModifiers();
    }

    public String getName() {
        return _class.getName();
    }

    public <A extends Annotation> A getAnnotation(Class<A> acls) {
        if (_classAnnotations == null) {
            return null;
        }
        return _classAnnotations.get(acls);
    }

    public Type getGenericType() {
        return _class;
    }

    public Class<?> getRawType() {
        return _class;
    }
    
    /*
    /******************************************************
    /* Public API, generic accessors
    /******************************************************
     */

    public boolean hasAnnotations() {
        return _classAnnotations.size() > 0;
    }

    public AnnotatedConstructor getDefaultConstructor() {
        return _defaultConstructor;
    }

    public List<AnnotatedConstructor> getConstructors() {
        if (_constructors == null) {
            return Collections.emptyList();
        }
        return _constructors;
    }

    public List<AnnotatedMethod> getStaticMethods() {
        if (_creatorMethods == null) {
            return Collections.emptyList();
        }
        return _creatorMethods;
    }

    public Iterable<AnnotatedMethod> memberMethods() {
        return _memberMethods;
    }

    public Iterable<AnnotatedMethod> ignoredMemberMethods() {
        if (_ignoredMethods == null) {
            List<AnnotatedMethod> l = Collections.emptyList();
            return l;
        }
        return _ignoredMethods;
    }

    public int getMemberMethodCount() {
        return _memberMethods.size();
    }

    public AnnotatedMethod findMethod(String name, Class<?>[] paramTypes) {
        return _memberMethods.find(name, paramTypes);
    }

    public int getFieldCount() {
        return (_fields == null) ? 0 : _fields.size();
    }

    public Iterable<AnnotatedField> fields() {
        if (_fields == null) {
            List<AnnotatedField> l = Collections.emptyList();
            return l;
        }
        return _fields;
    }

    public Iterable<AnnotatedField> ignoredFields() {
        if (_ignoredFields == null) {
            List<AnnotatedField> l = Collections.emptyList();
            return l;
        }
        return _ignoredFields;
    }
    
    /*
    /******************************************************
    /* Methods for resolving class annotations
    /* (resolution consisting of inheritance, overrides,
    /* and injection of mix-ins as necessary)
    /******************************************************
     */

    /**
     * Initialization method that will recursively collect Jackson
     * annotations for this class and all super classes and
     * interfaces.
     * <p>
     * Starting with 1.2, it will also apply mix-in annotations,
     * as per [JACKSON-76]
     */
    protected void resolveClassAnnotations() {
        _classAnnotations = new AnnotationMap();
        // add mix-in annotations first (overrides)
        if (_primaryMixIn != null) {
            _addClassMixIns(_classAnnotations, _class, _primaryMixIn);
        }
        // first, annotations from the class itself:
        for (Annotation a : _class.getDeclaredAnnotations()) {
            if (_annotationIntrospector.isHandled(a)) {
                _classAnnotations.addIfNotPresent(a);
            }
        }

        // and then from super types
        for (Class<?> cls : _superTypes) {
            // and mix mix-in annotations in-between
            _addClassMixIns(_classAnnotations, cls);
            for (Annotation a : cls.getDeclaredAnnotations()) {
                if (_annotationIntrospector.isHandled(a)) {
                    _classAnnotations.addIfNotPresent(a);
                }
            }
        }

        /* and finally... any annotations there might be for plain
         * old Object.class: separate because for all other purposes
         * it is just ignored (not included in super types)
         */
        /* 12-Jul-2009, tatu: Should this be done for interfaces too?
         *   For now, yes, seems useful for some cases, and not harmful
         *   for any?
         */
        _addClassMixIns(_classAnnotations, Object.class);
    }

    /**
     * Helper method for adding any mix-in annotations specified
     * class might have.
     */
    protected void _addClassMixIns(AnnotationMap annotations, Class<?> toMask) {
        if (_mixInResolver != null) {
            _addClassMixIns(annotations, toMask, _mixInResolver.findMixInClassFor(toMask));
        }
    }

    protected void _addClassMixIns(AnnotationMap annotations, Class<?> toMask,
                                   Class<?> mixin) {
        if (mixin == null) {
            return;
        }
        // Ok, first: annotations from mix-in class itself:
        for (Annotation a : mixin.getDeclaredAnnotations()) {
            if (_annotationIntrospector.isHandled(a)) {
                annotations.addIfNotPresent(a);
            }
        }
        /* And then from its supertypes, if any. But note that we will
         *  only consider super-types up until reaching the masked
         * class (if found); this because often mix-in class
         * is a sub-class (for convenience reasons). And if so, we
         * absolutely must NOT include super types of masked class,
         * as that would inverse precedence of annotations.
         */
        for (Class<?> parent : ClassUtil.findSuperTypes(mixin, toMask)) {
            for (Annotation a : parent.getDeclaredAnnotations()) {
                if (_annotationIntrospector.isHandled(a)) {
                    annotations.addIfNotPresent(a);
                }
            }
        }
    }

    /*
    /**************************************************************
    /* Methods for populating creator (ctor, factory) information
    /**************************************************************
     */

    /**
     * Initialization method that will find out all constructors
     * and potential static factory methods the class has.
     * <p>
     * Starting with 1.2, it will also apply mix-in annotations,
     * as per [JACKSON-76]
     *
     * @param includeAll If true, includes all creator methods; if false,
     *                   will only include the no-arguments "default" constructor
     */
    public void resolveCreators(boolean includeAll) {
        // Then see which constructors we have
        _constructors = null;
        for (Constructor<?> ctor : _class.getDeclaredConstructors()) {
            switch (ctor.getParameterTypes().length) {
                case 0:
                    _defaultConstructor = _constructConstructor(ctor, true);
                    break;
                default:
                    if (includeAll) {
                        if (_constructors == null) {
                            _constructors = new ArrayList<AnnotatedConstructor>();
                        }
                        _constructors.add(_constructConstructor(ctor, false));
                    }
            }
        }
        // and if need be, augment with mix-ins
        if (_primaryMixIn != null) {
            if (_defaultConstructor != null || _constructors != null) {
                _addConstructorMixIns(_primaryMixIn);
            }
        }


        /* And then... let's remove all constructors that are
         * deemed to be ignorable after all annotations have been
         * properly collapsed.
         */
        if (_defaultConstructor != null) {
            if (_annotationIntrospector.isIgnorableConstructor(_defaultConstructor)) {
                _defaultConstructor = null;
            }
        }
        if (_constructors != null) {
            // count down to allow safe removal
            for (int i = _constructors.size(); --i >= 0; ) {
                if (_annotationIntrospector.isIgnorableConstructor(_constructors.get(i))) {
                    _constructors.remove(i);
                }
            }
        }

        _creatorMethods = null;

        if (includeAll) {
            /* Then static methods which are potential factory
             * methods
             */
            for (Method m : _class.getDeclaredMethods()) {
                if (!Modifier.isStatic(m.getModifiers())) {
                    continue;
                }
                int argCount = m.getParameterTypes().length;
                // factory methods take at least one arg:
                if (argCount < 1) {
                    continue;
                }
                if (_creatorMethods == null) {
                    _creatorMethods = new ArrayList<AnnotatedMethod>();
                }
                _creatorMethods.add(_constructCreatorMethod(m));
            }
            // mix-ins to mix in?
            if (_primaryMixIn != null && _creatorMethods != null) {
                _addFactoryMixIns(_primaryMixIn);
            }
            // anything to ignore at this point?
            if (_creatorMethods != null) {
                // count down to allow safe removal
                for (int i = _creatorMethods.size(); --i >= 0; ) {
                    if (_annotationIntrospector.isIgnorableMethod(_creatorMethods.get(i))) {
                        _creatorMethods.remove(i);
                    }
                }
            }
        }
    }

    protected void _addConstructorMixIns(Class<?> mixin) {
        MemberKey[] ctorKeys = null;
        int ctorCount = (_constructors == null) ? 0 : _constructors.size();
        for (Constructor<?> ctor : mixin.getDeclaredConstructors()) {
            switch (ctor.getParameterTypes().length) {
                case 0:
                    if (_defaultConstructor != null) {
                        _addMixOvers(ctor, _defaultConstructor, false);
                    }
                    break;
                default:
                    if (ctorKeys == null) {
                        ctorKeys = new MemberKey[ctorCount];
                        for (int i = 0; i < ctorCount; ++i) {
                            ctorKeys[i] = new MemberKey(_constructors.get(i).getAnnotated());
                        }
                    }
                    MemberKey key = new MemberKey(ctor);

                    for (int i = 0; i < ctorCount; ++i) {
                        if (!key.equals(ctorKeys[i])) {
                            continue;
                        }
                        _addMixOvers(ctor, _constructors.get(i), true);
                        break;
                    }
            }
        }
    }

    protected void _addFactoryMixIns(Class<?> mixin) {
        MemberKey[] methodKeys = null;
        int methodCount = _creatorMethods.size();

        for (Method m : mixin.getDeclaredMethods()) {
            if (!Modifier.isStatic(m.getModifiers())) {
                continue;
            }
            if (m.getParameterTypes().length == 0) {
                continue;
            }
            if (methodKeys == null) {
                methodKeys = new MemberKey[methodCount];
                for (int i = 0; i < methodCount; ++i) {
                    methodKeys[i] = new MemberKey(_creatorMethods.get(i).getAnnotated());
                }
            }
            MemberKey key = new MemberKey(m);
            for (int i = 0; i < methodCount; ++i) {
                if (!key.equals(methodKeys[i])) {
                    continue;
                }
                _addMixOvers(m, _creatorMethods.get(i), true);
                break;
            }
        }
    }

    /*
    /**************************************************************
    /* Methods for populating method information
    /**************************************************************
     */

    /**
     * @param collectIgnored Whether to collect list of ignored methods for later retrieval
     */
    public void resolveMemberMethods(MethodFilter methodFilter, boolean collectIgnored) {
        _memberMethods = new AnnotatedMethodMap();
        AnnotatedMethodMap mixins = new AnnotatedMethodMap();
        // first: methods from the class itself
        _addMemberMethods(_class, methodFilter, _memberMethods, _primaryMixIn, mixins);

        // and then augment these with annotations from super-types:
        for (Class<?> cls : _superTypes) {
            Class<?> mixin = (_mixInResolver == null) ? null : _mixInResolver.findMixInClassFor(cls);
            _addMemberMethods(cls, methodFilter, _memberMethods, mixin, mixins);
        }
        // Special case: mix-ins for Object.class? (to apply to ALL classes)
        if (_mixInResolver != null) {
            Class<?> mixin = _mixInResolver.findMixInClassFor(Object.class);
            if (mixin != null) {
                _addMethodMixIns(methodFilter, _memberMethods, mixin, mixins);
            }
        }

        /* Any unmatched mix-ins? Most likely error cases (not matching
         * any method); but there is one possible real use case:
         * exposing Object#hashCode (alas, Object#getClass can NOT be
         * exposed, see [JACKSON-140])
         */
        if (!mixins.isEmpty()) {
            Iterator<AnnotatedMethod> it = mixins.iterator();
            while (it.hasNext()) {
                AnnotatedMethod mixIn = it.next();
                try {
                    Method m = Object.class.getDeclaredMethod(mixIn.getName(), mixIn.getParameterClasses());
                    if (m != null) {
                        AnnotatedMethod am = _constructMethod(m);
                        _addMixOvers(mixIn.getAnnotated(), am, false);
                        _memberMethods.add(am);
                    }
                } catch (Exception e) {
                }
            }
        }

        /* And last but not least: let's remove all methods that are
         * deemed to be ignorable after all annotations have been
         * properly collapsed.
         */
        Iterator<AnnotatedMethod> it = _memberMethods.iterator();
        while (it.hasNext()) {
            AnnotatedMethod am = it.next();
            if (_annotationIntrospector.isIgnorableMethod(am)) {
                it.remove();
                if (collectIgnored) {
                    _ignoredMethods = ArrayBuilders.addToList(_ignoredMethods, am);
                }
            }
        }
    }

    protected void _addMemberMethods(Class<?> cls,
                                     MethodFilter methodFilter,
                                     AnnotatedMethodMap methods,
                                     Class<?> mixInCls, AnnotatedMethodMap mixIns) {
        // first, mixIns, since they have higher priority then class methods
        if (mixInCls != null) {
            _addMethodMixIns(methodFilter, methods, mixInCls, mixIns);
        }

        if (cls != null) {
            // then methods from the class itself
            for (Method m : cls.getDeclaredMethods()) {
                if (!_isIncludableMethod(m, methodFilter)) {
                    continue;
                }
                AnnotatedMethod old = methods.find(m);
                if (old == null) {
                    AnnotatedMethod newM = _constructMethod(m);
                    methods.add(newM);
                    // Ok, but is there a mix-in to connect now?
                    old = mixIns.remove(m);
                    if (old != null) {
                        _addMixOvers(old.getAnnotated(), newM, false);
                    }
                } else {
                    /* If sub-class already has the method, we only want
                     * to augment annotations with entries that are not
                     * masked by sub-class:
                     */
                    _addMixUnders(m, old);
                }
            }
        }
    }

    protected void _addMethodMixIns(MethodFilter methodFilter,
                                    AnnotatedMethodMap methods,
                                    Class<?> mixInCls, AnnotatedMethodMap mixIns) {
        for (Method m : mixInCls.getDeclaredMethods()) {
            if (!_isIncludableMethod(m, methodFilter)) {
                continue;
            }
            AnnotatedMethod am = methods.find(m);
            /* Do we already have a method to augment (from sub-class
             * that will mask this mixIn)? If so, add if visible
             * without masking (no such annotation)
             */
            if (am != null) {
                _addMixUnders(m, am);
                /* Otherwise will have precedence, but must wait
                 * until we find the real method (mixIn methods are
                 * just placeholder, can't be called)
                 */
            } else {
                mixIns.add(_constructMethod(m));
            }
        }
    }

    /*
    /******************************************************
    /* Methods for populating field information
    /******************************************************
     */

    /**
     * Method that will collect all member (non-static) fields
     * that are either public, or have at least a single annotation
     * associated with them.
     *
     * @param collectIgnored Whether to collect list of ignored methods for later retrieval
     */
    public void resolveFields(boolean collectIgnored) {
        LinkedHashMap<String, AnnotatedField> foundFields = new LinkedHashMap<String, AnnotatedField>();
        _addFields(foundFields, _class);

        /* And last but not least: let's remove all fields that are
         * deemed to be ignorable after all annotations have been
         * properly collapsed.
         */
        Iterator<Map.Entry<String, AnnotatedField>> it = foundFields.entrySet().iterator();
        while (it.hasNext()) {
            AnnotatedField f = it.next().getValue();
            if (_annotationIntrospector.isIgnorableField(f)) {
                it.remove();
                if (collectIgnored) {
                    _ignoredFields = ArrayBuilders.addToList(_ignoredFields, f);
                }
            } else {

            }
        }
        if (foundFields.isEmpty()) {
            _fields = Collections.emptyList();
        } else {
            _fields = new ArrayList<AnnotatedField>(foundFields.size());
            _fields.addAll(foundFields.values());
        }
    }

    protected void _addFields(Map<String, AnnotatedField> fields, Class<?> c) {
        /* First, a quick test: we only care for regular classes (not
         * interfaces, primitive types etc), except for Object.class.
         * A simple check to rule out other cases is to see if there
         * is a super class or not.
         */
        Class<?> parent = c.getSuperclass();
        if (parent != null) {
            // Let's add super-class' fields first, then ours.
            /* 21-Feb-2010, tatu: Need to handle masking: as per [JACKSON-226]
             *    we otherwise get into trouble...
             */
            _addFields(fields, parent);
            for (Field f : c.getDeclaredFields()) {
                // static fields not included, nor transient
                if (!_isIncludableField(f)) {
                    continue;
                }
                /* Ok now: we can (and need) not filter out ignorable fields
                 * at this point; partly because mix-ins haven't been
                 * added, and partly because logic can be done when
                 * determining get/settability of the field.
                 */
                fields.put(f.getName(), _constructField(f));
            }
            // And then... any mix-in overrides?
            if (_mixInResolver != null) {
                Class<?> mixin = _mixInResolver.findMixInClassFor(c);
                if (mixin != null) {
                    _addFieldMixIns(mixin, fields);
                }
            }
        }
    }

    /**
     * Method called to add field mix-ins from given mix-in class (and its fields)
     * into already collected actual fields (from introspected classes and their
     * super-classes)
     */
    protected void _addFieldMixIns(Class<?> mixin, Map<String, AnnotatedField> fields) {
        for (Field mixinField : mixin.getDeclaredFields()) {
            /* there are some dummy things (static, synthetic); better
             * ignore
             */
            if (!_isIncludableField(mixinField)) {
                continue;
            }
            String name = mixinField.getName();
            // anything to mask? (if not, quietly ignore)
            AnnotatedField maskedField = fields.get(name);
            if (maskedField != null) {
                for (Annotation a : mixinField.getDeclaredAnnotations()) {
                    if (_annotationIntrospector.isHandled(a)) {
                        maskedField.addOrOverride(a);
                    }
                }
            }
        }
    }

    /*
    /**************************************************************
    /* Helper methods, constructing value types
    /**************************************************************
     */

    protected AnnotatedMethod _constructMethod(Method m) {
        /* note: parameter annotations not used for regular (getter, setter)
         * methods; only for creator methods (static factory methods)
         */
        return new AnnotatedMethod(m, _collectRelevantAnnotations(m.getDeclaredAnnotations()), null);
    }

    protected AnnotatedConstructor _constructConstructor(Constructor<?> ctor, boolean defaultCtor) {
        return new AnnotatedConstructor(ctor, _collectRelevantAnnotations(ctor.getDeclaredAnnotations()),
                defaultCtor ? null : _collectRelevantAnnotations(ctor.getParameterAnnotations()));
    }

    protected AnnotatedMethod _constructCreatorMethod(Method m) {
        return new AnnotatedMethod(m, _collectRelevantAnnotations(m.getDeclaredAnnotations()),
                _collectRelevantAnnotations(m.getParameterAnnotations()));
    }

    protected AnnotatedField _constructField(Field f) {
        return new AnnotatedField(f, _collectRelevantAnnotations(f.getDeclaredAnnotations()));
    }

    protected AnnotationMap[] _collectRelevantAnnotations(Annotation[][] anns) {
        int len = anns.length;
        AnnotationMap[] result = new AnnotationMap[len];
        for (int i = 0; i < len; ++i) {
            result[i] = _collectRelevantAnnotations(anns[i]);
        }
        return result;
    }

    protected AnnotationMap _collectRelevantAnnotations(Annotation[] anns) {
        AnnotationMap annMap = new AnnotationMap();
        if (anns != null) {
            for (Annotation a : anns) {
                if (_annotationIntrospector.isHandled(a)) {
                    annMap.add(a);
                }
            }
        }
        return annMap;
    }
 
    /*
    /**************************************************************
    /* Helper methods, inclusion filtering
    /**************************************************************
     */

    protected boolean _isIncludableMethod(Method m, MethodFilter filter) {
        if (filter != null && !filter.includeMethod(m)) {
            return false;
        }
        /* 07-Apr-2009, tatu: Looks like generics can introduce hidden
         *   bridge and/or synthetic methods. I don't think we want to
         *   consider those...
         */
        if (m.isSynthetic() || m.isBridge()) {
            return false;
        }
        return true;
    }

    private boolean _isIncludableField(Field f) {
        /* I'm pretty sure synthetic fields are to be skipped...
         * (methods definitely are)
         */
        if (f.isSynthetic()) {
            return false;
        }
        // Static fields are never included, nor transient
        int mods = f.getModifiers();
        if (Modifier.isStatic(mods) || Modifier.isTransient(mods)) {
            return false;
        }
        return true;
    }

    /*
    /**************************************************************
    /* Helper methods, attaching annotations
    /**************************************************************
     */

    /**
     * @param addParamAnnotations Whether parameter annotations are to be
     *                            added as well
     */
    protected void _addMixOvers(Constructor<?> mixin, AnnotatedConstructor target,
                                boolean addParamAnnotations) {
        for (Annotation a : mixin.getDeclaredAnnotations()) {
            if (_annotationIntrospector.isHandled(a)) {
                target.addOrOverride(a);
            }
        }
        if (addParamAnnotations) {
            Annotation[][] pa = mixin.getParameterAnnotations();
            for (int i = 0, len = pa.length; i < len; ++i) {
                for (Annotation a : pa[i]) {
                    target.addOrOverrideParam(i, a);
                }
            }
        }
    }

    /**
     * @param addParamAnnotations Whether parameter annotations are to be
     *                            added as well
     */
    protected void _addMixOvers(Method mixin, AnnotatedMethod target,
                                boolean addParamAnnotations) {
        for (Annotation a : mixin.getDeclaredAnnotations()) {
            if (_annotationIntrospector.isHandled(a)) {
                target.addOrOverride(a);
            }
        }
        if (addParamAnnotations) {
            Annotation[][] pa = mixin.getParameterAnnotations();
            for (int i = 0, len = pa.length; i < len; ++i) {
                for (Annotation a : pa[i]) {
                    target.addOrOverrideParam(i, a);
                }
            }
        }
    }

    protected void _addMixUnders(Method mixin, AnnotatedMethod target) {
        for (Annotation a : mixin.getDeclaredAnnotations()) {
            if (_annotationIntrospector.isHandled(a)) {
                target.addIfNotPresent(a);
            }
        }
    }

    /*
    /**************************************************************
    /* Other methods
    /**************************************************************
     */

    @Override
    public String toString() {
        return "[AnnotedClass " + _class.getName() + "]";
    }
}

