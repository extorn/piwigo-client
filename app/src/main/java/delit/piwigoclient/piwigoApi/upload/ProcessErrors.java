package delit.piwigoclient.piwigoApi.upload;

import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;

import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import delit.libs.ui.util.ParcelUtils;

public class ProcessErrors implements Parcelable {
    private final LinkedHashMap<Date,String> errorsRecorded = new LinkedHashMap<>();

    protected ProcessErrors() {}

    protected ProcessErrors(Parcel in) {
        ParcelUtils.readMap(in, errorsRecorded);
    }

    public void addError(@NonNull Date time, @NonNull String error) {
        synchronized (errorsRecorded) {
            errorsRecorded.put(time, error);
        }
    }

    public boolean isEmpty() {
        return errorsRecorded.isEmpty();
    }

    /**
     * @return A copy of the current state.
     */
    public Set<Map.Entry<Date, String>> getEntrySet() {
        synchronized (errorsRecorded) {
            return new LinkedHashMap<>(errorsRecorded).entrySet();
        }
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        synchronized (errorsRecorded) {
            ParcelUtils.writeMap(dest, errorsRecorded);
        }
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Creator<ProcessErrors> CREATOR = new Creator<ProcessErrors>() {
        @Override
        public ProcessErrors createFromParcel(Parcel in) {
            return new ProcessErrors(in);
        }

        @Override
        public ProcessErrors[] newArray(int size) {
            return new ProcessErrors[size];
        }
    };

    public void clear() {
        errorsRecorded.clear();
    }
}
