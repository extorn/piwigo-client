package delit.piwigoclient.piwigoApi;

import android.content.Context;
import android.os.AsyncTask;
import android.util.Log;

import androidx.annotation.NonNull;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import delit.libs.core.util.Logging;
import delit.libs.http.cache.CachingAsyncHttpClient;
import delit.libs.ui.SafeAsyncTask;
import delit.libs.ui.util.ExecutorManager;
import delit.libs.util.CollectionUtils;
import delit.piwigoclient.BuildConfig;
import delit.piwigoclient.R;
import delit.piwigoclient.business.ConnectionPreferences;
import delit.piwigoclient.model.piwigo.PiwigoSessionDetails;
import delit.piwigoclient.piwigoApi.handlers.AbstractPiwigoDirectResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.AbstractPiwigoWsResponseHandler;

public class Worker extends SafeAsyncTask<Long, Integer, Boolean> {

    /**
     * An {@link Executor} that can be used to execute tasks in parallel.
     */
    private static final long MAX_TIMEOUT_MILLIS = 1000 * 60; // 1 minute - I can't think of a reason for a single call to exceed this time. Unless debugging!
    private static final ExecutorManager HTTP_THREAD_POOL_EXECUTOR;
    private static final ExecutorManager HTTP_LOGIN_THREAD_POOL_EXECUTOR;
    private static final int CPU_COUNT = Runtime.getRuntime().availableProcessors();
    private static final int CORE_POOL_SIZE = 6;
    private static final int MAXIMUM_POOL_SIZE = Math.max(24, CPU_COUNT * 4);
    private static final int KEEP_ALIVE_SECONDS = 60;
    private static final List<String> runningExecutorTasks = new ArrayList<>(MAXIMUM_POOL_SIZE);
    private static final List<String> queuedExecutorTasks = new ArrayList<>(MAXIMUM_POOL_SIZE);
    private static final String TAG = "WORKER";

    static {
        ExecutorManager manager = new ExecutorManager(CORE_POOL_SIZE, MAXIMUM_POOL_SIZE, KEEP_ALIVE_SECONDS * 1000, 1);
        manager.blockIfBusy(true);
        manager.setThreadFactory(new ExecutorManager.NamedThreadFactory("AsyncHttpTask"));
        manager.allowCoreThreadTimeOut(true);
        HTTP_THREAD_POOL_EXECUTOR = manager;

        ExecutorManager loginManager = new ExecutorManager(CORE_POOL_SIZE, MAXIMUM_POOL_SIZE, KEEP_ALIVE_SECONDS * 1000, 1);
        loginManager.blockIfBusy(true);
        loginManager.setThreadFactory(new ExecutorManager.NamedThreadFactory("AsyncHttpLoginTask"));
        loginManager.allowCoreThreadTimeOut(true);

        HTTP_LOGIN_THREAD_POOL_EXECUTOR = loginManager;
    }

    private final String DEFAULT_TAG = "PwgAccessSvcAsyncTask";
    private String tag = DEFAULT_TAG;


    private boolean rerunning = false;
    private final AbstractPiwigoDirectResponseHandler handler;
    private ConnectionPreferences.ProfilePreferences connectionPreferences;
    private boolean workerDone;
    private AsyncTask<Long, Integer, Boolean> task;

    public Worker(@NonNull AbstractPiwigoDirectResponseHandler handler, Context context) {
        this.handler = handler;
        withContext(context);
    }

    public void beforeCall() {
        workerDone = false;
    }

    //TODO why can't I use the WeakReference here (OwnedSafeAsyncTask)
    private AbstractPiwigoDirectResponseHandler getOwner() {
        return handler;
    }

    private void updatePoolSize(AbstractPiwigoDirectResponseHandler handler) {
        //Update the max pool size.
        try {
            CachingAsyncHttpClient client = handler.getHttpClientFactory().getAsyncHttpClient(handler.getConnectionPrefs(), getContext());
            if (client != null) {
                int newMaxPoolSize = client.getMaxConcurrentConnections();
                int newCorePoolSize = Math.min(newMaxPoolSize, Math.max(3, newMaxPoolSize / 2));
                HTTP_THREAD_POOL_EXECUTOR.setCorePoolSize(newCorePoolSize);
                HTTP_THREAD_POOL_EXECUTOR.setMaxPoolSize(newMaxPoolSize);
            }
        } catch (RuntimeException e) {
            Logging.recordException(e);
            handler.sendFailureMessage(-1, null, null, new IllegalStateException(getContext().getString(R.string.error_building_http_engine), e));
        }

    }

    /**
     * @param success null if the handler is still running.
     */
    public void afterCallInBackgroundThread(Boolean success) {
        recordExcutionFinished();
        if(success != null) {
            Logging.log(Log.DEBUG, tag, "Worker "+getTaskName()+" terminated, handler success : " + success);
        } else {
            Logging.log(Log.DEBUG, tag, "Worker "+getTaskName()+" terminated, handler still running");
        }
    }

