package net.kdt.pojavlaunch.battlyworlds;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.widget.SwitchCompat;

import net.kdt.pojavlaunch.R;

import java.util.List;
import java.util.ArrayList;

final class BattlyWorldsVoiceDialog implements BattlyWorldsVoiceManager.Listener,
        BattlyWorldsRealtimeClient.PartyInviteListener {
    private final Activity activity;
    private final LinearLayout participants;
    private final TextView connect;
    private final TextView microphone;
    private final TextView deafen;
    private final TextView roomMode;
    private final TextView partyMode;
    private final TextView modeDescription;
    private final AlertDialog dialog;

    BattlyWorldsVoiceDialog(Activity activity) {
        this.activity = activity;
        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(12), dp(18), dp(16));

        TextView title = text(R.string.battlyworlds_voice_title, 20, true);
        root.addView(title);
        TextView description = text(R.string.battlyworlds_voice_description, 13, false);
        description.setTextColor(0xFFC6D6E3);
        description.setPadding(0, dp(4), 0, dp(12));
        root.addView(description);

        LinearLayout actions = new LinearLayout(activity);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        connect = action(R.string.battlyworlds_voice_join, R.drawable.ic_social_join);
        microphone = action(R.string.battlyworlds_voice_mute, R.drawable.ic_battly_mic);
        deafen = action(R.string.battlyworlds_voice_deafen, R.drawable.ic_battly_headphones);
        actions.addView(connect, weight(0));
        actions.addView(microphone, weight(8));
        actions.addView(deafen, weight(8));
        root.addView(actions);

        LinearLayout modes = new LinearLayout(activity);
        modes.setOrientation(LinearLayout.HORIZONTAL);
        modes.setPadding(0, dp(10), 0, 0);
        roomMode = action(R.string.battlyworlds_voice_room_mode, R.drawable.ic_battly_worlds_line);
        partyMode = action(R.string.battlyworlds_voice_party_mode, R.drawable.ic_battly_social);
        modes.addView(roomMode, weight(0));
        modes.addView(partyMode, weight(8));
        root.addView(modes);
        modeDescription = text(0, 12, false);
        modeDescription.setTextColor(0xFF9FB8C5);
        modeDescription.setPadding(0, dp(6), 0, 0);
        root.addView(modeDescription);
        TextView overlaySettings = action(R.string.battlyworlds_voice_overlay_settings,
                R.drawable.ic_battly_settings_line);
        LinearLayout.LayoutParams overlayParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        overlayParams.topMargin = dp(8);
        root.addView(overlaySettings, overlayParams);
        overlaySettings.setOnClickListener(v -> showOverlaySettings());

        TextView membersTitle = text(R.string.battlyworlds_voice_participants, 14, true);
        membersTitle.setPadding(0, dp(14), 0, dp(6));
        root.addView(membersTitle);
        participants = new LinearLayout(activity);
        participants.setOrientation(LinearLayout.VERTICAL);
        root.addView(participants, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        ScrollView contentScroll = new ScrollView(activity);
        contentScroll.setFillViewport(true);
        contentScroll.setOverScrollMode(ScrollView.OVER_SCROLL_IF_CONTENT_SCROLLS);
        contentScroll.addView(root, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        connect.setOnClickListener(v -> {
            if (BattlyWorldsVoiceManager.isJoined()) BattlyWorldsVoiceManager.leave();
            else BattlyWorldsVoiceManager.join(activity);
        });
        microphone.setOnClickListener(v -> BattlyWorldsVoiceManager.setMuted(
                !BattlyWorldsVoiceManager.isMuted()));
        deafen.setOnClickListener(v -> BattlyWorldsVoiceManager.setDeafened(
                !BattlyWorldsVoiceManager.isDeafened()));
        roomMode.setOnClickListener(v -> switchToRoom());
        partyMode.setOnClickListener(v -> showPartySelector());

        dialog = new AlertDialog.Builder(activity, R.style.BattlyDialog)
                .setView(contentScroll)
                .setNegativeButton(R.string.global_cancel, null)
                .create();
        dialog.setOnDismissListener(ignored -> {
            BattlyWorldsVoiceManager.removeListener(this);
            BattlyWorldsRealtimeClient.removeListener(this);
        });
    }

    void show() {
        dialog.show();
        BattlyWorldsVoiceManager.addListener(this);
        BattlyWorldsRealtimeClient.addListener(this);
        refresh();
        if (dialog.getWindow() != null) {
            int available = activity.getResources().getDisplayMetrics().widthPixels - dp(32);
            int availableHeight = activity.getResources().getDisplayMetrics().heightPixels - dp(32);
            dialog.getWindow().setLayout(Math.min(available, dp(620)), availableHeight);
        }
    }

    @Override public void onVoiceChanged() { activity.runOnUiThread(this::refresh); }
    @Override public void onVoiceError(String message) {
        activity.runOnUiThread(() -> Toast.makeText(activity, message, Toast.LENGTH_LONG).show());
    }
    @Override public void onMembersChanged(List<BattlyWorldsRealtimeClient.Member> members) {
        activity.runOnUiThread(() -> renderMembers(members));
    }
    @Override public void onVoiceChannelChanged(String voiceChannel) {
        activity.runOnUiThread(this::refresh);
    }
    @Override public void onVoicePartyInvite(String partyId, String fromUsername) {
        activity.runOnUiThread(() -> showPartyInvite(partyId, fromUsername));
    }

    private void refresh() {
        boolean joined = BattlyWorldsVoiceManager.isJoined();
        boolean joining = BattlyWorldsVoiceManager.isJoining();
        connect.setText(joined ? R.string.battlyworlds_voice_leave
                : joining ? R.string.battlyworlds_voice_connecting : R.string.battlyworlds_voice_join);
        connect.setCompoundDrawablesRelativeWithIntrinsicBounds(
                joined ? R.drawable.ic_close_white : R.drawable.ic_social_join, 0, 0, 0);
        connect.setEnabled(!joining);
        connect.setAlpha(joining ? 0.65f : 1f);
        microphone.setText(BattlyWorldsVoiceManager.isMuted()
                ? R.string.battlyworlds_voice_unmute : R.string.battlyworlds_voice_mute);
        microphone.setCompoundDrawablesRelativeWithIntrinsicBounds(
                BattlyWorldsVoiceManager.isMuted() ? R.drawable.ic_battly_mic_off
                        : R.drawable.ic_battly_mic, 0, 0, 0);
        deafen.setText(BattlyWorldsVoiceManager.isDeafened()
                ? R.string.battlyworlds_voice_undeafen : R.string.battlyworlds_voice_deafen);
        microphone.setEnabled(joined);
        deafen.setEnabled(joined);
        microphone.setAlpha(joined ? 1f : 0.45f);
        deafen.setAlpha(joined ? 1f : 0.45f);
        boolean inParty = !"room".equals(BattlyWorldsRealtimeClient.getCurrentVoiceChannel());
        roomMode.setBackground(background(inParty ? 0xCC1E343D : 0xDD287762));
        partyMode.setBackground(background(inParty ? 0xDD287762 : 0xCC1E343D));
        roomMode.setEnabled(joined && inParty);
        partyMode.setEnabled(joined);
        modeDescription.setText(inParty ? R.string.battlyworlds_voice_party_active
                : R.string.battlyworlds_voice_room_active);
        renderMembers(BattlyWorldsRealtimeClient.getMembers());
    }

    private void renderMembers(List<BattlyWorldsRealtimeClient.Member> members) {
        participants.removeAllViews();
        String ownId = BattlyWorldsRealtimeClient.getCurrentUserId();
        String ownChannel = BattlyWorldsRealtimeClient.getCurrentVoiceChannel();
        for (BattlyWorldsRealtimeClient.Member member : members) {
            if (member.userId.equals(ownId) || !member.voiceConnected
                    || !ownChannel.equals(member.voiceChannel)) continue;
            LinearLayout row = new LinearLayout(activity);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(12), dp(8), dp(8), dp(8));
            row.setBackground(background(0x9930434D));

            boolean speaking = BattlyWorldsVoiceManager.isUserSpeaking(member.userId);
            FrameLayout avatarFrame = new FrameLayout(activity);
            avatarFrame.setPadding(dp(2), dp(2), dp(2), dp(2));
            avatarFrame.setBackground(avatarBorder(speaking));
            ImageView avatar = new ImageView(activity);
            avatar.setScaleType(ImageView.ScaleType.CENTER_CROP);
            avatar.setClipToOutline(true);
            avatar.setBackground(background(0xFF20343E));
            avatarFrame.addView(avatar, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            BattlyWorldsAvatarLoader.load(avatar, member.username);
            row.addView(avatarFrame, new LinearLayout.LayoutParams(dp(42), dp(42)));

            LinearLayout memberInfo = new LinearLayout(activity);
            memberInfo.setOrientation(LinearLayout.VERTICAL);
            memberInfo.setPadding(dp(10), 0, dp(8), 0);
            TextView name = text(0, 14, true);
            name.setText(member.username + (member.muted ? " · " + activity.getString(R.string.battlyworlds_voice_muted) : ""));
            memberInfo.addView(name);
            LinearLayout volumeRow = new LinearLayout(activity);
            volumeRow.setGravity(Gravity.CENTER_VERTICAL);
            SeekBar volume = new SeekBar(activity);
            volume.setMax(100);
            volume.setProgress(BattlyWorldsVoiceManager.getUserVolume(member.userId));
            TextView volumeValue = text(0, 11, false);
            volumeValue.setText(volume.getProgress() + "%");
            volumeValue.setMinWidth(dp(42));
            volumeValue.setGravity(Gravity.END);
            volume.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override public void onProgressChanged(SeekBar seekBar, int value, boolean fromUser) {
                    volumeValue.setText(value + "%");
                    if (fromUser) BattlyWorldsVoiceManager.setUserVolume(member.userId, value);
                }
                @Override public void onStartTrackingTouch(SeekBar seekBar) { }
                @Override public void onStopTrackingTouch(SeekBar seekBar) { }
            });
            volumeRow.addView(volume, new LinearLayout.LayoutParams(0, dp(30), 1f));
            volumeRow.addView(volumeValue);
            memberInfo.addView(volumeRow);
            row.addView(memberInfo, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            SwitchCompat silence = new SwitchCompat(activity);
            silence.setText(R.string.battlyworlds_voice_silence_user);
            silence.setTextColor(0xFFC6D6E3);
            silence.setChecked(BattlyWorldsVoiceManager.isUserSilenced(member.userId));
            silence.setEnabled(member.voiceConnected && BattlyWorldsVoiceManager.isJoined());
            silence.setOnCheckedChangeListener((button, checked) ->
                    BattlyWorldsVoiceManager.setUserSilenced(member.userId, checked));
            row.addView(silence);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            params.bottomMargin = dp(6);
            participants.addView(row, params);
        }
        if (participants.getChildCount() == 0) {
            TextView empty = text(R.string.battlyworlds_voice_no_participants, 13, false);
            empty.setTextColor(0xFF9FB8C5);
            participants.addView(empty);
        }
    }

    private void switchToRoom() {
        BattlyWorldsRealtimeClient.switchToRoomVoice((success, value) -> activity.runOnUiThread(() -> {
            if (!success) Toast.makeText(activity, R.string.battlyworlds_voice_party_error,
                    Toast.LENGTH_LONG).show();
            refresh();
        }));
    }

    private void showPartySelector() {
        List<BattlyWorldsRealtimeClient.Member> available = new ArrayList<>();
        String ownId = BattlyWorldsRealtimeClient.getCurrentUserId();
        for (BattlyWorldsRealtimeClient.Member member : BattlyWorldsRealtimeClient.getMembers()) {
            if (!member.userId.equals(ownId)) available.add(member);
        }
        if (available.isEmpty()) {
            Toast.makeText(activity, R.string.battlyworlds_voice_party_empty, Toast.LENGTH_LONG).show();
            return;
        }
        String[] names = new String[available.size()];
        boolean[] selected = new boolean[available.size()];
        for (int i = 0; i < available.size(); i++) names[i] = available.get(i).username;
        new AlertDialog.Builder(activity, R.style.BattlyDialog)
                .setTitle(R.string.battlyworlds_voice_party_create)
                .setMultiChoiceItems(names, selected, (dialog, which, checked) -> selected[which] = checked)
                .setNegativeButton(R.string.global_cancel, null)
                .setPositiveButton(R.string.battlyworlds_voice_party_invite, (dialog, which) -> {
                    List<String> ids = new ArrayList<>();
                    for (int i = 0; i < selected.length; i++) if (selected[i]) ids.add(available.get(i).userId);
                    BattlyWorldsRealtimeClient.createVoiceParty(ids, (success, value) ->
                            activity.runOnUiThread(() -> {
                                Toast.makeText(activity, success
                                                ? R.string.battlyworlds_voice_party_created
                                                : R.string.battlyworlds_voice_party_error,
                                        Toast.LENGTH_LONG).show();
                                refresh();
                            }));
                }).show();
    }

    private void showPartyInvite(String partyId, String fromUsername) {
        new AlertDialog.Builder(activity, R.style.BattlyDialog)
                .setTitle(R.string.battlyworlds_voice_party_invitation)
                .setMessage(activity.getString(R.string.battlyworlds_voice_party_invitation_message,
                        fromUsername))
                .setNegativeButton(R.string.global_cancel, null)
                .setPositiveButton(R.string.battlyworlds_voice_party_join, (dialog, which) ->
                        BattlyWorldsRealtimeClient.joinVoiceParty(partyId, (success, value) ->
                                activity.runOnUiThread(() -> {
                                    if (!success) Toast.makeText(activity,
                                            R.string.battlyworlds_voice_party_error,
                                            Toast.LENGTH_LONG).show();
                                    refresh();
                                }))).show();
    }

    private void showOverlaySettings() {
        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(18), dp(8), dp(18), dp(4));
        TextView help = text(R.string.battlyworlds_voice_overlay_drag_help, 13, false);
        help.setTextColor(0xFFC6D6E3);
        content.addView(help);

        LinearLayout opacityRow = new LinearLayout(activity);
        opacityRow.setGravity(Gravity.CENTER_VERTICAL);
        opacityRow.setPadding(0, dp(12), 0, 0);
        TextView opacityLabel = text(R.string.battlyworlds_voice_overlay_opacity, 13, true);
        TextView opacityValue = text(0, 12, false);
        SeekBar opacity = new SeekBar(activity);
        opacity.setMax(75);
        int current = BattlyWorldsPreferences.getVoiceOverlayOpacity(activity);
        opacity.setProgress(current - 25);
        opacityValue.setText(current + "%");
        opacityValue.setGravity(Gravity.END);
        opacityValue.setMinWidth(dp(46));
        opacity.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int value = progress + 25;
                opacityValue.setText(value + "%");
                if (fromUser) {
                    BattlyWorldsPreferences.setVoiceOverlayOpacity(activity, value);
                    BattlyWorldsVoiceOverlay.refreshAppearance(activity);
                }
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) { }
            @Override public void onStopTrackingTouch(SeekBar seekBar) { }
        });
        opacityRow.addView(opacityLabel);
        opacityRow.addView(opacity, new LinearLayout.LayoutParams(0, dp(36), 1f));
        opacityRow.addView(opacityValue);
        content.addView(opacityRow);

        FrameLayout editor = BattlyWorldsVoiceOverlay.createPositionEditor(activity,
                activity.getString(R.string.battlyworlds_voice_overlay_preview));
        LinearLayout.LayoutParams editorParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(190));
        editorParams.topMargin = dp(10);
        content.addView(editor, editorParams);
        AlertDialog settings = new AlertDialog.Builder(activity, R.style.BattlyDialog)
                .setTitle(R.string.battlyworlds_voice_overlay_settings)
                .setView(content)
                .setNeutralButton(R.string.battlyworlds_voice_overlay_reset, (dialog, which) ->
                        BattlyWorldsVoiceOverlay.resetPosition(activity))
                .setPositiveButton(android.R.string.ok, null)
                .create();
        settings.show();
    }

    private TextView action(int textRes, int iconRes) {
        TextView view = text(textRes, 13, true);
        view.setGravity(Gravity.CENTER);
        view.setPadding(dp(12), dp(10), dp(12), dp(10));
        view.setCompoundDrawablesRelativeWithIntrinsicBounds(iconRes, 0, 0, 0);
        view.setCompoundDrawablePadding(dp(8));
        view.setBackground(background(0xCC1E343D));
        return view;
    }

    private TextView text(int textRes, int size, boolean bold) {
        TextView view = new TextView(activity);
        if (textRes != 0) view.setText(textRes);
        view.setTextColor(0xFFFFFFFF);
        view.setTextSize(size);
        if (bold) view.setTypeface(Typeface.DEFAULT_BOLD);
        return view;
    }

    private LinearLayout.LayoutParams weight(int leftMargin) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        params.leftMargin = dp(leftMargin);
        return params;
    }

    private static GradientDrawable background(int color) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(12);
        drawable.setStroke(1, 0x558ADBC6);
        return drawable;
    }

    private static GradientDrawable avatarBorder(boolean speaking) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(0x00203038);
        drawable.setCornerRadius(10);
        drawable.setStroke(speaking ? 3 : 1, speaking ? 0xFF72E3BE : 0x665D7782);
        return drawable;
    }

    private int dp(int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }
}
