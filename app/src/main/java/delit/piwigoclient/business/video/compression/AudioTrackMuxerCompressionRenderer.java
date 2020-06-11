package delit.piwigoclient.business.video.compression;

import android.content.Context;
import android.media.MediaCodec;
import android.media.MediaCrypto;
import android.media.MediaFormat;
import android.os.Build;
import android.os.Handler;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.Renderer;
import com.google.android.exoplayer2.audio.AudioRendererEventListener;
import com.google.android.exoplayer2.audio.AudioSink;
import com.google.android.exoplayer2.audio.MediaCodecAudioRenderer;
import com.google.android.exoplayer2.decoder.DecoderInputBuffer;
import com.google.android.exoplayer2.drm.DrmSessionManager;
import com.google.android.exoplayer2.drm.FrameworkMediaCrypto;
import com.google.android.exoplayer2.mediacodec.MediaCodecInfo;
import com.google.android.exoplayer2.mediacodec.MediaCodecSelector;
import com.google.android.exoplayer2.util.MediaClock;
import com.google.android.exoplayer2.util.Util;

/**
 * This doesn't seem to work (with the video compression, but does alone) - issue with the link to exoplayer I think... It's kind of there, but not completely. Very weird error.
 */
@RequiresApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
public class AudioTrackMuxerCompressionRenderer extends MediaCodecAudioRenderer {

    private final MediaMuxerControl mediaMuxerControl;
    private final ExoPlayerCompression.AudioCompressionParameters compressionSettings;
    private final CompressionAudioSink audioSink;
    private MediaFormat currentOverallOutputMediaFormat;


    public AudioTrackMuxerCompressionRenderer(Context context, MediaCodecSelector mediaCodecSelector, @Nullable DrmSessionManager<FrameworkMediaCrypto> drmSessionManager, boolean playClearSamplesWithoutKeys, @Nullable Handler eventHandler, @Nullable AudioRendererEventListener eventListener, MediaMuxerControl mediaMuxerControl, AudioSink audioSink, ExoPlayerCompression.AudioCompressionParameters compressionSettings) {
        super(context, mediaCodecSelector, drmSessionManager, playClearSamplesWithoutKeys, eventHandler, eventListener, audioSink);
        this.mediaMuxerControl = mediaMuxerControl;
        this.compressionSettings = compressionSettings;
        if(!(audioSink instanceof CompressionAudioSink)) {
            throw new IllegalArgumentException("Audio Sink must be instance of " + CompressionAudioSink.class.getName());
        }
        this.audioSink = (CompressionAudioSink) audioSink;
    }

    @Override
    protected void onQueueInputBuffer(DecoderInputBuffer buffer) {
        audioSink.recordRawAudioDataRead(buffer.timeUs, buffer.data.limit());
        super.onQueueInputBuffer(buffer);
    }

    @Override
    protected void onEnabled(boolean joining) throws ExoPlaybackException {
        try {
            mediaMuxerControl.setHasAudio();
            super.onEnabled(joining);
        } catch (ExoPlaybackException e) {
            throw e;
        } catch (Exception e) {
            throw ExoPlaybackException.createForRenderer(e, getIndex());
        }
    }

    @Override
    protected void configureCodec(MediaCodecInfo codecInfo, MediaCodec streamDecoderCodec, Format format, MediaCrypto crypto) {
        super.configureCodec(codecInfo, streamDecoderCodec, format, crypto);
        int codecMaxInputSize = getCodecMaxInputSize(codecInfo, format, getStreamFormats());
        MediaFormat currentDecodedMediaFormat = getMediaFormat(format, format.sampleMimeType, codecMaxInputSize);
        if(compressionSettings.isTranscodeDesired()) {
            MediaFormat outputMediaFormat = getOutputMediaFormat(currentDecodedMediaFormat);
            currentOverallOutputMediaFormat = outputMediaFormat;
        } else {
            currentOverallOutputMediaFormat = currentDecodedMediaFormat;
        }
    }

    @Override
    protected void onOutputFormatChanged(MediaCodec codec, MediaFormat decoderOutputFormat) throws ExoPlaybackException {
        audioSink.configure(decoderOutputFormat, currentOverallOutputMediaFormat);
        super.onOutputFormatChanged(codec, decoderOutputFormat);

    }

    @Override
    protected boolean allowPassthrough(String mimeType) {
        boolean passthrough = super.allowPassthrough(mimeType);
        passthrough = compressionSettings.getBitRate() == ExoPlayerCompression.AudioCompressionParameters.AUDIO_PASSTHROUGH_BITRATE || "audio/raw".equals(mimeType); // this means that the data is not run through a decoder by the super class.
        return passthrough;
    }

    @Override
    public MediaClock getMediaClock() {
        if (mediaMuxerControl.isHasVideo()) {
            return null; // don't act like a clock! It causes data to be skipped.
        } else {
            return super.getMediaClock();
        }
    }


    protected MediaFormat getOutputMediaFormat(MediaFormat inputMediaFormat) {

        int likelyInputBitrate = getLikelyBitrate(inputMediaFormat);

        if (compressionSettings.getBitRate() != ExoPlayerCompression.AudioCompressionParameters.AUDIO_PASSTHROUGH_BITRATE) {

            int audioChannelCount = inputMediaFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT);

            String outputFormatMimeType = getOutputAudioFormatMime();
            MediaFormat outputMediaFormat = MediaFormat.createAudioFormat(outputFormatMimeType, inputMediaFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE), audioChannelCount);

            int desiredBitrate = compressionSettings.getBitRate();
            if (outputFormatMimeType.equals(inputMediaFormat.getString(MediaFormat.KEY_MIME))) {
                desiredBitrate = Math.min(likelyInputBitrate, desiredBitrate);
            }

            outputMediaFormat.setInteger(MediaFormat.KEY_AAC_PROFILE, android.media.MediaCodecInfo.CodecProfileLevel.AACObjectLC);
            outputMediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, desiredBitrate);
//            outputMediaFormat.setInteger(MediaFormat.KEY_BITRATE_MODE, mode);
            if (inputMediaFormat.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
                outputMediaFormat.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, inputMediaFormat.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE));
            } else {
                outputMediaFormat.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 655360);
            }
            // Set codec configuration values.
            if (Util.SDK_INT >= 23) {
                outputMediaFormat.setInteger(MediaFormat.KEY_PRIORITY, 0 /* realtime priority */);
            }
            return outputMediaFormat;
        }

        // we're just passing the data through un-processed so the media format stays the same
        return inputMediaFormat;
    }

    private String getOutputAudioFormatMime() {
        String audioFormatOutputMime = "audio/mp4a-latm";
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            audioFormatOutputMime = MediaFormat.MIMETYPE_AUDIO_AAC;
        }
        return audioFormatOutputMime;
    }

    private int getLikelyBitrate(MediaFormat inputMediaFormat) {
        int maxInputBitrate = (inputMediaFormat.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE) * inputMediaFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE) * inputMediaFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT) * 8 / 1024);
        int[] stdBitrates = new int[]{32000, 64000, 96000, 128000, 196000};
        int selectedBitrate = 0;
        for (int i = stdBitrates.length - 1; i >= 0; i--) {
            if (stdBitrates[i] < maxInputBitrate) {
                return stdBitrates[i];
            }
        }
        return stdBitrates[0];
    }
}