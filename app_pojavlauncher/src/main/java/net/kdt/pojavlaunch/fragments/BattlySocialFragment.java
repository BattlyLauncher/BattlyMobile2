package net.kdt.pojavlaunch.fragments;

import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ImageSpan;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import net.kdt.pojavlaunch.PojavApplication;
import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.battlysocial.BattlySocialApi;
import net.kdt.pojavlaunch.battlysocial.BattlySocialManager;
import net.kdt.pojavlaunch.battlyworlds.BattlyWorldsFeature;
import net.kdt.pojavlaunch.utils.BattlySkinApi;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class BattlySocialFragment extends Fragment {
    public static final String TAG = "BattlySocialFragment";
    private static final long REFRESH_INTERVAL_MS = 15000L;

    private enum Mode { FRIENDS, REQUESTS, SEARCH }

    private final List<Row> rows = new ArrayList<>();
    private final SocialAdapter adapter = new SocialAdapter();
    private final Map<String, Bitmap> avatarCache = new ConcurrentHashMap<>();
    private final Runnable refreshRunnable = new Runnable() {
        @Override
        public void run() {
            if (!isAdded()) return;
            loadOverview(false);
            Tools.MAIN_HANDLER.postDelayed(this, REFRESH_INTERVAL_MS);
        }
    };

    private TextView statusView;
    private TextView summaryView;
    private View searchBar;
    private EditText searchInput;
    private Button friendsTab;
    private Button requestsTab;
    private Button searchTab;
    private BattlySocialApi.Overview overview;
    private List<BattlySocialApi.Invite> invites = Collections.emptyList();
    private List<BattlySocialApi.SearchUser> searchResults = Collections.emptyList();
    private Mode mode = Mode.FRIENDS;
    private boolean loading;

    public BattlySocialFragment() {
        super(R.layout.fragment_battly_social);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        statusView = view.findViewById(R.id.social_status);
        summaryView = view.findViewById(R.id.social_summary);
        searchBar = view.findViewById(R.id.social_search_bar);
        searchInput = view.findViewById(R.id.social_search_input);
        friendsTab = view.findViewById(R.id.social_tab_friends);
        requestsTab = view.findViewById(R.id.social_tab_requests);
        searchTab = view.findViewById(R.id.social_tab_search);
        ImageButton refresh = view.findViewById(R.id.social_refresh);
        ImageButton searchSubmit = view.findViewById(R.id.social_search_submit);
        RecyclerView list = view.findViewById(R.id.social_list);
        list.setLayoutManager(new GridLayoutManager(requireContext(), 2));
        list.setAdapter(adapter);
        updateSummary(0, 0, 0);

        refresh.setOnClickListener(v -> loadOverview(true));
        friendsTab.setOnClickListener(v -> setMode(Mode.FRIENDS));
        requestsTab.setOnClickListener(v -> setMode(Mode.REQUESTS));
        searchTab.setOnClickListener(v -> setMode(Mode.SEARCH));
        searchSubmit.setOnClickListener(v -> runSearch());
        searchInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                runSearch();
                return true;
            }
            return false;
        });
        BattlySocialManager.heartbeatLauncher(requireContext());
        loadOverview(true);
    }

    @Override
    public void onResume() {
        super.onResume();
        Tools.MAIN_HANDLER.removeCallbacks(refreshRunnable);
        Tools.MAIN_HANDLER.postDelayed(refreshRunnable, REFRESH_INTERVAL_MS);
    }

    @Override
    public void onPause() {
        Tools.MAIN_HANDLER.removeCallbacks(refreshRunnable);
        super.onPause();
    }

    private void setMode(Mode nextMode) {
        mode = nextMode;
        searchBar.setVisibility(mode == Mode.SEARCH ? View.VISIBLE : View.GONE);
        friendsTab.setAlpha(mode == Mode.FRIENDS ? 1f : 0.68f);
        requestsTab.setAlpha(mode == Mode.REQUESTS ? 1f : 0.68f);
        searchTab.setAlpha(mode == Mode.SEARCH ? 1f : 0.68f);
        rebuildRows();
        if (mode == Mode.SEARCH) searchInput.requestFocus();
    }

    private void loadOverview(boolean showLoading) {
        if (loading || !isAdded()) return;
        loading = true;
        if (showLoading) statusView.setText(R.string.battly_social_loading);
        final android.content.Context appContext = requireContext().getApplicationContext();
        PojavApplication.sExecutorService.execute(() -> {
            try {
                BattlySocialApi.Overview loadedOverview = BattlySocialApi.fetchOverview(appContext);
                List<BattlySocialApi.Invite> loadedInvites = BattlySocialApi.fetchInvites(appContext);
                Tools.runOnUiThread(() -> {
                    loading = false;
                    if (!isAdded()) return;
                    overview = loadedOverview;
                    invites = loadedInvites;
                    updateSummary(overview.onlineCount, overview.playingCount,
                            overview.requestCount + overview.inviteCount);
                    rebuildRows();
                });
            } catch (Throwable throwable) {
                Tools.runOnUiThread(() -> {
                    loading = false;
                    if (!isAdded()) return;
                    statusView.setText(throwable.getMessage());
                });
            }
        });
    }

    private void updateSummary(int online, int playing, int pending) {
        summaryView.setText(getString(R.string.battly_social_summary, online, playing, pending));
    }

    private void runSearch() {
        String query = searchInput.getText() == null ? "" : searchInput.getText().toString().trim();
        if (query.length() < 2) {
            statusView.setText(R.string.battly_social_search_min);
            return;
        }
        statusView.setText(R.string.battly_social_searching);
        final android.content.Context appContext = requireContext().getApplicationContext();
        PojavApplication.sExecutorService.execute(() -> {
            try {
                List<BattlySocialApi.SearchUser> results = BattlySocialApi.search(appContext, query);
                Tools.runOnUiThread(() -> {
                    if (!isAdded()) return;
                    searchResults = results;
                    rebuildRows();
                });
            } catch (Throwable throwable) {
                Tools.runOnUiThread(() -> {
                    if (isAdded()) statusView.setText(throwable.getMessage());
                });
            }
        });
    }

    private void rebuildRows() {
        rows.clear();
        if (overview == null) {
            adapter.notifyDataSetChanged();
            return;
        }
        if (mode == Mode.FRIENDS) {
            for (BattlySocialApi.Invite invite : invites) rows.add(Row.invite(invite));
            for (BattlySocialApi.Friend friend : overview.friends) rows.add(Row.friend(friend));
            statusView.setText(rows.isEmpty()
                    ? R.string.battly_social_no_friends
                    : R.string.battly_social_friends_hint);
        } else if (mode == Mode.REQUESTS) {
            for (BattlySocialApi.Request request : overview.receivedRequests) rows.add(Row.received(request));
            for (BattlySocialApi.Request request : overview.sentRequests) rows.add(Row.sent(request));
            statusView.setText(rows.isEmpty()
                    ? R.string.battly_social_no_requests
                    : R.string.battly_social_requests_hint);
        } else {
            for (BattlySocialApi.SearchUser user : searchResults) rows.add(Row.search(user));
            statusView.setText(searchResults.isEmpty()
                    ? R.string.battly_social_search_empty
                    : R.string.battly_social_search_results);
        }
        adapter.notifyDataSetChanged();
    }

    private void executeAction(NetworkAction action, String successMessage) {
        final android.content.Context appContext = requireContext().getApplicationContext();
        statusView.setText(R.string.battly_social_working);
        PojavApplication.sExecutorService.execute(() -> {
            try {
                action.run(appContext);
                Tools.runOnUiThread(() -> {
                    if (!isAdded()) return;
                    Toast.makeText(requireContext(), successMessage, Toast.LENGTH_SHORT).show();
                    loadOverview(false);
                });
            } catch (Throwable throwable) {
                Tools.runOnUiThread(() -> {
                    if (isAdded()) statusView.setText(throwable.getMessage());
                });
            }
        });
    }

    private void loadAvatar(ImageView image, String username) {
        image.setTag(username);
        Bitmap cached = avatarCache.get(username);
        if (cached != null) {
            image.setImageBitmap(cached);
            return;
        }
        image.setImageResource(R.drawable.ic_battly_social);
        PojavApplication.sExecutorService.execute(() -> {
            try {
                Bitmap bitmap = BattlySkinApi.downloadFaceBitmap(username);
                avatarCache.put(username, bitmap);
                Tools.runOnUiThread(() -> {
                    if (username.equals(image.getTag())) image.setImageBitmap(bitmap);
                });
            } catch (Throwable ignored) {
            }
        });
    }

    private final class SocialAdapter extends RecyclerView.Adapter<SocialHolder> {
        @NonNull
        @Override
        public SocialHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new SocialHolder(LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_battly_social, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull SocialHolder holder, int position) {
            holder.bind(rows.get(position));
        }

        @Override
        public int getItemCount() {
            return rows.size();
        }
    }

    private final class SocialHolder extends RecyclerView.ViewHolder {
        final ImageView avatar;
        final TextView name;
        final TextView detail;
        final Button primary;
        final Button secondary;

        SocialHolder(@NonNull View itemView) {
            super(itemView);
            avatar = itemView.findViewById(R.id.social_avatar);
            name = itemView.findViewById(R.id.social_name);
            detail = itemView.findViewById(R.id.social_detail);
            primary = itemView.findViewById(R.id.social_primary_action);
            secondary = itemView.findViewById(R.id.social_secondary_action);
        }

        void bind(Row row) {
            loadAvatar(avatar, row.username);
            name.setText(row.username);
            primary.setVisibility(View.VISIBLE);
            secondary.setVisibility(View.VISIBLE);
            primary.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0);
            secondary.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0);
            primary.setEnabled(true);
            secondary.setEnabled(true);
            primary.setOnClickListener(null);
            secondary.setOnClickListener(null);
            itemView.setOnLongClickListener(null);

            if (row.kind == Row.KIND_FRIEND) {
                BattlySocialApi.Friend friend = row.friend;
                detail.setText(friendDetail(friend));
                if (friend.server != null && friend.server.joinable) {
                    setActionText(primary, R.string.battly_social_join, R.drawable.ic_social_join);
                    primary.setOnClickListener(v -> BattlySocialManager.joinServer(
                            requireActivity(), friend.server, friend.version));
                } else {
                    primary.setVisibility(View.GONE);
                }
                setActionText(secondary, R.string.battly_social_invite, R.drawable.ic_social_add);
                secondary.setOnClickListener(v -> inviteFriend(friend));
                itemView.setOnLongClickListener(v -> {
                    confirmRemove(friend.username);
                    return true;
                });
            } else if (row.kind == Row.KIND_INVITE) {
                BattlySocialApi.Invite invite = row.invite;
                detail.setText(invite.kind.equals("server") && invite.server != null
                        ? getString(R.string.battly_social_invited_server, invite.server.name, invite.version)
                        : getString(R.string.battly_social_invited_world));
                setActionText(primary, R.string.battly_social_join, R.drawable.ic_social_join);
                primary.setOnClickListener(v -> acceptInvite(invite));
                secondary.setText(R.string.battly_social_decline);
                secondary.setOnClickListener(v -> executeAction(
                        context -> BattlySocialApi.updateInvite(context, invite.inviteId, "declined"),
                        getString(R.string.battly_social_invite_declined)));
            } else if (row.kind == Row.KIND_RECEIVED) {
                detail.setText(R.string.battly_social_request_received);
                setActionText(primary, R.string.battly_social_accept, R.drawable.ic_social_accept);
                primary.setOnClickListener(v -> executeAction(
                        context -> BattlySocialApi.acceptRequest(context, row.username),
                        getString(R.string.battly_social_request_accepted)));
                secondary.setText(R.string.battly_social_decline);
                secondary.setOnClickListener(v -> executeAction(
                        context -> BattlySocialApi.rejectRequest(context, row.username),
                        getString(R.string.battly_social_request_declined)));
            } else if (row.kind == Row.KIND_SENT) {
                detail.setText(R.string.battly_social_request_sent);
                primary.setVisibility(View.GONE);
                secondary.setText(R.string.battly_social_cancel);
                secondary.setOnClickListener(v -> executeAction(
                        context -> BattlySocialApi.cancelRequest(context, row.username),
                        getString(R.string.battly_social_request_cancelled)));
            } else {
                BattlySocialApi.SearchUser user = row.searchUser;
                detail.setText(relationText(user.relation));
                secondary.setVisibility(View.GONE);
                if ("none".equals(user.relation)) {
                    setActionText(primary, R.string.battly_social_add, R.drawable.ic_social_add);
                    primary.setEnabled(true);
                    primary.setOnClickListener(v -> executeAction(
                            context -> BattlySocialApi.sendFriendRequest(context, user.username),
                            getString(R.string.battly_social_request_sent_ok)));
                } else {
                    primary.setText(relationText(user.relation));
                    primary.setEnabled(false);
                    primary.setOnClickListener(null);
                }
            }
        }
    }

    private void setActionText(Button button, int textRes, int iconRes) {
        Drawable icon = androidx.core.content.ContextCompat.getDrawable(requireContext(), iconRes);
        if (icon == null) {
            button.setText(textRes);
            return;
        }
        int size = Math.round(14 * getResources().getDisplayMetrics().density);
        icon.setBounds(0, 0, size, size);
        SpannableString label = new SpannableString("\uFFFC  " + getString(textRes));
        label.setSpan(new ImageSpan(icon, ImageSpan.ALIGN_BASELINE), 0, 1,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        button.setCompoundDrawables(null, null, null, null);
        button.setGravity(android.view.Gravity.CENTER);
        button.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        int horizontalPadding = Math.round(8 * getResources().getDisplayMetrics().density);
        button.setPadding(horizontalPadding, 0, horizontalPadding, 0);
        button.setText(label);
    }

    private void inviteFriend(BattlySocialApi.Friend friend) {
        if (overview != null && overview.myServer != null) {
            executeAction(
                    context -> BattlySocialApi.sendServerInvite(
                            context, friend.username, overview.myServer, overview.myVersion),
                    getString(R.string.battly_social_invite_sent));
            return;
        }
        BattlyWorldsFeature.showDisabledDialog(requireContext());
    }

    private void acceptInvite(BattlySocialApi.Invite invite) {
        if ("battlyworlds".equals(invite.kind)) {
            BattlyWorldsFeature.showDisabledDialog(requireContext());
            return;
        }
        if (invite.server == null) return;
        executeAction(
                context -> BattlySocialApi.updateInvite(context, invite.inviteId, "accepted"),
                getString(R.string.battly_social_join_preparing));
        BattlySocialManager.joinServer(requireActivity(), invite.server, invite.version);
    }

    private void confirmRemove(String username) {
        AlertDialog dialog = Tools.createStyledDialogBuilder(requireContext())
                .setTitle(R.string.battly_social_remove_title)
                .setMessage(getString(R.string.battly_social_remove_message, username))
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.battly_social_remove, (d, which) -> executeAction(
                        context -> BattlySocialApi.removeFriend(context, username),
                        getString(R.string.battly_social_removed)))
                .create();
        Tools.styleDialog(dialog);
        dialog.show();
    }

    private String friendDetail(BattlySocialApi.Friend friend) {
        if ("offline".equals(friend.state)) return getString(R.string.battly_social_offline);
        if (friend.server != null) {
            return getString(R.string.battly_social_playing_server,
                    TextUtils.isEmpty(friend.server.name) ? friend.server.address : friend.server.name,
                    TextUtils.isEmpty(friend.version) ? "-" : friend.version);
        }
        if (friend.isPlaying()) {
            return getString(R.string.battly_social_playing,
                    TextUtils.isEmpty(friend.version) ? "-" : friend.version);
        }
        return getString(R.string.battly_social_online);
    }

    private String relationText(String relation) {
        switch (relation) {
            case "friend":
                return getString(R.string.battly_social_already_friend);
            case "received":
                return getString(R.string.battly_social_request_received);
            case "sent":
                return getString(R.string.battly_social_request_sent);
            default:
                return getString(R.string.battly_social_user);
        }
    }

    private interface NetworkAction {
        void run(android.content.Context context) throws Exception;
    }

    private static final class Row {
        static final int KIND_FRIEND = 0;
        static final int KIND_INVITE = 1;
        static final int KIND_RECEIVED = 2;
        static final int KIND_SENT = 3;
        static final int KIND_SEARCH = 4;

        final int kind;
        final String username;
        final BattlySocialApi.Friend friend;
        final BattlySocialApi.Invite invite;
        final BattlySocialApi.SearchUser searchUser;

        private Row(int kind, String username, BattlySocialApi.Friend friend,
                    BattlySocialApi.Invite invite, BattlySocialApi.SearchUser searchUser) {
            this.kind = kind;
            this.username = username;
            this.friend = friend;
            this.invite = invite;
            this.searchUser = searchUser;
        }

        static Row friend(BattlySocialApi.Friend friend) {
            return new Row(KIND_FRIEND, friend.username, friend, null, null);
        }

        static Row invite(BattlySocialApi.Invite invite) {
            return new Row(KIND_INVITE, invite.fromUsername, null, invite, null);
        }

        static Row received(BattlySocialApi.Request request) {
            return new Row(KIND_RECEIVED, request.username, null, null, null);
        }

        static Row sent(BattlySocialApi.Request request) {
            return new Row(KIND_SENT, request.username, null, null, null);
        }

        static Row search(BattlySocialApi.SearchUser user) {
            return new Row(KIND_SEARCH, user.username, null, null, user);
        }
    }
}
