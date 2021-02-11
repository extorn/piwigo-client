package delit.libs.ui.view.preference;

import android.content.Context;
import android.util.AttributeSet;

import androidx.activity.ComponentActivity;
import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;
import androidx.preference.DialogPreference;

import delit.libs.ui.util.DisplayUtils;

public abstract class MyDialogPreference extends DialogPreference {
    public MyDialogPreference(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    public MyDialogPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public MyDialogPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public MyDialogPreference(Context context) {
        super(context);
    }

    protected final <T extends ViewModel> T obtainActivityViewModel(@NonNull Context context, @NonNull Class<T> modelClass) {
        ComponentActivity activity = (ComponentActivity) DisplayUtils.getActivity(context);
        ViewModelProvider.AndroidViewModelFactory factory = ViewModelProvider.AndroidViewModelFactory.getInstance(activity.getApplication());
        return new ViewModelProvider(activity, factory).get(modelClass);
    }

    protected final <T extends ViewModel> T obtainActivityViewModel(@NonNull Context context, String key, @NonNull Class<T> modelClass) {
        ComponentActivity activity = (ComponentActivity) DisplayUtils.getActivity(context);
        ViewModelProvider.AndroidViewModelFactory factory = ViewModelProvider.AndroidViewModelFactory.getInstance(activity.getApplication());
        return new ViewModelProvider(activity, factory).get(key, modelClass);
    }
}
