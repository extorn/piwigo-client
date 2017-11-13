package delit.piwigoclient.piwigoApi.upload.handlers;

import delit.piwigoclient.piwigoApi.PiwigoResponseBufferingHandler;

/**
 * Created by gareth on 28/06/17.
 */
@Deprecated
public class StubAddImageListener extends BaseStubPiwigoResponseListener<PiwigoResponseBufferingHandler.PiwigoAddImageResponse> {
    public StubAddImageListener() {
        super(PiwigoResponseBufferingHandler.PiwigoAddImageResponse.class);
    }
}
