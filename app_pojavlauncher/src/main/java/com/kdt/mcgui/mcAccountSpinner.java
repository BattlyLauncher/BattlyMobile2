package com.kdt.mcgui;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.animation.ArgbEvaluator;
import android.graphics.Shader;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.util.AttributeSet;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.AppCompatSpinner;
import androidx.core.content.res.ResourcesCompat;


import net.kdt.pojavlaunch.PojavApplication;
import net.kdt.pojavlaunch.PojavProfile;
import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.authenticator.listener.DoneListener;
import net.kdt.pojavlaunch.authenticator.listener.ErrorListener;
import net.kdt.pojavlaunch.authenticator.listener.ProgressListener;
import net.kdt.pojavlaunch.authenticator.microsoft.PresentedException;
import net.kdt.pojavlaunch.authenticator.microsoft.MicrosoftBackgroundLogin;
import net.kdt.pojavlaunch.extra.ExtraConstants;
import net.kdt.pojavlaunch.extra.ExtraCore;
import net.kdt.pojavlaunch.extra.ExtraListener;
import net.kdt.pojavlaunch.value.MinecraftAccount;
import net.kdt.pojavlaunch.utils.BattlyPlusManager;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;

import fr.spse.extended_view.ExtendedTextView;

public class mcAccountSpinner extends AppCompatSpinner implements AdapterView.OnItemSelectedListener {
    public mcAccountSpinner(@NonNull Context context) {
        this(context, null);
    }
    public mcAccountSpinner(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private final List<String> mAccountList = new ArrayList<>(2);
    private MinecraftAccount mSelectecAccount = null;

    /* Display the head of the current profile, here just to allow bitmap recycling */
    private BitmapDrawable mHeadDrawable;

    /* Current animator to for the login bar, is swapped when changing step */
    private ObjectAnimator mLoginBarAnimator;
    private float mLoginBarWidth = -1;

    /* Paint used to display the bottom bar, to show the login progress. */
    private final Paint mLoginBarPaint = new Paint();

    /* When a login is performed in the background, we need to know where we are */
    private final static int MAX_LOGIN_STEP = 5;
    private int mLoginStep = 0;

    /* Login listeners */
    private final ProgressListener mProgressListener = step -> {
        // Animate the login bar, cosmetic purposes only
        mLoginStep = step;
        if(mLoginBarAnimator != null){
            mLoginBarAnimator.cancel();
            mLoginBarAnimator.setFloatValues( mLoginBarWidth, (getWidth()/MAX_LOGIN_STEP * mLoginStep));
        }else{
            mLoginBarAnimator = ObjectAnimator.ofFloat(this, "LoginBarWidth", mLoginBarWidth, (getWidth()/MAX_LOGIN_STEP * mLoginStep));
        }
        mLoginBarAnimator.start();
    };

    private final DoneListener mDoneListener = account -> {
        Log.i("McAccountSpinner", "Account login completed for " + account.username);
        Toast.makeText(getContext(), R.string.main_login_done, Toast.LENGTH_SHORT).show();

        // Check if the account being added is not one that is already existing
        // Like login twice on the same mc account...
        for(String mcAccountName : mAccountList){
            if(mcAccountName.equals(account.username)) return;
        }

        mSelectecAccount = account;
        invalidate();
        mAccountList.add(account.username);
        reloadAccounts(false, mAccountList.size() -1);
    };

    private final ErrorListener mErrorListener = errorMessage -> {
        mLoginBarPaint.setColor(Color.RED);
        Context context = getContext();
        if(errorMessage instanceof PresentedException) {
            PresentedException exception = (PresentedException) errorMessage;
            Throwable cause = exception.getCause();
            if(cause == null) {
                Tools.dialog(context, context.getString(R.string.global_error), exception.toString(context));
            }else {
                Tools.showError(context, exception.toString(context), exception.getCause());
            }
        }else {
            Tools.showError(getContext(), errorMessage);
        }
        invalidate();
    };

    /* Triggered when we need to do microsoft login */
    @Keep
    private final ExtraListener<Uri> mMicrosoftLoginListener = (key, value) -> {
        mLoginBarPaint.setColor(getResources().getColor(R.color.minebutton_color));
        new MicrosoftBackgroundLogin(false, value.getQueryParameter("code")).performLogin(
                mProgressListener, mDoneListener, mErrorListener);
        return false;
    };

    /* Triggered when we need to perform mojang login */
    @Keep
    private final ExtraListener<String[]> mMojangLoginListener = (key, value) -> {
        Log.i("McAccountSpinner", "Processing account login for " + value[0]);
        MinecraftAccount account = MinecraftAccount.load(value[0]);
        if (account == null) account = new MinecraftAccount();
        account.username = value[0];
        account.isMicrosoft = false;
        account.msaRefreshToken = "0";
        account.xuid = null;
        if (!value[1].isEmpty()) {
            account.accessToken = value[1]; // Battly or Mojang token — marks account as non-local
        }
        if (value[1].isEmpty()) {
            account.accessToken = "0";
        }
        if (value.length > 2 && value[2] != null && !value[2].isEmpty()) {
            account.profileId = value[2];
        }
        MinecraftAccount finalAccount = account;
        PojavApplication.sExecutorService.execute(() -> {
            if (!finalAccount.isLocal()) {
                finalAccount.updateSkinFace();
            }
            try {
                finalAccount.save();
                Log.i("McAccountSpinner", "Account saved for " + finalAccount.username);
            } catch (IOException e) {
                Log.e("McAccountSpinner", "Failed to save the account : " + e);
            }
            Tools.runOnUiThread(() -> mDoneListener.onLoginDone(finalAccount));
        });
        return false;
    };

    public void completeMojangLogin(@NonNull String[] loginData) {
        Log.i("McAccountSpinner", "Received direct account login for " + loginData[0]);
        mMojangLoginListener.onValueSet(ExtraConstants.MOJANG_LOGIN_TODO, loginData);
    }

    public void completeMicrosoftLogin(@NonNull Uri callbackUri) {
        mMicrosoftLoginListener.onValueSet(ExtraConstants.MICROSOFT_LOGIN_TODO, callbackUri);
    }

    @SuppressLint("ClickableViewAccessibility")
    private void init(){
        mLoginBarPaint.setColor(getResources().getColor(R.color.minebutton_color));
        mLoginBarPaint.setStrokeWidth(getResources().getDimensionPixelOffset(R.dimen._2sdp));

        // Set behavior
        reloadAccounts(true, 0);
        setOnItemSelectedListener(this);

        ExtraCore.addExtraListener(ExtraConstants.MOJANG_LOGIN_TODO, mMojangLoginListener);
        ExtraCore.addExtraListener(ExtraConstants.MICROSOFT_LOGIN_TODO, mMicrosoftLoginListener);
    }

    @Override
    public final void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        if(position == 0){  // Add account button
            if(mAccountList.size() > 1){
                ExtraCore.setValue(ExtraConstants.SELECT_AUTH_METHOD, true);
                // Reset selection to current account so back navigation doesn't re-trigger this
                post(() -> {
                    int accountPos = mSelectecAccount != null
                            ? mAccountList.indexOf(mSelectecAccount.username)
                            : 1;
                    setSelection(accountPos > 0 ? accountPos : 1, false);
                });
            }
            return;
        }

        pickAccount(position);
        if(mSelectecAccount != null)
            performLogin(mSelectecAccount);
    }

