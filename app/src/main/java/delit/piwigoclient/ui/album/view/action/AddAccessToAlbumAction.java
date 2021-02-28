package delit.piwigoclient.ui.album.view.action;

import android.os.Parcel;
import android.os.Parcelable;

import androidx.appcompat.app.AlertDialog;

import java.util.HashSet;

import delit.libs.ui.util.ParcelUtils;
import delit.piwigoclient.R;
import delit.piwigoclient.model.piwigo.CategoryItem;
import delit.piwigoclient.piwigoApi.handlers.AlbumAddPermissionsResponseHandler;
import delit.piwigoclient.ui.album.view.AbstractViewAlbumFragment;
import delit.piwigoclient.ui.common.FragmentUIHelper;
import delit.piwigoclient.ui.common.dialogmessage.QuestionResultAdapter;

public class AddAccessToAlbumAction<F extends AbstractViewAlbumFragment<F,FUIH>,FUIH extends FragmentUIHelper<FUIH,F>>  extends QuestionResultAdapter<FUIH,F> implements Parcelable {

    public static final Creator<AddAccessToAlbumAction<?,?>> CREATOR = new Creator<AddAccessToAlbumAction<?,?>>() {
        @Override
        public AddAccessToAlbumAction<?,?> createFromParcel(Parcel in) {
            return new AddAccessToAlbumAction<>(in);
        }

        @Override
        public AddAccessToAlbumAction<?,?>[] newArray(int size) {
            return new AddAccessToAlbumAction[size];
        }
    };
    private final HashSet<Long> newlyAddedGroups;
    private final HashSet<Long> newlyAddedUsers;

    public AddAccessToAlbumAction(FUIH uiHelper, HashSet<Long> newlyAddedGroups, HashSet<Long> newlyAddedUsers) {
        super(uiHelper);
        this.newlyAddedGroups = newlyAddedGroups;
        this.newlyAddedUsers = newlyAddedUsers;
    }

    protected AddAccessToAlbumAction(Parcel in) {
        super(in);
        newlyAddedGroups = ParcelUtils.readLongSet(in);
        newlyAddedUsers = ParcelUtils.readLongSet(in);
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        super.writeToParcel(dest, flags);
        ParcelUtils.writeLongSet(dest, newlyAddedGroups);
        ParcelUtils.writeLongSet(dest, newlyAddedUsers);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void onResult(AlertDialog dialog, Boolean positiveAnswer) {
        F fragment = getUiHelper().getParent();
        final CategoryItem currentCategoryDetails = fragment.getGalleryModel().getContainerDetails();
        if (Boolean.TRUE == positiveAnswer) {
            getUiHelper().addActiveServiceCall(R.string.gallery_details_updating_progress_title, new AlbumAddPermissionsResponseHandler(currentCategoryDetails, newlyAddedGroups, newlyAddedUsers, true));
        } else {
            getUiHelper().addActiveServiceCall(R.string.gallery_details_updating_progress_title, new AlbumAddPermissionsResponseHandler(currentCategoryDetails, newlyAddedGroups, newlyAddedUsers, false));
        }
    }
}
