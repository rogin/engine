package com.mirth.connect.client.core;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.stream.Stream;

import org.apache.http.HttpEntity;
import org.apache.http.entity.BasicHttpEntity;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.InputStreamEntity;
import org.apache.http.message.BasicHeader;
import org.junit.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.zafarkhaja.semver.Version;
import com.mirth.connect.model.notification.Notification;

public class ConnectServiceUtilTest {
    private static final String FILE_POPULATED = "notifications-populated.json";
    private static final String FILE_EMPTY = "notifications-empty.json";

    /**
     * Creates a streamable entity from the given file.
     * 
     * @param filename
     * @return
     * @throws Exception if the file was not found
     */
    private InputStreamEntity createEntityFromFile(String filename) throws Exception {
        InputStream is = getClass().getClassLoader().getResourceAsStream(filename);

        if(is == null) {
            throw new Exception("Failed to find resource: " + filename);
        }
        
        return new InputStreamEntity(is);
    }

    @Test
    public void test_jsonStreamPopulated() throws Exception {
        HttpEntity entity = createEntityFromFile(FILE_POPULATED);
        Stream<JsonNode> stream = ConnectServiceUtil.toJsonStream(entity);
        assertEquals("Expected all 25 elements to be present", 25L, stream.count());
    }

    @Test
    public void test_jsonStreamEmpty() throws Exception {
        HttpEntity entity = createEntityFromFile(FILE_EMPTY);
        Stream<JsonNode> stream = ConnectServiceUtil.toJsonStream(entity);
        assertEquals("Expected no elements in stream", 0L, stream.count());
    }

    @Test
    public void test_getResponseContent() throws Exception {
        String expectedContent = "this is my content";
        BasicHttpEntity entity = new BasicHttpEntity();
        entity.setContentType(new BasicHeader("Content-Type", "text/plain; charset = UTF-8"));
        entity.setContent(new ByteArrayInputStream(expectedContent.getBytes(StandardCharsets.UTF_8)));
        String content = ConnectServiceUtil.getResponseContent(entity);
        assertEquals(expectedContent, content);
    }

    @Test
    public void test_getCharsetWhenMissing() throws Exception {
        HttpEntity entity = createEntityFromFile(FILE_POPULATED);
        Charset charset = ConnectServiceUtil.getCharset(entity);
        assertEquals(ContentType.TEXT_PLAIN.getCharset().name(), charset.name());
    }

    @Test
    public void test_getCharsetWhenSet() throws Exception {
        //inspired by https://github.com/apache/httpcomponents-core/blob/4.4.x/httpcore/src/test/java/org/apache/http/entity/TestContentType.java#L173
        BasicHttpEntity entity = new BasicHttpEntity();
        String expectedCharset = "UTF-8";
        entity.setContentType(new BasicHeader("Content-Type", "application/json; charset = " + expectedCharset));
        Charset charset = ConnectServiceUtil.getCharset(entity);
        assertEquals(expectedCharset, charset.name());
    }

    @Test
    public void test_toVersionThreeDigits() throws Exception {
        int[] expected = { 4, 1, 5};
        int[] parsedVersion = ConnectServiceUtil.toVersionArray("4.1.5");
        assertArrayEquals(expected, parsedVersion);
    }

    @Test
    public void test_toVersionFourDigits() throws Exception {
        int[] expected = { 3, 12, 0, 4};
        int[] parsedVersion = ConnectServiceUtil.toVersionArray("3.12.0.4");
        assertArrayEquals(expected, parsedVersion);
    }

    @Test
    public void test_currentIsOlderMajorVersion() throws Exception {
        Version current = Version.parse("3.12.0");
        assertTrue(ConnectServiceUtil.isCurrentOlderThan(current, "4.0.0"));
    }

    @Test
    public void test_currentIsNewerMajorVersion() throws Exception {
        Version current = Version.parse("4.0.0");
        assertFalse(ConnectServiceUtil.isCurrentOlderThan(current, "3.12.0"));
    }

    @Test
    public void test_currentIsSameVersion() throws Exception {
        Version current = Version.parse("4.0.0");
        assertFalse(ConnectServiceUtil.isCurrentOlderThan(current, "4.0.0"));
    }

