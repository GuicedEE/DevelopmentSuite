module com.guicedee.nativeimage {
    requires transitive com.google.guice;
    requires static org.graalvm.nativeimage;
    exports com.guicedee.nativeimage;
    opens com.guicedee.nativeimage to org.graalvm.nativeimage.builder;
    uses com.guicedee.nativeimage.GuiceNativeConfiguration;
}
