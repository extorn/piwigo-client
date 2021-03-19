package delit.piwigoclient.piwigoApi.upload.actors;

import android.content.Context;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.documentfile.provider.DocumentFile;
import androidx.lifecycle.LiveData;

import java.util.ArrayList;
import java.util.List;

import delit.libs.ui.util.DisplayUtils;
import delit.libs.util.IOUtils;
import delit.piwigoclient.database.AppSettingsRepository;
import delit.piwigoclient.database.UriPermissionUse;
import delit.piwigoclient.database.livedata.PermissionsRemovingObserver;
import delit.piwigoclient.piwigoApi.upload.FileUploadDetails;
import delit.piwigoclient.piwigoApi.upload.UploadJob;

import static delit.piwigoclient.ui.upload.AbstractUploadFragment.URI_PERMISSION_CONSUMER_ID_FOREGROUND_UPLOAD;

public class JobCleanupActor extends UploadActor {

    private AppSettingsRepository appSettingsRepository;
    private static final String TAG = "JobCleanupActor";

    public JobCleanupActor(@NonNull Context context, @NonNull UploadJob uploadJob, @NonNull AppSettingsRepository appSettingsRepository, @NonNull ActorListener listener) {
        super(context, uploadJob, listener);
        this.appSettingsRepository = appSettingsRepository;
    }

    public void deleteSuccessfullyUploadedFilesFromDevice() {
        List<Uri> uris = new ArrayList<>(getUploadJob().getFilesForUpload().size());
        for(FileUploadDetails uploadDetails : getUploadJob().getFilesForUpload()) {
            if(uploadDetails.isSuccessfullyUploaded()) {
                if(uploadDetails.isDeleteAfterUpload()) {
                    DocumentFile docFile = IOUtils.getSingleDocFile(getContext(), uploadDetails.getFileUri());
                    if (docFile != null && docFile.exists()) {
                        if (!docFile.delete()) {
                            IOUtils.onFileDeleteFailed(TAG, docFile, "uploaded file");
                        }
                    }
                }
                uris.add(uploadDetails.getFileUri());
            }
        }
        if(!uris.isEmpty()) {
            LiveData<List<UriPermissionUse>> liveData = appSettingsRepository.getAllUriPermissions(uris);
            DisplayUtils.runOnUiThread(() -> liveData.observeForever(new PermissionsRemovingObserver(getContext(), appSettingsRepository, liveData, uris, URI_PERMISSION_CONSUMER_ID_FOREGROUND_UPLOAD)));
        }
    }
}
