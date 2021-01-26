package delit.libs.ui.view.recycler;

import android.os.Bundle;

import androidx.annotation.NonNull;

/**
 * WARNING: If you extend this, you must override the store to and load from bundle methods
 *          adding in the extra fields you want persisted.
 * @param <Q>
 */
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

    public final Bundle storeToBundle(@NonNull Bundle bundle) {
        Bundle b = new Bundle();
        String bundleName = writeContentToBundle(b);
        bundle.putBundle(bundleName, b);
        return bundle;
    }

    /**
     * Override this as needed.
     * If you override this, override getBundleName
     *
     * @param b
     * @return
     */
    protected String writeContentToBundle(Bundle b) {
        b.putString("type", getBundleName());
        b.putBoolean("allowItemSelection", allowItemSelection);
        b.putBoolean("initialSelectionLocked", initialSelectionLocked);
        b.putBoolean("multiSelectionEnabled", multiSelectionEnabled);
        b.putBoolean("allowItemDeletion", allowItemDeletion);
        b.putBoolean("allowItemAddition", allowItemAddition);
        b.putBoolean("enabled", enabled);
        b.putBoolean("readonly", readonly);
        return getBundleName();
    }

    protected String getBundleName() {
        return "BaseRecyclerViewAdapterPreferences";
    }

    public final Q loadFromBundle(Bundle parent) {
        if(parent == null) {
            return (Q) this;
        }
        Bundle b = parent.getBundle(getBundleName());
        if(b != null) {
            String expectedType = getBundleName();
            String receivedType = b.getString("type");
            if(!expectedType.equals(receivedType)) {
                throw new IllegalStateException(String.format("Unable to load preferences. super.writeContentToBundle and super.readContentFromBundle must always be called. Expected %1$s but received type %2$s",expectedType, receivedType));
            }
            readContentFromBundle(b);
        }
        return (Q) this;
    }

    protected void readContentFromBundle(Bundle b) {
        allowItemSelection = b.getBoolean("allowItemSelection");
        initialSelectionLocked = b.getBoolean("initialSelectionLocked");
        multiSelectionEnabled = b.getBoolean("multiSelectionEnabled");
        allowItemDeletion = b.getBoolean("allowItemDeletion");
        allowItemAddition = b.getBoolean("allowItemAddition");
        enabled = b.getBoolean("enabled");
        readonly = b.getBoolean("readonly");
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