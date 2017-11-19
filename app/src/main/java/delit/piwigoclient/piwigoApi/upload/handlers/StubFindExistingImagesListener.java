package delit.piwigoclient.piwigoApi.upload.handlers;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import delit.piwigoclient.piwigoApi.PiwigoResponseBufferingHandler;
import delit.piwigoclient.piwigoApi.handlers.AbstractPiwigoDirectResponseHandler;
import delit.piwigoclient.piwigoApi.upload.UploadJob;

/**
 * Created by gareth on 28/06/17.
 */
@Deprecated
public class StubFindExistingImagesListener extends BaseStubPiwigoResponseListener<PiwigoResponseBufferingHandler.PiwigoFindExistingImagesResponse> {

    private UploadJob thisUploadJob;
    private ArrayList<File> filesExistingOnServerAlready;

    public StubFindExistingImagesListener(UploadJob thisUploadJob) {
        super(PiwigoResponseBufferingHandler.PiwigoFindExistingImagesResponse.class);
        this.thisUploadJob = thisUploadJob;
    }

    public void reset() {
        super.reset();
        filesExistingOnServerAlready = null;
    }

    @Override
    protected void withSuccessResponse(PiwigoResponseBufferingHandler.PiwigoFindExistingImagesResponse response) {
        HashMap<String, Long> preexistingItemChecksums = response.getExistingImages();
        filesExistingOnServerAlready = new ArrayList<>();
        for (Map.Entry<File, String> fileCheckSumEntry : thisUploadJob.getFileChecksums().entrySet()) {
            if (preexistingItemChecksums.containsKey(fileCheckSumEntry.getValue())) {
                filesExistingOnServerAlready.add(fileCheckSumEntry.getKey());
            }
        }

        if (filesExistingOnServerAlready.size() > 0) {

            thisUploadJob.getFilesForUpload().removeAll(filesExistingOnServerAlready);
            long imageExistsMessageId = AbstractPiwigoDirectResponseHandler.getNextMessageId();
            PiwigoResponseBufferingHandler.PiwigoUploadFileFilesExistAlreadyResponse r1 = new PiwigoResponseBufferingHandler.PiwigoUploadFileFilesExistAlreadyResponse(imageExistsMessageId, filesExistingOnServerAlready);
            PiwigoResponseBufferingHandler.getDefault().preRegisterResponseHandlerForNewMessage(thisUploadJob.getJobId(), imageExistsMessageId);
            PiwigoResponseBufferingHandler.getDefault().processResponse(r1);
        }
    }

    public List<File> getFilesExistingOnServerAlready() {
        return filesExistingOnServerAlready;
    }

}
