package delit.libs.ui.util;

import android.content.Context;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;

import com.crashlytics.android.Crashlytics;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import delit.piwigoclient.BuildConfig;

public class MediaScanner implements MediaScannerConnection.MediaScannerConnectionClient {

    private static ThreadLocal<MediaScanner> mediaScannerInstance = new ThreadLocal<>();
    private final MediaScannerConnection connection;
    private Handler tasksHandler;
    private ArrayList<MediaScannerTask> tasks = new ArrayList<>(3);
    private HandlerThread tasksThread = new HandlerThread("MediaScanner") {
        @Override
        protected void onLooperPrepared() {
            super.onLooperPrepared();
            tasksHandler = new Handler(tasksThread.getLooper());
            synchronized (this) {
                notify();
            }
        }
    };
    private volatile boolean connected;

    private MediaScanner(Context context) {
        connection = new MediaScannerConnection(context, this);
        connection.connect();
        tasksThread.start();
    }

    public static MediaScanner instance(Context context) {
        if (mediaScannerInstance.get() == null) {
            mediaScannerInstance.set(new MediaScanner(context));
        }
        return mediaScannerInstance.get();
    }

    public void invokeScan(MediaScannerScanTask task) {
        task.setMediaScanner(this);
        tasks.add(task);
        tasksHandler.post(task);
    }

    @Override
    public void onMediaScannerConnected() {
        connected = true;
        synchronized (this) {
            notifyAll();
        }
    }

    private MediaScannerConnection getConnection() {
        return connection;
    }

    private boolean isConnected() {
        return connected;
    }

    @Override
    public void onScanCompleted(String path, Uri uri) {
        if (tasks.isEmpty()) {
            return;
        }
        MediaScannerScanTask task = ((MediaScannerScanTask) tasks.get(0));
        task.addResult(path, uri);
    }

    public void close() {
        tasks.clear();
        connected = false;
        connection.disconnect();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
            tasksThread.quitSafely();
        } else {
            tasksThread.quit();
        }
        mediaScannerInstance.remove();
    }

    private void removeTask(MediaScannerScanTask mediaScannerScanTask) {
        tasks.remove(mediaScannerScanTask);
    }

    private void connect() {
        connected = false;
        getConnection().connect();
    }

    private static abstract class MediaScannerTask implements Runnable {
        protected static final String TAG = "MediaScannerTask";
        protected MediaScanner mediaScanner;

        public void setMediaScanner(MediaScanner mediaScanner) {
            this.mediaScanner = mediaScanner;
        }
    }

    public static class MediaScannerImportTask extends MediaScannerScanTask {

        public MediaScannerImportTask(List<File> files) {
            super(files);
            setProcessResultsOnBackgroundThread(true);
        }

        public MediaScannerImportTask(File file) {
            this(Arrays.asList(file));
        }

        @Override
        public void onScanComplete(Map<File, Uri> batchResults, int firstResultIdx, int lastResultIdx, boolean jobFinished) {

        }
    }

    public static abstract class MediaScannerScanTask extends MediaScannerTask {
        private final List<File> files;
        private final int resultsBatchSize;
        private Map<File, Uri> results;
        private int resultsAwaited;
        private int firstResultIdx;
        private boolean processResultsOnBackgroundThread;

        public MediaScannerScanTask(List<File> files) {
            this(files, files.size());
        }

        public MediaScannerScanTask(List<File> files, int resultsBatchSize) {
            this.files = files;
            this.resultsBatchSize = resultsBatchSize;
            resultsAwaited = files.size();
        }

        public void setProcessResultsOnBackgroundThread(boolean processResultsOnBackgroundThread) {
            this.processResultsOnBackgroundThread = processResultsOnBackgroundThread;
        }

        private void waitForMediaScannerToStart() {
            long timeoutAt = System.currentTimeMillis() + 30000;
            while (!mediaScanner.isConnected() && System.currentTimeMillis() < timeoutAt) {
                synchronized (mediaScanner) {
                    try {
                        mediaScanner.wait();
                    } catch (InterruptedException e) {
                        // waiting for scanner to start
                    }
                }
            }
        }

        @Override
        public void run() {
            waitForMediaScannerToStart();
            results = new HashMap<>(resultsBatchSize);
            for (File f : files) {
                if (!f.isDirectory()) {
                    try {
                        mediaScanner.getConnection().scanFile(f.getAbsolutePath(), null);
                    } catch (IllegalStateException e) {
                        // connection has died
                        if (mediaScanner.isConnected()) {
                            Crashlytics.log(Log.ERROR, TAG, "Media scanner unexpectedly disconnected");
                            mediaScanner.connect();
                            waitForMediaScannerToStart();
                            mediaScanner.getConnection().scanFile(f.getAbsolutePath(), null);
                        } else {
                            // shutting down deliberately - just exit loop now.
                            return;
                        }
                    }

                } else {
                    resultsAwaited--;
                }
            }
            while (resultsAwaited > 0) {
                synchronized (this) {
                    try {
                        this.wait();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }

            if (BuildConfig.DEBUG) {
                Log.d(TAG, "Media scanner task ending");
            }
            mediaScanner.removeTask(this);
        }

        void addResult(String path, Uri uri) {
            resultsAwaited--;
            boolean taskFinished = resultsAwaited == 0;

            synchronized (results) {
                results.put(new File(path), uri);
                if (results.size() == resultsBatchSize || resultsAwaited == 0) {
                    final Map<File, Uri> batchResults = results;
                    final int nextResultIdx = firstResultIdx + batchResults.size();
                    if (processResultsOnBackgroundThread) {
                        onScanComplete(batchResults, firstResultIdx, nextResultIdx - 1, resultsAwaited == 0);
                    } else {
                        DisplayUtils.runOnUiThread(new BatchResultProcessor(firstResultIdx, nextResultIdx - 1, batchResults, taskFinished));
                    }
                    firstResultIdx = nextResultIdx;
                    results = new HashMap<>(resultsBatchSize);
                }
            }
            if (taskFinished) {
                synchronized (this) {
                    notifyAll();
                }
            }
        }

        public abstract void onScanComplete(Map<File, Uri> batchResults, int firstResultIdx, int lastResultIdx, boolean jobFinished);


        private class BatchResultProcessor implements Runnable {
            int firstResultIdx;
            int lastResultIdx;
            Map<File, Uri> batchResults;
            boolean finalBatch;

            public BatchResultProcessor(int firstResultIdx, int lastResultIdx, Map<File, Uri> batchResults, boolean finalBatch) {
                this.firstResultIdx = firstResultIdx;
                this.lastResultIdx = lastResultIdx;
                this.batchResults = batchResults;
                this.finalBatch = finalBatch;
            }

            @Override
            public void run() {
                onScanComplete(batchResults, firstResultIdx, lastResultIdx, finalBatch);
            }
        }
    }
}