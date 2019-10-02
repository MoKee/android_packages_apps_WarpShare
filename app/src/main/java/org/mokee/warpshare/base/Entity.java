/*
 * Copyright (C) 2019 The MoKee Open Source Project
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

package org.mokee.warpshare.base;

import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.text.TextUtils;
import android.util.Log;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.concurrent.ExecutionException;

import static android.content.ContentResolver.SCHEME_FILE;
import static androidx.core.content.FileProvider.getUriForFile;

@SuppressWarnings("WeakerAccess")
public class Entity {

    private static final String TAG = "Entity";

    public final Uri uri;

    private final Context mContext;

    private boolean mOk = false;

    private String mName;
    private String mPath;
    private String mType;
    private long mSize;

    public Entity(Context context, Uri uri, String type) {
        mContext = context;

        if (uri == null) {
            this.uri = null;
            return;
        }

        if (SCHEME_FILE.equals(uri.getScheme())) {
            uri = generateContentUri(context, uri);
            if (uri == null) {
                this.uri = null;
                return;
            }
        }

        this.uri = uri;

        Cursor cursor;

        try {
            cursor = context.getContentResolver().query(
                    uri, null, null, null, null);
        } catch (SecurityException e) {
            Log.e(TAG, "Failed resolving uri: " + uri, e);
            return;
        }

        if (cursor == null) {
            return;
        }

        final int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
        final int sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE);

        cursor.moveToFirst();

        mName = cursor.getString(nameIndex);
        mPath = "./" + mName;
        mType = type;
        mSize = cursor.isNull(sizeIndex) ? -1 : cursor.getLong(sizeIndex);

        cursor.close();

        if (TextUtils.isEmpty(mType)) {
            mType = context.getContentResolver().getType(uri);
        }

        mOk = true;
    }

    private Uri generateContentUri(Context context, Uri uri) {
        final String path = uri.getPath();
        if (TextUtils.isEmpty(path)) {
            Log.e(TAG, "Empty uri path: " + uri);
            return null;
        }

        final File file = new File(path);
        if (!file.exists()) {
            Log.e(TAG, "File not exists: " + uri);
            return null;
        }

        return getUriForFile(context, "org.mokee.warpshare.files", file);
    }

    public boolean ok() {
        return mOk;
    }

    public String name() {
        return mName;
    }

    public String path() {
        return mPath;
    }

    public String type() {
        return mType;
    }

    public long size() {
        return mSize;
    }

    public InputStream stream() throws FileNotFoundException {
        return mContext.getContentResolver().openInputStream(uri);
    }

    public Bitmap thumbnail(int size) {
        try {
            return Glide.with(mContext)
                    .asBitmap()
                    .load(uri)
                    .centerCrop()
                    .diskCacheStrategy(DiskCacheStrategy.NONE)
                    .submit(size, size)
                    .get();
        } catch (ExecutionException | InterruptedException e) {
            Log.e(TAG, "Failed generating thumbnail", e);
            return null;
        }
    }

}
