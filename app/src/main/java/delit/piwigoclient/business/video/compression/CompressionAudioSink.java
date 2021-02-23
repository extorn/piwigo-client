package delit.piwigoclient.business.video.compression;

import android.media.MediaCodec;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.os.Build;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import com.google.android.exoplayer2.PlaybackParameters;
import com.google.android.exoplayer2.audio.AudioAttributes;
import com.google.android.exoplayer2.audio.AudioSink;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import delit.libs.core.util.Logging;

import static android.media.MediaCodec.BUFFER_FLAG_END_OF_STREAM;

@RequiresApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
public class CompressionAudioSink implements AudioSink {
    private static final long ENCODER_CODEC_INPUT_TIMEOUT_IN_MS = 10000;
    private static final long ENCODER_CODEC_OUTPUT_TIMEOUT_IN_MS = 200;
    public static final String SUCCESS_ERROR_CODE = "1234567890";
    private final ExoPlayerCompression.AudioCompressionParameters compressionSettings;

    private static final boolean VERBOSE_LOGGING = false;
    private static final String TAG = "MyAudioSink";
    private final MediaMuxerControl mediaMuxerControl;
    private MediaCodec.BufferInfo audioBufferInfo = new MediaCodec.BufferInfo(); // shared copy for efficiency.
    private MediaFormat currentOutputMediaFormat;
    private boolean allDataProcessed;
    private boolean transcoding;
    private MediaCodec encoder;
    private Set<Long> encodingFrames = new LinkedHashSet<>();
    private int steppedWithoutActionCount;
    private boolean initialised;
    private boolean handledEndOfStream;
    private LinkedHashMap<Long,Integer> originalAudioDataSampleTimeSizeMap = new LinkedHashMap<>();
    private PlaybackParameters playbackParameters;

    public CompressionAudioSink(MediaMuxerControl mediaMuxerControl) {
        this(mediaMuxerControl, null);
    }

    public CompressionAudioSink(MediaMuxerControl mediaMuxerControl, ExoPlayerCompression.AudioCompressionParameters compressionSettings) {

        this.mediaMuxerControl = mediaMuxerControl;
        this.compressionSettings = compressionSettings;
    }

    @Override
    public void setListener(Listener listener) {
        //Do nothing.
    }

    @Override
    public boolean isEncodingSupported(int encoding) {
        return true; // we'll handle it all here (passthough is fine too - we'll decide later if we want to use that)
    }

    @Override
    public long getCurrentPositionUs(boolean sourceEnded) {
        if(mediaMuxerControl.isAudioConfigured()) {
            return mediaMuxerControl.getLastWrittenDataTimeUs();
        }
        return AudioSink.CURRENT_POSITION_NOT_SET;
    }

    @Override
    public void configure(int inputEncoding, int inputChannelCount, int inputSampleRate, int specifiedBufferSize, @Nullable int[] outputChannels, int trimStartFrames, int trimEndFrames) throws ConfigurationException {
        //this is called in onOutputFormatChanged (can set this directly so it correctly matches the decoder output there)
    }

    public void configure(MediaFormat decoderOutputFormat, MediaFormat outputFormat) {
        // If we're transcoding then the output format changed flag is called with that new format.
        currentOutputMediaFormat = outputFormat;
        transcoding = compressionSettings.isTranscodeDesired();
        if(!transcoding) {
            initialiseMediaMuxerAudio(outputFormat);
        }
    }

    private void initialiseMediaMuxerAudio(MediaFormat outputFormat) {
        mediaMuxerControl.addAudioTrack(outputFormat);
        mediaMuxerControl.markAudioConfigured();
        mediaMuxerControl.startMediaMuxer();
    }

    @Override
    public void play() {
        // get ready to handle data
    }

    @Override
    public void handleDiscontinuity() {
        // do nothing. Only called if allowed to skip.
    }

