package delit.piwigoclient.model.piwigo;

import android.os.Parcel;

import androidx.annotation.NonNull;

import java.util.Date;

public class StaticCategoryItem extends CategoryItem {
    static final String BLANK_TAG = "PIWIGO_CLIENT_INTERNAL_BLANK";
    public static final StaticCategoryItem ROOT_ALBUM = new StaticCategoryItem(0, "--------", null, false, null, 0, 0, 0, null);
    public static final StaticCategoryItem ORPHANS_ROOT_ALBUM = new StaticCategoryItem(-1, "Orphans", null, false, null, 0, 0, 0, null);
    public static final StaticCategoryItem BLANK = new StaticCategoryItem(Long.MIN_VALUE, BLANK_TAG, null, true, null, 0, 0, 0, null);
    public static final StaticCategoryItem ALBUM_HEADING = new StaticCategoryItem(Long.MIN_VALUE + 100, "AlbumsHeading", null, true, null, 0, 0, 0, null) {

        @Override
        public int getType() {
            return GalleryItem.ALBUM_HEADING_TYPE;
        }
    };
    public static final CategoryItem ADVERT = new StaticCategoryItem(Long.MIN_VALUE + 1, null, null, true, null, 0, 0, 0, null) {

        @Override
        public int getType() {
            return GalleryItem.ADVERT_TYPE;
        }
    };

    public StaticCategoryItem(CategoryItemStub stub) {
        super(stub);
    }

    public StaticCategoryItem(long id) {
        super(id);
    }

    public StaticCategoryItem(long id, String name, String description, boolean isPrivate, Date lastAltered, int photoCount, long totalPhotoCount, long subCategories, String thumbnailUrl) {
        super(id, name, description, isPrivate, lastAltered, photoCount, totalPhotoCount, subCategories, thumbnailUrl);
    }

    @NonNull
    public CategoryItem toInstance() {
        return toInstance(this.getId());
    }

    public StaticCategoryItem(Parcel in) {
        super(in);
    }

    @NonNull
    public CategoryItem toInstance(long newId) {
        CategoryItem item = new CategoryItem(newId,this.getName(), this.getDescription(), this.isPrivate(), this.getLastAltered(), this.getPhotoCount(), this.getTotalPhotos(), this.getSubCategories(), this.getThumbnailUrl());
        return item;
    }
}
