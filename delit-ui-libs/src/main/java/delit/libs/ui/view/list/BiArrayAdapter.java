package delit.libs.ui.view.list;

import android.content.Context;
import android.widget.ArrayAdapter;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import delit.libs.util.ArrayUtils;
import delit.libs.util.CollectionUtils;


public class BiArrayAdapter<T> extends ArrayAdapter<T> {
    private final List<Long> objectIds;

    public BiArrayAdapter(@NonNull Context context, @NonNull T[] objects, @NonNull long[] objectIds) {
        this(context, android.R.layout.simple_spinner_item, 0, objects, objectIds);
    }

    public BiArrayAdapter(@NonNull Context context, int resource, @NonNull T[] objects, @NonNull long[] objectIds) {
        this(context, resource, 0, objects, objectIds);
    }

    public BiArrayAdapter(@NonNull Context context, int resource, int textViewResourceId, @NonNull T[] objects, @NonNull long[] objectIds) {
        super(context, resource, textViewResourceId, CollectionUtils.toArrayList(objects));
        this.objectIds = new ArrayList<>(objectIds.length);
        this.objectIds.addAll(ArrayUtils.toList(objectIds));
    }

    public BiArrayAdapter(@NonNull Context context, int resource, @NonNull List<T> objects, @NonNull List<Long> objectIds) {
        this(context, resource, 0, objects, objectIds);
    }

    public BiArrayAdapter(@NonNull Context context, int resource, int textViewResourceId, @NonNull List<T> objects, @NonNull List<Long> objectIds) {
        super(context, resource, textViewResourceId, objects);
        this.objectIds = objectIds;
    }

    @Override
    public long getItemId(int position) {
        return objectIds.get(position);
    }

    public T getItemById(long id) {
        int idx = objectIds.indexOf(id);
        if (idx < 0) {
            throw new IllegalStateException("item not found");
        }
        return getItem(idx);
    }

    public int getPosition(long id) {
        return objectIds.indexOf(id);
    }

    @Override
    public void remove(@Nullable T object) {
        int positionToRemove = getPosition(object);
        if (positionToRemove >= 0) {
            objectIds.remove(positionToRemove);
        }
        super.remove(object);
    }
}
