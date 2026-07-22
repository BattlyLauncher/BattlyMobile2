package net.kdt.pojavlaunch.prefs;

import android.content.Context;
import android.util.AttributeSet;

import androidx.preference.ListPreference;

/**
 * ListPreference storage without the stock Material/AppCompat chooser.
 * The renderer screen uses a Battly-styled chooser and must not open both UIs.
 */
public class NoDialogListPreference extends ListPreference {
    public NoDialogListPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public NoDialogListPreference(Context context) {
        super(context);
    }

    @Override
    protected void onClick() {
        // Consumed by the explicit OnPreferenceClickListener.
    }
}
