package com.guicedee.nativeimage.test;

import com.google.inject.*;
import com.google.inject.matcher.Matchers;
import com.guicedee.nativeimage.GuiceNativeConfiguration;
import java.lang.module.ModuleDescriptor;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class JvmCompatibilityTest {
    @Singleton public static class Dependency { }
    public static class Service {
        @Inject Dependency dependency;
        public String call() { return "runtime"; }
    }

    @Test void optionalNativeLibraryPreservesNormalJvmInjectionAndInterception() {
        var calls = new AtomicInteger();
        var injector = Guice.createInjector(new AbstractModule() {
            @Override protected void configure() {
                bindInterceptor(Matchers.subclassesOf(Service.class),
                        new com.google.inject.matcher.AbstractMatcher<>() {
                            @Override public boolean matches(java.lang.reflect.Method method) {
                                return method.getName().equals("call");
                            }
                        }, invocation -> {
                            calls.incrementAndGet();
                            return "intercepted:" + invocation.proceed();
                        });
            }
        });
        var service = injector.getInstance(Service.class);
        assertEquals("intercepted:runtime", service.call());
        assertEquals(1, calls.get());
        assertSame(injector.getInstance(Dependency.class), service.dependency);
    }

    @Test void hostedSdkRemainsOptionalAndGuiceHasNoNativeImageDependency() {
        var support = GuiceNativeConfiguration.class.getModule().getDescriptor();
        var sdk = support.requires().stream().filter(requirement -> requirement.name().equals("org.graalvm.nativeimage"))
                .findFirst().orElseThrow();
        assertTrue(sdk.modifiers().contains(ModuleDescriptor.Requires.Modifier.STATIC));
        assertTrue(Guice.class.getModule().getDescriptor().requires().stream()
                .noneMatch(requirement -> requirement.name().startsWith("org.graalvm")
                        || requirement.name().equals("com.guicedee.nativeimage")));
    }
}
