package delit.piwigoclient.ui.events;

import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.StringRes;

import org.greenrobot.eventbus.EventBus;

public class BlockingUserInteractionQuestion implements Parcelable {
    public final @StringRes
    int questionStringId;
    private Boolean userResponse;

    public BlockingUserInteractionQuestion(@StringRes int questionStringId) {
        this.questionStringId = questionStringId;
    }

    protected BlockingUserInteractionQuestion(Parcel in) {
        questionStringId = in.readInt();
        byte tmpUserResponse = in.readByte();
        userResponse = tmpUserResponse == 0 ? null : tmpUserResponse == 1;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(questionStringId);
        dest.writeByte((byte) (userResponse == null ? 0 : userResponse ? 1 : 2));
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Creator<BlockingUserInteractionQuestion> CREATOR = new Creator<BlockingUserInteractionQuestion>() {
        @Override
        public BlockingUserInteractionQuestion createFromParcel(Parcel in) {
            return new BlockingUserInteractionQuestion(in);
        }

        @Override
        public BlockingUserInteractionQuestion[] newArray(int size) {
            return new BlockingUserInteractionQuestion[size];
        }
    };

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