    @Override
    public final void onNothingSelected(AdapterView<?> parent) {}


    @Override
    protected void onDraw(Canvas canvas) {
        if(mLoginBarWidth == -1) mLoginBarWidth = getWidth(); // Initial draw

        if(mLoginStep <= 0 || mLoginStep >= MAX_LOGIN_STEP) return;
        float bottom = getHeight() - mLoginBarPaint.getStrokeWidth()/2;
        canvas.drawLine(0, bottom, mLoginBarWidth, bottom, mLoginBarPaint);
    }

    public void removeCurrentAccount(){
        removeAccount(getSelectedItemPosition());
    }

    private void removeAccount(int position) {
        if(position == 0) return;
        File accountFile = new File(Tools.DIR_ACCOUNT_NEW, mAccountList.get(position)+".json");
        if(accountFile.exists()) accountFile.delete();
        mAccountList.remove(position);

        reloadAccounts(false, 0);
        if (mAccountList.size() <= 1) {
            ExtraCore.setValue(ExtraConstants.SELECT_AUTH_METHOD, true);
        }
    }

    @Keep
    public void setLoginBarWidth(float value){
        mLoginBarWidth = value;
        invalidate(); // Need to redraw each time this is changed
    }

    /** Allows checking whether we have an online account */
    public boolean isAccountOnline(){
        return mSelectecAccount != null && !mSelectecAccount.accessToken.equals("0");
    }

