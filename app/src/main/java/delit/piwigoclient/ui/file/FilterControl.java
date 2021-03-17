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

    private Set<String> allFilters;
    private Set<String> activeFilters;
    private Set<String> selectedFilters;
    private FilterListener listener;
    private ViewGroup filtersViewGroup;
    private CheckBox showFiltersToggle;
    private Button toggleAll;
    private boolean allSelected;
    private boolean showInactiveFilters = true;
    private boolean showingInactiveFilters = false;
    private CheckBox inactiveFilterControl;

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
        showFiltersToggle.setOnCheckedChangeListener((buttonView, showFilters) -> {
            filtersViewGroup.setVisibility(showFilters ? VISIBLE : GONE);
            configureInactiveFilterVisibilityToggleControl();
        });
        filtersViewGroup = findViewById(R.id.filters);
        toggleAll = findViewById(R.id.toggle_all_button);
        toggleAll.setOnClickListener(v -> onToggleAllSelection());

        inactiveFilterControl = findViewById(R.id.view_inactive_filters_button);
        inactiveFilterControl.setOnCheckedChangeListener((buttonView, isChecked) -> FilterControl.this.toggleShowingInactiveFilters(isChecked));

        setToggleSelectionButtonText();
        addDemoData();
    }

    public void setShowInactiveFilters(boolean showInactiveFilters) {
        this.showInactiveFilters = showInactiveFilters;
        buildFilterViews(false);
    }

    private void toggleShowingInactiveFilters(boolean showInactiveFiltersNow) {
        if(showingInactiveFilters != showInactiveFiltersNow) {
            showingInactiveFilters = showInactiveFiltersNow;
            inactiveFilterControl.setChecked(showingInactiveFilters);
            buildFilterViews(false);
        }
    }

    private void onToggleAllSelection() {
        if (!CollectionUtils.equals(activeFilters, selectedFilters)) {
            selectAllFilters();
            allSelected = true;
        } else {
            selectNoFilters();
            allSelected = false;
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
        if (CollectionUtils.equals(activeFilters, selectedFilters)) {
            toggleAll.setText(getContext().getString(R.string.button_show_none));
        } else {
            toggleAll.setText(getContext().getString(R.string.button_show_all));
        }
    }

    private void addDemoData() {
        if (!isInEditMode()) {
            return;
        }
        allFilters = new HashSet<>();
        allFilters.add("jpg");
        allFilters.add("bmp");
        allFilters.add("wav");
        allFilters.add("raw");
        allFilters.add("mp4");
        activeFilters = new HashSet<>();
        activeFilters.addAll(allFilters);
        selectedFilters = new HashSet<>();
        selectedFilters.add("mp4");
        selectedFilters.add("jpg");
        buildFilterViews(false);
    }

    public void setListener(FilterListener listener) {
        this.listener = listener;
    }

    public void selectAll() {
        selectAll(true);
    }
    public void selectAll(boolean notifyListenersOfFilterStatusChange) {
        if(selectedFilters == null) {
            selectedFilters = new HashSet<>();
        }

        if(activeFilters == null) {
            activeFilters = new HashSet<>();
        }
        boolean changed = selectedFilters.retainAll(activeFilters);
        changed |= selectedFilters.addAll(activeFilters);
        buildFilterViews(changed && notifyListenersOfFilterStatusChange);
    }
    public void selectNone() {
        selectNone(true);
    }
    public void selectNone(boolean notifyListenersOfFilterStatusChange) {
        selectedFilters = new HashSet<>();
        buildFilterViews(notifyListenersOfFilterStatusChange);
    }

    public void buildFilterViews(boolean notifyOnFiltersChanged) {

        // initialise local cached set of selected items
        if (selectedFilters == null && activeFilters != null) {
            selectedFilters = new HashSet<>(activeFilters);
        }

        // clear all the existing filters
        filtersViewGroup.removeAllViews();
        boolean allSelected = false;
        int lastActiveFilterIdx = 0;
        // for each file type visible, show a checkbox and set it according to out local model
        if (activeFilters != null && selectedFilters != null) {
            boolean filterHidden = false;
            boolean filterShown = false;
            allSelected = CollectionUtils.equals(activeFilters, selectedFilters);
            for (String filterText : allFilters) {
                boolean isInactive = !activeFilters.contains(filterText);
                if((!showInactiveFilters || !showingInactiveFilters) && isInactive) {
                    continue;
                }
                FlowLayout.LayoutParams layoutParams = new FlowLayout.LayoutParams(FlowLayout.LayoutParams.WRAP_CONTENT, FlowLayout.LayoutParams.WRAP_CONTENT);
                boolean checked = selectedFilters.contains(filterText);
                if (!checked) {
                    filterHidden = true;
                    if(listener != null) {
                        listener.onFilterUnchecked(getContext(), filterText);
                    }
                } else {
                    filterShown = true;
                    if(listener != null) {
                        listener.onFilterChecked(getContext(), filterText);
                    }
                }
                View filterView = createFilterView(filterText, checked, !isInactive);
                if(isInactive) {
                    filtersViewGroup.addView(filterView, layoutParams);
                } else {
                    filtersViewGroup.addView(filterView, lastActiveFilterIdx++, layoutParams);
                }
            }
            if (notifyOnFiltersChanged && (filterShown || filterHidden)) {
                if(listener != null) {
                    listener.onFiltersChanged(getContext(), filterHidden, filterShown);
                }
            }
        }

        configureInactiveFilterVisibilityToggleControl();

        boolean filtersVisible = (activeFilters != null && !activeFilters.isEmpty());
        showFiltersToggle.setChecked(showFiltersToggle.isChecked() && filtersVisible);
        showFiltersToggle.setEnabled(filtersVisible);
        toggleAll.setEnabled(filtersVisible);
        this.allSelected = allSelected;
        setToggleSelectionButtonText();
    }

    private void configureInactiveFilterVisibilityToggleControl() {
        boolean showInactiveFiltersViewControl = showInactiveFilters && allFilters != null && activeFilters != null && activeFilters.size() != allFilters.size() && allFilters.size() > 0;
        inactiveFilterControl.setVisibility(showInactiveFiltersViewControl ? filtersViewGroup.getVisibility() : GONE);
    }

    private View createFilterView(String fileExt, boolean checked, boolean isActive) {
        MaterialCheckBox fileExtControl = new MaterialCheckBox(getContext());
        int paddingPx = DisplayUtils.dpToPx(getContext(), 5);
        fileExtControl.setPadding(paddingPx, paddingPx, paddingPx, paddingPx);
        fileExtControl.setText(fileExt);
        fileExtControl.setEnabled(isEnabled() && isActive);
        fileExtControl.setChecked(checked);
        fileExtControl.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if(isChecked) {
                selectedFilters.add(fileExt);
                if(listener != null) {
                    if(CollectionUtils.equals(activeFilters, selectedFilters)) {
                        allSelected = true; // all selected
                        setToggleSelectionButtonText();
                    }
                    listener.onFilterChecked(getContext(), fileExt);
                }
            } else {
                selectedFilters.remove(fileExt);
                if(listener != null) {
                    if(selectedFilters.size() == 0) {
                        allSelected = false; // all selected
                        setToggleSelectionButtonText();
                    }
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
        ss.allFilters = allFilters;
        ss.checkedFilters = selectedFilters;
        return ss;
    }

    @Override
    public void onRestoreInstanceState(Parcelable state) {
        FilterControl.SavedState ss = (FilterControl.SavedState) state;
        allFilters = ss.allFilters;
        selectedFilters = ss.checkedFilters;
        super.onRestoreInstanceState(ss.getSuperState());
    }

    public void showAll() {
        activeFilters = null;
        buildFilterViews(true);
    }

    public Set<String> getAllFilters() {
        return allFilters;
    }

    public void setAllFilters(SortedSet<String> allFilters) {
        this.allFilters = allFilters;
    }

    public void setActiveFilters(SortedSet<String> activeFilters) {
        this.activeFilters = activeFilters;
        this.activeFilters.retainAll(allFilters);
    }


    public void setCheckedFilters(SortedSet<String> checkedFilters) {
        if(this.selectedFilters == null) {
            this.selectedFilters = new HashSet<>(checkedFilters);
        } else {
            this.selectedFilters.clear();
            this.selectedFilters.addAll(checkedFilters);
        }
        buildFilterViews(true);
    }

    static class SavedState extends BaseSavedState {
        Set<String> allFilters;
        Set<String> checkedFilters;
        Set<String> activeFilters;

        SavedState(Parcelable superState) {
            super(superState);
        }

        SavedState(Parcel in) {
            super(in);
            allFilters = ParcelUtils.readStringSet(in);
            checkedFilters = ParcelUtils.readStringSet(in);
            activeFilters = ParcelUtils.readStringSet(in);
        }

        @Override
        public void writeToParcel(Parcel out, int flags) {
            super.writeToParcel(out, flags);
            ParcelUtils.writeStringSet(out, allFilters);
            ParcelUtils.writeStringSet(out, checkedFilters);
            ParcelUtils.writeStringSet(out, activeFilters);
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
        for(int i = 0; i < filtersViewGroup.getChildCount(); i++) {
            filtersViewGroup.getChildAt(i).setEnabled(enabled);
        }
        toggleAll.setEnabled(enabled && filtersViewGroup.getChildCount() > 0);
        showFiltersToggle.setEnabled(enabled && filtersViewGroup.getChildCount() > 0);
    }

    public interface FilterListener {
        void onFilterUnchecked(Context context, String filter);

        void onFilterChecked(Context context, String filter);

        void onFiltersChanged(Context context, boolean filterHidden, boolean filterShown);
    }
}
