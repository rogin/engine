/*
 * Copyright (c) Mirth Corporation. All rights reserved.
 * 
 * http://www.mirthcorp.com
 * 
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.mirth.connect.client.core;

import java.net.URI;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.io.IOUtils;
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
    private static String NOTIFICATION_GET = "getNotifications";
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

    public static List<Notification> getNotifications(String serverId, String mirthVersion, Map<String, String> extensionVersions, String[] protocols, String[] cipherSuites) throws Exception {
        List<Notification> allNotifications = new ArrayList<Notification>();

        System.out.println("Entered getNotifications");

        // make an api call

        CloseableHttpClient client = HttpClients.createDefault();
        CloseableHttpResponse response = null;

        HttpGet get = new HttpGet("https://api.github.com/repos/nextgenhealthcare/connect/releases");
        // HttpGet get = new HttpGet();
        // get.setURI(URI.create("josephlpaul.com"));
        
        // HttpClientContext getContext = HttpClientContext.create();

        try {
            ObjectMapper mapper = new ObjectMapper();

            response = client.execute(get);
            System.out.println(response.getStatusLine());
            HttpEntity entity1 = response.getEntity();
            // BufferedReader br = new BufferedReader(new InputStreamReader(entity1.getContent()));
            String responseContent = IOUtils.toString(entity1.getContent(), "utf8");
 
            // System.out.println(responseContent);           

            JsonNode rootNode = mapper.readTree(responseContent);

            int i = 1;
            for (JsonNode childNode : rootNode) {
                // System.out.println(i++);
                // System.out.println(childNode.asText());

                Notification notification = new Notification();
             
                notification.setId(childNode.get("id").asInt());
                notification.setName(childNode.get("name").asText());
                notification.setDate(childNode.get("published_at").asText());

                // create the content html
                String content = createNotificationHtml(childNode.get("name").asText(), childNode.get("html_url").asText());
                notification.setContent(content);

                allNotifications.add(notification);
            }

            // do something useful with the response body
            // and ensure it is fully consumed
            EntityUtils.consume(entity1);


        } catch (Exception e) {
            System.out.print(e);
            e.printStackTrace();
            throw e;
        } finally {
            HttpClientUtils.closeQuietly(client);
            HttpClientUtils.closeQuietly(response);
        }

        // Notification notification = new Notification();

        // notification.setId(1);
        // notification.setName("4.5.3");
        // notification.setDate("4/24/25");
        // notification.setContent("<p>Tagged <a class=\"commit-link\" data-hovercard-type=\"commit\" data-hovercard-url=\"https://github.com/OpenIntegrationEngine/engine/commit/6ce3a9f0e3d84841f0b1e07c2808cf0bdb3d0a78/hovercard\" href=\"https://github.com/OpenIntegrationEngine/engine/commit/6ce3a9f0e3d84841f0b1e07c2808cf0bdb3d0a78\"><tt>6ce3a9f</tt></a></p>");

        // allNotifications.add(notification);
        // try returning some hardcoded notifications

        return allNotifications;
    }

    private static String createNotificationHtml (String name, String releaseUrl) {
        System.out.println("Entered createNotificationHtml");

        StringBuilder content = new StringBuilder();

        // create header with name
        content.append("<h3>");
        content.append(name);
        content.append("</h3>");

        // announce there is a new version with a p element
        content.append("<p>");
        content.append("A new version of Mirth Connect is available!");
        content.append("</p");

        // create a link to the release webpage
        content.append("<a href=\"" + releaseUrl + "\">");
        content.append("Release Webpage");
        content.append("</a>");

        return content.toString();
    }

    public static List<Notification> getNotifications2(String serverId, String mirthVersion, Map<String, String> extensionVersions, String[] protocols, String[] cipherSuites) throws Exception {

        System.out.println("Entered getNotifications2");


        CloseableHttpClient client = null;
        HttpPost post = new HttpPost();
        CloseableHttpResponse response = null;

        List<Notification> allNotifications = new ArrayList<Notification>();

        try {
            ObjectMapper mapper = new ObjectMapper();
            String extensionVersionsJson = mapper.writeValueAsString(extensionVersions);
            NameValuePair[] params = { new BasicNameValuePair("op", NOTIFICATION_GET),
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

                String responseContent = IOUtils.toString(responseEntity.getContent(), responseCharset).trim();
                JsonNode rootNode = mapper.readTree(responseContent);

                for (JsonNode childNode : rootNode) {
                    Notification notification = new Notification();
                    notification.setId(childNode.get("id").asInt());
                    notification.setName(childNode.get("name").asText());
                    notification.setDate(childNode.get("date").asText());
                    notification.setContent(childNode.get("content").asText());
                    allNotifications.add(notification);
                }
            } else {
                throw new ClientException("Status code: " + statusCode);
            }
        } catch (Exception e) {
            throw e;
        } finally {
            HttpClientUtils.closeQuietly(response);
            HttpClientUtils.closeQuietly(client);
        }

        return allNotifications;
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
