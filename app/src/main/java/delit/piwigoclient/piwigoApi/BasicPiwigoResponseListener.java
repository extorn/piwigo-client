package delit.piwigoclient.piwigoApi;

import android.app.Activity;
import android.os.Bundle;
import android.preference.DialogPreference;
import android.util.Log;
import android.view.ViewGroup;

import com.crashlytics.android.Crashlytics;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import delit.piwigoclient.R;
import delit.piwigoclient.piwigoApi.handlers.AbstractPiwigoDirectResponseHandler;
import delit.piwigoclient.ui.common.UIHelper;

/**
 * Created by gareth on 15/10/17.
 */

public class BasicPiwigoResponseListener implements PiwigoResponseBufferingHandler.PiwigoResponseListener {

    private static final String TAG = "BasicPiwigoLsnr";
    private static final String HANDLER_ID = "handlerId";
    private long handlerId;
    private UIHelper uiHelper;
    private Object parent;


    public BasicPiwigoResponseListener() {
        handlerId = PiwigoResponseBufferingHandler.getNextHandlerId();
    }

    @Override
    public long getHandlerId() {
        return handlerId;
    }

    public void switchHandlerId(long newHandlerId) {
        handlerId = newHandlerId;
    }

    public void withUiHelper(DialogPreference parent, UIHelper uiHelper) {
        this.uiHelper = uiHelper;
        this.parent = parent;
    }

    public void withUiHelper(ViewGroup parent, UIHelper uiHelper) {
        this.uiHelper = uiHelper;
        this.parent = parent;
    }

    public void withUiHelper(AppCompatActivity parent, UIHelper uiHelper) {
        this.uiHelper = uiHelper;
        this.parent = parent;
    }

    public void withUiHelper(Fragment parent, UIHelper uiHelper) {
        this.uiHelper = uiHelper;
        this.parent = parent;
    }

    public void onSaveInstanceState(Bundle savedInstanceState) {
        savedInstanceState.putLong(HANDLER_ID, handlerId);
    }

    public void onRestoreInstanceState(Bundle savedInstanceState) {
        handlerId = savedInstanceState.getLong(HANDLER_ID);
    }

    protected void showOrQueueMessage(int title, String message) {
        uiHelper.showOrQueueDialogMessage(title, message);
    }

    private void showOrQueueRetryDialogMessageWithDetail(final PiwigoResponseBufferingHandler.RemoteErrorResponse errorResponse, int title, String msg, String detail) {
        handleErrorRetryPossible(errorResponse, title, msg, detail);
    }

    private void showOrQueueRetryDialogMessage(final PiwigoResponseBufferingHandler.BasePiwigoResponse response, int title, String msg) {
        if (response instanceof PiwigoResponseBufferingHandler.RemoteErrorResponse) {
            final PiwigoResponseBufferingHandler.RemoteErrorResponse errorResponse = (PiwigoResponseBufferingHandler.RemoteErrorResponse) response;
            handleErrorRetryPossible(errorResponse, title, msg, null);

        } else {
            handleErrorRetryNotPossible(response, title, msg, null);
        }
    }

    protected void handleErrorRetryNotPossible(PiwigoResponseBufferingHandler.BasePiwigoResponse response, int title, String msg, String detail) {
        if(detail == null) {
            showOrQueueMessage(title, msg);
        } else {
            showOrQueueMessage(title, msg + "\n\n" + detail);
        }
    }

    private static class ErrorRetryQuestionResultHandler extends UIHelper.QuestionResultAdapter {
        private final transient AbstractPiwigoDirectResponseHandler handler;
        private final transient PiwigoResponseBufferingHandler.RemoteErrorResponse errorResponse;
        private final long handlerId;

        public ErrorRetryQuestionResultHandler(UIHelper uiHelper, AbstractPiwigoDirectResponseHandler handler, PiwigoResponseBufferingHandler.RemoteErrorResponse errorResponse, long handlerId) {
            super(uiHelper);
            this.handlerId = handlerId;
            this.handler = handler;
            this.errorResponse = errorResponse;
        }

