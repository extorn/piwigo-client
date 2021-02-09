package delit.piwigoclient.business.video.capture;

import android.content.Context;
import android.os.Build;
import android.os.Handler;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import com.google.android.exoplayer2.DefaultRenderersFactory;
import com.google.android.exoplayer2.Renderer;
import com.google.android.exoplayer2.audio.AudioProcessor;
import com.google.android.exoplayer2.audio.AudioRendererEventListener;
import com.google.android.exoplayer2.drm.DrmSessionManager;
import com.google.android.exoplayer2.drm.FrameworkMediaCrypto;
import com.google.android.exoplayer2.mediacodec.MediaCodecSelector;
import com.google.android.exoplayer2.metadata.MetadataOutput;
import com.google.android.exoplayer2.text.TextOutput;
import com.google.android.exoplayer2.video.VideoRendererEventListener;

import java.util.ArrayList;

@RequiresApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
public class FrameCaptureRenderersFactory extends DefaultRenderersFactory {

    private final FrameHandler frameHandler;

    public FrameCaptureRenderersFactory(Context context, FrameHandler frameHandler) {
        super(context);
        this.frameHandler = frameHandler;
    }

    @Override
    public Renderer[] createRenderers(Handler eventHandler, VideoRendererEventListener videoRendererEventListener, AudioRendererEventListener audioRendererEventListener, TextOutput textRendererOutput, MetadataOutput metadataRendererOutput, @Nullable DrmSessionManager<FrameworkMediaCrypto> drmSessionManager) {
        return super.createRenderers(eventHandler, videoRendererEventListener, audioRendererEventListener, textRendererOutput, metadataRendererOutput, drmSessionManager);
    }


    @Override
    protected void buildVideoRenderers(Context context, @Nullable DrmSessionManager<FrameworkMediaCrypto> drmSessionManager, long allowedVideoJoiningTimeMs, Handler eventHandler, VideoRendererEventListener eventListener, int extensionRendererMode, final ArrayList<Renderer> out) {
        out.add(new VideoFrameCaptureRenderer(
                context,
                MediaCodecSelector.DEFAULT,
                allowedVideoJoiningTimeMs,
                drmSessionManager,
                false,
                eventHandler,
                eventListener,
                MAX_DROPPED_VIDEO_FRAME_COUNT_TO_NOTIFY, frameHandler));
    }

    @Override
    protected void buildAudioRenderers(Context context, @Nullable DrmSessionManager<FrameworkMediaCrypto> drmSessionManager, AudioProcessor[] audioProcessors, Handler eventHandler, AudioRendererEventListener eventListener, int extensionRendererMode, ArrayList<Renderer> out) {
        // needed to empty the buffered data extracted from the source else the extractor will block as the buffers fill.
        out.add(new AudioTrackDumpingRenderer(context,
                MediaCodecSelector.DEFAULT,
                drmSessionManager,
                false,
                eventHandler,
                eventListener));
    }
}