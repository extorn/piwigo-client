package delit.libs.ui;

import androidx.annotation.NonNull;

import java.lang.ref.WeakReference;
import java.util.Objects;

public abstract class OwnedSafeAsyncTask<Owner, Params, Progress, Result> extends SafeAsyncTask<Params, Progress, Result> {

    private static final String TAG = "OwnedSafeAsyncTask";
    private final WeakReference<Owner> ownerRef;

    public OwnedSafeAsyncTask(@NonNull Owner owner) {
        ownerRef = new WeakReference<>(owner);
    }

    public Owner getOwner() {
        return Objects.requireNonNull(ownerRef.get());
    }

}
