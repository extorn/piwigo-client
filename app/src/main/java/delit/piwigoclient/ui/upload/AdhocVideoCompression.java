package delit.piwigoclient.ui.upload;

import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.view.View;

import androidx.annotation.RequiresApi;
import androidx.documentfile.provider.DocumentFile;

import java.util.Map;
import java.util.Objects;

import delit.libs.util.IOUtils;
import delit.piwigoclient.business.video.compression.ExoPlayerCompression;
import delit.piwigoclient.piwigoApi.upload.UploadJob;
import delit.piwigoclient.ui.common.UIHelper;

class AdhocVideoCompression {
    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
    public static void compressVideos(View linkedView, UploadJob.VideoCompressionParams compressionSettings, Map<Uri, Long> filesForUpload, UIHelper uiHelper) {
        Context context = linkedView.getContext();
        for(Uri fileForCompression : filesForUpload.keySet()) {
            ExoPlayerCompression.CompressionParameters exoCompressionSettings = new ExoPlayerCompression.CompressionParameters();
            exoCompressionSettings.setAddAudioTrack(compressionSettings.getAudioBitrate() != 0);
            exoCompressionSettings.setAddVideoTrack(compressionSettings.getQuality() - 0.00001 > 0);
            exoCompressionSettings.getVideoCompressionParameters().setWantedBitRatePerPixelPerSecond(compressionSettings.getQuality());
            exoCompressionSettings.getAudioCompressionParameters().setBitRate(compressionSettings.getAudioBitrate());
            DocumentFile moviesFolder = DocumentFile.fromFile(context.getExternalFilesDir(Environment.DIRECTORY_MOVIES));
            DocumentFile outputVideo;
            int i = 0;
            DocumentFile inputDocFile = IOUtils.getSingleDocFile(context, fileForCompression);
            String compressedFileExt = exoCompressionSettings.getOutputFileExt(context);
            String outputFilenameSuffixWithoutExt = IOUtils.getFileNameWithoutExt(Objects.requireNonNull(inputDocFile.getName()));
            String outputFilenameSuffix = outputFilenameSuffixWithoutExt + '.' + compressedFileExt;

            do {
                i++;
                outputVideo = moviesFolder.findFile("compressed_" + i + outputFilenameSuffix);
                if(outputVideo == null) {
                    outputVideo = moviesFolder.createFile(exoCompressionSettings.getOutputFileMimeType(null), "compressed_" + i + outputFilenameSuffixWithoutExt);
                    break;
                }
            } while (true);

            new ExoPlayerCompression().invokeFileCompression(context, fileForCompression, Objects.requireNonNull(outputVideo).getUri(), new DebugCompressionListener(uiHelper, linkedView), exoCompressionSettings);
        }
    }
}
