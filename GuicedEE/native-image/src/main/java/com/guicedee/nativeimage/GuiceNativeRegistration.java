package com.guicedee.nativeimage;

import com.google.inject.internal.aop.ClassBuilding;
import com.google.inject.gee.InjectionPoint;
import java.lang.reflect.Method;
import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Predicate;
import org.graalvm.nativeimage.hosted.RuntimeReflection;

/** Explicit closed-world reflection and interceptor selections contributed by application/library SPIs. */
public final class GuiceNativeRegistration {
    private final PreparedEnhancers registry;

    GuiceNativeRegistration(PreparedEnhancers registry) { this.registry = registry; }

    /**
     * Inspect immutable class metadata without creating instances. Guice inspects objects bound
     * with toInstance twice; large library objects often have no injectable members at all.
     * Only proven empty results are retained. Types with injection points use ordinary Guice.
     */
    public void inspectMemberInjection(Class<?>... types) {
        for (Class<?> type : types) {
            if (InjectionPoint.forInstanceMethodsAndFields(type).isEmpty()) {
                registry.typesWithoutMemberInjection.add(type);
            }
        }
    }

    public void reflect(Class<?>... types) {
        for (Class<?> type : types) {
            RuntimeReflection.register(type);
            RuntimeReflection.register(type.getDeclaredConstructors());
            RuntimeReflection.register(type.getDeclaredMethods());
            RuntimeReflection.register(type.getDeclaredFields());
        }
    }

    /** Register the union of methods selected by the runtime injector's interceptor bindings. */
    public void intercept(Class<?> type, Predicate<Method> selected) {
        reflect(type);
        var builder = ClassBuilding.buildAheadOfTimeEnhancerBuilder(type);
        Method[] methods = builder.getEnhanceableMethods();
        var bits = new BitSet();
        for (int i = 0; i < methods.length; i++) if (selected.test(methods[i])) bits.set(i);
        if (bits.isEmpty()) throw new IllegalArgumentException("No intercepted methods selected for " + type.getName());
        var enhancer = builder.buildEnhancer(bits);
        Map<String, BiFunction<Object, Object[], Object>> invokers = new HashMap<>();
        for (var constructor : type.getDeclaredConstructors()) if (ClassBuilding.canEnhance(constructor)) {
            String signature = ClassBuilding.signature(constructor);
            invokers.put(signature, enhancer.apply(signature));
        }
        for (int i = bits.nextSetBit(0); i >= 0; i = bits.nextSetBit(i + 1)) {
            RuntimeReflection.register(methods[i]);
            String signature = ClassBuilding.signature(methods[i]);
            invokers.put(signature, enhancer.apply(signature));
        }
        var prepared = registry.builders.computeIfAbsent(type,
                key -> new PreparedEnhancers.Builder(key, methods));
        prepared.enhancers.put((BitSet) bits.clone(), Map.copyOf(invokers));
    }
}
