package delit.libs.http.cache;

import android.os.Looper;

import java.lang.ref.WeakReference;

import delit.libs.http.AsyncHttpRequest;

public class RequestHandle {
    private final WeakReference<AsyncHttpRequest> request;

    public RequestHandle(AsyncHttpRequest request) {
        this.request = new WeakReference<>(request);
    }

    /**
     * Attempts to cancel this request. This attempt will fail if the request has already completed,
     * has already been cancelled, or could not be cancelled for some other reason. If successful,
     * and this request has not started when cancel is called, this request should never run. If the
     * request has already started, then the mayInterruptIfRunning parameter determines whether the
     * thread executing this request should be interrupted in an attempt to stop the request.
     * <p>&nbsp;</p> After this method returns, subsequent calls to isDone() will always return
     * true. Subsequent calls to isCancelled() will always return true if this method returned
     * true. Subsequent calls to isDone() will return true either if the request got cancelled by
     * this method, or if the request completed normally
     *
     * @param mayInterruptIfRunning true if the thread executing this request should be interrupted;
     *                              otherwise, in-progress requests are allowed to complete
     * @return false if the request could not be cancelled, typically because it has already
     * completed normally; true otherwise
     */
    public boolean cancel(final boolean mayInterruptIfRunning) {
        final AsyncHttpRequest _request = request.get();
        if (_request != null) {
            if (Looper.myLooper() == Looper.getMainLooper()) {
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        _request.cancel(mayInterruptIfRunning);
                    }
                }).start();
                // Cannot reliably tell if the request got immediately canceled at this point
                // we'll assume it got cancelled
                return true;
            } else {
                return _request.cancel(mayInterruptIfRunning);
            }
        }
        return false;
    }

    /**
     * Returns true if this task completed. Completion may be due to normal termination, an
     * exception, or cancellation -- in all of these cases, this method will return true.
     *
     * @return true if this task completed
     */
    public boolean isFinished() {
        AsyncHttpRequest _request = request.get();
        return _request == null || _request.isDone();
    }

    /**
     * Returns true if this task was cancelled before it completed normally.
     *
     * @return true if this task was cancelled before it completed
     */
    public boolean isCancelled() {
        AsyncHttpRequest _request = request.get();
        return _request == null || _request.isCancelled();
    }

    public boolean shouldBeGarbageCollected() {
        boolean should = isCancelled() || isFinished();
        if (should)
            request.clear();
        return should;
    }

    /**
     * Will return TAG of underlying AsyncHttpRequest if it's not already GCed
     *
     * @return Object TAG, can be null
     */
    public Object getTag() {
        AsyncHttpRequest _request = request.get();
        return _request == null ? null : _request.getTag();
    }

    /**
     * Will set Object as TAG to underlying AsyncHttpRequest
     *
     * @param tag Object used as TAG to underlying AsyncHttpRequest
     * @return this RequestHandle to allow fluid syntax
     */
    public RequestHandle setTag(Object tag) {
        AsyncHttpRequest _request = request.get();
        if (_request != null)
            _request.setRequestTag(tag);
        return this;
    }
}