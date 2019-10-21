package delit.piwigoclient.piwigoApi;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.preference.PreferenceManager;
import android.util.Log;

import androidx.annotation.NonNull;

import com.crashlytics.android.Crashlytics;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import delit.libs.util.CollectionUtils;
import delit.piwigoclient.BuildConfig;
import delit.piwigoclient.R;
import delit.piwigoclient.business.ConnectionPreferences;
import delit.piwigoclient.model.piwigo.PiwigoSessionDetails;
import delit.piwigoclient.piwigoApi.handlers.AbstractPiwigoDirectResponseHandler;
import delit.piwigoclient.piwigoApi.http.CachingAsyncHttpClient;

import static android.os.AsyncTask.Status.FINISHED;

public class Worker extends AsyncTask<Long, Integer, Boolean> {

    /**
     * An {@link Executor} that can be used to execute tasks in parallel.
     */
    private static final long MAX_TIMEOUT_MILLIS = 1000 * 60; // 1 minute - I can't think of a reason for a single call to exceed this time. Unless debugging!
    private static final ThreadPoolExecutor HTTP_THREAD_POOL_EXECUTOR;
    private static final ThreadPoolExecutor HTTP_LOGIN_THREAD_POOL_EXECUTOR;
    private static final int CPU_COUNT = Runtime.getRuntime().availableProcessors();
    private static final int CORE_POOL_SIZE = 6;
    private static final int MAXIMUM_POOL_SIZE = Math.max(6, CPU_COUNT * 2 + 1);
    private static final int KEEP_ALIVE_SECONDS = 60;
    private static final List<String> runningExecutorTasks = new ArrayList<>(MAXIMUM_POOL_SIZE);
    private static final List<String> queuedExecutorTasks = new ArrayList<>(MAXIMUM_POOL_SIZE);
    private static final BlockingQueue<Runnable> sPoolWorkQueue =
            new LinkedBlockingQueue<Runnable>(128) {
                @Override
                public void put(@NonNull Runnable o) throws InterruptedException {
                    Log.d("StandardQueue", "New Queue Size : " + size());
                    super.put(o);
                }
            };
    private static final BlockingQueue<Runnable> loginPoolWorkQueue =
            new LinkedBlockingQueue<Runnable>(20) {
                @Override
                public void put(@NonNull Runnable o) throws InterruptedException {
                    Log.d("LoginQueue", "New Queue Size : " + size());
                    super.put(o);
                }
            };

    private static final ThreadFactory sThreadFactory = new ThreadFactory() {
        private final AtomicInteger mCount = new AtomicInteger(1);

        public Thread newThread(@NonNull Runnable r) {
            return new Thread(r, "AsyncTask #" + mCount.getAndIncrement());
        }
    };
    private static final ThreadFactory loginThreadFactory = new ThreadFactory() {
        private final AtomicInteger mCount = new AtomicInteger(1);

        public Thread newThread(@NonNull Runnable r) {
            return new Thread(r, "AsyncLoginTask #" + mCount.getAndIncrement());
        }
    };

    static {
        ThreadPoolExecutor threadPoolExecutor = new ThreadPoolExecutor(
                CORE_POOL_SIZE, MAXIMUM_POOL_SIZE, KEEP_ALIVE_SECONDS, TimeUnit.SECONDS,
                sPoolWorkQueue, sThreadFactory);
        threadPoolExecutor.allowCoreThreadTimeOut(true);
        HTTP_THREAD_POOL_EXECUTOR = threadPoolExecutor;

        ThreadPoolExecutor loginThreadPoolExecutor = new ThreadPoolExecutor(
                CORE_POOL_SIZE, MAXIMUM_POOL_SIZE, KEEP_ALIVE_SECONDS, TimeUnit.SECONDS,
                loginPoolWorkQueue, loginThreadFactory);
        threadPoolExecutor.allowCoreThreadTimeOut(true);

        HTTP_LOGIN_THREAD_POOL_EXECUTOR = loginThreadPoolExecutor;
    }

