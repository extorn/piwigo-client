package delit.piwigoclient.ui.common.list;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import android.widget.ArrayAdapter;

import java.util.List;


public class MappedArrayAdapter<T, S> extends ArrayAdapter<T> {
    private final List<S> objectValues;

    public MappedArrayAdapter(@NonNull Context context, int resource, @NonNull T[] objects, @NonNull S[] objectIds) {
        this(context, resource, 0, objects, objectIds);
    }

    public MappedArrayAdapter(@NonNull Context context, int resource, int textViewResourceId, @NonNull T[] objects, @NonNull S[] objectValues) {
        super(context, resource, textViewResourceId, com.google.android.gms.common.util.ArrayUtils.toArrayList(objects));
        this.objectValues = com.google.android.gms.common.util.ArrayUtils.toArrayList(objectValues);
    }

    public MappedArrayAdapter(@NonNull Context context, int resource, @NonNull List<T> objects, @NonNull List<S> objectValues) {
        this(context, resource, 0, objects, objectValues);
    }

    public MappedArrayAdapter(@NonNull Context context, int resource, int textViewResourceId, @NonNull List<T> objects, @NonNull List<S> objectValues) {
        super(context, resource, textViewResourceId, objects);
        this.objectValues = objectValues;
    }

    public S getItemValue(int position) {
        return objectValues.get(position);
    }

    public T getItemByValue(S value) {
        int idx = objectValues.indexOf(value);
        if (idx < 0) {
            return null;
        }
        return getItem(idx);
    }

    public int getPositionByValue(S value) {
        return objectValues.indexOf(value);
    }

    @Override
    public void remove(@Nullable T object) {
        int positionToRemove = getPosition(object);
        if (positionToRemove >= 0) {
            objectValues.remove(positionToRemove);
        }
        super.remove(object);
    }
}
