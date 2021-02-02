package delit.piwigoclient.ui.permissions;

import android.os.Bundle;

import delit.libs.ui.view.recycler.BaseRecyclerViewAdapterPreferences;

public class AlbumSelectionListAdapterPreferences extends BaseRecyclerViewAdapterPreferences<AlbumSelectionListAdapterPreferences> {
    private boolean flattenAlbumHierarchy = true;
    private boolean showThumbnails;
    private boolean allowRootAlbumSelection;

    public AlbumSelectionListAdapterPreferences(Bundle bundle) {
        loadFromBundle(bundle);
    }

    public AlbumSelectionListAdapterPreferences(boolean allowEdit) {
        setFlattenAlbumHierarchy(true);
        setShowThumbnails(false); // thumbnails aren't supported for category item stubs.
        selectable(true, false);
        if (!allowEdit) {
            readonly();
        }
    }

    public AlbumSelectionListAdapterPreferences(boolean flattenAlbumHierarchy, boolean showThumbnails, boolean allowEditing, boolean allowMultiSelect, boolean initialSelectionLocked) {
        setFlattenAlbumHierarchy(flattenAlbumHierarchy);
        setShowThumbnails(showThumbnails);
        selectable(allowMultiSelect, initialSelectionLocked);
        if (!allowEditing) {
            readonly();
        }
    }

    public boolean isFlattenAlbumHierarchy() {
        return flattenAlbumHierarchy;
    }

    public void setFlattenAlbumHierarchy(boolean flattenAlbumHierarchy) {
        this.flattenAlbumHierarchy = flattenAlbumHierarchy;
    }

    public boolean isShowThumbnails() {
        return showThumbnails;
    }

    @Override
    protected String getBundleName() {
        return "AlbumSelectionListAdapterPrefs";
    }

    @Override
    protected String writeContentToBundle(Bundle b) {
        super.writeContentToBundle(b);
        b.putBoolean("showThumbnails", showThumbnails);
        b.putBoolean("flattenAlbumHierarchy", flattenAlbumHierarchy);
        b.putBoolean("allowRootAlbumSelection", allowRootAlbumSelection);
        return getBundleName();
    }

    @Override
    protected void readContentFromBundle(Bundle b) {
        super.readContentFromBundle(b);
        showThumbnails = b.getBoolean("showThumbnails");
        flattenAlbumHierarchy = b.getBoolean("flattenAlbumHierarchy");
        allowRootAlbumSelection = b.getBoolean("allowRootAlbumSelection");
    }

    public void setShowThumbnails(boolean showThumbnails) {
        this.showThumbnails = showThumbnails;
    }

    public boolean isAllowRootAlbumSelection() {
        return allowRootAlbumSelection;
    }

    public void setAllowRootAlbumSelection(boolean allowRootAlbumSelection) {
        this.allowRootAlbumSelection = allowRootAlbumSelection;
    }
}
