/*
 * Copyright (c) Mirth Corporation. All rights reserved.
 * 
 * http://www.mirthcorp.com
 * 
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.mirth.connect.client.core;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.io.IOUtils;
import org.apache.commons.text.StringEscapeUtils;
import org.apache.http.HttpEntity;
import org.apache.http.NameValuePair;
import org.apache.http.StatusLine;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.client.utils.HttpClientUtils;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.config.SocketConfig;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.conn.ssl.SSLContexts;
import org.apache.http.entity.ContentType;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.BasicHttpClientConnectionManager;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mirth.connect.model.User;
import com.mirth.connect.model.converters.ObjectXMLSerializer;
import com.mirth.connect.model.notification.Notification;
import com.mirth.connect.util.MirthSSLUtil;

public class ConnectServiceUtil {
    private final static String URL_CONNECT_SERVER = "https://connect.mirthcorp.com";
    private final static String URL_REGISTRATION_SERVLET = "/RegistrationServlet";
    private final static String URL_USAGE_SERVLET = "/UsageStatisticsServlet";
    private final static String URL_NOTIFICATION_SERVLET = "/NotificationServlet";
    private static String URL_NOTIFICATIONS = "https://api.github.com/repos/openintegrationengine/engine/releases";
    private static String NOTIFICATION_COUNT_GET = "getNotificationCount";
    private final static int TIMEOUT = 10000;
    public final static Integer MILLIS_PER_DAY = 86400000;

    public static void registerUser(String serverId, String mirthVersion, User user, String[] protocols, String[] cipherSuites) throws ClientException {
        CloseableHttpClient httpClient = null;
        CloseableHttpResponse httpResponse = null;
        NameValuePair[] params = { new BasicNameValuePair("serverId", serverId),
                new BasicNameValuePair("version", mirthVersion),
                new BasicNameValuePair("user", ObjectXMLSerializer.getInstance().serialize(user)) };

        HttpPost post = new HttpPost();
        post.setURI(URI.create(URL_CONNECT_SERVER + URL_REGISTRATION_SERVLET));
        post.setEntity(new UrlEncodedFormEntity(Arrays.asList(params), Charset.forName("UTF-8")));
        RequestConfig requestConfig = RequestConfig.custom().setConnectTimeout(TIMEOUT).setConnectionRequestTimeout(TIMEOUT).setSocketTimeout(TIMEOUT).build();

        try {
            HttpClientContext postContext = HttpClientContext.create();
            postContext.setRequestConfig(requestConfig);
            httpClient = getClient(protocols, cipherSuites);
            httpResponse = httpClient.execute(post, postContext);
            StatusLine statusLine = httpResponse.getStatusLine();
            int statusCode = statusLine.getStatusCode();
            if ((statusCode != HttpStatus.SC_OK) && (statusCode != HttpStatus.SC_MOVED_TEMPORARILY)) {
                throw new Exception("Failed to connect to update server: " + statusLine);
            }
        } catch (Exception e) {
            throw new ClientException(e);
        } finally {
            HttpClientUtils.closeQuietly(httpResponse);
            HttpClientUtils.closeQuietly(httpClient);
        }
    }

    /**
     * Query an external source for new releases. Return notifications for each release that's greater than the current version.
     * 
     * @param serverId
     * @param mirthVersion
     * @param extensionVersions
     * @param protocols
     * @param cipherSuites
     * @return a non-null list
     * @throws Exception should anything fail dealing with the web request and the handling of its response
     */
    public static List<Notification> getNotifications(String serverId, String mirthVersion, Map<String, String> extensionVersions, String[] protocols, String[] cipherSuites) throws Exception {
        List<Notification> validNotifications = Collections.emptyList();

        CloseableHttpClient httpClient = null;
        CloseableHttpResponse httpResponse = null;
        HttpEntity responseEntity = null;

        try {
            HttpClientContext getContext = HttpClientContext.create();
            getContext.setRequestConfig(createRequestConfig());
            httpClient = getClient(protocols, cipherSuites);
            httpResponse = httpClient.execute(new HttpGet(URL_NOTIFICATIONS), getContext);

            int statusCode = httpResponse.getStatusLine().getStatusCode();
            if (statusCode == HttpStatus.SC_OK) {
                responseEntity = httpResponse.getEntity();

                var newerOnly = filterForNewerVersions(toJsonStream(responseEntity), mirthVersion);
                validNotifications = newerOnly.map(node -> toNotification(node)).collect(Collectors.toList());
            } else {
                throw new ClientException("Status code: " + statusCode);
            }
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
    private static RequestConfig createRequestConfig() {
        return RequestConfig.custom().setConnectTimeout(TIMEOUT).setConnectionRequestTimeout(TIMEOUT).setSocketTimeout(TIMEOUT).build();
    }

    /**
     * Filter the stream to only new versions, then create a notification for each.
     * 
     * @param nodes
     * @param currentVersion
     * @return a non-null List
     */
    protected static Stream<JsonNode> filterForNewerVersions(Stream<JsonNode> nodes, String currentVersion) {
        int[] curVersion = toVersionArray(currentVersion);
        return nodes.takeWhile(node -> isCurrentOlderThan(curVersion, node.get("tag_name").asText()));
    }

    /**
     * Convert a JSON response to a stream of {@link JsonNode}.
     * 
     * @param responseEntity
     * @return a stream
     * @throws IOException
     * @throws JsonMappingException
     */
    protected static Stream<JsonNode> toJsonStream(HttpEntity responseEntity) throws IOException, JsonMappingException {
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
    protected static String getResponseContent(HttpEntity responseEntity) throws IOException {
        return IOUtils.toString(responseEntity.getContent(), getCharset(responseEntity));
    }

    /**
     * Try pulling a charset from the given response. Default to {@link ContentType#TEXT_PLAIN}.
     * 
     * @param responseEntity
     * @return
     */
    protected static Charset getCharset(HttpEntity responseEntity) {
        Charset charset = ContentType.TEXT_PLAIN.getCharset();
        try {
            charset = ContentType.getOrDefault(responseEntity).getCharset();
        } catch (Exception ignore) {}
        return charset;
    }

    /**
     * Split a given version string into an int[].
     * 
     * @param version
     * @return
     */
    protected static int[] toVersionArray(String version) {
        return Arrays.stream(version.split("[\\.]")).mapToInt(Integer::parseInt).toArray();
    }

    /**
     * Compare the current version with another. If current is greater than or equal, this returns false.
     * 
     * @param currentVersion
     * @param anotherVersion
     * @return true if the current version is less than than the other
     */
    protected static boolean isCurrentOlderThan (int[] currentVersion, String anotherVersion) {
        return Arrays.compare(currentVersion, toVersionArray(anotherVersion)) < 0;
    }

    /**
     * Given a JSON node from a GitHub release feed, convert it to a HTML notification.
     * 
     * @param node
     * @return a notification with HTML content
     */
    protected static Notification toNotification(JsonNode node) {
        Notification notification = new Notification();
             
        notification.setId(node.get("id").asInt());
        notification.setName(node.get("name").asText());
        notification.setDate(node.get("published_at").asText());

        // create the content html
        String content = toNotificationContent(node);
        notification.setContent(content);

        return notification;
    }

    /**
     * Create the HTML content for a notification.
     * 
     * @param node
     * @return an HTML String
     */
    protected static String toNotificationContent (JsonNode node) {
        String escapedName = StringEscapeUtils.escapeHtml4(node.get("name").asText());
        String escapedReleaseUrl = StringEscapeUtils.escapeHtml4(node.get("html_url").asText());

        StringBuilder content = new StringBuilder(256);

        // create header with name
        content.append("<h2>")
            .append(escapedName)
            .append("</h2>");

        // announce there is a new version
        content.append("<h3>")
            .append("A new version of Open Integration Engine is available!")
            .append("</h3>");

        // create a link to the release webpage
        content.append("<a href=\"" + escapedReleaseUrl + "\">")
            .append("Release Webpage")
            .append("</a>");

        return content.toString();
    }

    public static int getNotificationCount(String serverId, String mirthVersion, Map<String, String> extensionVersions, Set<Integer> archivedNotifications, String[] protocols, String[] cipherSuites) {
        CloseableHttpClient client = null;
        HttpPost post = new HttpPost();
        CloseableHttpResponse response = null;

        int notificationCount = 0;

        try {
            ObjectMapper mapper = new ObjectMapper();
            String extensionVersionsJson = mapper.writeValueAsString(extensionVersions);
            NameValuePair[] params = { new BasicNameValuePair("op", NOTIFICATION_COUNT_GET),
                    new BasicNameValuePair("serverId", serverId),
                    new BasicNameValuePair("version", mirthVersion),
                    new BasicNameValuePair("extensionVersions", extensionVersionsJson) };
            RequestConfig requestConfig = RequestConfig.custom().setConnectTimeout(TIMEOUT).setConnectionRequestTimeout(TIMEOUT).setSocketTimeout(TIMEOUT).build();

            post.setURI(URI.create(URL_CONNECT_SERVER + URL_NOTIFICATION_SERVLET));
            post.setEntity(new UrlEncodedFormEntity(Arrays.asList(params), Charset.forName("UTF-8")));

            HttpClientContext postContext = HttpClientContext.create();
            postContext.setRequestConfig(requestConfig);
            client = getClient(protocols, cipherSuites);
            response = client.execute(post, postContext);
            StatusLine statusLine = response.getStatusLine();
            int statusCode = statusLine.getStatusCode();
            if ((statusCode == HttpStatus.SC_OK)) {
                HttpEntity responseEntity = response.getEntity();
                Charset responseCharset = null;
                try {
                    responseCharset = ContentType.getOrDefault(responseEntity).getCharset();
                } catch (Exception e) {
                    responseCharset = ContentType.TEXT_PLAIN.getCharset();
                }

                List<Integer> notificationIds = mapper.readValue(IOUtils.toString(responseEntity.getContent(), responseCharset).trim(), new TypeReference<List<Integer>>() {
                });
                for (int id : notificationIds) {
                    if (!archivedNotifications.contains(id)) {
                        notificationCount++;
                    }
                }
            }
        } catch (Exception e) {
        } finally {
            HttpClientUtils.closeQuietly(response);
            HttpClientUtils.closeQuietly(client);
        }
        return notificationCount;
    }

    public static boolean sendStatistics(String serverId, String mirthVersion, boolean server, String data, String[] protocols, String[] cipherSuites) {
        if (data == null) {
            return false;
        }

        boolean isSent = false;

        CloseableHttpClient client = null;
        HttpPost post = new HttpPost();
        CloseableHttpResponse response = null;
        NameValuePair[] params = { new BasicNameValuePair("serverId", serverId),
                new BasicNameValuePair("version", mirthVersion),
                new BasicNameValuePair("server", Boolean.toString(server)),
                new BasicNameValuePair("data", data) };
        RequestConfig requestConfig = RequestConfig.custom().setConnectTimeout(TIMEOUT).setConnectionRequestTimeout(TIMEOUT).setSocketTimeout(TIMEOUT).build();

        post.setURI(URI.create(URL_CONNECT_SERVER + URL_USAGE_SERVLET));
        post.setEntity(new UrlEncodedFormEntity(Arrays.asList(params), Charset.forName("UTF-8")));

        try {
            HttpClientContext postContext = HttpClientContext.create();
            postContext.setRequestConfig(requestConfig);
            client = getClient(protocols, cipherSuites);
            response = client.execute(post, postContext);
            StatusLine statusLine = response.getStatusLine();
            int statusCode = statusLine.getStatusCode();
            if ((statusCode == HttpStatus.SC_OK)) {
                isSent = true;
            }
        } catch (Exception e) {
        } finally {
            HttpClientUtils.closeQuietly(response);
            HttpClientUtils.closeQuietly(client);
        }
        return isSent;
    }

    private static CloseableHttpClient getClient(String[] protocols, String[] cipherSuites) {
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
