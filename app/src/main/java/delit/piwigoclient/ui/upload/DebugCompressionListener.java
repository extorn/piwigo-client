package delit.piwigoclient.ui.upload;

import android.net.Uri;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

import delit.libs.core.util.Logging;
import delit.libs.ui.util.DisplayUtils;
import delit.libs.util.IOUtils;
import delit.piwigoclient.R;
import delit.piwigoclient.business.video.compression.ExoPlayerCompression;
import delit.piwigoclient.ui.common.UIHelper;
import delit.piwigoclient.ui.upload.list.UploadDataItemModel;


public class DebugCompressionListener implements ExoPlayerCompression.CompressionListener {
    private static final String TAG = "CompressListen";
    private final UIHelper uiHelper;
        private final SimpleDateFormat strFormat;
        private long startCompressionAt;
        private View linkedView;

        {
            strFormat = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
            strFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
        }

        public DebugCompressionListener(UIHelper uiHelper, View linkedView) {
            this.uiHelper = uiHelper;
            this.linkedView = linkedView;
        }

        @Override
        public void onCompressionStarted(Uri inputFile, Uri outputFile) {
            DisplayUtils.runOnUiThread(() -> {
                uiHelper.showDetailedMsg(R.string.alert_information, "Video Compression started");
                startCompressionAt = System.currentTimeMillis();
                AbstractUploadFragment fragment = (AbstractUploadFragment) uiHelper.getParent();
                fragment.getFilesForUploadViewAdapter().updateCompressionProgress(inputFile, outputFile, 0);
                linkedView.setEnabled(false);
            });

        }

        @Override
        public void onCompressionError(Uri inputFile, Uri outputFile, Exception e) {
            DisplayUtils.runOnUiThread(() -> {
                uiHelper.showDetailedMsg(R.string.alert_information, "Video Compression failed");
                linkedView.setEnabled(true);
                Logging.log(Log.ERROR, TAG, "Video Compression failed");
                Logging.recordException(e);
                AbstractUploadFragment fragment = (AbstractUploadFragment) uiHelper.getParent();
                fragment.getFilesForUploadViewAdapter().updateCompressionProgress(inputFile, outputFile, 0);
                IOUtils.delete(uiHelper.getAppContext(), outputFile);
            });

        }

        @Override
        public void onCompressionComplete(Uri inputFile, Uri outputFile) {
            DisplayUtils.runOnUiThread(() -> {
                uiHelper.showDetailedMsg(R.string.alert_information, "Video Compression finished");
                linkedView.setEnabled(true);
                AbstractUploadFragment fragment = (AbstractUploadFragment) uiHelper.getParent();
                fragment.getFilesForUploadViewAdapter().updateCompressionProgress(inputFile, outputFile, 0);
                Uri compressedFileUri = IOUtils.addFileToMediaStore(uiHelper.getAppContext(), outputFile);
                //FIXME - This works because even though the compressed file (media store) uri is not written to, it is one already (I think!).
                String filename = IOUtils.getFilename(uiHelper.getAppContext(), outputFile);
                String mimeType = IOUtils.getMimeType(uiHelper.getAppContext(), outputFile);
                fragment.getFilesForUploadViewAdapter().add(new UploadDataItemModel.UploadDataItem(outputFile, filename, mimeType));
            });
        }

        @Override
        public void onCompressionProgress(Uri inputFile, Uri outputFile, final double compressionProgress, final long mediaDurationMs) {
            if (!DisplayUtils.isRunningOnUIThread()) {
                DisplayUtils.runOnUiThread(() -> onCompressionProgress(inputFile, outputFile, compressionProgress, mediaDurationMs));
                return;
            }
            AbstractUploadFragment fragment = (AbstractUploadFragment) uiHelper.getParent();
            FilesToUploadRecyclerViewAdapter adapter = fragment.getFilesForUploadViewAdapter();
            if(adapter != null) {
                adapter.updateCompressionProgress(inputFile, outputFile, (int) Math.rint(compressionProgress));
            }
            long currentTime = System.currentTimeMillis();
            long elapsedTime = currentTime - startCompressionAt;
            long estimateTotalCompressionTime = Math.round(100 * (elapsedTime / compressionProgress));
            long endCompressionAtEstimate = startCompressionAt + estimateTotalCompressionTime;
            Date endCompressionAt = new Date(endCompressionAtEstimate);
            String remainingTimeStr = strFormat.format(new Date(endCompressionAtEstimate - currentTime));
            String elapsedCompressionTimeStr = strFormat.format(new Date(elapsedTime));

            if (mediaDurationMs > 0) {
                double timeProcessed = mediaDurationMs * (compressionProgress / 100);
                double compressionRate = timeProcessed / elapsedTime;
                uiHelper.showDetailedMsg(R.string.alert_information, String.format(Locale.getDefault(), "Video Compression\nrate: %5$.02fx\nprogress: %1$.02f%%\nremaining time: %4$s\nElapsted time: %2$s\nEstimate Finish at: %3$tH:%3$tM:%3$tS", compressionProgress, elapsedCompressionTimeStr, endCompressionAt, remainingTimeStr, compressionRate), Toast.LENGTH_SHORT, 1);
            } else {
                uiHelper.showDetailedMsg(R.string.alert_information, String.format(Locale.getDefault(), "Video Compression\nprogress: %1$.02f%%\nremaining time: %4$s\nElapsted time: %2$s\nEstimate Finish at: %3$tH:%3$tM:%3$tS", compressionProgress, elapsedCompressionTimeStr, endCompressionAt, remainingTimeStr), Toast.LENGTH_SHORT, 1);
            }
        }
    }
