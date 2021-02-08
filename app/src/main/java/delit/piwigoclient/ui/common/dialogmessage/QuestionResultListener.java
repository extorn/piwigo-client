package delit.piwigoclient.ui.common.dialogmessage;

import android.os.Parcelable;
import android.view.ViewGroup;

import androidx.annotation.LayoutRes;
import androidx.appcompat.app.AlertDialog;

import delit.piwigoclient.ui.common.UIHelper;

public interface QuestionResultListener<P extends UIHelper<P,T>, T> extends Parcelable {
    void onDismiss(AlertDialog dialog);

    void onResultInternal(AlertDialog dialog, Boolean positiveAnswer);

    void onResult(AlertDialog dialog, Boolean positiveAnswer);

    void onShow(AlertDialog alertDialog);

    void setUiHelper(P uiHelper);

    T getParent();

    void chainResult(QuestionResultListener<P,T> listener);

    void onPopulateDialogView(ViewGroup dialogView, @LayoutRes int layoutId);

    void onBeforeShow(AlertDialog alertDialog);
}
