module jline {
    requires transitive jansi;
    requires transitive java.logging;

    exports org.jline.builtins;
    exports org.jline.keymap;
    exports org.jline.style;
    exports org.jline.terminal;
    exports org.jline.utils;
    exports org.jline.reader;
    exports org.jline.reader.impl;
    exports org.jline.reader.impl.history;
    exports org.jline.reader.impl.completer;
    exports org.jline.terminal.impl;

    exports org.jline.terminal.spi;
    exports org.jline.terminal.impl.jansi.win;

    uses org.jline.terminal.spi.JansiSupport;
}