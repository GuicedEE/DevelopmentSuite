package com.guicedee.nativeimage;

import com.google.inject.internal.BytecodeGen;
import java.lang.reflect.Method;
import java.util.*;
import java.util.function.*;

final class PreparedEnhancers {
    final Map<Class<?>, Builder> builders = new HashMap<>();
    final Set<Class<?>> typesWithoutMemberInjection = new HashSet<>();

    static final class Builder implements BytecodeGen.EnhancerBuilder {
        final Class<?> type;
        final Method[] methods;
        final Map<BitSet, Map<String, BiFunction<Object, Object[], Object>>> enhancers = new HashMap<>();

        Builder(Class<?> type, Method[] methods) { this.type = type; this.methods = methods; }
        @Override public Method[] getEnhanceableMethods() { return methods.clone(); }
        @Override public Function<String, BiFunction<Object, Object[], Object>> buildEnhancer(BitSet selection) {
            var invokers = enhancers.get(selection);
            if (invokers == null) {
                throw new IllegalStateException("Missing native Guice interceptor registration for "
                        + type.getName() + " with method selection " + selection
                        + ". Register this selection using GuiceNativeConfiguration; runtime bytecode generation is unavailable.");
            }
            return invokers::get;
        }
    }
}
