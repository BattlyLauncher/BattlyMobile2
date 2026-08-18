package net.kdt.pojavlaunch.customcontrols.gamepad;

import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.Spinner;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.SwitchCompat;
import androidx.recyclerview.widget.RecyclerView;

import net.kdt.pojavlaunch.EfficientAndroidLWJGLKeycode;
import net.kdt.pojavlaunch.GrabListener;
import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;

import android.widget.TextView;

public class GamepadMapperAdapter extends RecyclerView.Adapter<GamepadMapperAdapter.ViewHolder> implements GamepadDataProvider {
    private static final int BUTTON_COUNT = 20;

    private GamepadMap mSimulatedGamepadMap;
    private RebinderButton[] mRebinderButtons;
    private GamepadEmulatedButton[] mRealButtons;
    private final ArrayAdapter<String> mKeyAdapter;
    private final int mSpecialKeycodeCount;
    private GrabListener mGamepadGrabListener;
    private boolean mGrabState = false;
    private boolean mOldState = false;
    private int mExpandedPosition = -1;
    private ControllerTypeResolver.Style mControllerStyle = ControllerTypeResolver.Style.GENERIC;

    public GamepadMapperAdapter(Context context) {
        GamepadMapStore.load();
        mKeyAdapter = new ArrayAdapter<>(context, R.layout.item_centered_textview_large);
        String[] specialKeycodeNames = {
                context.getString(R.string.controller_action_unassigned),
                context.getString(R.string.controller_action_mouse_right),
                context.getString(R.string.controller_action_mouse_middle),
                context.getString(R.string.controller_action_mouse_left),
                context.getString(R.string.controller_action_scroll_up),
                context.getString(R.string.controller_action_scroll_down)
        };
        mSpecialKeycodeCount = specialKeycodeNames.length;
        mKeyAdapter.addAll(specialKeycodeNames);
        mKeyAdapter.addAll(EfficientAndroidLWJGLKeycode.generateKeyName());
        createRebinderMap();
        updateRealButtons();
    }

    public void setControllerStyle(ControllerTypeResolver.Style style) {
        ControllerTypeResolver.Style normalized = style == null || style == ControllerTypeResolver.Style.AUTO
                ? ControllerTypeResolver.Style.GENERIC : style;
        if (mControllerStyle == normalized) return;
        mControllerStyle = normalized;
        notifyItemRangeChanged(0, mRebinderButtons.length);
    }

    public void refreshMappings() {
        updateRealButtons();
        notifyItemRangeChanged(0, mRebinderButtons.length);
    }

    public static String[] getControlLabels(Context context, ControllerTypeResolver.Style style) {
        boolean playStation = style == ControllerTypeResolver.Style.PLAYSTATION;
        boolean switchStyle = style == ControllerTypeResolver.Style.SWITCH;
        String[] face = playStation
                ? new String[]{context.getString(R.string.controller_button_cross), context.getString(R.string.controller_button_circle),
                context.getString(R.string.controller_button_square), context.getString(R.string.controller_button_triangle)}
                : switchStyle ? new String[]{"B", "A", "Y", "X"} : new String[]{"A", "B", "X", "Y"};
        return new String[]{
                face[0], face[1], face[2], face[3],
                playStation ? context.getString(R.string.controller_button_options) : switchStyle ? "+" : "Menu",
                playStation ? context.getString(R.string.controller_button_create) : switchStyle ? "-" : "View",
                playStation ? "R2" : switchStyle ? "ZR" : "RT",
                playStation ? "L2" : switchStyle ? "ZL" : "LT",
                playStation ? "R1" : switchStyle ? "R" : "RB",
                playStation ? "L1" : switchStyle ? "L" : "LB",
                context.getString(R.string.controller_direction_forward), context.getString(R.string.controller_direction_right),
                context.getString(R.string.controller_direction_left), context.getString(R.string.controller_direction_backward),
                context.getString(R.string.controller_stick_press_r), context.getString(R.string.controller_stick_press_l),
                context.getString(R.string.controller_dpad_up), context.getString(R.string.controller_dpad_down),
                context.getString(R.string.controller_dpad_right), context.getString(R.string.controller_dpad_left)
        };
    }

