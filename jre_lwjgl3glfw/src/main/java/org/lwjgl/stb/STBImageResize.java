/*
 * Copyright LWJGL. All rights reserved.
 * License terms: https://www.lwjgl.org/license
 * MACHINE GENERATED FILE, DO NOT EDIT — patched for LWJGL 3.3.3 compatibility
 */
package org.lwjgl.stb;

import java.nio.*;
import static org.lwjgl.system.MemoryUtil.*;

/**
 * Bindings to the <a href="https://github.com/nothings/stb/blob/master/stb_image_resize.h">stb_image_resize</a> library.
 *
 * <p>This version adds the LWJGL 3.3.3 {@code nstbir_resize_uint8_linear} method as a shim over the old
 * {@code nstbir_resize_uint8} native binding, so MC 26.1.2 can be launched without a full LWJGL upgrade.</p>
 */
public class STBImageResize {

    public static final int
        STBIR_ALPHA_CHANNEL_NONE       = -1;
    public static final int
        STBIR_FLAG_ALPHA_PREMULTIPLIED = 1,
        STBIR_FLAG_ALPHA_USES_COLORSPACE = 2;
    public static final int
        STBIR_EDGE_CLAMP   = 1,
        STBIR_EDGE_REFLECT = 2,
        STBIR_EDGE_WRAP    = 3,
        STBIR_EDGE_ZERO    = 4;
    public static final int
        STBIR_FILTER_DEFAULT     = 0,
        STBIR_FILTER_BOX         = 1,
        STBIR_FILTER_TRIANGLE    = 2,
        STBIR_FILTER_CUBICBSPLINE = 3,
        STBIR_FILTER_CATMULLROM  = 4,
        STBIR_FILTER_MITCHELL    = 5;
    public static final int
        STBIR_COLORSPACE_LINEAR = 0,
        STBIR_COLORSPACE_SRGB   = 1;
    public static final int
        STBIR_TYPE_UINT8  = 0,
        STBIR_TYPE_UINT16 = 1,
        STBIR_TYPE_UINT32 = 2,
        STBIR_TYPE_FLOAT  = 3;

    protected STBImageResize() {
        throw new UnsupportedOperationException();
    }

    static {
        LibSTB.initialize();
    }

    // ------------------- Old (v1) native bindings -------------------

    public static native int nstbir_resize_uint8(long input_pixels, int input_w, int input_h, int input_stride_in_bytes, long output_pixels, int output_w, int output_h, int output_stride_in_bytes, int num_channels);
    public static boolean stbir_resize_uint8(ByteBuffer input_pixels, int input_w, int input_h, int input_stride_in_bytes, ByteBuffer output_pixels, int output_w, int output_h, int output_stride_in_bytes, int num_channels) {
        return nstbir_resize_uint8(memAddress(input_pixels), input_w, input_h, input_stride_in_bytes, memAddress(output_pixels), output_w, output_h, output_stride_in_bytes, num_channels) != 0;
    }

    public static native int nstbir_resize_float(long input_pixels, int input_w, int input_h, int input_stride_in_bytes, long output_pixels, int output_w, int output_h, int output_stride_in_bytes, int num_channels);
    public static boolean stbir_resize_float(FloatBuffer input_pixels, int input_w, int input_h, int input_stride_in_bytes, FloatBuffer output_pixels, int output_w, int output_h, int output_stride_in_bytes, int num_channels) {
        return nstbir_resize_float(memAddress(input_pixels), input_w, input_h, input_stride_in_bytes, memAddress(output_pixels), output_w, output_h, output_stride_in_bytes, num_channels) != 0;
    }

    public static native int nstbir_resize_uint8_srgb(long input_pixels, int input_w, int input_h, int input_stride_in_bytes, long output_pixels, int output_w, int output_h, int output_stride_in_bytes, int num_channels, int alpha_channel, int flags);
    public static boolean stbir_resize_uint8_srgb(ByteBuffer input_pixels, int input_w, int input_h, int input_stride_in_bytes, ByteBuffer output_pixels, int output_w, int output_h, int output_stride_in_bytes, int num_channels, int alpha_channel, int flags) {
        return nstbir_resize_uint8_srgb(memAddress(input_pixels), input_w, input_h, input_stride_in_bytes, memAddress(output_pixels), output_w, output_h, output_stride_in_bytes, num_channels, alpha_channel, flags) != 0;
    }

    public static native int nstbir_resize_uint8_srgb_edgemode(long input_pixels, int input_w, int input_h, int input_stride_in_bytes, long output_pixels, int output_w, int output_h, int output_stride_in_bytes, int num_channels, int alpha_channel, int flags, int edge_wrap_mode);
    public static boolean stbir_resize_uint8_srgb_edgemode(ByteBuffer input_pixels, int input_w, int input_h, int input_stride_in_bytes, ByteBuffer output_pixels, int output_w, int output_h, int output_stride_in_bytes, int num_channels, int alpha_channel, int flags, int edge_wrap_mode) {
        return nstbir_resize_uint8_srgb_edgemode(memAddress(input_pixels), input_w, input_h, input_stride_in_bytes, memAddress(output_pixels), output_w, output_h, output_stride_in_bytes, num_channels, alpha_channel, flags, edge_wrap_mode) != 0;
    }

