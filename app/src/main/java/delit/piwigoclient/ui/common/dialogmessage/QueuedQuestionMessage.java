package delit.piwigoclient.ui.common.dialogmessage;

import android.os.Parcel;
import android.view.View;

import delit.piwigoclient.R;
import delit.piwigoclient.ui.common.UIHelper;

public class QueuedQuestionMessage<P extends UIHelper<P,T>,T> extends QueuedDialogMessage<P,T> {

    private final int negativeButtonTextId;
    private final int layoutId;
    private final int neutralButtonTextId;

    public QueuedQuestionMessage(Parcel in) {
        super(in);
        negativeButtonTextId = in.readInt();
        layoutId = in.readInt();
        neutralButtonTextId = in.readInt();
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        super.writeToParcel(dest, flags);
        dest.writeInt(negativeButtonTextId);
        dest.writeInt(layoutId);
        dest.writeInt(neutralButtonTextId);
    }

    public static final Creator<QueuedQuestionMessage<?,?>> CREATOR = new Creator<QueuedQuestionMessage<?,?>>() {
        @Override
        public QueuedQuestionMessage<?,?> createFromParcel(Parcel in) {
            return new QueuedQuestionMessage<>(in);
        }

        @Override
        public QueuedQuestionMessage<?,?>[] newArray(int size) {
            return new QueuedQuestionMessage[size];
        }
    };

    public QueuedQuestionMessage(int titleId, String message, int positiveButtonTextId, int negativeButtonTextId, QuestionResultListener<P,T> listener) {
        this(titleId, message, null, View.NO_ID, positiveButtonTextId, negativeButtonTextId, listener);
    }

    public QueuedQuestionMessage(int titleId, String message, String detail, int positiveButtonTextId, int negativeButtonTextId, QuestionResultListener<P,T> listener) {
        this(titleId, message, detail, View.NO_ID, positiveButtonTextId, negativeButtonTextId, listener);
    }

    public QueuedQuestionMessage(int titleId, String message, int layoutId, int positiveButtonTextId, int negativeButtonTextId, QuestionResultListener<P,T> listener) {
        this(titleId, message, null, layoutId, positiveButtonTextId, negativeButtonTextId, View.NO_ID, listener);
    }

    public QueuedQuestionMessage(int titleId, String message, String detail, int layoutId, int positiveButtonTextId, int negativeButtonTextId, QuestionResultListener<P,T> listener) {
        this(titleId, message, detail, layoutId, positiveButtonTextId, negativeButtonTextId, View.NO_ID, listener);
    }

    public QueuedQuestionMessage(int titleId, String message, String detail, int layoutId, int positiveButtonTextId, int negativeButtonTextId, int neutralButtonTextId, QuestionResultListener<P,T> listener) {

        super(titleId, message, detail, positiveButtonTextId, false, listener);
        this.negativeButtonTextId = negativeButtonTextId;
        if(detail != null && !detail.trim().isEmpty() && layoutId == View.NO_ID) {
            this.layoutId = R.layout.layout_dialog_detailed;
        } else {
            this.layoutId = layoutId;
        }
        this.neutralButtonTextId = neutralButtonTextId;
    }

    public boolean isShowNegativeButton() {
        return negativeButtonTextId != View.NO_ID;
    }

    public boolean isShowNeutralButton() {
        return neutralButtonTextId != View.NO_ID;
    }

    public int getNeutralButtonTextId() {
        return neutralButtonTextId;
    }

    public int getNegativeButtonTextId() {
        return negativeButtonTextId;
    }

    @Override
    public int getLayoutId() {
        return layoutId;
    }

}
