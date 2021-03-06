package delit.libs.ui;

import android.content.Context;
import android.os.AsyncTask;
import android.util.Log;

import androidx.annotation.NonNull;

import java.lang.ref.WeakReference;
import java.util.Objects;
import java.util.concurrent.Executor;

import delit.libs.core.util.Logging;
import delit.libs.util.Utils;
import delit.libs.util.progress.ProgressListener;

public abstract class SafeAsyncTask<Params, Progress, Result> extends AsyncTask<Params, Progress, Result> {

    private static final String TAG = "SafeAsyncTask";

    private WeakReference<Context> contextRef;

    public SafeAsyncTask(){
    }


    public SafeAsyncTask<Params, Progress, Result> withContext(@NonNull Context context) {
        contextRef = new WeakReference<>(context);
        return this;
    }

    public Context getContext() {
        return Objects.requireNonNull(contextRef.get());
    }

    public SafeAsyncTask<Params, Progress, Result> executeOnExecutor(Executor executor) {
        super.executeOnExecutor(executor,(Params[])null);
        return this;
    }

    @SafeVarargs
    @Override
    protected final Result doInBackground(Params... params) {
        try {
            return doInBackgroundSafely(params);
        } catch(Exception e) {
            Logging.log(Log.ERROR, TAG, "Error in async task");
            Logging.recordException(e);
            Logging.waitForExceptionToBeSent();
            throw e;
        }
    }

    protected abstract Result doInBackgroundSafely(Params... params);
    protected void onPreExecuteSafely() {}
    protected void onPostExecuteSafely(Result result) {}
    protected void onProgressUpdateSafely(Progress[] progresses) {}

    protected void onCancelledSafely(Result result) {}
    protected void onCancelledSafely() {}

    @Override
    protected final void onPreExecute() {
        try {
            onPreExecuteSafely();
        } catch(Exception e) {
            Logging.log(Log.ERROR, TAG, "Error in async task");
            Logging.recordException(e);
            Logging.waitForExceptionToBeSent();
            throw e;
        }
    }



    @Override
    protected final void onPostExecute(Result result) {
        try {
            onPostExecuteSafely(result);
        } catch(Exception e) {
            Logging.log(Log.ERROR, TAG, "Error in async task");
            Logging.recordException(e);
            Logging.waitForExceptionToBeSent();
            throw e;
        }
    }



    @SafeVarargs
    @Override
    public final void onProgressUpdate(Progress... progresses) {
        try {
            onProgressUpdateSafely(progresses);
        } catch(Exception e) {
            Logging.log(Log.ERROR, TAG, "Error in async task");
            Logging.recordException(e);
            Logging.waitForExceptionToBeSent();
            throw e;
        }
    }



    @Override
    protected final void onCancelled(Result result) {
        try {
            onCancelledSafely(result);
        } catch(Exception e) {
            Logging.log(Log.ERROR, TAG, "Error in async task");
            Logging.recordException(e);
            Logging.waitForExceptionToBeSent();
            throw e;
        }
    }

    public boolean cancelSafely(boolean interrupt) {
        Logging.log(Log.DEBUG, TAG, "Cancelling Background task - " + Utils.getId(this));
        return cancel(interrupt);
    }


    @Override
    protected final void onCancelled() {
        try {
            onCancelledSafely();
        } catch(Exception e) {
            Logging.log(Log.ERROR, TAG, "Error in async task");
            Logging.recordException(e);
            Logging.waitForExceptionToBeSent();
            throw e;
        }
    }

    protected static class AsyncTaskProgressLink implements ProgressListener {

        private final ProgressUpdateCaller updateCaller;

        public interface ProgressUpdateCaller {
            void onProgress(int percent);
        }

        public AsyncTaskProgressLink(@NonNull ProgressUpdateCaller updateCaller) {
            this.updateCaller = updateCaller;
        }

        @Override
        public void onProgress(double percent, boolean forceNotification) {
            updateCaller.onProgress((int) Math.rint(percent * 100));
        }

        @Override
        public void onStarted() {
            onProgress(0, true);
        }

        @Override
        public void onComplete() {
            onProgress(1, true);
        }

        @Override
        public void setMinimumProgressToNotifyFor(double notifyOnProgress) {
            throw new UnsupportedOperationException(); // illogical
        }

        @Override
        public double getMinimumProgressToNotifyFor() {
            return 0.01;//1%
        }

        @Override
        public void onProgress(double percent) {
            onProgress(percent, false);
        }
    }
}
