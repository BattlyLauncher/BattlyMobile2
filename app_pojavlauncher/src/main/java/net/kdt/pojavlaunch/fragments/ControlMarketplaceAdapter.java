package net.kdt.pojavlaunch.fragments;

import android.graphics.drawable.Drawable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ImageSpan;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import net.kdt.pojavlaunch.R;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class ControlMarketplaceAdapter extends RecyclerView.Adapter<ControlMarketplaceAdapter.ViewHolder> {

    public interface OnApplyListener {
        void onApply(JSONObject item);

        void onPreview(JSONObject item);

        void onLike(JSONObject item);

        void onSave(JSONObject item);

        boolean isLiked(JSONObject item);

        boolean isSaved(JSONObject item);
    }

    private final List<JSONObject> mItems = new ArrayList<>();
    private final OnApplyListener mListener;

    public ControlMarketplaceAdapter(OnApplyListener listener) {
        mListener = listener;
    }

    public void setItems(JSONArray items) {
        mItems.clear();
        if (items != null) {
            for (int i = 0; i < items.length(); i++) {
                JSONObject obj = items.optJSONObject(i);
                if (obj != null) mItems.add(obj);
            }
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_control_marketplace, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        JSONObject item = mItems.get(position);
        holder.bind(item, mListener);
    }

    @Override
    public int getItemCount() {
        return mItems.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView mName;
        final TextView mAuthor;
        final TextView mDescription;
        final TextView mTags;
        final TextView mStats;
        final Button mApplyBtn;
        final Button mPreviewBtn;
        final Button mLikeBtn;
        final Button mSaveBtn;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            mName = itemView.findViewById(R.id.ctrl_item_name);
            mAuthor = itemView.findViewById(R.id.ctrl_item_author);
            mDescription = itemView.findViewById(R.id.ctrl_item_desc);
            mTags = itemView.findViewById(R.id.ctrl_item_tags);
            mStats = itemView.findViewById(R.id.ctrl_item_stats);
            mApplyBtn = itemView.findViewById(R.id.ctrl_item_apply_btn);
            mPreviewBtn = itemView.findViewById(R.id.ctrl_item_preview_btn);
            mLikeBtn = itemView.findViewById(R.id.ctrl_item_like_btn);
            mSaveBtn = itemView.findViewById(R.id.ctrl_item_save_btn);
        }

        void bind(JSONObject item, OnApplyListener listener) {
            mName.setText(item.optString(
                    "name",
                    mName.getContext().getString(R.string.ctrl_item_unnamed)));
            mAuthor.setText(mAuthor.getContext().getString(
                    R.string.ctrl_item_by_author,
                    item.optString(
                            "ownerName",
                            mAuthor.getContext().getString(R.string.ctrl_item_anonymous))));

            String desc = item.optString("description", "").trim();
            mDescription.setText(desc.isEmpty()
                    ? mDescription.getContext().getString(R.string.ctrl_item_no_description)
                    : desc);
            mDescription.setVisibility(View.VISIBLE);

            JSONArray tags = item.optJSONArray("tags");
            if (tags != null && tags.length() > 0) {
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < tags.length(); i++) {
                    if (i > 0) sb.append("  ");
                    sb.append("#").append(tags.optString(i));
                }
                mTags.setText(sb.toString());
                mTags.setVisibility(View.VISIBLE);
            } else {
                mTags.setVisibility(View.GONE);
            }

            int downloads = item.optInt("downloads", 0);
            int likes = item.optInt("likes", 0);
            int iconSize = (int) (mStats.getTextSize() * 1.3f);

            Drawable arrowIcon = mStats.getContext().getDrawable(R.drawable.minecraft_arrow);
            arrowIcon.setBounds(0, 0, iconSize, iconSize);
            Drawable heartIcon = mStats.getContext().getDrawable(R.drawable.minecraft_heart_pottery_sherd);
            heartIcon.setBounds(0, 0, iconSize, iconSize);

            SpannableStringBuilder statsText = new SpannableStringBuilder();
            statsText.append("\u00A0");
            statsText.setSpan(new ImageSpan(arrowIcon, ImageSpan.ALIGN_BASELINE),
                    0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            statsText.append(" ").append(String.valueOf(downloads)).append("   ");
            int heartStart = statsText.length();
            statsText.append("\u00A0");
            statsText.setSpan(new ImageSpan(heartIcon, ImageSpan.ALIGN_BASELINE),
                    heartStart, heartStart + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            statsText.append(" ").append(String.valueOf(likes));
            mStats.setText(statsText);

            boolean liked = listener.isLiked(item);
            boolean saved = listener.isSaved(item);
            mPreviewBtn.setText(R.string.ctrl_preview_btn);
            mApplyBtn.setText(R.string.ctrl_apply_btn);
            mLikeBtn.setText(liked ? R.string.ctrl_liked_btn : R.string.ctrl_like_btn);
            mSaveBtn.setText(saved ? R.string.ctrl_saved_btn : R.string.ctrl_save_btn);
            setButtonIcon(mApplyBtn, android.R.drawable.ic_menu_save);
            setButtonIcon(mPreviewBtn, android.R.drawable.ic_menu_view);
            setButtonIcon(mLikeBtn, liked ? android.R.drawable.btn_star_big_on : android.R.drawable.btn_star_big_off);
            setButtonIcon(mSaveBtn, saved ? android.R.drawable.ic_menu_agenda : android.R.drawable.ic_menu_add);
            mApplyBtn.setOnClickListener(v -> listener.onApply(item));
            mPreviewBtn.setOnClickListener(v -> listener.onPreview(item));
            mLikeBtn.setOnClickListener(v -> listener.onLike(item));
            mSaveBtn.setOnClickListener(v -> listener.onSave(item));
        }

        private void setButtonIcon(Button button, int iconRes) {
            Drawable icon = button.getContext().getDrawable(iconRes);
            if (icon == null) return;
            CharSequence label = button.getText();
            int size = Math.round(button.getTextSize() * 1.05f);
            icon.setBounds(0, 0, size, size);
            icon.setTint(0xFF8DEEDC);
            SpannableStringBuilder text = new SpannableStringBuilder();
            text.append("\u00A0");
            text.setSpan(new ImageSpan(icon, ImageSpan.ALIGN_BASELINE),
                    0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            text.append("  ").append(label);
            button.setAllCaps(false);
            button.setGravity(Gravity.CENTER);
            button.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
            button.setMinWidth(0);
            button.setMinimumWidth(0);
            button.setPadding(0, 0, 0, 0);
            button.setIncludeFontPadding(false);
            button.setCompoundDrawables(null, null, null, null);
            button.setText(text);
        }
    }
}
