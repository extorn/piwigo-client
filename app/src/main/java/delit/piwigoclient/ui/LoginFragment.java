package delit.piwigoclient.ui;

import android.content.Context;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import org.greenrobot.eventbus.EventBus;

import delit.piwigoclient.R;
import delit.piwigoclient.business.ConnectionPreferences;
import delit.piwigoclient.model.piwigo.PiwigoSessionDetails;
import delit.piwigoclient.piwigoApi.BasicPiwigoResponseListener;
import delit.piwigoclient.piwigoApi.PiwigoResponseBufferingHandler;
import delit.piwigoclient.piwigoApi.handlers.LoginResponseHandler;
import delit.piwigoclient.ui.common.fragment.MyFragment;
import delit.piwigoclient.ui.events.PiwigoLoginSuccessEvent;

/**
 * Created by gareth on 27/05/17.
 */

public class LoginFragment extends MyFragment implements View.OnClickListener {

    private static final java.lang.String STATE_AUTO_LOGIN = "autoLogin";
    private Button loginButton;

    public static LoginFragment newInstance() {
        return new LoginFragment();
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {

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
        ConnectionPreferences.ProfilePreferences connectionPrefs = ConnectionPreferences.getActiveProfile();
        String serverUri = connectionPrefs.getTrimmedNonNullPiwigoServerAddress(prefs, getContext());
        getUiHelper().addActiveServiceCall(String.format(getString(R.string.logging_in_to_piwigo_pattern), serverUri), new LoginResponseHandler().invokeAsync(getContext()));
    }

    @Override
    protected BasicPiwigoResponseListener buildPiwigoResponseListener(Context context) {
        return new CustomPiwigoResponseListener();
    }

    private class CustomPiwigoResponseListener extends BasicPiwigoResponseListener {
        @Override
        public void onBeforeHandlePiwigoResponse(PiwigoResponseBufferingHandler.Response response) {
            if (response instanceof LoginResponseHandler.PiwigoOnLoginResponse) {
                LoginResponseHandler.PiwigoOnLoginResponse rsp = (LoginResponseHandler.PiwigoOnLoginResponse) response;
                if(PiwigoSessionDetails.isFullyLoggedIn(ConnectionPreferences.getActiveProfile())) {
                    onLogin(rsp.getOldCredentials());
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

    private void onLogin(PiwigoSessionDetails oldCredentials) {
        EventBus.getDefault().post(new PiwigoLoginSuccessEvent(oldCredentials, true));
    }

    private void onLoginFailed() {
        loginButton.setEnabled(true);
    }

}
