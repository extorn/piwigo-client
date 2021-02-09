package delit.piwigoclient.ui.common.dialogmessage;

import android.content.Context;
import android.os.Parcel;
import android.os.Parcelable;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import delit.libs.util.CustomSnackbar;
import delit.libs.util.ObjectUtils;

public class QueuedSimpleMessage implements Parcelable {
    private int id = -1;
    private final int duration;
    private final int titleResId;
    private final String message;

    public QueuedSimpleMessage(@StringRes int titleResId, String message, int duration) {
        this.duration = duration;
        this.titleResId = titleResId;
        this.message = message;
    }

    public QueuedSimpleMessage(Parcel in) {
        duration = in.readInt();
        titleResId = in.readInt();
        message = in.readString();
        id = in.readInt();
    }

    public void setId(int id) {
        this.id = id;
    }

    public static final Creator<QueuedSimpleMessage> CREATOR = new Creator<QueuedSimpleMessage>() {
        @Override
        public QueuedSimpleMessage createFromParcel(Parcel in) {
            return new QueuedSimpleMessage(in);
        }

        @Override
        public QueuedSimpleMessage[] newArray(int size) {
            return new QueuedSimpleMessage[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(duration);
        dest.writeInt(titleResId);
        dest.writeString(message);
        dest.writeInt(id);
    }

    public int getSnackbarDuration() {
        return duration == Toast.LENGTH_SHORT ? CustomSnackbar.LENGTH_SHORT : duration == Toast.LENGTH_LONG ? CustomSnackbar.LENGTH_LONG : CustomSnackbar.LENGTH_INDEFINITE;
    }

    public int getDuration() {
        return duration;
    }

    @Override
    public int hashCode() {
        return duration + (titleResId * 3) + (5 * message.hashCode());
    }

    @Override
    public boolean equals(@Nullable Object obj) {
        if(!(obj instanceof QueuedSimpleMessage)) {
            return false;
        }
        QueuedSimpleMessage other = (QueuedSimpleMessage) obj;
        return (id >= 0 && id == other.id) || (duration == other.duration && titleResId == other.titleResId && ObjectUtils.areEqual(message, other.message));
    }

    public String getMessage() {
        return message;
    }

    public int getTitleResId() {
        return titleResId;
    }

    public int getId() {
        return id;
    }

    public @NonNull String toString(@NonNull Context context) {
        StringBuilder sb = new StringBuilder();
        sb.append("title : ");
        sb.append(context.getString(getTitleResId()));
        sb.append('\n');
        sb.append("msg : ");
        sb.append(getMessage());
        sb.append('\n');
        sb.append("duration : ");
        sb.append(getDuration());
        sb.append('\n');
        return sb.toString();
    }
}
