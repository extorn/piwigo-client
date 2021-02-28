package delit.piwigoclient.ui.album.view.action;

import android.content.SharedPreferences;
import android.os.Parcel;
import android.os.Parcelable;

import androidx.appcompat.app.AlertDialog;

import delit.piwigoclient.R;
import delit.piwigoclient.business.ConnectionPreferences;
import delit.piwigoclient.piwigoApi.HttpConnectionCleanup;
import delit.piwigoclient.ui.album.view.AbstractViewAlbumFragment;
import delit.piwigoclient.ui.common.FragmentUIHelper;
import delit.piwigoclient.ui.common.dialogmessage.QuestionResultAdapter;

public class BadRequestRedirectionAction<F extends AbstractViewAlbumFragment<F,FUIH>,FUIH extends FragmentUIHelper<FUIH,F>> extends QuestionResultAdapter<FUIH, F> implements Parcelable {

    public static final Creator<BadRequestRedirectionAction<?,?>> CREATOR = new Creator<BadRequestRedirectionAction<?,?>>() {
        @Override
        public BadRequestRedirectionAction<?,?> createFromParcel(Parcel in) {
            return new BadRequestRedirectionAction<>(in);
        }

        @Override
        public BadRequestRedirectionAction<?,?>[] newArray(int size) {
            return new BadRequestRedirectionAction[size];
        }
    };
    private final ConnectionPreferences.ProfilePreferences connectionPreferences;

    public BadRequestRedirectionAction(FUIH uiHelper, ConnectionPreferences.ProfilePreferences connectionPreferences) {
        super(uiHelper);
        this.connectionPreferences = connectionPreferences;
    }

    protected BadRequestRedirectionAction(Parcel in) {
        super(in);
        connectionPreferences = in.readParcelable(ConnectionPreferences.ProfilePreferences.class.getClassLoader());
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        super.writeToParcel(dest, flags);
        dest.writeParcelable(connectionPreferences, flags);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void onResult(AlertDialog dialog, Boolean positiveAnswer) {
        if (positiveAnswer != null && positiveAnswer) {
            F fragment = getUiHelper().getParent();
            SharedPreferences prefs = fragment.getPrefs();
            connectionPreferences.setFollowHttpRedirects(prefs, getContext(), true);
            //hard reset all http clients! No other solution sadly.
            getUiHelper().addActiveServiceCall(getContext().getString(R.string.loading_new_server_configuration), new HttpConnectionCleanup(connectionPreferences, getContext()).start(), "httpCleanup");
        }
    }
}
