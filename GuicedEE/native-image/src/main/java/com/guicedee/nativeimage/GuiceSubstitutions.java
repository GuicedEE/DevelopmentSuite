package com.guicedee.nativeimage;

import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.google.inject.internal.BytecodeGen;
import com.google.inject.internal.aop.ClassBuilding;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.function.BiFunction;
import org.graalvm.nativeimage.ImageSingletons;

@TargetClass(BytecodeGen.class)
final class GuiceSubstitutions {
    // Guice already supports reflective constructors and methods. Retain interception separately.
    @Substitute static BiFunction<Object, Object[], Object> fastConstructor(Constructor<?> constructor) { return null; }
    @Substitute static BiFunction<Object, Object[], Object> fastMethod(Method method) { return null; }
    @Substitute static BytecodeGen.EnhancerBuilder enhancerBuilder(Class<?> type) {
        var prepared = ImageSingletons.lookup(PreparedEnhancers.class).builders.get(type);
        if (prepared != null) return prepared;
        // Guice inspects classes even when no methods match its interceptor bindings.
        // Inspection is safe; an unregistered non-empty selection fails explicitly in Builder.
        return new PreparedEnhancers.Builder(type,
                ClassBuilding.buildEnhancerBuilder(type).getEnhanceableMethods());
    }
}
