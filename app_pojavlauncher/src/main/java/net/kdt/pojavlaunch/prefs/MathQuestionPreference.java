package net.kdt.pojavlaunch.prefs;

import android.content.Context;
import android.util.AttributeSet;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.preference.SwitchPreferenceCompat;

import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;

public class MathQuestionPreference extends SwitchPreferenceCompat {
    public MathQuestionPreference(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onClick() {
        if (isChecked()) { // Don't ask for confirmation if turning off
            super.onClick();
            return;
        }

        final Context ctx = getContext();
        AlertDialog dialog = new AlertDialog.Builder(ctx, R.style.BattlyDialog)
                .setTitle(R.string.sodium_confirm_title)
                .setIcon(R.drawable.minecraft_tnt)
                .setMessage(R.string.sodium_confirm_message)
                .setPositiveButton(R.string.sodium_confirm_accept, (d, w) -> super.onClick())
                .setNegativeButton(android.R.string.cancel, null)
                .create();
        Tools.styleDialog(dialog);
        dialog.show();
    }
}