    @Override
    public boolean handleBuffer(ByteBuffer buffer, long presentationTimeUs) throws InitializationException, WriteException {
        initialised = true;
        try {
            mediaMuxerControl.markDataRead(true);
            if(transcoding) {
                    configureEncoderIfNeeded();
                processAnyEncoderOutput();
            }
            boolean bufferWritten = onDecoderOutputReceived(buffer, presentationTimeUs);
            if(bufferWritten) {
                steppedWithoutActionCount = 0;
            } else {
                checkForCompressorStuck();
            }
            return bufferWritten;
        } catch (IOException e) {
            throw new RuntimeException("Error while handling output buffer", e);
        }
    }

    private void checkForCompressorStuck() {
        if (steppedWithoutActionCount > 50 && !mediaMuxerControl.isAudioConfigured() || steppedWithoutActionCount > 300) {
            boolean lastRendererReadDatasource = mediaMuxerControl.getAndResetIsSourceDataRead();
            if (!lastRendererReadDatasource) {
                // Compression has crashed. Why?!

                throw new RuntimeException(new Exception("Compression got stuck for some reason - stopping"));
            }
        }
    }

    private void configureEncoderIfNeeded() {
        if (encoder == null) {
            try {
                initialiseOutputEncoder(currentOutputMediaFormat);
            } catch (IOException e) {
                throw new RuntimeException("Unable to initialise output encoder", e);
            }
            if (VERBOSE_LOGGING) {
                Log.d(TAG, "encoder not available to process data");
            }
        }
    }

    private boolean onDecoderOutputReceived(ByteBuffer buffer, long presentationTimeUs) throws IOException {
        if (mediaMuxerControl.isAudioConfigured() && !mediaMuxerControl.isMediaMuxerStarted()) {
            // still trying to configure other tracks so lets not fill the buffers!
            return false;
        }
        if (VERBOSE_LOGGING) {
            Log.d(TAG, String.format("Checking audio buffer at positionUs %1$d", presentationTimeUs));
        }

        // send the decoded data somewhere
        if (transcoding) {
            if (encoder == null) {
                throw new IllegalStateException("encoder not configured");
            }
            return processDecodedDataBuffer(buffer, presentationTimeUs, 0);
        } else {
            // this is not a stream being transcoded. Write it straight to the mediamuxer
            if (VERBOSE_LOGGING) {
                Log.d(TAG, String.format("writing raw sample audio data : [%1$d]", audioBufferInfo.presentationTimeUs));
            }
            writeDataToMediaMuxer(buffer, 0, presentationTimeUs);
        }
        return true;
    }


