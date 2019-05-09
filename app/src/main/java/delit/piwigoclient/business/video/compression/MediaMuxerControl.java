package delit.piwigoclient.business.video.compression;

import android.media.MediaCodec;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.os.Build;
import android.util.Log;

import com.google.android.exoplayer2.Player;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

import androidx.annotation.RequiresApi;

import static com.google.android.exoplayer2.Player.STATE_BUFFERING;
import static com.google.android.exoplayer2.Player.STATE_ENDED;
import static com.google.android.exoplayer2.Player.STATE_IDLE;
import static com.google.android.exoplayer2.Player.STATE_READY;

@RequiresApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
public class MediaMuxerControl {
    private static final String TAG = "MediaMuxerControl";
    private Map<String, MediaFormat> trackFormats;
    private MediaMuxer mediaMuxer;
    private boolean audioConfigured;
    private boolean videoConfigured;
    private boolean mediaMuxerStarted;
    private File outputFile;
    private boolean hasAudio;
    private boolean hasVideo;
    private long lastWrittenDataTimeUs;

    public MediaMuxerControl(File outputFile) throws IOException {
        this.outputFile = outputFile;
        mediaMuxer = buildMediaMuxer(outputFile);
        trackFormats = new HashMap<>(2);
    }

    public void markAudioConfigured() {
        if (!audioConfigured) {
            audioConfigured = true;
            Log.d(TAG, "Muxer : Audio Input configured");
        }
    }

    public long getLastWrittenDataTimeMs() {
        return Math.round(Math.floor(((double) lastWrittenDataTimeUs) / 1000));
    }

    public void markVideoConfigured() {
        if (!videoConfigured) {
            videoConfigured = true;
            Log.d(TAG, "Muxer : Video Input configured");
        }
    }

    public void reset() {
        Log.d(TAG, "resetting media muxer");
        audioConfigured = false;
        videoConfigured = false;
        mediaMuxerStarted = false;
        hasAudio = false;
        hasVideo = false;
        outputFile = null;
        trackFormats.clear();
        mediaMuxer = null;
    }

    public boolean startMediaMuxer() {
        if (isConfigured() && !mediaMuxerStarted) {
            try {
                // the media muxer is ready to start accepting data now from the encoder
                Log.d(TAG, "starting media muxer");
                mediaMuxer.start();
                mediaMuxerStarted = true;
            } catch (IllegalStateException e) {
                Log.d(TAG, "Error starting media muxer", e);
            }
            return true;
        }
        return mediaMuxerStarted;
    }

    public boolean stopMediaMuxer() {
        Log.d(TAG, "stopping media muxer");
        if (mediaMuxerStarted) {
            mediaMuxer.stop();
            return true;
        }
        return false;
    }

    public void writeSampleData(int outputTrackIndex, ByteBuffer encodedData, MediaCodec.BufferInfo info) {
        if (info.presentationTimeUs < lastWrittenDataTimeUs) {
            Log.e(TAG, "Muxing tracks out of step with each other!");
        } else {
            Log.d(TAG, String.format("Writing data for track %1$d at time %2$d", outputTrackIndex, lastWrittenDataTimeUs));
        }
        lastWrittenDataTimeUs = info.presentationTimeUs;
        mediaMuxer.writeSampleData(outputTrackIndex, encodedData, info);
    }

    public int getVideoTrackId() {
        if (trackFormats.containsKey("video")) {
            return 0;
        }
        throw new IllegalStateException("Video track not added to muxer");
    }

    public int getAudioTrackId() {
        if (trackFormats.containsKey("audio")) {
            return trackFormats.containsKey("video") ? 1 : 0;
        }
        throw new IllegalStateException("Audio track not added to muxer");
    }

    private MediaMuxer buildMediaMuxer() {
        Log.d(TAG, "Building new MediaMuxer");
        try {
            MediaMuxer mediaMuxer = buildMediaMuxer(outputFile);
            if (trackFormats.containsKey("video")) {
                mediaMuxer.addTrack(trackFormats.get("video"));
            }
            if (trackFormats.containsKey("audio")) {
                mediaMuxer.addTrack(trackFormats.get("audio"));
            }
            return mediaMuxer;
        } catch (IOException e) {
            throw new RuntimeException("Unable to recreat the media muxer", e);
        }

    }

    public void addAudioTrack(MediaFormat outputFormat) {
        Log.d(TAG, "Muxer : Adding Audio track");
        if (null != trackFormats.put("audio", outputFormat)) {
            // rebuild needed
            release();
        }
        mediaMuxer = buildMediaMuxer();
    }

    public void addVideoTrack(MediaFormat outputFormat) {
        Log.d(TAG, "Muxer : Adding Video track");
        if (null != trackFormats.put("video", outputFormat)) {
            // rebuild needed
            release();
        }
        mediaMuxer = buildMediaMuxer();
    }

    public boolean isConfigured() {
        return hasAudio == audioConfigured && hasVideo == videoConfigured;
    }

    public void release() {
        Log.d(TAG, "Muxer : releasing old muxer");
        mediaMuxer.release();
    }

    private MediaMuxer buildMediaMuxer(File outputFile) throws IOException {
        return new MediaMuxer(outputFile.getAbsolutePath(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
    }

    public void setHasVideo() {
        hasVideo = true;
    }

    public boolean isHasVideo() {
        return hasVideo;
    }

    public boolean hasVideoTrack() {
        return trackFormats.containsKey("video");
    }

    public boolean isHasAudio() {
        return hasAudio;
    }

    public void setHasAudio() {
        hasAudio = true;
    }

    public void videoRendererStopped() {
        if (hasVideo) {
            Log.d(TAG, "Video Rendering stopped");
            hasVideo = false;
            videoConfigured = false;
            if (!hasAudio) {
                release();
                reset();
            }
        }
    }

    public void audioRendererStopped() {
        if (hasAudio) {
            Log.d(TAG, "Audio Rendering stopped");
            hasAudio = false;
            audioConfigured = false;
            if (!hasVideo) {
                release();
                reset();
            }
        }
    }

    public boolean isAudioConfigured() {
        return audioConfigured;
    }

    public boolean isVideoConfigured() {
        return videoConfigured;
    }

    public PlaybackListener getPlaybackListener(ExoPlayerCompression.CompressionListener listener) {
        return new PlaybackListener(listener, this);
    }

    public boolean isReadyForAudio(long bufferPresentationTimeUs) {
        return bufferPresentationTimeUs - lastWrittenDataTimeUs <= 1000;
    }


    public static class PlaybackListener extends Player.DefaultEventListener {

        private final MediaMuxerControl mediaMuxerControl;
        private ExoPlayerCompression.CompressionListener listener;

        public PlaybackListener(ExoPlayerCompression.CompressionListener listener, MediaMuxerControl mediaMuxerControl) {
            this.listener = listener;
            this.mediaMuxerControl = mediaMuxerControl;
        }

        @Override
        public void onPlayerStateChanged(boolean playWhenReady, int playbackState) {
            switch (playbackState) {
                case STATE_READY:
                    Log.e(TAG, "PLAYER STATE : READY");
                    break;
                case STATE_BUFFERING:
                    Log.e(TAG, "PLAYER STATE : BUFFERING");
                    break;
                case STATE_IDLE:
                    Log.e(TAG, "PLAYER STATE : IDLE");
                    break;
                case STATE_ENDED:
                    Log.e(TAG, "PLAYER STATE : ENDED");
                    break;
            }

            if (playbackState == Player.STATE_ENDED) {
                listener.onCompressionComplete();
            }
        }
    }
}