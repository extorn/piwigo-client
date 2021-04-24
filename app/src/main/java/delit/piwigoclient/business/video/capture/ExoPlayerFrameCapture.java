package delit.piwigoclient.business.video.capture;

import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.HandlerThread;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

import com.google.android.exoplayer2.DefaultLoadControl;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.LoadControl;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.PlaybackParameters;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.extractor.DefaultExtractorsFactory;
import com.google.android.exoplayer2.extractor.ExtractorsFactory;
import com.google.android.exoplayer2.source.ProgressiveMediaSource;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.trackselection.AdaptiveTrackSelection;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.trackselection.ExoTrackSelection;
import com.google.android.exoplayer2.trackselection.TrackSelectionArray;
import com.google.android.exoplayer2.trackselection.TrackSelector;
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Objects;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import delit.libs.core.util.Logging;
import delit.piwigoclient.BuildConfig;


@RequiresApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
public class ExoPlayerFrameCapture {

    private static final String TAG = "ExoPlayerFrameCapture";
    private final ArrayList<ExoPlayerFrameCaptureThread> activeCaptureThreads = new ArrayList<>(1);

    public ExoPlayerFrameCapture() {
    }

    public static boolean isSupported() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT;
    }

    /**
     * @param context
     * @param frameHandler
     * @param waitForLock
     * @return Thread invoked (this may be paused waiting for the lock to become available).
     */
    public Thread invokeFrameCapture(final Context context, FrameHandler frameHandler, boolean waitForLock) {
        SingletonExoPlayerFrameCaptureThread thread = new SingletonExoPlayerFrameCaptureThread(context, frameHandler, waitForLock);
        synchronized (activeCaptureThreads) {
            activeCaptureThreads.add(thread);
        }
        thread.start();
        return thread;
    }

    private class SingletonExoPlayerFrameCaptureThread extends ExoPlayerFrameCaptureThread {
        private Lock lock = new ReentrantLock();
        private boolean waitForLock;

        public SingletonExoPlayerFrameCaptureThread(Context context, FrameHandler frameHandler, boolean waitForLock) {
            super(context, frameHandler);
            this.waitForLock = waitForLock;
        }

        @Override
        public void run() {
            try {
                if(waitForLock) {
                    lock.lock();
                } else {
                    boolean lockAcquired = lock.tryLock(); // make this singleton
                    if(!lockAcquired) {
                        getFrameHandler().getListener().onCaptureError(getFrameHandler().getVideoFileUri(), new Exception("Lock not available"));
                        return;
                    }
                }
                super.run();
            } finally {
                synchronized (ExoPlayerFrameCapture.class) {
                    lock.unlock();
                }
            }
        }
    }

    public void cancel() {
        for (ExoPlayerFrameCaptureThread th : activeCaptureThreads) {
            th.cancel();
        }
        synchronized (activeCaptureThreads) {
            while (activeCaptureThreads.size() > 0) {
                try {
                    activeCaptureThreads.wait();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "Video Compressors have all exited");
        }
    }

    private static class PlayerMonitor implements Player.EventListener {
        private final FrameHandler frameHandler;
        private final Looper looper;
        private SimpleExoPlayer player;
        private Uri inputFile;
        private boolean exiting;

        public PlayerMonitor(SimpleExoPlayer player, FrameHandler frameHandler, Looper looper, Uri inputFile) {
            this.frameHandler = frameHandler;
            this.player = player;
            this.looper = looper;
            this.inputFile = inputFile;
            this.exiting = false;
        }

        @Override
        public void onPlayerError(ExoPlaybackException error) {
            frameHandler.getListener().onCaptureError(inputFile, error);
        }

        @Override
        public void onPlaybackStateChanged(int playbackState) {
            if (!exiting && (frameHandler.isCaptureComplete() || playbackState == Player.STATE_ENDED || playbackState == Player.STATE_IDLE)) {
                exiting = true;
                new Thread() {
                    @Override
                    public void run() {
                        try {
                            if (BuildConfig.DEBUG) {
                                Log.d(TAG, "stopping and releasing player");
                            }
                            player.stop(true);
                        } catch(Exception e) {
                            Logging.recordException(e);
                        }
                        player.release();
                        looper.quitSafely();

                    }
                }.start();
            }
        }

        @Override
        public void onTracksChanged(TrackGroupArray trackGroups, TrackSelectionArray trackSelections) {
        }
    }

    public interface FrameCaptureListener {

        void onCaptureStarted(Uri inputFile);

        void onCaptureComplete(Uri inputFile);

        void onCaptureError(Uri inputFile, Exception e);
    }

    private static class FrameCaptureListenerWrapper implements FrameCaptureListener {
        private final FrameCaptureListener wrapped;

        public FrameCaptureListenerWrapper(FrameCaptureListener wrapped) {
            this.wrapped = wrapped;
        }

        public FrameCaptureListener getWrapped() {
            return wrapped;
        }

        @Override
        public void onCaptureStarted(Uri inputFile) {
            wrapped.onCaptureStarted(inputFile);
        }

        @Override
        public void onCaptureError(Uri inputFile, Exception e) {
            wrapped.onCaptureError(inputFile, e);
        }

        @Override
        public void onCaptureComplete(Uri inputFile) {
            wrapped.onCaptureComplete(inputFile);
        }
    }

    private static class InternalFrameCaptureListener extends FrameCaptureListenerWrapper {
        private final Player player;

        public InternalFrameCaptureListener(Player player, FrameCaptureListener wrapped) {
            super(wrapped);
            this.player = player;
        }

        @Override
        public void onCaptureComplete(Uri inputFile) {
            super.onCaptureComplete(inputFile);
            stopPlayer();

        }

        private void stopPlayer() {
            if(player.getPlayWhenReady()) {
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "Stopping player");
                }
                player.setPlayWhenReady(false);
            }
        }

        @Override
        public void onCaptureError(Uri inputFile, Exception e) {
            super.onCaptureError(inputFile, e);
        }
    }

    private class ExoPlayerFrameCaptureThread extends HandlerThread {

        private final WeakReference<Context> contextRef;
        private SimpleExoPlayer player;
        private FrameHandler frameHandler;

        public FrameHandler getFrameHandler() {
            return frameHandler;
        }

        public ExoPlayerFrameCaptureThread(Context context, FrameHandler frameHandler) {
            super("ExoFrameCaptureThread");
            this.contextRef = new WeakReference<>(context);
            this.frameHandler = frameHandler;
        }

        private @NonNull Context getContext() {
            return Objects.requireNonNull(contextRef.get());
        }

        @Override
        protected void onLooperPrepared() {
            try {
                invokeFrameCapture();
                if(BuildConfig.DEBUG) {
                    Log.d(TAG, "frame capture invoked");
                }
            } catch (RuntimeException e) {
                Logging.recordException(e);
                frameHandler.getListener().onCaptureError(frameHandler.getVideoFileUri(), e);
            }
        }

        @Override
        public void run() {
            try {
                super.run();
                synchronized (activeCaptureThreads) {
                    activeCaptureThreads.remove(this);
                    activeCaptureThreads.notifyAll();
                }
            } catch(Exception e) {
                Logging.log(Log.ERROR, TAG, "Unexpected error in exo player frame capture listener thread. Cancelling frame capture.");
                Logging.recordException(e);
                cancel();
            }
        }

        private void invokeFrameCapture() {
            ExoTrackSelection.Factory videoTrackSelectionFactory = new AdaptiveTrackSelection.Factory();
            LoadControl loadControl = new DefaultLoadControl();
            TrackSelector trackSelector = new DefaultTrackSelector(getContext(), videoTrackSelectionFactory);

            Uri inputFile = frameHandler.getVideoFileUri();
            FrameCaptureRenderersFactory renderersFactory = new FrameCaptureRenderersFactory(getContext(), frameHandler);
            player = new SimpleExoPlayer.Builder(getContext(), renderersFactory).setTrackSelector(trackSelector).setLoadControl(loadControl).build();
            player.setThrowsWhenUsingWrongThread(false);
            // no errors will get caught by the listener until this point.
            frameHandler.setListener(new InternalFrameCaptureListener(player, frameHandler));

            PlayerMonitor playerMonitor = new PlayerMonitor(player, frameHandler, Looper.myLooper(), inputFile);
            player.addListener(playerMonitor); // watch for errors and report them
            ExtractorsFactory extractorsFactory = new DefaultExtractorsFactory();
            ProgressiveMediaSource.Factory factory = new ProgressiveMediaSource.Factory(new DefaultDataSourceFactory(getContext(), "PiwigoCompression"), extractorsFactory);
            frameHandler.getListener().onCaptureStarted(inputFile);
            ProgressiveMediaSource videoSource = factory.createMediaSource(new MediaItem.Builder().setUri(inputFile).build());
            player.setMediaSource(videoSource);
            player.prepare();
            PlaybackParameters playbackParams = new PlaybackParameters(1.0f);
            player.setPlaybackParameters(playbackParams);
            player.seekTo(Math.min(1000, player.getDuration()));
            player.setPlayWhenReady(true);
        }

        public void cancel() {
            player.stop(); // will cause the thread to shutdown safely once all messages have been processed.
        }
    }
}