        @Override
        public void onResult(AlertDialog dialog, Boolean positiveAnswer) {
            //TODO fix NPE exception - will occur here because handler and errorResponse are both transient as non serializable.
            if (Boolean.TRUE.equals(positiveAnswer)) {
                if (handler == null) {
                    Crashlytics.log(Log.ERROR, TAG, "attempt to process alert message for handler after app pause resume (handler is now not available)");
                    Crashlytics.logException(new NullPointerException("unable to handle positive dialog answer"));
                } else {
                    if (handler.runInBackground()) {
                        getUiHelper().addBackgroundServiceCall(handler.getMessageId());
                    } else {
                        getUiHelper().addActiveServiceCall(handler);
                    }
                    handler.rerun(dialog.getContext().getApplicationContext());
                }

            } else {
                BasicPiwigoResponseListener listener = (BasicPiwigoResponseListener) PiwigoResponseBufferingHandler.getDefault().getRegisteredHandler(handlerId);
                if (listener == null) {
                    Crashlytics.log(Log.ERROR, TAG, "attempt to process alert message for handler after app pause resume (listener is now not available)");
                    Crashlytics.logException(new NullPointerException("unable to handle negative dialog answer"));
                } else {
                    listener.onAfterHandlePiwigoResponse(errorResponse);
                }
            }
        }
    }

    protected void handleErrorRetryPossible(final PiwigoResponseBufferingHandler.RemoteErrorResponse errorResponse, int title, String msg, String detail) {
        final AbstractPiwigoDirectResponseHandler handler = errorResponse.getHttpResponseHandler();
        UIHelper.QuestionResultListener dialogListener = new ErrorRetryQuestionResultHandler(uiHelper, handler, errorResponse, handlerId);

        if(detail == null) {
            uiHelper.showOrQueueDialogQuestion(title, msg, R.string.button_cancel, R.string.button_retry, dialogListener);
        } else {
            uiHelper.showOrQueueEnhancedDialogQuestion(title, msg, detail, R.string.button_cancel, R.string.button_retry, dialogListener);
        }
    }

    public void onBeforeHandlePiwigoResponse(PiwigoResponseBufferingHandler.Response response) {
    }

    public void onBeforeHandlePiwigoResponseInListener(PiwigoResponseBufferingHandler.Response response) {
    }

    public <T extends PiwigoResponseBufferingHandler.Response> void onAfterHandlePiwigoResponse(T response) {
    }


    @Override
    public final void handlePiwigoResponse(PiwigoResponseBufferingHandler.Response response) {

        onBeforeHandlePiwigoResponse(response);

        UIHelper.Action action = uiHelper.getActionOnResponse(response);
        boolean runListenerHandlerCode = true;
        if(action != null) {
            if(response instanceof PiwigoResponseBufferingHandler.ErrorResponse) {
                runListenerHandlerCode = action.onFailure(uiHelper, (PiwigoResponseBufferingHandler.ErrorResponse) response);
            } else {
                runListenerHandlerCode = action.onSuccess(uiHelper, response);
            }
        }

        uiHelper.onServiceCallComplete(response);

        if(runListenerHandlerCode) {
            onBeforeHandlePiwigoResponseInListener(response);
            if (response instanceof PiwigoResponseBufferingHandler.PiwigoHttpErrorResponse) {
                handlePiwigoHttpErrorResponse((PiwigoResponseBufferingHandler.PiwigoHttpErrorResponse) response);
            } else if (response instanceof PiwigoResponseBufferingHandler.PiwigoUnexpectedReplyErrorResponse) {
                handlePiwigoUnexpectedReplyErrorResponse((PiwigoResponseBufferingHandler.PiwigoUnexpectedReplyErrorResponse) response);
            } else if (response instanceof PiwigoResponseBufferingHandler.PiwigoServerErrorResponse) {
                handlePiwigoServerErrorResponse((PiwigoResponseBufferingHandler.PiwigoServerErrorResponse) response);
            } else if(response instanceof PiwigoResponseBufferingHandler.UrlErrorResponse) {
                handleUrlErrorResponse((PiwigoResponseBufferingHandler.UrlErrorResponse) response);
            }
            if (!(response instanceof PiwigoResponseBufferingHandler.RemoteErrorResponse)) {
                // don't call user code if we may be re-trying. The outcome is not yet know.
                onAfterHandlePiwigoResponse(response);
            }
        }
    }

