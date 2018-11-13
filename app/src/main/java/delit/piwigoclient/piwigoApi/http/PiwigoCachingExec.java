/*
 * ====================================================================
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 * ====================================================================
 *
 * This software consists of voluntary contributions made by many
 * individuals on behalf of the Apache Software Foundation.  For more
 * information on the Apache Software Foundation, please see
 * <http://www.apache.org/>.
 *
 */
package delit.piwigoclient.piwigoApi.http;

import java.io.IOException;

import cz.msebera.android.httpclient.Header;
import cz.msebera.android.httpclient.HeaderElement;
import cz.msebera.android.httpclient.HttpException;
import cz.msebera.android.httpclient.annotation.ThreadSafe;
import cz.msebera.android.httpclient.client.cache.HeaderConstants;
import cz.msebera.android.httpclient.client.methods.CloseableHttpResponse;
import cz.msebera.android.httpclient.client.methods.HttpExecutionAware;
import cz.msebera.android.httpclient.client.methods.HttpRequestWrapper;
import cz.msebera.android.httpclient.client.protocol.HttpClientContext;
import cz.msebera.android.httpclient.conn.routing.HttpRoute;
import cz.msebera.android.httpclient.impl.execchain.ClientExecChain;
import cz.msebera.android.httpclient.message.BasicHeader;
import cz.msebera.android.httpclient.message.BasicHeaderElement;

/* LogFactory removed by HttpClient for Android script. */

/**
 * Strips headers preventing caching.
 * Ensures that it will check with the server if possible though.
 *
 * @since 4.3
 */
@ThreadSafe // So long as the responseCache implementation is threadsafe
public class PiwigoCachingExec implements ClientExecChain {
    private final ClientExecChain backendExec;

    public PiwigoCachingExec(final ClientExecChain backendExec) {
        this.backendExec = backendExec;
    }

    @Override
    public CloseableHttpResponse execute(
            final HttpRoute route,
            final HttpRequestWrapper request,
            final HttpClientContext clientContext,
            final HttpExecutionAware execAware) throws IOException, HttpException {
        CloseableHttpResponse response = backendExec.execute(route, request, clientContext, execAware);
        if(HeaderConstants.GET_METHOD.equals(request.getMethod())) {
            // force allow caching.
            Header h = response.getFirstHeader(HeaderConstants.CACHE_CONTROL);
            if(h != null) {
                HeaderElement[] cachingFlags = h.getElements();
                int mustRevalidateIdx = -1;
                for (int i = 0; i < cachingFlags.length; i++) {
                    if(HeaderConstants.CACHE_CONTROL_NO_CACHE.equals(cachingFlags[i].getName())) {
                        cachingFlags[i] = null;
                    } else if(HeaderConstants.PRAGMA.equals(cachingFlags[i].getName())) {
                        cachingFlags[i] = null;
                    } else if(HeaderConstants.CACHE_CONTROL_NO_STORE.equals(cachingFlags[i].getName())) {
                        cachingFlags[i] = null;
                    } else if(HeaderConstants.CACHE_CONTROL_MUST_REVALIDATE.equals(cachingFlags[i].getName())) {
                        // replace the flag to allow us to allow use of cached items
                        cachingFlags[i] = new BasicHeaderElement(HeaderConstants.CACHE_CONTROL_MAX_AGE, "0");
                    }
                }
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < cachingFlags.length; i++) {
                    HeaderElement he = cachingFlags[i];
                    if(he != null) {
                        if(sb.length() > 0) {
                            sb.append(", ");
                        }
                        sb.append(he);
                    }
                }
                String newValue = sb.toString();
                response.setHeader(HeaderConstants.CACHE_CONTROL, newValue);
            }
        }
        return response;
    }
}
