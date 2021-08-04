/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package com.oracle.plugin.jtreg.util;

import com.intellij.psi.PsiComment;
import com.intellij.psi.tree.java.IJavaElementType;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Simple parser for jtreg tags.
 */
public class JTRegTagParser {

    private static final Pattern TAG_PATTERN = Pattern.compile("@([a-zA-Z]+)(\\s+|$)");

    public static Result parseTags(PsiComment header) {
        String text = header.getText();
        List<Tag> tags = new ArrayList<>();
        int start = -1;
        int end = -1;
        int tagStart = -1;
        int tagEnd = -1;

        text = text.substring(0, text.length() - 2);

        String tagName = null;
        StringBuilder tagText = new StringBuilder();
        int prefix = header.getTokenType() instanceof IJavaElementType ? 2 : 3;
        String[] lines = text.substring(prefix).split("\n");
        int pos = header.getTextOffset() + prefix;

        for (String line : lines) {
            if (line.replaceAll("[*\\s]+", "").isEmpty()) {
                pos += line.length() + 1;
                continue;
            }
            Matcher m = TAG_PATTERN.matcher(line);
            if (m.find()) {
                if (tagName != null) {
                    tags.add(new Tag(start, pos, tagStart, tagEnd, tagName, tagText.toString()));
                    tagText.delete(0, tagText.length());
                }

                tagName = m.group(1);

                start = pos;
                tagStart = pos + m.start();
                tagEnd = pos + m.end(1);
                tagText.append(line.substring(m.end()));
            } else if (tagName != null) {
                int asterisk = line.indexOf('*');
                tagText.append(line.substring(asterisk + 1));
            }

            pos += line.length() + 1;

            if (tagName != null) {
                end = pos;
            }
        }

        if (tagName != null) {
            tags.add(new Tag(start, end, tagStart, tagEnd, tagName, tagText.toString()));
        }

        Map<String, List<Tag>> result = new HashMap<>();

        for (Tag tag : tags) {
            List<Tag> innerTags = result.get(tag.getName());

            if (innerTags == null) {
                result.put(tag.getName(), innerTags = new ArrayList<>());
            }

            innerTags.add(tag);
        }

        return new Result(result);
    }

    /**
     * Class holding parser results.
     */
    public static final class Result {
        private final Map<String, List<Tag>> name2Tag;

        public Result(Map<String, List<Tag>> name2Tag) {
            this.name2Tag = name2Tag;
        }

        public Map<String, List<Tag>> getName2Tag() {
            return name2Tag;
        }

    }
}

