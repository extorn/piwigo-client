package delit.piwigoclient.ui.events;

import android.text.SpannableString;

import androidx.fragment.app.FragmentActivity;

public class ToolbarEvent {
    private SpannableString spannableTitle;
    private String title;
    private boolean expandToolbarView;
    private boolean contractToolbarView;
    private FragmentActivity activity;

    public void setExpandToolbarView(boolean expandToolbarView) {
        this.expandToolbarView = expandToolbarView;
        this.contractToolbarView = false;
    }

    public void setContractToolbarView(boolean contractToolbarView) {
        this.contractToolbarView = contractToolbarView;
        this.expandToolbarView = false;
    }

    public boolean isExpandToolbarView() {
        return expandToolbarView;
    }

    public boolean isContractToolbarView() {
        return contractToolbarView;
    }

    public void setSpannableTitle(SpannableString spannableTitle) {
        this.spannableTitle = spannableTitle;
        if(title == null) {
            setContractToolbarView(true);
        }
    }

    public void setTitle(String title) {
        this.title = title;
        if(title == null) {
            setContractToolbarView(true);
        }
    }

    public String getTitle() {
        return title;
    }

    public SpannableString getSpannableTitle() {
        return spannableTitle;
    }

    public void setActivity(FragmentActivity activity) {
        this.activity = activity;
    }

    public FragmentActivity getActivity() {
        return activity;
    }
}
