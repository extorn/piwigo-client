package delit.piwigoclient.piwigoApi.upload.actors;

import android.net.Uri;

import delit.piwigoclient.piwigoApi.upload.UploadJob;

public abstract class FileUploadCancelMonitorThread extends Thread {
    private static final String TAG = "FileUploadCancelMoni";
    private final Uri uploadJobKey;
    private final UploadJob thisUploadJob;
    private boolean done;

    public FileUploadCancelMonitorThread(UploadJob thisUploadJob, Uri uploadJobKey) {
        this.thisUploadJob = thisUploadJob;
        this.uploadJobKey = uploadJobKey;
    }

    @Override
    public void run() {
        setName("FileUploadCancelWatch");
        do {
            try {
                synchronized (thisUploadJob) {
                    thisUploadJob.wait();
                }
            } catch (InterruptedException e) {
                if(!thisUploadJob.isFileUploadStillWanted(uploadJobKey)) {
                    done = true;
                    onFileUploadCancelled(uploadJobKey);
                }
            }
        } while(!done);
    }

    public abstract void onFileUploadCancelled(Uri uploadJobKey);


    public void markDone() {
        done = true;
        interrupt();
    }
}
