package delit.piwigoclient.piwigoApi.upload;

import android.content.Context;
import android.net.Uri;
import android.os.FileObserver;

import androidx.annotation.Nullable;

import java.io.File;

class BackgroundUploadInvokingFolderChangeObserver extends FileObserver implements UriWatcher {
    private final File watchedFile;
    private FileEventProcessor eventProcessor;
    private final Context context;

    BackgroundUploadInvokingFolderChangeObserver(Context context, File f) {
        super(f.getAbsolutePath(), FileObserver.CREATE ^ FileObserver.MOVED_TO);
        this.context = context;
        this.watchedFile = f;
    }

    @Override
    public void onEvent(int event, @Nullable String path) {
        switch(event) {
            case FileObserver.CLOSE_WRITE:
            case FileObserver.MOVED_TO:
            case FileObserver.CREATE:
            case FileObserver.MODIFY:
            case FileObserver.ATTRIB:
                if(eventProcessor == null) {
                    eventProcessor = new FileEventProcessor();
                }
                eventProcessor.execute(new File(watchedFile, path));
                break;
            default:
                // do nothing for other events.
        }
    }

    @Override
    public Uri getWatchedUri() {
        return null;
    }

    private class FileEventProcessor extends Thread {

        private File eventSourceFile;
        private int lastEventId;
        private boolean running;

        @Override
        public void run() {
            while(!processEvent(eventSourceFile, lastEventId)){} // do until processed.
            lastEventId = 0;
        }

        public void execute(File eventSourceFile) {
            lastEventId++;
            if(lastEventId <= 0) {
                lastEventId = 1;
            }
            this.eventSourceFile = eventSourceFile;
            synchronized (this) {
                if (!running) {
                    running = true;
                    start();
                }
            }
        }

        private boolean processEvent(File eventSourceFile, int eventId) {
            long len = eventSourceFile.length();
            long lastMod = eventSourceFile.lastModified();
            try {
                Thread.sleep(5000); // wait 5 seconds before double checking the file size etc (if its in use, it will have altered)
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            if(eventId == lastEventId && len == eventSourceFile.length() && lastMod == eventSourceFile.lastModified()) {
                BackgroundPiwigoUploadService.resumeUploadService(context);
                return true;
            }
            return false;
        }
    }
}
