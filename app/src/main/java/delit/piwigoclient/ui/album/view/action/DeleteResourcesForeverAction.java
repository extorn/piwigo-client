package delit.piwigoclient.ui.album.view.action;

import android.os.Parcel;
import android.os.Parcelable;

import androidx.appcompat.app.AlertDialog;

import java.util.HashSet;

import delit.libs.ui.util.ParcelUtils;
import delit.piwigoclient.R;
import delit.piwigoclient.model.piwigo.ResourceItem;
import delit.piwigoclient.piwigoApi.handlers.ImageDeleteResponseHandler;
import delit.piwigoclient.ui.album.view.BulkActionResourceProvider;
import delit.piwigoclient.ui.common.FragmentUIHelper;
import delit.piwigoclient.ui.common.dialogmessage.QuestionResultAdapter;
import delit.piwigoclient.ui.common.fragment.MyFragment;

public class DeleteResourcesForeverAction<F extends MyFragment<F,FUIH> & BulkActionResourceProvider,FUIH extends FragmentUIHelper<FUIH,F>, T extends ResourceItem> extends QuestionResultAdapter<FUIH,F> implements Parcelable {

    public static final Creator<DeleteResourcesForeverAction<?,?,?>> CREATOR = new Creator<DeleteResourcesForeverAction<?,?,?>>() {
        @Override
        public DeleteResourcesForeverAction<?,?,?> createFromParcel(Parcel in) {
            return new DeleteResourcesForeverAction<>(in);
        }

        @Override
        public DeleteResourcesForeverAction<?,?,?>[] newArray(int size) {
            return new DeleteResourcesForeverAction[size];
        }
    };
    private final HashSet<Long> selectedItemIds;
    private final HashSet<T> selectedItems;

    public DeleteResourcesForeverAction(FUIH uiHelper, HashSet<Long> selectedItemIds, HashSet<T> selectedItems) {
        super(uiHelper);
        this.selectedItemIds = selectedItemIds;
        this.selectedItems = selectedItems;
    }

    protected DeleteResourcesForeverAction(Parcel in) {
        super(in);
        selectedItemIds = ParcelUtils.readLongSet(in);
        selectedItems = ParcelUtils.readHashSet(in, ResourceItem.class.getClassLoader());
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        super.writeToParcel(dest, flags);
        ParcelUtils.writeLongSet(dest, selectedItemIds);
        ParcelUtils.writeSet(dest, selectedItems);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void onResult(AlertDialog dialog, Boolean positiveAnswer) {
        if (Boolean.TRUE == positiveAnswer) {
            BulkResourceActionData currentBulkResourceActionData = getUiHelper().getParent().getBulkResourceActionData();
            if (currentBulkResourceActionData != null) {
                currentBulkResourceActionData.trackMessageId(getUiHelper().addActiveServiceCall(R.string.progress_delete_resources, new ImageDeleteResponseHandler<>(selectedItemIds, selectedItems)));
            }
        }
    }
}
