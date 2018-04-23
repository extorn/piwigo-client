package delit.piwigoclient.piwigoApi;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.util.Log;

import java.lang.ref.WeakReference;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import delit.piwigoclient.BuildConfig;
import delit.piwigoclient.R;
import delit.piwigoclient.business.ConnectionPreferences;
import delit.piwigoclient.model.piwigo.PiwigoSessionDetails;
import delit.piwigoclient.piwigoApi.handlers.AbstractPiwigoDirectResponseHandler;
import delit.piwigoclient.piwigoApi.http.CachingAsyncHttpClient;
import delit.piwigoclient.ui.MyApplication;

public class Worker extends AsyncTask<Long, Integer, Boolean> {

    /**
     * An {@link Executor} that can be used to execute tasks in parallel.
     */
    public static final ThreadPoolExecutor HTTP_THREAD_POOL_EXECUTOR;
    private static final int CPU_COUNT = Runtime.getRuntime().availableProcessors();
    private static final int CORE_POOL_SIZE = Math.max(2, Math.min(CPU_COUNT - 1, 4));
    private static final int MAXIMUM_POOL_SIZE = CPU_COUNT * 2 + 1;
    private static final int KEEP_ALIVE_SECONDS = 30;
    private static final BlockingQueue<Runnable> sPoolWorkQueue =
            new LinkedBlockingQueue<>(128);

    private static final ThreadFactory sThreadFactory = new ThreadFactory() {
        private final AtomicInteger mCount = new AtomicInteger(1);

        public Thread newThread(@NonNull Runnable r) {
            return new Thread(r, "AsyncTask #" + mCount.getAndIncrement());
        }
    };
    static {
        ThreadPoolExecutor threadPoolExecutor = new ThreadPoolExecutor(
                CORE_POOL_SIZE, MAXIMUM_POOL_SIZE, KEEP_ALIVE_SECONDS, TimeUnit.SECONDS,
                sPoolWorkQueue, sThreadFactory);
        threadPoolExecutor.allowCoreThreadTimeOut(true);
        HTTP_THREAD_POOL_EXECUTOR = threadPoolExecutor;
    }

    private WeakReference<Context> context;

    private final String DEFAULT_TAG = "PwgAccessSvcAsyncTask";

    private String tag = DEFAULT_TAG;


    private AbstractPiwigoDirectResponseHandler handler;

    public Worker(AbstractPiwigoDirectResponseHandler handler, Context context) {
        this.context = new WeakReference<>(context);
        this.handler = handler;
    }

    public Context getContext() {
        return context.get();
    }

    public void beforeCall() {}

    private void updatePoolSize(AbstractPiwigoDirectResponseHandler handler) {
        //Update the max pool size.
        try {
            CachingAsyncHttpClient client = handler.getHttpClientFactory().getAsyncHttpClient(context.get());
            if(client != null) {
                int newMaxPoolSize = client.getMaxConcurrentConnections();
                HTTP_THREAD_POOL_EXECUTOR.setCorePoolSize(Math.min(newMaxPoolSize, Math.max(3, newMaxPoolSize / 2)));
                HTTP_THREAD_POOL_EXECUTOR.setMaximumPoolSize(newMaxPoolSize);
            }
        } catch(RuntimeException e) {
            handler.sendFailureMessage(-1, null, null, new IllegalStateException(MyApplication.getInstance().getString(R.string.error_building_http_engine), e));
        }

    }

    public void afterCall(boolean success) {
        context = null;
    }

    @Override
    protected final Boolean doInBackground(Long... params) {
        try {
            if (params.length != 1) {
                throw new IllegalArgumentException("Exactly one parameter must be passed - the id for this call");
            }
            long messageId = params[0];
            return executeCall(messageId);
        } catch(RuntimeException e) {
            if(BuildConfig.DEBUG) {
                Log.e(tag, "ASync code crashed unexpectedly", e);
            }
            return false;
        }
    }

    protected boolean executeCall(long messageId) {
        SharedPreferences prefs = null;
        if(context != null) {
            prefs = PreferenceManager.getDefaultSharedPreferences(context.get());
        }
        String piwigoServerUrl = null;
        if(PiwigoSessionDetails.isLoggedIn()) {
            piwigoServerUrl = PiwigoSessionDetails.getInstance().getServerUrl();
        } else if(prefs != null) {
            piwigoServerUrl = ConnectionPreferences.getPiwigoServerAddress(prefs, context.get());
        }
        AbstractPiwigoDirectResponseHandler handler = getHandler(prefs);
        if(handler != null) {
            this.tag = handler.getTag();
        }
        handler.setMessageId(messageId);
        handler.setCallDetails(context.get(), piwigoServerUrl, true);

        beforeCall();
        updatePoolSize(handler);

        synchronized (Worker.class) {
            if (PiwigoSessionDetails.getInstance() == null) {
                handler.getNewLogin();
            }
        }
        handler.runCall();

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
                            if(BuildConfig.DEBUG) {
                                Log.e(handler.getTag(), "Service call cancelled before handler could finish running");
                            }
                            handler.cancelCallAsap();
                        }
                    }
                } else {
                    timedOut = true;
                }
            }
        }
        if(handler.isRunning()) {
            if(BuildConfig.DEBUG) {
                Log.e(handler.getTag(), "Timeout while waiting for service call handler to finish running");
            }
            handler.cancelCallAsap();
        }

        afterCall(handler.isSuccess());

        return handler.isSuccess();
    }

    protected AbstractPiwigoDirectResponseHandler getHandler(SharedPreferences prefs) {
        return handler;
    }

    public long start(long messageId) {
        AsyncTask<Long, Integer, Boolean> task = executeOnExecutor(HTTP_THREAD_POOL_EXECUTOR, messageId);
        //TODO collect a list of tasks and kill them all if the app exits.
        return messageId;
    }
}