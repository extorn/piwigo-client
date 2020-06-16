package delit.piwigoclient.ui.permissions;

import android.os.Bundle;

import androidx.annotation.NonNull;

import delit.libs.ui.view.recycler.BaseRecyclerViewAdapterPreferences;

public class AlbumSelectionListAdapterPreferences extends BaseRecyclerViewAdapterPreferences {
    private boolean flattenAlbumHierarchy = true;
    private boolean showThumbnails;
    private boolean allowRootAlbumSelection;

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
    public Bundle storeToBundle(@NonNull Bundle bundle) {
        super.storeToBundle(bundle);
        Bundle b = new Bundle();
        b.putBoolean("showThumbnails", showThumbnails);
        b.putBoolean("flattenAlbumHierarchy", flattenAlbumHierarchy);
        b.putBoolean("allowRootAlbumSelection", allowRootAlbumSelection);
        bundle.putBundle("albumSelectionListAdapterPrefs", b);
        return bundle;
    }

    @Override
    public AlbumSelectionListAdapterPreferences loadFromBundle(Bundle bundle) {
        super.loadFromBundle(bundle);
        Bundle b = bundle.getBundle("albumSelectionListAdapterPrefs");
        if(b != null) {
            showThumbnails = b.getBoolean("showThumbnails");
            flattenAlbumHierarchy = b.getBoolean("flattenAlbumHierarchy");
            allowRootAlbumSelection = b.getBoolean("allowRootAlbumSelection");
        }
        return this;
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
