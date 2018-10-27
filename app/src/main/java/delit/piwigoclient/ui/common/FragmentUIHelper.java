package delit.piwigoclient.ui.common;

import android.content.Context;
import android.content.SharedPreferences;
import android.support.v4.app.Fragment;

/**
 * Created by gareth on 17/10/17.
 */

public class FragmentUIHelper extends UIHelper<Fragment> {

    private boolean blockDialogsFromShowing = false;


    public FragmentUIHelper(Fragment parent, SharedPreferences prefs, Context context) {
        super(parent, prefs, context);
    }

    @Override
    protected boolean canShowDialog() {
        boolean canShowDialog = super.canShowDialog();
        if(canShowDialog) {
            Fragment f = getParent();
            if(f != null) {
                MyActivity activity = (MyActivity) f.getActivity();
                if(activity != null) {
                    UIHelper activityUiHelper = activity.getUiHelper();
                    canShowDialog = !activityUiHelper.isDialogShowing();
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
