package delit.libs.ui.view.list;

import android.content.Context;
import android.text.Editable;
import android.widget.ArrayAdapter;

import androidx.annotation.IdRes;
import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;


public class MappedArrayAdapter<T, S> extends ArrayAdapter<T> {
    private final List<S> objectValues;

    public MappedArrayAdapter(@NonNull Context context, @LayoutRes int resource, @NonNull T[] objects, @NonNull S[] objectIds) {
        this(context, resource, 0, objects, objectIds);
    }

    public MappedArrayAdapter(@NonNull Context context, @LayoutRes int resource, @IdRes int textViewResourceId, @NonNull T[] objects, @NonNull S[] objectValues) {
        super(context, resource, textViewResourceId, com.google.android.gms.common.util.ArrayUtils.toArrayList(objects));
        this.objectValues = com.google.android.gms.common.util.ArrayUtils.toArrayList(objectValues);
    }

    public MappedArrayAdapter(@NonNull Context context, @LayoutRes int resource, @NonNull List<T> objects, @NonNull List<S> objectValues) {
        this(context, resource, 0, objects, objectValues);
    }

    public MappedArrayAdapter(@NonNull Context context, @LayoutRes int resource, @IdRes int textViewResourceId, @NonNull List<T> objects, @NonNull List<S> objectValues) {
        super(context, resource, textViewResourceId, objects);
        this.objectValues = objectValues;
    }

    public MappedArrayAdapter(@NonNull Context context, @LayoutRes int resource, @NonNull Map<T,S> objects) {
        super(context, resource, new ArrayList<>(objects.keySet()));
        this.objectValues = new ArrayList<>(objects.values());
    }

    public MappedArrayAdapter(@NonNull Context context, @LayoutRes int resource, @IdRes int textViewResourceId, @NonNull Map<T,S> objects) {
        super(context, resource, textViewResourceId, new ArrayList<>(objects.keySet()));
        this.objectValues = new ArrayList<>(objects.values());
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

    @Override
    public void add(@Nullable T object) {
        throw new UnsupportedOperationException("Use add with two args");
    }

    @Override
    public void addAll(T... items) {
        throw new UnsupportedOperationException("Use add with two collection args");
    }

    @Override
    public void addAll(@NonNull Collection<? extends T> collection) {
        throw new UnsupportedOperationException("Use add with two collection args");
    }


    public void add(@Nullable T object, @Nullable S value) {
        objectValues.add(value);
        super.add(object);
    }

    public void addAll(@NonNull Collection<? extends T> collection, @NonNull Collection<? extends S> values) {
        if(collection.size() != values.size()) {
            throw new IllegalArgumentException("Size of items and values collections must be identical");
        }
        objectValues.addAll(values);
        super.addAll(collection);
    }

    public int getPositionByValue(S value) {
        return objectValues.indexOf(value);
    }

    protected List<S> getObjectValues() {
        return objectValues;
    }

    @Override
    public void remove(@Nullable T object) {
        int positionToRemove = getPosition(object);
        if (positionToRemove >= 0) {
            objectValues.remove(positionToRemove);
        }
        super.remove(object);
    }

    public S getItemValueByItem(T item) {
        int itemPos = getPosition(item);
        if(itemPos < 0) {
            return null;
        }
        return getItemValue(itemPos);
    }
}
