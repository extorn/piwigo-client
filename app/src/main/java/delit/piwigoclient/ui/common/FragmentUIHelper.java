package delit.piwigoclient.ui.common;

import android.content.Context;
import android.content.SharedPreferences;
import android.view.View;

import androidx.core.view.ViewCompat;
import androidx.fragment.app.Fragment;

/**
 * Created by gareth on 17/10/17.
 */

public class FragmentUIHelper<FUI extends FragmentUIHelper<FUI,T>, T extends Fragment> extends UIHelper<FUI,T> {

    private boolean blockDialogsFromShowing = false;


    public FragmentUIHelper(T parent, SharedPreferences prefs, Context context, View attachedView) {
        super(parent, prefs, context, attachedView);
    }

    @Override
    protected View getParentView() {
        T parent = getParent();
        if(parent == null) {
            return null;
        }
        return parent.getView();
    }

    @Override
    protected boolean canShowDialog() {
        boolean canShowDialog = super.canShowDialog();
        if(canShowDialog) {
            Fragment f = getParent();
            if(f != null) {
                MyActivity<?,?> activity = (MyActivity<?,?>) f.getActivity();
                if(activity != null) {
                    ActivityUIHelper<?,?> activityUiHelper = activity.getUiHelper();
                    canShowDialog = getParentView() != null && ViewCompat.isAttachedToWindow(getParentView());
                    canShowDialog &= !activityUiHelper.isDialogShowing();
                    canShowDialog &= !activity.isFinishing();
                    canShowDialog &= (!blockDialogsFromShowing) && getParent().isResumed();
                }
            }
        }
        return canShowDialog;
    }

    public void setBlockDialogsFromShowing(boolean blockDialogsFromShowing) {
        this.blockDialogsFromShowing = blockDialogsFromShowing;
    }
}
