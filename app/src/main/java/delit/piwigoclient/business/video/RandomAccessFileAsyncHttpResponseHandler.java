/*
    Android Asynchronous Http Client
    Copyright (c) 2014 Marek Sebera <marek.sebera@gmail.com>
    https://loopj.com

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

        https://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.
*/

package delit.piwigoclient.business.video;

import android.text.TextUtils;
import android.util.Log;

import com.crashlytics.android.Crashlytics;
import com.google.android.exoplayer2.C;
import com.loopj.android.http.AsyncHttpClient;
import com.loopj.android.http.FileAsyncHttpResponseHandler;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import cz.msebera.android.httpclient.ConnectionClosedException;
import cz.msebera.android.httpclient.Header;
import cz.msebera.android.httpclient.HttpEntity;
import cz.msebera.android.httpclient.HttpResponse;
import delit.piwigoclient.BuildConfig;


public class RandomAccessFileAsyncHttpResponseHandler extends FileAsyncHttpResponseHandler {

    private static final String TAG = "RndAccFileRspHdlr";
    private static final Pattern CONTENT_RANGE_HEADER =
            Pattern.compile("^bytes (\\d+)-(\\d+)/(\\d+)$");
    private final RemoteAsyncFileCachingDataSource.CacheListener cacheListener;
    private final boolean logEnabled = false;
    private RandomAccessFile destinationFile;
    private long totalFileContentBytes;
    private long lastContentByte;
    private long firstContentByte;
    private CachedContent cacheMetaData;
    private boolean loadSucceeded;
    private boolean canParseResponseData;
    private boolean isIdle;

    public RandomAccessFileAsyncHttpResponseHandler(CachedContent cacheMetaData, RemoteAsyncFileCachingDataSource.CacheListener cacheListener, boolean usePoolThread) throws FileNotFoundException {
        super(cacheMetaData.getCachedDataFile(), false, false, usePoolThread);
        this.cacheMetaData = cacheMetaData;
        this.cacheListener = cacheListener;
    }

    @Override
    public void onFailure(int statusCode, Header[] headers, Throwable throwable, File file) {

    }

    @Override
    public void onSuccess(int statusCode, Header[] headers, File file) {
        loadSucceeded = true;
    }

    public boolean isLoadSucceeded() {
        return loadSucceeded;
    }

    @Override
    public void sendResponseMessage(HttpResponse response) throws IOException {
        // do not process if request has been cancelled
        parseContentLengthDetails(response);
        int statusCode = response.getStatusLine().getStatusCode();
        canParseResponseData = statusCode == 200 || statusCode == 206;
        super.sendResponseMessage(response);
    }

    @Override
    protected byte[] getResponseData(HttpEntity entity) throws IOException {
        isIdle = false;
        if (entity != null) {
            InputStream instream = entity.getContent();
            long contentLength = entity.getContentLength();
            if (instream != null) {
                try {
                    if (canParseResponseData) {
                        storeResponseDataToRandomAccessFile(contentLength, instream);
                    }
                } finally {
                    synchronized (cacheMetaData) {
                        cacheMetaData.consolidateRanges();
                        CacheUtils.saveCachedContent(cacheMetaData);
                    }
                    if (cacheMetaData.isComplete()) {
                        cacheListener.onFullyCached(cacheMetaData);
                    }
                    AsyncHttpClient.silentCloseInputStream(instream);
                }
            }
        }
        isIdle = true;
        return null;
    }

