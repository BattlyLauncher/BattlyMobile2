/*
 * Copyright LWJGL. All rights reserved.
 * License terms: https://www.lwjgl.org/license
 */
package org.lwjgl.openal;

/**
 * Functional interface for the ALC_SOFT_system_events callback.
 * Added in LWJGL 3.3.3 / OpenAL Soft 1.23.0.
 * On Android this callback is a no-op stub.
 */
@FunctionalInterface
public interface SOFTSystemEventProcI {
    /**
     * @param eventType   the event type
     * @param objectType  the object type
     * @param objectId    the object id
     * @param length      message length
     * @param message     pointer to message string
     * @param userParam   user-provided pointer
     */
    void invoke(int eventType, int objectType, long objectId, int length, long message, long userParam);
}
