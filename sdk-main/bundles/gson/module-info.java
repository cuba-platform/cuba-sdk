open module gson {
    requires java.sql;
    requires jdk.unsupported;

    exports com.google.gson;
    exports com.google.gson.annotations;
    exports com.google.gson.internal;
    exports com.google.gson.internal.bind;
    exports com.google.gson.reflect;
    exports com.google.gson.stream;
}