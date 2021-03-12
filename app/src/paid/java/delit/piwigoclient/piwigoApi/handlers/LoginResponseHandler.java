package delit.piwigoclient.piwigoApi.handlers;

import android.content.Context;

import androidx.annotation.NonNull;

import org.greenrobot.eventbus.EventBus;

import delit.libs.ui.util.ExecutorManager;
import delit.piwigoclient.business.AppPreferences;
import delit.piwigoclient.business.ConnectionPreferences;
import delit.piwigoclient.model.piwigo.PiwigoSessionDetails;
import delit.piwigoclient.ui.events.ServerUpdatesAvailableEvent;

public class LoginResponseHandler extends AbstractLoginResponseHandler<LoginResponseHandler> {

    public LoginResponseHandler() {
        super();
    }

    public LoginResponseHandler(String password) {
        super(password);
    }

    @Override
    protected void performExtraServerCalls(@NonNull Context context, @NonNull ConnectionPreferences.ProfilePreferences connectionPrefs, ExecutorManager executor) {
        PiwigoSessionDetails sessionDetails = getPiwigoSessionDetails();
        if(sessionDetails.isAdminUser() && AppPreferences.isCheckForPiwigoServerUpdates(context, getSharedPrefs())) {
            executor.submit(()->getUpdatesOfServerComponents(context, connectionPrefs));
        }
    }

    private void getUpdatesOfServerComponents(Context context, ConnectionPreferences.ProfilePreferences connectionPrefs) {
        ServerAdminCheckForUpdatesResponseHandler serverAdminCheckForUpdatesHandler = new ServerAdminCheckForUpdatesResponseHandler();
        serverAdminCheckForUpdatesHandler.setPerformingLogin(); // need this otherwise it will go recursive getting another login session
        serverAdminCheckForUpdatesHandler.invokeAndWait(context, connectionPrefs);
        if (serverAdminCheckForUpdatesHandler.isSuccess()) {
            ServerAdminCheckForUpdatesResponseHandler.PiwigoServerUpdateResponse response = (ServerAdminCheckForUpdatesResponseHandler.PiwigoServerUpdateResponse) serverAdminCheckForUpdatesHandler.getResponse();
            EventBus.getDefault().post(new ServerUpdatesAvailableEvent(response.isServerUpdateAvailable(), response.isPluginUpdateAvailable()));
        }
    }

}
