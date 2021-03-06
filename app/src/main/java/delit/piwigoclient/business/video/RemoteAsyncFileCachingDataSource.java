package delit.piwigoclient.business.video;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.preference.PreferenceManager;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.upstream.DataSpec;
import com.google.android.exoplayer2.upstream.HttpDataSource;
import com.google.android.exoplayer2.upstream.TransferListener;

import java.io.EOFException;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedByInterruptException;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import cz.msebera.android.httpclient.Header;
import cz.msebera.android.httpclient.client.cache.HeaderConstants;
import cz.msebera.android.httpclient.message.BasicHeader;
import cz.msebera.android.httpclient.message.BasicHeaderElement;
import cz.msebera.android.httpclient.message.BasicHeaderValueFormatter;
import delit.libs.core.util.Logging;
import delit.libs.http.RequestParams;
import delit.libs.http.cache.CachingAsyncHttpClient;
import delit.libs.http.cache.RequestHandle;
import delit.piwigoclient.BuildConfig;
import delit.piwigoclient.business.ConnectionPreferences;
import delit.piwigoclient.model.piwigo.PiwigoSessionDetails;
import delit.piwigoclient.piwigoApi.HttpClientFactory;
import delit.piwigoclient.util.UriUtils;

import static com.google.android.exoplayer2.C.LENGTH_UNSET;
import static com.google.android.exoplayer2.upstream.HttpDataSource.HttpDataSourceException.TYPE_CLOSE;
import static com.google.android.exoplayer2.upstream.HttpDataSource.HttpDataSourceException.TYPE_OPEN;
import static com.google.android.exoplayer2.upstream.HttpDataSource.HttpDataSourceException.TYPE_READ;

public class RemoteAsyncFileCachingDataSource implements HttpDataSource {

    private static final String TAG = "RemFileCacheDS";
    private TransferListener listener;
    private final Map<String, String> defaultRequestProperties;
    private final boolean logEnabled = false;
    private final SharedPreferences sharedPrefs;
    private final Context context;
    private DataSpec dataSpec;
    private CachedContent cacheMetaData;
    private RandomAccessFile localCachedDataFile;
    private long bytesAvailableToRead;
    private RequestHandle activeRequestHandle;
    private RandomAccessFileAsyncHttpResponseHandler httpResponseHandler;
    private final String userAgent;
    private final CacheListener cacheListener;
    private int connectTimeoutMillis;
    private int readTimeoutMillis;
    private boolean enableRedirects;
    private int maxRedirects;
    private long readFromFilePosition;
    private boolean performUriPathSegmentEncoding;
    private int responseCode;

    public RemoteAsyncFileCachingDataSource(Context context, CacheListener cacheListener, RequestProperties defaultRequestProperties, String userAgent) {
        this.context = context.getApplicationContext();
        this.userAgent = userAgent;
        this.cacheListener = cacheListener;
        this.defaultRequestProperties = defaultRequestProperties.getSnapshot();
        this.sharedPrefs = PreferenceManager.getDefaultSharedPreferences(context);
    }

    public void setEnableRedirects(boolean enableRedirects) {
        this.enableRedirects = enableRedirects;
    }

    public void setMaxRedirects(int maxRedirects) {
        this.maxRedirects = maxRedirects;
    }

    public void setReadTimeoutMillis(int readTimeoutMillis) {
        this.readTimeoutMillis = readTimeoutMillis;
    }

    public void setConnectTimeoutMillis(int connectTimeoutMillis) {
        this.connectTimeoutMillis = connectTimeoutMillis;
    }

    private CachedContent loadCacheAndReturnCacheMetaData(Uri remoteFileLocation) throws HttpDataSourceException {
        try {
            cacheMetaData = loadCacheMetaData(cacheMetaData, context, remoteFileLocation);
            return cacheMetaData;
        } catch (IOException e) {
            Logging.recordException(e);
            throw new HttpDataSourceException("Error loading cache", e, dataSpec, HttpDataSourceException.TYPE_OPEN);
        }
    }

    public static boolean isFileFullyCached(Context context, Uri remoteFileLocation) {
        try {
            CachedContent cacheMetaData = loadCacheMetaData(null, context, remoteFileLocation);
            return cacheMetaData.isComplete();
        } catch (IOException e) {
            return false;
        }
    }

