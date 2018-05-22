package delit.piwigoclient.ui.album.view;

import java.util.Date;

import delit.piwigoclient.ui.common.recyclerview.BaseRecyclerViewAdapterPreferences;

/**
 *
 SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(parent.getContext().getApplicationContext());
 preferredThumbnailSize = prefs.getString(parent.getContext().getString(R.string.preference_gallery_item_thumbnail_size_key), parent.getContext().getString(R.string.preference_gallery_item_thumbnail_size_default));

 *
 */
public class AlbumItemRecyclerViewAdapterPreferences extends BaseRecyclerViewAdapterPreferences {
        private Date recentlyAlteredThresholdDate;
        private String preferredThumbnailSize;
        private boolean useDarkMode;
        private boolean showAlbumThumbnailsZoomed;
        private boolean showLargeAlbumThumbnails;
        private float albumWidth;
        private boolean showResourceNames;
        private boolean useMasonryStyle;
        private final int scalingQuality = SCALING_QUALITY_MEDIUM;
        public static final int SCALING_QUALITY_PERFECT = Integer.MAX_VALUE;
        public static final int SCALING_QUALITY_VHIGH = 960;
        public static final int SCALING_QUALITY_HIGH = 480;
        public static final int SCALING_QUALITY_MEDIUM = 240;
        public static final int SCALING_QUALITY_LOW = 120;
        public static final int SCALING_QUALITY_VLOW = 60;

        public AlbumItemRecyclerViewAdapterPreferences(){}

        public AlbumItemRecyclerViewAdapterPreferences withRecentlyAlteredThresholdDate(Date recentlyAlteredThresholdDate) {
            this.recentlyAlteredThresholdDate = recentlyAlteredThresholdDate;
            return this;
        }

        public AlbumItemRecyclerViewAdapterPreferences withDarkMode(boolean useDarkMode) {
                this.useDarkMode = useDarkMode;
                return this;
        }

        public AlbumItemRecyclerViewAdapterPreferences withPreferredThumbnailSize(String preferredThumbnailSize) {
                this.preferredThumbnailSize = preferredThumbnailSize;
                return this;
        }

        public AlbumItemRecyclerViewAdapterPreferences withLargeAlbumThumbnails(boolean showLargeAlbumThumbnails) {
                this.showLargeAlbumThumbnails = showLargeAlbumThumbnails;
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

        public AlbumItemRecyclerViewAdapterPreferences withMasonryStyle(boolean useMasonryStyle) {
                this.useMasonryStyle = useMasonryStyle;
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

        public boolean isShowLargeAlbumThumbnails() {
                return showLargeAlbumThumbnails;
        }

        public boolean isUseDarkMode() {
                return useDarkMode;
        }

        public boolean isShowResourceNames() {
                return showResourceNames;
        }

        public boolean isUseMasonryStyle() {
                return useMasonryStyle;
        }
}