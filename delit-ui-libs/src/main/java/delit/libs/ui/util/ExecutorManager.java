package delit.libs.ui.util;

import android.util.Log;

import androidx.annotation.IntRange;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import delit.libs.core.util.Logging;

public class ExecutorManager {
    private static final String TAG = "ShortLivedExecutor";
    private final ThreadPoolExecutor executor;
    private final int queueCapacity;

    public ExecutorManager(int coreThreadPool, int maxThreadPool, long threadKeepAliveMillis, @IntRange(from = 1) int queueCapacity) {
        this.queueCapacity = queueCapacity;
        executor = new ThreadPoolExecutor(coreThreadPool, maxThreadPool, threadKeepAliveMillis, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(queueCapacity));
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.DiscardPolicy() {
            @Override
            public void rejectedExecution(Runnable r, ThreadPoolExecutor e) {
                if(waitForSpaceOnQueue()) {
                    e.execute(r);
                }
            }
        });
    }

    public ExecutorManager(int coreThreadPool, int maxThreadPool, long threadKeepAliveMillis, int queueCapacity, ThreadFactory threadFactory) {
        this(coreThreadPool, maxThreadPool, threadKeepAliveMillis, queueCapacity);
        executor.setThreadFactory(threadFactory);
    }

    public static <T> T getFromFuture(Future<T> future, long shortWaitMillis, long maxWaitMillis) throws ExecutionException, InterruptedException, TimeoutException {
        boolean retry;
        long timeoutAt = System.currentTimeMillis() + maxWaitMillis;
        do {
            retry = false;
            try {
                return future.get(shortWaitMillis, TimeUnit.MILLISECONDS);
            } catch (TimeoutException e) {
                if(System.currentTimeMillis() < timeoutAt) {
                    retry = true;
                }
            }
        } while(retry);
        throw new TimeoutException("Waited as long as possible");
    }


    public abstract static class TaskSubmitter<T,S> implements Callable<List<Future<T>>> {

        private final ArrayList<Future<T>> taskFutures;
        private final ExecutorManager executorManager;
        private final Collection<S> items;

        public TaskSubmitter(ExecutorManager executorManager, Collection<S> items) {
            this.executorManager = executorManager;
            this.items = items;
            taskFutures = new ArrayList<>(items.size());
        }

        @Override
        public List<Future<T>> call() {
            for(S item : items) {
                submitTask(buildTask(item));
            }
            return taskFutures;
        }

        public abstract Callable<T> buildTask(S item);

        protected final void submitTask(Callable<T> task) {
            taskFutures.add(executorManager.submit(task));
        }
    }

    public <T> Future<List<Future<T>>> submitTasksInTask(TaskSubmitter<T,?> task) {
        return executor.submit(task);
    }

    public <T> Future<T> submit(Callable<T> task) {
        return executor.submit(task);
    }

    private boolean waitForSpaceOnQueue() {
        while(0 == executor.getQueue().remainingCapacity() && !(executor.isTerminating() || executor.isShutdown())) {
            synchronized (this) {
                try {
                    wait(500);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
        return !executor.isTerminating() && !executor.isShutdown();
    }

    public Future<?> submit(Runnable task) {
        return executor.submit(task);
    }

    public <T> Future<T> submit(Runnable task, T result) {
        return executor.submit(task, result);
    }

    public void shutdown() {
        shutdown(5);
    }

    /**
     * @param waitForTasksToFinishSecs seconds to wait before forcing shutdown
     * @return true if all tasks finished. false if the executor was shutdown first
     */
    public boolean shutdown(int waitForTasksToFinishSecs) {
        try {
            Logging.log(Log.DEBUG, TAG,"attempt to shutdown executor");
            executor.shutdown();
            executor.awaitTermination(waitForTasksToFinishSecs, TimeUnit.SECONDS);
            return true;
        } catch (InterruptedException e) {
            Logging.log(Log.WARN, TAG, "tasks interrupted");
            return false;
        } finally {
            if (!executor.isTerminated()) {
                Logging.log(Log.ERROR, TAG,"cancel non-finished tasks");
            }
            executor.shutdownNow();
            Logging.log(Log.DEBUG, TAG,"shutdown finished");
        }
    }

    public void allowCoreThreadTimeOut(boolean allowTimeout) {
        executor.allowCoreThreadTimeOut(allowTimeout);
    }

    public Executor getExecutor() {
        return executor;
    }

    public boolean isBusy() {
        return !executor.getQueue().isEmpty();
    }

    public void blockIfBusy(boolean blockIfBusy) {
        if(!blockIfBusy) {
            executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        }
    }
}
