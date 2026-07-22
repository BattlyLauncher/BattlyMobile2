package net.kdt.pojavlaunch.fragments;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.Intent;
import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;

import com.google.android.gms.auth.GoogleAuthUtil;
import com.google.android.gms.auth.UserRecoverableAuthException;

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

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

public class BattlyLoginFragment extends Fragment {
    public static final String TAG = "BATTLY_LOGIN_FRAGMENT";

    private static final String BATTLY_LOGIN_URL = "https://battlylauncher.com/api/battly/launcher/login";
    private static final String BATTLY_TOKEN_LOGIN_URL = "https://battlylauncher.com/api/battly/launcher/loginWithToken";
    private static final String[] BATTLY_GOOGLE_NATIVE_LOGIN_URLS = {
            "https://api.battlylauncher.com/api/v2/battly/mobile/google-login",
            "https://battlylauncher.com/api/battly/mobile/google-login"
    };
    private static final String BATTLY_DISCORD_LOGIN_URL = "https://battlylauncher.com/auth/discord";
    private static final String BATTLY_REGISTER_URL = "https://battlylauncher.com/register?utm_source=battlymobile&utm_medium=login_page&utm_campaign=registers_from_battly_mobile";

    private EditText mUsernameEditText;
    private EditText mPasswordEditText;
    private TextView mErrorTextView;
    private Button mLoginButton;
    private Button mRegisterButton;
    private Button mGoogleButton;
    private Button mDiscordButton;
    private ActivityResultLauncher<Intent> mGoogleSignInLauncher;
    private ActivityResultLauncher<Intent> mGoogleAuthRecoveryLauncher;
    private Account mPendingGoogleAccount;
    private String mDiscordNonce;
    private volatile ServerSocket mDiscordServer;
    private volatile boolean mDiscordLoginCompleted;

