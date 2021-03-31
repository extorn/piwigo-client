package delit.piwigoclient.ui.tags.action;

import android.os.Parcel;
import android.os.Parcelable;
import android.util.Log;

import delit.libs.core.util.Logging;
import delit.piwigoclient.model.piwigo.Tag;
import delit.piwigoclient.piwigoApi.PiwigoResponseBufferingHandler;
import delit.piwigoclient.piwigoApi.handlers.TagsGetListResponseHandler;
import delit.piwigoclient.ui.common.FragmentUIHelper;
import delit.piwigoclient.ui.common.UIHelper;
import delit.piwigoclient.ui.tags.ViewTagFragment;

public class TagLoadedAction<F extends ViewTagFragment<F,FUIH>, FUIH extends FragmentUIHelper<FUIH, F>> extends UIHelper.Action<FUIH, F, TagsGetListResponseHandler.PiwigoGetTagsListRetrievedResponse> implements Parcelable {

    private static final String TAG = "TagLoadedAction";

    public TagLoadedAction(){}

    protected TagLoadedAction(Parcel in) {
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

    public static final Creator<TagLoadedAction<?,?>> CREATOR = new Creator<TagLoadedAction<?,?>>() {
        @Override
        public TagLoadedAction<?,?> createFromParcel(Parcel in) {
            return new TagLoadedAction<>(in);
        }

        @Override
        public TagLoadedAction<?,?>[] newArray(int size) {
            return new TagLoadedAction[size];
        }
    };

    @Override
    public boolean onSuccess(FUIH uiHelper, TagsGetListResponseHandler.PiwigoGetTagsListRetrievedResponse response) {
        boolean updated = false;
        F fragment = uiHelper.getParent();
        for (Tag t : response.getTags()) {
            if (t.getId() == fragment.getTagModel().getId()) {
                // tag has been located!
                fragment.reloadTagModel(t);
                updated = true;
            }
        }
        if (!updated) {
            //Something wierd is going on - this should never happen
            Logging.log(Log.ERROR, TAG, "Closing tag - tag was not available after refreshing session");
            fragment.getParentFragmentManager().popBackStack();
            return false;
        }
        fragment.loadAlbumResourcesPage(0);
        return false;
    }

    @Override
    public boolean onFailure(FUIH uiHelper, PiwigoResponseBufferingHandler.ErrorResponse response) {
        F fragment = uiHelper.getParent();
        Logging.log(Log.INFO, TAG, "removing from activity on piwigo error");
        fragment.getParentFragmentManager().popBackStack();
        return false;
    }
}
