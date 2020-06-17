package delit.piwigoclient.ui.file;

import android.content.Context;
import android.os.Build;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import com.google.android.material.checkbox.MaterialCheckBox;

import java.util.HashSet;
import java.util.Set;
import java.util.SortedSet;

import delit.libs.ui.util.DisplayUtils;
import delit.libs.ui.util.ParcelUtils;
import delit.libs.ui.view.FlowLayout;
import delit.libs.util.CollectionUtils;
import delit.piwigoclient.R;

public class FilterControl extends FrameLayout {

    private Set<String> allPossiblyVisibleFileExts; //TODO this isn't needed in this component. Its functionality bleed from file select frag I think.
    private Set<String> currentlyVisibleFileExts;
    private Set<String> selectedVisibleFileExts;
    private FilterListener listener;
    private ViewGroup fileExtFilters;
    private CheckBox showFiltersToggle;
    private Button toggleAll;
    private boolean selectToggle;

    public FilterControl(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public FilterControl(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public FilterControl(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init(context);
    }

    private void init(Context context) {
        View content = inflate(context, R.layout.layout_filter_control, null);
        addView(content);
        showFiltersToggle = findViewById(R.id.content_filter_label);
        showFiltersToggle.setChecked(true);
        showFiltersToggle.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if(isChecked) {
                    fileExtFilters.setVisibility(View.VISIBLE);
                } else {
                    fileExtFilters.setVisibility(View.GONE);
                }
            }
        });
        fileExtFilters = findViewById(R.id.filters);
        toggleAll = findViewById(R.id.toggle_all_button);
        toggleAll.setOnClickListener(v -> onToggleAllSelection());
        setToggleSelectionButtonText();
        addDemoData();
    }

    private void onToggleAllSelection() {
        if (!selectToggle) {
            selectAllFilters();
            selectToggle = true;
        } else {
            selectNoFilters();
            selectToggle = false;
        }
        setToggleSelectionButtonText();
    }

    private void selectNoFilters() {
        selectNone();
    }

    private void selectAllFilters() {
        selectAll();
    }

    private void setToggleSelectionButtonText() {
        if (selectToggle) {
            toggleAll.setText(getContext().getString(R.string.button_none));
        } else {
            toggleAll.setText(getContext().getString(R.string.button_all));
        }
    }

    private void addDemoData() {
        if (!isInEditMode()) {
            return;
        }
        allPossiblyVisibleFileExts = new HashSet<>();
        allPossiblyVisibleFileExts.add("jpg");
        allPossiblyVisibleFileExts.add("bmp");
        allPossiblyVisibleFileExts.add("wav");
        allPossiblyVisibleFileExts.add("raw");
        allPossiblyVisibleFileExts.add("mp4");
        currentlyVisibleFileExts = new HashSet<>();
        currentlyVisibleFileExts.addAll(allPossiblyVisibleFileExts);
        selectedVisibleFileExts = new HashSet<>();
        selectedVisibleFileExts.add("mp4");
        selectedVisibleFileExts.add("jpg");
        buildFileExtFilterControls(false);
    }

    public void setListener(FilterListener listener) {
        this.listener = listener;
    }

    public void selectAll() {
        selectAll(true);
    }
    public void selectAll(boolean notifyListenersOfFilterStatusChange) {
        if(selectedVisibleFileExts == null) {
            selectedVisibleFileExts = new HashSet<>();
        }
        selectedVisibleFileExts.addAll(currentlyVisibleFileExts);
        if(allPossiblyVisibleFileExts != null) {
            selectedVisibleFileExts.addAll(allPossiblyVisibleFileExts);
        }
        buildFileExtFilterControls(notifyListenersOfFilterStatusChange);
    }
    public void selectNone() {
        selectNone(true);
    }
    public void selectNone(boolean notifyListenersOfFilterStatusChange) {
        selectedVisibleFileExts = new HashSet<>();
        buildFileExtFilterControls(notifyListenersOfFilterStatusChange);
    }

    public void buildFileExtFilterControls(boolean notifyOnFiltersChanged) {

        // initialise local cached set of selected items
        if (selectedVisibleFileExts == null && allPossiblyVisibleFileExts != null) {
            selectedVisibleFileExts = new HashSet<>(allPossiblyVisibleFileExts);
        }

        // clear all the existing filters
        fileExtFilters.removeAllViews();
        boolean allSelected = false;
        // for each file type visible, show a checkbox and set it according to out local model
        if (currentlyVisibleFileExts != null && selectedVisibleFileExts != null) {
            boolean filterHidden = false;
            boolean filterShown = false;
            allSelected = CollectionUtils.equals(currentlyVisibleFileExts, selectedVisibleFileExts);
            for (String fileExt : currentlyVisibleFileExts) {
                FlowLayout.LayoutParams layoutParams = new FlowLayout.LayoutParams(FlowLayout.LayoutParams.WRAP_CONTENT, FlowLayout.LayoutParams.WRAP_CONTENT);
                boolean checked = selectedVisibleFileExts.contains(fileExt);
                if (!checked) {
                    filterShown = true;
                    if(listener != null) {
                        listener.onFilterUnchecked(getContext(), fileExt);
                    }
                } else {
                    filterHidden = true;
                    if(listener != null) {
                        listener.onFilterChecked(getContext(), fileExt);
                    }
                }
                fileExtFilters.addView(createFileExtFilterControl(fileExt, checked), layoutParams);
            }
            if (notifyOnFiltersChanged && (filterShown || filterHidden)) {
                if(listener != null) {
                    listener.onFiltersChanged(getContext(), filterHidden, filterShown);
                }
            }
        }

        boolean filtersVisible = (currentlyVisibleFileExts != null && !currentlyVisibleFileExts.isEmpty());
        showFiltersToggle.setChecked(showFiltersToggle.isChecked() && filtersVisible);
        showFiltersToggle.setEnabled(filtersVisible);
        toggleAll.setEnabled(filtersVisible);
        selectToggle = allSelected;
        setToggleSelectionButtonText();
    }

    private View createFileExtFilterControl(String fileExt, boolean checked) {
        MaterialCheckBox fileExtControl = new MaterialCheckBox(getContext());
        int paddingPx = DisplayUtils.dpToPx(getContext(), 5);
        fileExtControl.setPadding(paddingPx, paddingPx, paddingPx, paddingPx);
        fileExtControl.setText(fileExt);
        fileExtControl.setEnabled(isEnabled());
        fileExtControl.setChecked(checked);
        fileExtControl.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if(isChecked) {
                selectedVisibleFileExts.add(fileExt);
                if(listener != null) {
                    listener.onFilterChecked(getContext(), fileExt);
                }
            } else {
                selectedVisibleFileExts.remove(fileExt);
                if(listener != null) {
                    listener.onFilterUnchecked(getContext(), fileExt);
                }
            }
            if(listener != null) {
                listener.onFiltersChanged(getContext(), !isChecked, isChecked);
            }
        });
        return fileExtControl;
    }

    @Nullable
    @Override
    protected Parcelable onSaveInstanceState() {
        final FilterControl.SavedState ss =
                new FilterControl.SavedState(super.onSaveInstanceState());
        ss.allPossiblyVisibleFileExts = allPossiblyVisibleFileExts;
        ss.selectedVisibleFileExts = selectedVisibleFileExts;
        return ss;
    }

    @Override
    public void onRestoreInstanceState(Parcelable state) {
        FilterControl.SavedState ss = (FilterControl.SavedState) state;
        allPossiblyVisibleFileExts = ss.allPossiblyVisibleFileExts;
        selectedVisibleFileExts = ss.selectedVisibleFileExts;
        super.onRestoreInstanceState(ss.getSuperState());
    }

    public void showAll() {
        currentlyVisibleFileExts = null;
        buildFileExtFilterControls(true);
    }

    public Set<String> getAllPossibleFilters() {
        return allPossiblyVisibleFileExts;
    }

    public void setAllPossibleFilters(HashSet<String> allFilters) {
        this.allPossiblyVisibleFileExts = allFilters;
    }

    public void setVisibleFilters(SortedSet<String> visibleFileExts) {
        this.currentlyVisibleFileExts = visibleFileExts;
    }


    public void setSelectedFilters(SortedSet<String> visibleFileTypes) {
        if(selectedVisibleFileExts == null) {
            selectedVisibleFileExts = new HashSet<>(visibleFileTypes);
        } else {
            selectedVisibleFileExts.clear();
            selectedVisibleFileExts.addAll(visibleFileTypes);
        }
        buildFileExtFilterControls(true);
    }

    static class SavedState extends BaseSavedState {
        Set<String> allPossiblyVisibleFileExts;
        Set<String> selectedVisibleFileExts;
        Set<String> currentlyVisibleFileExts;

        SavedState(Parcelable superState) {
            super(superState);
        }

        SavedState(Parcel in) {
            super(in);
            allPossiblyVisibleFileExts = ParcelUtils.readStringSet(in);
            selectedVisibleFileExts = ParcelUtils.readStringSet(in);
            currentlyVisibleFileExts = ParcelUtils.readStringSet(in);
        }

        @Override
        public void writeToParcel(Parcel out, int flags) {
            super.writeToParcel(out, flags);
            ParcelUtils.writeStringSet(out, allPossiblyVisibleFileExts);
            ParcelUtils.writeStringSet(out, selectedVisibleFileExts);
            ParcelUtils.writeStringSet(out, currentlyVisibleFileExts);
        }

        public static final Parcelable.Creator<FilterControl.SavedState> CREATOR =
                new Parcelable.Creator<FilterControl.SavedState>() {
                    public FilterControl.SavedState createFromParcel(Parcel in) {
                        return new FilterControl.SavedState(in);
                    }

                    public FilterControl.SavedState[] newArray(int size) {
                        return new FilterControl.SavedState[size];
                    }
                };
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        for(int i = 0; i < fileExtFilters.getChildCount(); i++) {
            fileExtFilters.getChildAt(i).setEnabled(enabled);
        }
        toggleAll.setEnabled(enabled && fileExtFilters.getChildCount() > 0);
        showFiltersToggle.setEnabled(enabled && fileExtFilters.getChildCount() > 0);
    }

    public interface FilterListener {
        void onFilterUnchecked(Context context, String fileExt);

        void onFilterChecked(Context context, String fileExt);

        void onFiltersChanged(Context context, boolean filterHidden, boolean filterShown);
    }
}
