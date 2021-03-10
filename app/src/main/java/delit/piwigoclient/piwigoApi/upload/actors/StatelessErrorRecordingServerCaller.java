package delit.piwigoclient.piwigoApi.upload.actors;

import android.content.Context;
import android.os.Bundle;
import android.util.Log;

import delit.libs.core.util.Logging;
import delit.libs.util.Utils;
import delit.piwigoclient.business.ConnectionPreferences;
import delit.piwigoclient.model.piwigo.PiwigoSessionDetails;
import delit.piwigoclient.piwigoApi.PiwigoResponseBufferingHandler;
import delit.piwigoclient.piwigoApi.handlers.AbstractPiwigoWsResponseHandler;
import delit.piwigoclient.piwigoApi.upload.FileUploadDetails;
import delit.piwigoclient.piwigoApi.upload.UploadJob;

public class StatelessErrorRecordingServerCaller {

    private static final String TAG = "ErrorRecordingServerCaller";
    private Context context;

    public StatelessErrorRecordingServerCaller(Context context) {
        this.context = context;
    }

    public void invokeWithRetries(UploadJob thisUploadJob, AbstractPiwigoWsResponseHandler handler, int maxRetries) {
        invokeWithRetries(thisUploadJob, null, handler, maxRetries);
    }

    public void invokeWithRetries(UploadJob thisUploadJob, FileUploadDetails fud, AbstractPiwigoWsResponseHandler handler, int maxRetries) {
        int allowedAttempts = maxRetries;
        while (!handler.isSuccess() && allowedAttempts > 0 && !thisUploadJob.isCancelUploadAsap()) {
            allowedAttempts--;
            // this is blocking
            handler.invokeAndWait(context, thisUploadJob.getConnectionPrefs());
        }
        if (!handler.isSuccess()) {
            if (fud != null) {
                fud.addError( buildPiwigoServerCallErrorMessage(handler));
            } else {
                thisUploadJob.recordError(buildPiwigoServerCallErrorMessage(handler));
            }
            logServerCallError(context, handler, thisUploadJob.getConnectionPrefs());
        }
    }

    public static void logServerCallError(Context context, AbstractPiwigoWsResponseHandler handler, ConnectionPreferences.ProfilePreferences connectionPrefs) {
        Bundle b = new Bundle();
        if(handler.getPiwigoMethod() != null) {
            b.putString("piwigoMethod", handler.getPiwigoMethod());
        }
        if(handler.getRequestParameters() !=null) {
            b.putString("requestParams", handler.getRequestParameters().toString());
        }
        if(handler.getResponse() != null) {
            b.putString("responseType", Utils.getId(handler.getResponse()));
        }
        Throwable error = handler.getError();
        if(error != null) {
            b.putSerializable("error", error);
        }
        PiwigoSessionDetails.writeToBundle(b, connectionPrefs);
        Logging.logAnalyticEvent(context,"uploadError", b);
        Logging.log(Log.WARN, TAG, "PwgMethod: %1$s, ReqP: %2$s, RespT: %3$s", handler.getPiwigoMethod(), handler.getRequestParameters(), Utils.getId(handler.getResponse()));
        if(handler.getError() != null) {
            Logging.recordException(handler.getError());
        }
    }

    protected String buildPiwigoServerCallErrorMessage(AbstractPiwigoWsResponseHandler handler) {
        StringBuilder sb = new StringBuilder();
        sb.append("PiwigoMethod:");
        sb.append('\n');
        sb.append(handler.getPiwigoMethod());
        sb.append('\n');
        sb.append("Error:");
        sb.append('\n');
        if(handler.getError() != null) {
            sb.append(handler.getError().getMessage());
        } else {
            boolean detailAdded = false;
            if(handler.getResponse() != null && handler.getResponse() instanceof PiwigoResponseBufferingHandler.PiwigoHttpErrorResponse) {
                PiwigoResponseBufferingHandler.PiwigoHttpErrorResponse errorResponse = (PiwigoResponseBufferingHandler.PiwigoHttpErrorResponse)handler.getResponse();
                if(errorResponse.getResponse() != null) {
                    sb.append(errorResponse.getResponse());
                    detailAdded = true;
                }
                if(errorResponse.getErrorMessage() != null && !"java.lang.IllegalStateException: Expected BEGIN_OBJECT but was STRING at line 1 column 1 path $".equals(errorResponse.getErrorMessage())) {
                    if(detailAdded) {
                        sb.append('\n');
                    }
                    sb.append(errorResponse.getErrorMessage());
                    detailAdded = true;
                }
                if(errorResponse.getErrorDetail() != null) {
                    if(detailAdded) {
                        sb.append('\n');
                    }
                    sb.append(errorResponse.getErrorDetail());
                    detailAdded = true;
                }
            }
            if(!detailAdded){
                sb.append("???");
            }
        }
        return sb.toString();
    }
}
