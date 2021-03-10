package delit.piwigoclient.piwigoApi.upload;

import android.content.Context;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Handler;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.documentfile.provider.DocumentFile;

import java.lang.ref.WeakReference;
import java.util.Objects;

import delit.libs.core.util.Logging;
import delit.libs.util.IOUtils;

public class BackgroundUploadInvokingUriContentObserver extends ContentObserver implements UriWatcher {

    private final Uri watchedUri;
    private final Context context;
    private EventProcessor eventProcessor;
    private static final String TAG = "BackgroundUploadInvokingUriContentObserver";

    /**
     * Creates a content observer.
     *
     * @param handler The handler to run {@link #onChange} on, or null if none.
     */
    public BackgroundUploadInvokingUriContentObserver(Handler handler, Context context, Uri watchedUri) {
        super(handler);
        this.context = context;
        this.watchedUri = watchedUri;
    }

    @Override
    public void onChange(boolean selfChange) {
        onChange(selfChange, null);
    }

    @Override
    public void onChange(boolean selfChange, Uri uri) {
        if(eventProcessor == null) {
            eventProcessor = new EventProcessor();
        }
        eventProcessor.execute(context, watchedUri, uri);
    }

    @Override
    public void startWatching() {
        try {
            context.getContentResolver().registerContentObserver(watchedUri, false, this);
        } catch(SecurityException e) {
            Logging.log(Log.ERROR, TAG, "Unable to watch uri : " + watchedUri);
            Logging.recordException(e);
        }
    }

    @Override
    public void stopWatching() {
        context.getContentResolver().unregisterContentObserver(this);
    }

    @Override
    public Uri getWatchedUri() {
        return watchedUri;
    }

    private class EventProcessor extends Thread {

        private WeakReference<Context> contextRef;
        private Uri eventSourceUri;
        private int lastEventId;
        private boolean running;

        @Override
        public void run() {
            boolean processed;
            do {
                processed = processEvent(watchedUri, eventSourceUri, lastEventId);
            } while(!processed); // do until processed.
            lastEventId = 0;
        }

        public void execute(Context context, Uri watchedUri, Uri eventSourceUri) {
            contextRef = new WeakReference<>(context);
            lastEventId++;
            if (lastEventId <= 0) {
                lastEventId = 1;
            }
            this.eventSourceUri = eventSourceUri;
            synchronized (this) {
                if (!running) {
                    running = true;
                    start();
                }
            }
        }

        private @NonNull
        Context getContext() {
            return Objects.requireNonNull(contextRef.get());
        }

        private boolean processEvent(Uri watchedUri, Uri eventSourceUri, int eventId) {
            DocumentFile file = IOUtils.getSingleDocFile(getContext(), eventSourceUri);
            if(file == null) {
                Logging.log(Log.ERROR, TAG, "Unable to retrieve DocumentFile for uri " + eventSourceUri);
                return false;
            }
            long len = file.length();
            long lastMod = file.lastModified();
            try {
                Thread.sleep(5000); // wait 5 seconds before double checking the file size etc (if its in use, it will have altered)
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            if (eventId == lastEventId && len == file.length() && lastMod == file.lastModified()) {
                BackgroundPiwigoUploadService.sendActionResume(context);
                return true;
            }
            return false;
        }
    }
}
