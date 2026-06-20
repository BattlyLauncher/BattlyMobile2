package net.kdt.pojavlaunch.fragments;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import net.kdt.pojavlaunch.CustomControlsActivity;
import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;
import net.kdt.pojavlaunch.utils.BattlyPlusManager;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class ControlMarketplaceFragment extends Fragment {
    public static final String TAG = "ControlMarketplaceFragment";

    private static final String API_BASE = "https://api.battlylauncher.com/battlylauncher/controls";

    private RecyclerView mRecyclerView;
    private ControlMarketplaceAdapter mAdapter;
    private ProgressBar mProgressBar;
    private View mEmptyView;
    private EditText mSearchInput;
    private Button mSearchBtn;
    private Button mUploadBtn;
    private TextView mPageInfo;
    private ImageButton mPrevBtn;
    private ImageButton mNextBtn;
    private Button mSortNewest;
    private Button mSortPopular;
    private Button mSortLiked;

    private int mCurrentPage = 1;
    private int mTotalPages = 1;
    private String mCurrentSort = "newest";

    public ControlMarketplaceFragment() {
        super(R.layout.fragment_control_marketplace);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        mRecyclerView = view.findViewById(R.id.ctrl_recycler);
        mProgressBar = view.findViewById(R.id.ctrl_progress);
        mEmptyView = view.findViewById(R.id.ctrl_empty_text);
        mSearchInput = view.findViewById(R.id.ctrl_search_input);
        mSearchBtn = view.findViewById(R.id.ctrl_search_btn);
        mUploadBtn = view.findViewById(R.id.ctrl_upload_btn);
        mPageInfo = view.findViewById(R.id.ctrl_page_info);
        mPrevBtn = view.findViewById(R.id.ctrl_prev_btn);
        mNextBtn = view.findViewById(R.id.ctrl_next_btn);
        mSortNewest = view.findViewById(R.id.ctrl_sort_newest);
        mSortPopular = view.findViewById(R.id.ctrl_sort_popular);
        mSortLiked = view.findViewById(R.id.ctrl_sort_liked);

        ImageButton backBtn = view.findViewById(R.id.ctrl_back_btn);
        backBtn.setOnClickListener(v -> requireActivity().getSupportFragmentManager().popBackStack());

        mRecyclerView.setLayoutManager(new GridLayoutManager(requireContext(), 2));
        mAdapter = new ControlMarketplaceAdapter(new ControlMarketplaceAdapter.OnApplyListener() {
            @Override
            public void onApply(JSONObject item) {
                onApplyControl(item);
            }

            @Override
            public void onPreview(JSONObject item) {
                onPreviewControl(item);
            }
        });
        mRecyclerView.setAdapter(mAdapter);

        mSearchBtn.setOnClickListener(v -> {
            mCurrentPage = 1;
            loadControls();
        });

        mUploadBtn.setOnClickListener(v -> showUploadDialog());

        mPrevBtn.setOnClickListener(v -> {
            if (mCurrentPage > 1) {
                mCurrentPage--;
                loadControls();
            }
        });
        mNextBtn.setOnClickListener(v -> {
            if (mCurrentPage < mTotalPages) {
                mCurrentPage++;
                loadControls();
            }
        });

        view.findViewById(R.id.ctrl_sort_newest).setOnClickListener(v -> {
            mCurrentSort = "newest";
            mCurrentPage = 1;
            updateSortChips();
            loadControls();
        });
        view.findViewById(R.id.ctrl_sort_popular).setOnClickListener(v -> {
            mCurrentSort = "popular";
            mCurrentPage = 1;
            updateSortChips();
            loadControls();
        });
        view.findViewById(R.id.ctrl_sort_liked).setOnClickListener(v -> {
            mCurrentSort = "liked";
            mCurrentPage = 1;
            updateSortChips();
            loadControls();
        });

        loadControls();
    }

    private void updateSortChips() {
        int active = R.drawable.bg_ctrl_sort_active;
        int inactive = R.drawable.bg_battly_version_option;
        mSortNewest.setBackgroundResource("newest".equals(mCurrentSort) ? active : inactive);
        mSortPopular.setBackgroundResource("popular".equals(mCurrentSort) ? active : inactive);
        mSortLiked.setBackgroundResource("liked".equals(mCurrentSort) ? active : inactive);
    }

    private void loadControls() {
        setLoading(true);
        mEmptyView.setVisibility(View.GONE);

        new Thread(() -> {
            try {
                String q = mSearchInput.getText().toString().trim();
                StringBuilder urlStr = new StringBuilder(API_BASE)
                        .append("?page=").append(mCurrentPage)
                        .append("&limit=20")
                        .append("&sort=").append(URLEncoder.encode(mCurrentSort, "UTF-8"));
                if (!q.isEmpty()) {
                    urlStr.append("&q=").append(URLEncoder.encode(q, "UTF-8"));
                }

                URL url = new URL(urlStr.toString());
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);

                int code = conn.getResponseCode();
                Log.d(TAG, "loadControls HTTP " + code + " url=" + urlStr);
                if (code == 200) {
                    String body = readStream(conn.getInputStream());
                    Log.d(TAG, "loadControls body=" + body);
                    JSONObject resp = new JSONObject(body);
                    final JSONArray items = resp.optJSONArray("items") != null
                            ? resp.optJSONArray("items") : new JSONArray();
                    final int pages = resp.optInt("pages", 1);
                    final int total = resp.optInt("total", 0);
                    mTotalPages = pages;

                    if (!isAdded()) return;
                    requireActivity().runOnUiThread(() -> {
                        setLoading(false);
                        mAdapter.setItems(items);
                        mPageInfo.setText(getString(R.string.ctrl_page_info, mCurrentPage, mTotalPages));
                        mPrevBtn.setEnabled(mCurrentPage > 1);
                        mNextBtn.setEnabled(mCurrentPage < mTotalPages);
                        if (total == 0) {
                            mEmptyView.setVisibility(View.VISIBLE);
                        }
                    });
                } else {
                    String errBody = readStream(conn.getErrorStream());
                    Log.e(TAG, "loadControls non-200: code=" + code + " body=" + errBody);
                    showErrorOnUi(getString(R.string.ctrl_error_load));
                }
            } catch (Exception e) {
                Log.e(TAG, "loadControls exception", e);
                showErrorOnUi(getString(R.string.ctrl_error_network));
            }
        }).start();
    }

    private void onApplyControl(JSONObject item) {
        String id = item.optString("_id", "");
        String name = item.optString("name", "control");
        if (id.isEmpty()) return;

        new AlertDialog.Builder(requireContext(), R.style.BattlyDialog)
                .setTitle(R.string.ctrl_apply_title)
                .setMessage(getString(R.string.ctrl_apply_msg, name))
                .setPositiveButton(android.R.string.ok, (d, w) -> downloadAndApply(id, name))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void onPreviewControl(JSONObject item) {
        String id = item.optString("_id", "");
        String name = item.optString("name", getString(R.string.customctrl_preview_fallback_name));
        if (id.isEmpty()) return;

        mProgressBar.setVisibility(View.VISIBLE);
        new Thread(() -> {
            try {
                String body = downloadControlJson(id);
                File previewDir = new File(requireContext().getCacheDir(), "control-previews");
                if (!previewDir.exists()) previewDir.mkdirs();
                File previewFile = new File(previewDir, safeFileName(name) + ".json");
                try (FileWriter fw = new FileWriter(previewFile)) {
                    fw.write(body);
                }

                if (!isAdded()) return;
                requireActivity().runOnUiThread(() -> {
                    mProgressBar.setVisibility(View.GONE);
                    Intent intent = new Intent(requireContext(), CustomControlsActivity.class);
                    intent.putExtra(CustomControlsActivity.EXTRA_PREVIEW_CONTROL_PATH,
                            previewFile.getAbsolutePath());
                    intent.putExtra(CustomControlsActivity.EXTRA_PREVIEW_CONTROL_NAME, name);
                    startActivity(intent);
                });
            } catch (Exception e) {
                showErrorOnUi(getString(R.string.ctrl_error_preview));
            }
        }).start();
    }

    private void downloadAndApply(String id, String name) {
        mProgressBar.setVisibility(View.VISIBLE);
        new Thread(() -> {
            try {
                String body = downloadControlJson(id);
                String safeName = safeFileName(name);
                File ctrlDir = new File(Tools.CTRLMAP_PATH);
                if (!ctrlDir.exists()) ctrlDir.mkdirs();
                File outFile = new File(ctrlDir, safeName + ".json");

                try (FileWriter fw = new FileWriter(outFile)) {
                    fw.write(body);
                }

                if (!isAdded()) return;
                requireActivity().runOnUiThread(() -> {
                    mProgressBar.setVisibility(View.GONE);
                    new AlertDialog.Builder(requireContext(), R.style.BattlyDialog)
                            .setTitle(R.string.ctrl_set_default_title)
                            .setMessage(getString(R.string.ctrl_set_default_msg, safeName))
                            .setPositiveButton(R.string.ctrl_set_default_yes, (d, w) -> {
                                String path = outFile.getAbsolutePath();
                                LauncherPreferences.PREF_DEFAULTCTRL_PATH = path;
                                LauncherPreferences.DEFAULT_PREF.edit()
                                        .putString("defaultCtrl", path)
                                        .apply();
                                Toast.makeText(requireContext(),
                                        getString(R.string.ctrl_applied_success, safeName),
                                        Toast.LENGTH_LONG).show();
                            })
                            .setNegativeButton(R.string.ctrl_set_default_no, (d, w) ->
                                    Toast.makeText(requireContext(),
                                            getString(R.string.ctrl_applied_success, safeName),
                                            Toast.LENGTH_LONG).show())
                            .show();
                });
            } catch (Exception e) {
                showErrorOnUi(getString(R.string.ctrl_error_download));
            }
        }).start();
    }

    private String downloadControlJson(String id) throws Exception {
        URL url = new URL(API_BASE + "/" + id + "/download");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(15000);

        int code = conn.getResponseCode();
        if (code != 200) {
            throw new Exception("Control download failed: HTTP " + code);
        }

        String body = readStream(conn.getInputStream());
        JSONObject controlData = new JSONObject(body);
        if (!controlData.has("mControlDataList")) {
            throw new Exception("Invalid control format");
        }
        return body;
    }

    private static String safeFileName(String name) {
        String safeName = name.replaceAll("[^a-zA-Z0-9_\\- ]", "_").trim();
        return safeName.isEmpty() ? "control" : safeName;
    }

    private void showUploadDialog() {
        Context ctx = requireContext();
        String token = BattlyPlusManager.getToken(ctx);

        if (token.isEmpty()) {
            new AlertDialog.Builder(ctx, R.style.BattlyDialog)
                    .setTitle(R.string.ctrl_upload_auth_title)
                    .setMessage(R.string.ctrl_upload_auth_msg)
                    .setPositiveButton(android.R.string.ok, null)
                    .show();
            return;
        }

        File ctrlDir = new File(Tools.CTRLMAP_PATH);
        File[] files = ctrlDir.exists() ? ctrlDir.listFiles(f -> f.getName().endsWith(".json")) : null;

        if (files == null || files.length == 0) {
            Toast.makeText(ctx, R.string.ctrl_upload_no_files, Toast.LENGTH_LONG).show();
            return;
        }

        String[] fileNames = new String[files.length];
        for (int i = 0; i < files.length; i++) {
            fileNames[i] = files[i].getName().replace(".json", "");
        }

        File[] finalFiles = files;
        new AlertDialog.Builder(ctx, R.style.BattlyDialog)
                .setTitle(R.string.ctrl_upload_select)
                .setItems(fileNames, (d, which) -> showUploadDetailsDialog(finalFiles[which], token))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void showUploadDetailsDialog(File controlFile, String token) {
        View v = requireActivity().getLayoutInflater().inflate(R.layout.dialog_ctrl_upload, null);
        EditText nameEdit = v.findViewById(R.id.ctrl_upload_name);
        EditText descEdit = v.findViewById(R.id.ctrl_upload_desc);
        EditText tagsEdit = v.findViewById(R.id.ctrl_upload_tags);

        String defaultName = controlFile.getName().replace(".json", "");
        nameEdit.setText(defaultName);

        new AlertDialog.Builder(requireContext(), R.style.BattlyDialog)
                .setTitle(R.string.ctrl_upload_details)
                .setView(v)
                .setPositiveButton(R.string.ctrl_upload_publish, (d, w) -> {
                    String name = nameEdit.getText().toString().trim();
                    String desc = descEdit.getText().toString().trim();
                    String tagsRaw = tagsEdit.getText().toString().trim();
                    if (name.isEmpty()) {
                        Toast.makeText(requireContext(), R.string.ctrl_upload_name_required, Toast.LENGTH_SHORT).show();
                        return;
                    }
                    uploadControl(controlFile, name, desc, tagsRaw, token);
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void uploadControl(File controlFile, String name, String desc, String tagsRaw, String token) {
        mProgressBar.setVisibility(View.VISIBLE);
        new Thread(() -> {
            try {
                StringBuilder sb = new StringBuilder();
                try (BufferedReader br = new BufferedReader(new java.io.FileReader(controlFile))) {
                    String line;
                    while ((line = br.readLine()) != null) sb.append(line);
                }
                JSONObject controlData = new JSONObject(sb.toString());

                JSONArray tagsArray = new JSONArray();
                if (!tagsRaw.isEmpty()) {
                    String[] parts = tagsRaw.split(",");
                    for (String p : parts) {
                        String t = p.trim();
                        if (!t.isEmpty()) tagsArray.put(t);
                    }
                }

                JSONObject body = new JSONObject();
                body.put("name", name);
                body.put("description", desc);
                body.put("tags", tagsArray);
                body.put("controlData", controlData);

                byte[] bodyBytes = body.toString().getBytes(StandardCharsets.UTF_8);
                int code = 0;
                String respBody = "";
                List<String> tokens = BattlyPlusManager.getTokenCandidates(requireContext());
                for (String candidate : tokens) {
                    URL url = new URL(API_BASE + "/upload");
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("POST");
                    conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                    conn.setRequestProperty("Authorization", "Bearer " + candidate);
                    conn.setDoOutput(true);
                    conn.setConnectTimeout(15000);
                    conn.setReadTimeout(15000);
                    try (OutputStream os = conn.getOutputStream()) {
                        os.write(bodyBytes);
                    }
                    code = conn.getResponseCode();
                    respBody = readStream(code == 200 || code == 201
                            ? conn.getInputStream() : conn.getErrorStream());
                    conn.disconnect();
                    if (code != 401 && code != 403) {
                        break;
                    }
                }

                if (code == 200 || code == 201) {
                    if (!isAdded()) return;
                    requireActivity().runOnUiThread(() -> {
                        mProgressBar.setVisibility(View.GONE);
                        Toast.makeText(requireContext(),
                                R.string.ctrl_upload_pending_review,
                                Toast.LENGTH_LONG).show();
                        loadControls();
                    });
                } else {
                    JSONObject errResp = new JSONObject(respBody);
                    String errMsg = errResp.optString("error", getString(R.string.ctrl_error_upload));
                    showErrorOnUi(errMsg);
                }
            } catch (Exception e) {
                showErrorOnUi(getString(R.string.ctrl_error_upload));
            }
        }).start();
    }

    private void setLoading(boolean loading) {
        if (getActivity() == null) return;
        requireActivity().runOnUiThread(() -> {
            mProgressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
            mRecyclerView.setVisibility(loading ? View.GONE : View.VISIBLE);
        });
    }

    private void showErrorOnUi(String msg) {
        if (getActivity() == null) return;
        requireActivity().runOnUiThread(() -> {
            mProgressBar.setVisibility(View.GONE);
            mRecyclerView.setVisibility(View.VISIBLE);
            Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show();
        });
    }

    private static String readStream(InputStream is) throws Exception {
        if (is == null) return "{}";
        try (BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
            return sb.toString();
        }
    }
}
