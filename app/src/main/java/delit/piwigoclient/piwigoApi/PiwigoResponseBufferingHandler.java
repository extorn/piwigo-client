package delit.piwigoclient.piwigoApi;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.util.LongSparseArray;

import com.crashlytics.android.Crashlytics;
import com.google.gson.JsonElement;

import java.io.File;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.AtomicLong;

import delit.piwigoclient.BuildConfig;
import delit.piwigoclient.model.piwigo.ResourceItem;
import delit.piwigoclient.piwigoApi.handlers.AbstractPiwigoDirectResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.AbstractPiwigoWsResponseHandler;

/**
 * Created by gareth on 24/06/17.
 */

public class PiwigoResponseBufferingHandler {
    private static final String TAG = "PiwigoResponseHandler";
    private static final AtomicLong nextHandlerId = new AtomicLong();
    private static volatile PiwigoResponseBufferingHandler defaultInstance;
    private final Handler callbackHandler;
    private final ConcurrentMap<Long, Response> responses = new ConcurrentSkipListMap<>();
    private final ConcurrentMap<Long, Long> handlerResponseMap = new ConcurrentSkipListMap<>();
    private final ConcurrentMap<Long, PiwigoResponseListener> handlers = new ConcurrentSkipListMap<>();
    //Note: this isn't a great idea - potentially, if there's a bug, some child msg ids could get left lying around forever as orphans.
    private final LongSparseArray<LinkedHashSet<Long>> parkedChildMsgIds = new LongSparseArray<>();

    public PiwigoResponseBufferingHandler() {
        callbackHandler = new Handler(Looper.getMainLooper());
    }

    public synchronized static long getNextHandlerId() {
        long id = nextHandlerId.incrementAndGet();
        if (id < 0) {
            id = 0;
            nextHandlerId.set(0);
        }
        return id;
    }

    /**
     * Convenience singleton for apps using a process-wide EventBus instance.
     */
    public static PiwigoResponseBufferingHandler getDefault() {
        if (defaultInstance == null) {
            synchronized (PiwigoResponseBufferingHandler.class) {
                if (defaultInstance == null) {
                    defaultInstance = new PiwigoResponseBufferingHandler();
                }
            }
        }
        return defaultInstance;
    }

    public synchronized void handleAnyQueuedMessagesForHandler(PiwigoResponseListener handler) {
        //TODO maybe its something special about the fragments in a slideshow - not having state retained perhaps? Do others work as expected?
        //TODO perhaps the handlers are not being removed from the list when they are replaced by another? Maybe the new one isn't replacing them for some reason?
        //TODO this method leaves responses unhandled by handlers... both are retained. Memory leak. Re-examine the whole lifecycle of fragments that are handlers.
        List<Long> responsesMappingsToRemove = new ArrayList<>(10);
        List<Long> handlersToRemove = new ArrayList<>(10);
        HashMap<Long, Response> responsesToHandle = new HashMap<>();
        for (Map.Entry<Long, Long> handlerResponseEntry : handlerResponseMap.entrySet()) {
            if (handlerResponseEntry.getValue() == handler.getHandlerId()) { // deliberate object reference equality.
                long responseMessageId = handlerResponseEntry.getKey();
                Response r = responses.remove(responseMessageId);
                if (r != null) {
                    responsesToHandle.put(handlerResponseEntry.getKey(), r);
                }
            }
        }
        for (Iterator<Response> iterator = responsesToHandle.values().iterator(); iterator.hasNext(); ) {
            Response r = iterator.next();
            if (iterator.hasNext() && r.isEndResponse()) {
                // skip this response till last
                iterator.next();
            }
            if (handler.canHandlePiwigoResponseNow(r)) {
                handler.handlePiwigoResponse(r);
                if (r.isEndResponse()) {
                    responsesMappingsToRemove.add(r.getMessageId());
                }
            } else {
                // add it back to the queue.
                responses.put(r.getMessageId(), r);
            }
            iterator.remove();
        }
        if (responsesToHandle.size() > 0) {
            // we left the first item on the list as it is an end response which should be handled last for the handler after all other responses
            Response r = responsesToHandle.values().iterator().next();
            if (handler.canHandlePiwigoResponseNow(r)) {
                handler.handlePiwigoResponse(r);
                if (r.isEndResponse()) {
                    responsesMappingsToRemove.add(r.getMessageId());
                }
            } else {
                // add it back to the queue.
                responses.put(r.getMessageId(), r);
            }
        }

        // check which handlers are not listening for anything else
        for (Long responseId : responsesMappingsToRemove) {
            Long thisHandlerId = handlerResponseMap.remove(responseId);
            if (thisHandlerId != null && !handlerResponseMap.containsValue(thisHandlerId)) {
                handlersToRemove.add(thisHandlerId);
            }
        }
        // remove all the dead handlers.
        for (Long handlerId : handlersToRemove) {
            handlers.remove(handlerId);
        }
    }

