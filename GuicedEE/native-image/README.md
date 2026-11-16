# Optional native-image support

`com.guicedee:native-image` supplies the explicitly enabled `com.guicedee.nativeimage.GuiceNativeFeature`.
GuicedEE core has no dependency on this module or GraalVM. JVM applications keep ordinary Guice
construction and interception.

This is a library, so its executable entry point belongs to the consuming application. It uses the
workspace Maven 4 wrapper and the parent/BOM-managed JUnit and Mockito versions. Its metadata classes
are not Guice-injected; their reflective opens target the native image builder. The creator's local
executable-scaffolding checks do not resolve these inherited settings or this library-only role.

The feature prepares Guice enhancer classes during image analysis. It retains ordinary runtime
injector creation, scopes, interceptor instances and service construction. Unenhanced constructors
and methods use Guice's existing reflection fallback. The AOT enhancer factory in the Guice owner
module avoids dynamic lambda linkage and does not initialize the application superclass while
preparing its invokers.

Applications or integration libraries contribute `GuiceNativeConfiguration` through both JPMS
`provides` and `META-INF/services`. A provider calls `reflect(...)` for reflective entry points and
`intercept(type, predicate)` for the union of methods selected by its runtime interceptor bindings.
Multiple selections may be registered when configurations require different method sets. Providers
must contain metadata only: do not start an injector, load credentials, inspect `.env`, or create
network clients during the build. Runtime services remain ordinary Guice objects.

`inspectMemberInjection(...)` can inspect classes used for externally constructed instance bindings.
It retains only proof that a class hierarchy contains no injectable fields or methods, avoiding
repeated runtime reflection on large library objects. No instances, injectors or injection-point
objects enter the image heap. Classes with injectable members and unregistered classes keep Guice's
normal validation, including inherited and generic members and partial error results.

An unregistered interceptor selection fails with a registration error. Interception is never
silently disabled to make compilation succeed. Runtime bytecode generation and arbitrary runtime
classpath discovery are outside this closed-world integration; the initial benchmark uses the
existing application discovery SPI.

When GuicedEE core is present, the native adapter replaces the runtime scanner entry point
with an SPI-mode check. This removes the unreachable scanning implementation from the image
and gives an explicit error if a native application's environment requests scanning. It does
not change JVM discovery, freeze environment settings, or add a core dependency to this library.

The separate [benchmark project](../../benchmarks/guicedee-native/pom.xml) retains the original
application JAR, its descriptor and its workload. Its SDK reachability metadata is workload-specific;
passing that workload does not imply support for every Firebase product or every GuicedEE integration.
