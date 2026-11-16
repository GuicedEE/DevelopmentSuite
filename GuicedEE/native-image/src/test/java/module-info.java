module com.guicedee.nativeimage.test {
    requires com.guicedee.nativeimage;
    requires org.junit.jupiter.api;
    opens com.guicedee.nativeimage.test to org.junit.platform.commons, com.google.guice;
}