    public static native int nstbir_resize_uint8_generic(long input_pixels, int input_w, int input_h, int input_stride_in_bytes, long output_pixels, int output_w, int output_h, int output_stride_in_bytes, int num_channels, int alpha_channel, int flags, int edge_wrap_mode, int filter, int space, long alloc_context);
    public static boolean stbir_resize_uint8_generic(ByteBuffer input_pixels, int input_w, int input_h, int input_stride_in_bytes, ByteBuffer output_pixels, int output_w, int output_h, int output_stride_in_bytes, int num_channels, int alpha_channel, int flags, int edge_wrap_mode, int filter, int space) {
        return nstbir_resize_uint8_generic(memAddress(input_pixels), input_w, input_h, input_stride_in_bytes, memAddress(output_pixels), output_w, output_h, output_stride_in_bytes, num_channels, alpha_channel, flags, edge_wrap_mode, filter, space, 0L) != 0;
    }

    public static native int nstbir_resize_uint16_generic(long input_pixels, int input_w, int input_h, int input_stride_in_bytes, long output_pixels, int output_w, int output_h, int output_stride_in_bytes, int num_channels, int alpha_channel, int flags, int edge_wrap_mode, int filter, int space, long alloc_context);
    public static boolean stbir_resize_uint16_generic(ShortBuffer input_pixels, int input_w, int input_h, int input_stride_in_bytes, ShortBuffer output_pixels, int output_w, int output_h, int output_stride_in_bytes, int num_channels, int alpha_channel, int flags, int edge_wrap_mode, int filter, int space) {
        return nstbir_resize_uint16_generic(memAddress(input_pixels), input_w, input_h, input_stride_in_bytes, memAddress(output_pixels), output_w, output_h, output_stride_in_bytes, num_channels, alpha_channel, flags, edge_wrap_mode, filter, space, 0L) != 0;
    }

    public static native int nstbir_resize_float_generic(long input_pixels, int input_w, int input_h, int input_stride_in_bytes, long output_pixels, int output_w, int output_h, int output_stride_in_bytes, int num_channels, int alpha_channel, int flags, int edge_wrap_mode, int filter, int space, long alloc_context);
    public static boolean stbir_resize_float_generic(FloatBuffer input_pixels, int input_w, int input_h, int input_stride_in_bytes, FloatBuffer output_pixels, int output_w, int output_h, int output_stride_in_bytes, int num_channels, int alpha_channel, int flags, int edge_wrap_mode, int filter, int space) {
        return nstbir_resize_float_generic(memAddress(input_pixels), input_w, input_h, input_stride_in_bytes, memAddress(output_pixels), output_w, output_h, output_stride_in_bytes, num_channels, alpha_channel, flags, edge_wrap_mode, filter, space, 0L) != 0;
    }

    public static native int nstbir_resize(long input_pixels, int input_w, int input_h, int input_stride_in_bytes, long output_pixels, int output_w, int output_h, int output_stride_in_bytes, int datatype, int num_channels, int alpha_channel, int flags, int edge_mode_horizontal, int edge_mode_vertical, int filter_horizontal, int filter_vertical, int space, long alloc_context);
    public static boolean stbir_resize(ByteBuffer input_pixels, int input_w, int input_h, int input_stride_in_bytes, ByteBuffer output_pixels, int output_w, int output_h, int output_stride_in_bytes, int datatype, int num_channels, int alpha_channel, int flags, int edge_mode_horizontal, int edge_mode_vertical, int filter_horizontal, int filter_vertical, int space) {
        return nstbir_resize(memAddress(input_pixels), input_w, input_h, input_stride_in_bytes, memAddress(output_pixels), output_w, output_h, output_stride_in_bytes, datatype, num_channels, alpha_channel, flags, edge_mode_horizontal, edge_mode_vertical, filter_horizontal, filter_vertical, space, 0L) != 0;
    }

    public static native int nstbir_resize_subpixel(long input_pixels, int input_w, int input_h, int input_stride_in_bytes, long output_pixels, int output_w, int output_h, int output_stride_in_bytes, int datatype, int num_channels, int alpha_channel, int flags, int edge_mode_horizontal, int edge_mode_vertical, int filter_horizontal, int filter_vertical, int space, long alloc_context, float x_scale, float y_scale, float x_offset, float y_offset);
    public static boolean stbir_resize_subpixel(ByteBuffer input_pixels, int input_w, int input_h, int input_stride_in_bytes, ByteBuffer output_pixels, int output_w, int output_h, int output_stride_in_bytes, int datatype, int num_channels, int alpha_channel, int flags, int edge_mode_horizontal, int edge_mode_vertical, int filter_horizontal, int filter_vertical, int space, float x_scale, float y_scale, float x_offset, float y_offset) {
        return nstbir_resize_subpixel(memAddress(input_pixels), input_w, input_h, input_stride_in_bytes, memAddress(output_pixels), output_w, output_h, output_stride_in_bytes, datatype, num_channels, alpha_channel, flags, edge_mode_horizontal, edge_mode_vertical, filter_horizontal, filter_vertical, space, 0L, x_scale, y_scale, x_offset, y_offset) != 0;
    }

