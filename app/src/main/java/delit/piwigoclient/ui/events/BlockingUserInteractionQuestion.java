package delit.piwigoclient.ui.events;

import androidx.annotation.StringRes;

import org.greenrobot.eventbus.EventBus;

public class BlockingUserInteractionQuestion {
    public final @StringRes
    int questionStringId;
    private Boolean userResponse;

    public BlockingUserInteractionQuestion(@StringRes int questionStringId) {
        this.questionStringId = questionStringId;
    }

    public void askQuestion() {
        EventBus.getDefault().post(this);
        synchronized (this) {
            while (userResponse == null) {
                try {
                    this.wait();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public void respondYes() {
        userResponse = Boolean.TRUE;
        synchronized (this) {
            this.notifyAll();
        }
    }

    public void respondNo() {
        userResponse = Boolean.FALSE;
        synchronized (this) {
            this.notifyAll();
        }
    }

    public boolean getResponse() {
        return userResponse;
    }
}
