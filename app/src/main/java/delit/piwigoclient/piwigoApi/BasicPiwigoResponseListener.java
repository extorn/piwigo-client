package delit.piwigoclient.piwigoApi;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;

import androidx.appcompat.app.AppCompatActivity;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;

import com.drew.lang.annotations.Nullable;

import java.lang.ref.WeakReference;

import cz.msebera.android.httpclient.HttpStatus;
import delit.libs.core.util.Logging;
import delit.libs.ui.util.DisplayUtils;
import delit.libs.util.Utils;
import delit.piwigoclient.R;
import delit.piwigoclient.business.ConnectionPreferences;
import delit.piwigoclient.model.piwigo.PiwigoSessionDetails;
import delit.piwigoclient.piwigoApi.handlers.AbstractPiwigoDirectResponseHandler;
import delit.piwigoclient.ui.common.UIHelper;

/**
 * Created by gareth on 15/10/17.
 */

public class BasicPiwigoResponseListener<P extends UIHelper<P,T>, T> implements PiwigoResponseBufferingHandler.PiwigoResponseListener {

    private static final String TAG = "BasicPiwigoLsnr";
    private static final String HANDLER_ID = "handlerId";
    private long handlerId;
    private P uiHelper;
    private WeakReference<T> parent;


    public BasicPiwigoResponseListener() {
        handlerId = PiwigoResponseBufferingHandler.getNextHandlerId();
    }

    public @Nullable T getParent() {
        return parent == null ? (getUiHelper() != null ? getUiHelper().getParent() : null) : parent.get();
    }

    @Override
    public long getHandlerId() {
        return handlerId;
    }

    public void switchHandlerId(long newHandlerId) {
        handlerId = newHandlerId;
    }

    public void withUiHelper(T parent, P uiHelper) {
        this.uiHelper = uiHelper;
        this.parent = new WeakReference<>(parent);
    }

