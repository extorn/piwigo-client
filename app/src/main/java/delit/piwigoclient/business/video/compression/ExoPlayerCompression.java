package delit.piwigoclient.business.video.compression;

import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.RequiresApi;
import androidx.documentfile.provider.DocumentFile;

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
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory;
import com.google.android.exoplayer2.util.MimeTypes;

import java.io.File;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.math.BigInteger;
import java.util.ArrayList;

import delit.libs.util.IOUtils;
import delit.piwigoclient.BuildConfig;

import static android.media.MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CBR;


@RequiresApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
public class ExoPlayerCompression {

    private static final String TAG = "ExoPlayerCompression";
    private static final boolean VERBOSE = false;
    private ArrayList<ExoPlayerCompressionThread> activeCompressionThreads = new ArrayList<>(1);
    private Context context;


    public ExoPlayerCompression() {
    }

    public void invokeFileCompression(final Context context, final Uri inputFile, final Uri outputFile, final CompressionListener listener, final CompressionParameters compressionSettings) {
        this.context = context;
        ExoPlayerCompressionThread thread = new ExoPlayerCompressionThread(context, inputFile, outputFile, listener, compressionSettings);
        synchronized (activeCompressionThreads) {
            activeCompressionThreads.add(thread);
        }
        thread.start();

    }

