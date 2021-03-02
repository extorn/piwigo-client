package delit.piwigoclient.ui.tags.action;

import android.os.Parcel;
import android.os.Parcelable;

import androidx.appcompat.app.AlertDialog;

import java.util.HashSet;

import delit.piwigoclient.R;
import delit.piwigoclient.business.ConnectionPreferences;
import delit.piwigoclient.model.piwigo.PiwigoSessionDetails;
import delit.piwigoclient.model.piwigo.ResourceItem;
import delit.piwigoclient.piwigoApi.handlers.ImageUpdateInfoResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.PluginUserTagsUpdateResourceTagsListResponseHandler;
import delit.piwigoclient.ui.common.FragmentUIHelper;
import delit.piwigoclient.ui.common.dialogmessage.QuestionResultAdapter;
import delit.piwigoclient.ui.tags.ViewTagFragment;

public class OnDeleteTagsAction<F extends ViewTagFragment<F,FUIH>, FUIH extends FragmentUIHelper<FUIH, F>> extends QuestionResultAdapter<FUIH,F> implements Parcelable {

    public OnDeleteTagsAction(FUIH uiHelper) {
        super(uiHelper);
    }

    protected OnDeleteTagsAction(Parcel in) {
        super(in);
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        super.writeToParcel(dest, flags);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Creator<OnDeleteTagsAction<?,?>> CREATOR = new Creator<OnDeleteTagsAction<?,?>>() {
        @Override
        public OnDeleteTagsAction<?,?> createFromParcel(Parcel in) {
            return new OnDeleteTagsAction<>(in);
        }

        @Override
        public OnDeleteTagsAction<?,?>[] newArray(int size) {
            return new OnDeleteTagsAction[size];
        }
    };

    @Override
    public void onResult(AlertDialog dialog, Boolean positiveAnswer) {
        F fragment = getUiHelper().getParent();
        fragment.getViewAdapter().toggleItemSelection();
        if (Boolean.TRUE == positiveAnswer) {
            HashSet<Long> itemIdsForPermanentDelete = new HashSet<>(fragment.getDeleteActionData().getSelectedItemIds());
            HashSet<ResourceItem> itemsForPermanentDelete = new HashSet<>(fragment.getDeleteActionData().getSelectedItems());
            fragment.deleteResourcesFromServerForever(itemIdsForPermanentDelete, itemsForPermanentDelete);
        } else if (Boolean.FALSE == positiveAnswer) { // Negative answer
            PiwigoSessionDetails sessionDetails = PiwigoSessionDetails.getInstance(ConnectionPreferences.getActiveProfile());
            boolean allowTagEdit = !fragment.isAppInReadOnlyMode() && sessionDetails != null && sessionDetails.isUseUserTagPluginForUpdate();
            for (ResourceItem item : fragment.getDeleteActionData().getSelectedItems()) {
                item.getTags().remove(fragment.getCurrentTag());
                if (allowTagEdit) {
                    getUiHelper().addActiveServiceCall(R.string.progress_untag_resources_pattern, new PluginUserTagsUpdateResourceTagsListResponseHandler<>(item));
                } else {
                    getUiHelper().addActiveServiceCall(R.string.progress_untag_resources_pattern, new ImageUpdateInfoResponseHandler<>(item, true));
                }
            }
        } else {
            // Neutral (cancel button) - do nothing
        }
    }
}