    public static File getFullyLoadedCacheFile(Context context, Uri remoteFileLocation) {
        try {
            CachedContent cacheMetaData = loadCacheMetaData(null, context, remoteFileLocation);
            return cacheMetaData.getCachedDataFile();
        } catch (IOException e) {
            return null;
        }
    }

    private static CachedContent loadCacheMetaData(CachedContent cacheMetaData, Context context, Uri remoteFileLocation) throws IOException {
        //TODO this is rubbish - it will cause clashes as is not UUID!
        String filenameStem = CacheUtils.getVideoCacheFilenameStemFromVideoUri(remoteFileLocation.toString());
        File cacheFileMetadataFile = CacheUtils.getCacheMetadataFile(context, filenameStem);
        File cacheDataFile = CacheUtils.getCacheDataFile(context, filenameStem);

        if (cacheFileMetadataFile.exists()) {
            if (!cacheDataFile.exists()) {
                // cache is corrupt - delete meta data file
                if(!cacheFileMetadataFile.delete()) {
                    Logging.log(Log.WARN, TAG, "Failed when attempting to delete cached multimedia metadata file");
                }
            } else {
                if (cacheMetaData == null || !cacheMetaData.getCachedDataFile().equals(cacheDataFile)) {
                    cacheMetaData = CacheUtils.loadCachedContent(cacheFileMetadataFile);
                }
                if (cacheMetaData != null) {
                    return cacheMetaData;
                } else {
                    if(!cacheDataFile.delete()) {
                        Logging.log(Log.WARN, TAG, "Failed when attempting to delete cached multimedia data file");
                    }
                }
            }
        }
        cacheMetaData = new CachedContent();
        cacheMetaData.setOriginalUri(remoteFileLocation.toString());
        cacheMetaData.setCacheDataFilename(cacheDataFile.getName());
        cacheMetaData.setPersistTo(cacheFileMetadataFile);
        return cacheMetaData;
    }

    public void setPerformUriPathSegmentEncoding(boolean performUriPathSegmentEncoding) {
        this.performUriPathSegmentEncoding = performUriPathSegmentEncoding;
    }

    private void cancelAnyExistingOpenConnectionToServerAndWaitUntilDone() {
        if (activeRequestHandle != null && !activeRequestHandle.isFinished()) {
            activeRequestHandle.cancel(true);
            activeRequestHandle = null;
        }
    }

