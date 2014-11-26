package com.swookiee.tools.client;

import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.AuthCache;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.conn.ssl.SSLContextBuilder;
import org.apache.http.conn.ssl.TrustStrategy;
import org.apache.http.impl.auth.BasicScheme;
import org.apache.http.impl.client.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

/**
 * This Builder Class helps to create a {@link SwookieeClient} instance. You can configure target hostname, port,
 * username, password and HTTPS settings. If you do not set any properties the default target
 * <code>http://localhost:8080</code> with credentials <code>admin:admin123</code> will be used.
 * <p/>
 * Note: Since this API is in a very early stage changes may occur.
 */
public final class SwookieClientBuilder {

    private static final Logger logger = LoggerFactory.getLogger(SwookieClientBuilder.class);

    private final String hostname;
    private Integer port = 8080;
    private String username = "admin";
    private String password = "admin123";
    private boolean useHttps = false;
    private boolean useSelfSigned = false;
    private String proxyHost;
    private int proxyPort;

    private SwookieClientBuilder(final String hostname) {
        this.hostname = hostname;
    }

    public static SwookieClientBuilder newTarget(final String host) {
        return new SwookieClientBuilder(host);
    }

    public SwookieClientBuilder withPort(final Integer port) {
        this.port = port;
        return this;
    }

    public SwookieClientBuilder enableHttps() {
        this.useHttps = true;
        return this;
    }

    public SwookieClientBuilder enableSelfSignedHttps() {
        this.useSelfSigned = true;
        this.useHttps = true;
        return this;
    }

    public SwookieClientBuilder withProxy(String proxyHost, int proxyPort) {
        this.proxyHost = proxyHost;
        this.proxyPort = proxyPort;
        return this;
    }

    public SwookieClientBuilder withUsernamePassword(final String username, final String password) {
        this.username = username;
        this.password = password;
        return this;
    }

    public SwookieeClient create() throws SwookieeClientException {
        final HttpHost httpHost = getHttpHost();
        final CloseableHttpClient httpclient = getHttpClient();
        final BasicScheme basicAuth = new BasicScheme();
        final AuthCache authCache = new BasicAuthCache();
        authCache.put(httpHost, basicAuth);

        final HttpClientContext swookieeContext = HttpClientContext.create();
        swookieeContext.setAuthCache(authCache);

        return new SwookieeClient(httpclient, swookieeContext, httpHost);
    }

    private CloseableHttpClient getHttpClient() throws SwookieeClientException {
        final CredentialsProvider credsProvider = new BasicCredentialsProvider();
        credsProvider.setCredentials(new AuthScope(this.hostname, this.port), new UsernamePasswordCredentials(
                this.username, this.password));

        final HttpClientBuilder httpClientBuilder = HttpClients.custom().setDefaultCredentialsProvider(credsProvider);

        if (this.useSelfSigned) {
            try {
                final SSLContextBuilder builder = new SSLContextBuilder();
                builder.loadTrustMaterial(null, new TrustStrategy() {
                    @Override
                    public boolean isTrusted(X509Certificate[] chain, String authType)
                            throws CertificateException {
                        return true;
                    }
                });
                final SSLConnectionSocketFactory sslsf = new SSLConnectionSocketFactory(builder.build(),
                        SSLConnectionSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER);
                httpClientBuilder.setSSLSocketFactory(sslsf);

            } catch (NoSuchAlgorithmException | KeyStoreException | KeyManagementException ex) {
                throw new SwookieeClientException("Could not initiate self signed certification", ex);
            }
        }

        if (this.proxyHost != null) {
            addProxySettings(httpClientBuilder);
        }

        return httpClientBuilder.build();
    }

    private void addProxySettings(HttpClientBuilder httpClientBuilder) {
        HttpHost proxyHost = new HttpHost(this.proxyHost, this.proxyPort);
        httpClientBuilder.setProxy(proxyHost);
        logger.info("Using Proxy {} for HTTP connections", proxyHost.toString());
    }

    private HttpHost getHttpHost() {
        final HttpHost httpHost;
        if (useHttps) {
            httpHost = new HttpHost(this.hostname, this.port, "https");
        } else {
            httpHost = new HttpHost(this.hostname, this.port);
        }
        return httpHost;
    }
}
