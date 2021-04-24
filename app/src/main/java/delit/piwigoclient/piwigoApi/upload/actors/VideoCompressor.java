package delit.piwigoclient.piwigoApi.upload.actors;

import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.webkit.MimeTypeMap;

import androidx.annotation.RequiresApi;
import androidx.documentfile.provider.DocumentFile;

import java.util.Set;

import delit.libs.core.util.Logging;
import delit.libs.util.IOUtils;
import delit.piwigoclient.business.video.compression.ExoPlayerCompression;
import delit.piwigoclient.model.piwigo.PiwigoSessionDetails;
import delit.piwigoclient.piwigoApi.upload.FileUploadDetails;
import delit.piwigoclient.piwigoApi.upload.UploadJob;
import delit.piwigoclient.piwigoApi.upload.messages.PiwigoUploadFileLocalErrorResponse;

import static delit.piwigoclient.piwigoApi.handlers.AbstractPiwigoDirectResponseHandler.getNextMessageId;

public class VideoCompressor extends Thread {
    private static final String TAG = "VideoCompressor";
    private final Context context;

    public VideoCompressor(Context context) {
        this.context = context;
    }

    public interface VideoCompressorListener extends ExoPlayerCompression.CompressionListener {

        boolean isCompressionComplete();

        Exception getCompressionError();

        boolean isUnsupportedVideoFormat();

        void notifyUsersOfError(UploadJob uploadJob, PiwigoUploadFileLocalErrorResponse piwigoUploadFileLocalErrorResponse);

        void onCompressionSuccess(Uri inputFile, DocumentFile outputVideo);
    }

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
    public boolean compressVideo(UploadJob uploadJob, Uri rawVideo, VideoCompressorListener listener){
        FileUploadDetails fud = uploadJob.getFileUploadDetails(rawVideo);

        ExoPlayerCompression compressor = new ExoPlayerCompression();
        ExoPlayerCompression.CompressionParameters compressionSettings = new ExoPlayerCompression.CompressionParameters();

        double desiredBitratePerPixelPerSec = uploadJob.getPlayableMediaCompressionParams().getQuality();
        int desiredAudioBitrate = uploadJob.getPlayableMediaCompressionParams().getAudioBitrate();
        compressionSettings.setAddAudioTrack(desiredAudioBitrate != 0); // -1 is used for pass-through.
        compressionSettings.setAddVideoTrack(desiredBitratePerPixelPerSec - 0.00001 > 0); // no pass-through option.
        compressionSettings.getVideoCompressionParameters().setWantedBitRatePerPixelPerSecond(desiredBitratePerPixelPerSec);
        compressionSettings.getAudioCompressionParameters().setBitRate(desiredAudioBitrate);
        Set<String> serverAcceptedFileExts = PiwigoSessionDetails.getInstance(uploadJob.getConnectionPrefs()).getAllowedFileTypes();

        String outputFileExt = null;
        try {
            outputFileExt = compressionSettings.getOutputFileExt(context, serverAcceptedFileExts);
        } catch (IllegalStateException e) {
            listener.notifyUsersOfError(uploadJob, new PiwigoUploadFileLocalErrorResponse(getNextMessageId(), rawVideo, true, e));
        }
        DocumentFile outputVideo;

        if (outputFileExt == null) {
            if (uploadJob.isAllowUploadOfRawVideosIfIncompressible()) {
                Bundle b = new Bundle();
                b.putString("file_ext", IOUtils.getFileExt(rawVideo.toString()));
                Logging.logAnalyticEvent(context, "incompressible_video_encountered", b);
                fud.setCompressionNeeded(false);// will allow the raw file to be uploaded
                outputVideo = IOUtils.getSingleDocFile(context, rawVideo);
            } else {
                String msg = "Unable to find acceptable file extension for compressed video amongst " + serverAcceptedFileExts;
                Logging.log(Log.ERROR, TAG, msg);
                // cancel the upload of this file
                fud.setProcessingFailed();
                // notify listeners that this file encountered an error
                listener.notifyUsersOfError(uploadJob, new PiwigoUploadFileLocalErrorResponse(getNextMessageId(), rawVideo, true, new Exception(msg)));
                outputVideo = null;
            }
        } else {
            String outputMimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(outputFileExt);
            outputVideo = uploadJob.buildCompressedFile(context, rawVideo, outputMimeType);

            compressor.invokeFileCompression(context, rawVideo, outputVideo.getUri(), listener, compressionSettings);

            while (!listener.isCompressionComplete() && null == listener.getCompressionError()) {
                try {
                    uploadJob.waitBriefly(1000);
                    if (uploadJob.isCancelUploadAsap() || fud.isUploadCancelled()) {
                        compressor.cancel();
                        return false;
                    }
                } catch (InterruptedException e) {
                    Log.e(TAG, "Listener awoken!");
                    // either spurious wakeup or the upload job wished to be cancelled.
                }
            }
            if (listener.getCompressionError() != null && !fud.isUploadCancelled()) {
                if (outputVideo.exists()) {
                    if (!outputVideo.delete()) {
                        Logging.log(Log.ERROR, TAG, "Unable to delete corrupt compressed file.");
                    }
                }
                outputVideo = null;

                Exception e = listener.getCompressionError();
                if (listener.isUnsupportedVideoFormat() && uploadJob.isAllowUploadOfRawVideosIfIncompressible()) {
                    Bundle b = new Bundle();
                    b.putString("file_ext", IOUtils.getFileExt(rawVideo.toString()));
                    Logging.logAnalyticEvent(context, "incompressible_video_encountered", b);
                    fud.setCompressionNeeded(false);// will allow the raw file to be uploaded
                    outputVideo = IOUtils.getSingleDocFile(context, rawVideo);
                } else {
                    Logging.recordException(e);
                    // mark the upload for this file as cancelled
                    fud.setProcessingFailed();
                    // notify the listener of the error
                    listener.notifyUsersOfError(uploadJob, new PiwigoUploadFileLocalErrorResponse(getNextMessageId(), rawVideo, true, e));
                }
            } else {
                if (outputVideo.exists()) {
                    fud.setCompressedFileUri(outputVideo.getUri());
                } else {
                    outputVideo = null;
                }
            }
        }
        listener.onCompressionSuccess(rawVideo, outputVideo);
        return (outputVideo != null);
    }

}
