package delit.piwigoclient.database;

import android.app.Application;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;

import java.util.Collection;
import java.util.List;

public class PriorUploadsViewModel extends AndroidViewModel {

    private static final String TAG = "AppSettingsVM";
    private final PriorUploadRepository repository;

    public PriorUploadsViewModel(@NonNull Application application) {
        super(application);
        repository = new PriorUploadRepository(PiwigoUploadsDatabase.getInstance(application));
    }

    public List<Uri> getAllPreviouslyUploadedUrisToServerKeyMatching(String uploadToSite, Collection<Uri> uris) {
        return repository.getAllPreviouslyUploadedUrisToServerKeyMatching(uploadToSite, uris);
    }

    public List<String> getAllPreviouslyUploadedChecksumsToServerKeyMatching(String uploadToSite, Collection<String> checksums) {
        return repository.getAllPreviouslyUploadedChecksumsToServerKeyMatching(uploadToSite, checksums);
    }
}
