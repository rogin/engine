package com.mirth.connect.client.core;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import org.apache.http.entity.BasicHttpEntity;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.InputStreamEntity;
import org.apache.http.message.BasicHeader;
import org.junit.Test;

public class ConnectServiceUtilTest {
    private static final String FILE_POPULATED = "notifications-populated.json";
    private static final String FILE_EMPTY = "notifications-empty.json";

    private InputStreamEntity createEntityFromFile(String filename) {
        var is = getClass().getClassLoader().getResourceAsStream(filename);
        
        return new InputStreamEntity(is);
    }

    @Test
    public void test_jsonStreamPopulated() throws Exception {
        var entity = createEntityFromFile(FILE_POPULATED);
        var stream = ConnectServiceUtil.toJsonStream(entity);
        assertEquals("Expected all 25 elements to be present", 25L, stream.count());
    }

    @Test
    public void test_jsonStreamEmpty() throws Exception {
        var entity = createEntityFromFile(FILE_EMPTY);
        var stream = ConnectServiceUtil.toJsonStream(entity);
        assertEquals("Expected no elements in stream", 0L, stream.count());
    }

    @Test
    public void test_getResponseContent() throws Exception {
        var expectedContent = "this is my content";
        var entity = new BasicHttpEntity();
        entity.setContentType(new BasicHeader("Content-Type", "text/plain; charset = UTF-8"));
        entity.setContent(new ByteArrayInputStream(expectedContent.getBytes(StandardCharsets.UTF_8)));
        var content = ConnectServiceUtil.getResponseContent(entity);
        assertEquals(expectedContent, content);
    }

    @Test
    public void test_getResponseContentFromFile() throws Exception {
        var entity = createEntityFromFile(FILE_POPULATED);
        var content = ConnectServiceUtil.getResponseContent(entity);
        assertNotNull(content);
    }

    @Test
    public void test_getCharsetWhenMissing() throws Exception {
        var entity = createEntityFromFile(FILE_POPULATED);
        var charset = ConnectServiceUtil.getCharset(entity);
        assertEquals(ContentType.TEXT_PLAIN.getCharset().name(), charset.name());
    }

    @Test
    public void test_getCharsetWhenSet() throws Exception {
        //inspired by https://github.com/apache/httpcomponents-core/blob/4.4.x/httpcore/src/test/java/org/apache/http/entity/TestContentType.java#L173
        var entity = new BasicHttpEntity();
        var expectedCharset = "UTF-8";
        entity.setContentType(new BasicHeader("Content-Type", "application/json; charset = " + expectedCharset));
        var charset = ConnectServiceUtil.getCharset(entity);
        assertEquals(expectedCharset, charset.name());
    }

    @Test
    public void test_toVersionThreeDigits() throws Exception {
        int[] expected = { 4, 1, 5};
        var parsedVersion = ConnectServiceUtil.toVersionArray("4.1.5");
        assertArrayEquals(expected, parsedVersion);
    }

    @Test
    public void test_toVersionFourDigits() throws Exception {
        int[] expected = { 3, 12, 0, 4};
        var parsedVersion = ConnectServiceUtil.toVersionArray("3.12.0.4");
        assertArrayEquals(expected, parsedVersion);
    }

    @Test
    public void test_currentIsOlderMajorVersion() throws Exception {
        var current = ConnectServiceUtil.toVersionArray("3.12.0");
        assertTrue(ConnectServiceUtil.isCurrentOlderThan(current, "4.0.0"));
    }

    @Test
    public void test_currentIsNewerMajorVersion() throws Exception {
        var current = ConnectServiceUtil.toVersionArray("4.0.0");
        assertFalse(ConnectServiceUtil.isCurrentOlderThan(current, "3.12.0"));
    }

    @Test
    public void test_currentIsSameVersion() throws Exception {
        var current = ConnectServiceUtil.toVersionArray("4.0.0");
        assertFalse(ConnectServiceUtil.isCurrentOlderThan(current, "4.0.0"));
    }

    @Test
    public void test_currentIsOlderMinor() throws Exception {
        var current = ConnectServiceUtil.toVersionArray("4.0.1");
        assertTrue(ConnectServiceUtil.isCurrentOlderThan(current, "4.1.1"));
    }

