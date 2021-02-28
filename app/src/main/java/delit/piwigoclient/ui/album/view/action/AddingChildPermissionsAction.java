package delit.piwigoclient.ui.album.view.action;

import android.os.Parcelable;

import androidx.appcompat.app.AlertDialog;

import java.util.HashSet;

import delit.piwigoclient.R;
import delit.piwigoclient.model.piwigo.CategoryItem;
import delit.piwigoclient.piwigoApi.handlers.AlbumAddPermissionsResponseHandler;
import delit.piwigoclient.ui.album.view.AbstractViewAlbumFragment;
import delit.piwigoclient.ui.common.FragmentUIHelper;
import delit.piwigoclient.ui.common.dialogmessage.QuestionResultAdapter;

public class AddingChildPermissionsAction<F extends AbstractViewAlbumFragment<F,FUIH>,FUIH extends FragmentUIHelper<FUIH,F>> extends QuestionResultAdapter<FUIH, F> implements Parcelable {
    private HashSet<Long> newlyAddedGroups;
    private HashSet<Long> newlyAddedUsers;

    public AddingChildPermissionsAction(FUIH uiHelper, HashSet<Long> newlyAddedGroups, HashSet<Long> newlyAddedUsers) {
        super(uiHelper);
        this.newlyAddedGroups = newlyAddedGroups;
        this.newlyAddedUsers = newlyAddedUsers;
    }

    @Override
    public void onResult(AlertDialog dialog, Boolean positiveAnswer) {
        if (Boolean.TRUE == positiveAnswer) {
            F fragment = getUiHelper().getParent();
            final CategoryItem currentCategoryDetails = fragment.getGalleryModel().getContainerDetails();
            getUiHelper().addActiveServiceCall(R.string.gallery_details_updating_progress_title, new AlbumAddPermissionsResponseHandler(currentCategoryDetails, newlyAddedGroups, newlyAddedUsers, true));
        }
    }
}