    public MinecraftAccount getSelectedAccount(){
        return mSelectecAccount;
    }

    public int getLoginState(){
        return mLoginStep;
    }

    public boolean isLoginDone(){
        return mLoginStep >= MAX_LOGIN_STEP;
    }

    @SuppressLint("ClickableViewAccessibility")
    private void setNoAccountBehavior(){
        // Set custom behavior when no account are present, to make it act as a button
        if(mAccountList.size() != 1){
            // Remove any touch listener
            setOnTouchListener(null);
            return;
        }

        // Make the spinner act like a button, since there is no item to really select
        setOnTouchListener((v, event) -> {
            if(event.getAction() != MotionEvent.ACTION_UP) return false;
            // The activity should intercept this and spawn another fragment
            ExtraCore.setValue(ExtraConstants.SELECT_AUTH_METHOD, true);
            return true;
        });
    }

    /**
     * Reload the spinner, from memory or from scratch. A default account can be selected
     * @param fromFiles Whether we use files as the source of truth
     * @param overridePosition Force the spinner to be at this position, if not 0
     */
    private void reloadAccounts(boolean fromFiles, int overridePosition){
        if(fromFiles){
            mAccountList.clear();

            mAccountList.add(getContext().getString(R.string.main_add_account));
            File accountFolder = new File(Tools.DIR_ACCOUNT_NEW);
            if(accountFolder.exists()){
                String[] accountFiles = accountFolder.list((dir, name) -> name.endsWith(".json"));
                if (accountFiles != null) {
                    for (String fileName : accountFiles) {
                        String accountName = fileName.substring(0, fileName.length() - 5);
                        MinecraftAccount account = MinecraftAccount.load(accountName);
                        if (account != null && account.isLegacyMicrosoftDemo()) {
                            File legacyAccount = new File(accountFolder, fileName);
                            if (!legacyAccount.delete()) {
                                Log.w("McAccountSpinner", "Could not remove legacy Microsoft demo profile");
                            }
                            continue;
                        }
                        mAccountList.add(accountName);
                    }
                }
            }
        }

        String[] accountArray = mAccountList.toArray(new String[0]);
        AccountAdapter accountAdapter = new AccountAdapter(getContext(), R.layout.item_minecraft_account, accountArray);
        accountAdapter.setDropDownViewResource(R.layout.item_minecraft_account);
        setAdapter(accountAdapter);

        // Pick what's available, might just be the the add account "button"
        pickAccount(overridePosition == 0 ? -1 : overridePosition);
        if(mSelectecAccount != null)
            performLogin(mSelectecAccount);

        // Remove or add the behavior if needed
        setNoAccountBehavior();

    }

    private void performLogin(MinecraftAccount minecraftAccount){
        // Logging in when there's no internet is useless. This should really be turned into a network callback though.
        if(!Tools.isOnline(getContext())){
            return;
        }
        if(minecraftAccount.isLocal()) return;

        mLoginBarPaint.setColor(getResources().getColor(R.color.minebutton_color));
        if(minecraftAccount.isMicrosoft){
            if(System.currentTimeMillis() > minecraftAccount.expiresAt){
                // Perform login only if needed
                new MicrosoftBackgroundLogin(true, minecraftAccount.msaRefreshToken)
                        .performLogin(mProgressListener, mDoneListener, mErrorListener);
            }
            return;
        }
        if (minecraftAccount.isBattly() && MinecraftAccount.getSkinFace(minecraftAccount.username) == null) {
            PojavApplication.sExecutorService.execute(() -> {
                minecraftAccount.updateSkinFace();
                Tools.runOnUiThread(this::setImageFromSelectedAccount);
            });
        }
    }

