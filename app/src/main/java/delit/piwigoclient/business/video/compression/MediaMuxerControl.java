package delit.piwigoclient.business.video.compression;

import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMetadataRetriever;
import android.media.MediaMuxer;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.RequiresApi;

import com.crashlytics.android.Crashlytics;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeSet;

import delit.piwigoclient.util.IOUtils;

@RequiresApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
public class MediaMuxerControl /*implements MetadataOutput*/ {
    private static final String TAG = "MediaMuxerControl";
    private static final boolean VERBOSE = true;
    private final File inputFile;
    private final ExoPlayerCompression.CompressionListener listener;
    private final Handler eventHandler;
    private Map<String, MediaFormat> trackFormats;
    private Map<String, TrackStats> trackStatistics;
    private MediaMuxer mediaMuxer;
    private boolean audioConfigured;
    private boolean videoConfigured;
    private boolean mediaMuxerStarted;
    private File outputFile;
    private boolean hasAudio;
    private boolean hasVideo;
    private long lastWrittenDataTimeUs;
    private boolean isFinished;
    private int rotationDegrees;
    private Float latitude = Float.MAX_VALUE;
    private Float longitude = Float.MAX_VALUE;
    private MediaFormat inputVideoFormat;
    private boolean sourceDataRead;
    private TreeSet<Sample> queuedData = new TreeSet<>();

    public MediaMuxerControl(File inputFile, File outputFile, ExoPlayerCompression.CompressionListener listener) throws IOException {
        this.inputFile = inputFile;
        this.outputFile = outputFile;
        extractLocationData(inputFile);
        extractInputVideoFormat(inputFile);
        mediaMuxer = buildMediaMuxer(outputFile);
        trackFormats = new HashMap<>(2);
        trackStatistics = new HashMap<>(2);
        this.listener = listener;
        Looper looper = Looper.myLooper();
        if (looper == null) {
            throw new IllegalStateException("MediaMuxer control must run on a Handler thread (or one with a looper anyway!");
        }
        this.eventHandler = new Handler(looper);
    }

    private void extractInputVideoFormat(File inputFile) throws IOException {
        MediaExtractor mExtractor = new MediaExtractor();
        mExtractor.setDataSource(inputFile.getPath());
        int tracks = mExtractor.getTrackCount();
        for (int i = 0; i < tracks; i++) {
            inputVideoFormat = mExtractor.getTrackFormat(i);
            String mime = inputVideoFormat.getString(MediaFormat.KEY_MIME);
            if (mime.startsWith("video/")) {
                break;
            }
        }
        mExtractor.release();
    }

    public void markAudioConfigured() {
        if (!audioConfigured) {
            audioConfigured = true;
            if (VERBOSE) {
                Log.d(TAG, "Muxer : Audio Input configured");
            }
        }
    }

    public long getLastWrittenDataTimeMs() {
        return Math.round(Math.floor(((double) lastWrittenDataTimeUs) / 1000));
    }

    public long getLastWrittenDataTimeUs() {
        return lastWrittenDataTimeUs;
    }

    public void markVideoConfigured() {
        if (!videoConfigured) {
            videoConfigured = true;
            if (VERBOSE) {
                Log.d(TAG, "Muxer : Video Input configured");
            }
        }
    }

    public void reset() {
        if (VERBOSE) {
            Log.d(TAG, "resetting media muxer");
        }
        audioConfigured = false;
        videoConfigured = false;
        mediaMuxerStarted = false;
        hasAudio = false;
        hasVideo = false;
        outputFile = null;
        trackFormats.clear();
        trackStatistics.clear();
        mediaMuxer = null;
        isFinished = false;
    }

    public boolean startMediaMuxer() {
        if (isConfigured() && !mediaMuxerStarted) {
            try {
                // the media muxer is ready to start accepting data now from the encoder
                if (VERBOSE) {
                    Log.d(TAG, "starting media muxer");
                }
                mediaMuxer.start();
                mediaMuxerStarted = true;
            } catch (IllegalStateException e) {
                Crashlytics.log(Log.ERROR, TAG, "Error starting media muxer");
                Crashlytics.logException(e);
            }
            return true;
        }
        return mediaMuxerStarted;
    }

    public boolean isMediaMuxerStarted() {
        return mediaMuxerStarted;
    }

    public boolean stopMediaMuxer() {
        if (VERBOSE) {
            Log.d(TAG, "stopping media muxer");
        }
        if (mediaMuxerStarted) {
            mediaMuxer.stop();
            return true;
        }
        return false;
    }