    @Test
    public void test_currentIsOlderMinor() throws Exception {
        Version current = Version.parse("4.0.1");
        assertTrue(ConnectServiceUtil.isCurrentOlderThan(current, "4.1.1"));
    }

    @Test
    public void test_currentIsNewerMinor() throws Exception {
        Version current = Version.parse("4.1.1");
        assertFalse(ConnectServiceUtil.isCurrentOlderThan(current, "4.0.1"));
    }

    @Test
    public void test_currentIsOlderRevision() throws Exception {
        Version current = Version.parse("4.0.0");
        assertTrue(ConnectServiceUtil.isCurrentOlderThan(current, "4.0.1"));
    }

    @Test
    public void test_currentIsNewerRevision() throws Exception {
        Version current = Version.parse("4.0.1");
        assertFalse(ConnectServiceUtil.isCurrentOlderThan(current, "4.0.0"));
    }

    @Test
    public void test_currentIsNotOlderThanInvalidOther() throws Exception {
        Version current = Version.parse("4.0.1");
        assertFalse(ConnectServiceUtil.isCurrentOlderThan(current, "INVALID_VERSION_STRING"));
    }

    @Test
    public void test_noNotificationsForCurrentVersionNewerThanAllOther() throws Exception {
        HttpEntity entity = createEntityFromFile(FILE_POPULATED);
        Version current = Version.parse("4.6.0");
        Stream<JsonNode> notifications = ConnectServiceUtil.filterForNewerVersions(ConnectServiceUtil.toJsonStream(entity), current);
        assertEquals("Expected no notifications given a current version newer than all others", 0L, notifications.count());
    }

    @Test
    public void test_noNotificationsForEmptyComparisonList() throws Exception {
        HttpEntity entity = createEntityFromFile(FILE_EMPTY);
        Version current = Version.parse("1.0.0");
        Stream<JsonNode> notifications = ConnectServiceUtil.filterForNewerVersions(ConnectServiceUtil.toJsonStream(entity), current);
        assertEquals("Expected no notifications given an empty comparison list", 0L, notifications.count());
    }

    @Test
    public void test_noNotificationsWhenMatchingLatestVersion() throws Exception {
        HttpEntity entity = createEntityFromFile(FILE_POPULATED);
        Version current = Version.parse("4.5.2");
        Stream<JsonNode> notifications = ConnectServiceUtil.filterForNewerVersions(ConnectServiceUtil.toJsonStream(entity), current);
        assertEquals("Expected no notifications given a current version matching the latest in other list", 0L, notifications.count());
    }

    @Test
    public void test_notificationsWhenSomeVersionsNewer() throws Exception {
        HttpEntity entity = createEntityFromFile(FILE_POPULATED);
        Version current = Version.parse("4.2.0");
        Stream<JsonNode> notifications = ConnectServiceUtil.filterForNewerVersions(ConnectServiceUtil.toJsonStream(entity), current);
        assertEquals("Expected newer versions to generate notifications", 7L, notifications.count());
    }

    @Test
    public void test_notificationsWhenAllVersionsNewer() throws Exception {
        HttpEntity entity = createEntityFromFile(FILE_POPULATED);
        Version current = Version.parse("1.0.0");
        Stream<JsonNode> notifications = ConnectServiceUtil.filterForNewerVersions(ConnectServiceUtil.toJsonStream(entity), current);
        assertEquals("Expected newer versions to generate notifications", 25L, notifications.count());
    }

    @Test
    public void test_notificationInfo() throws Exception {
        HttpEntity entity = createEntityFromFile(FILE_POPULATED);
        Optional<JsonNode> firstNode = ConnectServiceUtil.toJsonStream(entity).findFirst();

        assertTrue("A node was expected to act upon", firstNode.isPresent());
        
        JsonNode orig = firstNode.get();

        Notification created = ConnectServiceUtil.toNotification(orig);
        assertEquals(orig.get("id").asInt(), (int)created.getId());
        assertEquals(orig.get("name").asText(), created.getName());
        assertEquals(orig.get("published_at").asText(), created.getDate());
        assertNotNull(created.getContent());
    }
}
