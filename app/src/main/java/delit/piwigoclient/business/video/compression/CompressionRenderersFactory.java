package delit.piwigoclient.business.video.compression;

import android.content.Context;
import android.os.Build;
import android.os.Handler;

import androidx.annotation.RequiresApi;

import com.google.android.exoplayer2.DefaultRenderersFactory;
import com.google.android.exoplayer2.Renderer;
import com.google.android.exoplayer2.audio.AudioRendererEventListener;
import com.google.android.exoplayer2.audio.AudioSink;
import com.google.android.exoplayer2.mediacodec.MediaCodecSelector;
import com.google.android.exoplayer2.metadata.MetadataOutput;
import com.google.android.exoplayer2.text.TextOutput;
import com.google.android.exoplayer2.video.VideoRendererEventListener;

import java.util.ArrayList;

@RequiresApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
public class CompressionRenderersFactory extends DefaultRenderersFactory {

    private final MediaMuxerControl mediaMuxerControl;
    private final ExoPlayerCompression.CompressionParameters compressionParameters;

    public CompressionRenderersFactory(Context context, MediaMuxerControl mediaMuxerControl, ExoPlayerCompression.CompressionParameters compressionParameters) {
        super(context);
        this.mediaMuxerControl = mediaMuxerControl;
        this.compressionParameters = compressionParameters;
    }

    @Override
    public Renderer[] createRenderers(Handler eventHandler, VideoRendererEventListener videoRendererEventListener, AudioRendererEventListener audioRendererEventListener, TextOutput textRendererOutput, MetadataOutput metadataRendererOutput) {
        return super.createRenderers(eventHandler, videoRendererEventListener, audioRendererEventListener, textRendererOutput, metadataRendererOutput);
    }


    @Override
    protected void buildVideoRenderers(
            Context context,
            @ExtensionRendererMode int extensionRendererMode,
            MediaCodecSelector mediaCodecSelector,
            boolean enableDecoderFallback,
            Handler eventHandler,
            VideoRendererEventListener eventListener,
            long allowedVideoJoiningTimeMs,
            ArrayList<Renderer> out) {
        if (compressionParameters.isAddVideoTrack()) {
            out.add(new VideoTrackMuxerCompressionRenderer(
                    context,
                    MediaCodecSelector.DEFAULT,
                    allowedVideoJoiningTimeMs,
                    false,
                    eventHandler,
                    eventListener,
                    MAX_DROPPED_VIDEO_FRAME_COUNT_TO_NOTIFY, mediaMuxerControl, compressionParameters.getVideoCompressionParameters()));
        } else {
            out.add(new VideoTrackDumpingRenderer(context,
                    MediaCodecSelector.DEFAULT,
                    allowedVideoJoiningTimeMs,
                    false,
                    eventHandler,
                    eventListener,
                    MAX_DROPPED_VIDEO_FRAME_COUNT_TO_NOTIFY, mediaMuxerControl));
        }
    }

    @Override
    protected void buildAudioRenderers(
            Context context,
            @ExtensionRendererMode int extensionRendererMode,
            MediaCodecSelector mediaCodecSelector,
            boolean enableDecoderFallback,
            AudioSink audioSink,
            Handler eventHandler,
            AudioRendererEventListener eventListener,
            ArrayList<Renderer> out) {
        if (compressionParameters.isAddAudioTrack()) {

            out.add(new AudioTrackMuxerCompressionRenderer(context,
                    MediaCodecSelector.DEFAULT,
                    false,
                    eventHandler,
                    eventListener,
                    mediaMuxerControl,
                    new CompressionAudioSink(mediaMuxerControl, compressionParameters.getAudioCompressionParameters()),/*
                    new DefaultAudioSink(AudioCapabilities.getCapabilities(context), audioProcessors),*/
                    compressionParameters.getAudioCompressionParameters()));
        } else {
            // needed to empty the buffered data extracted from the source else the extractor will block as the buffers fill.
            out.add(new AudioTrackDumpingRenderer(context,
                    MediaCodecSelector.DEFAULT,
                    false,
                    eventHandler,
                    eventListener,
                    mediaMuxerControl));
        }
    }

//    @Override
//    protected void buildMetadataRenderers(Context context, MetadataOutput output, Looper outputLooper, int extensionRendererMode, ArrayList<Renderer> out) {
//        out.add(new MetadataRenderer(mediaMuxerControl, null));
//    }
}