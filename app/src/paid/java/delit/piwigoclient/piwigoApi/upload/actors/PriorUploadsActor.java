package delit.piwigoclient.piwigoApi.upload.actors;

import android.content.Context;
import android.net.Uri;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import delit.piwigoclient.database.PriorUploadRepository;
import delit.piwigoclient.database.entity.PriorUpload;
import delit.piwigoclient.database.entity.UploadDestination;
import delit.piwigoclient.database.pojo.UploadDestinationWithPriorUploads;
import delit.piwigoclient.piwigoApi.upload.FileUploadDetails;
import delit.piwigoclient.piwigoApi.upload.UploadJob;

public class PriorUploadsActor extends UploadActor {

    public PriorUploadsActor(@NonNull Context context, @NonNull UploadJob uploadJob, @NonNull ActorListener listener) {
        super(context, uploadJob, listener);
    }

    public void filterPriorUploadsByUri(PriorUploadRepository repository) {
        String uploadToServerKey = getUploadJob().getConnectionPrefs().getAbsoluteProfileKey(getPrefs(), getContext());
        ArrayList<Uri> uris = getUploadJob().getFilesNotYetUploaded();
        List<Uri> previousUploads = repository.getAllPreviouslyUploadedUrisToServerKeyMatching(uploadToServerKey, uris);
        getUploadJob().filterPreviouslyUploadedFilesByUri(previousUploads);
    }

    public void updatePriorUploadsList(PriorUploadRepository repository) {
        List<PriorUpload> priorUploads = new ArrayList<>();
        Date now = new Date();
        for(FileUploadDetails uploadDetails : getUploadJob().getFilesForUpload()) {
            if (uploadDetails.isSuccessfullyUploaded() && !uploadDetails.isDeleteAfterUpload()) {
                priorUploads.add(new PriorUpload(null, uploadDetails.getFileUri(), uploadDetails.getChecksumOfFileToUpload(), now));
            }
        }
        if(!priorUploads.isEmpty()) {
            String connectionKey = getUploadJob().getConnectionPrefs().getAbsoluteProfileKey(getPrefs(), getContext());
            String serverUriStr = getUploadJob().getConnectionPrefs().getPiwigoServerAddress(getPrefs(), getContext());
            Uri serverUri = Objects.requireNonNull(Uri.parse(serverUriStr));
            UploadDestinationWithPriorUploads uploadsDetail = new UploadDestinationWithPriorUploads(new UploadDestination(connectionKey, serverUri));
            uploadsDetail.setPriorUploads(priorUploads);
            Future<?> f = repository.insert(uploadsDetail);
            try {
                f.get(6, TimeUnit.HOURS);
            } catch (ExecutionException e) {
                e.printStackTrace();
            } catch (InterruptedException e) {
                e.printStackTrace();
            } catch (TimeoutException e) {
                e.printStackTrace();
            }
        }
    }
}
