open module gradle.tooling.api {
    requires java.base;
    requires transitive org.slf4j;
//    requires transitive slf4j.simple;

    exports org.gradle.tooling;
    exports org.gradle.tooling.model;

//    uses org.slf4j.impl.StaticLoggerBinder;
//    uses org.slf4j.LoggerFactory;
}