    private void handleUrlErrorResponse(PiwigoResponseBufferingHandler.UrlErrorResponse msg) {
        if (msg.getStatusCode() < 0) {
            showOrQueueRetryDialogMessageWithDetail(msg, R.string.alert_title_error_talking_to_server, msg.getErrorMessage(), msg.getResponseBody());
        } else if (msg.getStatusCode() == 0) {
            showOrQueueRetryDialogMessageWithDetail(msg, R.string.alert_title_error_connecting_to_server, msg.getErrorMessage(), msg.getResponseBody());
        } else {
            showOrQueueRetryDialogMessageWithDetail(msg, R.string.alert_title_server_error, uiHelper.getContext().getString(R.string.alert_server_error_pattern, msg.getStatusCode(), msg.getErrorMessage()), msg.getResponseBody());
        }
    }

    protected void handlePiwigoServerErrorResponse(PiwigoResponseBufferingHandler.PiwigoServerErrorResponse msg) {
        showOrQueueRetryDialogMessage(msg, R.string.alert_title_error_handling_response, uiHelper.getContext().getString(R.string.alert_error_handling_response_pattern, msg.getPiwigoErrorCode(), msg.getPiwigoErrorMessage()));
    }

    protected void handlePiwigoHttpErrorResponse(PiwigoResponseBufferingHandler.PiwigoHttpErrorResponse msg) {
        if (msg.getStatusCode() < 0) {
            showOrQueueRetryDialogMessageWithDetail(msg, R.string.alert_title_error_talking_to_server, msg.getErrorMessage(), msg.getErrorDetail() + msg.getResponse());
        } else if (msg.getStatusCode() == 0) {
            showOrQueueRetryDialogMessageWithDetail(msg, R.string.alert_title_error_connecting_to_server, msg.getErrorMessage(), msg.getErrorDetail() + msg.getResponse());
        } else {
            showOrQueueRetryDialogMessageWithDetail(msg, R.string.alert_title_server_error, uiHelper.getContext().getString(R.string.alert_server_error_pattern, msg.getStatusCode(), msg.getErrorMessage()), msg.getErrorDetail() + msg.getResponse());
        }
    }

    protected void handlePiwigoUnexpectedReplyErrorResponse(PiwigoResponseBufferingHandler.PiwigoUnexpectedReplyErrorResponse msg) {
        switch (msg.getRequestOutcome()) {
            case PiwigoResponseBufferingHandler.PiwigoUnexpectedReplyErrorResponse.OUTCOME_UNKNOWN:
                showOrQueueRetryDialogMessage(msg, R.string.alert_title_error_handling_response, uiHelper.getContext().getString(R.string.alert_error_handling_response_pattern, -1, msg.getRawResponse()));
                break;
            case PiwigoResponseBufferingHandler.PiwigoUnexpectedReplyErrorResponse.OUTCOME_FAILED:
                showOrQueueRetryDialogMessage(msg, R.string.alert_title_error_handling_response, uiHelper.getContext().getString(R.string.alert_error_handling_response_pattern, -1, msg.getRawResponse()));
                break;
            case PiwigoResponseBufferingHandler.PiwigoUnexpectedReplyErrorResponse.OUTCOME_SUCCESS:
                showOrQueueMessage(R.string.alert_title_error_handling_response, uiHelper.getContext().getString(R.string.alert_error_handling_response_pattern, -1, msg.getRawResponse()));
        }
    }

    @Override
    public boolean canHandlePiwigoResponseNow(PiwigoResponseBufferingHandler.Response response) {
        // If this fragment is not presently active, delay processing the response till it is.
        boolean retVal;
        if (parent instanceof Fragment) {
            // If this fragment is not presently active, delay processing the response till it is.
            Activity activity = ((Fragment) parent).getActivity();
            retVal = ((Fragment) parent).isAdded() && activity != null;
        } else if (parent instanceof AppCompatActivity) {
            retVal = !((AppCompatActivity) parent).isFinishing();
        } else if (parent instanceof ViewGroup) {
            retVal = ((ViewGroup) parent).isShown();
        } else if (parent instanceof DialogPreference) {
            retVal = ((DialogPreference) parent).getDialog() != null;
        } else if (parent == null) {
            // this listener has become detached from the UI.
            retVal = false;
        } else {
            throw new IllegalArgumentException("Unsupported parent type " + parent);
        }
        return retVal;
    }
}
