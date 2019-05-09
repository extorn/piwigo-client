package delit.piwigoclient.business.video.compression;

import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.crashlytics.android.Crashlytics;
import com.google.android.exoplayer2.DefaultLoadControl;
import com.google.android.exoplayer2.ExoPlayerFactory;
import com.google.android.exoplayer2.LoadControl;
import com.google.android.exoplayer2.PlaybackParameters;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.extractor.DefaultExtractorsFactory;
import com.google.android.exoplayer2.extractor.ExtractorsFactory;
import com.google.android.exoplayer2.source.ExtractorMediaSource;
import com.google.android.exoplayer2.trackselection.AdaptiveTrackSelection;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.trackselection.TrackSelection;
import com.google.android.exoplayer2.trackselection.TrackSelector;
import com.google.android.exoplayer2.upstream.DefaultBandwidthMeter;
import com.google.android.exoplayer2.upstream.FileDataSourceFactory;

import net.ypresto.qtfaststart.QtFastStart;

import java.io.File;
import java.io.IOException;

import androidx.annotation.RequiresApi;

import static android.media.MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CBR;

/**
 * This doesn't seem to work - issue with the link between the renderers and the exoplayer I think... It's kind of there, but not completely. Very weird error.
 */
@RequiresApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
public class ExoPlayerCompression {

    private static final String TAG = "ExoPlayerCompression";

    public ExoPlayerCompression() {
    }

    public void compressFile(Context context, File inputFile, final File outputFile, CompressionListener listener) throws IOException {

        Looper eventLooper = Looper.myLooper() != null ? Looper.myLooper() : Looper.getMainLooper();
        Handler eventHandler = new Handler(eventLooper);

        DefaultBandwidthMeter bandwidthMeter = new DefaultBandwidthMeter();
        TrackSelection.Factory videoTrackSelectionFactory = new AdaptiveTrackSelection.Factory(bandwidthMeter);
        LoadControl loadControl = new DefaultLoadControl();
        TrackSelector trackSelector = new DefaultTrackSelector(videoTrackSelectionFactory);
        if (outputFile.exists()) {
            outputFile.delete();
        }
        MediaMuxerControl mediaMuxerControl = new MediaMuxerControl(outputFile);
        CompressionParameters compressionSettings = new CompressionParameters();
//        compressionSettings.getVideoCompressionParameters().setAllowSkippingFrames(true); // render every frame (slow!)
        compressionSettings.setAddAudioTrack(false);
        CompressionRenderersFactory renderersFactory = new CompressionRenderersFactory(context, mediaMuxerControl, compressionSettings);
        SimpleExoPlayer player = ExoPlayerFactory.newSimpleInstance(renderersFactory, trackSelector, loadControl);
        ExtractorMediaSource.Factory factory = new ExtractorMediaSource.Factory(new FileDataSourceFactory());
        ExtractorsFactory extractorsFactory = new DefaultExtractorsFactory();
        factory.setExtractorsFactory(extractorsFactory);

        CompressionListener listenerWrapper = new CompressionListenerWrapper(listener) {
            @Override
            public void onCompressionComplete() {
                makeTranscodedFileStreamable(outputFile);
                super.onCompressionComplete();
            }
        };
        listenerWrapper.onCompressionStarted();
        Uri videoUri = Uri.fromFile(inputFile);
        ExtractorMediaSource videoSource = factory.createMediaSource(videoUri);
        player.prepare(videoSource);
        PlaybackParameters playbackParams = new PlaybackParameters(1.0f);
        player.setPlaybackParameters(playbackParams);
        player.setPlayWhenReady(true);
        player.addListener(mediaMuxerControl.getPlaybackListener(listenerWrapper));
        eventHandler.postDelayed(new CompressionProgressListener(eventHandler, player, mediaMuxerControl, listenerWrapper), 1000);

        //TODO load data file into player and play it (ideally fast forwarded) to the compression renderer
    }

    private void makeTranscodedFileStreamable(File input) {
        File tmpFile = new File(input.getParentFile(), input.getName() + ".streaming.mp4");
        try {
            QtFastStart.fastStart(input, tmpFile);
            if (tmpFile.exists()) {
                boolean deletedOriginal = input.delete();
                if (!deletedOriginal) {
                    Crashlytics.log(Log.ERROR, TAG, "Error deleting streaming input file");
                }
                boolean renamed = tmpFile.renameTo(new File(tmpFile.getParentFile(), input.getName()));
                if (!renamed) {
                    Crashlytics.log(Log.ERROR, TAG, "Error renaming streaming output file");
                }
            } else {
                Crashlytics.log(Log.ERROR, TAG, "Error enabling streaming for MP4");
            }
        } catch (IOException e) {
            Crashlytics.log(Log.ERROR, TAG, "Error enabling streaming for transcoded MP4");
            Crashlytics.logException(e);
        } catch (QtFastStart.MalformedFileException e) {
            Crashlytics.log(Log.ERROR, TAG, "Error enabling streaming for transcoded MP4");
            Crashlytics.logException(e);
        } catch (QtFastStart.UnsupportedFileException e) {
            Crashlytics.log(Log.ERROR, TAG, "Error enabling streaming for transcoded MP4");
            Crashlytics.logException(e);
        }
    }

