/**
 * Copyright (c) 2010-2018 by the respective copyright holders.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.ihc.ws.http;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.time.LocalDateTime;

import org.apache.http.HttpResponse;
import org.apache.http.NoHttpResponseException;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.util.EntityUtils;
import org.openhab.binding.ihc.ws.exeptions.IhcExecption;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Simple HTTP Client for IHC / ELKO LS Controller connection purposes.
 *
 * @author Pauli Anttila - Initial contribution
 */
public abstract class IhcHttpsClient {

    private static final Logger logger = LoggerFactory.getLogger(IhcHttpsClient.class);

    final int DEF_CONNECT_TIMEOUT = 5000;

    private int connectTimeout = DEF_CONNECT_TIMEOUT;

    private HttpClient client = null;
    private HttpPost postReq = null;

    /**
     * @return the timeout in milliseconds
     *
     */
    public int getConnectTimeout() {
        return connectTimeout;
    }

    /**
     * @param timeout
     *            the timeout in milliseconds
     */
    public void setConnectTimeout(int timeout) {
        connectTimeout = timeout;
    }

    /**
     * Open HTTP connection.
     *
     * @param url
     *            Url to connect.
     */
    protected void openConnection(String url) throws IhcExecption {

        if (client == null) {
            client = IhcConnectionPool.getInstance().getHttpClient();
        }

        postReq = new HttpPost(url);
    }

    protected void closeConnection() {
    }

    /**
     * Send HTTP request and wait response from the server.
     *
     * @param query
     *            Data to send.
     * @param timeout
     *            the timeout to set in milliseconds
     * @return Response from server.
     */
    protected String sendQuery(String query, int timeout) throws IhcExecption {
        try {
            return sendQ(query, timeout);
        } catch (NoHttpResponseException e) {
            try {
                logger.debug("No response received, resend query");
                return sendQ(query, timeout);
            } catch (IOException ee) {
                throw new IhcExecption(ee);
            }
        } catch (SocketTimeoutException e) {
            try {
                logger.debug("Timeout received, resend query");
                return sendQ(query, timeout);
            } catch (IOException ee) {
                throw new IhcExecption(ee);
            }
        } catch (IOException e) {
            throw new IhcExecption(e);
        }
    }

    private String sendQ(String query, int timeout)
            throws ClientProtocolException, IOException, NoHttpResponseException {
        postReq.setEntity(new StringEntity(query, "UTF-8"));
        postReq.addHeader("content-type", "text/xml");

        logger.trace("Send query (timeout={}, headers={}): {}", timeout, postReq.getAllHeaders(), query);

        final RequestConfig params = RequestConfig.custom().setConnectTimeout(connectTimeout).setSocketTimeout(timeout)
                .build();
        postReq.setConfig(params);

        // Execute POST
        LocalDateTime start = LocalDateTime.now();
        HttpResponse response = client.execute(postReq, IhcConnectionPool.getInstance().getHttpContext());
        String resp = EntityUtils.toString(response.getEntity());
        if (logger.isTraceEnabled()) {
            logger.trace("Received response (in {}): {}", Duration.between(start, LocalDateTime.now()), resp);
        }
        return resp;
    }

    /**
     * Set request property.
     *
     * @param key
     *            property key.
     * @param value
     *            property value.
     */
    public void setRequestProperty(String key, String value) {
        postReq.setHeader(key, value);
    }
}
