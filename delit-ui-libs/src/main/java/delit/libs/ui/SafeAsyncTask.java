package delit.libs.ui;

import android.content.Context;
import android.os.AsyncTask;
import android.util.Log;

import androidx.annotation.NonNull;

import java.lang.ref.WeakReference;
import java.util.Objects;

import delit.libs.core.util.Logging;

public abstract class SafeAsyncTask<Params, Progress, Result> extends AsyncTask<Params, Progress, Result> {

    private static final String TAG = "SafeAsyncTask";

    private WeakReference<Context> contextRef;

    public SafeAsyncTask<Params, Progress, Result> withContext(@NonNull Context context) {
        contextRef = new WeakReference<>(context);
        return this;
    }

    public Context getContext() {
        return Objects.requireNonNull(contextRef.get());
    }

    @SafeVarargs
    @Override
    protected final Result doInBackground(Params... params) {
        try {
            return doInBackgroundSafely(params);
        } catch(Exception e) {
            Logging.log(Log.ERROR, TAG, "Error in async task");
            Logging.recordException(e);
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
            throw e;
        }
    }



    @SafeVarargs
    @Override
    protected final void onProgressUpdate(Progress... progresses) {
        try {
            onProgressUpdateSafely(progresses);
        } catch(Exception e) {
            Logging.log(Log.ERROR, TAG, "Error in async task");
            Logging.recordException(e);
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
            throw e;
        }
    }



    @Override
    protected final void onCancelled() {
        try {
            onCancelledSafely();
        } catch(Exception e) {
            Logging.log(Log.ERROR, TAG, "Error in async task");
            Logging.recordException(e);
            throw e;
        }
    }


}
