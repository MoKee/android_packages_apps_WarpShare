package org.mokee.warpshare;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;

import java.io.FileNotFoundException;
import java.io.InputStream;

public class ResolvedUri {

    private final Context mContext;

    public final Uri uri;

    private boolean mOk = false;

    private String mName;
    private String mPath;

    ResolvedUri(Context context, Uri uri) {
        mContext = context;
        this.uri = uri;

        if (uri == null) {
            return;
        }

        Cursor cursor = context.getContentResolver().query(
                uri, null, null, null, null);

        if (cursor == null) {
            return;
        }

        final int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);

        cursor.moveToFirst();

        mName = cursor.getString(nameIndex);
        mPath = "./" + mName;

        cursor.close();

        mOk = true;
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

    public InputStream stream() throws FileNotFoundException {
        return mContext.getContentResolver().openInputStream(uri);
    }

}