    private void parkChildMessageId(long currentMessageId, long newMessageId) {
        LinkedHashSet<Long> spawn = parkedChildMsgIds.get(currentMessageId);
        if (spawn == null) {
            spawn = new LinkedHashSet<>();
            parkedChildMsgIds.put(currentMessageId, spawn);
        }
        spawn.add(newMessageId);
    }

    private LinkedHashSet<Long> popParkedChildMessageIds(long currentMessageId) {
        LinkedHashSet<Long> item = parkedChildMsgIds.get(currentMessageId);
        parkedChildMsgIds.remove(currentMessageId);
        return item;
    }

    public synchronized void preRegisterResponseHandlerForNewMessage(long currentMessageId, long newMessageId) {
        Long handlerId = handlerResponseMap.get(currentMessageId);
        if (handlerId == null) {
            // record the parentage for processing when a handler is re-added.
            parkChildMessageId(currentMessageId, newMessageId);
        } else {
            handlerResponseMap.put(newMessageId, handlerId);
        }
    }

    public synchronized PiwigoResponseListener registerResponseHandler(long messageId, final PiwigoResponseListener h) {
        PiwigoResponseListener oldHandler = handlers.put(h.getHandlerId(), h);
        handlerResponseMap.put(messageId, h.getHandlerId());
        LinkedHashSet<Long> childMsgIds = popParkedChildMessageIds(messageId);
        if (childMsgIds != null) {
            for (Long childMsgId : childMsgIds) {
                handlerResponseMap.put(childMsgId, h.getHandlerId());
            }
        }
        final Response r = responses.remove(messageId);
        if (r != null) {
            if (r.isEndResponse()) {
                handlerResponseMap.remove(messageId);
            }
            callbackHandler.post(new Runnable() {
                @Override
                public void run() {
                    if (h.canHandlePiwigoResponseNow(r)) {
                        h.handlePiwigoResponse(r);
                    } else {
                        requeueResponseForLaterProcessing(r, h);
                    }
                }
            });
        }
        return oldHandler;
    }

    private synchronized void requeueResponseForLaterProcessing(Response r, PiwigoResponseListener h) {
        //Trying to replace the handler and the response back on the queue for later processing.
        if (BuildConfig.DEBUG) {
            Log.e("PiwigoResponseHandler", String.format("Unable to handle message response of type %1$s at this time - queuing for later", r.getClass().getName()));
        }
        if (r.isEndResponse()) {
            handlerResponseMap.put(r.getMessageId(), h.getHandlerId());
        }
        responses.put(r.getMessageId(), r);
    }