    private RandomAccessFileAsyncHttpResponseHandler openConnectionToServerInBackgroundAndContinueLoading(Uri uri, boolean allowGzip, long firstByteToRetrieve, long retrieveMaxBytes) {
        if (retrieveMaxBytes == 0) {
            return null;
        }
        ConnectionPreferences.ProfilePreferences activeConnectionPreferences = ConnectionPreferences.getPreferences(null, sharedPrefs, context);
        CachingAsyncHttpClient client = HttpClientFactory.getInstance(context).getVideoDownloadASyncHttpClient(activeConnectionPreferences, context);
        RequestParams requestParams = new RequestParams(defaultRequestProperties);

        List<Header> headers = new ArrayList<>();
        if (firstByteToRetrieve == 0 && retrieveMaxBytes == C.LENGTH_UNSET) {
            // pass the request through without range header
            if (logEnabled && BuildConfig.DEBUG) {
                Log.d(TAG, "DOWNLOAD BYTES " + "bytes=" + firstByteToRetrieve + "-" + 102400 + " of " + cacheMetaData.getTotalBytes());
            }
            headers.add(new BasicHeader("Range", "bytes=" + firstByteToRetrieve + "-" + 102400)); // 100kb (for the headers
        } else {
            long lastByteToRetrieve = (firstByteToRetrieve + retrieveMaxBytes - 1);
            String rangeRequest = "bytes=" + firstByteToRetrieve + "-";
            if (retrieveMaxBytes != C.LENGTH_UNSET) {
                rangeRequest += lastByteToRetrieve;
            }
            if (logEnabled && BuildConfig.DEBUG) {
                Log.d(TAG, "DOWNLOAD BYTES " + rangeRequest + " of " + cacheMetaData.getTotalBytes());
            }
            headers.add(new BasicHeader("Range", rangeRequest));
        }
        headers.add(new BasicHeader("User-Agent", userAgent));
        if (!allowGzip) {
            headers.add(new BasicHeader("Accept-Encoding", "identity"));
        }

        PiwigoSessionDetails sessionDetails = PiwigoSessionDetails.getInstance(activeConnectionPreferences);
        boolean onlyUseCache = sessionDetails != null && sessionDetails.isCached();
        if (onlyUseCache) {
            BasicHeaderElement[] headerElems = new BasicHeaderElement[]{new BasicHeaderElement("only-if-cached", Boolean.TRUE.toString()),
                    new BasicHeaderElement(HeaderConstants.CACHE_CONTROL_MAX_STALE, "" + Integer.MAX_VALUE)};
            String value = BasicHeaderValueFormatter.formatElements(headerElems, false, BasicHeaderValueFormatter.INSTANCE);
            headers.add(new BasicHeader(HeaderConstants.CACHE_CONTROL, value));
        }

        client.setConnectTimeout(connectTimeoutMillis);
        client.setResponseTimeout(readTimeoutMillis);
        client.setEnableRedirects(enableRedirects, maxRedirects);
        Header[] headersArray = headers.toArray(new Header[0]);

        for (Header h : headersArray) {
            Log.d(TAG, h.getValue());
        }

        boolean forceHttps = activeConnectionPreferences.isForceHttps(sharedPrefs, context);
        boolean testForExposingProxiedServer = activeConnectionPreferences.isWarnInternalUriExposed(sharedPrefs, context);
        String checkedUriStr = uri.toString();
        if(sessionDetails != null) {
            checkedUriStr = UriUtils.sanityCheckFixAndReportUri(checkedUriStr, sessionDetails.getServerUrl(), forceHttps, testForExposingProxiedServer, activeConnectionPreferences);
        }
        if (performUriPathSegmentEncoding) {
            checkedUriStr = UriUtils.encodeUriSegments(Uri.parse(checkedUriStr));
        }
        RandomAccessFileAsyncHttpResponseHandler thisResponseHandler = getResponseHandler(cacheMetaData);
        activeRequestHandle = client.get(context, checkedUriStr, headersArray, requestParams, thisResponseHandler);
        return thisResponseHandler;
    }

    private RandomAccessFileAsyncHttpResponseHandler getResponseHandler(CachedContent cacheMetaData) {
        if (httpResponseHandler == null) {
            httpResponseHandler = new RandomAccessFileAsyncHttpResponseHandler(cacheMetaData, cacheListener, true);
        }
        httpResponseHandler.setDestinationFile(localCachedDataFile);
        return httpResponseHandler;
    }

    private long openConnectionToServerAndBlockUntilContentLengthKnown(Uri uri, boolean allowGzip, long firstByteToRetrieve, long retrieveMaxBytes) throws IOException {
        if (retrieveMaxBytes == 0) {
            return 0;
        }
        RandomAccessFileAsyncHttpResponseHandler responseHandler = openConnectionToServerInBackgroundAndContinueLoading(uri, allowGzip, firstByteToRetrieve, retrieveMaxBytes);
        synchronized (cacheMetaData) {
            while (null == cacheMetaData.getRangeContaining(firstByteToRetrieve) && !responseHandler.isFailed()) {
                try {
                    cacheMetaData.wait(1000);
                } catch (InterruptedException e) {
                    if (logEnabled && BuildConfig.DEBUG) {
                        Log.d(TAG, "Awoken from slumber - do we have any more data yet?");
                    }
                }
            }
            responseCode = responseHandler.getStatusCode();
            if (responseHandler.isFailed()) {
                HttpIOException exception = new HttpIOException(responseHandler.getStatusCode(), responseHandler.getResponseData(), responseHandler.getRequestURI().toString());
                httpResponseHandler = null; // clear the http response handler so we get a new one next time.
                throw exception;
            }
        }
        return dataSpec.length == LENGTH_UNSET ? cacheMetaData.getTotalBytes() - dataSpec.position
                : dataSpec.length;
    }

