package delit.piwigoclient.business.video.compression;

import android.content.Context;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMetadataRetriever;
import android.media.MediaMuxer;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import androidx.annotation.RequiresApi;
import androidx.core.content.MimeTypeFilter;
import androidx.documentfile.provider.DocumentFile;

import com.crashlytics.android.Crashlytics;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeSet;

import delit.libs.util.IOUtils;
import delit.libs.util.LegacyIOUtils;
import io.fabric.sdk.android.services.common.Crash;

@RequiresApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
public class MediaMuxerControl /*implements MetadataOutput*/ {
    private static final String TAG = "MediaMuxerControl";
    private static final boolean VERBOSE = false;
    private final Uri inputFile;
    private long inputBytes;
    private final ExoPlayerCompression.CompressionListener listener;
    private final Handler eventHandler;
    private Map<String, MediaFormat> trackFormats;
    private Map<String, TrackStats> trackStatistics;
    private MediaMuxer mediaMuxer;
    private boolean audioConfigured;
    private boolean videoConfigured;
    private boolean mediaMuxerStarted;
    private Uri outputFile;
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
    private boolean safeShutdownInProgress;
    private Context context;
    private ParcelFileDescriptor outputFileDescriptor;

    public MediaMuxerControl(Context context, Uri inputFile, Uri outputFile, ExoPlayerCompression.CompressionListener listener) throws IOException {
        this.context = context;
        this.inputFile = inputFile;
        this.outputFile = outputFile;
        extractLocationData(inputFile);
        extractInputVideoFormat(inputFile);
        DocumentFile docFile = DocumentFile.fromSingleUri(context, inputFile);
        inputBytes = docFile.length();
        //mediaMuxer = buildMediaMuxer(outputFile);
        trackFormats = new HashMap<>(2);
        trackStatistics = new HashMap<>(2);
        this.listener = listener;
        Looper looper = Looper.myLooper();
        if (looper == null) {
            throw new IllegalStateException("MediaMuxer control must run on a Handler thread (or one with a looper anyway!");
        }
        this.eventHandler = new Handler(looper);
    }