    public synchronized void processResponse(final Response response) {
        final PiwigoResponseListener handler;
        final Long handlerId;
        if (response.isEndResponse()) {
            handlerId = handlerResponseMap.remove(response.getMessageId());
            if (BuildConfig.DEBUG) {
                Log.e(TAG, String.format("Removed handler registered for message with id %2$d after receiving message of type %1$s ", response.getClass().getName(), response.getMessageId()));
            }
        } else {
            handlerId = handlerResponseMap.get(response.getMessageId());
        }
        if (handlerId != null) {
            handler = handlers.get(handlerId);
        } else {
            if (BuildConfig.DEBUG) {
                Log.e(TAG, String.format("No handler registered for message of type %1$s with id %2$d", response.getClass().getName(), response.getMessageId()));
            }
            handler = null;
        }
        if (handler != null) {
            callbackHandler.post(new Runnable() {
                @Override
                public void run() {
                    try {
                        if (handler.canHandlePiwigoResponseNow(response)) {
                            handler.handlePiwigoResponse(response);
                        } else {
                            requeueResponseForLaterProcessing(response, handler);
                        }
                    } catch (IllegalArgumentException e) {
                        Crashlytics.logException(e);
                        //TODO this keeps happening in the wild - sink it and the response for now to prevent crash.
                        // the handler is attached, but to an unrecognised component type
                        if (BuildConfig.DEBUG) {
                            Log.e("PiwigoResponseHandler", "Handler attached to unrecognised parent component type", e);
                        }
                    }
                }
            });
        } else {
            // Allow 30 seconds grace after which this response could be expunged at any moment.
            response.setExpiresAt(System.currentTimeMillis() + 30000);
            responses.put(response.getMessageId(), response);
        }
        removeExpiredResponses();
    }

    public void removeExpiredResponses() {
        Iterator<Map.Entry<Long, Response>> iter = responses.entrySet().iterator();
        long currentTime = System.currentTimeMillis();
        while (iter.hasNext()) {
            Map.Entry<Long, Response> item = iter.next();
            if (item.getValue().getExpiresAt() < currentTime) {
                // still no handler for this...
                if (!handlerResponseMap.containsKey(item.getKey())) {
                    iter.remove();
                    if (BuildConfig.DEBUG) {
                        Log.d("handlers", "Message expired before delivery could be made");
                    }
                }
            }
        }
    }

    public synchronized PiwigoResponseListener deRegisterResponseHandler(long messageId) {
        Long handlerId = handlerResponseMap.remove(messageId);
        if (handlerId != null) {
            return handlers.remove(handlerId);
        }
        return null;
    }

    public void replaceHandler(BasicPiwigoResponseListener newHandler) {
        handlers.put(newHandler.getHandlerId(), newHandler);
    }

    public synchronized Set<Long> getUnknownMessageIds(Set<Long> messageIdsToCheck) {
        Iterator<Long> iterator = messageIdsToCheck.iterator();
        while (iterator.hasNext()) {
            Long next = iterator.next();
            if (!handlerResponseMap.containsKey(next)) {
                iterator.remove();
            }
        }
        return new HashSet<>(messageIdsToCheck);
    }

    public PiwigoResponseListener getRegisteredHandler(long handlerId) {
        return handlers.get(handlerId);
    }

    public PiwigoResponseListener getRegisteredHandlerByMessageId(long messageId) {
        Long handlerId = handlerResponseMap.get(messageId);
        if(handlerId == null) {
            return null;
        }
        return handlers.get(handlerId);
    }

    public interface PiwigoResponse extends Response {
        String getPiwigoMethod();
    }

    public interface Response {
        long getMessageId();

        boolean isEndResponse();

        long getExpiresAt();

        void setExpiresAt(long expiresAt);
    }

    public interface PiwigoResponseListener {
        /**
         * @param response
         * @return true if this response was meant for this handler.
         */
        void handlePiwigoResponse(Response response);

        boolean canHandlePiwigoResponseNow(Response response);

        long getHandlerId();
    }

    /**
     * Marker interface
     */
    public interface ErrorResponse extends Response, Serializable {
    }

    /**
     * Marker interface
     */
    public interface RemoteErrorResponse<T extends AbstractPiwigoDirectResponseHandler> extends ErrorResponse {

        T getHttpResponseHandler();
    }

    public static class CustomErrorResponse extends BaseResponse implements ErrorResponse {

        private final String errorMessage;

        public CustomErrorResponse(long messageId, String errorMessage) {
            super(messageId);
            this.errorMessage = errorMessage;
        }

        public String getErrorMessage() {
            return errorMessage;
        }
    }

    public static class PiwigoSuccessResponse extends BasePiwigoResponse {

        private final JsonElement response;

        public PiwigoSuccessResponse(long messageId, String piwigoMethod, JsonElement response, boolean isCached) {
            super(messageId, piwigoMethod, isCached);
            this.response = response;
        }

        public JsonElement getResponse() {
            return response;
        }
    }

