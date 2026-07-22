package com.mojang.text2speech;

public interface Narrator {
    Narrator EMPTY = new Narrator() {
    };

    default void say(String text) {
    }

    default void say(String text, boolean interrupt) {
    }

    default void say(String text, boolean interrupt, float volume) {
    }

    default void clear() {
    }

    default boolean active() {
        return false;
    }

    default void destroy() {
    }

    static Narrator getNarrator() {
        return EMPTY;
    }

    static void setJNAPath(String path) {
    }

    class InitializeException extends Exception {
        public InitializeException(String message) {
            super(message);
        }

        public InitializeException(Throwable cause) {
            super(cause);
        }

        public InitializeException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    class FatalException extends RuntimeException {
        public FatalException(String message) {
            super(message);
        }
    }
}
