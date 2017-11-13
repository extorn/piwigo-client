package delit.piwigoclient.piwigoApi.upload.handlers;

import delit.piwigoclient.model.UploadFileFragment;
import delit.piwigoclient.piwigoApi.PiwigoResponseBufferingHandler;

/**
 * Created by gareth on 28/06/17.
 */
@Deprecated
public class StubUploadChunkListener extends BaseStubPiwigoResponseListener<PiwigoResponseBufferingHandler.PiwigoUploadFileChunkResponse> {

    private UploadFileFragment uploadFileFragment;

    public StubUploadChunkListener() {
        super(PiwigoResponseBufferingHandler.PiwigoUploadFileChunkResponse.class);
    }

    @Override
    protected void withFailureResponse(PiwigoResponseBufferingHandler.Response response) {
        uploadFileFragment.incrementUploadAttempts();
    }

    public void setUploadFileFragment(UploadFileFragment currentUploadFileFragment) {
        uploadFileFragment = currentUploadFileFragment;
    }
}