    public static class PiwigoServerErrorResponse extends BasePiwigoResponse implements RemoteErrorResponse {
        private final transient AbstractPiwigoWsResponseHandler requestHandler;
        private final int piwigoErrorCode;
        private final String piwigoErrorMessage;

        public PiwigoServerErrorResponse(AbstractPiwigoWsResponseHandler requestHandler, int piwigoErrorCode, String piwigoErrorMessage, boolean isCached) {
            super(requestHandler.getMessageId(), requestHandler.getPiwigoMethod(), isCached);
            this.requestHandler = requestHandler;
            this.piwigoErrorCode = piwigoErrorCode;
            this.piwigoErrorMessage = piwigoErrorMessage;
        }

        public int getPiwigoErrorCode() {
            return piwigoErrorCode;
        }

        public String getPiwigoErrorMessage() {
            return piwigoErrorMessage;
        }

        @Override
        public AbstractPiwigoWsResponseHandler getHttpResponseHandler() {
            return requestHandler;
        }
    }

    public static class PiwigoUnexpectedReplyErrorResponse extends BasePiwigoResponse implements RemoteErrorResponse {

        public static final short OUTCOME_SUCCESS = 2;
        public static final short OUTCOME_FAILED = 1;
        public static final short OUTCOME_UNKNOWN = 0;

        private final String rawResponse;
        private final transient AbstractPiwigoWsResponseHandler requestHandler;
        private short requestOutcome;

        public PiwigoUnexpectedReplyErrorResponse(AbstractPiwigoWsResponseHandler requestHandler, short requestOutcome, String rawResponse, boolean isCached) {
            super(requestHandler.getMessageId(), requestHandler.getPiwigoMethod(), isCached);
            this.requestHandler = requestHandler;
            if (requestOutcome > OUTCOME_SUCCESS || requestOutcome < OUTCOME_UNKNOWN) {
                throw new IllegalArgumentException("RequestOutcome must be one of the constant values defined in " + this.getClass().getName());
            }
            this.requestOutcome = requestOutcome;
            this.rawResponse = rawResponse;
        }

        public String getRawResponse() {
            return rawResponse;
        }

        public short getRequestOutcome() {
            return requestOutcome;
        }

        public boolean requestSucceeded() {
            return requestOutcome == OUTCOME_SUCCESS;
        }

        public boolean requestFailed() {
            return requestOutcome == OUTCOME_FAILED;
        }

        @Override
        public AbstractPiwigoWsResponseHandler getHttpResponseHandler() {
            return requestHandler;
        }
    }

    public static class PiwigoHttpErrorResponse extends BasePiwigoResponse implements RemoteErrorResponse {

        private final transient AbstractPiwigoWsResponseHandler requestHandler;
        private final int statusCode;
        private final String errorMessage;
        private final String errorDetail;
        private final Throwable error;
        private String response;

        public PiwigoHttpErrorResponse(AbstractPiwigoWsResponseHandler requestHandler, int statusCode, String errorMessage, String errorDetail, Throwable error, boolean isCached) {
            super(requestHandler.getMessageId(), requestHandler.getPiwigoMethod(), isCached);
            this.error = error;
            this.requestHandler = requestHandler;
            this.statusCode = statusCode;
            this.errorMessage = errorMessage;
            this.errorDetail = errorDetail;
        }

        public void setResponse(String response) {
            this.response = response;
        }

        public String getResponse() {
            return response;
        }

        public PiwigoHttpErrorResponse(AbstractPiwigoWsResponseHandler requestHandler, int statusCode, String errorMessage, Throwable error, boolean isCached) {
            this(requestHandler, statusCode, errorMessage, null, error, isCached);
        }

        public int getStatusCode() {
            return statusCode;
        }

        public String getErrorMessage() {
            return errorMessage;
        }

        public String getErrorDetail() {
            return errorDetail;
        }

        @Override
        public AbstractPiwigoWsResponseHandler getHttpResponseHandler() {
            return requestHandler;
        }
    }

    public static class UrlCancelledResponse extends BaseUrlResponse {

        public UrlCancelledResponse(long messageId, String url) {
            super(messageId, url);
        }
    }

