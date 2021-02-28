package delit.piwigoclient.ui.album.view.action;

import android.os.Parcel;
import android.os.Parcelable;

import androidx.appcompat.app.AlertDialog;

import java.util.HashSet;

import delit.libs.ui.util.ParcelUtils;
import delit.piwigoclient.R;
import delit.piwigoclient.model.piwigo.ResourceItem;
import delit.piwigoclient.piwigoApi.handlers.ImageDeleteResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.ImageUpdateInfoResponseHandler;
import delit.piwigoclient.ui.album.view.AbstractViewAlbumFragment;
import delit.piwigoclient.ui.common.FragmentUIHelper;
import delit.piwigoclient.ui.common.dialogmessage.QuestionResultAdapter;

public class DeleteSharedResourcesAction<F extends AbstractViewAlbumFragment<F,FUIH>,FUIH extends FragmentUIHelper<FUIH,F>>  extends QuestionResultAdapter<FUIH, F> implements Parcelable {

    public static final Creator<DeleteSharedResourcesAction<?,?>> CREATOR = new Creator<DeleteSharedResourcesAction<?,?>>() {
        @Override
        public DeleteSharedResourcesAction<?,?> createFromParcel(Parcel in) {
            return new DeleteSharedResourcesAction<>(in);
        }

        @Override
        public DeleteSharedResourcesAction<?,?>[] newArray(int size) {
            return new DeleteSharedResourcesAction[size];
        }
    };
    private final HashSet<ResourceItem> sharedResources;

    public DeleteSharedResourcesAction(FUIH uiHelper, HashSet<ResourceItem> sharedResources) {
        super(uiHelper);
        this.sharedResources = sharedResources;
    }

    protected DeleteSharedResourcesAction(Parcel in) {
        super(in);
        sharedResources = ParcelUtils.readHashSet(in, ResourceItem.class.getClassLoader());
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        super.writeToParcel(dest, flags);
        ParcelUtils.writeSet(dest, sharedResources);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void onResult(AlertDialog dialog, Boolean positiveAnswer) {
        F fragment = getUiHelper().getParent();
        BulkResourceActionData deleteActionData = fragment.getBulkResourceActionData();

        if (Boolean.TRUE == positiveAnswer) {
            deleteActionData.trackMessageId(getUiHelper().addActiveServiceCall(R.string.progress_delete_resources, new ImageDeleteResponseHandler<>(deleteActionData.getSelectedItemIds(), deleteActionData.getSelectedItems())));
        } else if (Boolean.FALSE == positiveAnswer) {
            final long currentAlbumId = fragment.getGalleryModel().getContainerDetails().getId();
            HashSet<Long> itemIdsForPermanentDelete = new HashSet<>(deleteActionData.getSelectedItemIds());
            HashSet<ResourceItem> itemsForPermananentDelete = new HashSet<>(deleteActionData.getSelectedItems());
            for (ResourceItem item : sharedResources) {
                itemIdsForPermanentDelete.remove(item.getId());
                itemsForPermananentDelete.remove(item);
                item.getLinkedAlbums().remove(currentAlbumId);
                if(item.getLinkedAlbums().isEmpty()) {
                    getUiHelper().showOrQueueDialogMessage(R.string.alert_error, getContext().getString(R.string.alert_error_item_must_belong_to_at_least_one_album));
                } else {
                    deleteActionData.trackMessageId(getUiHelper().addActiveServiceCall(R.string.progress_unlink_resources, new ImageUpdateInfoResponseHandler<>(item, true)));
                }
            }
            //now we need to delete the rest.
            AbstractViewAlbumFragment.deleteResourcesFromServerForever(getUiHelper(), itemIdsForPermanentDelete, itemsForPermananentDelete);
        }
    }
}