    private void createRebinderMap() {
        mRebinderButtons = new RebinderButton[BUTTON_COUNT];
        mRealButtons = new GamepadEmulatedButton[BUTTON_COUNT];
        mSimulatedGamepadMap = new GamepadMap();
        int index = 0;
        mSimulatedGamepadMap.BUTTON_A = mRebinderButtons[index++] = new RebinderButton(R.drawable.button_a, R.string.controller_button_a);
        mSimulatedGamepadMap.BUTTON_B = mRebinderButtons[index++] = new RebinderButton(R.drawable.button_b, R.string.controller_button_b);
        mSimulatedGamepadMap.BUTTON_X = mRebinderButtons[index++] = new RebinderButton(R.drawable.button_x, R.string.controller_button_x);
        mSimulatedGamepadMap.BUTTON_Y = mRebinderButtons[index++] = new RebinderButton(R.drawable.button_y, R.string.controller_button_y);
        mSimulatedGamepadMap.BUTTON_START = mRebinderButtons[index++] = new RebinderButton(R.drawable.button_start, R.string.controller_button_start);
        mSimulatedGamepadMap.BUTTON_SELECT = mRebinderButtons[index++] = new RebinderButton(R.drawable.button_select, R.string.controller_button_select);
        mSimulatedGamepadMap.TRIGGER_RIGHT = mRebinderButtons[index++] = new RebinderButton(R.drawable.trigger_right, R.string.controller_button_trigger_right);
        mSimulatedGamepadMap.TRIGGER_LEFT = mRebinderButtons[index++] = new RebinderButton(R.drawable.trigger_left, R.string.controller_button_trigger_left);
        mSimulatedGamepadMap.SHOULDER_RIGHT = mRebinderButtons[index++] = new RebinderButton(R.drawable.shoulder_right, R.string.controller_button_shoulder_right);
        mSimulatedGamepadMap.SHOULDER_LEFT = mRebinderButtons[index++] = new RebinderButton(R.drawable.shoulder_left, R.string.controller_button_shoulder_left);
        mSimulatedGamepadMap.DIRECTION_FORWARD = mRebinderButtons[index++] = new RebinderButton(R.drawable.stick_right, R.string.controller_direction_forward);
        mSimulatedGamepadMap.DIRECTION_RIGHT = mRebinderButtons[index++] = new RebinderButton(R.drawable.stick_right, R.string.controller_direction_right);
        mSimulatedGamepadMap.DIRECTION_LEFT = mRebinderButtons[index++] = new RebinderButton(R.drawable.stick_right, R.string.controller_direction_left);
        mSimulatedGamepadMap.DIRECTION_BACKWARD = mRebinderButtons[index++] = new RebinderButton(R.drawable.stick_right, R.string.controller_direction_backward);
        mSimulatedGamepadMap.THUMBSTICK_RIGHT = mRebinderButtons[index++] = new RebinderButton(R.drawable.stick_right_click, R.string.controller_stick_press_r);
        mSimulatedGamepadMap.THUMBSTICK_LEFT = mRebinderButtons[index++] = new RebinderButton(R.drawable.stick_left_click, R.string.controller_stick_press_l);
        mSimulatedGamepadMap.DPAD_UP = mRebinderButtons[index++] = new RebinderButton(R.drawable.dpad_up, R.string.controller_dpad_up);
        mSimulatedGamepadMap.DPAD_DOWN = mRebinderButtons[index++] = new RebinderButton(R.drawable.dpad_down, R.string.controller_dpad_down);
        mSimulatedGamepadMap.DPAD_RIGHT = mRebinderButtons[index++] = new RebinderButton(R.drawable.dpad_right, R.string.controller_dpad_right);
        mSimulatedGamepadMap.DPAD_LEFT = mRebinderButtons[index] = new RebinderButton(R.drawable.dpad_left, R.string.controller_dpad_left);
    }

