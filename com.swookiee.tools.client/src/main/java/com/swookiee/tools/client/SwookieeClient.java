package com.swookiee.tools.client;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.util.List;

import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpStatus;
import org.apache.http.StatusLine;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.FileEntity;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.swookiee.runtime.ewok.representation.BundleRepresentation;
import com.swookiee.runtime.ewok.representation.BundleStatusRepresentation;

/**
 * This Class provides a very simple client for the OSGi RFC-182 based implementation of swookiee. For now it supports
 * reading the information of installed Bundles and enables you to install and start bundles from remote.
 * <p>
 * Note: Since this API is in a very early stage changes may occur.
 */
public final class SwookieeClient {

    private static final String FRAMEWORK_BUNDLES = "/framework/bundles";
    private static final String FRAMEWORK_BUNDLES_REPRESENTATIONS = "/framework/bundles/representations";

    private static final Logger logger = LoggerFactory.getLogger(SwookieeClient.class);

    private final CloseableHttpClient httpclient;
    private final HttpClientContext swookieeContext;
    private final HttpHost httpHost;
    private final ObjectMapper mapper;

    public SwookieeClient(final CloseableHttpClient httpclient, final HttpClientContext swookieeContext,
            final HttpHost httpHost) {
        this.httpclient = httpclient;
        this.swookieeContext = swookieeContext;
        this.httpHost = httpHost;
        this.mapper = new ObjectMapper();
    }

    public void stop() {
        try {
            httpclient.close();
        } catch (final IOException ex) {
            logger.error("Could not close HTTP connection.", ex);
        }
    }

    public String installBundle(final File file) throws SwookieeClientException, IOException {
        return installBundle(file, false);
    }

    public String installBundle(final File file, final boolean forceInstall) throws SwookieeClientException,
    IOException {
        final HttpPost post = new HttpPost(FRAMEWORK_BUNDLES);
        addFile(post, file);
        addForceHeader(post);
        final String response = makeCall(post, HttpStatus.SC_OK);
        return response.trim();
    }

    public void startBundle(final String bundlePath) throws SwookieeClientException {
        final HttpPut put = new HttpPut(String.format("%s/state", bundlePath));
        addActivate(put);
        makeCall(put, HttpStatus.SC_OK);
    }

    public String getConfiguredTarget() {
        return this.httpHost.toString();
    }

    public List<BundleRepresentation> getInstalledBundles() throws SwookieeClientException, JsonParseException,
    JsonMappingException, IllegalStateException, IOException {
        final HttpGet get = new HttpGet(FRAMEWORK_BUNDLES_REPRESENTATIONS);
        final String response = makeCall(get, HttpStatus.SC_OK);
        final List<BundleRepresentation> representations = mapper.readValue(response, mapper.getTypeFactory()
                .constructCollectionType(List.class, BundleRepresentation.class));
        return representations;

    }

    private String makeCall(final HttpRequest request, final int expectedStatusCode) throws SwookieeClientException {
        try (CloseableHttpResponse response = this.httpclient.execute(this.httpHost, request, this.swookieeContext)) {
            final StatusLine returnedStatus = response.getStatusLine();
            if (returnedStatus.getStatusCode() != expectedStatusCode) {
                throw new SwookieeClientException(String.format("Error during installation %d : %s",
                        returnedStatus.getStatusCode(), returnedStatus.getReasonPhrase()));
            }
            return EntityUtils.toString(response.getEntity(), Charset.defaultCharset());
        } catch (final IOException ex) {
            throw new SwookieeClientException("Could not obtain response: " + ex.getMessage(), ex);
        }
    }

    private void addForceHeader(final HttpPost post) {
        post.setHeader("X-ForceBundleUpdate", "true");
    }

    private void addFile(final HttpPost httppost, final File file) {
        final HttpEntity entity = new FileEntity(file, ContentType.create("application/vnd.osgi.bundle"));
        httppost.setEntity(entity);
        httppost.addHeader("Content-Location", file.getName());
    }

    private void addActivate(final HttpPut put) throws SwookieeClientException {
        try {
            final BundleStatusRepresentation activateBundleRepresentation = new BundleStatusRepresentation(32, 0);
            put.setEntity(new StringEntity(mapper.writeValueAsString(activateBundleRepresentation)));
        } catch (final UnsupportedEncodingException | JsonProcessingException ex) {
            throw new SwookieeClientException("Could not add activation dto: " + ex.getMessage(), ex);
        }
    }
}
