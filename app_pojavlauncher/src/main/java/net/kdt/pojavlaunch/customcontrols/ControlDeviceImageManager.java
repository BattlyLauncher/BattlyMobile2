package net.kdt.pojavlaunch.customcontrols;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.view.InputDevice;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;

import com.bumptech.glide.Glide;

import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.customcontrols.gamepad.ControllerTypeResolver;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.UUID;

public final class ControlDeviceImageManager {
    public static final String SOURCE_AUTO = "auto";
    public static final String SOURCE_XBOX = "xbox";
    public static final String SOURCE_DUALSENSE = "dualsense";
    public static final String SOURCE_DUALSHOCK4 = "dualshock4";
    public static final String SOURCE_SWITCH = "switch";
    public static final String SOURCE_KEYBOARD_MOUSE = "keyboard_mouse";
    public static final String SOURCE_CUSTOM = "custom";

    private static final long MAX_CUSTOM_IMAGE_BYTES = 12L * 1024L * 1024L;

    public interface SourceListener {
        void onSourceSelected(String source);
    }

    private ControlDeviceImageManager() {}

    public static void showSourcePicker(Activity activity, SourceListener listener) {
        String[] labels = activity.getResources().getStringArray(R.array.control_device_image_sources);
        AlertDialog dialog = new AlertDialog.Builder(activity, R.style.BattlyDialog)
                .setTitle(R.string.customctrl_device_image_title)
                .setItems(labels, (owner, which) -> listener.onSourceSelected(sourceAt(which)))
                .setNegativeButton(android.R.string.cancel, null)
                .create();
        dialog.setOnShowListener(ignored -> Tools.styleDialog(dialog));
        dialog.show();
    }

    private static String sourceAt(int index) {
        switch (index) {
            case 1: return SOURCE_XBOX;
            case 2: return SOURCE_DUALSENSE;
            case 3: return SOURCE_DUALSHOCK4;
            case 4: return SOURCE_SWITCH;
            case 5: return SOURCE_KEYBOARD_MOUSE;
            case 6: return SOURCE_CUSTOM;
            default: return SOURCE_AUTO;
        }
    }

    public static String importCustomImage(Context context, Uri uri) throws IOException {
        ContentResolver resolver = context.getContentResolver();
        String mime = resolver.getType(uri);
        if (mime != null && !mime.toLowerCase(Locale.ROOT).startsWith("image/")) {
            throw new IOException(context.getString(R.string.customctrl_device_image_invalid));
        }

        File root = context.getExternalFilesDir(null);
        if (root == null) root = context.getFilesDir();
        File directory = new File(root, "control-images");
        if (!directory.exists() && !directory.mkdirs()) {
            throw new IOException("Unable to create control image directory");
        }
        File destination = new File(directory, UUID.randomUUID() + ".img");
        long total = 0;
        try (InputStream input = resolver.openInputStream(uri);
             FileOutputStream output = new FileOutputStream(destination)) {
            if (input == null) throw new IOException("Unable to open selected image");
            byte[] buffer = new byte[32 * 1024];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                total += read;
                if (total > MAX_CUSTOM_IMAGE_BYTES) {
                    throw new IOException(context.getString(R.string.customctrl_device_image_too_large));
                }
                output.write(buffer, 0, read);
            }
        } catch (IOException error) {
            //noinspection ResultOfMethodCallIgnored
            destination.delete();
            throw error;
        }
        return destination.getAbsolutePath();
    }

    public static void loadInto(ImageView view, @Nullable String requestedSource, @Nullable String path) {
        String source = resolveDisplaySource(requestedSource);
        if (SOURCE_KEYBOARD_MOUSE.equals(source)) {
            Glide.with(view).clear(view);
            view.setImageDrawable(new KeyboardMouseDrawable(view.getContext()));
            return;
        }
        if (SOURCE_CUSTOM.equals(source)) {
            File file = path == null ? null : new File(path);
            if (file != null && file.isFile()) {
                Glide.with(view).load(file).fitCenter().into(view);
            } else {
                Glide.with(view).clear(view);
                view.setImageResource(R.drawable.ic_battly_gamepad_line);
            }
            return;
        }
        Glide.with(view)
                .load("file:///android_asset/controller3d/" + assetName(source))
                .fitCenter()
                .into(view);
    }

    public static String resolveDisplaySource(@Nullable String requestedSource) {
        String source = normalizeSource(requestedSource);
        return SOURCE_AUTO.equals(source) ? resolveConnectedSource() : source;
    }

    public static String resolveConnectedSource() {
        boolean keyboardOrMouse = false;
        for (int id : InputDevice.getDeviceIds()) {
            InputDevice device = InputDevice.getDevice(id);
            if (device == null || device.isVirtual()) continue;
            int sources = device.getSources();
            if ((sources & InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD
                    || (sources & InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK) {
                ControllerTypeResolver.Style style = ControllerTypeResolver.resolve(
                        ControllerTypeResolver.Style.AUTO, device);
                if (style == ControllerTypeResolver.Style.PLAYSTATION) {
                    String name = device.getName() == null ? "" : device.getName().toLowerCase(Locale.ROOT);
                    return name.contains("dualshock") ? SOURCE_DUALSHOCK4 : SOURCE_DUALSENSE;
                }
                if (style == ControllerTypeResolver.Style.SWITCH) return SOURCE_SWITCH;
                return SOURCE_XBOX;
            }
            keyboardOrMouse |= device.getKeyboardType() == InputDevice.KEYBOARD_TYPE_ALPHABETIC
                    || (sources & InputDevice.SOURCE_MOUSE) == InputDevice.SOURCE_MOUSE;
        }
        return keyboardOrMouse ? SOURCE_KEYBOARD_MOUSE : SOURCE_XBOX;
    }

    private static String normalizeSource(String source) {
        if (source == null || source.trim().isEmpty()) return SOURCE_AUTO;
        return source.toLowerCase(Locale.ROOT);
    }

    private static String assetName(String source) {
        switch (source) {
            case SOURCE_DUALSENSE: return "dualsense.png";
            case SOURCE_DUALSHOCK4: return "dualshock4.png";
            case SOURCE_SWITCH: return "switch-pro.png";
            default: return "xbox-series.png";
        }
    }

    private static final class KeyboardMouseDrawable extends Drawable {
        private final Drawable keyboard;
        private final Drawable mouse;

        KeyboardMouseDrawable(Context context) {
            keyboard = ContextCompat.getDrawable(context, R.drawable.ic_battly_keyboard_line);
            mouse = ContextCompat.getDrawable(context, R.drawable.ic_mouse_pointer);
        }

        @Override
        public void draw(@NonNull Canvas canvas) {
            Rect bounds = getBounds();
            int width = bounds.width();
            int height = bounds.height();
            if (keyboard != null) {
                keyboard.setBounds(bounds.left, bounds.top + height / 5,
                        bounds.left + (width * 3 / 4), bounds.bottom - height / 5);
                keyboard.draw(canvas);
            }
            if (mouse != null) {
                mouse.setBounds(bounds.left + (width * 2 / 3), bounds.top + height / 5,
                        bounds.right, bounds.bottom - height / 5);
                mouse.draw(canvas);
            }
        }

        @Override public void setAlpha(int alpha) {
            if (keyboard != null) keyboard.setAlpha(alpha);
            if (mouse != null) mouse.setAlpha(alpha);
        }
        @Override public void setColorFilter(@Nullable ColorFilter colorFilter) {
            if (keyboard != null) keyboard.setColorFilter(colorFilter);
            if (mouse != null) mouse.setColorFilter(colorFilter);
        }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }
}