    public interface CompressionListener {

        void onCompressionStarted();

        void onCompressionError(Exception e);

        void onCompressionComplete();

        void onCompressionProgress(double compressionProgress, long mediaDurationMs);
    }

    private static class CompressionListenerWrapper implements CompressionListener {
        CompressionListener wrapped;

        public CompressionListenerWrapper(CompressionListener wrapped) {
            this.wrapped = wrapped;
        }

        @Override
        public void onCompressionStarted() {
            wrapped.onCompressionStarted();
        }

        @Override
        public void onCompressionError(Exception e) {
            wrapped.onCompressionError(e);
        }

        @Override
        public void onCompressionComplete() {
            wrapped.onCompressionComplete();
        }

        @Override
        public void onCompressionProgress(double compressionProgress, long mediaDurationMs) {
            wrapped.onCompressionProgress(compressionProgress, mediaDurationMs);
        }
    }

    public static class VideoCompressionParameters {
        private int wantedWidthPx = -1;
        private int wantedHeightPx = -1;
        private int wantedKeyFrameRate = 30; //30
        private int wantedKeyFrameInterval = 3; // 3 in seconds where 0 is every frame and -1 is only a single key frame
        private int wantedBitRate = -1;
        private int wantedBitRateModeV21 = -1;
        private boolean isAllowSkippingFrames;
        private boolean needMediaClock;

        public VideoCompressionParameters() {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                //KEY_BITRATE_MODE api21+ - BITRATE_MODE_VBR / BITRATE_MODE_CBR / BITRATE_MODE_CQ
                wantedBitRateModeV21 = BITRATE_MODE_CBR;
            }
        }

        public int getWantedWidthPx() {
            return wantedWidthPx;
        }

        public void setWantedWidthPx(int wantedWidthPx) {
            this.wantedWidthPx = wantedWidthPx;
        }

        public int getWantedHeightPx() {
            return wantedHeightPx;
        }

        public void setWantedHeightPx(int wantedHeightPx) {
            this.wantedHeightPx = wantedHeightPx;
        }

        public int getWantedFrameRate() {
            return wantedKeyFrameRate;
        }

        public void setWantedKeyFrameRate(int wantedKeyFrameRate) {
            this.wantedKeyFrameRate = wantedKeyFrameRate;
        }

        /**
         * 3 in seconds where 0 is every frame and -1 is only a single key frame
         *
         * @return
         */
        public int getWantedKeyFrameInterval() {
            return wantedKeyFrameInterval;
        }

        public void setWantedKeyFrameInterval(int wantedKeyFrameInterval) {
            this.wantedKeyFrameInterval = wantedKeyFrameInterval;
        }

        public int getWantedBitRate() {
            return wantedBitRate;
        }

        public void setWantedBitRate(int wantedBitRate) {
            this.wantedBitRate = wantedBitRate;
        }

        public int getWantedBitRateModeV21() {
            return wantedBitRateModeV21;
        }

        public void setWantedBitRateModeV21(int wantedBitRateModeV21) {
            this.wantedBitRateModeV21 = wantedBitRateModeV21;
        }

        public boolean isAllowSkippingFrames() {
            return isAllowSkippingFrames;
        }

        public void setAllowSkippingFrames(boolean allowSkippingFrames) {
            isAllowSkippingFrames = allowSkippingFrames;
        }

        public boolean isNeedMediaClock() {
            return needMediaClock;
        }

        public void setNeedMediaClock(boolean needMediaClock) {
            this.needMediaClock = needMediaClock;
        }
    }

    public static class CompressionParameters {
        private boolean addAudioTrack;
        private boolean addVideoTrack;
        private VideoCompressionParameters videoCompressionParameters;

        public CompressionParameters() {
            addAudioTrack = true;
            addVideoTrack = true;
            videoCompressionParameters = new VideoCompressionParameters();
        }

        public boolean isAddVideoTrack() {
            return addVideoTrack;
        }

        public void setAddVideoTrack(boolean addVideoTrack) {
            this.addVideoTrack = addVideoTrack;
            videoCompressionParameters.setNeedMediaClock(addVideoTrack && !addAudioTrack);
        }

        public boolean isAddAudioTrack() {
            return addAudioTrack;
        }

        public void setAddAudioTrack(boolean addAudioTrack) {
            this.addAudioTrack = addAudioTrack;
            videoCompressionParameters.setNeedMediaClock(addVideoTrack && !addAudioTrack);
        }

        public VideoCompressionParameters getVideoCompressionParameters() {
            return videoCompressionParameters;
        }
    }


}
