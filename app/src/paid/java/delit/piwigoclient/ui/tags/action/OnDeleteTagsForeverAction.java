package delit.piwigoclient.ui.tags.action;

import android.os.Parcel;
import android.os.Parcelable;

import androidx.appcompat.app.AlertDialog;

import java.util.HashSet;

import delit.libs.ui.util.ParcelUtils;
import delit.piwigoclient.R;
import delit.piwigoclient.model.piwigo.ResourceItem;
import delit.piwigoclient.piwigoApi.handlers.ImageDeleteResponseHandler;
import delit.piwigoclient.ui.common.FragmentUIHelper;
import delit.piwigoclient.ui.common.dialogmessage.QuestionResultAdapter;
import delit.piwigoclient.ui.tags.ViewTagFragment;

public class OnDeleteTagsForeverAction<F extends ViewTagFragment<F,FUIH>, FUIH extends FragmentUIHelper<FUIH, F>> extends QuestionResultAdapter<FUIH, F> implements Parcelable {

    private final HashSet<Long> selectedItemIds;
    private final HashSet<? extends ResourceItem> selectedItems;

    public OnDeleteTagsForeverAction(FUIH uiHelper, HashSet<Long> selectedItemIds, HashSet<? extends ResourceItem> selectedItems) {
        super(uiHelper);
        this.selectedItemIds = selectedItemIds;
        this.selectedItems = selectedItems;
    }

    protected OnDeleteTagsForeverAction(Parcel in) {
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

    public static final Creator<OnDeleteTagsForeverAction<?,?>> CREATOR = new Creator<OnDeleteTagsForeverAction<?,?>>() {
        @Override
        public OnDeleteTagsForeverAction<?,?> createFromParcel(Parcel in) {
            return new OnDeleteTagsForeverAction<>(in);
        }

        @Override
        public OnDeleteTagsForeverAction<?,?>[] newArray(int size) {
            return new OnDeleteTagsForeverAction[size];
        }
    };

    @Override
    public void onResult(AlertDialog dialog, Boolean positiveAnswer) {
        if (Boolean.TRUE == positiveAnswer) {
            getUiHelper().addActiveServiceCall(R.string.progress_delete_resources, new ImageDeleteResponseHandler<>(selectedItemIds, selectedItems));
        }
    }
}
