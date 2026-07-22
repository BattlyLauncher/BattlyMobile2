package org.lwjgl.opengl;

import org.lwjgl.system.FunctionProvider;
import org.lwjgl.system.MemoryUtil;

public class GLContext {
    private static final ThreadLocal<ContextCapabilities> contextCapabilities = new ThreadLocal<>();

    public static GLContext createFromCurrent() {
        return new GLContext();
    }

    public static void initCapabilities() {
        if (contextCapabilities.get() == null) {
            System.out.println("LWJGLX: GL caps init");
            contextCapabilities.set(new ContextCapabilities());
        }
    }

    public static ContextCapabilities getCapabilities() {
        return contextCapabilities.get();
    }

    static long getFunctionAddress(String functionName) {
        FunctionProvider functionProvider = GL.getFunctionProvider();
        if (functionProvider == null || functionName == null) {
            return 0L;
        }
        return functionProvider.getFunctionAddress(functionName);
    }

    static long ngetFunctionAddress(long functionNameAddress) {
        if (functionNameAddress == 0L) {
            return 0L;
        }
        return getFunctionAddress(MemoryUtil.memASCII(functionNameAddress));
    }
}
