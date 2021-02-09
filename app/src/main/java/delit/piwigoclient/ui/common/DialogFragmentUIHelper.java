package delit.piwigoclient.ui.common;

import android.content.Context;
import android.content.SharedPreferences;
import android.view.View;

import androidx.preference.PreferenceDialogFragmentCompat;

public class DialogFragmentUIHelper<FUI extends FragmentUIHelper<FUI,T>, T extends PreferenceDialogFragmentCompat> extends UIHelper<FUI,T> {
    private final View parentView;

    public DialogFragmentUIHelper(T parent, View view, SharedPreferences prefs, Context context) {
        super(parent, prefs, context);
        this.parentView = view;
    }

    @Override
    protected View getParentView() {
        return parentView;
    }

    @Override
    protected boolean canShowDialog() {
        //Note this null test is needed because the parentView isn't set during the constructor call to setupDialogs
        return parentView != null && parentView.isAttachedToWindow();
    }
}
