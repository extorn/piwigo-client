package delit.piwigoclient.model.piwigo;

import android.os.Parcel;
import android.os.Parcelable;
import android.util.Log;

import java.util.List;

import delit.libs.core.util.Logging;

public class PiwigoFavorites extends ResourceContainer<PiwigoFavorites.FavoritesSummaryDetails, GalleryItem> implements Parcelable {

    private static final String TAG = "PiwigoFavorites";

    public PiwigoFavorites(FavoritesSummaryDetails favoritesSummaryDetails) {
        super(favoritesSummaryDetails, "GalleryItem", favoritesSummaryDetails.getPhotoCount());
    }

    public PiwigoFavorites(Parcel in) {
        super(in);
    }

    @Override
    public boolean setRetrieveItemsInReverseOrder(boolean retrieveItemsInReverseOrder) {
        Logging.log(Log.ERROR, TAG, "Unable to reverse the order of the items. Why is this being attempted?");
//        throw new UnsupportedOperationException("cannot reverse the order");
        return false;
    }

    @Override
    protected void sortItems(List<GalleryItem> items) {
        throw new UnsupportedOperationException("cannot sort the items");
    }

    public void clear() {
        super.clear();
    }

    @Override
    public int getImgResourceCount() {
        return getContainerDetails().getPhotoCount();
    }

    public static final Parcelable.Creator<PiwigoFavorites> CREATOR
            = new Parcelable.Creator<PiwigoFavorites>() {
        public PiwigoFavorites createFromParcel(Parcel in) {
            return new PiwigoFavorites(in);
        }

        public PiwigoFavorites[] newArray(int size) {
            return new PiwigoFavorites[size];
        }
    };

    public static class FavoritesSummaryDetails implements Identifiable, Parcelable, PhotoContainer {

        private int photoCount;

        public FavoritesSummaryDetails(int photoCount) {
            this.photoCount = photoCount;
        }

        public FavoritesSummaryDetails(Parcel in) {
            photoCount = in.readInt();
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            dest.writeInt(photoCount);
        }

        public static final Creator<FavoritesSummaryDetails> CREATOR = new Creator<FavoritesSummaryDetails>() {
            @Override
            public FavoritesSummaryDetails createFromParcel(Parcel in) {
                return new FavoritesSummaryDetails(in);
            }

            @Override
            public FavoritesSummaryDetails[] newArray(int size) {
                return new FavoritesSummaryDetails[size];
            }
        };

        @Override
        public int describeContents() {
            return 0;
        }


        @Override
        public long getId() {
            return 0;
        }

        public void setPhotoCount(int photoCount) {
            this.photoCount = photoCount;
        }

        public int getPhotoCount() {
            return photoCount;
        }

        @Override
        public int getPagesOfPhotos(int pageSize) {
            int pages = ((getPhotoCount() / pageSize) + (getPhotoCount() % pageSize > 0 ? 0 : -1));
            return Math.max(pages, 0);
        }
    }
}
