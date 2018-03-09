/*
 * Copyright (C) 2014-2018 yvolk (Yuri Volkov), http://yurivolkov.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.andstatus.app.data;

import android.content.ContentResolver;
import android.net.Uri;
import android.support.annotation.NonNull;
import android.text.TextUtils;
import android.webkit.MimeTypeMap;

import org.andstatus.app.util.MyLog;
import org.andstatus.app.util.StringUtils;
import org.andstatus.app.util.UriUtils;

public enum MyContentType {
    IMAGE("image/*", 2),
    TEXT("text/*", 3),
    VIDEO("video/*", 4),
    UNKNOWN("*/*", 0);
    
    private static final String TAG = MyContentType.class.getSimpleName();
    
    private final long code;
    public final String generalMimeType;


    @NonNull
    public static MyContentType fromPathOfSavedFile(String mediaFilePath) {
        return TextUtils.isEmpty(mediaFilePath)
                ? UNKNOWN
                : fromUri(DownloadType.UNKNOWN, null, Uri.parse(mediaFilePath), UNKNOWN.generalMimeType);
    }

    public static MyContentType fromUri(DownloadType downloadType, ContentResolver contentResolver, Uri uri,
                                        String defaultMimeType) {
        if (downloadType == DownloadType.AVATAR) return MyContentType.IMAGE;

        String mimeType = uri2MimeType(contentResolver, uri, defaultMimeType);
        if (mimeType.startsWith("image")) {
            return IMAGE;
        } else if (mimeType.startsWith("video")) {
            return VIDEO;
        } else if (mimeType.startsWith("text")) {
            return TEXT;
        } else {
            return UNKNOWN;
        }
    }

    /** Returns the enum or UNKNOWN */
    @NonNull
    public static MyContentType load(String strCode) {
        try {
            return load(Long.parseLong(strCode));
        } catch (NumberFormatException e) {
            MyLog.v(TAG, "Error converting '" + strCode + "'", e);
        }
        return UNKNOWN;
    }
    
    public static MyContentType load( long code) {
        for(MyContentType val : values()) {
            if (val.code == code) {
                return val;
            }
        }
        return UNKNOWN;
    }

    MyContentType(String generalMimeType, long code) {
        this.generalMimeType = generalMimeType;
        this.code = code;
    }

    public String save() {
        return Long.toString(code);
    }

    @NonNull
    public static String uri2MimeType(ContentResolver contentResolver, Uri uri) {
        return uri2MimeType(contentResolver, uri, "");
    }

    @NonNull
    public static String uri2MimeType(ContentResolver contentResolver, Uri uri, String defaultValue) {
        if (contentResolver != null && !UriUtils.isEmpty(uri)) {
            String mimeType = contentResolver.getType(uri);
            if (StringUtils.nonEmpty(mimeType)) return mimeType;
        }
        return path2MimeType(
                uri == null ? null : uri.getPath(),
                TextUtils.isEmpty(defaultValue) ? UNKNOWN.generalMimeType : defaultValue
        );
    }

    /** @return "bin" if no better extension found */
    @NonNull
    public static String mimeToFileExtension(String mimeType) {
        String extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType);
        return TextUtils.isEmpty(mimeType) ? "bin" : extension;
    }

    @NonNull
    private static String path2MimeType(String path, @NonNull String defaultValue) {
        if (TextUtils.isEmpty(path)) return defaultValue;
        String mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(MimeTypeMap.getFileExtensionFromUrl(path));
        return TextUtils.isEmpty(mimeType) ? defaultValue : mimeType;
    }
}