    private void extractInputVideoFormat(Uri inputFile) throws IOException {
        MediaExtractor mExtractor = new MediaExtractor();
        try {
            mExtractor.setDataSource(context, inputFile, null);
            int tracks = mExtractor.getTrackCount();
            for (int i = 0; i < tracks; i++) {
                inputVideoFormat = mExtractor.getTrackFormat(i);
                String mime = inputVideoFormat.getString(MediaFormat.KEY_MIME);
                if (MimeTypeFilter.matches(mime, "video/*")) {
                    break;
                }
            }
        } finally {
            mExtractor.release();
        }
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
            mediaMuxer = buildMediaMuxer();
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

    private void extractLocationData(Uri inputFile) {
        MediaMetadataRetriever metadataRetriever = new MediaMetadataRetriever();
        try {
            metadataRetriever.setDataSource(context, inputFile);
            String location = metadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_LOCATION);
            if (location != null) {
                String mode;
                switch (location.indexOf(".")) {
                    case 3:
                        //   Latitude and Longitude in Degrees:
                        //      ±DD.DDDD±DDD.DDDD/         (eg +12.345-098.765/)
                        mode = "degrees";
                        break;
                    case 5:
                        //   Latitude and Longitude in Degrees and Minutes:
                        //      ±DDMM.MMMM±DDDMM.MMMM/     (eg +1234.56-09854.321/)
                        mode = "degrees and minutes";
                        break;
                    case 7:
                        //   Latitude and Longitude in Degrees, Minutes and Seconds:
                        //      ±DDMMSS.SSSS±DDDMMSS.SSSS/ (eg +123456.7-0985432.1/)
                        mode = "degress minutes and seconds";
                        break;
                    default:
                        mode = "unidentified";
                }
                //            Altitude can be added optionally.
                //                    Latitude, Longitude (in Degrees) and Altitude:
                //      ±DD.DDDD±DDD.DDDD±AAA.AAA/         (eg +12.345-098.765+15.9/)
                //            Latitude, Longitude (in Degrees and Minutes) and Altitude:
                //      ±DDMM.MMMM±DDDMM.MMMM±AAA.AAA/     (eg +1234.56-09854.321+15.9/)
                //            Latitude, Longitude (in Degrees, Minutes and Seconds) and Altitude:
                //      ±DDMMSS.SSSS±DDDMMSS.SSSS±AAA.AAA/ (eg +123456.7-0985432.1+15.9/)

                int longStartsAt = Math.max(location.indexOf('-', 1), location.indexOf('+', 1));
                String latStr = location.substring(0, longStartsAt);

                int altitudeStartAt = Math.max(location.indexOf('-', longStartsAt + 1), location.indexOf('+', longStartsAt + 1));

                int longEndAt = location.length();
                if (location.lastIndexOf('/') == longEndAt - 1) {
                    longEndAt--;
                }
                if (altitudeStartAt > 0) {
                    longEndAt = altitudeStartAt;
                }

                String longStr = location.substring(longStartsAt, longEndAt);

                try {
                    latitude = Float.valueOf(latStr);
                    longitude = Float.valueOf(longStr);
                } catch (NumberFormatException e) {
                    Crashlytics.log(Log.ERROR, TAG, String.format("Error parsing lat long ISO String %1$s (%2$s), lat : %3$s, long : %4$s", location, mode, latStr, longStr));
                }
            }
        } finally {
            metadataRetriever.release();
        }
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

    public void markDataRead(boolean sourceDataProcessed) {
        this.sourceDataRead = sourceDataProcessed;
    }

    public boolean isSourceDataRead() {
        return sourceDataRead;
    }

    public boolean getAndResetIsSourceDataRead() {
        boolean val = sourceDataRead;
        sourceDataRead = false;
        return val;
    }

    public void writeSampleData(int outputTrackIndex, ByteBuffer encodedData, MediaCodec.BufferInfo info, Integer originalBytes) {

        TrackStats thisTrackStats = trackStatistics.get(getTrackName(outputTrackIndex));
        thisTrackStats.addOriginalBytesTranscoded(originalBytes == null ? 0 : originalBytes);

        int trackCount = trackFormats.size();

        if (!isMediaMuxerStarted()) {
            // if not started, we must be waiting for a track to be configured
            ByteBuffer data = IOUtils.deepCopyVisible(encodedData);
            queuedData.add(new Sample(outputTrackIndex, data, info));
            lastWrittenDataTimeUs = info.presentationTimeUs;
        } else {
            if (trackCount == 1) {
                // write the data now.
                writeSampleDataToMuxer(outputTrackIndex, encodedData, info);
                lastWrittenDataTimeUs = info.presentationTimeUs;
            } else {
                // we need to ensure correct muxing of tracks
                Sample newSample = new Sample(outputTrackIndex, encodedData, info);
                boolean newDataForSameTrackAsQueued = queuedData.isEmpty() || queuedData.first().outputTrackIndex == outputTrackIndex;
                // if no items in queue yet, or all data is same type, or this new item will be left on the queue after.
                if (newDataForSameTrackAsQueued || queuedData.last().compareTo(newSample) < 0) {
                    // clone data as item won't be consumed immediately
                    newSample.setData(IOUtils.deepCopyVisible(encodedData));
                }
                queuedData.add(newSample);

                if (!newDataForSameTrackAsQueued) {
                    while (queuedData.size() > 1) {
                        Sample firstItem = queuedData.pollFirst();
                        writeSampleDataToMuxer(firstItem.outputTrackIndex, firstItem.buffer, firstItem.getBufferInfo());
                    }
                    if (newSample.buffer == encodedData && queuedData.contains(newSample)) {
                        throw new RuntimeException("Error - input buffer will remain locked!");
                    }
                }
                lastWrittenDataTimeUs = queuedData.first().bufferPresentationTimeUs;
            }
        }
    }

    private void writeSampleDataToMuxer(int outputTrackIndex, ByteBuffer encodedData, MediaCodec.BufferInfo info) {
        TrackStats thisTrackStats = trackStatistics.get(getTrackName(outputTrackIndex));
        thisTrackStats.addTranscodedBytesWritten(encodedData.remaining());
//        lastWrittenDataTimeUs = info.presentationTimeUs;
        if (VERBOSE) {
            Log.d(TAG, "muxing data - " + getTrackName(outputTrackIndex) + " [" + info.presentationTimeUs + ']');
        }
        // if there's only one track, write all data to that track otherwise write to appropriate track.
        int mediaMuxerTrackIdx = trackFormats.size() > 1 ? outputTrackIndex : 0;
        mediaMuxer.writeSampleData(mediaMuxerTrackIdx, encodedData, info);
    }

    public void release() {

        if (mediaMuxerStarted) {
            flush();
        }

        if (VERBOSE) {
            Log.d(TAG, "Muxer : releasing old muxer");
        }
        if (this.mediaMuxer != null) {
            this.mediaMuxer.release();
        }
        try {
            if(outputFileDescriptor != null) {
                outputFileDescriptor.close();
            }
        } catch (IOException e) {
            Crashlytics.log(Log.ERROR, TAG, "Unable to close output file descriptor");
        }
        mediaMuxerStarted = false;
    }

    private void flush() {
        while (queuedData.size() > 0) {
            Sample firstItem = queuedData.pollFirst();
            writeSampleDataToMuxer(firstItem.outputTrackIndex, firstItem.buffer, firstItem.getBufferInfo());
        }
    }

    private String getTrackName(int outputTrackIndex) {
        try {
            return outputTrackIndex == getVideoTrackId() ? "video" : "audio";
        } catch (IllegalStateException e) {
            return "audio";
        }
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

        double value = BigDecimal.valueOf(bytesTranscoded).divide(BigDecimal.valueOf(inputBytes), new MathContext(2, RoundingMode.DOWN)).doubleValue();
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
            return 1;
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
            configureMediaMuxer(mediaMuxer);
            return mediaMuxer;
        } catch (IOException e) {
            throw new RuntimeException("Unable to recreate the media muxer", e);
        }

    }

