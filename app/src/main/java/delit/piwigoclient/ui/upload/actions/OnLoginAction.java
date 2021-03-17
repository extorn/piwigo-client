package delit.piwigoclient.ui.upload.actions;

import android.os.Parcel;
import android.os.Parcelable;

import com.google.android.material.button.MaterialButton;

import org.greenrobot.eventbus.EventBus;

import delit.piwigoclient.piwigoApi.handlers.LoginResponseHandler;
import delit.piwigoclient.ui.common.FragmentUIHelper;
import delit.piwigoclient.ui.common.UIHelper;
import delit.piwigoclient.ui.events.trackable.FileSelectionCompleteEvent;
import delit.piwigoclient.ui.upload.AbstractUploadFragment;

public class OnLoginAction<F extends AbstractUploadFragment<F,FUIH>,FUIH extends FragmentUIHelper<FUIH,F>>  extends UIHelper.Action<FUIH,F, LoginResponseHandler.PiwigoOnLoginResponse> implements Parcelable {
    public OnLoginAction(){}

    protected OnLoginAction(Parcel in) {
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

    public static final Creator<OnLoginAction<?,?>> CREATOR = new Creator<OnLoginAction<?,?>>() {
        @Override
        public OnLoginAction<?,?> createFromParcel(Parcel in) {
            return new OnLoginAction<>(in);
        }

        @Override
        public OnLoginAction<?,?>[] newArray(int size) {
            return new OnLoginAction<?,?>[size];
        }
    };

    @Override
    public boolean onSuccess(FUIH uiHelper, LoginResponseHandler.PiwigoOnLoginResponse response) {
        F fragment = uiHelper.getParent();
        if(fragment != null) {
            MaterialButton button = fragment.getFileSelectButton();
            if(button != null) { // will be null if fragment not visible or initialising perhaps.
                button.setEnabled(true);
            }
            FileSelectionCompleteEvent evt = EventBus.getDefault().getStickyEvent(FileSelectionCompleteEvent.class);
            if (evt != null) {
                fragment.onEvent(evt);
            }
        }
        return super.onSuccess(uiHelper, response);
    }
}
