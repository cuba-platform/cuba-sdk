open module mapdb {
    requires transitive kotlin.stdlib;
    requires transitive java.logging;
    requires transitive com.google.common;
    requires transitive elsa;
    requires transitive lz4;
    requires transitive org.eclipse.collections.api;
    requires transitive org.eclipse.collections.impl;

    exports org.mapdb;
}