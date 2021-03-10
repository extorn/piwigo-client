package delit.piwigoclient.piwigoApi;

import android.os.Parcel;
import android.os.Parcelable;
import android.util.Log;

import androidx.appcompat.app.AlertDialog;

import delit.libs.core.util.Logging;
import delit.piwigoclient.piwigoApi.handlers.AbstractPiwigoDirectResponseHandler;
import delit.piwigoclient.ui.common.UIHelper;
import delit.piwigoclient.ui.common.dialogmessage.QuestionResultAdapter;

public final class ErrorRetryQuestionResultHandler<P extends UIHelper<P,T>, T> extends QuestionResultAdapter<P,T> implements Parcelable {
    private static final String TAG = "ErrorRetryQuestionResultHandler";
    //TODO These items are not really sensible to cache if the app is serialized as such. Instead we check if they're null and don't try to use them.
    // Find some way of dealing with this situation in a more pleasant way.
    private final AbstractPiwigoDirectResponseHandler handler;
    private final PiwigoResponseBufferingHandler.RemoteErrorResponse<?> errorResponse;
    private final long handlerId;

    public ErrorRetryQuestionResultHandler(P uiHelper, AbstractPiwigoDirectResponseHandler handler, PiwigoResponseBufferingHandler.RemoteErrorResponse<?> errorResponse, long handlerId) {
        super(uiHelper);
        this.handlerId = handlerId;
        this.handler = handler;
        this.errorResponse = errorResponse;
    }

    protected ErrorRetryQuestionResultHandler(Parcel in) {
        super(in);
        handlerId = in.readLong();
        handler = null;
        errorResponse = null;

    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        super.writeToParcel(dest, flags);
        dest.writeLong(handlerId);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Creator<ErrorRetryQuestionResultHandler<?,?>> CREATOR = new Creator<ErrorRetryQuestionResultHandler<?,?>>() {
        @Override
        public ErrorRetryQuestionResultHandler<?,?> createFromParcel(Parcel in) {
            return new ErrorRetryQuestionResultHandler<>(in);
        }

        @Override
        public ErrorRetryQuestionResultHandler<?,?>[] newArray(int size) {
            return new ErrorRetryQuestionResultHandler<?,?>[size];
        }
    };

    @Override
    public void onResult(AlertDialog dialog, Boolean positiveAnswer) {
        if (Boolean.TRUE.equals(positiveAnswer)) {
            if (handler == null) {
                Logging.log(Log.ERROR, TAG, "attempt to process alert message for handler after app pause resume (handler is now not available)");
                Logging.recordException(new NullPointerException("unable to handle positive dialog answer"));
            } else {
                if (handler.runInBackground()) {
                    getUiHelper().addBackgroundServiceCall(handler.getMessageId());
                } else {
                    getUiHelper().addActiveServiceCall(handler);
                }
                handler.rerun(dialog.getContext().getApplicationContext());
            }

        } else {
            BasicPiwigoResponseListener<?,?> listener = (BasicPiwigoResponseListener<?,?>) PiwigoResponseBufferingHandler.getDefault().getRegisteredHandler(handlerId);
            if (listener == null) {
                Logging.log(Log.ERROR, TAG, "attempt to process alert message for handler after app pause resume (listener is now not available)");
                Logging.recordException(new NullPointerException("unable to handle negative dialog answer"));
            } else {
                listener.onAfterHandlePiwigoResponse(errorResponse);
            }
        }
    }
}