    private void updateRealButtons() {
        GamepadMap currentRealMap = mGrabState ? GamepadMapStore.getGameMap() : GamepadMapStore.getMenuMap();
        int index = 0;
        mRealButtons[index++] = currentRealMap.BUTTON_A;
        mRealButtons[index++] = currentRealMap.BUTTON_B;
        mRealButtons[index++] = currentRealMap.BUTTON_X;
        mRealButtons[index++] = currentRealMap.BUTTON_Y;
        mRealButtons[index++] = currentRealMap.BUTTON_START;
        mRealButtons[index++] = currentRealMap.BUTTON_SELECT;
        mRealButtons[index++] = currentRealMap.TRIGGER_RIGHT;
        mRealButtons[index++] = currentRealMap.TRIGGER_LEFT;
        mRealButtons[index++] = currentRealMap.SHOULDER_RIGHT;
        mRealButtons[index++] = currentRealMap.SHOULDER_LEFT;
        mRealButtons[index++] = currentRealMap.DIRECTION_FORWARD;
        mRealButtons[index++] = currentRealMap.DIRECTION_RIGHT;
        mRealButtons[index++] = currentRealMap.DIRECTION_LEFT;
        mRealButtons[index++] = currentRealMap.DIRECTION_BACKWARD;
        mRealButtons[index++] = currentRealMap.THUMBSTICK_RIGHT;
        mRealButtons[index++] = currentRealMap.THUMBSTICK_LEFT;
        mRealButtons[index++] = currentRealMap.DPAD_UP;
        mRealButtons[index++] = currentRealMap.DPAD_DOWN;
        mRealButtons[index++] = currentRealMap.DPAD_RIGHT;
        mRealButtons[index] = currentRealMap.DPAD_LEFT;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater layoutInflater = LayoutInflater.from(parent.getContext());
        View view = layoutInflater.inflate(R.layout.item_controller_mapping, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.attach(position);
    }

    @Override
    public void onViewRecycled(@NonNull ViewHolder holder) {
        super.onViewRecycled(holder);
        holder.detach();
    }

    @Override
    public int getItemCount() {
        return mRebinderButtons.length;
    }

    /** Opens the selected mapping and keeps it visible when chosen from the controller diagram. */
    public void focusMapping(int position, RecyclerView recyclerView) {
        if (position < 0 || position >= getItemCount() || recyclerView == null) return;
        int previous = mExpandedPosition;
        mExpandedPosition = position;
        if (previous >= 0 && previous != position) notifyItemChanged(previous);
        notifyItemChanged(position);
        recyclerView.smoothScrollToPosition(position);
    }

    private void updateStickIcons() {
        // Which stick is used for keyboard emulation depends on grab state, so we need
        // to update the mapper UI icons accordingly
        int stickIcon = mGrabState ? R.drawable.stick_left : R.drawable.stick_right;
        ((RebinderButton)mSimulatedGamepadMap.DIRECTION_FORWARD).iconResourceId = stickIcon;
        ((RebinderButton)mSimulatedGamepadMap.DIRECTION_BACKWARD).iconResourceId = stickIcon;
        ((RebinderButton)mSimulatedGamepadMap.DIRECTION_RIGHT).iconResourceId = stickIcon;
        ((RebinderButton)mSimulatedGamepadMap.DIRECTION_LEFT).iconResourceId = stickIcon;
    }

    private static class RebinderButton extends GamepadButton {
        public int iconResourceId;
        public final int localeResourceId;
        private GamepadMapperAdapter.ViewHolder mButtonHolder;

        public RebinderButton(int iconResourceId, int localeResourceId) {
            this.iconResourceId = iconResourceId;
            this.localeResourceId = localeResourceId;
        }

        public void changeViewHolder(GamepadMapperAdapter.ViewHolder viewHolder) {
            mButtonHolder = viewHolder;
            if(mButtonHolder != null) mButtonHolder.setPressed(mIsDown);
        }
        
        @Override
        protected void onDownStateChanged(boolean isDown) {
            if(mButtonHolder == null) return;
            mButtonHolder.setPressed(isDown);
        }
    }
    
    public class ViewHolder extends RecyclerView.ViewHolder implements AdapterView.OnItemSelectedListener, View.OnClickListener, CompoundButton.OnCheckedChangeListener {
        private static final int COLOR_ACTIVE_BUTTON = 0x2000FF00;
        private final Context mContext;
        private final ImageView mButtonIcon;
        private final ImageView mExpansionIndicator;
        private final Spinner[] mKeySpinners;
        private final View mExpandedView;
        private final SwitchCompat mToggleableSwitch;
        private final TextView mKeycodeLabel;
        private final TextView mButtonName;
        private int mAttachedPosition = -1;
        private GamepadEmulatedButton mAttachedButton;
        private short[] mKeycodes;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            mContext = itemView.getContext();
            mButtonIcon = itemView.findViewById(R.id.controller_mapper_button);
            mExpandedView = itemView.findViewById(R.id.controller_mapper_expanded_view);
            mExpansionIndicator = itemView.findViewById(R.id.controller_mapper_expand_button);
            mKeycodeLabel = itemView.findViewById(R.id.controller_mapper_keycode_label);
            mButtonName = itemView.findViewById(R.id.controller_mapper_button_name);
            mToggleableSwitch = itemView.findViewById(R.id.controller_mapper_toggleable_switch);
            mToggleableSwitch.setOnCheckedChangeListener(this);
            View defaultView = itemView.findViewById(R.id.controller_mapper_default_view);
            defaultView.setOnClickListener(this);
            mKeySpinners = new Spinner[4];
            mKeySpinners[0] = itemView.findViewById(R.id.controller_mapper_key_spinner1);
            mKeySpinners[1] = itemView.findViewById(R.id.controller_mapper_key_spinner2);
            mKeySpinners[2] = itemView.findViewById(R.id.controller_mapper_key_spinner3);
            mKeySpinners[3] = itemView.findViewById(R.id.controller_mapper_key_spinner4);
            for(Spinner spinner : mKeySpinners) {
                spinner.setAdapter(mKeyAdapter);
                spinner.setOnItemSelectedListener(this);
            }
        }
        private void attach(int index) {
            RebinderButton rebinderButton = mRebinderButtons[index];
            boolean expanded = index == mExpandedPosition;
            mExpandedView.setVisibility(expanded ? View.VISIBLE : View.GONE);
            mExpansionIndicator.setRotation(expanded ? 0 : 180);
            setButtonArtwork(index, rebinderButton);
            String buttonName = getButtonName(index, rebinderButton);
            mButtonIcon.setContentDescription(buttonName);
            mButtonName.setText(buttonName);
            rebinderButton.changeViewHolder(this);

            GamepadEmulatedButton realButton = mRealButtons[index];

            mAttachedButton = realButton;

            if(realButton instanceof GamepadButton) {
                mToggleableSwitch.setChecked(((GamepadButton)realButton).isToggleable);
                mToggleableSwitch.setVisibility(View.VISIBLE);
            }else {
                mToggleableSwitch.setVisibility(View.GONE);
            }

            mKeycodes = realButton.keycodes;

            int spinnerIndex;

            // Populate spinners with known keycodes until we run out of keycodes
            for(spinnerIndex = 0; spinnerIndex < mKeycodes.length; spinnerIndex++) {
                Spinner keySpinner = mKeySpinners[spinnerIndex];
                keySpinner.setEnabled(true);
                short keyCode = mKeycodes[spinnerIndex];
                int selected;
                if(keyCode < 0) selected = keyCode + mSpecialKeycodeCount;
                else selected = EfficientAndroidLWJGLKeycode.getIndexByValue(keyCode) + mSpecialKeycodeCount;
                keySpinner.setSelection(selected);
            }
            // In case if there is too much spinners, disable the rest of them
            for(;spinnerIndex < mKeySpinners.length; spinnerIndex++) {
                mKeySpinners[spinnerIndex].setEnabled(false);
            }
            updateKeycodeLabel();

            mAttachedPosition = index;
        }

        private void setButtonArtwork(int index, RebinderButton rebinderButton) {
            if (mControllerStyle == ControllerTypeResolver.Style.PLAYSTATION && index >= 0 && index <= 3) {
                PlayStationButtonDrawable.Symbol[] symbols = {
                        PlayStationButtonDrawable.Symbol.CROSS,
                        PlayStationButtonDrawable.Symbol.CIRCLE,
                        PlayStationButtonDrawable.Symbol.SQUARE,
                        PlayStationButtonDrawable.Symbol.TRIANGLE
                };
                mButtonIcon.setImageDrawable(new PlayStationButtonDrawable(symbols[index]));
            } else {
                mButtonIcon.setImageResource(rebinderButton.iconResourceId);
            }
        }

        private String getButtonName(int index, RebinderButton rebinderButton) {
            if (mControllerStyle != ControllerTypeResolver.Style.PLAYSTATION) {
                return mContext.getString(rebinderButton.localeResourceId);
            }
            switch (index) {
                case 0: return mContext.getString(R.string.controller_button_cross);
                case 1: return mContext.getString(R.string.controller_button_circle);
                case 2: return mContext.getString(R.string.controller_button_square);
                case 3: return mContext.getString(R.string.controller_button_triangle);
                case 4: return mContext.getString(R.string.controller_button_options);
                case 5: return mContext.getString(R.string.controller_button_create);
                default: return mContext.getString(rebinderButton.localeResourceId);
            }
        }
        private void detach() {
            if (mAttachedPosition >= 0) mRebinderButtons[mAttachedPosition].changeViewHolder(null);
            mAttachedPosition = -1;
            mAttachedButton = null;
        }
        private void setPressed(boolean pressed) {
            itemView.setBackgroundColor(pressed ? COLOR_ACTIVE_BUTTON : Color.TRANSPARENT);
        }

        private void updateKeycodeLabel() {
            StringBuilder labelBuilder = new StringBuilder();
            boolean first = true;
            int unspecifiedPosition = GamepadMap.UNSPECIFIED + mSpecialKeycodeCount;
            for (Spinner keySpinner : mKeySpinners) {
                if (keySpinner.getSelectedItemPosition() == unspecifiedPosition) continue;
                if (!first) labelBuilder.append(" + ");
                else first = false;
                labelBuilder.append(keySpinner.getSelectedItem().toString());
            }
            if(labelBuilder.length() == 0) labelBuilder.append(mKeyAdapter.getItem(unspecifiedPosition));
            mKeycodeLabel.setText(labelBuilder.toString());
        }

        @Override
        public void onItemSelected(AdapterView<?> adapterView, View view, int selectionIndex, long selectionId) {
            if(mAttachedPosition == -1) return;
            int editedKeycodeIndex = -1;
            for(int i = 0; i < mKeySpinners.length && i < mKeycodes.length; i++) {
                if(!adapterView.equals(mKeySpinners[i])) continue;
                editedKeycodeIndex = i;
                break;
            }
            if(editedKeycodeIndex == -1) return;
            int keycode_offset = selectionIndex - mSpecialKeycodeCount;
            if(selectionIndex < mSpecialKeycodeCount) mKeycodes[editedKeycodeIndex] = (short) (keycode_offset);
            else mKeycodes[editedKeycodeIndex] = EfficientAndroidLWJGLKeycode.getValueByIndex(keycode_offset);
            updateKeycodeLabel();
            try {
                GamepadMapStore.save();
            }catch (Exception e) {
                Tools.showError(adapterView.getContext(), e);
            }
        }

        @Override
        public void onNothingSelected(AdapterView<?> adapterView) {

        }

        @Override
        public void onClick(View view) {
            int previous = mExpandedPosition;
            mExpandedPosition = previous == mAttachedPosition ? -1 : mAttachedPosition;
            if (previous >= 0 && previous != mAttachedPosition) notifyItemChanged(previous);
            notifyItemChanged(mAttachedPosition);
        }

        @Override
        public void onCheckedChanged(CompoundButton compoundButton, boolean checked) {
            if(!(mAttachedButton instanceof GamepadButton)) return;
            ((GamepadButton)mAttachedButton).isToggleable = checked;
            try {
                GamepadMapStore.save();
            }catch (Exception e) {
                Tools.showError(compoundButton.getContext(), e);
            }
        }
    }

    @Override
    public GamepadMap getMenuMap() {
        return mSimulatedGamepadMap;
    }

    @Override
    public GamepadMap getGameMap() {
        return mSimulatedGamepadMap;
    }

    @Override
    public boolean isGrabbing() {
        return mGrabState;
    }

    @Override
    public void attachGrabListener(GrabListener grabListener) {
        mGamepadGrabListener = grabListener;
        grabListener.onGrabState(mGrabState);
    }

    @Override
    public void detachGrabListener(GrabListener grabListener) {
        mGamepadGrabListener = null;
    }

    public void setGrabState(boolean newState) {
        mGrabState = newState;
        if(mGamepadGrabListener != null) mGamepadGrabListener.onGrabState(newState);
        if(mGrabState == mOldState) return;
        updateRealButtons();
        updateStickIcons();
        notifyItemRangeChanged(0, mRebinderButtons.length);
        mOldState = mGrabState;
    }
}
