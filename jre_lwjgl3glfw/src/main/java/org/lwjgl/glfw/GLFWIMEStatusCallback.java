/*
 * Copyright LWJGL. All rights reserved.
 * License terms: https://www.lwjgl.org/license
 */
package org.lwjgl.glfw;

/**
 * Stub for the concrete callback class returned by {@link GLFW#glfwSetIMEStatusCallback}.
 * On Android this callback is a no-op; the return value (previous callback) is always {@code null}.
 */
public abstract class GLFWIMEStatusCallback implements GLFWIMEStatusCallbackI, AutoCloseable {

    /** Creates a {@code GLFWIMEStatusCallback} wrapping the given functional interface. */
    public static GLFWIMEStatusCallback create(GLFWIMEStatusCallbackI sam) {
        return new GLFWIMEStatusCallback() {
            @Override
            public void invoke(long window, int imeStatus) {
                sam.invoke(window, imeStatus);
            }
        };
    }

    /** No-op free; no native resources on Android. */
    public void free() {}

    @Override
    public void close() { free(); }
}
