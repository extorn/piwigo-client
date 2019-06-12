package delit.piwigoclient.ui.common.util;

import android.content.Context;
import android.media.MediaScannerConnection;
import android.net.Uri;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

public class MediaScanner implements MediaScannerConnection.MediaScannerConnectionClient {

    private final MediaScannerConnection connection;
    private Map<File, Uri> scanResults = new HashMap<>();
    private boolean readyToScan;
    private int scansRunning;

    public MediaScanner(Context context) {
        connection = new MediaScannerConnection(context, this);
        connection.connect();
    }

    @Override
    public void onMediaScannerConnected() {
        readyToScan = true;
        synchronized (this) {
            notifyAll();
        }
    }

    public boolean isReadyToScan() {
        return readyToScan;
    }

    @Override
    public void onScanCompleted(String path, Uri uri) {
        synchronized (scanResults) {
            scanResults.put(new File(path), uri);
            scansRunning--;
            scanResults.notifyAll();
        }
        if (scansRunning == 0) {
            synchronized (this) {
                this.notifyAll();
            }
        }
    }

    public void scan(File f) {
        synchronized (scanResults) {
            scanResults.put(f, null);
            scansRunning++;
        }
        connection.scanFile(f.getAbsolutePath(), null);
    }

    public int getScansRunning() {
        return scansRunning;
    }

    public Uri getScanResults(File f) {
        Uri result;
        synchronized (scanResults) {
            if (!scanResults.containsKey(f)) {
                throw new IllegalStateException("Scan not scheduled for file " + f.getAbsolutePath());
            }
        }

        do {
            result = scanResults.get(f);
            if (result == null) {
                synchronized (scanResults) {
                    try {
                        scanResults.wait();
//                            Thread.sleep(100);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        } while (result == null);
        return result;
    }

    public void close() {
        readyToScan = false;
        connection.disconnect();
    }
}