package delit.piwigoclient.ui.common.recyclerview;

import android.os.Bundle;

public class BaseRecyclerViewAdapterPreferences {
        private boolean allowItemSelection;
        private boolean initialSelectionLocked;
        private boolean multiSelectionEnabled;
        private boolean allowItemDeletion;
        private boolean allowItemAddition;
        private boolean enabled;

        public BaseRecyclerViewAdapterPreferences(){}

        public BaseRecyclerViewAdapterPreferences locked() {
            allowItemSelection = false;
            initialSelectionLocked = true;
            multiSelectionEnabled = false;
            allowItemDeletion = false;
            allowItemAddition = false;
            enabled = true;
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

        public BaseRecyclerViewAdapterPreferences unlocked(boolean multiSelectionAllowed, boolean initialSelectionLocked) {
            selectable(multiSelectionAllowed, initialSelectionLocked);
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
            return this;
        }

        public boolean isReadOnly() {
            return !allowItemDeletion && !allowItemSelection && !allowItemAddition;
        }

        public boolean isAllowItemSelection() {
            return allowItemSelection;
        }

        public boolean isAllowItemAddition() {
            return allowItemAddition;
        }

        public boolean isInitialSelectionLocked() {
            return initialSelectionLocked;
        }

        public boolean isMultiSelectionEnabled() {
            return multiSelectionEnabled;
        }

        public boolean isAllowItemDeletion() {
            return allowItemDeletion;
        }

        public boolean isEnabled() {
            return enabled;
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
}