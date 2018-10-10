package delit.piwigoclient.ui.common;

import android.content.SharedPreferences;
import android.support.v4.app.Fragment;
import android.support.v7.app.AppCompatActivity;

import delit.piwigoclient.ui.common.fragment.MyFragment;

/**
 * Created by gareth on 17/10/17.
 */

public class ActivityUIHelper extends UIHelper<MyActivity> {
    public ActivityUIHelper(MyActivity parent, SharedPreferences prefs) {
        super(parent, prefs, parent);
    }

    @Override
    protected boolean canShowDialog() {
        return super.canShowDialog() && !getParent().isFinishing();
    }

    @Override
    protected DismissListener buildDialogDismissListener() {
        return new CustomDismissListener();
    }

    class CustomDismissListener extends DismissListener {
        @Override
        protected void onNoDialogToShow() {
            Fragment f = getParent().getActiveFragment();
            if(f instanceof MyFragment) {
                UIHelper helper = ((MyFragment)f).getUiHelper();
                if(helper != null) {
                    helper.showNextQueuedMessage();
                }
            }
        }
    }
}
