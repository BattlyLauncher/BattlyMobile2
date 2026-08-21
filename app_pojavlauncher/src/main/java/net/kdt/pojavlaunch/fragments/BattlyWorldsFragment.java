package net.kdt.pojavlaunch.fragments;

import android.content.Intent;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import net.kdt.pojavlaunch.LauncherActivity;
import net.kdt.pojavlaunch.PojavApplication;
import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.battlyworlds.BattlyWorldsInvites;
import net.kdt.pojavlaunch.battlyworlds.BattlyWorldsDiscovery;

import java.util.ArrayList;
import java.util.List;

public final class BattlyWorldsFragment extends Fragment {
    public static final String TAG = "BattlyWorldsFragment";
    private static final String PLUS_URL = "https://battlylauncher.com/plus"
            + "?utm_source=battly_worlds_mobile&utm_medium=app&utm_campaign=unlimited_invites";

    private final List<BattlyWorldsInvites.PublicRoom> rooms = new ArrayList<>();
    private final RoomAdapter adapter = new RoomAdapter();
    private TextView status;
    private TextView plusUpsell;
    private boolean loading;

    public BattlyWorldsFragment() {
        super(R.layout.fragment_battly_worlds);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        if (!BattlyWorldsInvites.isBattlyLoggedIn(requireContext())) {
            Toast.makeText(requireContext(), R.string.battlyworlds_login_required,
                    Toast.LENGTH_LONG).show();
            requireActivity().getSupportFragmentManager().popBackStack();
            return;
        }
        BattlyWorldsDiscovery.markCurrentCampaignSeen(requireContext());
        BattlyWorldsInvites.trackUsage(requireContext(), "worlds_opened", "public_rooms");
        status = view.findViewById(R.id.battlyworlds_public_status);
        plusUpsell = view.findViewById(R.id.battlyworlds_public_plus_upsell);
        Drawable plusLogo = AppCompatResources.getDrawable(requireContext(), R.drawable.logo);
        Drawable plusChevron = AppCompatResources.getDrawable(requireContext(), R.drawable.ic_battly_chevron);
        int logoSize = Math.round(24 * getResources().getDisplayMetrics().density);
        int chevronSize = Math.round(16 * getResources().getDisplayMetrics().density);
        if (plusLogo != null) plusLogo.setBounds(0, 0, logoSize, logoSize);
        if (plusChevron != null) plusChevron.setBounds(0, 0, chevronSize, chevronSize);
        plusUpsell.setCompoundDrawablesRelative(plusLogo, null, plusChevron, null);
        plusUpsell.setOnClickListener(v -> openPlusSubscription());
        RecyclerView list = view.findViewById(R.id.battlyworlds_public_list);
        list.setLayoutManager(new GridLayoutManager(requireContext(), 2));
        list.setAdapter(adapter);
        view.<ImageButton>findViewById(R.id.battlyworlds_public_refresh)
                .setOnClickListener(v -> loadRooms());
        view.findViewById(R.id.battlyworlds_join_code).setOnClickListener(v -> askJoinCode());
        loadRooms();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (!rooms.isEmpty()) loadRooms();
    }

    private void loadRooms() {
        if (loading || !isAdded()) return;
        loading = true;
        status.setText(R.string.battlyworlds_public_loading);
        Context appContext = requireContext().getApplicationContext();
        PojavApplication.sExecutorService.execute(() -> {
            try {
                BattlyWorldsInvites.Entitlements entitlements =
                        BattlyWorldsInvites.refreshEntitlements(appContext);
                List<BattlyWorldsInvites.PublicRoom> loaded = BattlyWorldsInvites.getPublicRooms(appContext);
                Tools.runOnUiThread(() -> {
                    loading = false;
                    if (!isAdded()) return;
                    rooms.clear();
                    rooms.addAll(loaded);
                    BattlyWorldsInvites.trackUsage(requireContext(), "public_rooms_viewed",
                            loaded.isEmpty() ? "empty" : "results");
                    adapter.notifyDataSetChanged();
                    plusUpsell.setVisibility(entitlements.plus ? View.GONE : View.VISIBLE);
                    status.setText(rooms.isEmpty()
                            ? R.string.battlyworlds_public_empty
                            : R.string.battlyworlds_public_available);
                });
            } catch (Throwable throwable) {
                Tools.runOnUiThread(() -> {
                    loading = false;
                    if (!isAdded()) return;
                    status.setText(getString(R.string.battlyworlds_public_error,
                            throwable.getMessage() == null ? "" : throwable.getMessage()));
                });
            }
        });
    }

    private void openPlusSubscription() {
        BattlyWorldsInvites.trackUsage(requireContext(), "plus_clicked", "public_rooms");
        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(PLUS_URL)));
    }

    private void askJoinCode() {
        EditText input = new EditText(requireContext());
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
        input.setHint(R.string.battlyworlds_join_hint);
        AlertDialog dialog = Tools.createStyledDialogBuilder(requireContext())
                .setTitle(R.string.battlyworlds_join_code)
                .setView(input)
                .setNegativeButton(R.string.global_cancel, null)
                .setPositiveButton(R.string.battlyworlds_join, null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> {
                    String code = input.getText() == null ? "" : input.getText().toString().trim().toUpperCase();
                    if (!BattlyWorldsInvites.looksLikeShortCode(code)) {
                        input.setError(getString(R.string.battlyworlds_invalid_code));
                        return;
                    }
                    dialog.dismiss();
                    BattlyWorldsInvites.trackUsage(requireContext(), "join_code_submitted", "manual_code");
                    BattlyWorldsInvites.joinPublicRoom((LauncherActivity) requireActivity(),
                            new BattlyWorldsInvites.PublicRoom(code, "", "", "", false, 3));
                }));
        Tools.styleDialog(dialog);
        dialog.show();
    }

    private void join(BattlyWorldsInvites.PublicRoom room) {
        BattlyWorldsInvites.trackUsage(requireContext(), "public_room_join_clicked", "room_card");
        BattlyWorldsInvites.joinPublicRoom((LauncherActivity) requireActivity(), room);
    }

    private final class RoomAdapter extends RecyclerView.Adapter<RoomHolder> {
        @NonNull
        @Override
        public RoomHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_battly_world_room, parent, false);
            return new RoomHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull RoomHolder holder, int position) {
            BattlyWorldsInvites.PublicRoom room = rooms.get(position);
            String title = room.title.isEmpty()
                    ? getString(R.string.battlyworlds_public_room_fallback, room.hostUsername)
                    : room.title;
            holder.title.setText(title);
            holder.meta.setText(getString(R.string.battlyworlds_public_room_meta,
                    room.version.isEmpty() ? "Minecraft" : room.version,
                    room.hostUsername.isEmpty() ? getString(R.string.battlyworlds_player_anonymous) : room.hostUsername));
            holder.code.setText(getString(R.string.battlyworlds_public_room_code, room.code));
            holder.itemView.setOnClickListener(v -> join(room));
            holder.join.setOnClickListener(v -> join(room));
        }

        @Override
        public int getItemCount() {
            return rooms.size();
        }
    }

    private static final class RoomHolder extends RecyclerView.ViewHolder {
        final TextView title;
        final TextView meta;
        final TextView code;
        final ImageButton join;

        RoomHolder(@NonNull View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.battlyworlds_room_title);
            meta = itemView.findViewById(R.id.battlyworlds_room_meta);
            code = itemView.findViewById(R.id.battlyworlds_room_code);
            join = itemView.findViewById(R.id.battlyworlds_room_join);
        }
    }
}
