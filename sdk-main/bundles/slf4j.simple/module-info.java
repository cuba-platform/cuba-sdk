open module slf4j.simple {
    requires transitive org.slf4j;

    exports org.slf4j.impl;

    uses org.slf4j.spi.LoggerFactoryBinder;
//    provides org.slf4j.spi.LoggerFactoryBinder with org.slf4j.impl.StaticLoggerBinder;
}