    private final void extractLocationData(File inputFile) {
        MediaMetadataRetriever metadataRetriever = new MediaMetadataRetriever();
        metadataRetriever.setDataSource(inputFile.getPath());
        String location = metadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_LOCATION);
        if (location != null) {
            //Lat = ±DDMM.MMMM & Lon = ±DDDMM.MMMM
            int splitAt = Math.max(location.lastIndexOf('-'), location.lastIndexOf('+'));
            latitude = Float.valueOf(location.substring(0, splitAt));
            longitude = Float.valueOf(location.substring(splitAt));
        }
        metadataRetriever.release();
    }

    public boolean hasAudioDataQueued() {
        try {
            int audioTrack = getAudioTrackId();
            for (Sample s : queuedData) {
                if (s.outputTrackIndex == audioTrack) {
                    return true;
                }
            }
        } catch (IllegalStateException e) {
            // ignore.
        }
        return false;
    }

    public boolean hasVideoDataQueued() {
        try {
            int videoTrack = getVideoTrackId();
            for (Sample s : queuedData) {
                if (s.outputTrackIndex == videoTrack) {
                    return true;
                }
            }
        } catch (IllegalStateException e) {
            // ignore.
        }
        return false;
    }

    public void markDataRead() {
        this.sourceDataRead = true;
    }

    public boolean isSourceDataRead() {
        return sourceDataRead;
    }

    public void writeSampleData(int outputTrackIndex, ByteBuffer encodedData, MediaCodec.BufferInfo info, Long originalBytes) {

        TrackStats thisTrackStats = trackStatistics.get(getTrackName(outputTrackIndex));
        thisTrackStats.addOriginalBytesTranscoded(originalBytes);

        int trackCount = trackFormats.size();

        if (queuedData.isEmpty() && trackCount == 2) {
            ByteBuffer data = IOUtils.deepCopyVisible(encodedData);
            queuedData.add(new Sample(outputTrackIndex, data, info));
        } else {
            boolean canWrite = trackCount != 2;
            Sample firstItem;
            if (canWrite) {
                // there's only this track in the muxer to write - lets write all items in the queue and the new item (ordered correctly)
                queuedData.add(new Sample(outputTrackIndex, encodedData, info)); // no need to clone it as we're writing it right now
                do {
                    firstItem = queuedData.pollFirst();
                    writeSampleDataToMuxer(firstItem.outputTrackIndex, firstItem.buffer, firstItem.getBufferInfo());
                } while (!queuedData.isEmpty()); // can finish queue as we're closing down...
            } else { // queued data has at least one item in it
                firstItem = queuedData.first();
                // test this first item against the new one (most likely to be different)
                canWrite = firstItem.outputTrackIndex != outputTrackIndex;
                if (canWrite) {
                    queuedData.add(new Sample(outputTrackIndex, encodedData, info)); // no need to clone it as we're writing it right now
                    do {
                        firstItem = queuedData.pollFirst();
                        writeSampleDataToMuxer(firstItem.outputTrackIndex, firstItem.buffer, firstItem.getBufferInfo());
                    } while (queuedData.size() > 1); // leave one item since the next rxd may be earlier.
                } else {
                    ByteBuffer data = IOUtils.deepCopyVisible(encodedData);
                    queuedData.add(new Sample(outputTrackIndex, data, info));
                }
            }
        }
    }

    private void writeSampleDataToMuxer(int outputTrackIndex, ByteBuffer encodedData, MediaCodec.BufferInfo info) {
        TrackStats thisTrackStats = trackStatistics.get(getTrackName(outputTrackIndex));
        thisTrackStats.addTranscodedBytesWritten(encodedData.remaining());
        lastWrittenDataTimeUs = info.presentationTimeUs;
        mediaMuxer.writeSampleData(outputTrackIndex, encodedData, info);
    }

    public void release() {
        if (VERBOSE) {
            Log.d(TAG, "Muxer : releasing old muxer");
        }
        if (this.mediaMuxer != null) {
            this.mediaMuxer.release();
        }
    }

    private String getTrackName(int outputTrackIndex) {
        return outputTrackIndex == getVideoTrackId() ? "video" : "audio";
    }

    public boolean isFinished() {
        return isFinished;
    }

    /**
     * @return percentage 0 - 1
     */
    public double getOverallProgress() {
        long bytesTranscoded = 0;
        TrackStats trackStats = trackStatistics.get("video");
        if (trackStats != null) {
            bytesTranscoded += trackStats.getOriginalBytesTranscoded();
        }
        trackStats = trackStatistics.get("audio");
        if (trackStats != null) {
            bytesTranscoded += trackStats.getOriginalBytesTranscoded();
        }
        double value = BigDecimal.valueOf(bytesTranscoded).divide(BigDecimal.valueOf(inputFile.length()), new MathContext(2, RoundingMode.DOWN)).doubleValue();
        return value;
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
        if (VERBOSE) {
            Log.d(TAG, "Building new MediaMuxer");
        }
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
        if (VERBOSE) {
            Log.d(TAG, "Muxer : Adding Audio track");
        }
        if (null != trackFormats.put("audio", outputFormat)) {
            // rebuild needed
            release();
        } else {
            trackStatistics.put("audio", new TrackStats());
        }
        mediaMuxer = buildMediaMuxer();
    }

    public void addVideoTrack(MediaFormat outputFormat) {
        if (VERBOSE) {
            Log.d(TAG, "Muxer : Adding Video track");
        }
        if (null != trackFormats.put("video", outputFormat)) {
            // rebuild needed
            release();
        } else {
            trackStatistics.put("video", new TrackStats());
        }
        mediaMuxer = buildMediaMuxer();
    }

    public boolean isConfigured() {
        return hasAudio == audioConfigured && hasVideo == videoConfigured;
    }

    private MediaMuxer buildMediaMuxer(File outputFile) throws IOException {
        release();
        MediaMuxer muxer = new MediaMuxer(outputFile.getAbsolutePath(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
        this.mediaMuxer = muxer;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            if (Float.MAX_VALUE != latitude) {
                try {
                    mediaMuxer.setLocation(latitude, longitude);
                } catch (IllegalArgumentException e) {
                    Crashlytics.log(Log.ERROR, TAG, "Location data out of range - cannot copy to transcoded file");
                    Crashlytics.logException(e);
                }
            }
        }
        if (rotationDegrees > 0) {
            mediaMuxer.setOrientationHint(rotationDegrees);
        }
        return muxer;
    }

    public void setOrientationHint(int rotationDegrees) {
        this.rotationDegrees = rotationDegrees;
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
            if (VERBOSE) {
                Log.d(TAG, "Video Rendering stopped");
            }
            hasVideo = false;
            videoConfigured = false;
            if (!hasAudio) {
                release();
                isFinished = true;
                onCompressionComplete();
            }
        }
    }

    public void audioRendererStopped() {
        if (hasAudio) {
            if (VERBOSE) {
                Log.d(TAG, "Audio Rendering stopped");
            }
            hasAudio = false;
            audioConfigured = false;
            if (!hasVideo) {
                release();
                isFinished = true;
                onCompressionComplete();
            }
        }
    }

    private void onCompressionComplete() {
        eventHandler.post(new Runnable() {
            @Override
            public void run() {
                listener.onCompressionComplete();
            }
        });
    }

    public boolean isAudioConfigured() {
        return audioConfigured;
    }

    public boolean isVideoConfigured() {
        return videoConfigured;
    }

    public MediaFormat getTrueVideoInputFormat() {
        return inputVideoFormat;
    }

    private static class Sample implements Comparable<Sample> {
        private static MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo(); // static copy to save allocations
        int outputTrackIndex;
        ByteBuffer buffer;
        // Buffer Info stuff
        int offset;
        int size;
        int bufferFlags;
        long bufferPresentationTimeUs;

        public Sample(int outputTrackIndex, ByteBuffer buffer, MediaCodec.BufferInfo info) {
            this.outputTrackIndex = outputTrackIndex;
            this.buffer = buffer;
            this.offset = info.offset;
            this.size = info.size;
            this.bufferFlags = info.flags;
            this.bufferPresentationTimeUs = info.presentationTimeUs;
        }

        public MediaCodec.BufferInfo getBufferInfo() {
            bufferInfo.set(offset, size, bufferPresentationTimeUs, bufferFlags);
            return bufferInfo;
        }

        @Override
        public int compareTo(Sample o) {
            long x = bufferPresentationTimeUs;
            long y = o.bufferPresentationTimeUs;
            return (x < y) ? -1 : ((x == y) ? 0 : 1);
        }
    }

    /*@Override
    public void onMetadata(Metadata metadata) {
        for(int i = 0; i < metadata.length(); i++) {
            Metadata.Entry e = metadata.get(i);
            Log.e(TAG, "Metadata Entry : " + e.toString());
        }
    }*/

    private static class TrackStats {
        long originalBytesTranscoded;
        long transcodedBytesWritten;

        public void addTranscodedBytesWritten(long transcodedBytesWritten) {
            this.transcodedBytesWritten += transcodedBytesWritten;
        }

        public void addOriginalBytesTranscoded(long originalBytesTranscoded) {
            this.originalBytesTranscoded += originalBytesTranscoded;
        }

        public long getOriginalBytesTranscoded() {
            return originalBytesTranscoded;
        }

        public double getCompression() {
            return transcodedBytesWritten / originalBytesTranscoded;
        }
    }
}