    private void storeResponseDataToRandomAccessFile(long contentLength, InputStream instream) throws IOException {
        CachedContent.SerializableRange existingRange;
        synchronized (cacheMetaData) {
            existingRange = cacheMetaData.getRangeContaining(firstContentByte - 1);
        }
        FileChannel destinationChannel = null;
        long onePercent = contentLength / 100; // send progress update every 1%
        try {
            byte[] tmp = new byte[BUFFER_SIZE];
            ByteBuffer buffer = ByteBuffer.wrap(tmp);
            int l, count = 0;
            destinationChannel = destinationFile.getChannel();
            long newRangeLowerBound;
            long newRangeUpperBound;
            int percentDone = 0;
            long lastSentNotification = 0;
            long downloadedBytesSinceLastReport = 0;
            // do not send messages if request has been cancelled
            while ((l = instream.read(tmp)) != -1 && !Thread.currentThread().isInterrupted()) {
                newRangeLowerBound = firstContentByte + count;
                newRangeUpperBound = newRangeLowerBound + l - 1;
                count += l;
                downloadedBytesSinceLastReport += l;
                buffer.position(l);
                buffer.flip();
                synchronized (destinationChannel) {
                    if (!destinationChannel.isOpen()) {
                        Log.d(TAG, "response handler is being run after the local cache file has been closed. Exiting");
                        //exit while loop
                        break;
                    }
                }
                destinationChannel.write(buffer, newRangeLowerBound);
                buffer.clear();
                synchronized (cacheMetaData) {
                    if (existingRange != null) {
                        existingRange.pushUpperBoundTo(newRangeUpperBound);
                    } else {
                        existingRange = cacheMetaData.addRange(newRangeLowerBound, newRangeUpperBound);
                    }
                    //TODO enable this line when download is working okay.
                    CacheUtils.saveCachedContent(cacheMetaData);
                    long now = System.currentTimeMillis();
                    if (now - lastSentNotification > 1000) {
                        // max one update every 1000 millis.
                        lastSentNotification = now;
                        cacheListener.onRangeAdded(cacheMetaData, existingRange.getLower(), existingRange.getUpper(), downloadedBytesSinceLastReport);
                        downloadedBytesSinceLastReport = 0;
                    }
                    cacheMetaData.notifyAll();
                }
//                        destinationFile.write(tmp, 0, l);
                int thisPercentDone = (int) (contentLength / onePercent);
                if (thisPercentDone > percentDone) {
                    percentDone = thisPercentDone;
                    sendProgressMessage(count, contentLength);
                }
            }
            if (downloadedBytesSinceLastReport > 0) {
                cacheListener.onRangeAdded(cacheMetaData, existingRange.getLower(), existingRange.getUpper(), downloadedBytesSinceLastReport);
            }
        } catch (SocketException e) {
            Crashlytics.logException(e);
            Log.d(TAG, "Sinking Socket exception");
        } catch (ConnectionClosedException e) {
            Crashlytics.logException(e);
            if (logEnabled && BuildConfig.DEBUG) {
                Log.d(TAG, "Connection closed while reading data from http response. The request was probably cancelled. Sinking error");
            }
        } catch (IOException e) {
            Crashlytics.logException(e);
            if (logEnabled && BuildConfig.DEBUG) {
                Log.e(TAG, "Unrecoverable error reading data from http response", e);
            }
            throw e;
        } catch (Throwable e) {
            Crashlytics.logException(e);
            if (logEnabled && BuildConfig.DEBUG) {
                Log.e(TAG, "Unrecoverable error reading data from http response", e);
            }
            throw e;
        }
    }

    /**
     * Attempts to extract the length of the content from the response headers of an open connection.
     *
     * @return The extracted length, or {@link C#LENGTH_UNSET}.
     */
    private void parseContentLengthDetails(HttpResponse httpResponse) {
        long contentLength = -1;
        Header header = httpResponse.getFirstHeader("Content-Length");
        String contentLengthHeader = header.getValue();
        if (!TextUtils.isEmpty(contentLengthHeader)) {
            try {
                contentLength = Long.parseLong(contentLengthHeader);
                totalFileContentBytes = contentLength;
                firstContentByte = 0;
                lastContentByte = totalFileContentBytes - 1;
            } catch (NumberFormatException e) {
                Crashlytics.logException(e);
                if (logEnabled && BuildConfig.DEBUG) {
                    Log.e(TAG, "Unexpected Content-Length [" + contentLengthHeader + "]");
                }
            }
        }
        header = httpResponse.getFirstHeader("Content-Range");
        String contentRangeHeader = header != null ? header.getValue() : null;
        if (!TextUtils.isEmpty(contentRangeHeader)) {
            Matcher matcher = CONTENT_RANGE_HEADER.matcher(contentRangeHeader);
            if (matcher.find()) {
                try {
                    totalFileContentBytes = Long.parseLong(matcher.group(3));

                    lastContentByte = Long.parseLong(matcher.group(2));

                    firstContentByte = Long.parseLong(matcher.group(1));

                    Log.d(TAG, String.format("RETRIEVED BYTES %1$d-%2$d of %3$d", firstContentByte, lastContentByte, totalFileContentBytes));

                    long contentLengthFromRange =
                            (lastContentByte - firstContentByte) + 1;

                    if (contentLength < 0) {
                        // Some proxy servers strip the Content-Length header. Fall back to the length
                        // calculated here in this case.
                        contentLength = contentLengthFromRange;
                    } else if (contentLength != contentLengthFromRange) {
                        // If there is a discrepancy between the Content-Length and Content-Range headers,
                        // assume the one with the larger value is correct. We have seen cases where carrier
                        // change one of them to reduce the size of a request, but it is unlikely anybody would
                        // increase it.
                        if (logEnabled && BuildConfig.DEBUG) {
                            Log.w(TAG, "Inconsistent headers [" + contentLengthHeader + "] [" + contentRangeHeader
                                    + "]");
                        }
                        contentLength = Math.max(contentLength, contentLengthFromRange);
                    }
                    Log.d(TAG, "total file bytes " + totalFileContentBytes);
                    cacheMetaData.setTotalBytes(totalFileContentBytes);
                } catch (NumberFormatException e) {
                    Crashlytics.logException(e);
                    if (logEnabled && BuildConfig.DEBUG) {
                        Log.e(TAG, "Unexpected Content-Range [" + contentRangeHeader + "]");
                    }
                }
            }
        } else {
            Log.d(TAG, String.format("RETRIEVED ALL BYTES (%1$d-%2$d of %3$d)", firstContentByte, lastContentByte, totalFileContentBytes));
        }
    }

    public void setDestinationFile(RandomAccessFile destinationFile) {
        this.destinationFile = destinationFile;
        this.loadSucceeded = false;
    }

    public boolean isIdle() {
        return isIdle;
    }
}
