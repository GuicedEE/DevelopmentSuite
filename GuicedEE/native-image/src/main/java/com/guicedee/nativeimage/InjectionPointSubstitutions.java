package com.guicedee.nativeimage;

import com.google.inject.ConfigurationException;
import com.google.inject.TypeLiteral;
import com.google.inject.gee.InjectionPoint;
import com.google.inject.internal.Errors;
import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import java.util.Collections;
import java.util.Set;
import org.graalvm.nativeimage.ImageSingletons;

@TargetClass(InjectionPoint.class)
final class InjectionPointSubstitutions {
    @Alias private static native Set<InjectionPoint> getInjectionPoints(TypeLiteral<?> type, boolean statics, Errors errors);

    @Substitute
    public static Set<InjectionPoint> forInstanceMethodsAndFields(TypeLiteral<?> type) {
        if (ImageSingletons.lookup(PreparedEnhancers.class).typesWithoutMemberInjection.contains(type.getRawType())) {
            // An absence of annotated members does not depend on generic type arguments.
            return Collections.emptySet();
        }
        // Preserve Guice's validation, ordering and partial results for every other type.
        Errors errors = new Errors();
        Set<InjectionPoint> result = getInjectionPoints(type, false, errors);
        if (errors.hasErrors()) {
            throw new ConfigurationException(errors.getMessages()).withPartialValue(result);
        }
        return result;
    }
}
