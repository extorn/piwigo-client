package delit.piwigoclient.ui.common.dialogmessage;

import android.os.Parcel;
import android.os.Parcelable;

import androidx.appcompat.app.AlertDialog;

import delit.piwigoclient.business.ConnectionPreferences;
import delit.piwigoclient.ui.common.UIHelper;

public class UnexpectedUriQuestionResult<P extends UIHelper<P,T>,T>  extends QuestionResultAdapter<P,T>implements Parcelable {


    public UnexpectedUriQuestionResult(P uiHelper) {
        super(uiHelper);
    }

    protected UnexpectedUriQuestionResult(Parcel in) {
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

    public static final Creator<UnexpectedUriQuestionResult<?,?>> CREATOR = new Creator<UnexpectedUriQuestionResult<?,?>>() {
        @Override
        public UnexpectedUriQuestionResult<?,?> createFromParcel(Parcel in) {
            return new UnexpectedUriQuestionResult<>(in);
        }

        @Override
        public UnexpectedUriQuestionResult<?,?>[] newArray(int size) {
            return new UnexpectedUriQuestionResult<?,?>[size];
        }
    };

    @Override
    public void onResult(AlertDialog dialog, Boolean positiveAnswer) {
        if (Boolean.FALSE.equals(positiveAnswer)) {
            ConnectionPreferences.getActiveProfile().setWarnInternalUriExposed(getUiHelper().getPrefs(), getUiHelper().getAppContext(), false);
        }
    }
}