    /** Pick the selected account, the one in settings if 0 is passed */
    private void pickAccount(int position){
        MinecraftAccount selectedAccount;
        if(position != -1){
            PojavProfile.setCurrentProfile(getContext(), mAccountList.get(position));
            selectedAccount = PojavProfile.getCurrentProfileContent(getContext(), mAccountList.get(position));

            // WORKAROUND
            // Account file corrupted due to previous versions having improper encoding
            if (selectedAccount == null){
                Context ctx = Objects.requireNonNull(getContext());

                new AlertDialog.Builder(ctx, R.style.BattlyDialog)
                        .setCancelable(false)
                        .setTitle(R.string.account_corrupted)
                        .setMessage(R.string.login_again)
                        .setPositiveButton(R.string.delete_account_and_login, (dialog, which) -> {
                            removeCurrentAccount();
                            pickAccount(-1);
                            setSelection(0);
                        })
                        .show();


            }
            setSelection(position);
        }else {
            // Get the current profile, or the first available profile if the wanted one is unavailable
            selectedAccount = PojavProfile.getCurrentProfileContent(getContext(), null);
            int spinnerPosition = selectedAccount == null
                    ? mAccountList.size() <= 1 ? 0 : 1
                    : mAccountList.indexOf(selectedAccount.username);
            setSelection(spinnerPosition, false);
        }

        mSelectecAccount = selectedAccount;
        setImageFromSelectedAccount();
    }

    @Deprecated()
    /* Legacy behavior, update the head image manually for the selected account */
    private void setImageFromSelectedAccount(){
        BitmapDrawable oldBitmapDrawable = mHeadDrawable;

        if(mSelectecAccount != null){
            View layout = getSelectedView();
            if(layout != null){
                ExtendedTextView view = layout.findViewById(R.id.account_item);
                Bitmap bitmap = mSelectecAccount.getSkinFace();
                if(bitmap != null) {
                    mHeadDrawable = new BitmapDrawable(getResources(), bitmap);
                    view.setCompoundDrawables(mHeadDrawable, null, null, null);
                }else{
                    view.setCompoundDrawables(ResourcesCompat.getDrawable(getResources(), R.drawable.bg_battly_launcher_avatar, null), null, null, null);
                }
                view.postProcessDrawables();
            }
        }

        if(oldBitmapDrawable != null){
            oldBitmapDrawable.getBitmap().recycle();
        }
    }

    private class AccountAdapter extends ArrayAdapter<String> {

        private final HashMap<String, Drawable> mImageCache = new HashMap<>();
        public AccountAdapter(@NonNull Context context, int resource, @NonNull String[] objects) {
            super(context, resource, objects);
        }

        @Override
        public View getDropDownView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
            if(convertView == null){
                convertView = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_minecraft_account, parent, false);
            }

            ExtendedTextView textview = convertView.findViewById(R.id.account_item);
            ImageView deleteButton = convertView.findViewById(R.id.delete_account_button);

