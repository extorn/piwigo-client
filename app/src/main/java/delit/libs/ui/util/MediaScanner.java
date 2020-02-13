package delit.libs.ui.util;

import android.content.Context;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;
import android.util.Log;
import android.util.StringBuilderPrinter;

import com.crashlytics.android.Crashlytics;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import delit.piwigoclient.BuildConfig;

public class MediaScanner implements MediaScannerConnection.MediaScannerConnectionClient {

    private static final String TAG = "MediaScannerWrapper";
    private static ThreadLocal<MediaScanner> mediaScannerInstance = new ThreadLocal<>();
    private final MediaScannerConnection connection;
    private Handler tasksHandler;
    private Handler lookupsThreadHandler;
    private ArrayList<MediaScannerTask> tasks = new ArrayList<>(3);
    private HandlerThread lookupsThread = new HandlerThread("MediaScannerLookups") {
        @Override
        protected void onLooperPrepared() {
            super.onLooperPrepared();
            lookupsThreadHandler = new Handler(lookupsThread.getLooper());
            synchronized (this) {
                notify();
            }
        }
    };
    private HandlerThread tasksThread = new HandlerThread("MediaScannerScanTasks") {
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
        lookupsThread.start();
    }

    public static MediaScanner instance(Context context) {
        if (mediaScannerInstance.get() == null) {
            mediaScannerInstance.set(new MediaScanner(context));
        }
        return mediaScannerInstance.get();
    }

    public void invokeScan(MediaScannerScanTask task) {
        task.setMediaScanner(this);
        synchronized (tasks) {
            tasks.add(task);
        }
        tasksHandler.post(task);
        if (BuildConfig.DEBUG) {
            dumpStatus();
        }
    }

    private void dumpStatus() {

        StringBuilder sb = new StringBuilder("MediaScanner Status:\n");
        StringBuilderPrinter sbp = new StringBuilderPrinter(sb);
        sbp.println("Tasks Looper :\n");
        tasksHandler.getLooper().dump(sbp, TAG);
//        sb.append('\n');
//        sbp.println("MediaScanner Lookups Looper :\n");
        //lookupsThreadHandler.getLooper().dump(sbp, TAG);
        sb.append('\n');
        sb.append("Tasks : ");

        synchronized (tasks) {
            Iterator<MediaScannerTask> iter = tasks.iterator();
            while (iter.hasNext()) {
                MediaScannerScanTask task = (MediaScannerScanTask) iter.next();
                sb.append(task.getId());
                if (iter.hasNext()) {
                    sb.append(", ");
                }
            }
            Log.d(TAG, sb.toString());
        }
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
        synchronized (tasks) {
            if (tasks.isEmpty()) {
                return;
            }
            MediaScannerScanTask task = ((MediaScannerScanTask) tasks.get(0));
            task.addResult(path, uri);
        }
    }