    public static class HttpIOException extends IOException {
        private static final long serialVersionUID = -6054733448482594260L;
        private final int statusCode;
        private final byte[] responseData;
        private final String uri;

        public HttpIOException(int statusCode, byte[] responseData, String uri) {
            super("Error loading uri : " + uri);
            this.statusCode = statusCode;
            this.responseData = responseData;
            this.uri = uri;
        }

        public int getStatusCode() {
            return statusCode;
        }

        public byte[] getResponseData() {
            return responseData;
        }

        public String getUri() {
            return uri;
        }
    }

    private long getBytesToRetrieveFromServer(long position, CachedContent.SerializableRange cachedRangeDetail) {
        ArrayList<CachedContent.SerializableRange> ranges = cacheMetaData.getCachedRanges();
        long retVal = -1;
        if (cachedRangeDetail != null) {
            int rangeIdx = ranges.indexOf(cachedRangeDetail);
            if (rangeIdx < ranges.size() - 1) {
                CachedContent.SerializableRange nextRange = ranges.get(rangeIdx + 1);
                retVal = nextRange.getLower() - cachedRangeDetail.getUpper();
            } else if (cacheMetaData.getTotalBytes() > 0) {
                retVal = cacheMetaData.getTotalBytes() - (cachedRangeDetail.getUpper() + 1);
            } else {
                retVal = -1;
            }
        } else if (cacheMetaData.getCachedRanges().isEmpty()) {
            retVal = -1;
        } else {
            for (CachedContent.SerializableRange range : ranges) {
                if (range.getLower() > position) {
                    retVal = range.getLower() - position;
                    break;
                }
            }
            if (retVal < 0 && cacheMetaData.getTotalBytes() > 0) {
                retVal = cacheMetaData.getTotalBytes() - position;
            } else {
                retVal = -1;
            }
        }
        return Math.max(retVal, -1);
    }

    @Override
    public void addTransferListener(@NonNull TransferListener transferListener) {
        this.listener = transferListener;
    }

    @Override
    public long open(DataSpec dataSpec) throws HttpDataSourceException {
        if (logEnabled && BuildConfig.DEBUG) {
            if (dataSpec.length >= 0) {
                Log.d(TAG, String.format("OPEN STREAM: %1$d-%2$d (%3$d)", dataSpec.position, dataSpec.position + dataSpec.length - 1, dataSpec.length));
            } else {
                Log.d(TAG, String.format("OPEN STREAM: %1$d (%2$d)", dataSpec.position, dataSpec.length));
            }
        }
        if (listener != null) {
            listener.onTransferStart(this, dataSpec, true);
        }
        if (cacheMetaData == null) {
            cacheMetaData = loadCacheAndReturnCacheMetaData(dataSpec.uri);
            cacheListener.onCacheLoaded(cacheMetaData, dataSpec.position);
            if (cacheMetaData.isComplete()) {
                cacheListener.onFullyCached(cacheMetaData);
            }
        }
        this.dataSpec = dataSpec;
        try {
            bytesAvailableToRead = invokeNewCallToServer(dataSpec.position);

        } catch (FileNotFoundException e) {
            Logging.recordException(e);
            throw new HttpDataSourceException("cache data file doesn't exist (" + cacheMetaData.getCachedDataFile().getAbsolutePath() + ")", e, dataSpec, TYPE_OPEN);
        } catch (HttpIOException e) {
            Logging.recordException(e);
            throw new HttpDataSourceException("error reading from server uri (" + cacheMetaData.getOriginalUri() + ")", e, dataSpec, TYPE_OPEN);
        } catch (IOException e) {
            Logging.recordException(e);
            throw new HttpDataSourceException("error reading from cache data file (" + cacheMetaData.getCachedDataFile().getAbsolutePath() + ")", e, dataSpec, TYPE_OPEN);
        }
        try {
            // open the file.
            boolean readChannelOpen = localCachedDataFile.getChannel().isOpen();
            if(!readChannelOpen) {
                Logging.log(Log.ERROR, TAG, "Unable to open file channel to cached data");
                throw new IOException("Unable to open file channel into cached data");
            }
            long bytesRemaining = dataSpec.length == LENGTH_UNSET ? localCachedDataFile.length() - dataSpec.position
                    : dataSpec.length;
            if (bytesRemaining < 0) {
                throw new EOFException();
            }
        } catch (IOException e) {
            Logging.recordException(e);
            throw new HttpDataSourceException(e, dataSpec, TYPE_OPEN);
        }
        readFromFilePosition = dataSpec.position;
        if (dataSpec.length >= 0) {
            if (logEnabled && BuildConfig.DEBUG) {
                Log.d(TAG, String.format("STREAM READY: %1$d-%2$d (%3$d) (%4$d)", dataSpec.position, Math.max(0, dataSpec.position + dataSpec.length - 1), dataSpec.length, bytesAvailableToRead));
            }
        } else {
            if (logEnabled && BuildConfig.DEBUG) {
                Log.d(TAG, String.format("STREAM READY: %1$d (%2$d) (%3$d)", dataSpec.position, dataSpec.length, bytesAvailableToRead));
            }
        }
        return bytesAvailableToRead;
    }

