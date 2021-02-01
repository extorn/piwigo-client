package delit.piwigoclient.ui.album.view;

import android.os.Bundle;

import androidx.annotation.NonNull;

import java.util.Date;

import delit.libs.ui.util.BundleUtils;
import delit.libs.ui.view.recycler.BaseRecyclerViewAdapterPreferences;

public class AlbumItemRecyclerViewAdapterPreferences extends BaseRecyclerViewAdapterPreferences<AlbumItemRecyclerViewAdapterPreferences> {
    private Date recentlyAlteredThresholdDate;
    private String preferredThumbnailSize;
    private String preferredAlbumThumbnailSize;
    private boolean showAlbumThumbnailsZoomed;
    private float albumWidthInches;
    private boolean showResourceNames;

    public AlbumItemRecyclerViewAdapterPreferences() {
    }

    @Override
    protected String getBundleName() {
        return "AlbumItemRecyclerViewAdapterPrefs";
    }

    @Override
    protected String writeContentToBundle(Bundle b) {
        super.writeContentToBundle(b);
        BundleUtils.putDate(b, "recentlyAlteredThresholdDate", recentlyAlteredThresholdDate);
        b.putString("preferredThumbnailSize", preferredThumbnailSize);
        b.putString("preferredAlbumThumbnailSize", preferredAlbumThumbnailSize);
        b.putBoolean("showAlbumThumbnailsZoomed", showAlbumThumbnailsZoomed);
        b.putFloat("albumWidth", albumWidthInches);
        b.putBoolean("showResourceNames", showResourceNames);
//                b.putInt("scalingQuality", scalingQuality);
        return getBundleName();
    }

    @Override
    protected void readContentFromBundle(@NonNull Bundle b) {
        super.readContentFromBundle(b);
        recentlyAlteredThresholdDate = BundleUtils.getDate(b, "recentlyAlteredThresholdDate");
        preferredThumbnailSize = b.getString("preferredThumbnailSize");
        preferredAlbumThumbnailSize = b.getString("preferredAlbumThumbnailSize");
        showAlbumThumbnailsZoomed = b.getBoolean("showAlbumThumbnailsZoomed");
        albumWidthInches = b.getFloat("albumWidth");
        showResourceNames = b.getBoolean("showResourceNames");
//                scalingQuality = b.getInt("scalingQuality");
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

    public AlbumItemRecyclerViewAdapterPreferences withShowingResourceNames(boolean showResourceNames) {
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