    public void close() {
        synchronized (tasks) {
            tasks.clear();
        }
        connected = false;
        connection.disconnect();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
            tasksThread.quitSafely();
            lookupsThread.quitSafely();
        } else {
            tasksThread.quit();
            lookupsThread.quit();
        }
        mediaScannerInstance.remove();
    }

    private void removeTask(MediaScannerScanTask mediaScannerScanTask) {
        synchronized (tasks) {
            tasks.remove(mediaScannerScanTask);
        }
    }

    private void connect() {
        connected = false;
        getConnection().connect();
    }

    public void cancelActiveScan(String id) {
        synchronized (tasks) {
            Iterator<MediaScannerTask> iter = tasks.iterator();
            while (iter.hasNext()) {
                MediaScannerTask task = iter.next();
                if (task instanceof MediaScannerScanTask) {
                    MediaScannerScanTask scanTask = (MediaScannerScanTask) task;
                    if (id.equals(scanTask.getId())) {
                        if (scanTask.started) {
                            scanTask.cancelScan();
                        } else {
                            iter.remove();
                        }
                    }
                }

            }
        }
    }

    private static abstract class MediaScannerTask implements Runnable {
        protected static final String TAG = "MediaScannerTask";
        protected MediaScanner mediaScanner;

        public void setMediaScanner(MediaScanner mediaScanner) {
            this.mediaScanner = mediaScanner;
        }
    }

    public static class MediaScannerImportTask extends MediaScannerScanTask {

        public MediaScannerImportTask(String id, List<File> files) {
            super(id, files);
            setProcessResultsOnBackgroundThread(true);
        }

        public MediaScannerImportTask(String id, File file) {
            this(id, Arrays.asList(file));
        }

        @Override
        public void onScanComplete(Map<File, Uri> batchResults, int firstResultIdx, int lastResultIdx, boolean jobFinished) {

        }
    }

    public static abstract class MediaScannerScanTask extends MediaScannerTask {
        private String id;
        private final List<File> files;
        private final int resultsBatchSize;
        private Map<File, Uri> results;
        private int resultsAwaited;
        private int firstResultIdx;
        private boolean processResultsOnBackgroundThread;
        private boolean cancelScan;
        private int processingFileCount;
        private boolean started;
        private UUID taskUuid = UUID.randomUUID();


        /**
         * @param id    some unique id for the task so it can be identified if want to cancel later
         * @param files files to scan details of
         */
        public MediaScannerScanTask(String id, List<File> files) {
            this(id, files, files.size());
        }

        /**
         * @param id               some unique id for the task so it can be identified if want to cancel later
         * @param files            files to scan details of
         * @param resultsBatchSize max number of results to wait for before processing
         */
        public MediaScannerScanTask(String id, List<File> files, int resultsBatchSize) {
            this.id = id;
            this.files = new ArrayList<>(files);
            this.resultsBatchSize = resultsBatchSize;
            resultsAwaited = this.files.size();
        }

        /**
         * If not called, then the results will be processed on the main UI thread (most likely what is wanted)
         * @param processResultsOnBackgroundThread on UI thread if false else the running media scanner background thread
         */
        public void setProcessResultsOnBackgroundThread(boolean processResultsOnBackgroundThread) {
            this.processResultsOnBackgroundThread = processResultsOnBackgroundThread;
        }

        public void cancelScan() {
            if (started) {
                cancelScan = true;
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "task scheduled for cancellation : " + this.toString());
                }
                mediaScanner.lookupsThreadHandler.removeCallbacksAndMessages(taskUuid);
                synchronized (this) {
                    notifyAll();
                }
            }
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
            started = true;
            cancelScan = false;
            try {
                waitForMediaScannerToStart();
                results = new HashMap<>(resultsBatchSize);
                for (File f : files) {
                    if (!f.isDirectory()) {
                        processingFileCount++;
                        try {
//                            mediaScanner.getConnection().scanFile(f.getAbsolutePath(), null);
                            mediaScanner.lookupsThreadHandler.postAtTime(new LookupTask(f), taskUuid, SystemClock.uptimeMillis());
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

                    if (processingFileCount > resultsBatchSize * 2) {
                        if (pauseThread()) {
                            return;
                        }
                    }
                }

                while (resultsAwaited > 0) {
                    if (pauseThread()) {
                        return;
                    }
                }

            } finally {

                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "Media scanner task ending");
                }
                mediaScanner.removeTask(this);
                started = false;
            }
        }

        /**
         * @return true if should cancel job
         */
        private boolean pauseThread() {
            if (cancelScan) {
                return true; //already cancelled.
            }
            synchronized (this) {
                try {
                    this.wait();
                } catch (InterruptedException e) {
                    if (cancelScan) {
                        if (BuildConfig.DEBUG) {
                            Log.d(TAG, "cancelling scan for task " + this.toString());
                        }
                        return true;
                    }
                    e.printStackTrace();
                }
            }
            return false;
        }

        void addResult(String path, Uri uri) {
            if (cancelScan || !started) {
                // don't care about the result any more.
                return;
            }
            processingFileCount--;
            resultsAwaited--;
            boolean taskFinished = resultsAwaited == 0;

            synchronized (this) {
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
            }
            synchronized (this) {
                notifyAll();
            }
        }

        public String getId() {
            return id;
        }

        public abstract void onScanComplete(Map<File, Uri> batchResults, int firstResultIdx, int lastResultIdx, boolean jobFinished);

        private class LookupTask implements Runnable {

            private final String filePath;

            LookupTask(File f) {
                this.filePath = f.getAbsolutePath();
            }

            @Override
            public void run() {
                mediaScanner.getConnection().scanFile(filePath, null);
            }
        }


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