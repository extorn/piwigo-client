package delit.libs.ui.util;

import android.os.Parcel;

import java.io.File;

public class LegacyParcelUtils {
    public static void writeFile(Parcel out, File file) {
        if (file != null) {
            out.writeValue(file.getAbsolutePath());
        } else {
            out.writeValue(null);
        }
    }

    public static File readFile(Parcel in) {
        String value = ParcelUtils.readString(in);
        if (value == null) {
            return null;
        }
        return new File(value);
    }
}
