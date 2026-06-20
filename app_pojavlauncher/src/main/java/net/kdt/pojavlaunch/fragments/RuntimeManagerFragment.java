package net.kdt.pojavlaunch.fragments;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.multirt.RTRecyclerViewAdapter;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;
import net.kdt.pojavlaunch.utils.JREAutoDownloader;

public class RuntimeManagerFragment extends Fragment {
    public static final String TAG = "RuntimeManagerFragment";

    private RTRecyclerViewAdapter adapter;

    public RuntimeManagerFragment() {
        super(R.layout.fragment_runtime_manager);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        RecyclerView recyclerView = view.findViewById(R.id.runtime_recycler);
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new RTRecyclerViewAdapter();
        recyclerView.setAdapter(adapter);

        bindDownloadButton(view.findViewById(R.id.runtime_download_jre8), 8);
        bindDownloadButton(view.findViewById(R.id.runtime_download_jre17), 17);
        bindDownloadButton(view.findViewById(R.id.runtime_download_jre21), 21);
        bindDownloadButton(view.findViewById(R.id.runtime_download_jre25), 25);
    }

    private void bindDownloadButton(Button button, int javaVersion) {
        button.setOnClickListener(v -> {
            button.setEnabled(false);
            Toast.makeText(requireContext(), getString(R.string.multirt_download_started, javaVersion), Toast.LENGTH_SHORT).show();
            JREAutoDownloader.downloadJREAsync(javaVersion, new JREAutoDownloader.DownloadCallback() {
                @Override
                public void onSuccess(String jreName) {
                    if (!isAdded()) {
                        return;
                    }
                    requireActivity().runOnUiThread(() -> {
                        button.setEnabled(true);
                        LauncherPreferences.loadPreferences(requireContext());
                        adapter.notifyDataSetChanged();
                        Toast.makeText(requireContext(), getString(R.string.multirt_download_finished, jreName), Toast.LENGTH_SHORT).show();
                    });
                }

                @Override
                public void onError(Exception e) {
                    if (!isAdded()) {
                        return;
                    }
                    requireActivity().runOnUiThread(() -> {
                        button.setEnabled(true);
                        Toast.makeText(requireContext(), e.getMessage(), Toast.LENGTH_LONG).show();
                    });
                }
            });
        });
    }
}
