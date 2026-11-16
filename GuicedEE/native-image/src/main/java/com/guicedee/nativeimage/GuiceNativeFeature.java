package com.guicedee.nativeimage;

import com.google.inject.internal.InternalFlags;
import java.util.ServiceLoader;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.hosted.RuntimeClassInitialization;

/** Enabled explicitly by --features; normal JVM applications do not load or depend on this feature. */
public final class GuiceNativeFeature implements Feature {
    @Override public void beforeAnalysis(BeforeAnalysisAccess access) {
        RuntimeClassInitialization.initializeAtBuildTime("com.google.inject.internal.aop");
        RuntimeClassInitialization.initializeAtBuildTime(InternalFlags.class);
        RuntimeClassInitialization.initializeAtBuildTime(InternalFlags.class.getDeclaredClasses());
        RuntimeClassInitialization.initializeAtBuildTime(PreparedEnhancers.class, PreparedEnhancers.Builder.class);
        var registry = new PreparedEnhancers();
        var registration = new GuiceNativeRegistration(registry);
        int providers = 0;
        for (var provider : ServiceLoader.load(GuiceNativeConfiguration.class, access.getApplicationClassLoader())) {
            provider.configure(registration);
            providers++;
        }
        if (providers == 0) throw new IllegalStateException("No GuiceNativeConfiguration providers found. Register build metadata using JPMS provides and META-INF/services.");
        ImageSingletons.add(PreparedEnhancers.class, registry);
        System.out.println("GuicedEE native: " + providers + " metadata providers, " + registry.builders.size()
                + " intercepted classes prepared, " + registry.typesWithoutMemberInjection.size() + " types proven to have no member injection.");
    }
}
