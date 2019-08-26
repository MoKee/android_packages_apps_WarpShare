package org.mokee.warpshare;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;

import java.io.FileNotFoundException;
import java.io.InputStream;

public class ResolvedUri {

    private final Context mContext;

    public final boolean ok;

    public final Uri uri;
    public final String name;
    public final String path;

    ResolvedUri(Context context, Uri uri) {
        mContext = context;
        this.uri = uri;

        Cursor cursor = context.getContentResolver().query(
                uri, null, null, null, null);

        if (cursor == null) {
            name = null;
            path = null;
            ok = false;
            return;
        }

        final int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);

        cursor.moveToFirst();

        name = cursor.getString(nameIndex);
        path = "./" + name;

        cursor.close();

        ok = true;
    }

    public InputStream stream() throws FileNotFoundException {
        return mContext.getContentResolver().openInputStream(uri);
    }

}