    private final String DEFAULT_TAG = "PwgAccessSvcAsyncTask";
    private WeakReference<Context> context;
    private String tag = DEFAULT_TAG;


    private AbstractPiwigoDirectResponseHandler handler;
    private ConnectionPreferences.ProfilePreferences connectionPreferences;

    public Worker(@NonNull AbstractPiwigoDirectResponseHandler handler, Context context) {
        this.context = new WeakReference<>(context);
        this.handler = handler;
    }

    public Context getContext() {
        return context.get();
    }

    public void beforeCall() {
    }

    private void updatePoolSize(AbstractPiwigoDirectResponseHandler handler) {
        //Update the max pool size.
        try {
            CachingAsyncHttpClient client = handler.getHttpClientFactory().getAsyncHttpClient(handler.getConnectionPrefs(), context.get());
            if (client != null) {
//                int newMaxPoolSize = client.getMaxConcurrentConnections();
//                HTTP_THREAD_POOL_EXECUTOR.setCorePoolSize(Math.min(newMaxPoolSize, Math.max(3, newMaxPoolSize / 2)));
//                HTTP_THREAD_POOL_EXECUTOR.setMaximumPoolSize(newMaxPoolSize);
            }
        } catch (RuntimeException e) {
            Crashlytics.logException(e);
            handler.sendFailureMessage(-1, null, null, new IllegalStateException(getContext().getString(R.string.error_building_http_engine), e));
        }

    }

    public void afterCall(boolean success) {
        context = null;
    }

    @Override
    protected final Boolean doInBackground(Long... params) {
        boolean result;
        try {
            if (params.length != 1) {
                throw new IllegalArgumentException("Exactly one parameter must be passed - the id for this call");
            }
            long messageId = params[0];
            result = executeCall(messageId);
            Log.d(tag, "Worker returning : " + result);
            return result;
        } catch (RuntimeException e) {
            Crashlytics.logException(e);
            if (BuildConfig.DEBUG) {
                Log.e(tag, "ASync code crashed unexpectedly", e);
            }
            result = false;
            Log.d(tag, "Worker returning : " + result);
            return result;
        }
    }

    protected ConnectionPreferences.ProfilePreferences getProfilePreferences() {
        return connectionPreferences != null ? connectionPreferences : ConnectionPreferences.getActiveProfile();
    }

    protected boolean executeCall(long messageId) {

        recordExcutionStart();

//        Thread.currentThread().setName(handler.getClass().getSimpleName());

        Log.e(tag, "Running worker for handler " + handler.getClass().getSimpleName() + " on thread " + Thread.currentThread().getName() + " (will be paused v soon)");

        SharedPreferences prefs = null;
        if (context != null) {
            prefs = PreferenceManager.getDefaultSharedPreferences(context.get());
        }

        AbstractPiwigoDirectResponseHandler handler = getHandler(prefs);
        this.tag = handler.getTag();

        ConnectionPreferences.ProfilePreferences profilePrefs = getProfilePreferences();

        handler.setMessageId(messageId);
        handler.setCallDetails(context.get(), profilePrefs, true);

        beforeCall();
        updatePoolSize(handler);

        boolean haveValidSession = true;
        if (!handler.isPerformingLogin()) {
            synchronized (Worker.class) {
                if (PiwigoSessionDetails.getInstance(profilePrefs) == null) {
                    haveValidSession = handler.getNewLogin();
                }
            }
        }

        if (haveValidSession) {
            handler.beforeCall();
            handler.runCall(false);

            // this is the absolute timeout - in case something is seriously wrong.
            long callTimeoutAtTime = System.currentTimeMillis() + 300000;

            synchronized (handler) {
                boolean timedOut = false;
                while (handler.isRunning() && !isCancelled() && !timedOut) {
                    long waitForMillis = callTimeoutAtTime - System.currentTimeMillis();
                    if (waitForMillis > 0) {
                        try {
                            handler.wait(waitForMillis);
                        } catch (InterruptedException e) {
                            // Either this has been cancelled or the wait timed out or the handler completed okay and notified us
                            if (isCancelled()) {
                                if (BuildConfig.DEBUG) {
                                    Log.e(handler.getTag(), "Service call cancelled before handler " + handler.getClass().getSimpleName() + " could finish running");
                                }
                                handler.cancelCallAsap();
                            }
                        }
                    } else {
                        timedOut = true;
                    }
                }
            }
        } else {
            handler.sendFailureMessage(-1, null, null, new IllegalArgumentException(getContext().getString(R.string.error_unable_to_acquire_valid_session)));
        }

        afterCall(handler.isSuccess());


        return handler.isSuccess();
    }