    private boolean processDecodedDataBuffer(ByteBuffer buffer, long bufferPresentationTimeUs, int flags) {
        if (VERBOSE_LOGGING) {
            Log.d(TAG, String.format("Encoding data at positionUs %1$d", bufferPresentationTimeUs));
        }
        int bytesWritten = 0;
        do {
            int inputBufferId = encoder.dequeueInputBuffer(ENCODER_CODEC_INPUT_TIMEOUT_IN_MS);
            if (bytesWritten == 0 && inputBufferId < 0) {
                if (VERBOSE_LOGGING) {
                    Log.e(TAG, "Encoder InputBuffId : " + inputBufferId);
                    Log.e(TAG, "BufferBytes Need to wait for space in encoder : " + buffer.remaining());
                }
                return false;
            }

            ByteBuffer encoderInputBuffer;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                encoderInputBuffer = encoder.getInputBuffer(inputBufferId);
            } else {
                encoderInputBuffer = encoder.getInputBuffers()[inputBufferId];
            }

            if (encoderInputBuffer != null) {
                encoderInputBuffer.clear();
                if (encoderInputBuffer.capacity() < buffer.remaining()) {
                    // the encoder buffer is smaller, we need to copy the raw data in chunks.
                    ByteBuffer tmp = buffer.duplicate();
                    tmp.limit(encoderInputBuffer.capacity());
                    buffer.position(tmp.limit());
                    bytesWritten = tmp.remaining();
                    encoderInputBuffer.put(tmp);
                } else {
                    bytesWritten = buffer.remaining();
                    encoderInputBuffer.put(buffer);
                }
                encoder.queueInputBuffer(inputBufferId, 0, bytesWritten, bufferPresentationTimeUs, flags);
            }
        }while (buffer.remaining() > 0);
        encodingFrames.add(bufferPresentationTimeUs);
        return true;
    }

    @Override
    public void playToEndOfStream() throws WriteException {
        if(isEnded()) {
            if(mediaMuxerControl.isFinished()) {
                if(VERBOSE_LOGGING) {
                    Log.d(TAG, "Renderer is still running after ending - terminating by throwing exception");
                    throw new RuntimeException(SUCCESS_ERROR_CODE);
                }
            }
            return;
        }
        // finish up (the input stream has ended).
        if(VERBOSE_LOGGING) {
            Log.d(TAG, "Notified of EOS in Source data");
        }
        if(transcoding) {
            processAnyEncoderOutput();
        }
        if (handledEndOfStream || !initialised) {
            steppedWithoutActionCount++;
            checkForCompressorStuck();
            return;
        }
        if(transcoding) {
            //Submit an EOS flag to the encoder. Presumably one isn't otherwise sent in handleBuffer.
            if(VERBOSE_LOGGING) {
                Log.d(TAG, "Submitted EOS to encoder");
            }
            handledEndOfStream = processDecodedDataBuffer(ByteBuffer.allocate(0), getCurrentPositionUs(true), BUFFER_FLAG_END_OF_STREAM);
        } else {
            notifyMediaMuxerOfEoS();
            handledEndOfStream = true;
        }

    }

    @Override
    public boolean isEnded() {
        return !initialised || allDataProcessed;
    }

    @Override
    public boolean hasPendingData() {
        return false; // have data ready to write.. (check the mediaMuxerControl)
    }

    @Override
    public PlaybackParameters setPlaybackParameters(PlaybackParameters playbackParameters) {
        this.playbackParameters = playbackParameters;
        return playbackParameters; //realAudioSink.setPlaybackParameters(playbackParameters); // ignore.
    }

    @Override
    public PlaybackParameters getPlaybackParameters() {
        return playbackParameters; //realAudioSink.getPlaybackParameters();
    }

    @Override
    public void setAudioAttributes(AudioAttributes audioAttributes) {
        // we won't bother
    }

    @Override
    public void setAudioSessionId(int audioSessionId) {
        // we won't bother
    }

    @Override
    public void enableTunnelingV21(int tunnelingAudioSessionId) {
        // we won't bother
    }

    @Override
    public void disableTunneling() {
        // we won't bother
    }

    @Override
    public void setVolume(float volume) {
        // we won't bother
    }

    @Override
    public void pause() {
        // we won't bother
    }

    @Override
    public void reset() {
        // Do nothing. Perhaps throw exception if we've already written data to the mediaMuxer
        if(initialised) {
            initialised = false;
            handledEndOfStream = false;
            audioBufferInfo = null;
            currentOutputMediaFormat = null;
            allDataProcessed = false;
            transcoding = false;
            if(encoder != null) {
                encoder.release();
                encoder = null;
            }
            encodingFrames = new HashSet<>();
            if(VERBOSE_LOGGING) {
                if(steppedWithoutActionCount > 0) {
                    Log.e(TAG, "Largest steppedWithoutActionCount : " + steppedWithoutActionCount);
                }
            }
            steppedWithoutActionCount = 0;
            originalAudioDataSampleTimeSizeMap.clear();
        }

    }

    private long getAudioDataOriginalBytesToTime(long bufferPresentationTimeUs) {
        long bytesOfAudioFromInputFileProcessed = 0;
        Iterator<Map.Entry<Long,Integer>> iterator = originalAudioDataSampleTimeSizeMap.entrySet().iterator();
        while(iterator.hasNext()) {
            Map.Entry<Long, Integer> entry = iterator.next();
            if(entry.getKey() > bufferPresentationTimeUs) {
                break;
            }
            bytesOfAudioFromInputFileProcessed += entry.getValue();
            iterator.remove();
        }
        return bytesOfAudioFromInputFileProcessed;
    }

    @Override
    public void release() {
        //clear all buffers and memory used.
    }

    private void initialiseOutputEncoder(MediaFormat outputMediaFormat) throws IOException {
        String outputMimeType = outputMediaFormat.getString(MediaFormat.KEY_MIME);
        android.media.MediaCodecInfo encoderCodecInfo = selectEncoderCodec(outputMimeType);
        if (encoderCodecInfo == null) {
            if (VERBOSE_LOGGING) {
                // Don't fail CTS if they don't have an AVC codec (not here, anyway).
                Log.e(TAG, "Unable to find an appropriate codec for " + outputMimeType);
            }
            throw new IOException("Unable to find appropriate codec for " + outputMimeType);
        }
        if (VERBOSE_LOGGING) {
            Log.d(TAG, "found codec: " + encoderCodecInfo.getName());
        }

        logEncoderCapabilities(encoderCodecInfo);


        try {
            encoder = MediaCodec.createByCodecName(encoderCodecInfo.getName());
            encoder.configure(outputMediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            encoder.start();

        } catch (IOException e) {
            throw new IOException("Unable to create, configure and start encoder for codec for " + outputMimeType, e);
        }
    }

    private void logEncoderCapabilities(android.media.MediaCodecInfo encoderCodecInfo) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {

            android.media.MediaCodecInfo.CodecCapabilities capabilities = encoderCodecInfo.getCapabilitiesForType(getOutputAudioFormatMime());
            android.media.MediaCodecInfo.CodecProfileLevel[] supportedProfileLevels = capabilities.profileLevels;

            StringBuilder sb = new StringBuilder("Supported AAC profile levels : ");
            Field[] fields = android.media.MediaCodecInfo.CodecProfileLevel.class.getDeclaredFields();
            int found = 0;
            for (Field f : fields) {
                if (f.getName().startsWith("AACObject")) {
                    for (android.media.MediaCodecInfo.CodecProfileLevel supportedProfileLevel : supportedProfileLevels) {
                        try {
                            if (supportedProfileLevel.profile == f.getInt(null)) {
                                sb.append(f.getName());
                                found++;
                                if (found < supportedProfileLevels.length) {
                                    sb.append(", ");
                                } else {
                                    break;
                                }
                            }
                        } catch (IllegalAccessException e) {
                            // ignore this as its for debug.
                        }
                    }
                }
                if (found == supportedProfileLevels.length) {
                    break;
                }
            }
            Log.d(TAG, sb.toString());


            android.media.MediaCodecInfo.EncoderCapabilities encoderCapabilities = capabilities.getEncoderCapabilities();
            boolean cbrOk = encoderCapabilities.isBitrateModeSupported(android.media.MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CBR);
            boolean cqOk = encoderCapabilities.isBitrateModeSupported(android.media.MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CQ);
            boolean vbrOk = encoderCapabilities.isBitrateModeSupported(android.media.MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_VBR);
            Log.d(TAG, "Supported bitrate modes : " + (cbrOk ? "cbr" : "") + (cqOk ? "cq" : "") + (vbrOk ? "vbr" : ""));
        }
    }

    /**
     * @return true if all data has been processed
     */
    private boolean processAnyEncoderOutput() {
        int outputBufIndex = 0;
        MediaCodec.BufferInfo outBuffInfo = new MediaCodec.BufferInfo();
        while (outputBufIndex != MediaCodec.INFO_TRY_AGAIN_LATER) {
            // read data from the encoder if available.
            outputBufIndex = encoder.dequeueOutputBuffer(outBuffInfo, ENCODER_CODEC_OUTPUT_TIMEOUT_IN_MS);
            // if then else on the buf Index
            if (isEncoderHasOutputReadyToRead(outputBufIndex)) {
                processEncoderOutputData(encoder, outBuffInfo, outputBufIndex);
            } if (isEncoderOutputFormatChanged(outputBufIndex)) { // -2
                processEncoderOutputFormatChanged(encoder);
            } else if (outputBufIndex == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) { // -3
                // we don't care - this is a deprecated signal.
                Logging.log(Log.ERROR, TAG, "Output buffers changed during encode!");
            } else if (outputBufIndex == MediaCodec.INFO_TRY_AGAIN_LATER) { //-1
                // No data to process at the moment. (Can any flags be present?). If not, do a continue; ?
            }

            // check the flags now.
            if (isEncoderBufferHasCodecConfigNotData(outBuffInfo)) {
                processEncoderCodecConfigData();    //TODO should the data be written to the media muxer if its codec config data?
            }

            if (isEncoderHasFinishedWritingAllData(outBuffInfo)) {
                processEncoderHasFinishedWritingAllData();
                return true;
            }
        }
        return false;
    }

    private void processEncoderHasFinishedWritingAllData() {
        releaseEncoder();
        notifyMediaMuxerOfEoS();
    }

    private void notifyMediaMuxerOfEoS() {
        if (VERBOSE_LOGGING) {
            Log.d(TAG, "Audio encoder: EOS - Stopping Audio Renderer");
        }
        mediaMuxerControl.audioRendererStopped();
        allDataProcessed = true;
        // The encoder is done with.
        if (VERBOSE_LOGGING) {
            if (encodingFrames.size() > 0) {
                Log.e(TAG, String.format("releasing encoding codec. It has %1$d frames still being encoded", encodingFrames.size()));
            } else {
                Log.d(TAG, "releasing encoding codec.");
            }
        }
    }

    private boolean isEncoderHasFinishedWritingAllData(MediaCodec.BufferInfo outBuffInfo) {
        return (outBuffInfo.flags & BUFFER_FLAG_END_OF_STREAM) == BUFFER_FLAG_END_OF_STREAM;
    }

    private void processEncoderCodecConfigData() {
        if(!mediaMuxerControl.isAudioConfigured()) {
            currentOutputMediaFormat = encoder.getOutputFormat();
            if (VERBOSE_LOGGING) {
                Log.d(TAG, "Output format changed to : " + currentOutputMediaFormat);
            }
            initialiseMediaMuxerAudio(currentOutputMediaFormat);
        }
    }

    private void processEncoderOutputFormatChanged(MediaCodec encoder) {
        currentOutputMediaFormat = encoder.getOutputFormat();
        if (VERBOSE_LOGGING) {
            Log.d(TAG, "Output format changed to : " + currentOutputMediaFormat);
        }
        initialiseMediaMuxerAudio(currentOutputMediaFormat);
    }

    private void processEncoderOutputData(MediaCodec encoder, MediaCodec.BufferInfo outBuffInfo, int outputBufIndex) {
        ByteBuffer outputBuffer = getEncoderOutputBuffer(encoder, outputBufIndex);
        outputBuffer.position(outBuffInfo.offset);
        outputBuffer.limit(outBuffInfo.offset + outBuffInfo.size);

        if ((outBuffInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0 || outBuffInfo.size == 0) {
            if (VERBOSE_LOGGING) {
                Log.d(TAG, String.format("writing transcoded sample audio data : [%1$d] - flags : %2$d", audioBufferInfo.presentationTimeUs, outBuffInfo.flags));
            }
            writeDataToMediaMuxer(outputBuffer, outBuffInfo.flags, outBuffInfo.presentationTimeUs);
        }
        encoder.releaseOutputBuffer(outputBufIndex, false);
    }

    private boolean isEncoderHasOutputReadyToRead(int outputBufIndex) {
        return outputBufIndex >= 0;
    }

    private boolean isEncoderOutputFormatChanged(int outputBufIndex) {
        return outputBufIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED;
    }

    private boolean isEncoderBufferHasCodecConfigNotData(MediaCodec.BufferInfo outBuffInfo) {
        return (outBuffInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0;
    }

    private ByteBuffer getEncoderOutputBuffer(MediaCodec encoder, int encoderOutputBufferId) {
        ByteBuffer encodedDataBuffer = null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            encodedDataBuffer = encoder.getOutputBuffer(encoderOutputBufferId);
        }
        if (encodedDataBuffer == null && encoderOutputBufferId >= 0) {
            ByteBuffer[] encoderOutputBuffers = encoder.getOutputBuffers();
            encodedDataBuffer = encoderOutputBuffers[encoderOutputBufferId];
            if (VERBOSE_LOGGING) {
                Log.d(TAG, "encoder output buffers changed");
            }
        }
        return encodedDataBuffer;
    }

    private void writeDataToMediaMuxer(ByteBuffer buffer, int bufferFlags, long bufferPresentationTimeUs) {
        Iterator<Long> iterator = encodingFrames.iterator();
        while(iterator.hasNext()) {
            if(iterator.next() <= bufferPresentationTimeUs) {
                iterator.remove();
            }
        }
        audioBufferInfo.flags = bufferFlags;
        audioBufferInfo.offset = 0;
        audioBufferInfo.size = buffer.remaining();
        audioBufferInfo.presentationTimeUs = bufferPresentationTimeUs;

        long origAudioBytes = getAudioDataOriginalBytesToTime(bufferPresentationTimeUs);
        mediaMuxerControl.writeSampleData(mediaMuxerControl.getAudioTrackId(), buffer, audioBufferInfo, origAudioBytes);
    }

    private void releaseEncoder() {
        if(!transcoding) {
            if (VERBOSE_LOGGING) {
                Log.d(TAG, "releaseTranscoder called, but not transcoding track!");
            }
            return;
        }
        if (VERBOSE_LOGGING) {
            Log.d(TAG, "stopping and releasing encoder");
        }
        if (encoder != null) {
            encoder.stop();
            encoder.release();
            encoder = null;
        }
    }

    private String getOutputAudioFormatMime() {
        String audioFormatOutputMime = "audio/mp4a-latm";
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            audioFormatOutputMime = MediaFormat.MIMETYPE_AUDIO_AAC;
        }
        return audioFormatOutputMime;
    }


    public void recordRawAudioDataRead(long timeUs, int byteCount) {
        originalAudioDataSampleTimeSizeMap.put(timeUs, byteCount);
    }

    /**
     * Method cloned verbartim from the Parent class
     * <p>
     * Returns the first codec capable of encoding the specified MIME type, or null if no
     * match was found.
     */
    private android.media.MediaCodecInfo selectEncoderCodec(String mimeType) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            return selectEncoderCodecV21(mimeType);
        } else {
            return selectEncoderCodecV20(mimeType);
        }
    }

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    private android.media.MediaCodecInfo selectEncoderCodecV21(String mimeType) {
        MediaCodecList codecList = new MediaCodecList(MediaCodecList.ALL_CODECS);
        for (android.media.MediaCodecInfo codecInfo : codecList.getCodecInfos()) {
            if (!codecInfo.isEncoder()) {
                continue;
            }
            String[] types = codecInfo.getSupportedTypes();
            for (String type : types) {
                if (type.equalsIgnoreCase(mimeType)) {
                    return codecInfo;
                }
            }
        }
        return null;
    }

    private android.media.MediaCodecInfo selectEncoderCodecV20(String mimeType) {
        int numCodecs = MediaCodecList.getCodecCount();
        for (int i = 0; i < numCodecs; i++) {
            android.media.MediaCodecInfo codecInfo = MediaCodecList.getCodecInfoAt(i);
            if (!codecInfo.isEncoder()) {
                continue;
            }
            String[] types = codecInfo.getSupportedTypes();
            for (String type : types) {
                if (type.equalsIgnoreCase(mimeType)) {
                    return codecInfo;
                }
            }
        }
        return null;
    }

}
