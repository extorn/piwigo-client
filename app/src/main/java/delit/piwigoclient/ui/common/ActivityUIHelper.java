package delit.piwigoclient.ui.common;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Parcel;
import android.os.Parcelable;
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

public class ActivityUIHelper<UIH extends ActivityUIHelper<UIH,T>,T extends BaseMyActivity<T,UIH>> extends UIHelper<UIH,T> {
    public ActivityUIHelper(T parent, SharedPreferences prefs, View attachedView) {
        super(parent, prefs, parent, attachedView);
    }

    @Override
    public Context getAppContext() {
        return getParent();
    }

    @Override
    protected boolean canShowDialog() {
        boolean canShow = super.canShowDialog();
        canShow &= getParent().isAttachedToWindow();
        canShow &= !getParent().isFinishing();
        return canShow;
    }

    @Override
    protected View getParentView() {
        T parent = getParent();
        if(parent == null) {
            return null;
        }
        View v = parent.getWindow().getDecorView().findViewById(android.R.id.content);
//        View iv = v.findViewById(R.id.main_view);
//        return iv != null ? iv : v;
        return v;
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
                FragmentUIHelper<?,?> helper = ((MyFragment<?,?>)f).getUiHelper();
                if(helper != null) {
                    helper.showNextQueuedMessage();
                }
            }
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(final BlockingUserInteractionQuestion event) {
        QueuedQuestionMessage<UIH,T> message = new QueuedQuestionMessage<>(R.string.alert_question_title, getAppContext().getString(event.questionStringId), R.string.button_yes, R.string.button_no, new BlockingUserInteractionQuestionResultAdapter<UIH,T>((UIH) this, event));
        showMessageImmediatelyIfPossible(message);
    }

    private static class BlockingUserInteractionQuestionResultAdapter<UIH extends ActivityUIHelper<UIH,T>,T extends BaseMyActivity<T, UIH>> extends QuestionResultAdapter<UIH,T> implements Parcelable {
        private final BlockingUserInteractionQuestion event;

        public BlockingUserInteractionQuestionResultAdapter(UIH uiHelper, BlockingUserInteractionQuestion event) {
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

        public static final Creator<BlockingUserInteractionQuestionResultAdapter<?,?>> CREATOR = new Creator<BlockingUserInteractionQuestionResultAdapter<?,?>>() {
            @Override
            public BlockingUserInteractionQuestionResultAdapter<?,?> createFromParcel(Parcel in) {
                return new BlockingUserInteractionQuestionResultAdapter<>(in);
            }

            @Override
            public BlockingUserInteractionQuestionResultAdapter<?,?>[] newArray(int size) {
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
