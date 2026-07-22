package com.kdt.mcgui;

import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static net.kdt.pojavlaunch.fragments.ProfileEditorFragment.DELETED_PROFILE;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Build;
import android.transition.Slide;
import android.transition.Transition;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.GridView;
import android.widget.PopupWindow;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.res.ResourcesCompat;
import androidx.fragment.app.FragmentActivity;

import net.kdt.pojavlaunch.LauncherActivity;
import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.analytics.Telemetry;
import net.kdt.pojavlaunch.extra.ExtraConstants;
import net.kdt.pojavlaunch.extra.ExtraCore;
import net.kdt.pojavlaunch.fragments.ProfileEditorFragment;
import net.kdt.pojavlaunch.fragments.ProfileTypeSelectFragment;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;
import net.kdt.pojavlaunch.profiles.ProfileAdapter;
import net.kdt.pojavlaunch.profiles.ProfileAdapterExtra;
import net.kdt.pojavlaunch.profiles.ProfileIconCache;
import net.kdt.pojavlaunch.value.launcherprofiles.LauncherProfiles;

import fr.spse.extended_view.ExtendedTextView;

/**
 * A class implementing custom spinner like behavior, notably:
 * dropdown popup view with a custom direction.
 */
public class mcVersionSpinner extends ExtendedTextView {
    private static final int VERSION_SPINNER_PROFILE_CREATE = 0;
    public mcVersionSpinner(@NonNull Context context) {
        super(context);
        init();
    }
    public mcVersionSpinner(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }
    public mcVersionSpinner(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    /* The class is in charge of displaying its own list with adapter content being known in advance */
    private GridView mListView = null;
    private PopupWindow mPopupWindow = null;
    private Object mPopupAnimation;
    private int mSelectedIndex;

    private ProfileAdapter mProfileAdapter;


    /** Set the selection AND saves it as a shared preference */
    public void setProfileSelection(int position){
        Object selectedItem = mProfileAdapter.getItem(position);
        if (!(selectedItem instanceof String)) return;
        setProfileSelection((String) selectedItem);
    }

    private void setProfileSelection(String profileKey) {
        int position = mProfileAdapter.resolveProfileIndex(profileKey);
        if (position < 0) return;
        setSelection(position);
        if (LauncherPreferences.DEFAULT_PREF != null) {
            LauncherPreferences.DEFAULT_PREF.edit()
                    .putString(LauncherPreferences.PREF_KEY_CURRENT_PROFILE, profileKey)
                    .apply();
        }
        Telemetry.logProfileSelected(profileKey);
        if (getContext() instanceof LauncherActivity) {
            ((LauncherActivity) getContext()).refreshHomeProfileUi();
        }
    }

    public void setSelection(int position){
        Object selectedItem = mProfileAdapter.getItem(position);
        if (selectedItem == null) return;
        if(mListView != null) mListView.setSelection(position);
        mProfileAdapter.setView(this, selectedItem, false);
        mSelectedIndex = position;
    }

    public void openProfileEditor(FragmentActivity fragmentActivity) {
        Object currentSelection = mProfileAdapter.getItem(mSelectedIndex);
        if(currentSelection instanceof ProfileAdapterExtra) {
            performExtraAction((ProfileAdapterExtra) currentSelection);
        }else{
            Tools.swapFragment(fragmentActivity, ProfileEditorFragment.class, ProfileEditorFragment.TAG, null);
        }
    }

    /** Reload profiles from the file, forcing the spinner to consider the new data */
    public void reloadProfiles(){
        mProfileAdapter.reloadProfiles();
        int selectedIndex = mProfileAdapter.resolveProfileIndex(getSavedProfileKey());
        if (selectedIndex < 0 && mProfileAdapter.getCount() > 0) {
            selectedIndex = 0;
        }
        setSelection(selectedIndex);
    }

    /** Initialize various behaviors */
    private void init(){
        mProfileAdapter = new ProfileAdapter(new ProfileAdapterExtra[]{
                new ProfileAdapterExtra(VERSION_SPINNER_PROFILE_CREATE,
                        R.string.create_profile,
                        ResourcesCompat.getDrawable(getResources(), R.drawable.ic_add, getContext().getTheme())),
        });
        // Setup various attributes
        setTextSize(TypedValue.COMPLEX_UNIT_PX, getResources().getDimensionPixelSize(R.dimen._12ssp));
        setGravity(Gravity.CENTER_VERTICAL);
        int startPadding = getContext().getResources().getDimensionPixelOffset(R.dimen._17sdp);
        int endPadding = getContext().getResources().getDimensionPixelOffset(R.dimen._5sdp);
        setPaddingRelative(startPadding, 0, endPadding, 0);
        setCompoundDrawablePadding(startPadding);
        mProfileAdapter.setOnProfileDeleteListener(this::confirmDeleteProfile);
        mProfileAdapter.setOnProfileClickListener(this::handleProfileItemClick);

        int profileIndex;
        String extra_value = (String) ExtraCore.consumeValue(ExtraConstants.REFRESH_VERSION_SPINNER);
        if(extra_value != null){
            profileIndex = extra_value.equals(DELETED_PROFILE) ? 0
                    : getProfileAdapter().resolveProfileIndex(extra_value);
        }else
            profileIndex = mProfileAdapter.resolveProfileIndex(getSavedProfileKey());

        if (profileIndex >= 0) {
            setProfileSelection(profileIndex);
        } else if (mProfileAdapter.getCount() > 0) {
            setSelection(0);
        }

        // Popup window behavior
        setOnClickListener(new OnClickListener() {
            final int offset = -getContext().getResources().getDimensionPixelOffset(R.dimen._4sdp);
            @Override
            public void onClick(View v) {
                if(mPopupWindow == null) getPopupWindow();

                if(mPopupWindow.isShowing()){
                    mPopupWindow.dismiss();
                    return;
                }
                mPopupWindow.showAsDropDown(mcVersionSpinner.this, 0, offset);
                // Post() is required for the layout inflation phase
                post(() -> mListView.setSelection(mSelectedIndex));
            }
        });
    }

    private void performExtraAction(ProfileAdapterExtra extra) {
        //Replace with switch-case if you want to add more extra actions
        if (extra.id == VERSION_SPINNER_PROFILE_CREATE) {
            Tools.swapFragment((FragmentActivity) getContext(), ProfileTypeSelectFragment.class,
                    ProfileTypeSelectFragment.TAG, null);
        }
    }


    /** Create the listView and popup window for the interface, and set up the click behavior */
    @SuppressLint("ClickableViewAccessibility")
    private void getPopupWindow(){
        mListView = (GridView) inflate(getContext(), R.layout.spinner_mc_version, null);
        mListView.setAdapter(mProfileAdapter);
        mPopupWindow = new PopupWindow(mListView, MATCH_PARENT,
                getContext().getResources().getDimensionPixelOffset(R.dimen._260sdp));
        mPopupWindow.setElevation(5);
        mPopupWindow.setClippingEnabled(false);

        // Block clicking outside of the popup window
        mPopupWindow.setOutsideTouchable(true);
        mPopupWindow.setFocusable(true);
        mPopupWindow.setTouchInterceptor((v, event) -> {
            if(event.getAction() == MotionEvent.ACTION_OUTSIDE){
                mPopupWindow.dismiss();
                return true;
            }
            return false;
        });


        // Custom animation, nice slide in
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M){
            mPopupAnimation = new Slide(Gravity.BOTTOM);
            mPopupWindow.setEnterTransition((Transition) mPopupAnimation);
            mPopupWindow.setExitTransition((Transition) mPopupAnimation);
        }
    }

