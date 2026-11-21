package sy.homs.mobile;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;

import java.io.File;
import java.io.FileNotFoundException;

/** يقدّم صورة البطاقة المؤقتة (cache/share) لتطبيقات المشاركة بلا مكتبات خارجية */
public class ShareProvider extends ContentProvider {
    static final String AUTH = "sy.homs.mobile.share";

    static Uri uri(String name) { return Uri.parse("content://" + AUTH + "/" + name); }

    private File file(Uri u) {
        String n = u.getLastPathSegment() == null ? "" : u.getLastPathSegment().replaceAll("[^A-Za-z0-9._-]", "");
        return new File(new File(getContext().getCacheDir(), "share"), n);
    }

    @Override public boolean onCreate() { return true; }

    @Override public ParcelFileDescriptor openFile(Uri u, String mode) throws FileNotFoundException {
        File f = file(u);
        if (!f.exists()) throw new FileNotFoundException();
        return ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override public String getType(Uri u) { return "image/png"; }

    @Override public Cursor query(Uri u, String[] p, String s, String[] a, String o) {
        File f = file(u);
        MatrixCursor c = new MatrixCursor(new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE});
        c.addRow(new Object[]{f.getName(), f.length()});
        return c;
    }

    @Override public Uri insert(Uri u, ContentValues v) { return null; }
    @Override public int delete(Uri u, String s, String[] a) { return 0; }
    @Override public int update(Uri u, ContentValues v, String s, String[] a) { return 0; }
}
