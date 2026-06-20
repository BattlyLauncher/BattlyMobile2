/*
 * Copyright LWJGL. All rights reserved.
 * License terms: https://www.lwjgl.org/license
 */
package org.lwjgl.glfw;

import org.lwjgl.system.NativeType;

/**
 * Instances of this interface may be passed to the {@link GLFW#glfwSetPreeditCallback SetPreeditCallback} method.
 * Added in GLFW 3.4 / LWJGL 3.3.1 for IME preedit support.
 */
@FunctionalInterface
public interface GLFWPreeditCallbackI {
    void invoke(
        @NativeType("GLFWwindow *") long window,
        int preeditCount,
        @NativeType("unsigned int *") long preeditString,
        int blockCount,
        @NativeType("int *") long blockSizes,
        int focusedBlock,
        int caret
    );
}
