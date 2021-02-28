package delit.piwigoclient.ui.album.view.action;

import android.content.SharedPreferences;
import android.os.Parcel;
import android.os.Parcelable;

import androidx.appcompat.app.AlertDialog;

import delit.piwigoclient.business.ConnectionPreferences;
import delit.piwigoclient.ui.album.view.AbstractViewAlbumFragment;
import delit.piwigoclient.ui.common.FragmentUIHelper;
import delit.piwigoclient.ui.common.dialogmessage.QuestionResultAdapter;

public class BadHttpProtocolAction<F extends AbstractViewAlbumFragment<F,FUIH>,FUIH extends FragmentUIHelper<FUIH,F>> extends QuestionResultAdapter<FUIH, F> implements Parcelable {

    public static final Creator<BadHttpProtocolAction<?,?>> CREATOR = new Creator<BadHttpProtocolAction<?,?>>() {
        @Override
        public BadHttpProtocolAction<?,?> createFromParcel(Parcel in) {
            return new BadHttpProtocolAction<>(in);
        }

        @Override
        public BadHttpProtocolAction<?,?>[] newArray(int size) {
            return new BadHttpProtocolAction[size];
        }
    };
    private final ConnectionPreferences.ProfilePreferences connectionPreferences;

    public BadHttpProtocolAction(FUIH uiHelper, ConnectionPreferences.ProfilePreferences connectionPreferences) {
        super(uiHelper);
        this.connectionPreferences = connectionPreferences;
    }

    protected BadHttpProtocolAction(Parcel in) {
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
            connectionPreferences.setForceHttps(prefs, getContext(), true);
        }
    }
}
