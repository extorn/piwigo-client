package delit.piwigoclient.ui.common.dialogmessage;

import android.app.Dialog;
import android.os.Parcel;
import android.os.Parcelable;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.ToggleButton;

import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.concurrent.atomic.AtomicInteger;

import delit.libs.ui.util.ParcelUtils;
import delit.libs.util.Utils;
import delit.piwigoclient.R;
import delit.piwigoclient.ui.common.UIHelper;

public class QueuedDialogMessage<P extends UIHelper<P,T>,T> implements Parcelable {
    private static final AtomicInteger idGen = new AtomicInteger();
    private final int id;
    private final int titleId;
    private final String message;
    private final int positiveButtonTextId;
    private final boolean cancellable;
    private final String detail;
    private final QuestionResultListener<P,T> listener;
    private final boolean hasListener;

    @NonNull
    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("QueuedDialogMessage{");
        sb.append("id=").append(id);
        sb.append(", titleId=").append(titleId);
        sb.append(", message='").append(message).append('\'');
        sb.append(", detail='").append(detail).append('\'');
        sb.append(", listener=").append(Utils.getId(listener));
        sb.append(", hasListener=").append(hasListener);
        sb.append('}');
        return sb.toString();
    }

    public QueuedDialogMessage(Parcel in) {
        id = in.readInt();
        titleId = in.readInt();
        message = in.readString();
        positiveButtonTextId = in.readInt();
        cancellable = ParcelUtils.readBool(in);
        detail = in.readString();
        listener = ParcelUtils.readValue(in, QuestionResultListener.class.getClassLoader(), QuestionResultListener.class);
        hasListener = ParcelUtils.readBool(in);
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(id);
        dest.writeInt(titleId);
        dest.writeString(message);
        dest.writeInt(positiveButtonTextId);
        ParcelUtils.writeBool(dest, cancellable);
        dest.writeString(detail);
        try {
            dest.writeParcelable(listener, 0);
        } catch(RuntimeException e) {
            dest.writeParcelable(null, 0); // so we can still read the non parcelable object in (as null)
        }
        ParcelUtils.writeBool(dest, listener != null); // has listener
    }

    public QueuedDialogMessage(int titleId, int positiveButtonTextId, boolean cancellable, QuestionResultListener<P,T> listener) {
        this(titleId, "", null, positiveButtonTextId, cancellable, listener);
    }

    public QueuedDialogMessage(int titleId, String message, String detail) {
        this(titleId, message, detail, R.string.button_ok, true, null);
    }

    public QueuedDialogMessage(int titleId, String message, String detail, QuestionResultListener<P,T> listener) {
        this(titleId, message, detail, R.string.button_ok, true, listener);
    }

    public QueuedDialogMessage(int titleId, String message, String detail, int positiveButtonTextId) {
        this(titleId, message, detail, positiveButtonTextId, true, null);
    }

    public QueuedDialogMessage(int titleId, String message, String detail, int positiveButtonTextId, boolean cancellable, QuestionResultListener<P,T> listener) {
        this.id = idGen.incrementAndGet();
        this.titleId = titleId;
        if (message == null) {
            throw new IllegalArgumentException("Message cannot be null");
        }
        this.message = message;
        this.positiveButtonTextId = positiveButtonTextId;
        this.listener = listener;
        this.detail = detail;
        this.cancellable = cancellable;
        this.hasListener = listener != null;
    }

    public static final Creator<QueuedDialogMessage<?,?>> CREATOR = new Creator<QueuedDialogMessage<?,?>>() {
        @Override
        public QueuedDialogMessage<?,?> createFromParcel(Parcel in) {
            return new QueuedDialogMessage<>(in);
        }

        @Override
        public QueuedDialogMessage<?,?>[] newArray(int size) {
            return new QueuedDialogMessage<?,?>[size];
        }
    };

    public int getTitleId() {
        return titleId;
    }

    public String getDetail() {
        return detail;
    }

    public boolean isCancellable() {
        return cancellable;
    }

    public int getPositiveButtonTextId() {
        return positiveButtonTextId;
    }

    public String getMessage() {
        return message;
    }

    public QuestionResultListener<P,T> getListener() {
        return listener;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof QueuedDialogMessage)) {
            return false;
        }
        QueuedDialogMessage<?,?> other = ((QueuedDialogMessage<?,?>) obj);
        return titleId == other.titleId && message.equals(other.message);
    }

    public void populateCustomView(ViewGroup dialogView) {
        int layoutId = getLayoutId();

        if (layoutId == R.layout.layout_dialog_detailed) {
            final TextView messageView = dialogView.findViewById(R.id.detailed_dialog_message);
            messageView.setText(getMessage());
            final TextView detailView = dialogView.findViewById(R.id.detailed_dialog_extra_detail);
            detailView.setText(getDetail());

            ToggleButton detailsVisibleButton = dialogView.findViewById(R.id.detailed_dialog_extra_detail_toggle);
            detailsVisibleButton.setOnCheckedChangeListener((buttonView, isChecked) -> detailView.setVisibility(isChecked ? View.VISIBLE : View.GONE));
            detailsVisibleButton.toggle();
        } else {
            listener.onPopulateDialogView(dialogView, layoutId);
        }
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public boolean isHasListener() {
        return hasListener;
    }

    /**
     * WARNING: The message is not set on the dialog if a layout ID is provided. Be sure to add it to your layout.
     * @return Layout resource id to inflate.
     */
    @LayoutRes
    public int getLayoutId() {
        if (detail != null && !detail.trim().isEmpty()) {
            return R.layout.layout_dialog_detailed;
        } else {
            return View.NO_ID;
        }
    }

    public @Nullable
    WindowManager.LayoutParams showWithDialogLayoutParams(Dialog dialog, View view) {
        return null;
    }
}