    private void configureMediaMuxer(MediaMuxer muxer) {
        //TODO I've moved this from before the add Tracks. Does it solve the problem?!
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            if (Float.MAX_VALUE != latitude) {
                try {
                    muxer.setLocation(latitude, longitude);
                } catch (IllegalArgumentException e) {
                    Crashlytics.log(Log.ERROR, TAG, "Location data out of range - cannot copy to transcoded file");
                    Crashlytics.logException(e);
                }
            }
        }
        if (rotationDegrees > 0) {
            muxer.setOrientationHint(rotationDegrees);
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
//        mediaMuxer = buildMediaMuxer();
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
//        mediaMuxer = buildMediaMuxer();
    }

    public boolean isConfigured() {
        return hasAudio == audioConfigured && hasVideo == videoConfigured;
    }

    private MediaMuxer buildMediaMuxer(Uri outputFile) throws IOException {
        release();
        MediaMuxer muxer;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            outputFileDescriptor = context.getContentResolver().openFileDescriptor(outputFile, "rwt");
            muxer = new MediaMuxer(outputFileDescriptor.getFileDescriptor(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
        } else {
            muxer = new MediaMuxer(LegacyIOUtils.getFile(outputFile).getPath(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
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
                if (!safeShutdownInProgress) {
                    onCompressionComplete();
                } else {
                    onCompressionError(new Exception("Compression terminated abnormally"));
                }
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
                if (!safeShutdownInProgress) {
                    onCompressionComplete();
                } else {
                    onCompressionError(new Exception("Compression terminated abnormally"));
                }
            }
        }
    }

    private void onCompressionError(final Exception e) {
        eventHandler.post(new Runnable() {
            @Override
            public void run() {
                listener.onCompressionError(e);
            }
        });
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

    public void safeShutdown() {
        synchronized (this) {
            if (!safeShutdownInProgress) {
                if (isHasAudio() || isHasVideo()) {
                    eventHandler.postDelayed(new SafeShutdownAction(), 5000);
                }
            }
        }
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

            int comparison = compareToBufferPresentationTime(o);
            if (comparison == 0) {
                int x = outputTrackIndex;
                int y = o.outputTrackIndex;
                comparison = (x < y) ? -1 : ((x == y) ? 0 : 1);
            }
            return comparison;
        }

        public int compareToBufferPresentationTime(Sample o) {

            long x = bufferPresentationTimeUs;
            long y = o.bufferPresentationTimeUs;
            return (x < y) ? -1 : ((x == y) ? 0 : 1);
        }

        public void setData(ByteBuffer data) {
            this.buffer = data;
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

        public void addTranscodedBytesWritten(int transcodedBytesWritten) {
            this.transcodedBytesWritten += transcodedBytesWritten;
        }

        public void addOriginalBytesTranscoded(int originalBytesTranscoded) {
            this.originalBytesTranscoded += originalBytesTranscoded;
        }

        public long getOriginalBytesTranscoded() {
            return originalBytesTranscoded;
        }

        public double getCompression() {
            return transcodedBytesWritten / originalBytesTranscoded;
        }
    }

    private class SafeShutdownAction implements Runnable {
        @Override
        public void run() {
            if (isHasAudio() || isHasVideo()) {
                audioRendererStopped();
                videoRendererStopped();
            }
            safeShutdownInProgress = false;
        }
    }
}