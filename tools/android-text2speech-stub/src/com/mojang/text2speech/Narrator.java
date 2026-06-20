package com.mojang.text2speech;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public interface Narrator {
    Logger LOGGER = LoggerFactory.getLogger(Narrator.class);
    Narrator EMPTY = new Narrator() {
        @Override
        public void say(String text, boolean interrupt) {
        }

        @Override
        public void clear() {
        }

        @Override
        public void destroy() {
        }
    };

    void say(String text, boolean interrupt);

    void clear();

    default boolean active() {
        return false;
    }

    void destroy();

    static Narrator getNarrator() {
        return EMPTY;
    }

    class InitializeException extends Exception {
        public InitializeException(String message) {
            super(message);
        }

        public InitializeException(Throwable cause) {
            super(cause);
        }
    }
}
