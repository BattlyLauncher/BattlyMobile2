package net.kdt.pojavlaunch.profiles;

import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageButton;

import androidx.core.view.ViewCompat;
import androidx.core.graphics.ColorUtils;
import android.content.res.ColorStateList;

import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;
import net.kdt.pojavlaunch.value.launcherprofiles.LauncherProfiles;
import net.kdt.pojavlaunch.value.launcherprofiles.MinecraftProfile;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import fr.spse.extended_view.ExtendedTextView;

/*
 * Adapter for listing launcher profiles in a Spinner
 */
public class ProfileAdapter extends BaseAdapter {
    private Map<String, MinecraftProfile> mProfiles;
    private final MinecraftProfile dummy = new MinecraftProfile();
    private List<String> mProfileList;
    private ProfileAdapterExtra[] mExtraEntires;
    private OnProfileDeleteListener mDeleteListener;
    private OnProfileClickListener mClickListener;

    public ProfileAdapter(ProfileAdapterExtra[] extraEntries) {
        reloadProfiles(extraEntries);
    }
    /*
     * Gets how much profiles are loaded in the adapter right now
     * @returns loaded profile count
     */
    @Override
    public int getCount() {
        return mProfileList.size() + mExtraEntires.length;
    }
    /*
     * Gets the profile at a given index
     * @param position index to retreive
     * @returns MinecraftProfile name or null
     */
    @Override
    public Object getItem(int position) {
        int profileListSize = mProfileList.size();
        int extraPosition = position - profileListSize;
        if(position < profileListSize){
            String profileName = mProfileList.get(position);
            if(mProfiles.containsKey(profileName)) return profileName;
        }else if(extraPosition >= 0 && extraPosition < mExtraEntires.length) {
            return mExtraEntires[extraPosition];
        }
        return null;
    }



    public int resolveProfileIndex(String name) {
        return mProfileList.indexOf(name);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public void notifyDataSetChanged() {
        ensureProfilesLoaded();
        mProfiles = new HashMap<>(LauncherProfiles.mainProfileJson.profiles);
        mProfileList = new ArrayList<>(Arrays.asList(mProfiles.keySet().toArray(new String[0])));
        super.notifyDataSetChanged();
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        View v = convertView;
        if (v == null) v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_version_profile_layout,parent,false);
        Object item = getItem(position);
        setView(v, item, true);
        v.setOnClickListener(item != null && mClickListener != null
                ? clicked -> mClickListener.onClick(position, item)
                : null);
        ImageButton delete = v.findViewById(R.id.profile_item_delete);
        if (delete != null) {
            boolean canDelete = item instanceof String && mProfileList.size() > 1;
            delete.setVisibility(canDelete ? View.VISIBLE : View.GONE);
            delete.setFocusable(false);
            delete.setFocusableInTouchMode(false);
            delete.setOnClickListener(canDelete && mDeleteListener != null
                    ? clicked -> mDeleteListener.onDelete((String) item)
                    : null);
        }
        return v;
    }

    public void setViewProfile(View v, String nm, boolean displaySelection) {
        ExtendedTextView extendedTextView = textView(v);

        MinecraftProfile minecraftProfile = mProfiles.get(nm);
        if(minecraftProfile == null) minecraftProfile = dummy;
        Drawable cachedIcon = ProfileIconCache.fetchIcon(v.getResources(), nm, minecraftProfile.icon);
        extendedTextView.setCompoundDrawablesRelative(cachedIcon, null, extendedTextView.getCompoundsDrawables()[2], null);

        // Historically, the profile name "New" was hardcoded as the default profile name
        // We consider "New" the same as putting no name at all
        String profileName = (Tools.isValidString(minecraftProfile.name) && !"New".equalsIgnoreCase(minecraftProfile.name)) ? minecraftProfile.name : null;
        String versionName = minecraftProfile.lastVersionId;

        if (MinecraftProfile.LATEST_RELEASE.equalsIgnoreCase(versionName))
            versionName = v.getContext().getString(R.string.profiles_latest_release);
        else if (MinecraftProfile.LATEST_SNAPSHOT.equalsIgnoreCase(versionName))
            versionName = v.getContext().getString(R.string.profiles_latest_snapshot);

        if (versionName == null && profileName != null)
            extendedTextView.setText(profileName);
        else if (versionName != null && profileName == null)
            extendedTextView.setText(versionName);
        else extendedTextView.setText(String.format("%s - %s", profileName, versionName));

        // Keep the custom card background and only tint it when selected
        if(displaySelection){
            String selectedProfile = LauncherPreferences.DEFAULT_PREF.getString(LauncherPreferences.PREF_KEY_CURRENT_PROFILE,"");
            ViewCompat.setBackgroundTintList(extendedTextView, selectedProfile.equals(nm)
                    ? ColorStateList.valueOf(ColorUtils.setAlphaComponent(Color.WHITE, 22))
                    : null);
        }else ViewCompat.setBackgroundTintList(extendedTextView, null);
    }

    public void setViewExtra(View v, ProfileAdapterExtra extra) {
        ExtendedTextView extendedTextView = textView(v);
        extendedTextView.setCompoundDrawablesRelative(extra.icon, null, extendedTextView.getCompoundsDrawables()[2], null);
        extendedTextView.setText(extra.name);
        ViewCompat.setBackgroundTintList(extendedTextView, null);
    }

    public void setView(View v, Object object, boolean displaySelection) {
        if(object instanceof String) {
            setViewProfile(v, (String) object, displaySelection);
        }else if(object instanceof ProfileAdapterExtra) {
            setViewExtra(v, (ProfileAdapterExtra) object);
        }
    }

    private ExtendedTextView textView(View view) {
        if (view instanceof ExtendedTextView) return (ExtendedTextView) view;
        return view.findViewById(R.id.profile_item_label);
    }

    public void setOnProfileDeleteListener(OnProfileDeleteListener listener) {
        mDeleteListener = listener;
    }

    public void setOnProfileClickListener(OnProfileClickListener listener) {
        mClickListener = listener;
    }

    public interface OnProfileClickListener {
        void onClick(int position, Object item);
    }

    public interface OnProfileDeleteListener {
        void onDelete(String profileKey);
    }

    /** Reload profiles from the file */
    public void reloadProfiles(){
        ensureProfilesLoaded();
        mProfiles = new HashMap<>(LauncherProfiles.mainProfileJson.profiles);
        mProfileList = new ArrayList<>(Arrays.asList(mProfiles.keySet().toArray(new String[0])));
        super.notifyDataSetChanged();
    }

    private static void ensureProfilesLoaded() {
        try {
            LauncherProfiles.load();
        } catch (RuntimeException loadFailure) {
            android.util.Log.e("ProfileAdapter", "Unable to load launcher profiles; using a recoverable default", loadFailure);
            LauncherProfiles.createRecoverableDefault();
        }
    }

    /** Reload profiles from the file, with additional extra entries */
    public void reloadProfiles(ProfileAdapterExtra[] extraEntries) {
        if(extraEntries == null) mExtraEntires = new ProfileAdapterExtra[0];
        else mExtraEntires = extraEntries;
        this.reloadProfiles();
    }
}
