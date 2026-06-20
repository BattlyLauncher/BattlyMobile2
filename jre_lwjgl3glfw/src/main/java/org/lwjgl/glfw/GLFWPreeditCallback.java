/*
 * Copyright LWJGL. All rights reserved.
 * License terms: https://www.lwjgl.org/license
 */
package org.lwjgl.glfw;

/**
 * Stub for the concrete callback class returned by {@link GLFW#glfwSetPreeditCallback}.
 * On Android this callback is a no-op; the return value (previous callback) is always {@code null}.
 */
public abstract class GLFWPreeditCallback implements GLFWPreeditCallbackI, AutoCloseable {

    /** Creates a {@code GLFWPreeditCallback} wrapping the given functional interface. */
    public static GLFWPreeditCallback create(GLFWPreeditCallbackI sam) {
        return new GLFWPreeditCallback() {
            @Override
            public void invoke(long window, int preeditCount, long preeditString,
                               int blockCount, long blockSizes, int focusedBlock, int caret) {
                sam.invoke(window, preeditCount, preeditString, blockCount, blockSizes, focusedBlock, caret);
            }
        };
    }

    /** No-op free; no native resources on Android. */
    public void free() {}

    @Override
    public void close() { free(); }
}
