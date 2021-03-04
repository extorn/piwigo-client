package delit.piwigoclient.ui.common.dialogmessage;

import android.content.Context;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.Log;
import android.view.ViewGroup;

import androidx.annotation.LayoutRes;
import androidx.appcompat.app.AlertDialog;

import delit.libs.core.util.Logging;
import delit.piwigoclient.ui.common.UIHelper;

public class QuestionResultAdapter<P extends UIHelper<P,T>,T> implements QuestionResultListener<P,T>, Parcelable {

    private static final String TAG = "QuestionResultAdapter";
    private QuestionResultListener<P,T> chainedListener;

    private P uiHelper;

    public QuestionResultAdapter(P uiHelper) {
        this.uiHelper = uiHelper;
    }

    protected QuestionResultAdapter(Parcel in) {
        chainedListener = in.readParcelable(QuestionResultListener.class.getClassLoader());
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeParcelable(chainedListener, flags);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Creator<QuestionResultAdapter<?,?>> CREATOR = new Creator<QuestionResultAdapter<?,?>>() {
        @Override
        public QuestionResultAdapter<?,?> createFromParcel(Parcel in) {
            return new QuestionResultAdapter<>(in);
        }

        @Override
        public QuestionResultAdapter<?,?>[] newArray(int size) {
            return new QuestionResultAdapter[size];
        }
    };

    public T getParent() {
        return getUiHelper().getParent();
    }

    public P getUiHelper() {
        return uiHelper;
    }

    @Override
    public void setUiHelper(P uiHelper) {
        this.uiHelper = uiHelper;
    }

    public Context getContext() {
        return uiHelper.getAppContext();
    }

    @Override
    public void onShow(AlertDialog alertDialog) {
    }

    @Override
    public void onBeforeShow(AlertDialog alertDialog) {
    }

    @Override
    public void onResultInternal(AlertDialog dialog, Boolean positiveAnswer) {
        onResult(dialog, positiveAnswer);
        if (chainedListener != null) {
            chainedListener.onResult(dialog, positiveAnswer);
        }
    }

    @Override
    public void onResult(AlertDialog dialog, Boolean positiveAnswer) {
        if(null == positiveAnswer) {
            onNeutralResult(dialog);
        } else if(positiveAnswer) {
            onPositiveResult(dialog);
        } else {
            onNegativeResult(dialog);
        }
    }

    protected void onNeutralResult(AlertDialog dialog) {
    }

    protected void onNegativeResult(AlertDialog dialog) {
    }

    protected void onPositiveResult(AlertDialog dialog) {
    }

    @Override
    public void onDismiss(AlertDialog dialog) {
    }

    @Override
    public void onPopulateDialogView(ViewGroup dialogView, @LayoutRes int layoutId) {
        Logging.log(Log.DEBUG, TAG, "Unsupported layout id for dialog message : " + layoutId);
    }

    @Override
    public void chainResult(QuestionResultListener<P,T> listener) {
        chainedListener = listener;
    }

}
