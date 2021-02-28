package delit.piwigoclient.ui.slideshow.action;

import android.os.Parcel;
import android.os.Parcelable;

import androidx.appcompat.app.AlertDialog;

import delit.piwigoclient.R;
import delit.piwigoclient.model.piwigo.ResourceItem;
import delit.piwigoclient.piwigoApi.handlers.AlbumThumbnailUpdatedResponseHandler;
import delit.piwigoclient.ui.common.FragmentUIHelper;
import delit.piwigoclient.ui.common.dialogmessage.QuestionResultAdapter;
import delit.piwigoclient.ui.slideshow.item.AbstractSlideshowItemFragment;

public class UseAsAlbumThumbnailForParentAction<F extends AbstractSlideshowItemFragment<F,FUIH,T>, FUIH extends FragmentUIHelper<FUIH,F>,T extends ResourceItem> extends QuestionResultAdapter<FUIH,F> implements Parcelable {

    public UseAsAlbumThumbnailForParentAction(FUIH uiHelper) {
        super(uiHelper);
    }

    protected UseAsAlbumThumbnailForParentAction(Parcel in) {
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

    public static final Creator<UseAsAlbumThumbnailForParentAction<?,?,?>> CREATOR = new Creator<UseAsAlbumThumbnailForParentAction<?,?,?>>() {
        @Override
        public UseAsAlbumThumbnailForParentAction<?,?,?> createFromParcel(Parcel in) {
            return new UseAsAlbumThumbnailForParentAction<>(in);
        }

        @Override
        public UseAsAlbumThumbnailForParentAction<?,?,?>[] newArray(int size) {
            return new UseAsAlbumThumbnailForParentAction[size];
        }
    };

    @Override
    public void onResult(AlertDialog dialog, Boolean positiveAnswer) {
        if (Boolean.TRUE == positiveAnswer) {
            F parent = getUiHelper().getParent();
            ResourceItem model = parent.getModel();
            long albumId = model.getParentId();
            Long albumParentId = model.getParentageChain().size() > 1 ? model.getParentageChain().get(model.getParentageChain().size() - 2) : null;
            getUiHelper().addActiveServiceCall(R.string.progress_resource_details_updating, new AlbumThumbnailUpdatedResponseHandler(albumId, albumParentId, model.getId()));
        }
    }
}
