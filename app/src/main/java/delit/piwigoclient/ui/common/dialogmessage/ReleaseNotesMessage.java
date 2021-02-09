package delit.piwigoclient.ui.common.dialogmessage;

import android.app.Dialog;
import android.content.Context;
import android.os.Parcel;
import android.os.Parcelable;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ExpandableListView;
import android.widget.SimpleExpandableListAdapter;

import androidx.annotation.NonNull;

import java.util.Map;

import delit.libs.ui.util.ParcelUtils;
import delit.libs.ui.view.list.StringMapExpandableListAdapterBuilder;
import delit.piwigoclient.R;
import delit.piwigoclient.ui.common.UIHelper;

public class ReleaseNotesMessage<P extends UIHelper<P,T>,T> extends QueuedDialogMessage<P,T> implements Parcelable {

    private final Map<String, String> releaseHistory;

    public ReleaseNotesMessage(@NonNull Context context, @NonNull Map<String, String> releaseNotes, QuestionResultListener<P, T> listener) {
        super(R.string.alert_information, R.string.button_ok, true, listener);
        this.releaseHistory = releaseNotes;
    }

    protected ReleaseNotesMessage(Parcel in) {
        super(in);
        releaseHistory = ParcelUtils.readMap(in);
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        super.writeToParcel(dest, flags);
        ParcelUtils.writeMap(dest, releaseHistory);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public int getLayoutId() {
        return R.layout.layout_dialog_release_history;
    }

    @Override
    public void populateCustomView(ViewGroup dialogView) {
        SimpleExpandableListAdapter historicalReleasesListAdapter = new StringMapExpandableListAdapterBuilder().build(dialogView.getContext(), releaseHistory);
        ExpandableListView historicalReleasesListView = dialogView.findViewById(R.id.release_history_list);
        historicalReleasesListView.setAdapter(historicalReleasesListAdapter);
        if(releaseHistory.size() >= 1) {
            historicalReleasesListView.expandGroup(0);
        }
    }

    @Override
    public WindowManager.LayoutParams showWithDialogLayoutParams(Dialog dialog, View view) {
//            WindowManager.LayoutParams lp = new WindowManager.LayoutParams();
//            lp.copyFrom(dialog.getWindow().getAttributes());
//            lp.width = WindowManager.LayoutParams.MATCH_PARENT;
//            lp.height = WindowManager.LayoutParams.MATCH_PARENT;

        // adjust layout size
        view.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override
            public void onViewAttachedToWindow(View v) {
//                    AlertDialogLayout view = DisplayUtils.getParentOfType(v, AlertDialogLayout.class);
//                    view.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
//                    int dialogHeight = view.getMeasuredHeight();
//                    int idxMyView = DisplayUtils.getIndexOfChildContainingView(view, v);
//
//                    int space = dialogHeight;
//                    for(int i = 0; i < view.getChildCount(); i++) {
//                        if(i == idxMyView) {
//                            continue;
//                        }
//                        View child = view.getChildAt(i);
//                        space -= child.getMeasuredHeight();
//                        space -= child.getPaddingTop();
//                        space -= child.getPaddingBottom();
//                    }
//                    int myViewHeight = v.getMeasuredHeight();
//                    v.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.makeMeasureSpec(space, View.MeasureSpec.AT_MOST));
//                    v.getLayoutParams().height = space;
//                    view.requestLayout();
            }

            @Override
            public void onViewDetachedFromWindow(View v) {

            }
        });


        return null;// lp;
    }


    public static final Creator<ReleaseNotesMessage<?,?>> CREATOR = new Creator<ReleaseNotesMessage<?,?>>() {
        @Override
        public ReleaseNotesMessage<?,?> createFromParcel(Parcel in) {
            return new ReleaseNotesMessage<>(in);
        }

        @Override
        public ReleaseNotesMessage<?,?>[] newArray(int size) {
            return new ReleaseNotesMessage<?,?>[size];
        }
    };
}
