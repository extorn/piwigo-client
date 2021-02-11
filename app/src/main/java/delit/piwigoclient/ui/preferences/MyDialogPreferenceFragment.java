package delit.piwigoclient.ui.preferences;

import android.content.SharedPreferences;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;
import androidx.preference.PreferenceDialogFragmentCompat;
import androidx.preference.PreferenceManager;

import delit.piwigoclient.ui.common.DialogFragmentUIHelper;

/**
 * Created by gareth on 26/05/17.
 */

public abstract class MyDialogPreferenceFragment<F extends MyDialogPreferenceFragment<F,FUIH>, FUIH extends DialogFragmentUIHelper<FUIH,F>> extends PreferenceDialogFragmentCompat {

    private FUIH uiHelper;

    @Override
    protected void onBindDialogView(View view) {
        super.onBindDialogView(view);
        setupUiHelper(view);
    }

    protected void setupUiHelper(@NonNull View view) {
        // need to create or lock to this context because we're binding it to this view.
        if(uiHelper == null) {
            uiHelper = (FUIH) new DialogFragmentUIHelper(this, view, getSharedPreferences(), requireContext());
        } else {
            uiHelper.swapToNewContext(requireContext());
        }
    }

    protected FUIH getUIHelper() {
        return (FUIH) uiHelper;
    }

    protected SharedPreferences getSharedPreferences() {
        return PreferenceManager.getDefaultSharedPreferences(requireContext().getApplicationContext());
    }

    protected final <T extends ViewModel> T obtainActivityViewModel(@NonNull FragmentActivity activity, @NonNull Class<T> modelClass) {
        ViewModelProvider.AndroidViewModelFactory factory = ViewModelProvider.AndroidViewModelFactory.getInstance(activity.getApplication());
        return new ViewModelProvider(activity, factory).get(modelClass);
    }

    protected final <T extends ViewModel> T obtainActivityViewModel(@NonNull FragmentActivity activity, String key, @NonNull Class<T> modelClass) {
        ViewModelProvider.AndroidViewModelFactory factory = ViewModelProvider.AndroidViewModelFactory.getInstance(activity.getApplication());
        return new ViewModelProvider(activity, factory).get(key, modelClass);
    }

    protected final <T extends ViewModel> T obtainFragmentViewModel(@NonNull Fragment fragment, @NonNull Class<T> modelClass) {
        ViewModelProvider.AndroidViewModelFactory factory = ViewModelProvider.AndroidViewModelFactory.getInstance(fragment.requireActivity().getApplication());
        return new ViewModelProvider(fragment, factory).get(modelClass);
    }
}
