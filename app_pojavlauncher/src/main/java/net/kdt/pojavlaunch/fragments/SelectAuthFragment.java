package net.kdt.pojavlaunch.fragments;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.fragment.app.Fragment;

import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.PojavProfile;
import net.kdt.pojavlaunch.Tools;

public class SelectAuthFragment extends Fragment {
    public static final String TAG = "AUTH_SELECT_FRAGMENT";

    public SelectAuthFragment(){
        super(R.layout.fragment_select_auth_method);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        Button mBattlyButton = view.findViewById(R.id.button_battly_authentication);
        Button mMicrosoftButton = view.findViewById(R.id.button_microsoft_authentication);
        Button mLocalButton = view.findViewById(R.id.button_local_authentication);
        ImageButton mBackButton = view.findViewById(R.id.auth_back_button);
        boolean isRequiredLogin = PojavProfile.getAllProfilesList().isEmpty();
        mBackButton.setVisibility(isRequiredLogin ? View.GONE : View.VISIBLE);

        // Set icon size to 20dp for both auth buttons
        int iconSizePx = (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, 20f,
                requireContext().getResources().getDisplayMetrics());

        Drawable battlyIcon = loadScaledIcon(R.drawable.logo, iconSizePx);
        if (battlyIcon != null) {
            mBattlyButton.setCompoundDrawablesRelative(null, null, battlyIcon, null);
            mBattlyButton.setCompoundDrawablePadding(
                    (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 8f,
                            requireContext().getResources().getDisplayMetrics()));
        }

        Drawable msIcon = loadScaledIcon(R.drawable.ic_ms_logo, iconSizePx);
        if (msIcon != null) {
            mMicrosoftButton.setCompoundDrawablesRelative(null, null, msIcon, null);
            mMicrosoftButton.setCompoundDrawablePadding(
                    (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 8f,
                            requireContext().getResources().getDisplayMetrics()));
        }

        Drawable localIcon = loadScaledIcon(R.drawable.ic_menu_home, iconSizePx);
        if (localIcon != null) {
            mLocalButton.setCompoundDrawablesRelative(null, null, localIcon, null);
            mLocalButton.setCompoundDrawablePadding(
                (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 8f,
                    requireContext().getResources().getDisplayMetrics()));
        }

        mBattlyButton.setOnClickListener(v -> Tools.swapFragment(requireActivity(), BattlyLoginFragment.class, BattlyLoginFragment.TAG, null));
        mMicrosoftButton.setOnClickListener(v -> Tools.swapFragment(requireActivity(), MicrosoftLoginFragment.class, MicrosoftLoginFragment.TAG, null));
        mLocalButton.setOnClickListener(v -> Tools.swapFragment(requireActivity(), LocalLoginFragment.class, LocalLoginFragment.TAG, null));
        mBackButton.setOnClickListener(v -> requireActivity().onBackPressed());
    }

    /** Render a drawable resource to a scaled bitmap, strip near-white background, and return it as a Drawable. */
    private Drawable loadScaledIcon(int resId, int targetPx) {
        Drawable source = AppCompatResources.getDrawable(requireContext(), resId);
        if (source == null) return null;

        Bitmap argb = Bitmap.createBitmap(targetPx, targetPx, Bitmap.Config.ARGB_8888);
        android.graphics.Canvas canvas = new android.graphics.Canvas(argb);
        source.setBounds(0, 0, targetPx, targetPx);
        source.draw(canvas);
        int w = argb.getWidth(), h = argb.getHeight();
        int[] pixels = new int[w * h];
        argb.getPixels(pixels, 0, w, 0, 0, w, h);
        for (int i = 0; i < pixels.length; i++) {
            int p = pixels[i];
            if (Color.alpha(p) == 255
                    && Color.red(p) >= 240
                    && Color.green(p) >= 240
                    && Color.blue(p) >= 240) {
                pixels[i] = Color.TRANSPARENT;
            }
        }
        argb.setPixels(pixels, 0, w, 0, 0, w, h);
        BitmapDrawable drawable = new BitmapDrawable(requireContext().getResources(), argb);
        drawable.setBounds(0, 0, targetPx, targetPx);
        return drawable;
    }
}
