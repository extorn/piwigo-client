package delit.libs.util.security;

import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;

import delit.libs.ui.util.ParcelUtils;

public class X509LoadOperation implements Parcelable {
    private final Uri fileUri;

    public X509LoadOperation(@NonNull Uri fileUri) {
        this.fileUri = fileUri;
    }

    protected X509LoadOperation(Parcel in) {
        fileUri = ParcelUtils.readParcelable(in, Uri.class);
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeValue(fileUri);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Creator<X509LoadOperation> CREATOR = new Creator<X509LoadOperation>() {
        @Override
        public X509LoadOperation createFromParcel(Parcel in) {
            return new X509LoadOperation(in);
        }

        @Override
        public X509LoadOperation[] newArray(int size) {
            return new X509LoadOperation[size];
        }
    };

    public @NonNull Uri getFileUri() {
        return fileUri;
    }
}