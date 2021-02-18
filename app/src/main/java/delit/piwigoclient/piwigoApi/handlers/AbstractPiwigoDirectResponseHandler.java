package delit.piwigoclient.piwigoApi.handlers;

import android.content.Context;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import delit.piwigoclient.business.ConnectionPreferences;
import delit.piwigoclient.piwigoApi.PiwigoResponseBufferingHandler;
import delit.piwigoclient.piwigoApi.Worker;

/**
 * Created by gareth on 27/07/17.
 */

public abstract class AbstractPiwigoDirectResponseHandler extends AbstractBasicPiwigoResponseHandler {
    private static final AtomicLong nextMessageId = new AtomicLong();
    private long messageId;
    private PiwigoResponseBufferingHandler.BaseResponse response;
    private boolean publishResponses = true;
    private Worker worker;
    private boolean runAsync;
    private static List<Long> blockedMessageIds = new ArrayList<>();

    public AbstractPiwigoDirectResponseHandler(String tag) {
        super(tag);
        messageId = getNextMessageId();
    }

    public static synchronized long getNextMessageId() {
        long id;
        do {
            id = nextMessageId.incrementAndGet();
            if (id < 0) {
                nextMessageId.set(0);
                id = 0;
            }
        } while (blockedMessageIds.contains(id));
        return id;
    }

    public static synchronized void unblockMessageId(long messageId) {
        blockedMessageIds.remove(messageId);
    }

    public static synchronized void blockMessageId(long messageId) {
        blockedMessageIds.add(messageId);
    }

    public long getMessageId() {
        return messageId;
    }

    public void setMessageId(long messageId) {
        if (this.messageId < 0 || this.messageId == messageId) {
            this.messageId = messageId;
        } else {
            throw new IllegalArgumentException("Message ID can only be set once for a handler");
        }
    }

    public void setPublishResponses(boolean publishResponses) {
        this.publishResponses = publishResponses;
    }

    @Override
    public final void preRunCall() {
        /*if(messageId < 0) {
            messageId = getNextMessageId();
        }*/
    }

    protected void storeResponse(PiwigoResponseBufferingHandler.BaseResponse response) {

        if (!getUseSynchronousMode() && publishResponses) {
            PiwigoResponseBufferingHandler.getDefault().processResponse(response);
        } else {
            this.response = response;
        }
    }

    public void setWorker(Worker worker) {
        this.worker = worker;
    }

    @Override
    public boolean isSuccess() {
        return super.isSuccess() && !isResponseError() && response != null;
    }

    public PiwigoResponseBufferingHandler.BaseResponse getResponse() {
        return response;
    }

    public boolean isResponseError() {
        return response instanceof PiwigoResponseBufferingHandler.ErrorResponse;
    }

    protected Worker buildWorker(@NonNull Context context) {
        return new Worker(this, context);
    }

    public void invokeAndWait(@NonNull Context context, ConnectionPreferences.ProfilePreferences connectionPrefs) {
        setPublishResponses(false);
        runAsync = false;
        Worker w = buildWorker(context);
        setWorker(w);
        w.setConnectionPreferences(connectionPrefs);
        w.startAndWait(messageId);
    }

    /**
     * Run in same thread.
     * @param context
     * @param connectionPrefs
     */
//    public void run(Context context, ConnectionPreferences.ProfilePreferences connectionPrefs) {
//        runAsync = false;
//        setPublishResponses(false);
//        Worker w = buildWorker(context);
//        setWorker(w);
//        w.setConnectionPreferences(connectionPrefs);
//        w.run(messageId);
//    }

    public long invokeAsync(Context context, ConnectionPreferences.ProfilePreferences connectionPrefs) {
        runAsync = true;
        Worker w = buildWorker(context);
        setWorker(w);
        w.setConnectionPreferences(connectionPrefs);
        return w.start(messageId);
    }

    public long invokeAsync(Context context) {
        runAsync = true;
        Worker w = buildWorker(context);
        setWorker(w);
        return w.start(messageId);
    }

    public void rerun(Context context) {
        if (isRunAsync()) {
            invokeAsyncAgain(context);
        } else {
            invokeAgain(context);
        }
    }

    protected boolean invokeAgain(Context context) {
        Worker w = buildWorker(context);
        w.setConnectionPreferences(worker.getConnectionPreferences());
        w.setRerunning(true);
        setWorker(w);
        return w.run(messageId);
    }

    protected long invokeAsyncAgain(Context context) {
        Worker w = buildWorker(context);
        w.setConnectionPreferences(worker.getConnectionPreferences());
        w.setRerunning(true);
        setWorker(w);
        return w.start(messageId);
    }

    protected boolean isRunAsync() {
        return runAsync;
    }
}