    public static class UrlErrorResponse extends BaseUrlResponse implements RemoteErrorResponse {

        private final transient AbstractPiwigoDirectResponseHandler requestHandler;
        private final int statusCode;
        private final String errorMessage;
        private final String errorDetail;
        private final byte[] responseBody;

        public UrlErrorResponse(AbstractPiwigoDirectResponseHandler requestHandler, String url, int statusCode, byte[] responseBody, String errorMessage, String errorDetail) {
            super(requestHandler.getMessageId(), url);
            this.requestHandler = requestHandler;
            this.statusCode = statusCode;
            this.errorMessage = errorMessage;
            this.errorDetail = errorDetail;
            this.responseBody = responseBody;
        }

        public int getStatusCode() {
            return statusCode;
        }

        public String getErrorMessage() {
            return errorMessage;
        }

        public String getErrorDetail() {
            return errorDetail;
        }

        public String getResponseBody() {
            return responseBody == null ? "" : new String(responseBody);
        }

        @Override
        public AbstractPiwigoDirectResponseHandler getHttpResponseHandler() {
            return requestHandler;
        }

    }

    public static class UrlProgressResponse extends BaseUrlResponse {
        private final int progress;

        public UrlProgressResponse(long messageId, String url, int progress) {
            super(messageId, url, false);
            this.progress = progress;
        }

        public int getProgress() {
            return progress;
        }
    }

    public static class UrlToFileSuccessResponse extends BaseUrlResponse {
        private final File file;

        public UrlToFileSuccessResponse(long messageId, String url, File file) {
            super(messageId, url);
            this.file = file;
        }

        public File getFile() {
            return file;
        }
    }

    public static class UrlSuccessResponse extends BaseUrlResponse {
        private final byte[] data;

        public UrlSuccessResponse(long messageId, String url, byte[] data) {
            super(messageId, url);
            this.data = data;
        }

        public byte[] getData() {
            return data;
        }
    }

    public static class BaseUrlResponse extends BaseResponse {
        private final String url;

        public BaseUrlResponse(long messageId, String url) {
            super(messageId);
            this.url = url;
        }

        public BaseUrlResponse(long messageId, String url, boolean isEndRequest) {
            super(messageId, isEndRequest);
            this.url = url;
        }

        public String getUrl() {
            return url;
        }
    }

    public static class BasePiwigoResponse extends BaseResponse implements PiwigoResponse {
        private final String piwigoMethod;
        private final boolean isCached;

        public BasePiwigoResponse(long messageId, String piwigoMethod, boolean isCached) {
            super(messageId);
            this.piwigoMethod = piwigoMethod;
            this.isCached = isCached;
        }

        public BasePiwigoResponse(long messageId, String piwigoMethod, boolean isEndResponse, boolean isCached) {
            super(messageId, isEndResponse);
            this.piwigoMethod = piwigoMethod;
            this.isCached = isCached;
        }

        @Override
        public String getPiwigoMethod() {
            return piwigoMethod;
        }

        public boolean isCached() {
            return isCached;
        }
    }

    public static class PiwigoResourceItemResponse<T extends ResourceItem> extends BasePiwigoResponse {
        private final T piwigoResource;

        public PiwigoResourceItemResponse(long messageId, String piwigoMethod, T piwigoResource, boolean isCached) {
            super(messageId, piwigoMethod, true, isCached);
            this.piwigoResource = piwigoResource;
        }

        public T getPiwigoResource() {
            return piwigoResource;
        }
    }


    public static class BaseResponse implements Response {

        private final long messageId;
        private final boolean isEndResponse;
        private long expiresAt;


        public BaseResponse(long messageId) {
            this(messageId, true);
        }

        public BaseResponse(long messageId, boolean isEndResponse) {
            this.messageId = messageId;
            this.isEndResponse = isEndResponse;
        }

        @Override
        public long getMessageId() {
            return messageId;
        }

        @Override
        public boolean isEndResponse() {
            return isEndResponse;
        }

        @Override
        public long getExpiresAt() {
            return expiresAt;
        }

        @Override
        public void setExpiresAt(long expiresAt) {
            this.expiresAt = expiresAt;
        }
    }

}