    public BattlyLoginFragment() {
        super(R.layout.fragment_battly_login);
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mGoogleSignInLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
        result -> {
                    if (!isAdded()) {
                        return;
                    }
                    try {
                        Intent data = result.getData();
                        if (result.getResultCode() != android.app.Activity.RESULT_OK || data == null) {
                            throw new IllegalStateException("Google account selection was cancelled");
                        }
                        String accountName = data.getStringExtra(AccountManager.KEY_ACCOUNT_NAME);
                        String accountType = data.getStringExtra(AccountManager.KEY_ACCOUNT_TYPE);
                        if (!Tools.isValidString(accountName)) {
                            throw new IllegalStateException("Google returned an empty account name");
                        }
                        Account account = new Account(accountName,
                                Tools.isValidString(accountType) ? accountType : GoogleAuthUtil.GOOGLE_ACCOUNT_TYPE);
                        loginWithGoogleAccount(account);
                    } catch (Exception e) {
                        Log.w(TAG, "Google account selection failed", e);
                        if (mGoogleButton != null) {
                            mGoogleButton.setEnabled(true);
                        }
                        showError(getString(R.string.battly_login_google_error));
                    }
                });
        mGoogleAuthRecoveryLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(), result -> {
                    Account pendingAccount = mPendingGoogleAccount;
                    mPendingGoogleAccount = null;
                    if (isAdded() && result.getResultCode() == android.app.Activity.RESULT_OK
                            && pendingAccount != null) {
                        loginWithGoogleAccount(pendingAccount);
                    } else if (isAdded()) {
                        if (mGoogleButton != null) {
                            mGoogleButton.setEnabled(true);
                        }
                        showError(getString(R.string.battly_login_google_error));
                    }
                });
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        mUsernameEditText = view.findViewById(R.id.battly_edit_username);
        mPasswordEditText = view.findViewById(R.id.battly_edit_password);
        mErrorTextView = view.findViewById(R.id.battly_error_text);
        mLoginButton = view.findViewById(R.id.battly_login_button);
        mRegisterButton = view.findViewById(R.id.battly_register_button);
        mGoogleButton = view.findViewById(R.id.battly_google_button);
        mDiscordButton = view.findViewById(R.id.battly_discord_button);

        if (mLoginButton != null) {
            mLoginButton.setOnClickListener(v -> attemptLogin());
        }
        if (mGoogleButton != null) {
            mGoogleButton.setOnClickListener(v -> startGoogleLogin());
        }
        if (mDiscordButton != null) {
            mDiscordButton.setOnClickListener(v -> startDiscordLogin());
        }
        if (mRegisterButton != null) {
            mRegisterButton.setOnClickListener(v -> startActivity(
                    new Intent(Intent.ACTION_VIEW, Uri.parse(BATTLY_REGISTER_URL))));
        }
    }

    private void attemptLogin() {
        FragmentActivity activity = getActivity();
        if (activity == null) return;
        Context appContext = activity.getApplicationContext();
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
                        BattlyPlusManager.updateFromLoginResponse(appContext, resp);
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
                        appContext.getSharedPreferences("battly_account", Context.MODE_PRIVATE)
                                .edit()
                                .putString("battly_token", resolvedToken)
                                .putString("battly_username", resolvedUsername)
                                .putString("battly_uuid", resolvedUuid)
                                .apply();
                        if (BattlyWorldsFeature.ENABLED) {
                            BattlyWorldsInvites.registerDeviceToken(appContext);
                        }
                    }

                    completeBattlyLogin(activity, appContext, resolvedUsername, resolvedToken.isEmpty() ? "battly" : resolvedToken,
                            resolvedUuid, resolvedToken);
                } else {
                    runOnActivity(activity, () -> {
                        mLoginButton.setEnabled(true);
                        showError(getString(R.string.battly_login_error_credentials));
                    });
                }
            } catch (Exception e) {
                runOnActivity(activity, () -> {
                    mLoginButton.setEnabled(true);
                    showError(getString(R.string.battly_login_error_network));
                });
            }
        }).start();
    }

    private void startGoogleLogin() {
        if (mGoogleButton != null) {
            mGoogleButton.setEnabled(false);
        }
        mErrorTextView.setVisibility(View.GONE);
        Intent chooser = AccountManager.newChooseAccountIntent(
                null, null, new String[]{GoogleAuthUtil.GOOGLE_ACCOUNT_TYPE},
                null, null, null, null);
        mGoogleSignInLauncher.launch(chooser);
    }

    private void loginWithGoogleAccount(@Nullable Account account) {
        FragmentActivity activity = getActivity();
        if (activity == null) return;
        Context appContext = activity.getApplicationContext();
        new Thread(() -> {
            try {
                if (account == null) {
                    throw new IllegalStateException("Google returned an empty account");
                }
                String accessToken;
                try {
                    accessToken = GoogleAuthUtil.getToken(
                            appContext, account, "oauth2:openid email profile");
                } catch (UserRecoverableAuthException recoverable) {
                    mPendingGoogleAccount = account;
                    runOnActivity(activity, () -> mGoogleAuthRecoveryLauncher.launch(recoverable.getIntent()));
                    return;
                }
                if (!Tools.isValidString(accessToken)) {
                    throw new IllegalStateException("Google returned no usable token");
                }
                finishNativeGoogleLogin(activity, appContext,
                        loginWithNativeGoogle("", accessToken, "", account.name));
            } catch (Exception e) {
                Log.w(TAG, "Native Google login failed", e);
                runOnActivity(activity, () -> {
                    if (mGoogleButton != null) {
                        mGoogleButton.setEnabled(true);
                    }
                    showError(getString(R.string.battly_login_google_error));
                });
            }
        }, "Battly Native Google Login").start();
    }

    private void finishNativeGoogleLogin(FragmentActivity activity, Context appContext,
                                         JSONObject response) {
        JSONObject data = response.optJSONObject("data");
        String username = findString(data, "username", "name");
        String token = findString(data, "token", "accessToken", "access_token");
        String uuid = findString(data, "uuid", "id", "profileId", "profile_id");
        if (!Tools.isValidString(username) || !Tools.isValidString(token)) {
            throw new IllegalStateException("Battly Google login returned an incomplete account");
        }
        if (uuid.isEmpty()) {
            uuid = UUID.nameUUIDFromBytes(("OfflinePlayer:" + username)
                    .getBytes(StandardCharsets.UTF_8)).toString();
        }
        BattlyPlusManager.updateFromLoginResponse(appContext, response);
        completeBattlyLogin(activity, appContext, username, token, uuid, token);
    }

    private JSONObject loginWithNativeGoogle(String idToken, String accessToken, String authCode, String emailHint) throws Exception {
        JSONObject body = new JSONObject();
        if (Tools.isValidString(idToken)) {
            body.put("idToken", idToken);
        }
        if (Tools.isValidString(accessToken)) {
            body.put("accessToken", accessToken);
        }
        if (Tools.isValidString(authCode)) {
            body.put("authCode", authCode);
        }
        if (Tools.isValidString(emailHint)) {
            body.put("emailHint", emailHint);
        }
        IllegalStateException lastError = null;
        for (String endpoint : BATTLY_GOOGLE_NATIVE_LOGIN_URLS) {
            HttpURLConnection conn = (HttpURLConnection) new URL(endpoint).openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            conn.setDoOutput(true);
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(12000);
            byte[] bodyBytes = body.toString().getBytes(StandardCharsets.UTF_8);
            try (OutputStream os = conn.getOutputStream()) {
                os.write(bodyBytes);
            }

            int responseCode = conn.getResponseCode();
            java.io.InputStream stream = responseCode < 400
                    ? conn.getInputStream() : conn.getErrorStream();
            StringBuilder sb = new StringBuilder();
            if (stream != null) {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) sb.append(line);
                }
            }
            if (responseCode < 400) {
                return new JSONObject(sb.toString());
            }
            lastError = new IllegalStateException(
                    "Native Google login failed at " + endpoint + ": " + responseCode + " " + sb);
            if (responseCode != HttpURLConnection.HTTP_NOT_FOUND) {
                throw lastError;
            }
        }
        throw lastError != null ? lastError
                : new IllegalStateException("No Battly Google login endpoint is configured");
    }

    private void startDiscordLogin() {
        FragmentActivity activity = getActivity();
        if (activity == null || mDiscordButton == null) return;
        Context appContext = activity.getApplicationContext();
        mErrorTextView.setVisibility(View.GONE);
        mDiscordButton.setEnabled(false);
        mDiscordLoginCompleted = false;
        new Thread(() -> {
            try (ServerSocket server = new ServerSocket(0, 4, java.net.InetAddress.getByName("127.0.0.1"))) {
                mDiscordServer = server;
                server.setSoTimeout(120000);
                String nonce = UUID.randomUUID().toString().replace("-", "");
                mDiscordNonce = nonce;
                String url = BATTLY_DISCORD_LOGIN_URL
                        + "?launcher_port=" + server.getLocalPort()
                        + "&launcher_nonce=" + Uri.encode(nonce)
                        + "&launcher_deeplink=1";
                runOnActivity(activity, () -> startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))));

                String token = waitForOAuthToken(server, "discord", nonce);
                if (mDiscordLoginCompleted) return;
                if (!Tools.isValidString(token)) {
                    throw new IllegalStateException("Discord login did not return a token");
                }
                JSONObject response = loginWithToken(token);
                mDiscordLoginCompleted = true;
                JSONObject data = response.optJSONObject("data");
                String username = findString(data, "username", "name");
                String uuid = findString(data, "uuid", "id", "profileId", "profile_id");
                if (uuid.isEmpty()) {
                    uuid = UUID.nameUUIDFromBytes(("OfflinePlayer:" + username).getBytes(StandardCharsets.UTF_8)).toString();
                }
                BattlyPlusManager.updateFromLoginResponse(appContext, response);
                completeBattlyLogin(activity, appContext, username, token, uuid, token);
            } catch (Exception e) {
                if (mDiscordLoginCompleted) return;
                Log.w(TAG, "Discord OAuth login failed", e);
                runOnActivity(activity, () -> {
                    if (mDiscordButton != null) mDiscordButton.setEnabled(true);
                    showError(getString(R.string.battly_login_google_error));
                });
            } finally {
                mDiscordServer = null;
            }
        }, "Battly Discord Login").start();
    }

    @Override
    public void onResume() {
        super.onResume();
        consumePendingOAuth();
    }

    private void consumePendingOAuth() {
        FragmentActivity activity = getActivity();
        if (!isAdded() || activity == null) {
            return;
        }
        Context appContext = activity.getApplicationContext();
        android.content.SharedPreferences prefs = appContext
                .getSharedPreferences("battly_oauth", Context.MODE_PRIVATE);
        String provider = prefs.getString("provider", "");
        String token = prefs.getString("token", "");
        String nonce = prefs.getString("nonce", "");
        if (!"discord".equals(provider) || !Tools.isValidString(token)) {
            return;
        }
        if (Tools.isValidString(mDiscordNonce) && !mDiscordNonce.equals(nonce)) {
            return;
        }
        mDiscordLoginCompleted = true;
        closeDiscordServer();
        prefs.edit().clear().apply();
        if (mDiscordButton != null) {
            mDiscordButton.setEnabled(false);
        }
        new Thread(() -> {
            try {
                JSONObject response = loginWithToken(token);
                JSONObject data = response.optJSONObject("data");
                String username = findString(data, "username", "name");
                String uuid = findString(data, "uuid", "id", "profileId", "profile_id");
                if (uuid.isEmpty()) {
                    uuid = UUID.nameUUIDFromBytes(("OfflinePlayer:" + username).getBytes(StandardCharsets.UTF_8)).toString();
                }
                BattlyPlusManager.updateFromLoginResponse(appContext, response);
                completeBattlyLogin(activity, appContext, username, token, uuid, token);
            } catch (Exception e) {
                runOnActivity(activity, () -> {
                    if (mDiscordButton != null) {
                        mDiscordButton.setEnabled(true);
                    }
                    showError(getString(R.string.battly_login_google_error));
                });
            }
        }, "Battly Discord Deeplink Login").start();
    }

    private String waitForOAuthToken(ServerSocket server, String provider, String nonce) throws Exception {
        String expectedPath = "/auth/" + provider;
        while (!mDiscordLoginCompleted) {
            try (Socket socket = server.accept()) {
                HttpRequest request = readHttpRequest(socket);
                if ("OPTIONS".equalsIgnoreCase(request.method)) {
                    writeHttpResponse(socket, 204, "");
                    continue;
                }
                if (!"POST".equalsIgnoreCase(request.method) || !expectedPath.equals(request.path)) {
                    writeHttpResponse(socket, 404, "{\"ok\":false}");
                    continue;
                }
                JSONObject body = new JSONObject(request.body);
                if (!nonce.equals(body.optString("nonce", ""))) {
                    writeHttpResponse(socket, 403, "{\"ok\":false}");
                    continue;
                }
                String token = body.optString("token", "").trim();
                writeHttpResponse(socket, token.isEmpty() ? 400 : 200,
                        token.isEmpty() ? "{\"ok\":false}" : "{\"ok\":true}");
                return token;
            } catch (SocketTimeoutException timeout) {
                return "";
            }
        }
        return "";
    }

    private void closeDiscordServer() {
        ServerSocket server = mDiscordServer;
        if (server == null) return;
        try {
            server.close();
        } catch (Exception ignored) {
        }
    }

    @Override
    public void onDestroy() {
        mDiscordLoginCompleted = true;
        closeDiscordServer();
        super.onDestroy();
    }

    private JSONObject loginWithToken(String token) throws Exception {
        URL url = new URL(BATTLY_TOKEN_LOGIN_URL);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Authorization", "Bearer " + token);
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(10000);
        int responseCode = conn.getResponseCode();
        BufferedReader reader = new BufferedReader(new InputStreamReader(
                responseCode < 400 ? conn.getInputStream() : conn.getErrorStream(), StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) sb.append(line);
        reader.close();
        if (responseCode >= 400) {
            throw new IllegalStateException("Token login failed: " + responseCode);
        }
        return new JSONObject(sb.toString());
    }

    private void completeBattlyLogin(String username, String token, String uuid, String storedToken) {
        FragmentActivity activity = getActivity();
        if (activity == null) return;
        completeBattlyLogin(activity, activity.getApplicationContext(), username, token, uuid, storedToken);
    }

    private void completeBattlyLogin(FragmentActivity activity, Context appContext, String username, String token, String uuid, String storedToken) {
        if (!storedToken.isEmpty()) {
            appContext.getSharedPreferences("battly_account", Context.MODE_PRIVATE)
                    .edit()
                    .putString("battly_token", storedToken)
                    .putString("battly_username", username)
                    .putString("battly_uuid", uuid)
                    .apply();
            if (BattlyWorldsFeature.ENABLED) {
                BattlyWorldsInvites.registerDeviceToken(appContext);
            }
        }

        runOnActivity(activity, () -> {
            ExtraCore.setValue(ExtraConstants.MOJANG_LOGIN_TODO, new String[]{username, token, uuid});
            Tools.swapFragment(activity, MainMenuFragment.class, MainMenuFragment.TAG, null);
            BattlyPlusManager.refreshAsync(activity, isPlus -> {
                if (activity instanceof LauncherActivity) {
                    ((LauncherActivity) activity).runStartupPromptsAfterLogin();
                } else if (isPlus) {
                    BattlyPlusWelcomeDialog.showAfterLogin(activity);
                }
            });
        });
    }

    private void runOnActivity(FragmentActivity activity, Runnable action) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) return;
        activity.runOnUiThread(() -> {
            if (!isAdded()) return;
            action.run();
        });
    }

    private static HttpRequest readHttpRequest(Socket socket) throws Exception {
        BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        String requestLine = reader.readLine();
        if (requestLine == null) return new HttpRequest("", "", "");
        String[] parts = requestLine.split(" ");
        String method = parts.length > 0 ? parts[0] : "";
        String path = parts.length > 1 ? parts[1] : "";
        int contentLength = 0;
        String line;
        while ((line = reader.readLine()) != null && !line.isEmpty()) {
            int separator = line.indexOf(':');
            if (separator > 0 && "content-length".equalsIgnoreCase(line.substring(0, separator).trim())) {
                contentLength = Integer.parseInt(line.substring(separator + 1).trim());
            }
        }
        char[] body = new char[Math.max(0, contentLength)];
        int read = 0;
        while (read < body.length) {
            int count = reader.read(body, read, body.length - read);
            if (count < 0) break;
            read += count;
        }
        return new HttpRequest(method, path, new String(body, 0, read));
    }

    private static void writeHttpResponse(Socket socket, int status, String body) throws Exception {
        byte[] bodyBytes = body.getBytes(StandardCharsets.UTF_8);
        String reason = status == 200 ? "OK" : status == 204 ? "No Content" : "Error";
        String headers = "HTTP/1.1 " + status + " " + reason + "\r\n"
                + "Access-Control-Allow-Origin: *\r\n"
                + "Access-Control-Allow-Headers: Content-Type\r\n"
                + "Access-Control-Allow-Methods: POST, OPTIONS\r\n"
                + "Content-Type: application/json; charset=utf-8\r\n"
                + "Content-Length: " + bodyBytes.length + "\r\n"
                + "Connection: close\r\n\r\n";
        OutputStream out = socket.getOutputStream();
        out.write(headers.getBytes(StandardCharsets.UTF_8));
        out.write(bodyBytes);
        out.flush();
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

    private static final class HttpRequest {
        final String method;
        final String path;
        final String body;

        HttpRequest(String method, String path, String body) {
            this.method = method;
            this.path = path;
            this.body = body;
        }
    }

}