            // Handle the "Add account section"
            if(position == 0) {
                convertView.animate().cancel();
                convertView.setAlpha(1f);
                convertView.setBackgroundResource(R.drawable.bg_battly_launcher_account);
                clearPlusTextAnimation(convertView, textview);
                textview.setText(super.getItem(position));
                textview.setCompoundDrawables(ResourcesCompat.getDrawable(parent.getResources(), R.drawable.ic_add, null), null, null, null);
                deleteButton.setVisibility(View.GONE);
            }
            else {
                String username = super.getItem(position);
                Drawable accountHead = mImageCache.get(username);
                if (accountHead == null){
                    accountHead = new BitmapDrawable(parent.getResources(), MinecraftAccount.getSkinFace(username));
                    if (((BitmapDrawable) accountHead).getBitmap() == null) {
                        accountHead = ResourcesCompat.getDrawable(parent.getResources(), R.drawable.bg_battly_launcher_avatar, null);
                    }
                    mImageCache.put(username, accountHead);
                }
                accountHead = accountHead.getConstantState() != null
                        ? accountHead.getConstantState().newDrawable(parent.getResources()).mutate()
                        : accountHead.mutate();
                textview.setText(getStyledAccountText(username));
                textview.setCompoundDrawables(accountHead, null, null, null);
                applyAccountStyle(convertView, textview, username);

                deleteButton.setVisibility(View.VISIBLE);
                deleteButton.setOnClickListener(v -> {
                    showDeleteDialog(getContext(), position);
                });
            }
            return convertView;
        }

        private CharSequence getStyledAccountText(String username) {
            MinecraftAccount account = net.kdt.pojavlaunch.value.MinecraftAccount.load(username);
            String status;
            if (account == null || account.isLocal()) {
                String battlyUsername = getContext()
                        .getSharedPreferences("battly_account", android.content.Context.MODE_PRIVATE)
                        .getString("battly_username", "");
                if (!battlyUsername.isEmpty() && battlyUsername.equalsIgnoreCase(username)) {
                    status = BattlyPlusManager.isPlus(getContext())
                            ? getContext().getString(R.string.launcher_status_battly_plus)
                            : getContext().getString(R.string.launcher_status_battly);
                } else {
                    status = getContext().getString(R.string.launcher_status_local);
                }
            } else if (!account.isMicrosoft) {
                status = BattlyPlusManager.isPlus(getContext())
                        ? getContext().getString(R.string.launcher_status_battly_plus)
                        : getContext().getString(R.string.launcher_status_battly);
            } else {
                status = getContext().getString(R.string.launcher_status_microsoft);
            }
            SpannableStringBuilder textBuilder = new SpannableStringBuilder(username);
            textBuilder.append('\n').append(status);
            int statusStart = username.length() + 1;
            textBuilder.setSpan(new RelativeSizeSpan(0.72f), statusStart, textBuilder.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            textBuilder.setSpan(new ForegroundColorSpan(Color.parseColor("#C6D6E3")), statusStart, textBuilder.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            return textBuilder;
        }

        private void applyAccountStyle(View view, ExtendedTextView textview, String username) {
            MinecraftAccount account = MinecraftAccount.load(username);
            boolean isBattlyAccount = account != null && account.isBattly();
            view.setBackgroundResource(R.drawable.bg_battly_launcher_account);
            if (!isBattlyAccount || !BattlyPlusManager.isPlus(getContext())) {
                view.animate().cancel();
                clearPlusTextAnimation(view, textview);
                view.setAlpha(1f);
                return;
            }
            ValueAnimator oldAnimator = (ValueAnimator) view.getTag(R.id.tag_battly_plus_animator);
            if (oldAnimator != null) {
                oldAnimator.cancel();
            }
            ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
            animator.setDuration(1800);
            animator.setRepeatCount(ValueAnimator.INFINITE);
            animator.setRepeatMode(ValueAnimator.RESTART);
            animator.addUpdateListener(animation -> {
                float fraction = (float) animation.getAnimatedValue();
                int width = Math.max(textview.getWidth(),
                        Math.round(textview.getPaint().measureText(textview.getText().toString())));
                if (width <= 0) return;
                float offset = width * fraction;
                Shader shader = new LinearGradient(
                        -offset, 0, width - offset, 0,
                        new int[]{0xFF3E8ED0, 0xFFFF7FAC, 0xFF3E8ED0},
                        new float[]{0f, 0.5f, 1f},
                        Shader.TileMode.CLAMP);
                textview.getPaint().setShader(shader);
                textview.invalidate();
            });
            view.setTag(R.id.tag_battly_plus_animator, animator);
            textview.post(animator::start);
        }

        private void clearPlusTextAnimation(View view, ExtendedTextView textview) {
            ValueAnimator oldAnimator = (ValueAnimator) view.getTag(R.id.tag_battly_plus_animator);
            if (oldAnimator != null) {
                oldAnimator.cancel();
                view.setTag(R.id.tag_battly_plus_animator, null);
            }
            textview.getPaint().setShader(null);
            textview.invalidate();
        }



        @NonNull
        @Override
        public View getView(int position, View convertView, @NonNull ViewGroup parent) {
            View view = getDropDownView(position, convertView, parent);
            view.findViewById(R.id.delete_account_button).setVisibility(View.GONE);
            return view;
        }

        private void showDeleteDialog(Context context, int position) {
            new AlertDialog.Builder(context, R.style.BattlyDialog)
                    .setMessage(R.string.warning_remove_account)
                    .setPositiveButton(android.R.string.cancel, null)
                    .setNeutralButton(R.string.global_delete, (dialog, which) -> {
                        removeAccount(position);
                    })
                    .show();
        }
    }



}