    @Test
    public void test_currentIsNewerMinor() throws Exception {
        var current = ConnectServiceUtil.toVersionArray("4.1.1");
        assertFalse(ConnectServiceUtil.isCurrentOlderThan(current, "4.0.1"));
    }

    @Test
    public void test_currentIsOlderRevision() throws Exception {
        var current = ConnectServiceUtil.toVersionArray("4.0.0");
        assertTrue(ConnectServiceUtil.isCurrentOlderThan(current, "4.0.1"));
    }

    @Test
    public void test_currentIsNewerRevision() throws Exception {
        var current = ConnectServiceUtil.toVersionArray("4.0.1");
        assertFalse(ConnectServiceUtil.isCurrentOlderThan(current, "4.0.0"));
    }

    @Test
    public void test_currentIsNewerPatch() throws Exception {
        var current = ConnectServiceUtil.toVersionArray("4.0.0.1");
        assertFalse(ConnectServiceUtil.isCurrentOlderThan(current, "4.0.0"));
    }

    @Test
    public void test_currentIsOlderPatch() throws Exception {
        var current = ConnectServiceUtil.toVersionArray("4.0.1");
        assertTrue(ConnectServiceUtil.isCurrentOlderThan(current, "4.0.1.1"));
    }

    @Test
    public void test_currentIsOlderPatchAgain() throws Exception {
        var current = ConnectServiceUtil.toVersionArray("4.0.1");
        assertTrue(ConnectServiceUtil.isCurrentOlderThan(current, "4.0.1.9"));
    }

    @Test
    public void test_noNotificationsForCurrentVersionNewerThanAllOther() throws Exception {
        var entity = createEntityFromFile(FILE_POPULATED);
        var notifications = ConnectServiceUtil.filterForNewerVersions(ConnectServiceUtil.toJsonStream(entity), "4.6.0");
        assertEquals("Expected no notifications given a current version newer than all others", 0L, notifications.count());
    }

    @Test
    public void test_noNotificationsForEmptyComparisonList() throws Exception {
        var entity = createEntityFromFile(FILE_EMPTY);
        var notifications = ConnectServiceUtil.filterForNewerVersions(ConnectServiceUtil.toJsonStream(entity), "1.0.0");
        assertEquals("Expected no notifications given an empty comparison list", 0L, notifications.count());
    }

    @Test
    public void test_noNotificationsWhenMatchingLatestVersion() throws Exception {
        var entity = createEntityFromFile(FILE_POPULATED);
        var notifications = ConnectServiceUtil.filterForNewerVersions(ConnectServiceUtil.toJsonStream(entity), "4.5.2");
        assertEquals("Expected no notifications given a current version matching the latest in other list", 0L, notifications.count());
    }

    @Test
    public void test_notificationsWhenSomeVersionsNewer() throws Exception {
        var entity = createEntityFromFile(FILE_POPULATED);
        var notifications = ConnectServiceUtil.filterForNewerVersions(ConnectServiceUtil.toJsonStream(entity), "4.2.0");
        assertEquals("Expected newer versions to generate notifications", 7L, notifications.count());
    }

    @Test
    public void test_notificationsWhenAllVersionsNewer() throws Exception {
        var entity = createEntityFromFile(FILE_POPULATED);
        var notifications = ConnectServiceUtil.filterForNewerVersions(ConnectServiceUtil.toJsonStream(entity), "1.0.0");
        assertEquals("Expected newer versions to generate notifications", 25L, notifications.count());
    }

    @Test
    public void test_notificationInfo() throws Exception {
        var entity = createEntityFromFile(FILE_POPULATED);
        var firstNode = ConnectServiceUtil.toJsonStream(entity).findFirst();

        assertTrue("A node was expected to act upon", firstNode.isPresent());
        
        var orig = firstNode.get();

        var created = ConnectServiceUtil.toNotification(orig);
        assertEquals(orig.get("id").asInt(), (int)created.getId());
        assertEquals(orig.get("name").asText(), created.getName());
        assertEquals(orig.get("published_at").asText(), created.getDate());
        assertNotNull(created.getContent());
    }
}
