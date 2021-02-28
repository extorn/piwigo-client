package delit.piwigoclient.ui.album.view.action;

import android.os.Parcel;
import android.os.Parcelable;

import androidx.appcompat.app.AlertDialog;

import java.util.HashSet;

import delit.libs.ui.util.ParcelUtils;
import delit.piwigoclient.R;
import delit.piwigoclient.model.piwigo.CategoryItem;
import delit.piwigoclient.model.piwigo.GalleryItem;
import delit.piwigoclient.model.piwigo.PiwigoAlbum;
import delit.piwigoclient.piwigoApi.handlers.AlbumRemovePermissionsResponseHandler;
import delit.piwigoclient.ui.album.view.AbstractViewAlbumFragment;
import delit.piwigoclient.ui.common.FragmentUIHelper;
import delit.piwigoclient.ui.common.dialogmessage.QuestionResultAdapter;

public class RemoveAccessToAlbumAction<F extends AbstractViewAlbumFragment<F,FUIH>,FUIH extends FragmentUIHelper<FUIH,F>> extends QuestionResultAdapter<FUIH, F> implements Parcelable {
    public static final Creator<RemoveAccessToAlbumAction<?,?>> CREATOR = new Creator<RemoveAccessToAlbumAction<?,?>>() {
        @Override
        public RemoveAccessToAlbumAction<?,?> createFromParcel(Parcel in) {
            return new RemoveAccessToAlbumAction<>(in);
        }

        @Override
        public RemoveAccessToAlbumAction<?,?>[] newArray(int size) {
            return new RemoveAccessToAlbumAction[size];
        }
    };
    private final HashSet<Long> newlyRemovedGroups;
    private final HashSet<Long> newlyRemovedUsers;

    public RemoveAccessToAlbumAction(FUIH uiHelper, HashSet<Long> newlyRemovedGroups, HashSet<Long> newlyRemovedUsers) {
        super(uiHelper);
        this.newlyRemovedGroups = newlyRemovedGroups;
        this.newlyRemovedUsers = newlyRemovedUsers;
    }

    protected RemoveAccessToAlbumAction(Parcel in) {
        super(in);
        newlyRemovedGroups = ParcelUtils.readLongSet(in);
        newlyRemovedUsers = ParcelUtils.readLongSet(in);
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        super.writeToParcel(dest, flags);
        ParcelUtils.writeLongSet(dest, newlyRemovedGroups);
        ParcelUtils.writeLongSet(dest, newlyRemovedUsers);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void onResult(AlertDialog dialog, Boolean positiveAnswer) {
        if (Boolean.TRUE == positiveAnswer) {
            F fragment = getUiHelper().getParent();
            PiwigoAlbum<CategoryItem, GalleryItem> galleryModel = fragment.getGalleryModel();
            getUiHelper().addActiveServiceCall(R.string.gallery_details_updating_progress_title, new AlbumRemovePermissionsResponseHandler(galleryModel.getContainerDetails(), newlyRemovedGroups, newlyRemovedUsers));
        } else {
            getUiHelper().getParent().updateViewFromModelAlbumPermissions();
        }
    }
}