    private long invokeNewCallToServer(long position) throws IOException {
        boolean allowGzip = dataSpec.isFlagSet(DataSpec.FLAG_ALLOW_GZIP);

        if (localCachedDataFile == null) {
            initialiseRandomAccessCacheFile();
        }

        long bytesAvailableToRead;
        CachedContent.SerializableRange cachedRangeDetail = cacheMetaData.getRangeContaining(position);
        if (cachedRangeDetail == null) {
            cancelAnyExistingOpenConnectionToServerAndWaitUntilDone();
            long retrieveMaxBytes = getBytesToRetrieveFromServer(position, null);
            bytesAvailableToRead = openConnectionToServerAndBlockUntilContentLengthKnown(dataSpec.uri, allowGzip, position, retrieveMaxBytes);
        } else {
            boolean rangeEndsBeforeFileEnd = cacheMetaData.getTotalBytes() <= 0 || (cachedRangeDetail.getUpper() + 1) < cacheMetaData.getTotalBytes();
            if (rangeEndsBeforeFileEnd) {
                if (dataSpec.length == C.LENGTH_UNSET) {
                    cancelAnyExistingOpenConnectionToServerAndWaitUntilDone();
                    long retrieveMaxBytes = getBytesToRetrieveFromServer(position, cachedRangeDetail);
                    if (cacheMetaData.getTotalBytes() > 0) {
                        openConnectionToServerInBackgroundAndContinueLoading(dataSpec.uri, allowGzip, cachedRangeDetail.getUpper() + 1, retrieveMaxBytes);
                        bytesAvailableToRead = cacheMetaData.getTotalBytes() - position;
                    } else {
                        bytesAvailableToRead = openConnectionToServerAndBlockUntilContentLengthKnown(dataSpec.uri, allowGzip, cachedRangeDetail.getUpper() + 1, retrieveMaxBytes);
                    }
                } else {
                    if (cachedRangeDetail.getBytesFrom(position) < dataSpec.length - (dataSpec.position - position)) {
                        cancelAnyExistingOpenConnectionToServerAndWaitUntilDone();
                        long retrieveMaxBytes = getBytesToRetrieveFromServer(position, cachedRangeDetail);
                        openConnectionToServerInBackgroundAndContinueLoading(dataSpec.uri, allowGzip, cachedRangeDetail.getUpper() + 1, retrieveMaxBytes);
                        bytesAvailableToRead = cacheMetaData.getTotalBytes() - position;
                    } else {
                        bytesAvailableToRead = dataSpec.length - (dataSpec.position - position);
                    }
                }
            } else {
                bytesAvailableToRead = cachedRangeDetail.getBytesFrom(position);
            }
        }
        return bytesAvailableToRead;
    }

    private void ensureCacheFileExistsAndCacheMetadataInSync() throws IOException {
        if (!cacheMetaData.getCachedDataFile().exists()) {
            loadCacheAndReturnCacheMetaData(dataSpec.uri);
            initialiseRandomAccessCacheFile();
            cancelAnyExistingOpenConnectionToServerAndWaitUntilDone();
        }
    }

