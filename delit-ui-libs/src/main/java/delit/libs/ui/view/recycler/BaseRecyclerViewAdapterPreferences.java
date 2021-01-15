package delit.libs.ui.view.recycler;

import android.os.Bundle;

import androidx.annotation.NonNull;

public class BaseRecyclerViewAdapterPreferences<Q extends BaseRecyclerViewAdapterPreferences<Q>> {
    private boolean allowItemSelection;
    private boolean initialSelectionLocked;
    private boolean multiSelectionEnabled;
    private boolean allowItemDeletion;
    private boolean allowItemAddition;
    private boolean enabled;
    private boolean readonly;

    public BaseRecyclerViewAdapterPreferences() {
    }

    public BaseRecyclerViewAdapterPreferences<Q> readonly() {
        readonly = true;
        return this;
    }

    public Q notSelectable() {
        allowItemSelection = false;
        return (Q) this;
    }

    public Q selectable(boolean multiSelectionAllowed, boolean initialSelectionLocked) {
        allowItemSelection = true;
        this.initialSelectionLocked = initialSelectionLocked;
        this.multiSelectionEnabled = multiSelectionAllowed;
        allowItemDeletion = false;
        allowItemAddition = false;
        enabled = true;
        return (Q) this;
    }

    public Q deletable() {
        allowItemDeletion = true;
        return (Q) this;
    }

    public Bundle storeToBundle(@NonNull Bundle bundle) {
        Bundle b = new Bundle();
        b.putBoolean("allowItemSelection", allowItemSelection);
        b.putBoolean("initialSelectionLocked", initialSelectionLocked);
        b.putBoolean("multiSelectionEnabled", multiSelectionEnabled);
        b.putBoolean("allowItemDeletion", allowItemDeletion);
        b.putBoolean("allowItemAddition", allowItemAddition);
        b.putBoolean("enabled", enabled);
        b.putBoolean("readonly", readonly);
        bundle.putBundle("BaseRecyclerViewAdapterPreferences", b);
        return bundle;
    }

    public Q loadFromBundle(Bundle parent) {
        if(parent == null) {
            return (Q) this;
        }
        Bundle b = parent.getBundle("BaseRecyclerViewAdapterPreferences");
        if(b != null) {
            allowItemSelection = b.getBoolean("allowItemSelection");
            initialSelectionLocked = b.getBoolean("initialSelectionLocked");
            multiSelectionEnabled = b.getBoolean("multiSelectionEnabled");
            allowItemDeletion = b.getBoolean("allowItemDeletion");
            allowItemAddition = b.getBoolean("allowItemAddition");
            enabled = b.getBoolean("enabled");
            readonly = b.getBoolean("readonly");
        }
        return (Q) this;
    }

    public boolean isReadOnly() {
        return readonly;
    }

    public boolean isAllowItemSelection() {
        return allowItemSelection;
    }

    public void setAllowItemSelection(boolean allowItemSelection) {
        this.allowItemSelection = allowItemSelection;
    }

    public boolean isAllowItemAddition() {
        return allowItemAddition && !readonly;
    }

    public void setAllowItemAddition(boolean allowItemAddition) {
        this.allowItemAddition = allowItemAddition;
    }

    public boolean isInitialSelectionLocked() {
        return initialSelectionLocked;
    }

    public void setInitialSelectionLocked(boolean initialSelectionLocked) {
        this.initialSelectionLocked = initialSelectionLocked;
    }

    public boolean isMultiSelectionEnabled() {
        return multiSelectionEnabled;
    }

    public boolean isAllowItemDeletion() {
        return allowItemDeletion && !readonly;
    }

    public boolean isEnabled() {
        return enabled && !readonly;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public void withAllowMultiSelect(boolean multiSelectionAllowed) {
        this.multiSelectionEnabled = multiSelectionAllowed;
    }
}