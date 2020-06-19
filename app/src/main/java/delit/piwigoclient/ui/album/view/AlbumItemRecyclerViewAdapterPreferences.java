package delit.piwigoclient.ui.album.view;

import android.os.Bundle;

import java.util.Date;

import delit.libs.ui.util.BundleUtils;
import delit.libs.ui.view.recycler.BaseRecyclerViewAdapterPreferences;

public class AlbumItemRecyclerViewAdapterPreferences extends BaseRecyclerViewAdapterPreferences {
    private Date recentlyAlteredThresholdDate;
    private String preferredThumbnailSize;
    private String preferredAlbumThumbnailSize;
    private boolean showAlbumThumbnailsZoomed;
    private float albumWidthInches;
    private boolean showResourceNames;

    public AlbumItemRecyclerViewAdapterPreferences() {
    }

    @Override
    public Bundle storeToBundle(Bundle parent) {
        Bundle b = new Bundle();
        BundleUtils.putDate(b, "recentlyAlteredThresholdDate", recentlyAlteredThresholdDate);
        b.putString("preferredThumbnailSize", preferredThumbnailSize);
        b.putString("preferredAlbumThumbnailSize", preferredAlbumThumbnailSize);
        b.putBoolean("showAlbumThumbnailsZoomed", showAlbumThumbnailsZoomed);
        b.putFloat("albumWidth", albumWidthInches);
        b.putBoolean("showResourceNames", showResourceNames);
//                b.putInt("scalingQuality", scalingQuality);
        super.storeToBundle(b);
        parent.putBundle("AlbumItemRecyclerViewAdapterPreferences", b);
        return parent;
    }

    @Override
    public AlbumItemRecyclerViewAdapterPreferences loadFromBundle(Bundle parent) {
        Bundle b = parent.getBundle("AlbumItemRecyclerViewAdapterPreferences");
        if(b != null) {
            recentlyAlteredThresholdDate = BundleUtils.getDate(b, "recentlyAlteredThresholdDate");
            preferredThumbnailSize = b.getString("preferredThumbnailSize");
            preferredAlbumThumbnailSize = b.getString("preferredAlbumThumbnailSize");
            showAlbumThumbnailsZoomed = b.getBoolean("showAlbumThumbnailsZoomed");
            albumWidthInches = b.getFloat("albumWidth");
            showResourceNames = b.getBoolean("showResourceNames");
//                scalingQuality = b.getInt("scalingQuality");
            super.loadFromBundle(b);
        }
        return this;
    }

    public AlbumItemRecyclerViewAdapterPreferences withRecentlyAlteredThresholdDate(Date recentlyAlteredThresholdDate) {
        this.recentlyAlteredThresholdDate = recentlyAlteredThresholdDate;
        return this;
    }

    public AlbumItemRecyclerViewAdapterPreferences withPreferredThumbnailSize(String preferredThumbnailSize) {
        this.preferredThumbnailSize = preferredThumbnailSize;
        return this;
    }

    public AlbumItemRecyclerViewAdapterPreferences withPreferredAlbumThumbnailSize(String preferredAlbumThumbnailSize) {
        this.preferredAlbumThumbnailSize = preferredAlbumThumbnailSize;
        return this;
    }

    public AlbumItemRecyclerViewAdapterPreferences withAlbumWidthInches(float albumWidthInches) {
        this.albumWidthInches = albumWidthInches;
        return this;
    }

    public AlbumItemRecyclerViewAdapterPreferences withShowAlbumThumbnailsZoomed(boolean showAlbumThumbnailsZoomed) {
        this.showAlbumThumbnailsZoomed = showAlbumThumbnailsZoomed;
        return this;
    }

    public AlbumItemRecyclerViewAdapterPreferences withShowingAlbumNames(boolean showResourceNames) {
        this.showResourceNames = showResourceNames;
        return this;
    }

    public Date getRecentlyAlteredThresholdDate() {
        return recentlyAlteredThresholdDate;
    }

    public String getPreferredThumbnailSize() {
        return preferredThumbnailSize;
    }

    public float getAlbumWidthInches() {
        return albumWidthInches;
    }

    public boolean isShowAlbumThumbnailsZoomed() {
        return showAlbumThumbnailsZoomed;
    }

    public boolean isShowResourceNames() {
        return showResourceNames;
    }

    public String getPreferredAlbumThumbnailSize() {
        return preferredAlbumThumbnailSize;
    }
}