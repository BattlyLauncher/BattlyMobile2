package net.kdt.pojavlaunch.fragments;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import net.kdt.pojavlaunch.BuildConfig;
import net.kdt.pojavlaunch.R;

public class CreditsFragment extends Fragment {
    public static final String TAG = "CreditsFragment";

    public CreditsFragment() {
        super(R.layout.dialog_credits);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        TextView version = view.findViewById(R.id.credits_version);
        version.setText(getString(R.string.credits_version_dynamic, BuildConfig.VERSION_NAME));
    }
}
