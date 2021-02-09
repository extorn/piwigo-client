package delit.piwigoclient.ui.util;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Observer;

import com.drew.lang.annotations.Nullable;

import java.lang.ref.WeakReference;

/**
 * Can watch livedata for as long as you wish.
 * Once it has observed a change, it will stop watching.
 * @param <T>
 */
public abstract class LiveDataTransientObserver<T> implements Observer<T> {
    private final WeakReference<Context> contextRef;
    private final LiveData<T> liveData;

    public LiveDataTransientObserver(@NonNull Context context, @NonNull LiveData<T> liveData) {
        this.contextRef = new WeakReference<>(context);
        this.liveData = liveData;
    }

    public @Nullable
    Context getContext() {
        return contextRef.get();
    }

    @Override
    public final void onChanged(T t) {
        liveData.removeObserver(this);
        onChangeObserved(t);
    }

    protected abstract void onChangeObserved(T t);
}