    private void recordExcutionStart() {
        synchronized (queuedExecutorTasks) {
            synchronized (runningExecutorTasks) {
                queuedExecutorTasks.remove(handler.getTag());
                runningExecutorTasks.add(handler.getTag());
            }
        }
    }

    private void recordExcutionQueued() {
        synchronized (queuedExecutorTasks) {
            queuedExecutorTasks.add(getTaskName());
        }
    }

    protected String getTaskName() {
        return handler != null ? handler.getTag() : "Unknown";
    }

    private void recordExcutionFinished() {
        synchronized (runningExecutorTasks) {
            runningExecutorTasks.remove(handler.getTag());
        }
    }

    @Override
    protected void onPostExecute(Boolean aBoolean) {
        super.onPostExecute(aBoolean);
        recordExcutionFinished();
    }

    protected @NonNull AbstractPiwigoDirectResponseHandler getHandler(SharedPreferences prefs) {
        return handler;
    }

    public long start(long messageId) {
        try {
            AsyncTask<Long, Integer, Boolean> task = executeOnExecutor(getExecutor(), messageId);
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
                    sb.append("This task was of type : ").append(handler.getTag());
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
        AsyncTask<Long, Integer, Boolean> task = executeOnExecutor(getExecutor(), messageId);
        //TODO collect a list of tasks and kill them all if the app exits.
        Boolean retVal = null;
        boolean timedOut = false;
        long timeoutAt = System.currentTimeMillis() + MAX_TIMEOUT_MILLIS;
        while (!task.isCancelled() && !task.getStatus().equals(FINISHED) && !timedOut) {

            if(retVal != null) {
                timedOut = System.currentTimeMillis() > timeoutAt;
            }
            try {
                if (BuildConfig.DEBUG) {
                    Log.e(tag, "Thread " + Thread.currentThread().getName() + " starting to wait for response from handler " + handler.getClass().getSimpleName());
                }
                if(retVal == null) {
                    retVal = task.get();
                    if (retVal != null) {
                        timeoutAt = System.currentTimeMillis() + 1500;
                    }
                } else {
                    synchronized (task) {
                        task.wait(500);
                    }
                }
            } catch (InterruptedException e) {
                // ignore unless the worker is cancelled.
                if (BuildConfig.DEBUG) {
                    Log.e(tag, "Thread " + Thread.currentThread().getName() + " awakened from waiting for response from handler " + handler.getClass().getSimpleName());
                }
            } catch (ExecutionException e) {
                if (BuildConfig.DEBUG) {
                    Log.e(tag, "Thread " + Thread.currentThread().getName() + ": Error retrieving result from handler " + handler.getClass().getSimpleName(), e);
                }
            }
        }
        if(timedOut) {
            if (BuildConfig.DEBUG) {
                Log.e(tag, "Thread " + Thread.currentThread().getName() + ": Task not correctly being updated as finished from handler " + handler.getClass().getSimpleName());
            }
            task.cancel(true);
        }
        return retVal == null ? false : retVal;
    }

    public Executor getExecutor() {
        return handler != null && handler.isPerformingLogin() ? HTTP_LOGIN_THREAD_POOL_EXECUTOR : HTTP_THREAD_POOL_EXECUTOR;
    }

    public ConnectionPreferences.ProfilePreferences getConnectionPreferences() {
        return connectionPreferences;
    }

    public void setConnectionPreferences(ConnectionPreferences.ProfilePreferences connectionPreferences) {
        this.connectionPreferences = connectionPreferences;
    }
}