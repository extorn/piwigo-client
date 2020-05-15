package delit.piwigoclient.ui.file;

import android.content.Context;
import android.os.Build;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.CompoundButton;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import java.util.HashSet;
import java.util.Set;
import java.util.SortedSet;

import delit.libs.ui.util.DisplayUtils;
import delit.libs.ui.util.ParcelUtils;
import delit.libs.ui.view.FlowLayout;

public class FilterControl extends FlowLayout {

    private Set<String> allPossiblyVisibleFileExts;
    private Set<String> currentlyVisibleFileExts;
    private Set<String> selectedVisibleFileExts;
    private FilterListener listener;

    public FilterControl(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public FilterControl(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public FilterControl(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    public void setListener(FilterListener listener) {
        this.listener = listener;
    }

    public void selectAll() {
        if(currentlyVisibleFileExts != null) {
            selectedVisibleFileExts = new HashSet<>(currentlyVisibleFileExts);
        } else {
            selectedVisibleFileExts = new HashSet<>();
        }
        buildFileExtFilterControls();
    }

    public void buildFileExtFilterControls() {

        // initialise local cached set of selected items
        if (selectedVisibleFileExts == null && allPossiblyVisibleFileExts != null) {
            selectedVisibleFileExts = new HashSet<>(allPossiblyVisibleFileExts);
        }
        ViewGroup fileExtFilters = this;

        // clear all the existing filters
        fileExtFilters.removeAllViews();

        // for each file type visible, show a checkbox and set it according to out local model
        if (currentlyVisibleFileExts != null && selectedVisibleFileExts != null) {
            boolean filterHidden = false;
            boolean filterShown = false;
            for (String fileExt : currentlyVisibleFileExts) {
                FlowLayout.LayoutParams layoutParams = new FlowLayout.LayoutParams(FlowLayout.LayoutParams.WRAP_CONTENT, FlowLayout.LayoutParams.WRAP_CONTENT);
                boolean checked = selectedVisibleFileExts.contains(fileExt);
                if (!checked) {
                    filterShown = true;
                    listener.onFilterUnchecked(fileExt);
                } else {
                    filterHidden = true;
                    listener.onFilterChecked(fileExt);
                }
                fileExtFilters.addView(createFileExtFilterControl(fileExt, checked), layoutParams);
            }
            if (filterShown || filterHidden) {
                listener.onFiltersChanged(filterHidden, filterShown);
            }
        }
    }

    private View createFileExtFilterControl(String fileExt, boolean checked) {
        CheckBox fileExtControl = new CheckBox(getContext());
        int paddingPx = DisplayUtils.dpToPx(getContext(), 5);
        fileExtControl.setPadding(paddingPx, paddingPx, paddingPx, paddingPx);
        fileExtControl.setText(fileExt);
        fileExtControl.setEnabled(isEnabled());
        fileExtControl.setChecked(checked);
        fileExtControl.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if(isChecked) {
                    selectedVisibleFileExts.add(fileExt);
                    listener.onFilterChecked(fileExt);
                } else {
                    selectedVisibleFileExts.remove(fileExt);
                    listener.onFilterUnchecked(fileExt);
                }
                listener.onFiltersChanged(!isChecked, isChecked);
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
        buildFileExtFilterControls();
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
        for(int i = 0; i < this.getChildCount(); i++) {
            getChildAt(i).setEnabled(enabled);
        }
    }

    public interface FilterListener {
        void onFilterUnchecked(String fileExt);

        void onFilterChecked(String fileExt);

        void onFiltersChanged(boolean filterHidden, boolean filterShown);
    }
}
