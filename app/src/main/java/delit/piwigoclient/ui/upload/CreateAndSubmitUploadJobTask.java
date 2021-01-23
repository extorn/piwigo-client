package delit.piwigoclient.ui.upload;

import android.net.Uri;
import android.webkit.MimeTypeMap;

import androidx.core.content.MimeTypeFilter;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import delit.libs.ui.OwnedSafeAsyncTask;
import delit.libs.ui.util.DisplayUtils;
import delit.libs.util.IOUtils;
import delit.libs.util.SetUtils;
import delit.piwigoclient.R;
import delit.piwigoclient.business.ConnectionPreferences;
import delit.piwigoclient.business.UploadPreferences;
import delit.piwigoclient.model.piwigo.CategoryItemStub;
import delit.piwigoclient.model.piwigo.PiwigoSessionDetails;
import delit.piwigoclient.piwigoApi.upload.ForegroundPiwigoUploadService;
import delit.piwigoclient.piwigoApi.upload.UploadJob;

public class CreateAndSubmitUploadJobTask extends OwnedSafeAsyncTask<AbstractUploadFragment, Void, Integer, UploadJob> {
    private final Map<Uri,Long> filesToUploadAndSize;
    private final CategoryItemStub uploadToAlbum;
    private final byte privacyWanted;
    private final long piwigoListenerId;
    private final boolean deleteUploadedFiles;
    private final boolean filesizesChecked;

    public CreateAndSubmitUploadJobTask(AbstractUploadFragment owner, Map<Uri,Long> filesToUploadAndSize, CategoryItemStub uploadToAlbum, byte privacyWanted, long piwigoListenerId, boolean deleteUploadedFiles, boolean filesizesChecked) {
        super(owner);
        withContext(owner.requireContext());
        this.filesToUploadAndSize = filesToUploadAndSize;
        this.uploadToAlbum = uploadToAlbum;
        this.privacyWanted = privacyWanted;
        this.piwigoListenerId = piwigoListenerId;
        this.deleteUploadedFiles = deleteUploadedFiles;
        this.filesizesChecked = filesizesChecked;
    }

    @Override
    protected void onPreExecuteSafely() {
        super.onPreExecuteSafely();
        getOwner().getUiHelper().showProgressIndicator(getContext().getString(R.string.generating_upload_job), -1);
    }

    @Override
    protected UploadJob doInBackgroundSafely(Void... voids) {
        UploadJob activeJob = null;
        Long uploadJobId = getOwner().getUploadJobId();
        if (uploadJobId != null) {
            activeJob = ForegroundPiwigoUploadService.getActiveForegroundJob(getContext(), uploadJobId);
        }

        if (activeJob == null) {

            if (uploadToAlbum == null || CategoryItemStub.ROOT_GALLERY.equals(uploadToAlbum)) {
                getOwner().onUploadJobSettingsNeeded();
                return null;
            }

            boolean compressVideos = getOwner().isCompressVideos();
            boolean compressImages = getOwner().isCompressImages();

            if (!runIsAllFileTypesAcceptedByServerTests(filesToUploadAndSize.keySet(), compressVideos, compressImages)) {
                return null; // no, they aren't
            }

            if (!filesizesChecked) {
                if (!runAreAllFilesUnderUserChosenMaxUploadThreshold(filesToUploadAndSize, compressVideos, compressImages)) {
                    return null; // no, they aren't
                }
            }
        }

        if (activeJob == null) {
            activeJob = ForegroundPiwigoUploadService.createUploadJob(ConnectionPreferences.getActiveProfile(), filesToUploadAndSize, uploadToAlbum, privacyWanted, piwigoListenerId, deleteUploadedFiles);
            UploadJob.VideoCompressionParams vidCompParams = getOwner().buildVideoCompressionParams();
            UploadJob.ImageCompressionParams imageCompParams = getOwner().buildImageCompressionParams();
            if (vidCompParams != null) {
                activeJob.setVideoCompressionParams(vidCompParams);
                activeJob.setAllowUploadOfRawVideosIfIncompressible(getOwner().isRawVideoUploadPermittedIfNeeded());
                if(!vidCompParams.hasAStream()) {
                    DisplayUtils.runOnUiThread(()-> getOwner().getUiHelper().showOrQueueDialogMessage(R.string.alert_error, getContext().getString(R.string.video_compression_settings_invalid)));
                    return null;
                }
            }

            if (imageCompParams != null) {
                activeJob.setImageCompressionParams(imageCompParams);
            }
        }
        return activeJob;
    }