    public P getUiHelper() {
        return uiHelper;
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

    private void showOrQueueRetryDialogMessageWithDetail(final PiwigoResponseBufferingHandler.RemoteErrorResponse<?> errorResponse, int title, String msg, String detail) {
        handleErrorRetryPossible(errorResponse, title, msg, detail);
    }

    private void showOrQueueRetryDialogMessage(final PiwigoResponseBufferingHandler.BasePiwigoResponse response, int title, String msg) {
        if (response instanceof PiwigoResponseBufferingHandler.RemoteErrorResponse) {
            final PiwigoResponseBufferingHandler.RemoteErrorResponse<?> errorResponse = (PiwigoResponseBufferingHandler.RemoteErrorResponse<?>) response;
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

    protected void handleErrorRetryPossible(final PiwigoResponseBufferingHandler.RemoteErrorResponse<?> errorResponse, int title, String msg, String detail) {
        final AbstractPiwigoDirectResponseHandler handler = errorResponse.getHttpResponseHandler();
        ErrorRetryQuestionResultHandler<P,T> dialogListener = new ErrorRetryQuestionResultHandler<>(uiHelper, handler, errorResponse, handlerId);

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

    public <RSP extends PiwigoResponseBufferingHandler.Response> void onAfterHandlePiwigoResponse(RSP response) {
    }


    @Override
    public final void handlePiwigoResponse(PiwigoResponseBufferingHandler.Response response) {

        onBeforeHandlePiwigoResponse(response);

        uiHelper.onServiceCallComplete(response);

        UIHelper.Action<P,T,PiwigoResponseBufferingHandler.Response> action = uiHelper.getActionOnResponse(response);
        boolean runListenerHandlerCode = true;
        if(action != null) {
            if(response instanceof PiwigoResponseBufferingHandler.ErrorResponse) {
                runListenerHandlerCode = action.onFailure(uiHelper, (PiwigoResponseBufferingHandler.ErrorResponse) response);
            } else {
                runListenerHandlerCode = action.onSuccess(uiHelper, (PiwigoResponseBufferingHandler.Response)response);
            }
            if (response.isEndResponse()) {
                uiHelper.removeActionForResponse(response);
            }
        }


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
            Logging.log(Log.WARN,TAG,msg.getErrorMessage() + " " + msg.getErrorDetail());
            showOrQueueRetryDialogMessageWithDetail(msg, R.string.alert_title_error_talking_to_server, msg.getErrorMessage(), Utils.safeToString(msg.getResponseBody()));
        } else if (msg.getStatusCode() == 0) {
            Logging.log(Log.WARN,TAG,msg.getErrorMessage() + " " + msg.getErrorDetail());
            showOrQueueRetryDialogMessageWithDetail(msg, R.string.alert_title_error_connecting_to_server, msg.getErrorMessage(), Utils.safeToString(msg.getResponseBody()));
        } else {
            if (!(msg.getStatusCode() == HttpStatus.SC_GATEWAY_TIMEOUT && PiwigoSessionDetails.isCached(ConnectionPreferences.getActiveProfile()))) {

                String detailStr = uiHelper.getAppContext().getString(R.string.alert_server_uri_response_pattern, msg.getUrl(), Utils.safeToString(msg.getResponseBody()));
                showOrQueueRetryDialogMessageWithDetail(msg, R.string.alert_title_server_error, uiHelper.getAppContext().getString(R.string.alert_server_error_pattern, msg.getStatusCode(), msg.getErrorMessage()), detailStr);
            }
        }
    }


    protected void handlePiwigoServerErrorResponse(PiwigoResponseBufferingHandler.PiwigoServerErrorResponse msg) {
        String httpUriAndError = uiHelper.getAppContext().getString(R.string.alert_server_uri_response_pattern, msg.getUri(), msg.getPiwigoErrorMessage());
        showOrQueueRetryDialogMessage(msg, R.string.alert_title_error_handling_response, uiHelper.getAppContext().getString(R.string.alert_error_handling_response_pattern, msg.getPiwigoErrorCode(), httpUriAndError));
    }

    protected void handlePiwigoHttpErrorResponse(PiwigoResponseBufferingHandler.PiwigoHttpErrorResponse msg) {
        if (msg.getStatusCode() < 0) {
            Logging.log(Log.WARN,TAG,msg.getErrorMessage() + " " + msg.getErrorDetail());
            showOrQueueRetryDialogMessageWithDetail(msg, R.string.alert_title_error_talking_to_server, msg.getErrorMessage(), msg.getErrorDetail() + msg.getResponse());
        } else if (msg.getStatusCode() == 0) {
            Logging.log(Log.WARN,TAG,msg.getErrorMessage() + " " + msg.getErrorDetail());
            showOrQueueRetryDialogMessageWithDetail(msg, R.string.alert_title_error_connecting_to_server, msg.getErrorMessage(), msg.getErrorDetail() + msg.getResponse());
        } else {
            if (!(msg.getStatusCode() == HttpStatus.SC_GATEWAY_TIMEOUT && PiwigoSessionDetails.isCached(ConnectionPreferences.getActiveProfile()))) {
                String httpUriAndResponse = uiHelper.getAppContext().getString(R.string.alert_server_uri_response_pattern, msg.getUri(), msg.getResponse());
                showOrQueueRetryDialogMessageWithDetail(msg, R.string.alert_title_server_error, uiHelper.getAppContext().getString(R.string.alert_server_error_pattern, msg.getStatusCode(), msg.getErrorMessage()), msg.getErrorDetail() + httpUriAndResponse);
            }
        }
    }

    protected void handlePiwigoUnexpectedReplyErrorResponse(PiwigoResponseBufferingHandler.PiwigoUnexpectedReplyErrorResponse msg) {
        switch (msg.getRequestOutcome()) {
            case PiwigoResponseBufferingHandler.PiwigoUnexpectedReplyErrorResponse.OUTCOME_UNKNOWN:
                showOrQueueRetryDialogMessage(msg, R.string.alert_title_error_handling_response, uiHelper.getAppContext().getString(R.string.alert_error_handling_response_pattern, -1, msg.getRawResponse()));
                break;
            case PiwigoResponseBufferingHandler.PiwigoUnexpectedReplyErrorResponse.OUTCOME_FAILED:
                showOrQueueRetryDialogMessage(msg, R.string.alert_title_error_handling_response, uiHelper.getAppContext().getString(R.string.alert_error_handling_response_pattern, -1, msg.getRawResponse()));
                break;
            case PiwigoResponseBufferingHandler.PiwigoUnexpectedReplyErrorResponse.OUTCOME_SUCCESS:
                showOrQueueMessage(R.string.alert_title_error_handling_response, uiHelper.getAppContext().getString(R.string.alert_error_handling_response_pattern, -1, msg.getRawResponse()));
        }
    }

    @Override
    public boolean canHandlePiwigoResponseNow(PiwigoResponseBufferingHandler.Response response) {
        // If this fragment is not presently active, delay processing the response till it is.
        boolean retVal;
        T parent = getParent();
        if (parent instanceof Fragment) {
            // If this fragment is not presently active, delay processing the response till it is.
            Activity activity = ((Fragment) parent).getActivity();
            retVal = ((Fragment) parent).isAdded() && activity != null;
        } else if (parent instanceof AppCompatActivity) {
            retVal = !((AppCompatActivity) parent).isFinishing();
        } else if (parent instanceof ViewGroup) {
            DrawerLayout dl = DisplayUtils.getParentOfType((View) parent, DrawerLayout.class);
            if (dl != null) {
                retVal = true; // the drawer is attached regardless of whether visible or not.
            } else {
                retVal = ((ViewGroup) parent).isShown();
            }
        } else //noinspection ConstantConditions
            if (parent instanceof DialogFragment) {
            retVal = ((DialogFragment) parent).getDialog() != null;
        } else if (parent == null) {
            // this listener has become detached from the UI.
            retVal = false;
        } else {
            throw new IllegalArgumentException("Unsupported parent type " + parent);
        }
        return retVal;
    }
}
