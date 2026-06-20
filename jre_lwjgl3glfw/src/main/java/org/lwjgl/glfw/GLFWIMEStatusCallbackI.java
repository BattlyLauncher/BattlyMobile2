/*
 * Copyright LWJGL. All rights reserved.
 * License terms: https://www.lwjgl.org/license
 */
package org.lwjgl.glfw;

import org.lwjgl.system.NativeType;

/**
 * Instances of this interface may be passed to the {@link GLFW#glfwSetIMEStatusCallback SetIMEStatusCallback} method.
 * Added in GLFW 3.4 / LWJGL 3.3.1 for IME status change events.
 */
@FunctionalInterface
public interface GLFWIMEStatusCallbackI {
    void invoke(@NativeType("GLFWwindow *") long window, int imeStatus);
}
