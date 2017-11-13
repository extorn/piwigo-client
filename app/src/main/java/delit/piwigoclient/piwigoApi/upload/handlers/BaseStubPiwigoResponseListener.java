package delit.piwigoclient.piwigoApi.upload.handlers;

import cz.msebera.android.httpclient.HttpStatus;
import delit.piwigoclient.piwigoApi.PiwigoResponseBufferingHandler;

/**
 * Created by gareth on 09/10/17.
 */
@Deprecated
public class BaseStubPiwigoResponseListener<T extends PiwigoResponseBufferingHandler.BasePiwigoResponse> implements PiwigoResponseBufferingHandler.PiwigoResponseListener {

    private final Class<T> expectedResponseClass;
    private boolean success;
    private boolean unauthorized;
    private PiwigoResponseBufferingHandler.Response error;
    private boolean responseHandled;

    public boolean isResponseHandled() {
        return responseHandled;
    }

    public BaseStubPiwigoResponseListener(Class<T> expectedResponseClass) {
        this.expectedResponseClass = expectedResponseClass;
    }

    public void reset() {
        success = false;
        unauthorized = false;
        error = null;
        responseHandled = false;
    }

    @Override
    public void handlePiwigoResponse(PiwigoResponseBufferingHandler.Response response) {
        if (expectedResponseClass.isInstance(response)) {
            withSuccessResponse(expectedResponseClass.cast(response));
            success = true;
        } else {
            withFailureResponse(response);
            if (response instanceof PiwigoResponseBufferingHandler.PiwigoHttpErrorResponse) {
                PiwigoResponseBufferingHandler.PiwigoHttpErrorResponse errResponse = (PiwigoResponseBufferingHandler.PiwigoHttpErrorResponse) response;
                if (errResponse.getStatusCode() == HttpStatus.SC_UNAUTHORIZED) {
                    unauthorized = true;
                }
            }
            error = response;
        }
        responseHandled = true;
    }

    protected void withFailureResponse(PiwigoResponseBufferingHandler.Response response) {
    }

    protected void withSuccessResponse(T response) {
    }

    @Override
    public long getHandlerId() {
        return Long.MIN_VALUE; // shouldn't ever be tested.
    }

    @Override
    public boolean canHandlePiwigoResponseNow(PiwigoResponseBufferingHandler.Response response) {
        return true;
    }

    public PiwigoResponseBufferingHandler.Response getError() {
        return error;
    }

    public boolean isUnauthorized() {
        return unauthorized;
    }

    public boolean isSuccess() {
        return success;
    }
}

