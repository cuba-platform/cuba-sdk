module fuel {
    requires transitive result;
    requires transitive kotlin.stdlib;
    requires transitive jdk.crypto.ec;

    exports com.github.kittinunf.fuel;
    exports com.github.kittinunf.fuel.core;
}