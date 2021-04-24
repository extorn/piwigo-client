package delit.piwigoclient.ui.upload;

import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;

import delit.libs.ui.OwnedSafeAsyncTask;
import delit.libs.ui.util.DisplayUtils;
import delit.libs.util.IOUtils;
import delit.piwigoclient.R;
import delit.piwigoclient.business.ConnectionPreferences;
import delit.piwigoclient.business.UploadPreferences;
import delit.piwigoclient.model.piwigo.CategoryItemStub;
import delit.piwigoclient.model.piwigo.PiwigoSessionDetails;
import delit.piwigoclient.piwigoApi.upload.UploadJob;
import delit.piwigoclient.piwigoApi.upload.actors.ForegroundJobLoadActor;
import delit.piwigoclient.ui.upload.list.UploadDataItem;
import delit.piwigoclient.ui.upload.list.UploadDataItemModel;

public class CreateAndSubmitUploadJobTask extends OwnedSafeAsyncTask<AbstractUploadFragment<?,?>, Void, Integer, UploadJob> {
    private final UploadDataItemModel model;
    private final CategoryItemStub uploadToAlbum;
    private final byte privacyWanted;
    private final long piwigoListenerId;
    private final boolean filesizesChecked;

    public CreateAndSubmitUploadJobTask(AbstractUploadFragment<?,?> owner, UploadDataItemModel model, CategoryItemStub uploadToAlbum, byte privacyWanted, long piwigoListenerId, boolean filesizesChecked) {
        super(owner);
        withContext(owner.requireContext());
        this.model = model;
        this.uploadToAlbum = uploadToAlbum;
        this.privacyWanted = privacyWanted;
        this.piwigoListenerId = piwigoListenerId;
        this.filesizesChecked = filesizesChecked;
    }

    @Override
    protected void onPreExecuteSafely() {
        super.onPreExecuteSafely();
        getOwner().getUiHelper().showProgressIndicator(getContext().getString(R.string.generating_upload_job), -1);
    }

    @Override
    protected UploadJob doInBackgroundSafely(Void... nothing) {
        UploadJob activeJob = null;
        Long uploadJobId = getOwner().getUploadJobId();
        if (uploadJobId != null) {
            activeJob = new ForegroundJobLoadActor(getContext()).getActiveForegroundJob(uploadJobId);
        }

        if (activeJob == null) {

            if (uploadToAlbum == null || CategoryItemStub.ROOT_GALLERY.equals(uploadToAlbum)) {
                DisplayUtils.runOnUiThread(()->getOwner().onUploadJobSettingsNeeded());
                return null;
            }

            if (!runIsAllFileTypesAcceptedByServerTests(model)) {
                return null; // no, they aren't
            }

            if (!filesizesChecked) {
                if (!runAreAllFilesUnderUserChosenMaxUploadThreshold(model)) {
                    return null; // no, they aren't
                }
            }
        }

        if (activeJob == null) {
            ForegroundJobLoadActor jobLoadActor = new ForegroundJobLoadActor(getContext());
            UploadJob.VideoCompressionParams vidCompParams = getOwner().buildVideoCompressionParams();
            UploadJob.ImageCompressionParams imageCompParams = getOwner().buildImageCompressionParams();
            //First check the params
            if (vidCompParams != null) {
                if(!vidCompParams.hasAStream()) {
                    DisplayUtils.runOnUiThread(()-> getOwner().getUiHelper().showOrQueueDialogMessage(R.string.alert_error, getContext().getString(R.string.video_compression_settings_invalid)));
                    return null;
                }
            }
            // if the params are good, use them.
            UploadJob uploadJob = jobLoadActor.createUploadJob(ConnectionPreferences.getActiveProfile(), model, uploadToAlbum, privacyWanted, piwigoListenerId);
            if (vidCompParams != null) {
                uploadJob.setPlayableMediaCompressionParams(vidCompParams);
                uploadJob.setAllowUploadOfRawVideosIfIncompressible(getOwner().isRawVideoUploadPermittedIfNeeded());
            }
            if (imageCompParams != null) {
                uploadJob.setImageCompressionParams(imageCompParams);
            }
            activeJob = uploadJob;
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

    private boolean runAreAllFilesUnderUserChosenMaxUploadThreshold(UploadDataItemModel model) {

        int maxUploadSizeWantedThresholdMB = UploadPreferences.getMaxUploadFilesizeMb(getContext(), getOwner().getPrefs());
        long maxUploadSizeBytes = maxUploadSizeWantedThresholdMB * 1024 * 1024;

        final Set<UploadDataItem> filesForReview = new HashSet<>();
        for (UploadDataItem uploadDataItem : model.getUploadDataItemsReference()) {
            if(uploadDataItem.getDataLength() > maxUploadSizeBytes && !uploadDataItem.isCompressByDefault() && !Boolean.TRUE.equals(uploadDataItem.isCompressThisFile())) {
                filesForReview.add(uploadDataItem);
            }
        }
        if (filesForReview.size() > 0) {
            DisplayUtils.runOnUiThread(()->getOwner().onFilesForUploadTooLarge(filesForReview));
            return false;
        }
        return true;
    }

    private boolean runIsAllFileTypesAcceptedByServerTests(UploadDataItemModel model) {
        // check for server unacceptable files.
        ConnectionPreferences.ProfilePreferences activeProfile = ConnectionPreferences.getActiveProfile();
        Set<String> serverAcceptedFileTypes = PiwigoSessionDetails.getInstance(activeProfile).getAllowedFileTypes();
        Set<String> unacceptableFileExts = new TreeSet<>();
        for (UploadDataItem uploadDataItem : model.getUploadDataItemsReference()) {
            if(!serverAcceptedFileTypes.contains(uploadDataItem.getFileExt())) {
                if(IOUtils.isPlayableMedia(uploadDataItem.getMimeType()) || IOUtils.isImage(uploadDataItem.getMimeType())) {
                    if (!uploadDataItem.isCompressByDefault() && Boolean.TRUE.equals(uploadDataItem.isCompressThisFile())) {
                        unacceptableFileExts.add(uploadDataItem.getFileExt());
                    }
                } else {
                    unacceptableFileExts.add(uploadDataItem.getFileExt());
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
