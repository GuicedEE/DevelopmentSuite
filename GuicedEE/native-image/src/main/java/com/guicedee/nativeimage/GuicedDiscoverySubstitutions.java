package com.guicedee.nativeimage;

import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import java.util.function.BooleanSupplier;

/** Native applications discover their closed set of classes through the existing SPI. */
@TargetClass(className = "com.guicedee.guicedinjection.GuiceContext", onlyWith = GuicedCorePresent.class)
final class GuicedDiscoverySubstitutions {
    @Substitute
    private void loadScanner() {
        if (!NativeDiscoveryMode.mode().equals("spi")) {
            throw new IllegalStateException("Native GuicedEE requires GUICEDEE_DISCOVERY_MODE=spi and IGuiceDiscovery providers; runtime classpath scanning is unavailable");
        }
    }
}

@TargetClass(className = "com.guicedee.client.utils.Discovery", onlyWith = GuicedCorePresent.class)
final class NativeDiscoveryMode {
    @Alias static native String mode();
}

final class GuicedCorePresent implements BooleanSupplier {
    @Override public boolean getAsBoolean() {
        try {
            Class.forName("com.guicedee.guicedinjection.GuiceContext", false, getClass().getClassLoader());
            return true;
        } catch (ClassNotFoundException absent) {
            return false;
        }
    }
}
