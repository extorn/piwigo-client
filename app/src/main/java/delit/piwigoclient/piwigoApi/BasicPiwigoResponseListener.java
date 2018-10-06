package delit.piwigoclient.piwigoApi;

import android.app.Activity;
import android.support.v7.app.AlertDialog;
import android.os.Bundle;
import android.preference.DialogPreference;
import android.support.v4.app.Fragment;
import android.support.v7.app.AppCompatActivity;
import android.view.ViewGroup;

import delit.piwigoclient.R;
import delit.piwigoclient.piwigoApi.handlers.AbstractPiwigoDirectResponseHandler;
import delit.piwigoclient.ui.common.UIHelper;

/**
 * Created by gareth on 15/10/17.
 */

public class BasicPiwigoResponseListener implements PiwigoResponseBufferingHandler.PiwigoResponseListener {

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

    private void showOrQueueRetryDialogMessageWithDetail(final PiwigoResponseBufferingHandler.BasePiwigoResponse response, int title, String msg, String detail) {
        if (response instanceof PiwigoResponseBufferingHandler.RemoteErrorResponse) {
            final PiwigoResponseBufferingHandler.RemoteErrorResponse errorResponse = (PiwigoResponseBufferingHandler.RemoteErrorResponse) response;
            handleErrorRetryPossible(errorResponse, title, msg, detail);

        } else {
            handleErrorRetryNotPossible(response, title, msg, detail);
        }
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

    protected void handleErrorRetryPossible(final PiwigoResponseBufferingHandler.RemoteErrorResponse errorResponse, int title, String msg, String detail) {
        final AbstractPiwigoDirectResponseHandler handler = errorResponse.getHttpResponseHandler();

        UIHelper.QuestionResultListener dialogListener = new UIHelper.QuestionResultAdapter() {

            @Override
            public void onResult(AlertDialog dialog, Boolean positiveAnswer) {
                if (Boolean.TRUE.equals(positiveAnswer)) {
                    uiHelper.addActiveServiceCall(handler.getMessageId());
                    if (handler.isRunAsync()) {
                        handler.invokeAsyncAgain(dialog.getContext().getApplicationContext());
                    } else {
                        handler.invokeAgain(dialog.getContext().getApplicationContext());
                    }
                } else {
                    onAfterHandlePiwigoResponse(errorResponse);
                }
            }
        };

        if(detail == null) {
            uiHelper.showOrQueueDialogQuestion(title, msg, R.string.button_cancel, R.string.button_retry, dialogListener);
        } else {
            uiHelper.showOrQueueEnhancedDialogQuestion(title, msg, detail, R.string.button_cancel, R.string.button_retry, dialogListener);
        }
    }

    public void onBeforeHandlePiwigoResponse(PiwigoResponseBufferingHandler.Response response) {
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
            if (response instanceof PiwigoResponseBufferingHandler.PiwigoHttpErrorResponse) {
                handlePiwigoHttpErrorResponse((PiwigoResponseBufferingHandler.PiwigoHttpErrorResponse) response);
            } else if (response instanceof PiwigoResponseBufferingHandler.PiwigoUnexpectedReplyErrorResponse) {
                handlePiwigoUnexpectedReplyErrorResponse((PiwigoResponseBufferingHandler.PiwigoUnexpectedReplyErrorResponse) response);
            } else if (response instanceof PiwigoResponseBufferingHandler.PiwigoServerErrorResponse) {
                handlePiwigoServerErrorResponse((PiwigoResponseBufferingHandler.PiwigoServerErrorResponse) response);
            }
            if (!(response instanceof PiwigoResponseBufferingHandler.RemoteErrorResponse)) {
                // don't call user code if we may be re-trying. The outcome is not yet know.
                onAfterHandlePiwigoResponse(response);
            }
        }
    }

    protected void handlePiwigoServerErrorResponse(PiwigoResponseBufferingHandler.PiwigoServerErrorResponse msg) {
        showOrQueueRetryDialogMessage(msg, R.string.alert_title_error_handling_response, uiHelper.getContext().getString(R.string.alert_error_handling_response_pattern, msg.getPiwigoErrorCode(), msg.getPiwigoErrorMessage()));
    }

    protected void handlePiwigoHttpErrorResponse(PiwigoResponseBufferingHandler.PiwigoHttpErrorResponse msg) {
        if (msg.getStatusCode() < 0) {
            showOrQueueRetryDialogMessageWithDetail(msg, R.string.alert_title_error_talking_to_server, msg.getErrorMessage(), msg.getResponse());
        } else if (msg.getStatusCode() == 0) {
            showOrQueueRetryDialogMessageWithDetail(msg, R.string.alert_title_error_connecting_to_server, msg.getErrorMessage(), msg.getResponse());
        } else {
            showOrQueueRetryDialogMessageWithDetail(msg, R.string.alert_title_server_error, uiHelper.getContext().getString(R.string.alert_server_error_pattern, msg.getStatusCode(), msg.getErrorMessage()), msg.getResponse());
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