    private void initialiseRandomAccessCacheFile() throws IOException {
        if (!cacheMetaData.getCachedDataFile().exists()) {
            if(!cacheMetaData.getCachedDataFile().createNewFile()) {
                Logging.log(Log.ERROR, TAG, "Unable to create new multimedia cache data file");
            }
        }
        localCachedDataFile = new RandomAccessFile(cacheMetaData.getCachedDataFile(), "rw");
    }

    @Override
    public int read(byte[] buffer, int bufferOffset, int readLength) throws HttpDataSourceException {
        if (logEnabled && BuildConfig.DEBUG) {
            Log.d(TAG, String.format("READING STREAM: %1$d-%2$d (%3$d) (_%4$d_)", readFromFilePosition, readFromFilePosition + readLength - 1, readLength, bytesAvailableToRead));
        }
        if (readLength == 0) {
            return 0;
        } else if (bytesAvailableToRead == 0) {
            return C.RESULT_END_OF_INPUT;
        } else {
            //ensure we have data.
            long currentPosition = cacheMetaData.getTotalBytes() - bytesAvailableToRead;
            long maxAvailableToReadNow = 0;
            synchronized (cacheMetaData) {
                CachedContent.SerializableRange activeRange = cacheMetaData.getRangeContaining(currentPosition);
                if (activeRange != null) {
                    maxAvailableToReadNow = activeRange.getBytesFrom(currentPosition);
                } else {
                    if (activeRequestHandle == null || activeRequestHandle.isFinished() || activeRequestHandle.isCancelled()) {
                        if (this.httpResponseHandler.isLoadFailed() || this.httpResponseHandler.isIdle()) {
                            try {
                                if (logEnabled && BuildConfig.DEBUG) {
                                    Log.e(TAG, "Need to invoke a server call - No data Range available covering position " + currentPosition);
                                }
                                invokeNewCallToServer(currentPosition);
                            } catch (IOException e) {
                                Logging.recordException(e);
                                throw new HttpDataSourceException("cache data file that was in use suddenly doesn't exist (" + cacheMetaData.getCachedDataFile().getAbsolutePath() + ")", e, dataSpec, TYPE_OPEN);
                            }
                        }
                    }
                }

                while (maxAvailableToReadNow == 0 && activeRequestHandle != null) {
                    if (!activeRequestHandle.isFinished() && !activeRequestHandle.isCancelled()) {
                        try {
                            cacheMetaData.wait();
                        } catch (InterruptedException e) {
                            if (logEnabled && BuildConfig.DEBUG) {
                                Log.d(TAG, "Awoken from slumber - do we have any more data yet?");
                            }
                        }
                    }
                    if (activeRequestHandle != null && (activeRequestHandle.isFinished() || activeRequestHandle.isCancelled())) {
                        activeRequestHandle = null;
                        if (this.httpResponseHandler.isLoadFailed()) {
                            try {
                                invokeNewCallToServer(currentPosition);
                            } catch (IOException e) {
                                Logging.recordException(e);
                                throw new HttpDataSourceException("cache data file that was in use suddenly doesn't exist (" + cacheMetaData.getCachedDataFile().getAbsolutePath() + ")", e, dataSpec, TYPE_OPEN);
                            }
                        }
                    }
                    if (activeRange == null) {
                        activeRange = cacheMetaData.getRangeContaining(currentPosition);
                    }
                    if (activeRange != null) {
                        if (currentPosition == cacheMetaData.getTotalBytes() - 1) {
                            maxAvailableToReadNow = -1;
                        } else {
                            maxAvailableToReadNow = activeRange.getBytesFrom(currentPosition);
                        }
                    } else {
                        if (logEnabled && BuildConfig.DEBUG) {
                            Log.d(TAG, "Waiting for data Range to become available covering position " + currentPosition);
                        }
                    }
                }
            }

            int bytesRead = 0;
            try {
                int bytesToReadNow = (int) Math.min(maxAvailableToReadNow, readLength);
                if (bytesToReadNow > 0) {
                    ByteBuffer buff = ByteBuffer.wrap(buffer, bufferOffset, bytesToReadNow);
                    FileChannel cachedDataReadChannel = localCachedDataFile.getChannel();
                    try {
                        if (!cachedDataReadChannel.isOpen()) {
                            if (logEnabled && BuildConfig.DEBUG) {
                                Log.e(TAG, "Expected open channel but was closed");
                            }
                            ensureCacheFileExistsAndCacheMetadataInSync();
                            // we return 0 here because that will force a new request that will retrieve the file again.
                            return 0;
                        }
                        bytesRead = cachedDataReadChannel.read(buff, readFromFilePosition);
                    } catch(ClosedByInterruptException e) {
                        Logging.log(Log.DEBUG, TAG, "Read failed - request cancelled by user");
                    } catch (Throwable th) {
                        Logging.recordException(th);
                        if (logEnabled && BuildConfig.DEBUG) {
                            Log.e(TAG, "Error locking channel that is probably closed", th);
                        }
                    }
//                    Log.d(TAG, String.format("NEXT READ POS: %1$d READ(%2$d)", readFromFilePosition, bytesRead));
                    buff.flip();
                }
                if (bytesRead > 0) {
                    readFromFilePosition += bytesRead;
                    bytesAvailableToRead -= bytesRead;
                    if (listener != null) {
                        listener.onBytesTransferred(this, dataSpec, true, bytesRead);
                    }
                } else {
                    if (cacheMetaData.getTotalBytes() == currentPosition) {
                        throw new EOFException();
                    }
                }
            } catch (IOException e) {
                Logging.recordException(e);
                throw new HttpDataSourceException(e, dataSpec, TYPE_READ);
            } catch (Throwable th) {
                Logging.recordException(th);
                throw new HttpDataSourceException("Something weird went wrong", new IOException(th), dataSpec, TYPE_READ);
            }


            return bytesRead;
        }
    }