    @Override
    protected void onPostExecuteSafely(UploadJob newJob) {
        if(newJob != null) {
            getOwner().withNewUploadJob(newJob);
        }
        getOwner().getUiHelper().hideProgressIndicator();
    }

    private Map<Uri,Double> getFilesExceedingMaxDesiredUploadThreshold(Map<Uri,Long> filesForUploadAndSizes) {
        int maxUploadSizeWantedThresholdMB = UploadPreferences.getMaxUploadFilesizeMb(getContext(), getOwner().getPrefs());
        HashMap<Uri, Double> retVal = new HashMap<>();
        for (Map.Entry<Uri,Long> entry : filesForUploadAndSizes.entrySet()) {
            Uri f = entry.getKey();
            Long size = entry.getValue();
            double fileLengthMB = BigDecimal.valueOf(size).divide(BigDecimal.valueOf(1024* 1024), BigDecimal.ROUND_HALF_EVEN).doubleValue();
            if (fileLengthMB > maxUploadSizeWantedThresholdMB) {
                retVal.put(f, fileLengthMB);
            }
        }
        return retVal;
    }

    private boolean runAreAllFilesUnderUserChosenMaxUploadThreshold(Map<Uri,Long> filesForUploadAndSize, boolean compressVideos, boolean compressImages) {

        final Map<Uri,Double> filesForReview = getFilesExceedingMaxDesiredUploadThreshold(filesForUploadAndSize);

        StringBuilder filenameListStrB = new StringBuilder();
        Set<Uri> keysToRemove = new HashSet<>();
        for (Map.Entry<Uri,Double> f : filesForReview.entrySet()) {
            if (compressVideos && MimeTypeFilter.matches(IOUtils.getMimeType(getContext(), f.getKey()), "video/*")) {
                keysToRemove.add(f.getKey());
                continue;
            }
            if (compressImages && MimeTypeFilter.matches(IOUtils.getMimeType(getContext(), f.getKey()), "image/*")) {
                keysToRemove.add(f.getKey());
                continue;
            }
            double fileLengthMB = f.getValue();
            if (filesForReview.size() > 0) {
                filenameListStrB.append(", ");
            }
            filenameListStrB.append(f.getKey().getPath());
            filenameListStrB.append(String.format(Locale.getDefault(), "(%1$.1fMB)", fileLengthMB));
        }
        for(Uri uri : keysToRemove) {
            filesForReview.remove(uri);
        }
        if (filesForReview.size() > 0) {
            getOwner().onFilesForUploadTooLarge(filenameListStrB.toString(), filesForReview);
            return false;
        }
        return true;
    }

    private boolean runIsAllFileTypesAcceptedByServerTests(Collection<Uri> filesForUpload, boolean compressVideos, boolean compressImages) {
        // check for server unacceptable files.
        ConnectionPreferences.ProfilePreferences activeProfile = ConnectionPreferences.getActiveProfile();
        Set<String> serverAcceptedFileTypes = PiwigoSessionDetails.getInstance(activeProfile).getAllowedFileTypes();
        Set<String> fileTypesForUpload = IOUtils.getUniqueFileExts(getContext(), filesForUpload);
        Set<String> unacceptableFileExts = SetUtils.difference(fileTypesForUpload, serverAcceptedFileTypes);
        if (compressVideos) {
            Iterator<String> iter = unacceptableFileExts.iterator();
            MimeTypeMap mimeTypeMap = MimeTypeMap.getSingleton();
            while (iter.hasNext()) {
                String mimeType = mimeTypeMap.getMimeTypeFromExtension(iter.next());
                if (mimeType != null && MimeTypeFilter.matches(mimeType,"video/*")) {
                    iter.remove();
                }
            }
        }
        if (compressImages) {
            Iterator<String> iter = unacceptableFileExts.iterator();
            MimeTypeMap mimeTypeMap = MimeTypeMap.getSingleton();
            while (iter.hasNext()) {
                String mimeType = mimeTypeMap.getMimeTypeFromExtension(iter.next());
                if (mimeType != null && MimeTypeFilter.matches(mimeType,"image/*")) {
                    iter.remove();
                }
            }
        }

        if (!unacceptableFileExts.isEmpty()) {
            getOwner().withFilesUnacceptableForUploadRejected(unacceptableFileExts);
            return false;
        }
        return true;
    }
}
