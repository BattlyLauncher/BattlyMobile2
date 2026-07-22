package net.kdt.pojavlaunch.fragments;

import android.os.Bundle;
import android.net.Uri;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.multirt.RTRecyclerViewAdapter;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;
import net.kdt.pojavlaunch.utils.JREAutoDownloader;
import net.kdt.pojavlaunch.utils.RuntimeHealthManager;
import net.kdt.pojavlaunch.multirt.MultiRTUtils;
import net.kdt.pojavlaunch.PojavApplication;
import net.kdt.pojavlaunch.Tools;

import java.io.InputStream;
import java.util.List;

public class RuntimeManagerFragment extends Fragment {
    public static final String TAG = "RuntimeManagerFragment";

    private RTRecyclerViewAdapter adapter;
    private final ActivityResultLauncher<String[]> runtimeImporter = registerForActivityResult(
            new ActivityResultContracts.OpenDocument(), this::importRuntime);

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
        view.findViewById(R.id.runtime_import).setOnClickListener(v ->
                runtimeImporter.launch(new String[]{"application/x-xz", "application/octet-stream", "application/x-tar"}));
        view.findViewById(R.id.runtime_health).setOnClickListener(v -> showHealth());
    }

    private void importRuntime(Uri uri) {
        if (uri == null) return;
        Toast.makeText(requireContext(), R.string.global_wait, Toast.LENGTH_SHORT).show();
        PojavApplication.sExecutorService.execute(() -> {
            try (InputStream input = requireContext().getContentResolver().openInputStream(uri)) {
                if (input == null) throw new IllegalStateException("Unable to open runtime archive");
                String name = "jre-imported-" + System.currentTimeMillis();
                MultiRTUtils.installRuntimeNamed(Tools.NATIVE_LIB_DIR, input, name);
                MultiRTUtils.postPrepare(name);
                RuntimeHealthManager.Health health = RuntimeHealthManager.inspect(name);
                if (!health.isHealthy()) {
                    MultiRTUtils.removeRuntimeNamed(name);
                    throw new IllegalStateException("Imported runtime is incomplete: " + health.missing);
                }
                Tools.runOnUiThread(() -> {
                    if (!isAdded()) return;
                    adapter.notifyDataSetChanged();
                    Toast.makeText(requireContext(), R.string.multirt_imported, Toast.LENGTH_LONG).show();
                });
            } catch (Throwable throwable) {
                Tools.runOnUiThread(() -> { if (isAdded()) Tools.showError(requireContext(), throwable); });
            }
        });
    }

    private void showHealth() {
        RuntimeHealthManager.removeBrokenDownloadDirectories();
        List<RuntimeHealthManager.Health> health = RuntimeHealthManager.inspectAll();
        java.util.ArrayList<RuntimeHealthManager.Health> broken = new java.util.ArrayList<>();
        for (RuntimeHealthManager.Health item : health) if (!item.isHealthy()) broken.add(item);
        if (broken.isEmpty()) {
            Toast.makeText(requireContext(), R.string.multirt_all_healthy, Toast.LENGTH_LONG).show();
            adapter.notifyDataSetChanged();
            return;
        }
        String[] labels = new String[broken.size()];
        for (int i = 0; i < labels.length; i++) {
            RuntimeHealthManager.Health item = broken.get(i);
            labels[i] = item.name + " | Java " + item.javaMajor + "\n" + item.missing;
        }
        Tools.showStyledDialog(Tools.createStyledDialogBuilder(requireContext())
                .setTitle(R.string.multirt_health)
                .setMessage(R.string.multirt_repair_prompt)
                .setItems(labels, (d, which) -> repairRuntime(broken.get(which))));
    }

    private void repairRuntime(RuntimeHealthManager.Health health) {
        PojavApplication.sExecutorService.execute(() -> {
            try {
                MultiRTUtils.removeRuntimeNamed(health.name);
                Tools.runOnUiThread(() -> {
                    adapter.notifyDataSetChanged();
                    if (health.javaMajor == 8 || health.javaMajor == 17 || health.javaMajor == 21 || health.javaMajor == 25) {
                        downloadRuntime(health.javaMajor, null);
                    }
                });
            } catch (Throwable throwable) {
                Tools.runOnUiThread(() -> { if (isAdded()) Tools.showError(requireContext(), throwable); });
            }
        });
    }

    private void downloadRuntime(int javaVersion, Button button) {
        if (button != null) button.setEnabled(false);
        Toast.makeText(requireContext(), getString(R.string.multirt_download_started, javaVersion), Toast.LENGTH_SHORT).show();
        JREAutoDownloader.downloadJREAsync(javaVersion, new JREAutoDownloader.DownloadCallback() {
            @Override public void onSuccess(String jreName) {
                if (!isAdded()) return;
                requireActivity().runOnUiThread(() -> {
                    if (button != null) button.setEnabled(true);
                    LauncherPreferences.loadPreferences(requireContext());
                    adapter.notifyDataSetChanged();
                    Toast.makeText(requireContext(), getString(R.string.multirt_download_finished, jreName), Toast.LENGTH_SHORT).show();
                });
            }
            @Override public void onError(Exception e) {
                if (!isAdded()) return;
                requireActivity().runOnUiThread(() -> {
                    if (button != null) button.setEnabled(true);
                    Toast.makeText(requireContext(), e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void bindDownloadButton(Button button, int javaVersion) {
        button.setOnClickListener(v -> downloadRuntime(javaVersion, button));
    }
}