    @Override
    public void setRequestProperty(String name, String value) {

    }

    @Override
    public void clearRequestProperty(String name) {

    }

    @Override
    public void clearAllRequestProperties() {

    }

    @Override
    public int getResponseCode() {
        return httpResponseHandler == null || responseCode <= 0 ? -1 : responseCode;
    }

    @Override
    public Map<String, List<String>> getResponseHeaders() {
        return httpResponseHandler.getResponseHeaders();
    }

    @Override
    public Uri getUri() {
        return dataSpec != null ? dataSpec.uri : null;
    }

    @Override
    public void close() throws HttpDataSourceException {
        if (logEnabled && BuildConfig.DEBUG) {
            Log.d(TAG, "Closing local cache file");
        }
        cancelAnyExistingOpenConnectionToServerAndWaitUntilDone();
        boolean closingFile = false;
        try {
            if (localCachedDataFile != null) {
                closingFile = true;
                localCachedDataFile.close();
            }
        } catch (IOException e) {
            Logging.recordException(e);
            throw new HttpDataSourceException(e, dataSpec, TYPE_CLOSE);
        } finally {
            localCachedDataFile = null;
            if (closingFile) {
                if (listener != null) {
                    listener.onTransferEnd(this, dataSpec, true);
                }
            }
            dataSpec = null;
        }
    }

    public void pauseBackgroundLoad() {
        cancelAnyExistingOpenConnectionToServerAndWaitUntilDone();
    }

    public void resumeBackgroundLoad() {
        if ((activeRequestHandle == null || activeRequestHandle.isFinished() || activeRequestHandle.isCancelled()) && dataSpec != null) {
            long currentPosition = dataSpec.position;
            if (cacheMetaData.getTotalBytes() >= 0) {
                currentPosition = cacheMetaData.getTotalBytes() - bytesAvailableToRead;
            }
            try {
                invokeNewCallToServer(currentPosition);
            } catch (IOException e) {
                Logging.recordException(e);
                if (logEnabled && BuildConfig.DEBUG) {
                    Log.e(TAG, "cache data file that was in use suddenly doesn't exist (" + cacheMetaData.getCachedDataFile().getAbsolutePath() + ")", e);
                }
            }
        }
    }

    public interface CacheListener {
        void onFullyCached(CachedContent cacheContent);

        void onRangeAdded(CachedContent cacheFileContent, long lowerBound, long upperBound, long bytesAddedToRange);

        void onCacheLoaded(CachedContent cacheFileContent, long position);
    }
}
