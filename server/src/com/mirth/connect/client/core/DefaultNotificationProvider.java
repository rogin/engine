package com.mirth.connect.client.core;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.io.IOUtils;
import org.apache.http.HttpEntity;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.client.utils.HttpClientUtils;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.config.SocketConfig;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.entity.ContentType;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.BasicHttpClientConnectionManager;
import org.apache.http.ssl.SSLContexts;
import org.apache.http.util.EntityUtils;

import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.zafarkhaja.semver.Version;
import com.mirth.connect.model.notification.Notification;
import com.mirth.connect.util.MirthSSLUtil;

/**
 * Pulls data from a GitHub project referenced by {@link BrandingConstants#RELEASES_URL}
 */
public class DefaultNotificationProvider implements NotificationProvider {
    private final static int TIMEOUT = 10000;
    public final static Integer MILLIS_PER_DAY = 86400000;

    @Override
    public List<Notification> getNotifications(String serverId, Version currentVersion,
            Map<String, String> extensionVersions, String[] protocols, String[] cipherSuites) throws ClientException {
        List<Notification> validNotifications = Collections.emptyList();

        CloseableHttpClient httpClient = null;
        CloseableHttpResponse httpResponse = null;
        HttpEntity responseEntity = null;

        try {
            HttpClientContext getContext = HttpClientContext.create();
            getContext.setRequestConfig(createRequestConfig());
            httpClient = getClient(protocols, cipherSuites);
            httpResponse = httpClient.execute(new HttpGet(BrandingConstants.RELEASES_URL), getContext);

            int statusCode = httpResponse.getStatusLine().getStatusCode();
            if (statusCode != HttpStatus.SC_OK) throw new ClientException("Status code: " + statusCode);
            
            responseEntity = httpResponse.getEntity();

            Stream<JsonNode> newerOnly = filterForNewerVersions(toJsonStream(responseEntity), currentVersion);
            validNotifications = newerOnly.map(node -> toNotification(node)).collect(Collectors.toList());
        } catch(IOException e) {
            throw new ClientException("Failed to handle network response", e);
        } finally {
            EntityUtils.consumeQuietly(responseEntity);
            HttpClientUtils.closeQuietly(httpResponse);
            HttpClientUtils.closeQuietly(httpClient);
        }

        return validNotifications;
    }

    /**
     * Create a request config with appropriate network timeouts.
     * 
     * @return
     */
    protected RequestConfig createRequestConfig() {
        return RequestConfig.custom().setConnectTimeout(TIMEOUT).setConnectionRequestTimeout(TIMEOUT).setSocketTimeout(TIMEOUT).build();
    }

    /**
     * Filter the stream to only new versions, then create a notification for each.
     * 
     * @param nodes
     * @param currentVersion
     * @return a non-null List
     */
    protected Stream<JsonNode> filterForNewerVersions(Stream<JsonNode> nodes, Version currentVersion) {
        return nodes.filter(node -> isCurrentOlderThan(currentVersion, node.get("tag_name").asText()));
    }

    /**
     * Convert a JSON response to a stream of {@link JsonNode}.
     * 
     * @param responseEntity
     * @return a stream
     * @throws IOException
     * @throws JsonMappingException
     */
    protected Stream<JsonNode> toJsonStream(HttpEntity responseEntity) throws IOException, JsonMappingException {
        String responseContent = getResponseContent(responseEntity);

        JsonNode rootNode = new ObjectMapper().readTree(responseContent);

        //convert to stream to simplify
        return StreamSupport.stream(
            Spliterators.spliteratorUnknownSize(rootNode.elements(), Spliterator.ORDERED),false);
    }

    /**
     * Extract the reponse content. It attempts to use the charset found in the response, if any.
     * 
     * @param responseEntity
     * @return
     * @throws IOException
     */
    protected String getResponseContent(HttpEntity responseEntity) throws IOException {
        return IOUtils.toString(responseEntity.getContent(), getCharset(responseEntity));
    }

    /**
     * Try pulling a charset from the given response. Default to {@link ContentType#TEXT_PLAIN}.
     * 
     * @param responseEntity
     * @return
     */
    protected Charset getCharset(HttpEntity responseEntity) {
        Charset charset = ContentType.TEXT_PLAIN.getCharset();
        try {
            charset = ContentType.getOrDefault(responseEntity).getCharset();
        } catch (Exception ignore) {}
        return charset;
    }

    /**
     * Compare the current version with another. If current is less than other, return true. All others,
     * including a malformed other, returns false.
     * 
     * @param currentVersion
     * @param anotherVersion
     * @return true if the current version is less than than the other
     */
    protected boolean isCurrentOlderThan (Version currentVersion, String anotherVersion) {
        Optional<Version> parsedOther = Version.tryParse(anotherVersion);
        
        return parsedOther.isPresent() && currentVersion.isLowerThan(parsedOther.get());
    }

    /**
     * Given a JSON node from a GitHub release feed, convert it to a HTML notification.
     * 
     * @param node
     * @return a notification with HTML content
     */
    protected Notification toNotification(JsonNode node) {
        Notification notification = new Notification();
             
        notification.setId(node.get("id").asInt());
        notification.setName(node.get("name").asText());
        notification.setDate(node.get("published_at").asText());
        notification.setContent(toNotificationContent(node.get("body").asText()));

        return notification;
    }

    /**
     * Get all content before a newline (\r). If no newline, return the original string.
     * @param text
     * @return
     */
    protected String toNotificationContent(String text) {
        int idx = text.indexOf("\r");

        return idx < 0 ? text : text.substring(0, idx);
    }

    private CloseableHttpClient getClient(String[] protocols, String[] cipherSuites) {
        RegistryBuilder<ConnectionSocketFactory> socketFactoryRegistry = RegistryBuilder.<ConnectionSocketFactory> create();
        String[] enabledProtocols = MirthSSLUtil.getEnabledHttpsProtocols(protocols);
        String[] enabledCipherSuites = MirthSSLUtil.getEnabledHttpsCipherSuites(cipherSuites);
        SSLConnectionSocketFactory sslConnectionSocketFactory = new SSLConnectionSocketFactory(SSLContexts.createSystemDefault(), enabledProtocols, enabledCipherSuites, SSLConnectionSocketFactory.STRICT_HOSTNAME_VERIFIER);
        socketFactoryRegistry.register("https", sslConnectionSocketFactory);

        BasicHttpClientConnectionManager httpClientConnectionManager = new BasicHttpClientConnectionManager(socketFactoryRegistry.build());
        httpClientConnectionManager.setSocketConfig(SocketConfig.custom().setSoTimeout(TIMEOUT).build());
        return HttpClients.custom().setConnectionManager(httpClientConnectionManager).build();
    }
}
