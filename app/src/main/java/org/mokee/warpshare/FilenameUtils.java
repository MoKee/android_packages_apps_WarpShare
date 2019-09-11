package org.mokee.warpshare;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

class FilenameUtils {

    private static final Pattern PATTERN_FILENAME = Pattern.compile("[^/]+\\.([^./]+)$");

    static String extname(String path) {
        final Matcher matcher = PATTERN_FILENAME.matcher(path);
        if (matcher.matches()) {
            return matcher.group(1);
        } else {
            return null;
        }
    }



}
