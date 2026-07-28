package net.kdt.pojavlaunch.battlyworlds;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.gson.JsonObject;
import com.bumptech.glide.Glide;

import net.burningtnt.terracotta.TerracottaAndroidAPI;
import net.kdt.pojavlaunch.PojavApplication;
import net.kdt.pojavlaunch.PojavProfile;
import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.value.MinecraftAccount;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public final class BattlyWorldsDialog {
    private static final int PANEL = 0xF21A2A33;
    private static final int PANEL_SOFT = 0x8030434D;
    private static final int TEXT_MAIN = 0xFFFFFFFF;
    private static final int TEXT_MUTED = 0xFFC6D6E3;
    private static final int ACCENT = 0xFF8ADBC6;

    private final Activity mActivity;
    private final boolean mAutoHost;
    private final AlertDialog mDialog;
    private final TextView mStatus;
    private final TextView mResult;
    private final TextView mHint;
    private final TextView mPlanSummary;
    private final TextView mInviteButton;
    private final TextView mShareButton;
    private final ImageButton mCopyButton;
    private JsonObject mCurrentState;
    private String mHostRealCode = "";
    private String mHostShortCode = "";
    private boolean mAutoHostConsumed;
    private boolean mShortCodeLoading;

    private final BattlyWorldsManager.StateListener mStateListener = state -> {
        mCurrentState = state;
        refreshState();
    };

    public BattlyWorldsDialog(Activity activity) {
        this(activity, false);
    }

    public BattlyWorldsDialog(Activity activity, boolean autoHost) {
        mActivity = activity;
        mAutoHost = autoHost;

        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(22), dp(18), dp(22), dp(18));

        LinearLayout header = new LinearLayout(activity);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setOrientation(LinearLayout.HORIZONTAL);

        TextView title = new TextView(activity);
        title.setText(R.string.battlyworlds_title);
        title.setTextSize(24);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(TEXT_MAIN);
        header.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        ImageButton logs = iconButton(R.drawable.ic_battly_logs_line);
        TextView stop = iconButton("X");
        mCopyButton = iconButton(R.drawable.ic_battly_copy_line);
        logs.setContentDescription(activity.getString(R.string.battlyworlds_logs));
        stop.setContentDescription(activity.getString(R.string.global_cancel));
        mCopyButton.setContentDescription(activity.getString(R.string.global_copy));
        logs.setOnClickListener(v -> showLogs());
        stop.setOnClickListener(v -> closeOnly());
        mCopyButton.setOnClickListener(v -> copyCurrentResult());
        header.addView(logs);
        header.addView(mCopyButton);
        header.addView(stop);
        root.addView(header);

        TextView description = new TextView(activity);
        description.setText(R.string.battlyworlds_description_simple);
        description.setTextColor(TEXT_MUTED);
        description.setTextSize(14);
        description.setPadding(0, dp(8), 0, dp(14));
        root.addView(description);

        mPlanSummary = new TextView(activity);
        mPlanSummary.setTextColor(ACCENT);
        mPlanSummary.setTypeface(Typeface.DEFAULT_BOLD);
        mPlanSummary.setTextSize(12);
        mPlanSummary.setPadding(dp(12), dp(8), dp(12), dp(8));
        mPlanSummary.setBackground(round(0x223D5A60, dp(14), 0x338ADBC6, 1));
        LinearLayout.LayoutParams planParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        planParams.setMargins(0, 0, 0, dp(12));
        root.addView(mPlanSummary, planParams);

        LinearLayout stateCard = new LinearLayout(activity);
        stateCard.setOrientation(LinearLayout.HORIZONTAL);
        stateCard.setGravity(Gravity.CENTER_VERTICAL);
        stateCard.setPadding(dp(14), dp(12), dp(14), dp(12));
        stateCard.setBackground(round(PANEL_SOFT, dp(18), 0x22495D68, 1));

        ImageView icon = new ImageView(activity);
        icon.setImageResource(R.drawable.bworlds);
        icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        icon.setClipToOutline(false);
        icon.setPadding(dp(8), dp(8), dp(8), dp(8));
        icon.setBackground(round(0x334ED7C0, dp(18), 0, 0));
        stateCard.addView(icon, new LinearLayout.LayoutParams(dp(50), dp(50)));

        LinearLayout stateTexts = new LinearLayout(activity);
        stateTexts.setOrientation(LinearLayout.VERTICAL);
        stateTexts.setPadding(dp(12), 0, 0, 0);
        mStatus = new TextView(activity);
        mStatus.setTextColor(TEXT_MAIN);
        mStatus.setTextSize(17);
        mStatus.setTypeface(Typeface.DEFAULT_BOLD);
        mResult = new TextView(activity);
        mResult.setTextColor(TEXT_MUTED);
        mResult.setTextSize(13);
        mResult.setTextIsSelectable(true);
        stateTexts.addView(mStatus);
        stateTexts.addView(mResult);
        stateCard.addView(stateTexts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        root.addView(stateCard);

        mHint = new TextView(activity);
        mHint.setTextColor(0xFF9FB8C5);
        mHint.setTextSize(12);
        mHint.setPadding(0, dp(10), 0, dp(6));
        root.addView(mHint);

        LinearLayout actions = new LinearLayout(activity);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.END);
        actions.setPadding(0, dp(10), 0, 0);

        mInviteButton = primaryAction(activity.getString(R.string.battlyworlds_invite_friend));
        mShareButton = secondaryAction(activity.getString(R.string.battlyworlds_share));
        TextView join = secondaryAction(activity.getString(R.string.battlyworlds_join));
        TextView host = primaryAction(activity.getString(R.string.battlyworlds_host));

        mInviteButton.setOnClickListener(v -> askInviteFriend());
        mShareButton.setOnClickListener(v -> shareCurrentInvite());
        join.setOnClickListener(v -> askJoinCode());
        host.setOnClickListener(v -> startHost());

        actions.addView(mInviteButton);
        actions.addView(mShareButton);
        actions.addView(join);
        actions.addView(host);
        root.addView(actions);

        mDialog = new AlertDialog.Builder(activity, R.style.BattlyDialog)
                .setView(root)
                .create();
        mDialog.setOnDismissListener(dialog -> BattlyWorldsManager.removeStateListener(mStateListener));
    }

    public void show() {
        try {
            BattlyWorldsManager.initialize(mActivity);
            BattlyWorldsManager.attachActivity(mActivity);
            BattlyWorldsInvites.heartbeat(mActivity);
            PojavApplication.sExecutorService.execute(() -> {
                BattlyWorldsInvites.refreshEntitlements(mActivity);
                Tools.MAIN_HANDLER.post(this::refreshState);
            });
            BattlyWorldsManager.addStateListener(mStateListener);
            refreshState();
            mDialog.show();
            if (mAutoHost && !mAutoHostConsumed) {
                mAutoHostConsumed = true;
                Tools.MAIN_HANDLER.postDelayed(this::startHost, 250);
            }
        } catch (Throwable throwable) {
            Tools.showError(mActivity, throwable);
        }
    }

    private void startHost() {
        setBusy(true);
        new Thread(() -> {
            try {
                BattlyWorldsInvites.refreshEntitlements(mActivity);
                List<String> nodes = BattlyWorldsNodeList.fetch(mActivity);
                Tools.MAIN_HANDLER.post(() -> {
                    try {
                        BattlyWorldsManager.setWaiting(mActivity, true);
                        BattlyWorldsManager.startHost(getPlayerName(), nodes);
                    } catch (Throwable throwable) {
                        Tools.showError(mActivity, throwable);
                    } finally {
                        setBusy(false);
                    }
                });
            } catch (Throwable throwable) {
                Tools.MAIN_HANDLER.post(() -> {
                    setBusy(false);
                    Tools.showError(mActivity, throwable);
                });
            }
        }, "BattlyWorlds Host").start();
    }

    private void closeOnly() {
        mDialog.dismiss();
    }

    private void askJoinCode() {
        EditText input = new EditText(mActivity);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        input.setHint(R.string.battlyworlds_join_hint);

        AlertDialog dialog = new AlertDialog.Builder(mActivity, R.style.BattlyDialog)
                .setTitle(R.string.battlyworlds_join)
                .setView(input)
                .setNegativeButton(R.string.global_cancel, null)
                .setPositiveButton(R.string.battlyworlds_join, null)
                .create();
        dialog.setOnShowListener(d -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                String code = input.getText().toString().trim();
                if (!BattlyWorldsInvites.looksLikeShortCode(code)
                        && BattlyWorldsManager.parseRoomCode(code) == null) {
                    Toast.makeText(mActivity, R.string.battlyworlds_invalid_code, Toast.LENGTH_SHORT).show();
                    return;
                }
                dialog.dismiss();
                startGuest(code);
            });
            input.requestFocus();
            InputMethodManager imm = (InputMethodManager) mActivity.getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT);
        });
        dialog.show();
    }

    private void startGuest(String code) {
        setBusy(true);
        new Thread(() -> {
            try {
                String resolvedCode = BattlyWorldsInvites.resolveRoomCode(mActivity, code);
                BattlyWorldsInvites.refreshEntitlements(mActivity);
                List<String> nodes = BattlyWorldsNodeList.fetch(mActivity);
                Tools.MAIN_HANDLER.post(() -> {
                    try {
                        BattlyWorldsManager.setWaiting(mActivity, true);
                        boolean accepted = BattlyWorldsManager.join(resolvedCode, getPlayerName(), nodes);
                        if (!accepted) {
                            Toast.makeText(mActivity, R.string.battlyworlds_invalid_code, Toast.LENGTH_SHORT).show();
                        }
                    } catch (Throwable throwable) {
                        Tools.showError(mActivity, throwable);
                    } finally {
                        setBusy(false);
                    }
                });
            } catch (Throwable throwable) {
                Tools.MAIN_HANDLER.post(() -> {
                    setBusy(false);
                    Tools.showError(mActivity, throwable);
                });
            }
        }, "BattlyWorlds Guest").start();
    }

    private void askInviteFriend() {
        String roomCode = getPublicHostCode();
        if (roomCode == null || roomCode.trim().isEmpty()) {
            Toast.makeText(mActivity, R.string.battlyworlds_invite_no_room, Toast.LENGTH_SHORT).show();
            return;
        }
        if (!BattlyWorldsInvites.isBattlyLoggedIn(mActivity)) {
            Toast.makeText(mActivity, R.string.battlyworlds_invite_login_required, Toast.LENGTH_SHORT).show();
            return;
        }

        setBusy(true);
        new Thread(() -> {
            try {
                List<BattlyWorldsInvites.Friend> friends = BattlyWorldsInvites.fetchFriends(mActivity);
                sortFriends(friends);
                Tools.MAIN_HANDLER.post(() -> {
                    setBusy(false);
                    showFriendPicker(friends, roomCode);
                });
            } catch (Throwable throwable) {
                Tools.MAIN_HANDLER.post(() -> {
                    setBusy(false);
                    Tools.showError(mActivity, throwable);
                });
            }
        }, "BattlyWorlds Friends").start();
    }

    private void showFriendPicker(List<BattlyWorldsInvites.Friend> friends, String roomCode) {
        LinearLayout root = new LinearLayout(mActivity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(8), dp(16), dp(8));

        EditText search = new EditText(mActivity);
        search.setSingleLine(true);
        search.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        search.setHint(R.string.battlyworlds_search_friend_hint);
        root.addView(search, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        ScrollView scroll = new ScrollView(mActivity);
        LinearLayout list = new LinearLayout(mActivity);
        list.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(list);
        root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(260)));

        TextView searchBattly = secondaryAction(mActivity.getString(R.string.battlyworlds_search_action));
        searchBattly.setGravity(Gravity.CENTER);
        root.addView(searchBattly, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        AlertDialog dialog = new AlertDialog.Builder(mActivity, R.style.BattlyDialog)
                .setTitle(R.string.battlyworlds_invite_friend)
                .setView(root)
                .setNegativeButton(R.string.global_cancel, null)
                .create();

        Runnable updateList = () -> populateFriendRows(list, filterFriends(friends, search.getText().toString()), roomCode, dialog);
        search.addTextChangedListener(new SimpleTextWatcher(updateList));
        searchBattly.setOnClickListener(v -> {
            String query = search.getText().toString().trim();
            if (query.length() < 2) {
                Toast.makeText(mActivity, R.string.battlyworlds_search_friend_hint, Toast.LENGTH_SHORT).show();
                return;
            }
            dialog.dismiss();
            searchFriend(query);
        });
        dialog.setOnShowListener(d -> updateList.run());
        dialog.show();
    }

    private void populateFriendRows(LinearLayout list, List<BattlyWorldsInvites.Friend> friends, String roomCode, AlertDialog owner) {
        list.removeAllViews();
        if (friends.isEmpty()) {
            TextView empty = new TextView(mActivity);
            empty.setText(R.string.battlyworlds_invite_no_friends);
            empty.setTextColor(TEXT_MUTED);
            empty.setPadding(0, dp(18), 0, dp(18));
            list.addView(empty);
            return;
        }
        for (BattlyWorldsInvites.Friend friend : friends) {
            View row = friendRow(friend, () -> {
                owner.dismiss();
                sendFriendInvite(friend, roomCode);
            });
            list.addView(row);
        }
    }

    private View friendRow(BattlyWorldsInvites.Friend friend, Runnable action) {
        LinearLayout row = new LinearLayout(mActivity);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(10), dp(8), dp(10), dp(8));
        row.setBackground(round(0x263C4E58, dp(16), 0x22495D68, 1));
        row.setOnClickListener(v -> action.run());

        ImageView face = new ImageView(mActivity);
        face.setScaleType(ImageView.ScaleType.CENTER_CROP);
        face.setBackground(round(0x334ED7C0, dp(8), 0, 0));
        row.addView(face, new LinearLayout.LayoutParams(dp(38), dp(38)));
        loadFace(friend.username, face);

        LinearLayout texts = new LinearLayout(mActivity);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.setPadding(dp(12), 0, 0, 0);
        TextView name = new TextView(mActivity);
        name.setText(friend.username);
        name.setTextColor(TEXT_MAIN);
        name.setTypeface(Typeface.DEFAULT_BOLD);
        name.setTextSize(15);
        TextView state = new TextView(mActivity);
        state.setText(isOnline(friend) ? R.string.battlyworlds_friend_online : R.string.battlyworlds_friend_offline);
        state.setTextColor(isOnline(friend) ? ACCENT : 0xFF8DA1AD);
        state.setTextSize(12);
        texts.addView(name);
        texts.addView(state);
        row.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView invite = new TextView(mActivity);
        invite.setText(R.string.battlyworlds_invite_accept);
        invite.setTextColor(ACCENT);
        invite.setTypeface(Typeface.DEFAULT_BOLD);
        row.addView(invite);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, dp(8), 0, 0);
        row.setLayoutParams(params);
        return row;
    }

    private void loadFace(String username, ImageView imageView) {
        String url = "https://api.battlylauncher.com/api/face/" + Uri.encode(username);
        Glide.with(imageView)
                .load(url)
                .override(dp(38), dp(38))
                .circleCrop()
                .into(imageView);
    }

    private void searchFriend(String query) {
        setBusy(true);
        new Thread(() -> {
            try {
                List<BattlyWorldsInvites.UserSearchResult> users = BattlyWorldsInvites.searchUsers(mActivity, query);
                Tools.MAIN_HANDLER.post(() -> {
                    setBusy(false);
                    showUserSearchResults(users);
                });
            } catch (Throwable throwable) {
                Tools.MAIN_HANDLER.post(() -> {
                    setBusy(false);
                    Tools.showError(mActivity, throwable);
                });
            }
        }, "BattlyWorlds Search Friend").start();
    }

    private void showUserSearchResults(List<BattlyWorldsInvites.UserSearchResult> users) {
        if (users == null || users.isEmpty()) {
            Toast.makeText(mActivity, R.string.battlyworlds_search_no_results, Toast.LENGTH_SHORT).show();
            return;
        }

        String[] labels = new String[users.size()];
        for (int i = 0; i < users.size(); i++) labels[i] = users.get(i).username;

        new AlertDialog.Builder(mActivity, R.style.BattlyDialog)
                .setTitle(R.string.battlyworlds_search_friend)
                .setItems(labels, (dialog, which) -> askSendFriendRequest(users.get(which).username))
                .setNegativeButton(R.string.global_cancel, null)
                .show();
    }

    private void askSendFriendRequest(String username) {
        new AlertDialog.Builder(mActivity, R.style.BattlyDialog)
                .setTitle(R.string.battlyworlds_friend_request_title)
                .setMessage(mActivity.getString(R.string.battlyworlds_friend_request_message, username))
                .setNegativeButton(R.string.global_cancel, null)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> sendFriendRequest(username))
                .show();
    }

    private void sendFriendRequest(String username) {
        setBusy(true);
        new Thread(() -> {
            try {
                BattlyWorldsInvites.sendFriendRequest(mActivity, username);
                Tools.MAIN_HANDLER.post(() -> {
                    setBusy(false);
                    Toast.makeText(mActivity,
                            mActivity.getString(R.string.battlyworlds_friend_request_sent, username),
                            Toast.LENGTH_SHORT).show();
                });
            } catch (Throwable throwable) {
                Tools.MAIN_HANDLER.post(() -> {
                    setBusy(false);
                    Tools.showError(mActivity, throwable);
                });
            }
        }, "BattlyWorlds Friend Request").start();
    }

    private void sendFriendInvite(BattlyWorldsInvites.Friend friend, String roomCode) {
        setBusy(true);
        new Thread(() -> {
            try {
                BattlyWorldsInvites.sendInvite(mActivity, friend.username, roomCode, BattlyWorldsInvites.getHostVersion());
                Tools.MAIN_HANDLER.post(() -> {
                    setBusy(false);
                    Toast.makeText(mActivity,
                            mActivity.getString(R.string.battlyworlds_invite_sent, friend.username),
                            Toast.LENGTH_SHORT).show();
                });
            } catch (Throwable throwable) {
                Tools.MAIN_HANDLER.post(() -> {
                    setBusy(false);
                    Tools.showError(mActivity, throwable);
                });
            }
        }, "BattlyWorlds Send Invite").start();
    }

    private void refreshState() {
        refreshPlanSummary();
        String hostCode = BattlyWorldsManager.getHostCode(mCurrentState);
        String guestUrl = BattlyWorldsManager.getGuestUrl(mCurrentState);
        mStatus.setText(simpleStateTitle(hostCode, guestUrl));
        if (hostCode != null) {
            ensureShortHostCode(hostCode);
            String displayCode = getPublicHostCode();
            if (displayCode == null) {
                mResult.setText(R.string.battlyworlds_preparing_code);
            } else {
                mResult.setText(mActivity.getString(R.string.battlyworlds_host_code_simple, displayCode));
            }
            mHint.setText(R.string.battlyworlds_host_hint_simple);
            setRoomActionsEnabled(displayCode != null);
        } else if (guestUrl != null) {
            mResult.setText(R.string.battlyworlds_guest_connected_simple);
            mHint.setText(R.string.battlyworlds_guest_hint_simple);
            setRoomActionsEnabled(false);
        } else {
            mResult.setText(BattlyWorldsManager.describeState(mActivity, mCurrentState));
            mHint.setText(R.string.battlyworlds_tip_simple);
            setRoomActionsEnabled(false);
        }
    }

    private void refreshPlanSummary() {
        BattlyWorldsInvites.Entitlements entitlements = BattlyWorldsInvites.getCachedEntitlements();
        if (entitlements.plus) {
            mPlanSummary.setText(mActivity.getString(
                    R.string.battlyworlds_plus_summary,
                    entitlements.maxInvites,
                    entitlements.roomDurationHours));
        } else {
            mPlanSummary.setText(mActivity.getString(
                    R.string.battlyworlds_free_summary,
                    entitlements.maxInvites,
                    entitlements.roomDurationHours));
        }
    }

    private void ensureShortHostCode(String realCode) {
        if (realCode == null || mShortCodeLoading || realCode.equals(mHostRealCode) && !mHostShortCode.isEmpty()) {
            return;
        }
        if (!realCode.equals(mHostRealCode)) {
            mHostRealCode = realCode;
            mHostShortCode = "";
        }
        mShortCodeLoading = true;
        final String sourceCode = realCode;
        PojavApplication.sExecutorService.execute(() -> {
            try {
                String shortCode = BattlyWorldsInvites.createShortRoomCode(mActivity, sourceCode, BattlyWorldsInvites.getHostVersion());
                Tools.MAIN_HANDLER.post(() -> {
                    mShortCodeLoading = false;
                    if (sourceCode.equals(mHostRealCode)) {
                        mHostShortCode = shortCode;
                        refreshState();
                    }
                });
            } catch (Throwable throwable) {
                Tools.MAIN_HANDLER.post(() -> {
                    mShortCodeLoading = false;
                    if (sourceCode.equals(mHostRealCode)) {
                        Toast.makeText(mActivity, R.string.battlyworlds_short_code_failed, Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
    }

    private String getPublicHostCode() {
        return mHostShortCode == null || mHostShortCode.trim().isEmpty() ? null : mHostShortCode;
    }

    private void setRoomActionsEnabled(boolean enabled) {
        mInviteButton.setEnabled(enabled);
        mInviteButton.setAlpha(enabled ? 1f : 0.45f);
        mShareButton.setEnabled(enabled);
        mShareButton.setAlpha(enabled ? 1f : 0.45f);
        mCopyButton.setEnabled(enabled);
        mCopyButton.setAlpha(enabled ? 1f : 0.45f);
    }

    private String simpleStateTitle(String hostCode, String guestUrl) {
        if (hostCode != null) return mActivity.getString(R.string.battlyworlds_ready_to_invite);
        if (guestUrl != null) return mActivity.getString(R.string.battlyworlds_connected);
        return mActivity.getString(R.string.battlyworlds_ready);
    }

    private void copyCurrentResult() {
        String value = getPublicHostCode();
        if (value == null) value = BattlyWorldsManager.getGuestUrl(mCurrentState);
        if (value == null || value.trim().isEmpty()) {
            Toast.makeText(mActivity, R.string.battlyworlds_nothing_to_copy, Toast.LENGTH_SHORT).show();
            return;
        }
        ClipboardManager clipboard = (ClipboardManager) mActivity.getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null) {
            clipboard.setPrimaryClip(ClipData.newPlainText("BattlyWorlds", value));
            Toast.makeText(mActivity, R.string.battlyworlds_copied, Toast.LENGTH_SHORT).show();
        }
    }

    private void showLogs() {
        LinearLayout root = new LinearLayout(mActivity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(14), dp(8), dp(14), dp(8));

        EditText search = new EditText(mActivity);
        search.setSingleLine(true);
        search.setHint(R.string.battlyworlds_logs_search_hint);
        root.addView(search);

        CheckBox live = new CheckBox(mActivity);
        live.setText(R.string.battlyworlds_logs_live);
        live.setTextColor(TEXT_MUTED);
        live.setChecked(false);
        root.addView(live);

        TextView textView = new TextView(mActivity);
        textView.setTextIsSelectable(true);
        textView.setTextColor(TEXT_MUTED);
        textView.setTypeface(Typeface.MONOSPACE);
        textView.setTextSize(12);
        textView.setPadding(0, dp(10), 0, dp(10));
        ScrollView scrollView = new ScrollView(mActivity);
        scrollView.addView(textView);
        root.addView(scrollView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(260)));

        final String[] raw = {BattlyWorldsManager.collectLogs()};
        Runnable refresh = () -> textView.setText(filterLogs(simplifyLogs(raw[0]), search.getText().toString()));
        refresh.run();
        search.addTextChangedListener(new SimpleTextWatcher(refresh));

        final boolean[] running = {false};
        Runnable ticker = new Runnable() {
            @Override
            public void run() {
                if (!running[0]) return;
                raw[0] = BattlyWorldsManager.collectLogs();
                refresh.run();
                Tools.MAIN_HANDLER.postDelayed(this, 1000);
            }
        };
        live.setOnCheckedChangeListener((buttonView, isChecked) -> {
            running[0] = isChecked;
            if (isChecked) Tools.MAIN_HANDLER.post(ticker);
        });

        AlertDialog dialog = new AlertDialog.Builder(mActivity, R.style.BattlyDialog)
                .setTitle(R.string.battlyworlds_logs)
                .setView(root)
                .setNegativeButton(R.string.global_cancel, (d, w) -> running[0] = false)
                .setNeutralButton(R.string.global_save, null)
                .setPositiveButton(R.string.global_copy, null)
                .create();
        dialog.setOnShowListener(d -> {
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> saveLogs(raw[0]));
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> copyText(raw[0]));
        });
        dialog.setOnDismissListener(d -> running[0] = false);
        dialog.show();
    }

    private String filterLogs(String logs, String query) {
        String cleanQuery = query == null ? "" : query.trim().toLowerCase();
        if (cleanQuery.isEmpty()) return logs;
        StringBuilder builder = new StringBuilder();
        for (String line : logs.split("\n")) {
            if (line.toLowerCase().contains(cleanQuery)) builder.append(line).append('\n');
        }
        return builder.toString();
    }

    private void shareCurrentInvite() {
        String code = getPublicHostCode();
        if (code == null || code.trim().isEmpty()) {
            Toast.makeText(mActivity, R.string.battlyworlds_nothing_to_copy, Toast.LENGTH_SHORT).show();
            return;
        }
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_TEXT, mActivity.getString(R.string.battlyworlds_share_message, getPlayerName(), code));
        mActivity.startActivity(Intent.createChooser(intent, mActivity.getString(R.string.battlyworlds_share)));
    }

    private String simplifyLogs(String logs) {
        if (logs == null || logs.trim().isEmpty()) {
            return mActivity.getString(R.string.battlyworlds_logs_empty);
        }
        StringBuilder builder = new StringBuilder();
        for (String rawLine : logs.split("\n")) {
            String line = rawLine.trim();
            if (line.isEmpty()) continue;
            if (line.contains("Welcome using Terracotta")
                    || line.contains("profiles:")
                    || line.contains("machine_id")
                    || line.contains("Local IP Addresses")
                    || line.contains("AppState has been locked")) {
                continue;
            }
            line = line.replace("[State]: Switch to AppState::", "Estado: ");
            line = line.replace("[Core]: Setting to state ", "Nucleo: ");
            line = line.replace("[Server Scanner]:", "Escaner:");
            line = line.replace("[RoomExperiment]:", "Sala:");
            line = line.replace("[ScaffoldingClient]:", "Conexion:");
            line = line.replace("[Android]:", "Android:");
            builder.append(line).append('\n');
        }
        String result = builder.toString().trim();
        return result.isEmpty() ? mActivity.getString(R.string.battlyworlds_logs_empty) : result;
    }

    private void saveLogs(String logs) {
        try {
            File file = new File(Tools.DIR_GAME_HOME, "battlyworlds-log.txt");
            Tools.write(file.getAbsolutePath(), logs);
            Toast.makeText(mActivity, mActivity.getString(R.string.battlyworlds_logs_saved, file.getName()), Toast.LENGTH_SHORT).show();
        } catch (Throwable throwable) {
            Tools.showError(mActivity, throwable);
        }
    }

    private void copyText(String text) {
        ClipboardManager clipboard = (ClipboardManager) mActivity.getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null) {
            clipboard.setPrimaryClip(ClipData.newPlainText("BattlyWorlds logs", text));
            Toast.makeText(mActivity, R.string.battlyworlds_copied, Toast.LENGTH_SHORT).show();
        }
    }

    private String getPlayerName() {
        MinecraftAccount account = PojavProfile.getCurrentProfileContent(mActivity, null);
        if (account != null && account.username != null && !account.username.trim().isEmpty()) {
            return account.username.replace("Demo.", "");
        }
        return mActivity.getString(R.string.battlyworlds_player_anonymous);
    }

    private TextView primaryAction(String label) {
        TextView button = action(label);
        button.setTextColor(0xFF0B171B);
        button.setBackground(round(ACCENT, dp(16), 0, 0));
        return button;
    }

    private TextView secondaryAction(String label) {
        TextView button = action(label);
        button.setTextColor(ACCENT);
        button.setBackground(round(0x223D5A60, dp(16), 0x338ADBC6, 1));
        return button;
    }

    private TextView action(String label) {
        TextView button = new TextView(mActivity);
        button.setText(label);
        button.setGravity(Gravity.CENTER);
        button.setTypeface(Typeface.DEFAULT_BOLD);
        button.setPadding(dp(14), dp(9), dp(14), dp(9));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(dp(8), 0, 0, 0);
        button.setLayoutParams(params);
        return button;
    }

    private TextView iconButton(String label) {
        TextView button = new TextView(mActivity);
        button.setText(label);
        button.setTextColor(TEXT_MAIN);
        button.setTextSize(16);
        button.setGravity(Gravity.CENTER);
        button.setTypeface(Typeface.DEFAULT_BOLD);
        button.setBackground(round(0x263C4E58, dp(16), 0x22495D68, 1));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(36), dp(36));
        params.setMargins(dp(8), 0, 0, 0);
        button.setLayoutParams(params);
        return button;
    }

    private ImageButton iconButton(int drawableRes) {
        ImageButton button = new ImageButton(mActivity);
        button.setImageResource(drawableRes);
        button.setColorFilter(TEXT_MAIN);
        button.setScaleType(ImageView.ScaleType.CENTER);
        button.setPadding(dp(8), dp(8), dp(8), dp(8));
        button.setBackground(round(0x263C4E58, dp(16), 0x22495D68, 1));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(36), dp(36));
        params.setMargins(dp(8), 0, 0, 0);
        button.setLayoutParams(params);
        return button;
    }

    private void setBusy(boolean busy) {
        if (busy) {
            mStatus.setText(R.string.battlyworlds_state_loading_nodes);
        } else {
            refreshState();
        }
    }

    private void sortFriends(List<BattlyWorldsInvites.Friend> friends) {
        if (friends == null) return;
        Collections.sort(friends, Comparator
                .comparing((BattlyWorldsInvites.Friend f) -> !isOnline(f))
                .thenComparing(f -> f.username.toLowerCase()));
    }

    private List<BattlyWorldsInvites.Friend> filterFriends(List<BattlyWorldsInvites.Friend> friends, String query) {
        String clean = query == null ? "" : query.trim().toLowerCase();
        List<BattlyWorldsInvites.Friend> result = new ArrayList<>();
        if (friends == null) return result;
        for (BattlyWorldsInvites.Friend friend : friends) {
            if (clean.isEmpty() || friend.username.toLowerCase().contains(clean)) result.add(friend);
        }
        return result;
    }

    private boolean isOnline(BattlyWorldsInvites.Friend friend) {
        if (friend == null || friend.state == null) return false;
        String state = friend.state.trim().toLowerCase();
        return !state.isEmpty()
                && !"offline".equals(state)
                && !"desconectado".equals(state)
                && !"0".equals(state)
                && !"false".equals(state);
    }

    private GradientDrawable round(int color, int radius, int strokeColor, int strokeWidth) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        if (strokeWidth > 0) drawable.setStroke(strokeWidth, strokeColor);
        return drawable;
    }

    private int dp(int value) {
        return (int) (value * mActivity.getResources().getDisplayMetrics().density + 0.5f);
    }

    private static class SimpleTextWatcher implements TextWatcher {
        private final Runnable mRunnable;

        SimpleTextWatcher(Runnable runnable) {
            mRunnable = runnable;
        }

        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
            mRunnable.run();
        }

        @Override
        public void afterTextChanged(Editable s) {
        }
    }
}
