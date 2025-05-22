package com.mirth.connect.client.core;

import java.util.List;
import java.util.Map;

import com.github.zafarkhaja.semver.Version;
import com.mirth.connect.model.notification.Notification;

public interface NotificationProvider {

    /**
     * Query a resource for release versions, and create notifications for those newer than current.
     * @param serverId
     * @param currentVersion
     * @param extensionVersions
     * @param protocols
     * @param cipherSuites
     * @return a non-null list
     * @throws ClientException
     */
    List<Notification> getNotifications(String serverId, Version currentVersion,
            Map<String, String> extensionVersions, String[] protocols, String[] cipherSuites) throws ClientException;

}