    @Override
    public boolean cancelSafely(boolean interrupt) {
        handler.cancelCallAsap();
        task.cancel(interrupt);
        return super.cancelSafely(interrupt);
    }

    @Override
    protected final Boolean doInBackgroundSafely(Long... params) {
        try {
            if (params.length != 1) {
                throw new IllegalArgumentException("Exactly one parameter must be passed - the id for this call");
            }
            long messageId = params[0];
            boolean result = executeCall(messageId);
            Logging.log(Log.DEBUG, tag, "Worker executed code in background successfully: " + result);
            return result;
        } catch (RuntimeException e) {
            Logging.log(Log.ERROR, tag, "ASync worker background call crashed unexpectedly");
            Logging.recordException(e);
            return false;
        }
    }

    private ConnectionPreferences.ProfilePreferences getProfilePreferences() {
        return connectionPreferences != null ? connectionPreferences : ConnectionPreferences.getActiveProfile();
    }

    private boolean executeCall(long messageId) {

        recordExcutionStart();

//        Thread.currentThread().setName(handler.getClass().getSimpleName());

        if (BuildConfig.DEBUG) {
            Logging.log(Log.ERROR, tag, "Running worker for handler " + getOwner().getClass().getSimpleName() + " on thread " + Thread.currentThread().getName() + " (will be paused v soon)");
        }

        final AbstractPiwigoDirectResponseHandler handler = getOwner();

        try {
            this.tag = handler.getTag();

            ConnectionPreferences.ProfilePreferences profilePrefs = getProfilePreferences();

            handler.setMessageId(messageId);
            handler.setCallDetails(getContext(), profilePrefs, !handler.getUseSynchronousMode());

            beforeCall();
            updatePoolSize(handler);

            boolean haveValidSession = true;
            if (!handler.isPerformingLogin()) {
                synchronized (Worker.class) {
                    if (PiwigoSessionDetails.getInstance(profilePrefs) == null) {
                        Logging.log(Log.WARN, TAG, "No active session. Attempting login");
                        haveValidSession = handler.getNewLogin();
                    }
                }
            }

            if (haveValidSession) {
                handler.beforeCall();
                handler.runCall(rerunning);

                // this is the absolute timeout - in case something is seriously wrong.
                long start = System.currentTimeMillis();
                handler.waitUntilComplete(300000);
                if(BuildConfig.DEBUG) {
                    long callTookMillis = System.currentTimeMillis() - start;
                    String call;
                    if (handler instanceof AbstractPiwigoWsResponseHandler) {
                        call = ((AbstractPiwigoWsResponseHandler) handler).getPiwigoMethod();
                    } else {
                        URI uri = handler.getRequestURI();
                        if(uri != null) {
                            call = handler.getRequestURI().toASCIIString();
                        } else {
                            call = handler.getTag();
                        }
                    }
                    Logging.log(Log.ERROR, TAG, "HANDLER_TIME: %2$dms : calling [%3$d]%1$s", call, callTookMillis, handler.getMessageId());
                }
            } else {
                Logging.log(Log.WARN, TAG, "No active session after attempting login.");
                handler.sendFailureMessage(-1, null, null, new IllegalArgumentException(getContext().getString(R.string.error_unable_to_acquire_valid_session)));
            }

            afterCallInBackgroundThread(handler.isRunning() ? null : handler.isSuccess());

        } catch(RuntimeException e) {
            Logging.log(Log.ERROR,TAG, "Unexpected error in execute");
            Logging.recordException(e);
            Logging.waitForExceptionToBeSent();
        }

        return handler.isSuccess();
    }

    private void recordExcutionStart() {
        synchronized (queuedExecutorTasks) {
            synchronized (runningExecutorTasks) {
                queuedExecutorTasks.remove(getOwner().getTag());
                runningExecutorTasks.add(getOwner().getTag());
            }
        }
    }

    private void recordExcutionQueued() {
        synchronized (queuedExecutorTasks) {
            queuedExecutorTasks.add(getTaskName());
        }
    }

    private String getTaskName() {
        return getOwner().getTag();
    }

    private void recordExcutionFinished() {
        synchronized (runningExecutorTasks) {
            runningExecutorTasks.remove(getTaskName());
        }
        workerDone = true;
        synchronized (this) {
            notifyAll();
        }
    }

    @Override
    protected void onPostExecuteSafely(Boolean successful) {
        Logging.log(Log.DEBUG, tag, "Worker "+getTaskName()+" terminated: " + successful);
    }

