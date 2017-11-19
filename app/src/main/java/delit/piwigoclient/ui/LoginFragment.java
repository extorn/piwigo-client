package delit.piwigoclient.ui;

import android.content.Context;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import org.greenrobot.eventbus.EventBus;

import delit.piwigoclient.R;
import delit.piwigoclient.piwigoApi.BasicPiwigoResponseListener;
import delit.piwigoclient.piwigoApi.PiwigoAccessService;
import delit.piwigoclient.piwigoApi.PiwigoResponseBufferingHandler;
import delit.piwigoclient.ui.common.MyFragment;
import delit.piwigoclient.ui.events.PiwigoLoginSuccessEvent;

/**
 * Created by gareth on 27/05/17.
 */

public class LoginFragment extends MyFragment implements View.OnClickListener {

    private static final java.lang.String STATE_AUTO_LOGIN = "autoLogin";
    private Button loginButton;

    public static LoginFragment newInstance() {
        LoginFragment fragment = new LoginFragment();
        return fragment;
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {

        super.onCreateView(inflater, container, savedInstanceState);

        View view = inflater.inflate(R.layout.fragment_login, container, false);
        loginButton = view.findViewById(R.id.loginButton);
        loginButton.setEnabled(true);
        loginButton.setOnClickListener(this);
        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        if(!getUiHelper().isServiceCallInProgress()) {
            loginButton.callOnClick();
        }
    }

    @Override
    public void onClick(View v) {
        loginButton.setEnabled(false);
        String serverUri = prefs.getString(getString(R.string.preference_piwigo_server_address_key), "");
        getUiHelper().addActiveServiceCall(String.format(getString(R.string.logging_in_to_piwigo_pattern), serverUri), PiwigoAccessService.startActionLogin(getContext()));
    }

    @Override
    protected BasicPiwigoResponseListener buildPiwigoResponseListener(Context context) {
        return new CustomPiwigoResponseListener();
    }

    private class CustomPiwigoResponseListener extends BasicPiwigoResponseListener {
        @Override
        public void onBeforeHandlePiwigoResponse(PiwigoResponseBufferingHandler.Response response) {
            if (response instanceof PiwigoResponseBufferingHandler.PiwigoOnLoginResponse) {
                PiwigoResponseBufferingHandler.PiwigoOnLoginResponse rsp = (PiwigoResponseBufferingHandler.PiwigoOnLoginResponse) response;
                if(rsp.isSessionRetrieved() && rsp.isUserDetailsRetrieved()) {
                    onLogin();
                } else {
                    onLoginFailed();
                }
            } else {
                if(response instanceof PiwigoResponseBufferingHandler.ErrorResponse){
                    onLoginFailed();
                }
            }
        }
    }

    public void onLogin() {
        EventBus.getDefault().post(new PiwigoLoginSuccessEvent(true));
    }

    public void onLoginFailed() {
        loginButton.setEnabled(true);
    }

}
