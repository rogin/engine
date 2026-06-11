// SPDX-License-Identifier: MPL-2.0
// SPDX-FileCopyrightText: Mirth Corporation
// SPDX-FileCopyrightText: 2026 Tony Germano <tony@germano.name>

package com.mirth.connect.model.filters.elements;

import java.io.Serializable;
import java.util.List;

import org.apache.commons.lang3.builder.ToStringBuilder;

import com.mirth.connect.model.ContentSearchElementToStringStyle;

public class ContentSearchElement implements Serializable {

    private int contentCode;
    private List<String> searches;

    public ContentSearchElement(int contentCode, List<String> searches) {
        this.contentCode = contentCode;
        this.searches = searches;
    }

    public int getContentCode() {
        return contentCode;
    }

    public void setContentCode(int contentCode) {
        this.contentCode = contentCode;
    }

    public List<String> getSearches() {
        return searches;
    }

    public void setSearches(List<String> searches) {
        this.searches = searches;
    }

    @Override
    public String toString() {
        return ToStringBuilder.reflectionToString(this, ContentSearchElementToStringStyle.instance());
    }
}
