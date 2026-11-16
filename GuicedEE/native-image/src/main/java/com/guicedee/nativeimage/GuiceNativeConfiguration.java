package com.guicedee.nativeimage;

/** Build-time metadata only. Implementations must not start Guice, read secrets or create clients. */
public interface GuiceNativeConfiguration {
    void configure(GuiceNativeRegistration registration);
}
