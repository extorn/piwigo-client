package delit.libs.ui.view.preference;

import android.content.Context;
import android.util.AttributeSet;

import androidx.activity.ComponentActivity;
import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;
import androidx.preference.Preference;

import delit.libs.ui.util.DisplayUtils;

public abstract class MyPreference extends Preference {
    public MyPreference(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    public MyPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public MyPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public MyPreference(Context context) {
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