    private String getSavedProfileKey() {
        return LauncherPreferences.DEFAULT_PREF == null ? ""
                : LauncherPreferences.DEFAULT_PREF.getString(
                        LauncherPreferences.PREF_KEY_CURRENT_PROFILE, "");
    }

    private void handleProfileItemClick(int position, Object item) {
        if(item instanceof String) {
            setProfileSelection((String) item);
            hidePopup(true);
        }else if(item instanceof ProfileAdapterExtra) {
            hidePopup(false);
            performExtraAction((ProfileAdapterExtra) item);
        }
    }

    private void confirmDeleteProfile(String profileKey) {
        Tools.showStyledDialog(Tools.createStyledDialogBuilder(getContext())
                .setTitle(R.string.global_delete)
                .setMessage(R.string.profile_delete_confirm)
                .setPositiveButton(R.string.global_yes, (dialog, which) -> {
                    if (LauncherProfiles.mainProfileJson.profiles.size() <= 1) return;
                    ProfileIconCache.dropIcon(profileKey);
                    LauncherProfiles.mainProfileJson.profiles.remove(profileKey);
                    LauncherProfiles.write();
                    ExtraCore.setValue(ExtraConstants.REFRESH_VERSION_SPINNER, DELETED_PROFILE);
                    mProfileAdapter.reloadProfiles();
                    String selectedProfile = getSavedProfileKey();
                    if (profileKey.equals(selectedProfile) && mProfileAdapter.getCount() > 0) {
                        setProfileSelection(0);
                    } else {
                        reloadProfiles();
                    }
                    hidePopup(false);
                })
                .setNegativeButton(R.string.global_no, null));
    }

    private void hidePopup(boolean animate) {
        if(mPopupWindow == null) return;
        if(!animate && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            mPopupWindow.setEnterTransition(null);
            mPopupWindow.setExitTransition(null);
            mPopupWindow.dismiss();
            mPopupWindow.setEnterTransition((Transition) mPopupAnimation);
            mPopupWindow.setExitTransition((Transition) mPopupAnimation);
        }else {
            mPopupWindow.dismiss();
        }
    }

    public ProfileAdapter getProfileAdapter() {
        return mProfileAdapter;
    }
}
