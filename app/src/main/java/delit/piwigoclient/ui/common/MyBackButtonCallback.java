package delit.piwigoclient.ui.common;

import androidx.activity.OnBackPressedCallback;

public class MyBackButtonCallback extends OnBackPressedCallback {
    private BackButtonHandler handler;

    public MyBackButtonCallback(BackButtonHandler handler) {
        super(true);
        this.handler = handler;
    }

    @Override
    public void handleOnBackPressed() {
        if(!handler.onBackButton()) {
        }
    }
}
