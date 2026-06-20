package net.kdt.pojavlaunch.fragments;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;

import net.kdt.pojavlaunch.LauncherActivity;
import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.battlyworlds.BattlyWorldsFeature;
import net.kdt.pojavlaunch.battlyworlds.BattlyWorldsInvites;
import net.kdt.pojavlaunch.extra.ExtraConstants;
import net.kdt.pojavlaunch.extra.ExtraCore;
import net.kdt.pojavlaunch.utils.BattlyPlusManager;
import net.kdt.pojavlaunch.utils.BattlyPlusWelcomeDialog;

import org.json.JSONObject;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

public class BattlyLoginFragment extends Fragment {
    public static final String TAG = "BATTLY_LOGIN_FRAGMENT";

    private static final String BATTLY_LOGIN_URL = "https://battlylauncher.com/api/battly/launcher/login";
    private static final String BATTLY_REGISTER_URL = "https://battlylauncher.com/register?utm_source=battlymobile&utm_medium=login_page&utm_campaign=registers_from_battly_mobile";

    private EditText mUsernameEditText;
    private EditText mPasswordEditText;
    private TextView mErrorTextView;
    private Button mLoginButton;
    private Button mRegisterButton;

    public BattlyLoginFragment() {
        super(R.layout.fragment_battly_login);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        mUsernameEditText = view.findViewById(R.id.battly_edit_username);
        mPasswordEditText = view.findViewById(R.id.battly_edit_password);
        mErrorTextView = view.findViewById(R.id.battly_error_text);
        mLoginButton = view.findViewById(R.id.battly_login_button);
        mRegisterButton = view.findViewById(R.id.battly_register_button);

        mLoginButton.setOnClickListener(v -> attemptLogin());
        mRegisterButton.setOnClickListener(v -> startActivity(
                new Intent(Intent.ACTION_VIEW, Uri.parse(BATTLY_REGISTER_URL))));
    }

    private void attemptLogin() {
        String username = mUsernameEditText.getText().toString().trim();
        String password = mPasswordEditText.getText().toString();

        if (username.isEmpty() || password.isEmpty()) {
            showError(getString(R.string.battly_login_missing_fields));
            return;
        }

        mLoginButton.setEnabled(false);
        mErrorTextView.setVisibility(View.GONE);

        new Thread(() -> {
            try {
                URL url = new URL(BATTLY_LOGIN_URL);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                conn.setDoOutput(true);
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);

                JSONObject body = new JSONObject();
                body.put("username", username);
                body.put("password", password);

                byte[] bodyBytes = body.toString().getBytes(StandardCharsets.UTF_8);
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(bodyBytes);
                }

                int responseCode = conn.getResponseCode();
                if (responseCode == 200) {
                    // Read response to get actual username (may differ in case)
                    java.io.BufferedReader reader = new java.io.BufferedReader(
                            new java.io.InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) sb.append(line);
                    reader.close();

                    String resolvedUsername = username;
                    String resolvedToken = "";
                    String resolvedUuid = "";
                    try {
                        JSONObject resp = new JSONObject(sb.toString());
                        String responseUsername = findString(resp, "username", "name");
                        if (!responseUsername.isEmpty()) {
                            resolvedUsername = responseUsername;
                        }
                        resolvedToken = findString(resp, "token", "accessToken", "access_token",
                                "sessionToken", "session_token", "launcherToken", "launcher_token",
                                "authToken", "auth_token");
                        resolvedUuid = findString(resp, "uuid", "id", "profileId", "profile_id");
                        if (looksLikeToken(resolvedUuid)) {
                            resolvedUuid = "";
                        }
                        BattlyPlusManager.updateFromLoginResponse(requireContext(), resp);
                    } catch (Exception ignored) {
                        // Use provided username as fallback
                    }
                    if (resolvedUuid.isEmpty()) {
                        resolvedUuid = UUID.nameUUIDFromBytes(
                                ("OfflinePlayer:" + resolvedUsername).getBytes(StandardCharsets.UTF_8)
                        ).toString();
                    }

                    // Persist token and username for marketplace use
                    if (!resolvedToken.isEmpty()) {
                        requireActivity().getSharedPreferences("battly_account", android.content.Context.MODE_PRIVATE)
                                .edit()
                                .putString("battly_token", resolvedToken)
                                .putString("battly_username", resolvedUsername)
                                .putString("battly_uuid", resolvedUuid)
                                .apply();
                        if (BattlyWorldsFeature.ENABLED) {
                            BattlyWorldsInvites.registerDeviceToken(requireContext());
                        }
                    }

                    final String finalUsername = resolvedUsername;
                    final String finalToken = resolvedToken.isEmpty() ? "battly" : resolvedToken;
                    final String finalUuid = resolvedUuid;
                    requireActivity().runOnUiThread(() -> {
                        FragmentActivity activity = requireActivity();
                        ExtraCore.setValue(ExtraConstants.MOJANG_LOGIN_TODO,
                                new String[]{finalUsername, finalToken, finalUuid});
                        Tools.swapFragment(activity, MainMenuFragment.class,
                                MainMenuFragment.TAG, null);
                        if (activity instanceof LauncherActivity) {
                            ((LauncherActivity) activity).runStartupPromptsAfterLogin();
                        } else if (BattlyPlusManager.isPlus(activity)) {
                            BattlyPlusWelcomeDialog.showAfterLogin(activity);
                        }
                    });
                } else {
                    requireActivity().runOnUiThread(() -> {
                        mLoginButton.setEnabled(true);
                        showError(getString(R.string.battly_login_error_credentials));
                    });
                }
            } catch (Exception e) {
                requireActivity().runOnUiThread(() -> {
                    mLoginButton.setEnabled(true);
                    showError(getString(R.string.battly_login_error_network));
                });
            }
        }).start();
    }

    private void showError(String message) {
        mErrorTextView.setText(message);
        mErrorTextView.setVisibility(View.VISIBLE);
    }

    private static String findString(JSONObject object, String... keys) {
        if (object == null) {
            return "";
        }
        for (String key : keys) {
            String value = object.optString(key, "");
            if (value != null && !value.trim().isEmpty()) {
                return value.trim();
            }
        }
        java.util.Iterator<String> iterator = object.keys();
        while (iterator.hasNext()) {
            String childKey = iterator.next();
            JSONObject child = object.optJSONObject(childKey);
            if (child == null) {
                continue;
            }
            String nested = findString(child, keys);
            if (!nested.isEmpty()) {
                return nested;
            }
        }
        return "";
    }

    private static boolean looksLikeToken(String value) {
        String cleaned = value.replace("-", "");
        return cleaned.length() != 32 || !cleaned.matches("[0-9a-fA-F]+");
    }
}
