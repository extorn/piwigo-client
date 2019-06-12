package delit.piwigoclient.business.video.compression;

import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.RequiresApi;

import com.crashlytics.android.Crashlytics;
import com.google.android.exoplayer2.DefaultLoadControl;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.ExoPlayerFactory;
import com.google.android.exoplayer2.LoadControl;
import com.google.android.exoplayer2.PlaybackParameters;
import com.google.android.exoplayer2.Player;
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

import static android.media.MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CBR;


@RequiresApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
public class ExoPlayerCompression {

    private static final String TAG = "ExoPlayerCompression";
    private static final boolean VERBOSE = false;

    public ExoPlayerCompression() {
    }

    public void invokeFileCompression(final Context context, final File inputFile, final File outputFile, final CompressionListener listener) {
        new HandlerThread("mainFileCompressorThread") {
            @Override
            protected void onLooperPrepared() {
                try {
                    invokeCompressor();
                } catch (RuntimeException e) {
                    listener.onCompressionError(e);
                } catch (IOException e) {
                    listener.onCompressionError(e);
                }
            }

            private void invokeCompressor() throws IOException {
                TrackSelection.Factory videoTrackSelectionFactory = new AdaptiveTrackSelection.Factory(new DefaultBandwidthMeter());
                LoadControl loadControl = new DefaultLoadControl();
                TrackSelector trackSelector = new DefaultTrackSelector(videoTrackSelectionFactory);

                CompressionListener listenerWrapper = new InternalCompressionListener(listener, outputFile);

                MediaMuxerControl mediaMuxerControl;
                mediaMuxerControl = new MediaMuxerControl(inputFile, outputFile, listenerWrapper);

                CompressionParameters compressionSettings = new CompressionParameters();
                compressionSettings.getVideoCompressionParameters().setWantedBitRate(8000 * 250);

                CompressionRenderersFactory renderersFactory = new CompressionRenderersFactory(context, mediaMuxerControl, compressionSettings);
                SimpleExoPlayer player = ExoPlayerFactory.newSimpleInstance(renderersFactory, trackSelector, loadControl);
                player.addListener(new PlayerMonitor(listenerWrapper)); // watch for errors and report them
                ExtractorMediaSource.Factory factory = new ExtractorMediaSource.Factory(new FileDataSourceFactory());
                ExtractorsFactory extractorsFactory = new DefaultExtractorsFactory();
                factory.setExtractorsFactory(extractorsFactory);
                listenerWrapper.onCompressionStarted();
                Uri videoUri = Uri.fromFile(inputFile);
                ExtractorMediaSource videoSource = factory.createMediaSource(videoUri);
                player.prepare(videoSource);
                PlaybackParameters playbackParams = new PlaybackParameters(1.0f);
                player.setPlaybackParameters(playbackParams);
                player.setPlayWhenReady(true);

                Handler progressHandler = new Handler(getLooper());
                progressHandler.postDelayed(new CompressionProgressListener(progressHandler, player, mediaMuxerControl, listenerWrapper), 1000);
            }
        }.start();

    }

    private static class PlayerMonitor extends Player.DefaultEventListener {
        private final CompressionListener listenerWrapper;

        public PlayerMonitor(CompressionListener listenerWrapper) {
            this.listenerWrapper = listenerWrapper;
        }

        @Override
        public void onPlayerError(ExoPlaybackException error) {
            listenerWrapper.onCompressionError(error);
        }

        @Override
        public void onPlayerStateChanged(boolean playWhenReady, int playbackState) {
            if (playbackState == Player.STATE_ENDED) {
                Looper.myLooper().quitSafely();
            }
            super.onPlayerStateChanged(playWhenReady, playbackState);
        }
    }

