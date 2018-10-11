package delit.piwigoclient.ui.common.util;

import android.os.Parcel;
import android.os.Parcelable;
import android.support.annotation.NonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ParcelUtils {
    public static <T extends Parcelable> HashSet<T> readHashSet(@NonNull Parcel in) {
        ArrayList<T> store = (ArrayList<T>) in.readValue(null);
        if(store != null) {
            HashSet retVal = new HashSet<T>(store);
            retVal.addAll(store);
            return retVal;
        }
        return null;
    }

    public static void writeSet(@NonNull Parcel out, HashSet<? extends Parcelable> data) {
        if(data == null) {
            out.writeValue(null);
        }
        out.writeValue(new ArrayList<>(data));
    }

    public static void writeIntSet(@NonNull Parcel dest, Set<Integer> data) {
        int[] rawData = null;
        if(data != null) {
            ArrayList dataWrapper = new ArrayList<>(data);
            rawData= com.google.android.gms.common.util.ArrayUtils.toPrimitiveArray(dataWrapper);
        }
        dest.writeIntArray(rawData);
    }
    
    public static HashSet<Integer> readIntHashSet(@NonNull Parcel in) {
        int[] data = (int[]) in.readValue(null);
        HashSet<Integer> wrapper = null;
        if(data != null) {
            Collections.addAll(wrapper, com.google.android.gms.common.util.ArrayUtils.toWrapperArray(data));
        }
        return wrapper;
    }

    public static Set<Integer> readIntSet(@NonNull Parcel in, @NonNull Set<Integer> destSet) {
        int[] data = (int[]) in.readValue(null);
        if(destSet == null) {
            throw new IllegalArgumentException("destination set may not be null");
        }
        if(data != null) {
            Collections.addAll(destSet, com.google.android.gms.common.util.ArrayUtils.toWrapperArray(data));
        }
        return destSet;
    }

    public static void writeLongSet(Parcel dest, HashSet<Long> data) {
        long[] rawData = null;
        if(data != null) {
            ArrayList dataWrapper = new ArrayList<>(data);
            rawData= com.google.android.gms.common.util.ArrayUtils.toLongArray(dataWrapper);
        }
        dest.writeLongArray(rawData);
    }

    public static HashSet<Long> readLongSet(Parcel in) {
        long[] data = (long[]) in.readValue(null);
        HashSet<Long> wrapper = null;
        if(data != null) {
            Collections.addAll(wrapper, com.google.android.gms.common.util.ArrayUtils.toWrapperArray(data));
        }
        return wrapper;
    }


    public static Set<Long> readlongSet(@NonNull Parcel in, @NonNull Set<Long> destSet) {
        long[] data = (long[]) in.readValue(null);
        if(destSet == null) {
            throw new IllegalArgumentException("destination set may not be null");
        }
        if(data != null) {
            Collections.addAll(destSet, com.google.android.gms.common.util.ArrayUtils.toWrapperArray(data));
        }
        return destSet;
    }

    public static ArrayList<Long> readLongArrayList(Parcel in) {
        ArrayList<Long> data = null;
        Long[] rawData = (Long[]) in.readValue(null);
        if(rawData != null) {
            data = new ArrayList<>();
            Collections.addAll(data, rawData);
        }
        return data;
    }

    public static void writeLongArrayList(Parcel out, ArrayList<Long> parentageChain) {
        if(parentageChain != null) {
            out.writeArray(parentageChain.toArray(new Long[parentageChain.size()]));
        } else {
            out.writeArray(null);
        }
    }

    public static Date readDate(Parcel in) {
        Date date = null;
        long time = in.readLong();
        if(time != Long.MIN_VALUE) {
            date = new Date(time);
        }
        return date;
    }

    public static void writeDate(Parcel out, Date data) {
        out.writeLong(data==null?Long.MIN_VALUE:data.getTime());
    }

    public static void writeStringSet(Parcel dest, HashSet<String> entries) {
        dest.writeList(new ArrayList<>(entries));
    }

    public static HashSet<String> readStringSet(Parcel source) {
        List<String> rawData = (List<String>) source.readValue(null);
        HashSet<String> data = null;
        if(rawData != null) {
            data = new HashSet<>(rawData);
        }
        return data;
    }
}
