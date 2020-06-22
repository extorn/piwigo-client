package delit.piwigoclient.ui.common;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Parcel;
import android.os.Parcelable;
import android.view.View;

import androidx.appcompat.app.AlertDialog;
import androidx.core.view.ViewCompat;
import androidx.fragment.app.Fragment;

import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import delit.piwigoclient.R;
import delit.piwigoclient.ui.common.fragment.MyFragment;
import delit.piwigoclient.ui.events.BlockingUserInteractionQuestion;

/**
 * Created by gareth on 17/10/17.
 */

public class ActivityUIHelper<T extends BaseMyActivity> extends UIHelper<T> {
    public ActivityUIHelper(T parent, SharedPreferences prefs, View attachedView) {
        super(parent, prefs, parent, attachedView);
    }

    @Override
    public Context getAppContext() {
        return getParent();
    }

    @Override
    protected boolean canShowDialog() {
        return super.canShowDialog() && ViewCompat.isAttachedToWindow(getParentView()) && !getParent().isFinishing();
    }

    @Override
    protected View getParentView() {
        T parent = getParent();
        if(parent == null) {
            return null;
        }
        View v = parent.getWindow().getDecorView().findViewById(android.R.id.content);
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
        QueuedQuestionMessage message = new QueuedQuestionMessage(R.string.alert_question_title, getAppContext().getString(event.questionStringId), R.string.button_yes, R.string.button_no, new BlockingUserInteractionQuestionResultAdapter(this, event));
        showMessageImmediatelyIfPossible(message);
    }

    private static class BlockingUserInteractionQuestionResultAdapter<T extends ActivityUIHelper<Q>,Q extends MyActivity<Q>> extends QuestionResultAdapter<T,Q> implements Parcelable {
        private final BlockingUserInteractionQuestion event;

        public BlockingUserInteractionQuestionResultAdapter(T uiHelper, BlockingUserInteractionQuestion event) {
            super(uiHelper);
            this.event = event;
        }

        protected BlockingUserInteractionQuestionResultAdapter(Parcel in) {
            super(in);
            event = in.readParcelable(BlockingUserInteractionQuestion.class.getClassLoader());
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            super.writeToParcel(dest, flags);
            dest.writeParcelable(event, flags);
        }

        @Override
        public int describeContents() {
            return 0;
        }

        public static final Creator<BlockingUserInteractionQuestionResultAdapter> CREATOR = new Creator<BlockingUserInteractionQuestionResultAdapter>() {
            @Override
            public BlockingUserInteractionQuestionResultAdapter createFromParcel(Parcel in) {
                return new BlockingUserInteractionQuestionResultAdapter(in);
            }

            @Override
            public BlockingUserInteractionQuestionResultAdapter[] newArray(int size) {
                return new BlockingUserInteractionQuestionResultAdapter[size];
            }
        };

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
