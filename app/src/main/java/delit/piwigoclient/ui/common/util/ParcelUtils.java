package delit.piwigoclient.ui.common.util;

import android.os.Parcel;
import android.os.Parcelable;
import android.util.Log;

import androidx.annotation.NonNull;

import com.crashlytics.android.Crashlytics;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import delit.piwigoclient.util.ClassUtils;

public class ParcelUtils {
    public static <T extends Parcelable> HashSet<T> readHashSet(@NonNull Parcel in, ClassLoader loader) {
        ArrayList<T> store = readValue(in, loader, ArrayList.class);
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
        } else {
            out.writeValue(new ArrayList<>(data));
        }
    }

    public static void writeIntSet(@NonNull Parcel dest, Set<Integer> data) {
        ArrayList<Integer> dataWrapper = null;
        if(data != null) {
            dataWrapper = new ArrayList<>(data);
        }
        writeIntArrayList(dest, dataWrapper);
    }

    public static <T> T readValue(@NonNull Parcel in, ClassLoader loader, Class<T> expectedType) {
        Class<T> valueType = expectedType;
        Object o = in.readValue(loader);
        try {
            if(valueType.isPrimitive()) {
                valueType = ClassUtils.wrap(valueType);
            }
            T val = valueType.cast(o);
            if(val == null && valueType.isPrimitive()) {
                Crashlytics.log(Log.ERROR, "ParcelUtils", "read null, but expected value of type : " + expectedType.getName());
            }
            return val;
        } catch(ClassCastException e) {
            Crashlytics.log(Log.ERROR, "ParcelUtils", "returning null as value of unexpected type : " + o);
            Crashlytics.logException(e);
            return null;
        }
    }

    public static Set<Integer> readIntSet(@NonNull Parcel in, @NonNull Set<Integer> destSet, ClassLoader loader) {
        ArrayList<Integer> dataWrapper = readIntArrayList(in, loader);
        if(dataWrapper != null) {
            destSet.addAll(dataWrapper);
        }
        return destSet;
    }

    public static void writeLongSet(Parcel dest, HashSet<Long> data) {
        ArrayList<Long> dataWrapper = null;
        if(data != null) {
            dataWrapper = new ArrayList<>(data);
        }
        writeLongArrayList(dest, dataWrapper);
    }

    public static HashSet<Long> readLongSet(Parcel in, ClassLoader loader) {
        ArrayList<Long> dataWrapper = readLongArrayList(in, loader);
        HashSet<Long> wrapper = new HashSet<>(dataWrapper.size());
        if(dataWrapper != null) {
            wrapper = new HashSet<>(wrapper.size());
            wrapper.addAll(dataWrapper);
        }
        return wrapper;
    }

    public static ArrayList<Long> readLongArrayList(Parcel in, ClassLoader loader) {
        return readValue(in, loader, ArrayList.class);
    }

    public static void writeLongArrayList(Parcel out, ArrayList<Long> value) {
        out.writeValue(value);
    }

    public static ArrayList<Integer> readIntArrayList(Parcel in, ClassLoader loader) {
        return readValue(in, loader, ArrayList.class);
    }

    public static void writeIntArrayList(Parcel out, ArrayList<Integer> value) {
        out.writeValue(value);
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
        if(entries == null) {
            dest.writeList(null);
        } else {
            dest.writeList(new ArrayList<>(entries));
        }
    }

    public static HashSet<String> readStringSet(Parcel in, ClassLoader loader) {
        List<String> rawData = readValue(in, loader, ArrayList.class);
        HashSet<String> data = null;
        if(rawData != null) {
            data = new HashSet<>(rawData);
        }
        return data;
    }
//
//    public static <T extends Parcelable,S extends Parcelable> void writeMap(Parcel dest, HashMap<T ,ArrayList<S>> data) {
//        if(data == null) {
//            dest.writeBundle(null);
//            return;
//        }
//        Bundle b = new Bundle();
//        ArrayList<Parcelable> keys = new ArrayList<>(data.size());
//        ArrayList<ArrayList<? extends Parcelable>> values = new ArrayList<>(data.size());
//        for(Map.Entry<T,ArrayList<S>> entry : data.entrySet()) {
//            keys.add(entry.getKey());
//            values.add(entry.getValue());
//        }
//        b.putParcelableArrayList("keys", keys);
//        int i = 0;
//        for(ArrayList al : values) {
//            b.putParcelableArrayList("values_"+(i++), al);
//        }
//        dest.writeBundle(b);
//    }
//
//    public static <T extends Parcelable,S extends Parcelable> HashMap<T ,ArrayList<S>> readMap(Parcel in) {
//        Bundle b = in.readBundle();
//        if(b == null) {
//            return null;
//        }
//        ArrayList<T> keys = b.getParcelableArrayList("keys");
//        HashMap<T, ArrayList<S>> retVal = new HashMap<>(keys.size());
//        int i = 0;
//        for(Parcelable key : keys) {
//            retVal.put(keys.get(i), (ArrayList<S>) b.get("values_"+(i++)));
//        }
//        return retVal;
//    }

    public static <S, T> void writeMap(Parcel p, Map<S, T> data) {
        ArrayList<S> keys = null;
        ArrayList<T> values = null;
        if(data != null) {
            keys = new ArrayList<>(data.size());
            values = new ArrayList<>(data.size());
            for (Map.Entry<S, T> entry : data.entrySet()) {
                keys.add(entry.getKey());
                values.add(entry.getValue());
            }
        }
        p.writeValue(keys);
        p.writeValue(values);
    }

    public static <S,T,V extends Map<S, T>> V readMap(Parcel in, V dest, ClassLoader loader) {
        ArrayList<S> keys = readValue(in, loader, ArrayList.class);
        ArrayList<T> values = readValue(in, loader, ArrayList.class);
        if(keys != null && values != null) {
            for(int i = 0; i < values.size(); i++) {
                dest.put(keys.get(i), values.get(i));
            }
        }
        return dest;
    }

    public static <S, T> HashMap<S,T> readMap(Parcel in, ClassLoader loader) {
        HashMap<S,T> map = new HashMap<>(0);
        return readMap(in, map, loader);
    }

    public static Boolean readBoolean(Parcel in) {
        return readValue(in, null, boolean.class);
    }

    public static void writeBoolean(Parcel out, Boolean value) {
        out.writeValue(value);
    }
}
