package net.kdt.pojavlaunch.customcontrols.handleview;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;

import static net.kdt.pojavlaunch.Tools.currentDisplayMetrics;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Color;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import com.kdt.SideDialogView;

import net.kdt.pojavlaunch.EfficientAndroidLWJGLKeycode;
import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.colorselector.ColorSelector;
import net.kdt.pojavlaunch.customcontrols.ControlData;
import net.kdt.pojavlaunch.customcontrols.ControlDisplayUtils;
import net.kdt.pojavlaunch.customcontrols.ControlDrawerData;
import net.kdt.pojavlaunch.customcontrols.ControlJoystickData;
import net.kdt.pojavlaunch.customcontrols.buttons.ControlDrawer;
import net.kdt.pojavlaunch.customcontrols.buttons.ControlInterface;
import net.kdt.pojavlaunch.utils.interfaces.SimpleItemSelectedListener;
import net.kdt.pojavlaunch.utils.interfaces.SimpleSeekBarListener;
import net.kdt.pojavlaunch.utils.interfaces.SimpleTextWatcher;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class EditControlSideDialog extends SideDialogView {

    private final Spinner[] mKeycodeSpinners = new Spinner[4];
    public boolean internalChanges = false; // True when we programmatically change stuff.
    private final View.OnLayoutChangeListener mLayoutChangedListener = new View.OnLayoutChangeListener() {
        @Override
        public void onLayoutChange(View v, int left, int top, int right, int bottom, int oldLeft, int oldTop, int oldRight, int oldBottom) {
            if (internalChanges) return;

            internalChanges = true;
            int width = (int) (safeParseFloat(mWidthEditText.getText().toString()));

            if (width >= 0 && Math.abs(right - width) > 1) {
                mWidthEditText.setText(String.valueOf(right - left));
            }
            int height = (int) (safeParseFloat(mHeightEditText.getText().toString()));
            if (height >= 0 && Math.abs(bottom - height) > 1) {
                mHeightEditText.setText(String.valueOf(bottom - top));
            }

            if (mCurrentlyEditedButton != null
                    && mCurrentlyEditedButton.getProperties().isDeviceImageWidget()) {
                updateDeviceImageVisualSizeFromView();
            }

            internalChanges = false;
        }
    };
    private EditText mNameEditText, mDisplayValueEditText, mWidthEditText, mHeightEditText;
    @SuppressLint("UseSwitchCompatOrMaterialCode")
    private Switch mToggleSwitch, mPassthroughSwitch, mSwipeableSwitch, mForwardLockSwitch, mAbsoluteTrackingSwitch;
    private Spinner mDisplaySpinner, mOrientationSpinner;
    private Button mIconPickerButton;
    private final TextView[] mKeycodeTextviews = new TextView[4];
    private SeekBar mStrokeWidthSeekbar, mCornerRadiusSeekbar, mAlphaSeekbar, mDeviceImageVisualSizeSeekbar;
    private TextView mStrokePercentTextView, mCornerRadiusPercentTextView, mAlphaPercentTextView,
            mDeviceImageVisualSizeValue;
    private TextView mSelectBackgroundColor, mSelectStrokeColor;
    private ArrayAdapter<String> mAdapter, mDisplayAdapter;
    private List<String> mSpecialArray;
    private CheckBox mDisplayInGameCheckbox, mDisplayInMenuCheckbox, mDeviceImagePixelSizeCheckbox;
    private ControlInterface mCurrentlyEditedButton;
    private LinearLayout mDeviceImageVisualSizeLayout;
    private float mDeviceImageAspectRatio = 16f / 9f;
    // Decorative textviews
    private TextView mDisplayTextView, mOrientationTextView, mMappingTextView, mNameTextView,
            mCornerRadiusTextView, mVisibilityTextView, mSizeTextview, mSizeXTextView;

    // Color selector related stuff
    private ColorSelector mColorSelector;
    private final ViewGroup mParent;

    public EditControlSideDialog(Context context, ViewGroup parent) {
        super(context, parent, R.layout.dialog_control_button_setting);
        mParent = parent;
    }

    @Override
    protected void onInflate() {
        bindLayout();
        buildColorSelector();
        loadAdapter();
        setupRealTimeListeners();
    }

    @Override
    protected void onDestroy() {
        mColorSelector.disappear(true);
    }

    private void buildColorSelector() {
        mColorSelector = new ColorSelector(mParent.getContext(), mParent, null);
    }

    /**
     * Slide the layout into the visible screen area
     */
    public void appearColor(boolean fromRight, int color) {
        mColorSelector.show(fromRight, color == -1 ? Color.WHITE : color);
    }

    /**
     * Slide out the layout
     */
    public void disappearColor() {
        mColorSelector.disappear(false);
    }

    /**
     * Slide out the first visible layer.
     *
     * @return True if the last layer is disappearing
     */
    public boolean disappearLayer() {
        if (mColorSelector.isDisplaying()) {
            disappearColor();
            return false;
        } else {
            disappear(false);
            return true;
        }
    }

    /**
     * Switch the panels position if needed
     */
    public void adaptPanelPosition() {
        if (mDisplaying) {
            boolean isAtRight = mCurrentlyEditedButton.getControlView().getX() + mCurrentlyEditedButton.getControlView().getWidth() / 2f < currentDisplayMetrics.widthPixels / 2f;
            appear(isAtRight);
            if (mColorSelector.isDisplaying()) {
                Tools.runOnUiThread(() -> appearColor(isAtRight, mCurrentlyEditedButton.getProperties().bgColor));
            }
        }
    }

    public static void setPercentageText(TextView textView, int progress) {
        textView.setText(textView.getContext().getString(R.string.percent_format, progress));
    }

    /* LOADING VALUES */

    /**
     * Load values for basic control data
     */
    public void loadValues(ControlData data) {
        setDefaultVisibilitySetting();
        mDeviceImageVisualSizeLayout.setVisibility(GONE);
        mOrientationTextView.setVisibility(GONE);
        mOrientationSpinner.setVisibility(GONE);
        mForwardLockSwitch.setVisibility(GONE);
        mAbsoluteTrackingSwitch.setVisibility(GONE);

        mNameEditText.setText(data.name);
        mDisplaySpinner.setSelection(ControlDisplayUtils.typeToIndex(ControlDisplayUtils.getType(data)));
        mDisplayValueEditText.setText(data.displayValue == null ? "" : data.displayValue);
        updateDisplayEditorVisibility();
        mWidthEditText.setText(String.valueOf(data.getWidth()));
        mHeightEditText.setText(String.valueOf(data.getHeight()));

        mAlphaSeekbar.setProgress((int) (data.opacity * 100));
        mStrokeWidthSeekbar.setProgress((int) data.strokeWidth * 10);
        mCornerRadiusSeekbar.setProgress((int) data.cornerRadius);

        setPercentageText(mAlphaPercentTextView, (int) (data.opacity * 100));
        setPercentageText(mStrokePercentTextView, (int) data.strokeWidth * 10);
        setPercentageText(mCornerRadiusPercentTextView, (int) data.cornerRadius);

        mToggleSwitch.setChecked(data.isToggle);
        mPassthroughSwitch.setChecked(data.passThruEnabled);
        mSwipeableSwitch.setChecked(data.isSwipeable);

        mDisplayInGameCheckbox.setChecked(data.displayInGame);
        mDisplayInMenuCheckbox.setChecked(data.displayInMenu);

        for (int i = 0; i < data.keycodes.length; i++) {
            if (data.keycodes[i] < 0) {
                mKeycodeSpinners[i].setSelection(data.keycodes[i] + mSpecialArray.size());
            } else {
                mKeycodeSpinners[i].setSelection(EfficientAndroidLWJGLKeycode.getIndexByValue(data.keycodes[i]) + mSpecialArray.size());
            }
        }
    }

    /**
     * Load values for extended control data
     */
    public void loadValues(ControlDrawerData data) {
        loadValues(data.properties);

        mOrientationSpinner.setSelection(
                ControlDrawerData.orientationToInt(data.orientation));

        mMappingTextView.setVisibility(GONE);
        for (int i = 0; i < mKeycodeSpinners.length; i++) {
            mKeycodeSpinners[i].setVisibility(GONE);
            mKeycodeTextviews[i].setVisibility(GONE);
        }

        mOrientationTextView.setVisibility(VISIBLE);
        mOrientationSpinner.setVisibility(VISIBLE);

        mSwipeableSwitch.setVisibility(View.GONE);
        mPassthroughSwitch.setVisibility(View.GONE);
        mToggleSwitch.setVisibility(View.GONE);
    }

    public void loadPerformanceValues(ControlData data) {
        loadValues(data);

        mMappingTextView.setVisibility(GONE);
        for (int i = 0; i < mKeycodeSpinners.length; i++) {
            mKeycodeSpinners[i].setVisibility(GONE);
            mKeycodeTextviews[i].setVisibility(GONE);
        }

        mNameTextView.setVisibility(GONE);
        mNameEditText.setVisibility(GONE);
        mDisplayTextView.setVisibility(GONE);
        mDisplaySpinner.setVisibility(GONE);
        mDisplayValueEditText.setVisibility(GONE);
        mIconPickerButton.setVisibility(GONE);
        mSwipeableSwitch.setVisibility(GONE);
        mPassthroughSwitch.setVisibility(GONE);
        mToggleSwitch.setVisibility(GONE);
    }

    public void loadDeviceImageValues(ControlData data) {
        loadPerformanceValues(data);
        mDeviceImageAspectRatio = data.getHeight() > 0f
                ? data.getWidth() / data.getHeight()
                : 16f / 9f;
        mDeviceImageVisualSizeLayout.setVisibility(VISIBLE);
        mDeviceImagePixelSizeCheckbox.setChecked(false);
        setDeviceImagePixelEditorVisible(false);
        updateDeviceImageVisualSizeFromView();
        mSelectBackgroundColor.setVisibility(GONE);
        mSelectStrokeColor.setVisibility(GONE);
        mStrokeWidthSeekbar.setVisibility(GONE);
        mStrokePercentTextView.setVisibility(GONE);
        mCornerRadiusTextView.setVisibility(GONE);
        mCornerRadiusSeekbar.setVisibility(GONE);
        mCornerRadiusPercentTextView.setVisibility(GONE);
        View strokeLabel = mDialogContent.findViewById(R.id.editStrokeWidth_textView);
        if (strokeLabel != null) strokeLabel.setVisibility(GONE);
    }

    /**
     * Load values for the joystick
     */
    public void loadJoystickValues(ControlJoystickData data) {
        loadValues(data);

        mMappingTextView.setVisibility(GONE);
        for (int i = 0; i < mKeycodeSpinners.length; i++) {
            mKeycodeSpinners[i].setVisibility(GONE);
            mKeycodeTextviews[i].setVisibility(GONE);
        }

        mNameTextView.setVisibility(GONE);
        mNameEditText.setVisibility(GONE);
        mDisplayTextView.setVisibility(GONE);
        mDisplaySpinner.setVisibility(GONE);
        mDisplayValueEditText.setVisibility(GONE);
        mIconPickerButton.setVisibility(GONE);

        mCornerRadiusTextView.setVisibility(GONE);
        mCornerRadiusSeekbar.setVisibility(GONE);
        mCornerRadiusPercentTextView.setVisibility(GONE);

        mSwipeableSwitch.setVisibility(View.GONE);
        mPassthroughSwitch.setVisibility(View.GONE);
        mToggleSwitch.setVisibility(View.GONE);

        mForwardLockSwitch.setVisibility(VISIBLE);
        mForwardLockSwitch.setChecked(data.forwardLock);

        mAbsoluteTrackingSwitch.setVisibility(VISIBLE);
        mAbsoluteTrackingSwitch.setChecked(data.absolute);
    }

    /**
     * Load values for sub buttons
     */
    public void loadSubButtonValues(ControlData data, ControlDrawerData.Orientation drawerOrientation) {
        loadValues(data);

        // Size linked to the parent drawer depending on the drawer settings
        if(drawerOrientation != ControlDrawerData.Orientation.FREE){
            mSizeTextview.setVisibility(GONE);
            mSizeXTextView.setVisibility(GONE);
            mWidthEditText.setVisibility(GONE);
            mHeightEditText.setVisibility(GONE);
        }

        // No conditional, already depends on the parent drawer visibility
        mVisibilityTextView.setVisibility(GONE);
        mDisplayInMenuCheckbox.setVisibility(GONE);
        mDisplayInGameCheckbox.setVisibility(GONE);
    }

    private void loadAdapter() {
        //Initialize adapter for keycodes
        mAdapter = new ArrayAdapter<>(mDialogContent.getContext(), R.layout.item_centered_textview);
        mSpecialArray = ControlData.buildSpecialButtonArray();

        mAdapter.addAll(mSpecialArray);
        mAdapter.addAll(EfficientAndroidLWJGLKeycode.generateKeyName());
        mAdapter.setDropDownViewResource(android.R.layout.simple_list_item_single_choice);

        for (Spinner spinner : mKeycodeSpinners) {
            spinner.setAdapter(mAdapter);
        }

        // Orientation spinner
        ArrayAdapter<ControlDrawerData.Orientation> adapter = new ArrayAdapter<>(mDialogContent.getContext(), android.R.layout.simple_spinner_item);
        adapter.addAll(ControlDrawerData.getOrientations());
        adapter.setDropDownViewResource(android.R.layout.simple_list_item_single_choice);

        mOrientationSpinner.setAdapter(adapter);

        mDisplayAdapter = new ArrayAdapter<>(mDialogContent.getContext(), android.R.layout.simple_spinner_item);
        mDisplayAdapter.add(mDialogContent.getContext().getString(R.string.customctrl_display_text));
        mDisplayAdapter.add(mDialogContent.getContext().getString(R.string.customctrl_display_emoji));
        mDisplayAdapter.add(mDialogContent.getContext().getString(R.string.customctrl_display_icon));
        mDisplayAdapter.setDropDownViewResource(android.R.layout.simple_list_item_single_choice);
        mDisplaySpinner.setAdapter(mDisplayAdapter);
    }

    private void setDefaultVisibilitySetting() {
        for (int i = 0; i < ((ViewGroup)mDialogContent).getChildCount(); ++i) {
            ((ViewGroup)mDialogContent).getChildAt(i).setVisibility(VISIBLE);
        }
        for(Spinner s : mKeycodeSpinners) {
            s.setVisibility(View.INVISIBLE);
        }
    }

    private void bindLayout() {
        mNameEditText = mDialogContent.findViewById(R.id.editName_editText);
        mDisplaySpinner = mDialogContent.findViewById(R.id.editDisplay_spinner);
        mDisplayValueEditText = mDialogContent.findViewById(R.id.editDisplayValue_editText);
        mIconPickerButton = mDialogContent.findViewById(R.id.editIconPicker_button);
        mWidthEditText = mDialogContent.findViewById(R.id.editSize_editTextX);
        mHeightEditText = mDialogContent.findViewById(R.id.editSize_editTextY);
        mToggleSwitch = mDialogContent.findViewById(R.id.checkboxToggle);
        mPassthroughSwitch = mDialogContent.findViewById(R.id.checkboxPassThrough);
        mSwipeableSwitch = mDialogContent.findViewById(R.id.checkboxSwipeable);
        mForwardLockSwitch = mDialogContent.findViewById(R.id.checkboxForwardLock);
        mAbsoluteTrackingSwitch = mDialogContent.findViewById(R.id.checkboxAbsoluteFingerTracking);
        mKeycodeSpinners[0] = mDialogContent.findViewById(R.id.editMapping_spinner_1);
        mKeycodeSpinners[1] = mDialogContent.findViewById(R.id.editMapping_spinner_2);
        mKeycodeSpinners[2] = mDialogContent.findViewById(R.id.editMapping_spinner_3);
        mKeycodeSpinners[3] = mDialogContent.findViewById(R.id.editMapping_spinner_4);
        mKeycodeTextviews[0] = mDialogContent.findViewById(R.id.mapping_1_textview);
        mKeycodeTextviews[1] = mDialogContent.findViewById(R.id.mapping_2_textview);
        mKeycodeTextviews[2] = mDialogContent.findViewById(R.id.mapping_3_textview);
        mKeycodeTextviews[3] = mDialogContent.findViewById(R.id.mapping_4_textview);
        mOrientationSpinner = mDialogContent.findViewById(R.id.editOrientation_spinner);
        mStrokeWidthSeekbar = mDialogContent.findViewById(R.id.editStrokeWidth_seekbar);
        mCornerRadiusSeekbar = mDialogContent.findViewById(R.id.editCornerRadius_seekbar);
        mAlphaSeekbar = mDialogContent.findViewById(R.id.editButtonOpacity_seekbar);
        mDeviceImageVisualSizeSeekbar = mDialogContent.findViewById(R.id.editDeviceImageVisualSize_seekbar);
        mDeviceImageVisualSizeValue = mDialogContent.findViewById(R.id.editDeviceImageVisualSize_value);
        mDeviceImagePixelSizeCheckbox = mDialogContent.findViewById(R.id.editDeviceImagePixelSize_checkbox);
        mDeviceImageVisualSizeLayout = mDialogContent.findViewById(R.id.editDeviceImageVisualSize_layout);
        mSelectBackgroundColor = mDialogContent.findViewById(R.id.editBackgroundColor_textView);
        mSelectStrokeColor = mDialogContent.findViewById(R.id.editStrokeColor_textView);
        mStrokePercentTextView = mDialogContent.findViewById(R.id.editStrokeWidth_textView_percent);
        mAlphaPercentTextView = mDialogContent.findViewById(R.id.editButtonOpacity_textView_percent);
        mCornerRadiusPercentTextView = mDialogContent.findViewById(R.id.editCornerRadius_textView_percent);
        mDisplayInGameCheckbox = mDialogContent.findViewById(R.id.visibility_game_checkbox);
        mDisplayInMenuCheckbox = mDialogContent.findViewById(R.id.visibility_menu_checkbox);

        //Decorative stuff
        mDisplayTextView = mDialogContent.findViewById(R.id.editDisplay_textView);
        mMappingTextView = mDialogContent.findViewById(R.id.editMapping_textView);
        mOrientationTextView = mDialogContent.findViewById(R.id.editOrientation_textView);
        mNameTextView = mDialogContent.findViewById(R.id.editName_textView);
        mCornerRadiusTextView = mDialogContent.findViewById(R.id.editCornerRadius_textView);
        mVisibilityTextView = mDialogContent.findViewById(R.id.visibility_textview);
        mSizeTextview = mDialogContent.findViewById(R.id.editSize_textView);
        mSizeXTextView = mDialogContent.findViewById(R.id.editSize_x_textView);
    }

    /**
     * A long function linking all the displayed data on the popup and,
     * the currently edited mCurrentlyEditedButton
     * @noinspection SuspiciousNameCombination
     */
    private void setupRealTimeListeners() {
        mNameEditText.addTextChangedListener((SimpleTextWatcher) s -> {
            if (internalChanges) return;

            mCurrentlyEditedButton.getProperties().name = s.toString();

            // Cheap and unoptimized, doesn't break the abstraction layer
            mCurrentlyEditedButton.setProperties(mCurrentlyEditedButton.getProperties(), false);
        });

        mDisplaySpinner.setOnItemSelectedListener((SimpleItemSelectedListener) (parent, view, position, id) -> {
            if (mCurrentlyEditedButton == null) return;
            mCurrentlyEditedButton.getProperties().displayType = ControlDisplayUtils.indexToType(position);
            updateDisplayEditorVisibility();
            refreshCurrentlyEditedButtonDisplay();
        });

        mDisplayValueEditText.addTextChangedListener((SimpleTextWatcher) s -> {
            if (internalChanges || mCurrentlyEditedButton == null) return;
            mCurrentlyEditedButton.getProperties().displayValue = s.toString();
            refreshCurrentlyEditedButtonDisplay();
        });

        mIconPickerButton.setOnClickListener(v -> showIconPicker());

        mWidthEditText.addTextChangedListener((SimpleTextWatcher) s -> {
            if (internalChanges) return;

            float width = safeParseFloat(s.toString());
            if (width >= 0) {
                mCurrentlyEditedButton.getProperties().setWidth(width);
                if (mCurrentlyEditedButton.getProperties() instanceof ControlJoystickData) {
                    // Joysticks are square
                     mCurrentlyEditedButton.getProperties().setHeight(width);
                }
                mCurrentlyEditedButton.updateProperties();
            }
        });

        mHeightEditText.addTextChangedListener((SimpleTextWatcher) s -> {
            if (internalChanges) return;

            float height = safeParseFloat(s.toString());
            if (height >= 0) {
                mCurrentlyEditedButton.getProperties().setHeight(height);
                if (mCurrentlyEditedButton.getProperties() instanceof ControlJoystickData) {
                    // Joysticks are square
                    mCurrentlyEditedButton.getProperties().setWidth(height);
                }
                mCurrentlyEditedButton.updateProperties();
            }
        });

        mDeviceImageVisualSizeSeekbar.setOnSeekBarChangeListener((SimpleSeekBarListener) (seekBar, progress, fromUser) -> {
            if (internalChanges || !fromUser || mCurrentlyEditedButton == null
                    || !mCurrentlyEditedButton.getProperties().isDeviceImageWidget()) return;

            float targetWidth = currentDisplayMetrics.widthPixels * ((progress + 10) / 100f);
            float targetHeight = targetWidth / Math.max(0.1f, mDeviceImageAspectRatio);
            float currentTop = Math.max(0f, mCurrentlyEditedButton.getControlView().getY());
            float bottomMargin = Tools.dpToPx(12);
            float maxHeight = Math.max(
                    Tools.dpToPx(48),
                    currentDisplayMetrics.heightPixels - currentTop - bottomMargin
            );
            if (targetHeight > maxHeight) {
                targetHeight = maxHeight;
                targetWidth = targetHeight * mDeviceImageAspectRatio;
            }

            ControlData data = mCurrentlyEditedButton.getProperties();
            data.setWidth(targetWidth);
            data.setHeight(targetHeight);
            mCurrentlyEditedButton.updateProperties();
            mCurrentlyEditedButton.regenerateDynamicCoordinates();
            updateDeviceImageVisualSizeFromView();
        });

        mDeviceImagePixelSizeCheckbox.setOnCheckedChangeListener((buttonView, isChecked) ->
                setDeviceImagePixelEditorVisible(isChecked));

        mSwipeableSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (internalChanges) return;
            mCurrentlyEditedButton.getProperties().isSwipeable = isChecked;
        });
        mToggleSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (internalChanges) return;
            mCurrentlyEditedButton.getProperties().isToggle = isChecked;
        });
        mPassthroughSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (internalChanges) return;
            mCurrentlyEditedButton.getProperties().passThruEnabled = isChecked;
        });
        mForwardLockSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (internalChanges) return;
            if(mCurrentlyEditedButton.getProperties() instanceof ControlJoystickData){
                ((ControlJoystickData) mCurrentlyEditedButton.getProperties()).forwardLock = isChecked;
            }
        });
        mAbsoluteTrackingSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (internalChanges) return;
            if(mCurrentlyEditedButton.getProperties() instanceof ControlJoystickData){
                ((ControlJoystickData) mCurrentlyEditedButton.getProperties()).absolute = isChecked;
            }
        });

        mAlphaSeekbar.setOnSeekBarChangeListener((SimpleSeekBarListener) (seekBar, progress, fromUser) -> {
            if (internalChanges) return;
            mCurrentlyEditedButton.getProperties().opacity = mAlphaSeekbar.getProgress() / 100f;
            mCurrentlyEditedButton.getControlView().setAlpha(mAlphaSeekbar.getProgress() / 100f);
            setPercentageText(mAlphaPercentTextView, progress);
        });

        mStrokeWidthSeekbar.setOnSeekBarChangeListener((SimpleSeekBarListener) (seekBar, progress, fromUser) -> {
            if (internalChanges) return;
            mCurrentlyEditedButton.getProperties().strokeWidth = mStrokeWidthSeekbar.getProgress() / 10F;
            mCurrentlyEditedButton.setBackground();
            setPercentageText(mStrokePercentTextView, progress);
        });

        mCornerRadiusSeekbar.setOnSeekBarChangeListener((SimpleSeekBarListener) (seekBar, progress, fromUser) -> {
            if (internalChanges) return;
            mCurrentlyEditedButton.getProperties().cornerRadius = mCornerRadiusSeekbar.getProgress();
            mCurrentlyEditedButton.setBackground();
            setPercentageText(mCornerRadiusPercentTextView, progress);
        });


        for (int i = 0; i < mKeycodeSpinners.length; ++i) {
            int finalI = i;
            mKeycodeTextviews[i].setOnClickListener(v -> mKeycodeSpinners[finalI].performClick());

            mKeycodeSpinners[i].setOnItemSelectedListener((SimpleItemSelectedListener) (parent, view, position, id) -> {
                // Side note, spinner listeners are fired later than all the other ones.
                // Meaning the internalChanges bool is useless here.
                if (position < mSpecialArray.size()) {
                    mCurrentlyEditedButton.getProperties().keycodes[finalI] = mKeycodeSpinners[finalI].getSelectedItemPosition() - mSpecialArray.size();
                } else {
                    mCurrentlyEditedButton.getProperties().keycodes[finalI] = EfficientAndroidLWJGLKeycode.getValueByIndex(mKeycodeSpinners[finalI].getSelectedItemPosition() - mSpecialArray.size());
                }
                mKeycodeTextviews[finalI].setText((String) mKeycodeSpinners[finalI].getSelectedItem());
            });
        }


        mOrientationSpinner.setOnItemSelectedListener((SimpleItemSelectedListener) (parent, view, position, id) -> {
            // Side note, spinner listeners are fired later than all the other ones.
            // Meaning the internalChanges bool is useless here.

            if (mCurrentlyEditedButton instanceof ControlDrawer) {
                ((ControlDrawer) mCurrentlyEditedButton).drawerData.orientation = ControlDrawerData.intToOrientation(mOrientationSpinner.getSelectedItemPosition());
                ((ControlDrawer) mCurrentlyEditedButton).syncButtons();
            }
        });

        mDisplayInGameCheckbox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (internalChanges) return;
            mCurrentlyEditedButton.getProperties().displayInGame = isChecked;
        });

        mDisplayInMenuCheckbox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (internalChanges) return;
            mCurrentlyEditedButton.getProperties().displayInMenu = isChecked;
        });

        mSelectStrokeColor.setOnClickListener(v -> {
            mColorSelector.setAlphaEnabled(false);
            mColorSelector.setColorSelectionListener(color -> {
                mCurrentlyEditedButton.getProperties().strokeColor = color;
                mCurrentlyEditedButton.setBackground();
            });
            appearColor(isAtRight(), mCurrentlyEditedButton.getProperties().strokeColor);
        });

        mSelectBackgroundColor.setOnClickListener(v -> {
            mColorSelector.setAlphaEnabled(true);
            mColorSelector.setColorSelectionListener(color -> {
                mCurrentlyEditedButton.getProperties().bgColor = color;
                mCurrentlyEditedButton.setBackground();
            });
            appearColor(isAtRight(), mCurrentlyEditedButton.getProperties().bgColor);
        });
    }

    private void updateDisplayEditorVisibility() {
        boolean isIcon = mDisplaySpinner.getSelectedItemPosition() == ControlDisplayUtils.typeToIndex(ControlDisplayUtils.TYPE_ICON);
        mIconPickerButton.setVisibility(VISIBLE);
        mDisplayValueEditText.setHint(isIcon ? R.string.customctrl_icon_value_hint : R.string.customctrl_display_value_hint);
    }

    private void refreshCurrentlyEditedButtonDisplay() {
        mCurrentlyEditedButton.setProperties(mCurrentlyEditedButton.getProperties(), false);
    }

    private void setDeviceImagePixelEditorVisible(boolean visible) {
        int visibility = visible ? VISIBLE : GONE;
        mWidthEditText.setVisibility(visibility);
        mHeightEditText.setVisibility(visibility);
        mSizeXTextView.setVisibility(visibility);
    }

    private void updateDeviceImageVisualSizeFromView() {
        if (mCurrentlyEditedButton == null || mDeviceImageVisualSizeSeekbar == null) return;
        float width = mCurrentlyEditedButton.getProperties().getWidth();
        int percent = Math.max(10, Math.min(90,
                Math.round(width * 100f / Math.max(1, currentDisplayMetrics.widthPixels))));
        mDeviceImageVisualSizeSeekbar.setProgress(percent - 10);
        setPercentageText(mDeviceImageVisualSizeValue, percent);
    }

    private void showIconPicker() {
        Context context = mDialogContent.getContext();
        try {
            List<ControlDisplayUtils.IconItem> allIcons = ControlDisplayUtils.listIconItems(context);
            ArrayList<ControlDisplayUtils.IconItem> visibleIcons = new ArrayList<>(allIcons);

            EditText searchEditText = new EditText(context);
            searchEditText.setSingleLine(true);
            searchEditText.setHint(R.string.customctrl_icon_search_hint);
            searchEditText.setTextColor(context.getResources().getColor(android.R.color.white));
            searchEditText.setHintTextColor(0x99FFFFFF);
            searchEditText.setPadding(
                    (int) Tools.dpToPx(16),
                    (int) Tools.dpToPx(8),
                    (int) Tools.dpToPx(16),
                    (int) Tools.dpToPx(8)
            );

            ListView listView = new ListView(context);
            listView.setDivider(null);
            listView.setCacheColorHint(Color.TRANSPARENT);

            ArrayAdapter<ControlDisplayUtils.IconItem> iconAdapter = new ArrayAdapter<ControlDisplayUtils.IconItem>(context, android.R.layout.simple_list_item_1, visibleIcons) {
                @Override
                public View getView(int position, View convertView, ViewGroup parent) {
                    ImageView iconView;
                    TextView nameTextView;
                    TextView idTextView;

                    LinearLayout row = new LinearLayout(context);
                    row.setOrientation(LinearLayout.HORIZONTAL);
                    row.setGravity(android.view.Gravity.CENTER_VERTICAL);
                    row.setPadding(
                            (int) Tools.dpToPx(16),
                            (int) Tools.dpToPx(8),
                            (int) Tools.dpToPx(16),
                            (int) Tools.dpToPx(8)
                    );

                    int iconSize = (int) Tools.dpToPx(34);
                    iconView = new ImageView(context);
                    iconView.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
                    row.addView(iconView, new LinearLayout.LayoutParams(iconSize, iconSize));

                    LinearLayout textContainer = new LinearLayout(context);
                    textContainer.setOrientation(LinearLayout.VERTICAL);
                    LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(
                            0,
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            1f
                    );
                    textParams.setMarginStart((int) Tools.dpToPx(12));
                    row.addView(textContainer, textParams);

                    nameTextView = new TextView(context);
                    nameTextView.setTextColor(context.getResources().getColor(android.R.color.white));
                    nameTextView.setTextSize(15);
                    nameTextView.setSingleLine(true);
                    textContainer.addView(nameTextView);

                    idTextView = new TextView(context);
                    idTextView.setTextColor(0x99FFFFFF);
                    idTextView.setTextSize(12);
                    idTextView.setSingleLine(true);
                    textContainer.addView(idTextView);

                    ControlDisplayUtils.IconItem iconItem = getItem(position);
                    if (iconItem != null) {
                        iconView.setImageDrawable(ControlDisplayUtils.loadIcon(context, iconItem.iconName));
                        iconView.setContentDescription(iconItem.getDisplayName(context));
                        nameTextView.setText(iconItem.getDisplayName(context));
                        idTextView.setText(iconItem.fullId);
                    }
                    return row;
                }
            };
            listView.setAdapter(iconAdapter);

            searchEditText.addTextChangedListener((SimpleTextWatcher) text -> {
                visibleIcons.clear();
                visibleIcons.addAll(ControlDisplayUtils.filterIconItems(context, allIcons, text.toString()));
                iconAdapter.notifyDataSetChanged();
            });

            LinearLayout container = new LinearLayout(context);
            container.setOrientation(LinearLayout.VERTICAL);
            int margin = (int) Tools.dpToPx(12);
            container.setPadding(margin, 0, margin, 0);
            container.addView(searchEditText, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            ));
            container.addView(listView, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    (int) Tools.dpToPx(360)
            ));

            AlertDialog dialog = new AlertDialog.Builder(context, R.style.BattlyDialog)
                    .setTitle(R.string.customctrl_icon_picker_title)
                    .setView(container)
                    .setNegativeButton(android.R.string.cancel, null)
                    .create();
            listView.setOnItemClickListener((parent, view, position, id) -> {
                ControlDisplayUtils.IconItem iconItem = visibleIcons.get(position);
                internalChanges = true;
                mDisplaySpinner.setSelection(ControlDisplayUtils.typeToIndex(ControlDisplayUtils.TYPE_ICON));
                mDisplayValueEditText.setText(iconItem.iconName);
                internalChanges = false;

                mCurrentlyEditedButton.getProperties().displayType = ControlDisplayUtils.TYPE_ICON;
                mCurrentlyEditedButton.getProperties().displayValue = iconItem.iconName;
                updateDisplayEditorVisibility();
                refreshCurrentlyEditedButtonDisplay();
                dialog.dismiss();
            });
            dialog.setOnShowListener(dialogInterface -> Tools.styleDialog(dialog));
            dialog.show();
        } catch (IOException e) {
            Tools.showError(context, e);
        }
    }

    private float safeParseFloat(String string) {
        float out = -1; // -1
        try {
            out = Float.parseFloat(string);
        } catch (NumberFormatException e) {
            Log.e("EditControlPopup", e.toString());
        }
        return out;
    }

    public void setCurrentlyEditedButton(ControlInterface button) {
        if (mCurrentlyEditedButton != null)
            mCurrentlyEditedButton.getControlView().removeOnLayoutChangeListener(mLayoutChangedListener);
        mCurrentlyEditedButton = button;
        mCurrentlyEditedButton.getControlView().addOnLayoutChangeListener(mLayoutChangedListener);
    }

}
