package delit.piwigoclient.ui.common.recyclerview;

import android.os.Bundle;

public class BaseRecyclerViewAdapterPreferences {
        private boolean allowItemSelection;
        private boolean initialSelectionLocked;
        private boolean multiSelectionEnabled;
        private boolean allowItemDeletion;
        private boolean allowItemAddition;
        private boolean enabled;
        private boolean readonly;

        public BaseRecyclerViewAdapterPreferences(){}

        public BaseRecyclerViewAdapterPreferences readonly() {
            readonly = true;
            return this;
        }

        public BaseRecyclerViewAdapterPreferences selectable(boolean multiSelectionAllowed, boolean initialSelectionLocked) {
            allowItemSelection = true;
            this.initialSelectionLocked = initialSelectionLocked;
            this.multiSelectionEnabled = multiSelectionAllowed;
            allowItemDeletion = false;
            allowItemAddition = false;
            enabled = true;
            return this;
        }

        public BaseRecyclerViewAdapterPreferences deletable() {
            allowItemDeletion = true;
            return this;
        }

        public Bundle storeToBundle(Bundle parent) {
            Bundle b = new Bundle();
            b.putBoolean("allowItemSelection", allowItemSelection);
            b.putBoolean("initialSelectionLocked", initialSelectionLocked);
            b.putBoolean("multiSelectionEnabled", multiSelectionEnabled);
            b.putBoolean("allowItemDeletion", allowItemDeletion);
            b.putBoolean("allowItemAddition", allowItemAddition);
            b.putBoolean("enabled", enabled);
            b.putBoolean("readonly", readonly);
            parent.putBundle("BaseRecyclerViewAdapterPreferences", b);
            return parent;
        }

        public BaseRecyclerViewAdapterPreferences loadFromBundle(Bundle parent) {
            Bundle b = parent.getBundle("BaseRecyclerViewAdapterPreferences");
            allowItemSelection = b.getBoolean("allowItemSelection");
            initialSelectionLocked = b.getBoolean("initialSelectionLocked");
            multiSelectionEnabled = b.getBoolean("multiSelectionEnabled");
            allowItemDeletion = b.getBoolean("allowItemDeletion");
            allowItemAddition = b.getBoolean("allowItemAddition");
            enabled = b.getBoolean("enabled");
            readonly = b.getBoolean("readonly");
            return this;
        }

        public boolean isReadOnly() {
            return readonly;
        }

        public boolean isAllowItemSelection() {
            return allowItemSelection;
        }

        public boolean isAllowItemAddition() {
            return allowItemAddition && !readonly;
        }

        public boolean isInitialSelectionLocked() {
            return initialSelectionLocked;
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

        public void setAllowItemSelection(boolean allowItemSelection) {
            this.allowItemSelection = allowItemSelection;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

    public void setAllowItemAddition(boolean allowItemAddition) {
        this.allowItemAddition = allowItemAddition;
    }

    public void setInitialSelectionLocked(boolean initialSelectionLocked) {
        this.initialSelectionLocked = initialSelectionLocked;
    }
}