    public static native int nstbir_resize_region(long input_pixels, int input_w, int input_h, int input_stride_in_bytes, long output_pixels, int output_w, int output_h, int output_stride_in_bytes, int datatype, int num_channels, int alpha_channel, int flags, int edge_mode_horizontal, int edge_mode_vertical, int filter_horizontal, int filter_vertical, int space, long alloc_context, float s0, float t0, float s1, float t1);
    public static boolean stbir_resize_region(ByteBuffer input_pixels, int input_w, int input_h, int input_stride_in_bytes, ByteBuffer output_pixels, int output_w, int output_h, int output_stride_in_bytes, int datatype, int num_channels, int alpha_channel, int flags, int edge_mode_horizontal, int edge_mode_vertical, int filter_horizontal, int filter_vertical, int space, float s0, float t0, float s1, float t1) {
        return nstbir_resize_region(memAddress(input_pixels), input_w, input_h, input_stride_in_bytes, memAddress(output_pixels), output_w, output_h, output_stride_in_bytes, datatype, num_channels, alpha_channel, flags, edge_mode_horizontal, edge_mode_vertical, filter_horizontal, filter_vertical, space, 0L, s0, t0, s1, t1) != 0;
    }

    // Array overloads (v1)
    public static native int nstbir_resize_float(float[] input_pixels, int input_w, int input_h, int input_stride_in_bytes, float[] output_pixels, int output_w, int output_h, int output_stride_in_bytes, int num_channels);
    public static boolean stbir_resize_float(float[] input_pixels, int input_w, int input_h, int input_stride_in_bytes, float[] output_pixels, int output_w, int output_h, int output_stride_in_bytes, int num_channels) {
        return nstbir_resize_float(input_pixels, input_w, input_h, input_stride_in_bytes, output_pixels, output_w, output_h, output_stride_in_bytes, num_channels) != 0;
    }

    public static native int nstbir_resize_uint16_generic(short[] input_pixels, int input_w, int input_h, int input_stride_in_bytes, short[] output_pixels, int output_w, int output_h, int output_stride_in_bytes, int num_channels, int alpha_channel, int flags, int edge_wrap_mode, int filter, int space, long alloc_context);
    public static boolean stbir_resize_uint16_generic(short[] input_pixels, int input_w, int input_h, int input_stride_in_bytes, short[] output_pixels, int output_w, int output_h, int output_stride_in_bytes, int num_channels, int alpha_channel, int flags, int edge_wrap_mode, int filter, int space) {
        return nstbir_resize_uint16_generic(input_pixels, input_w, input_h, input_stride_in_bytes, output_pixels, output_w, output_h, output_stride_in_bytes, num_channels, alpha_channel, flags, edge_wrap_mode, filter, space, 0L) != 0;
    }

    public static native int nstbir_resize_float_generic(float[] input_pixels, int input_w, int input_h, int input_stride_in_bytes, float[] output_pixels, int output_w, int output_h, int output_stride_in_bytes, int num_channels, int alpha_channel, int flags, int edge_wrap_mode, int filter, int space, long alloc_context);
    public static boolean stbir_resize_float_generic(float[] input_pixels, int input_w, int input_h, int input_stride_in_bytes, float[] output_pixels, int output_w, int output_h, int output_stride_in_bytes, int num_channels, int alpha_channel, int flags, int edge_wrap_mode, int filter, int space) {
        return nstbir_resize_float_generic(input_pixels, input_w, input_h, input_stride_in_bytes, output_pixels, output_w, output_h, output_stride_in_bytes, num_channels, alpha_channel, flags, edge_wrap_mode, filter, space, 0L) != 0;
    }

    // ------------------- New (v2) shim for LWJGL 3.3.3 -------------------

    /**
     * LWJGL 3.3.3 shim: {@code stbir_resize_uint8_linear} from stb_image_resize2.h.
     * <p>Delegates to the old {@link #nstbir_resize_uint8} and returns the output pointer on
     * success (matching the new API contract) or {@code 0} on failure.</p>
     *
     * @return output pixel buffer address on success, {@code 0} on failure
     */
    public static long nstbir_resize_uint8_linear(long input_pixels, int input_w, int input_h, int input_stride_in_bytes,
                                                   long output_pixels, int output_w, int output_h, int output_stride_in_bytes,
                                                   int num_channels) {
        int ok = nstbir_resize_uint8(input_pixels, input_w, input_h, input_stride_in_bytes,
                                     output_pixels, output_w, output_h, output_stride_in_bytes, num_channels);
        return ok != 0 ? output_pixels : 0L;
    }
}
