package delit.piwigoclient.ui.slideshow.action;

import android.os.Parcel;
import android.os.Parcelable;

import androidx.appcompat.app.AlertDialog;

import org.greenrobot.eventbus.EventBus;

import delit.piwigoclient.R;
import delit.piwigoclient.model.piwigo.ResourceItem;
import delit.piwigoclient.piwigoApi.handlers.ImageDeleteResponseHandler;
import delit.piwigoclient.ui.common.FragmentUIHelper;
import delit.piwigoclient.ui.common.dialogmessage.QuestionResultAdapter;
import delit.piwigoclient.ui.events.trackable.AlbumItemActionStartedEvent;
import delit.piwigoclient.ui.slideshow.item.AbstractSlideshowItemFragment;

public class OnDeleteItemAction<F extends AbstractSlideshowItemFragment<F,FUIH,T>, FUIH extends FragmentUIHelper<FUIH,F>, T extends ResourceItem> extends QuestionResultAdapter<FUIH,F> implements Parcelable {

    public OnDeleteItemAction(FUIH uiHelper) {
        super(uiHelper);
    }

    protected OnDeleteItemAction(Parcel in) {
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

    public static final Creator<OnDeleteItemAction<?,?,?>> CREATOR = new Creator<OnDeleteItemAction<?,?,?>>() {
        @Override
        public OnDeleteItemAction<?,?,?> createFromParcel(Parcel in) {
            return new OnDeleteItemAction<>(in);
        }

        @Override
        public OnDeleteItemAction<?,?,?>[] newArray(int size) {
            return new OnDeleteItemAction[size];
        }
    };

    @Override
    public void onResult(AlertDialog dialog, Boolean positiveAnswer) {
        if (Boolean.TRUE == positiveAnswer) {
            F fragment = getUiHelper().getParent();
            ResourceItem model = fragment.getModel();
            AlbumItemActionStartedEvent event = new AlbumItemActionStartedEvent(model);
            getUiHelper().setTrackingRequest(event.getActionId());
            EventBus.getDefault().post(event);
            getUiHelper().addActiveServiceCall(R.string.progress_delete_resource, new ImageDeleteResponseHandler<>(model));
        }
    }
}
