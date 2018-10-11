package delit.piwigoclient.ui.album.view;

import android.os.Bundle;

import java.util.Date;

import delit.piwigoclient.ui.common.recyclerview.BaseRecyclerViewAdapterPreferences;
import delit.piwigoclient.ui.common.util.BundleUtils;

public class AlbumItemRecyclerViewAdapterPreferences extends BaseRecyclerViewAdapterPreferences {
    public static final int SCALING_QUALITY_PERFECT = Integer.MAX_VALUE;
    public static final int SCALING_QUALITY_VHIGH = 960;
    public static final int SCALING_QUALITY_HIGH = 480;
    public static final int SCALING_QUALITY_MEDIUM = 240;
    public static final int SCALING_QUALITY_LOW = 120;
    public static final int SCALING_QUALITY_VLOW = 60;
    private final int scalingQuality = SCALING_QUALITY_MEDIUM;
    private Date recentlyAlteredThresholdDate;
    private String preferredThumbnailSize;
    private String preferredAlbumThumbnailSize;
    private boolean showAlbumThumbnailsZoomed;
    private float albumWidth;
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
        b.putFloat("albumWidth", albumWidth);
        b.putBoolean("showResourceNames", showResourceNames);
//                b.putInt("scalingQuality", scalingQuality);
        super.storeToBundle(b);
        parent.putBundle("AlbumItemRecyclerViewAdapterPreferences", b);
        return parent;
    }

    @Override
    public AlbumItemRecyclerViewAdapterPreferences loadFromBundle(Bundle parent) {
        Bundle b = parent.getBundle("AlbumItemRecyclerViewAdapterPreferences");
        recentlyAlteredThresholdDate = BundleUtils.getDate(b, "recentlyAlteredThresholdDate");
        preferredThumbnailSize = b.getString("preferredThumbnailSize");
        preferredAlbumThumbnailSize = b.getString("preferredAlbumThumbnailSize");
        showAlbumThumbnailsZoomed = b.getBoolean("showAlbumThumbnailsZoomed");
        albumWidth = b.getFloat("albumWidth");
        showResourceNames = b.getBoolean("showResourceNames");
//                scalingQuality = b.getInt("scalingQuality");
        super.loadFromBundle(b);
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

    public AlbumItemRecyclerViewAdapterPreferences withAlbumWidth(float albumWidth) {
        this.albumWidth = albumWidth;
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

    public float getAlbumWidth() {
        return albumWidth;
    }

    public int getScalingQuality() {
        return scalingQuality;
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