    private void makeTranscodedFileStreamable(File input) {
        if (VERBOSE) {
            Log.d(TAG, "Enabling streaming for transcoded MP4");
        }
        File tmpFile = new File(input.getParentFile(), input.getName() + ".streaming.mp4");
        try {
            boolean wroteFastStartFile = QtFastStart.fastStart(input, tmpFile);
            if (wroteFastStartFile) {
                boolean deletedOriginal = input.delete();
                if (!deletedOriginal) {
                    Crashlytics.log(Log.ERROR, TAG, "Error deleting streaming input file");
                }
                boolean renamed = tmpFile.renameTo(new File(tmpFile.getParentFile(), input.getName()));
                if (!renamed) {
                    Crashlytics.log(Log.ERROR, TAG, "Error renaming streaming output file");
                }
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
        private final CompressionListener wrapped;

        public CompressionListenerWrapper(CompressionListener wrapped) {
            this.wrapped = wrapped;
        }

        public CompressionListener getWrapped() {
            return wrapped;
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


    public static class AudioCompressionParameters {

        private final long maxInterleavingIntervalUs;

        public AudioCompressionParameters(final long maxInterleavingIntervalUs) {
            this.maxInterleavingIntervalUs = maxInterleavingIntervalUs;
        }

        public long getMaxInterleavingIntervalUs() {
            return maxInterleavingIntervalUs;
        }
    }
    public static class VideoCompressionParameters {
        private final long maxInterleavingIntervalUs;
        private int wantedWidthPx = -1;
        private int wantedHeightPx = -1;
        private int wantedKeyFrameRate = 30; //30
        private int wantedKeyFrameInterval = 3; // 3 in seconds where 0 is every frame and -1 is only a single key frame
        private int wantedBitRate = -1;
        private int wantedBitRateModeV21 = -1;
        private boolean isAllowSkippingFrames;
        private boolean isHardRotateVideo;

        public VideoCompressionParameters(final long maxInterleavingIntervalMs) {
            this.maxInterleavingIntervalUs = maxInterleavingIntervalMs;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                //KEY_BITRATE_MODE api21+ - BITRATE_MODE_VBR / BITRATE_MODE_CBR / BITRATE_MODE_CQ
                wantedBitRateModeV21 = BITRATE_MODE_CBR;
            }
        }

        public long getMaxInterleavingIntervalUs() {
            return maxInterleavingIntervalUs;
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

        public boolean isHardRotateVideo() {
            return isHardRotateVideo;
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

        /**
         * If true, the video may literally be saved in portrait format with no rotate tag.
         * <br>
         * WARNING - This means it cannot be played by all media players, as some presume video
         * is in landscape mode with a rotate flag if needed!
         * </b>
         *
         * @param hardRotateVideo
         */
        public void setHardRotateVideo(boolean hardRotateVideo) {
            isHardRotateVideo = hardRotateVideo;
        }
    }

    public static class CompressionParameters {
        private final AudioCompressionParameters audioCompressionParameters;
        private long maxInterleavingIntervalUs = 500000; // 500ms
        private boolean addAudioTrack;
        private boolean addVideoTrack;
        private VideoCompressionParameters videoCompressionParameters;

        public CompressionParameters() {
            setAddVideoTrack(true);
            setAddAudioTrack(true);
            videoCompressionParameters = new VideoCompressionParameters(maxInterleavingIntervalUs);
            audioCompressionParameters = new AudioCompressionParameters(maxInterleavingIntervalUs);
        }

        public boolean isAddVideoTrack() {
            return addVideoTrack;
        }

        public void setAddVideoTrack(boolean addVideoTrack) {
            this.addVideoTrack = addVideoTrack;
        }

        public boolean isAddAudioTrack() {
            return addAudioTrack;
        }

        public void setAddAudioTrack(boolean addAudioTrack) {
            this.addAudioTrack = addAudioTrack;
        }

        public VideoCompressionParameters getVideoCompressionParameters() {
            return videoCompressionParameters;
        }

        public AudioCompressionParameters getAudioCompressionParameters() {
            return audioCompressionParameters;
        }
    }


    private class InternalCompressionListener extends CompressionListenerWrapper {

        private final File outputFile;
        private long mediaDurationMs;

        public InternalCompressionListener(CompressionListener wrapped, File outputFile) {
            super(wrapped);
            this.outputFile = outputFile;
        }

        @Override
        public void onCompressionProgress(double compressionProgress, long mediaDurationMs) {
            super.onCompressionProgress(compressionProgress, mediaDurationMs);
            this.mediaDurationMs = mediaDurationMs;
        }

        @Override
        public void onCompressionComplete() {
            makeTranscodedFileStreamable(outputFile);
            super.onCompressionProgress(100d, mediaDurationMs);
            super.onCompressionComplete();
        }

        @Override
        public void onCompressionError(Exception e) {
            super.onCompressionError(e);
            Looper.myLooper().quitSafely();
        }
    }
}
