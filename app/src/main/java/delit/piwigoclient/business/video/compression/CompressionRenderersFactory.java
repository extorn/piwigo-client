package delit.piwigoclient.business.video.compression;

import android.content.Context;
import android.os.Build;
import android.os.Handler;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import com.google.android.exoplayer2.DefaultRenderersFactory;
import com.google.android.exoplayer2.Renderer;
import com.google.android.exoplayer2.audio.AudioCapabilities;
import com.google.android.exoplayer2.audio.AudioProcessor;
import com.google.android.exoplayer2.audio.AudioRendererEventListener;
import com.google.android.exoplayer2.audio.DefaultAudioSink;
import com.google.android.exoplayer2.drm.DrmSessionManager;
import com.google.android.exoplayer2.drm.FrameworkMediaCrypto;
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
    public Renderer[] createRenderers(Handler eventHandler, VideoRendererEventListener videoRendererEventListener, AudioRendererEventListener audioRendererEventListener, TextOutput textRendererOutput, MetadataOutput metadataRendererOutput, @Nullable DrmSessionManager<FrameworkMediaCrypto> drmSessionManager) {
        return super.createRenderers(eventHandler, videoRendererEventListener, audioRendererEventListener, textRendererOutput, metadataRendererOutput, drmSessionManager);
    }


    @Override
    protected void buildVideoRenderers(Context context, @Nullable DrmSessionManager<FrameworkMediaCrypto> drmSessionManager, long allowedVideoJoiningTimeMs, Handler eventHandler, VideoRendererEventListener eventListener, int extensionRendererMode, final ArrayList<Renderer> out) {
        if (compressionParameters.isAddVideoTrack()) {
            out.add(new VideoTrackMuxerCompressionRenderer(
                    context,
                    MediaCodecSelector.DEFAULT,
                    allowedVideoJoiningTimeMs,
                    drmSessionManager,
                    false,
                    eventHandler,
                    eventListener,
                    MAX_DROPPED_VIDEO_FRAME_COUNT_TO_NOTIFY, mediaMuxerControl, compressionParameters.getVideoCompressionParameters()));
        }
    }

    @Override
    protected void buildAudioRenderers(Context context, @Nullable DrmSessionManager<FrameworkMediaCrypto> drmSessionManager, AudioProcessor[] audioProcessors, Handler eventHandler, AudioRendererEventListener eventListener, int extensionRendererMode, ArrayList<Renderer> out) {
        if (compressionParameters.isAddAudioTrack()) {
            out.add(new AudioTrackMuxerCompressionRenderer(context,
                    MediaCodecSelector.DEFAULT,
                    drmSessionManager,
                    false,
                    eventHandler,
                    eventListener,
                    mediaMuxerControl,
                    new DefaultAudioSink(AudioCapabilities.getCapabilities(context), audioProcessors),
                    compressionParameters.getAudioCompressionParameters()));
        } else {
            // needed to empty the buffered data extracted from the source else the extractor will block as the buffers fill.
            out.add(new AudioTrackDumpingRenderer(context,
                    MediaCodecSelector.DEFAULT,
                    drmSessionManager,
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