    public void cancel() {
        for (ExoPlayerCompressionThread th : activeCompressionThreads) {
            th.cancel();
        }
        synchronized (activeCompressionThreads) {
            while (activeCompressionThreads.size() > 0) {
                try {
                    activeCompressionThreads.wait();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "Video Compressors have all exited");
        }
    }

    private static class PlayerMonitor extends Player.DefaultEventListener {
        private final CompressionListener listenerWrapper;
        private final Looper looper;
        private final MediaMuxerControl mediaMuxerControl;
        private SimpleExoPlayer player;

        public PlayerMonitor(SimpleExoPlayer player, MediaMuxerControl mediaMuxerControl, CompressionListener listenerWrapper, Looper looper) {
            this.mediaMuxerControl = mediaMuxerControl;
            this.listenerWrapper = listenerWrapper;
            this.player = player;
            this.looper = looper;
        }

        @Override
        public void onPlayerError(ExoPlaybackException error) {
            listenerWrapper.onCompressionError(error);
        }

        @Override
        public void onPlayerStateChanged(boolean playWhenReady, int playbackState) {
            if (playbackState == Player.STATE_ENDED || playbackState == Player.STATE_IDLE) {
                player.release();
                player = null;
                if (mediaMuxerControl.isHasAudio() || mediaMuxerControl.isHasVideo()) {
                    mediaMuxerControl.safeShutdown();
                }
                looper.quitSafely();
            }
            super.onPlayerStateChanged(playWhenReady, playbackState);
        }
    }

    private void makeTranscodedFileStreamable(Uri fileUri) {
        if (VERBOSE) {
            Log.d(TAG, "Enabling streaming for transcoded MP4");
        }

        try {
            String filePath = fileUri.getPath();
            if(filePath == null) {
                throw new IOException("Uri to make streamable does not represent a local file : " + fileUri);
            }
            File fileAsFile = new File(filePath);
            File tmpFile = new File(fileAsFile.getParentFile(), fileAsFile.getName() + ".streaming.mp4");

            // TODO use the exoplayer functions to move the MOOV atom to the front.
//            DefaultDataSourceFactory factory = new DefaultDataSourceFactory();
//            factory.createDataSource()
//            new Mp4Extractor();
//            new DataSpec(fileUri)

            boolean wroteFastStartFile = MyFastStart.fastStart(context, fileUri);
            if (wroteFastStartFile) {
                boolean deletedOriginal = fileAsFile.delete();
                if (!deletedOriginal) {
                    Crashlytics.log(Log.ERROR, TAG, "Error deleting streaming input file");
                }
                boolean renamed = tmpFile.renameTo(new File(tmpFile.getParentFile(), fileAsFile.getName()));
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

        public static final int AUDIO_PASSTHROUGH_BITRATE = -1;
        private int bitRate = 128000;
        private final long maxInterleavingIntervalUs;

        public AudioCompressionParameters(final long maxInterleavingIntervalUs) {
            this.maxInterleavingIntervalUs = maxInterleavingIntervalUs;
        }

        public int getBitRate() {
            return bitRate;
        }

        public void setBitRate(int bitRate) {
            if (bitRate > 0) {
                this.bitRate = bitRate;
            }
        }

        public long getMaxInterleavingIntervalUs() {
            return maxInterleavingIntervalUs;
        }
    }
    public static class VideoCompressionParameters {
        private final long maxInterleavingIntervalUs;
        private int wantedWidthPx = -1;
        private int wantedHeightPx = -1;
        private int wantedFrameRate = 30; //30
        private int wantedKeyFrameInterval = 3; // 3 in seconds where 0 is every frame and -1 is only a single key frame
        private double wantedBitRatePerPixelPerSecond = 0.1;
        private int wantedBitRateModeV21 = BITRATE_MODE_CBR;
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
            return wantedFrameRate;
        }

        public void setWantedFrameRate(int wantedFrameRate) {
            this.wantedFrameRate = wantedFrameRate;
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

        public void setWantedBitRatePerPixelPerSecond(double wantedBitRatePerPixelPerSecond) {
            if (wantedBitRatePerPixelPerSecond > 0) {
                this.wantedBitRatePerPixelPerSecond = wantedBitRatePerPixelPerSecond;
            }
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

        public int getWantedBitRate(int width, int height, int fps) {
            long bitRate = Math.round(width * height * fps * wantedBitRatePerPixelPerSecond);
            return BigInteger.valueOf(bitRate).intValue();
        }
    }

    public static class CompressionParameters {
        private final AudioCompressionParameters audioCompressionParameters;
        private long maxInterleavingIntervalUs = 500000; // 500ms
        private boolean addAudioTrack;
        private boolean addVideoTrack;
        private VideoCompressionParameters videoCompressionParameters;
        private boolean isEnableFastStart = true;

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

        public void disableFastStart() {
            isEnableFastStart = false;
        }

        public VideoCompressionParameters getVideoCompressionParameters() {
            return videoCompressionParameters;
        }

        public AudioCompressionParameters getAudioCompressionParameters() {
            return audioCompressionParameters;
        }

        public String getOutputFileMimeType() {
            return MimeTypes.VIDEO_MP4;
        }

        public boolean isEnableFastStart() {
            return isEnableFastStart;
        }
    }


    private class InternalCompressionListener extends CompressionListenerWrapper {

        private final Uri inputFile;
        private final Uri outputFile;
        private long mediaDurationMs;
        private boolean enableFastStart;

        public InternalCompressionListener(CompressionListener wrapped, Uri inputFile, Uri outputFile, boolean enableFastStart) {
            super(wrapped);
            this.inputFile = inputFile;
            this.outputFile = outputFile;
        }

        @Override
        public void onCompressionProgress(double compressionProgress, long mediaDurationMs) {
            super.onCompressionProgress(compressionProgress, mediaDurationMs);
            this.mediaDurationMs = mediaDurationMs;
        }

        @Override
        public void onCompressionComplete() {
            DocumentFile inputDocFile = DocumentFile.fromSingleUri(context, inputFile);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                if(inputDocFile != null) {
                    if(!IOUtils.setLastModified(context, outputFile, inputDocFile.lastModified())) {
                        Crashlytics.log(Log.WARN, TAG, "Unable to set last modified date on compressed video " + outputFile);
                    }
                }
            }
            if (enableFastStart) {
                makeTranscodedFileStreamable(outputFile);
            }
            super.onCompressionProgress(100d, mediaDurationMs);
            super.onCompressionComplete();
        }

        @Override
        public void onCompressionError(Exception e) {
            super.onCompressionError(e);
        }
    }

    private class ExoPlayerCompressionThread extends HandlerThread {

        private final CompressionParameters compressionSettings;
        private final CompressionListener listener;
        private final Uri outputFile;
        private final Uri inputFile;
        private final WeakReference<Context> contextRef;
        private SimpleExoPlayer player;
        private boolean cancelled;

        public ExoPlayerCompressionThread(Context context, Uri inputFile, Uri outputFile, CompressionListener listener, CompressionParameters compressionSettings) {
            super("mainFileCompressorThread");
            this.contextRef = new WeakReference<>(context);
            this.inputFile = inputFile;
            this.outputFile = outputFile;
            this.listener = listener;
            this.compressionSettings = compressionSettings;
        }

        @Override
        protected void onLooperPrepared() {
            try {
                invokeCompressor(compressionSettings);
            } catch (RuntimeException e) {
                Crashlytics.logException(e);
                listener.onCompressionError(e);
            } catch (IOException e) {
                Crashlytics.logException(e);
                listener.onCompressionError(e);
            }
        }

        @Override
        public void run() {
            try {
                super.run();
                synchronized (activeCompressionThreads) {
                    activeCompressionThreads.remove(this);
                    activeCompressionThreads.notifyAll();
                }
            } catch(Exception e) {
                Crashlytics.log(Log.ERROR, TAG, "Unexpected error in exo player compression listener thread. Cancelling compression.");
                Crashlytics.logException(e);
                Log.e(TAG, "Unexpected error in exo player compression listener thread. Cancelling compression.", e);
                cancel();
            }
        }

        private void invokeCompressor(CompressionParameters compressionSettings) throws IOException {
            TrackSelection.Factory videoTrackSelectionFactory = new AdaptiveTrackSelection.Factory(new DefaultBandwidthMeter());
            LoadControl loadControl = new DefaultLoadControl();
            TrackSelector trackSelector = new DefaultTrackSelector(videoTrackSelectionFactory);

            CompressionListener listenerWrapper = new InternalCompressionListener(listener, inputFile, outputFile, compressionSettings.isEnableFastStart());

            MediaMuxerControl mediaMuxerControl;
            mediaMuxerControl = new MediaMuxerControl(contextRef.get(), inputFile, outputFile, listenerWrapper);

            CompressionRenderersFactory renderersFactory = new CompressionRenderersFactory(contextRef.get(), mediaMuxerControl, compressionSettings);
            player = ExoPlayerFactory.newSimpleInstance(renderersFactory, trackSelector, loadControl);
            PlayerMonitor playerMonitor = new PlayerMonitor(player, mediaMuxerControl, listenerWrapper, Looper.myLooper());
            player.addListener(playerMonitor); // watch for errors and report them
            ExtractorMediaSource.Factory factory = new ExtractorMediaSource.Factory(new DefaultDataSourceFactory(contextRef.get(), "PiwigoCompression"));
            ExtractorsFactory extractorsFactory = new DefaultExtractorsFactory();
            factory.setExtractorsFactory(extractorsFactory);
            listenerWrapper.onCompressionStarted();
            ExtractorMediaSource videoSource = factory.createMediaSource(inputFile);
            player.prepare(videoSource);
            PlaybackParameters playbackParams = new PlaybackParameters(1.0f);
            player.setPlaybackParameters(playbackParams);
            player.setPlayWhenReady(true);

            Handler progressHandler = new Handler(getLooper());
            progressHandler.postDelayed(new CompressionProgressListener(progressHandler, player, mediaMuxerControl, listenerWrapper), 1000);
            if (cancelled) {
                DocumentFile outputDocFile = DocumentFile.fromSingleUri(contextRef.get(), outputFile);
                if (outputDocFile.exists() && !outputDocFile.delete()) {
                    Crashlytics.log(Log.ERROR, TAG, "Unable to delete output file after compression cancelled");
                }
            }
        }

        public void cancel() {
            cancelled = true;
            player.stop(); // will cause the thread to shutdown safely once all messages have been processed.
        }
    }
}