    public long start(long messageId) {
        try {
            task = executeOnExecutor(getExecutorManager().getExecutorService(), messageId);
            recordExcutionQueued();
            //TODO collect a list of tasks and kill them all if the app exits.
            return messageId;
        } catch (RejectedExecutionException e) {
            StringBuilder sb = new StringBuilder();
            synchronized (queuedExecutorTasks) {
                synchronized (runningExecutorTasks) {
                    String runningTaskFreqMapStr = CollectionUtils.getFrequencyMapAsString(CollectionUtils.toFrequencyMap(runningExecutorTasks));
                    String queuedTaskFreqMapStr = CollectionUtils.getFrequencyMapAsString(CollectionUtils.toFrequencyMap(queuedExecutorTasks));
                    sb.append("Main Executor is Running Task: ").append(runningTaskFreqMapStr);
                    sb.append('\n');
                    sb.append("Main Executor has Queued Tasks: ").append(queuedTaskFreqMapStr);
                    sb.append('\n');
                    sb.append("This task was of type : ").append(getOwner().getTag());
                }
            }
            throw new RejectedExecutionException(sb.toString(), e);
        }
    }

    /**
     * Run synchronously in the same thread
     *
     * @param messageId
     * @return true if succeeded
     */
    public boolean run(long messageId) {
        return doInBackground(messageId);
    }


    /**
     * Run synchronously in a different thread
     *
     * @param messageId
     * @return true if succeeded
     */
    public boolean startAndWait(long messageId) {
        final AsyncTask<Long, Integer, Boolean> task = executeOnExecutor(getExecutorManager().getExecutorService(), messageId);
        //TODO collect a list of tasks and kill them all if the app exits.
        Boolean retVal = null;
        boolean timedOut = false;
        long workerStartedAt = System.currentTimeMillis();
        long timeoutAt = workerStartedAt + MAX_TIMEOUT_MILLIS;
        while (!task.isCancelled() && !workerDone && !timedOut) {

            try {
                if (BuildConfig.DEBUG) {
                    Log.e(tag, "Thread " + Thread.currentThread().getName() + " starting to wait for response from handler " + getOwner().getClass().getSimpleName());
                }
                if(retVal == null) {
                    long pauseMillis = 1000;
                    try {
                        long currentTime = System.currentTimeMillis();
                        retVal = task.get(pauseMillis, TimeUnit.MILLISECONDS); // allow it to loop around until timed out (rather than hang forever)
                        Logging.log(Log.DEBUG, TAG, "WORKER waited %2$dms for task %1$s to provide result", handler.getTag(), System.currentTimeMillis() - currentTime);
                    } catch (TimeoutException e) {
                        Logging.log(Log.DEBUG, TAG, "WORKER timed out after %2$dms waiting for task %1$s to provide result", handler.getTag(), pauseMillis);
                    }
                    if (retVal != null) {
                        timeoutAt = System.currentTimeMillis() + 1500;
                    }
                } else {
                    Logging.log(Log.DEBUG, TAG, "Waiting up to 100ms for task %1$s to finish after result received", handler.getTag());
                    synchronized (this) {
                        wait(100);
                    }
                }
                if(retVal != null) {
                    timedOut = System.currentTimeMillis() > timeoutAt;
                }
            } catch (InterruptedException e) {
                // ignore unless the worker is cancelled.
                if (BuildConfig.DEBUG) {
                    Log.e(tag, "Thread " + Thread.currentThread().getName() + " awakened from waiting for response from handler " + getOwner().getClass().getSimpleName());
                }
            } catch (ExecutionException e) {
                if (BuildConfig.DEBUG) {
                    Log.e(tag, "Thread " + Thread.currentThread().getName() + ": Error retrieving result from handler " + getOwner().getClass().getSimpleName(), e);
                }
                workerDone = true; // definitely stop waiting.
            }
        }
        if(BuildConfig.DEBUG) {
            long callTookMillis = System.currentTimeMillis() - workerStartedAt;
            String call;
            if (handler instanceof AbstractPiwigoWsResponseHandler) {
                call = ((AbstractPiwigoWsResponseHandler) handler).getPiwigoMethod();
            } else {
                call = handler.getRequestURI().toASCIIString();
            }
            Logging.log(Log.ERROR, TAG, "WORKER_TIME: %2$dms : calling [%3$d]%1$s", call, callTookMillis, handler.getMessageId());
        }
        if(timedOut) {
            if (BuildConfig.DEBUG) {
                Log.e(tag, "Thread " + Thread.currentThread().getName() + ": Task not correctly being updated as finished from handler " + getOwner().getClass().getSimpleName());
            }
            task.cancel(true);
        }
        return retVal == null ? false : retVal;
    }

    public ExecutorManager getExecutorManager() {
        return getOwner().isPerformingLogin() ? HTTP_LOGIN_THREAD_POOL_EXECUTOR : HTTP_THREAD_POOL_EXECUTOR;
    }

    public ConnectionPreferences.ProfilePreferences getConnectionPreferences() {
        return connectionPreferences;
    }

    public void setConnectionPreferences(ConnectionPreferences.ProfilePreferences connectionPreferences) {
        this.connectionPreferences = connectionPreferences;
    }

    public void setRerunning(boolean rerunning) {
        this.rerunning = rerunning;
    }
}