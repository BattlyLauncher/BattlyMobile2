package com.bangbang93;

import java.io.File;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.security.CodeSource;
import java.util.function.Predicate;

public final class ForgeInstaller {
    private ForgeInstaller() {
    }

    public static void main(String[] args) throws Exception {
        File minecraftDir = args.length == 0 ? new File(".") : new File(args[0]);
        File installerJar = findInstallerJar();
        setStaticBoolean("net.minecraftforge.installer.SimpleInstaller", "headless", true);

        Class<?> utilClass = Class.forName("net.minecraftforge.installer.json.Util");
        Object installProfile = utilClass.getDeclaredMethod("loadInstallProfile").invoke(null);
        Object callback = createProgressCallback();
        Object action = createClientAction(installProfile, callback);

        boolean success = runAction(action, minecraftDir, installerJar);
        if (!success) {
            throw new IllegalStateException("Forge client installation failed");
        }
    }

    private static File findInstallerJar() throws Exception {
        CodeSource codeSource = Class.forName("net.minecraftforge.installer.SimpleInstaller")
                .getProtectionDomain()
                .getCodeSource();
        if (codeSource == null || codeSource.getLocation() == null) {
            throw new IllegalStateException("Unable to locate Forge installer jar");
        }
        return new File(codeSource.getLocation().toURI());
    }

    private static void setStaticBoolean(String className, String fieldName, boolean value) {
        try {
            Field field = Class.forName(className).getField(fieldName);
            field.setBoolean(null, value);
        } catch (ReflectiveOperationException ignored) {
        }
    }

    private static Object createClientAction(Object installProfile, Object callback) throws Exception {
        Class<?> profileClass = Class.forName("net.minecraftforge.installer.json.InstallV1");
        Class<?> callbackClass = Class.forName("net.minecraftforge.installer.actions.ProgressCallback");

        try {
            Class<?> actionsClass = Class.forName("net.minecraftforge.installer.actions.Actions");
            @SuppressWarnings({"rawtypes", "unchecked"})
            Object clientAction = Enum.valueOf((Class<? extends Enum>) actionsClass.asSubclass(Enum.class), "CLIENT");
            Method getAction = actionsClass.getMethod("getAction", profileClass, callbackClass);
            return getAction.invoke(clientAction, installProfile, callback);
        } catch (ReflectiveOperationException ignored) {
            Class<?> clientInstallClass = Class.forName("net.minecraftforge.installer.actions.ClientInstall");
            return clientInstallClass
                    .getConstructor(profileClass, callbackClass)
                    .newInstance(installProfile, callback);
        }
    }

    private static Object createProgressCallback() throws Exception {
        Class<?> callbackClass = Class.forName("net.minecraftforge.installer.actions.ProgressCallback");
        if (!callbackClass.isInterface()) {
            Method withOutputs = callbackClass.getMethod("withOutputs", OutputStream[].class);
            return withOutputs.invoke(null, (Object) new OutputStream[]{System.out});
        }

        InvocationHandler handler = (proxy, method, args) -> {
            String methodName = method.getName();
            if ("toString".equals(methodName)) {
                return "Battly Forge installer progress callback";
            }
            if ("hashCode".equals(methodName)) {
                return System.identityHashCode(proxy);
            }
            if ("equals".equals(methodName)) {
                return proxy == args[0];
            }
            if (args != null && args.length > 0 &&
                    ("start".equals(methodName) || "stage".equals(methodName) || "message".equals(methodName))) {
                System.out.println(String.valueOf(args[0]));
            }
            return defaultValue(method.getReturnType());
        };
        return Proxy.newProxyInstance(callbackClass.getClassLoader(), new Class<?>[]{callbackClass}, handler);
    }

    private static boolean runAction(Object action, File minecraftDir, File installerJar) throws Exception {
        try {
            Method run = action.getClass().getMethod("run", File.class, File.class);
            return Boolean.TRUE.equals(run.invoke(action, minecraftDir, installerJar));
        } catch (NoSuchMethodException ignored) {
            Method run = action.getClass().getMethod("run", File.class, Predicate.class, File.class);
            Predicate<String> allowAll = value -> true;
            return Boolean.TRUE.equals(run.invoke(action, minecraftDir, allowAll, installerJar));
        }
    }

    private static Object defaultValue(Class<?> type) {
        if (type == Void.TYPE) {
            return null;
        }
        if (type == Boolean.TYPE) {
            return false;
        }
        if (type == Byte.TYPE) {
            return (byte) 0;
        }
        if (type == Short.TYPE) {
            return (short) 0;
        }
        if (type == Integer.TYPE) {
            return 0;
        }
        if (type == Long.TYPE) {
            return 0L;
        }
        if (type == Float.TYPE) {
            return 0f;
        }
        if (type == Double.TYPE) {
            return 0d;
        }
        if (type == Character.TYPE) {
            return '\0';
        }
        return null;
    }
}
