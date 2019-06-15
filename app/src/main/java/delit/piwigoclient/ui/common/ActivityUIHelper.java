package delit.piwigoclient.ui.common;

import android.content.SharedPreferences;
import android.view.View;

import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import delit.piwigoclient.R;
import delit.piwigoclient.ui.common.fragment.MyFragment;
import delit.piwigoclient.ui.events.BlockingUserInteractionQuestion;

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
    protected View getParentView() {
        View v = getParent().getWindow().getDecorView().findViewById(android.R.id.content);
        View iv = v.findViewById(R.id.main_view);
        return iv != null ? iv : v;
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

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(final BlockingUserInteractionQuestion event) {
        QueuedQuestionMessage message = new QueuedQuestionMessage(R.string.alert_question_title, getContext().getString(event.questionStringId), R.string.button_yes, R.string.button_no, new BlockingUserInteractionQuestionResultAdapter(this, event));
        showMessageImmediatelyIfPossible(message);
    }

    private static class BlockingUserInteractionQuestionResultAdapter extends QuestionResultAdapter<ActivityUIHelper> {
        private final BlockingUserInteractionQuestion event;

        public BlockingUserInteractionQuestionResultAdapter(ActivityUIHelper uiHelper, BlockingUserInteractionQuestion event) {
            super(uiHelper);
            this.event = event;
        }

        @Override
        public void onResult(AlertDialog dialog, Boolean positiveAnswer) {
            if (Boolean.TRUE == positiveAnswer) {
                event.respondYes();
            } else {
                event.respondNo();
            }
        }
    }
}
