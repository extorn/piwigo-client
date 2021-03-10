package delit.piwigoclient.piwigoApi.upload;

import android.content.Context;

import delit.piwigoclient.piwigoApi.upload.network.MyBroadcastEventListener;
import delit.piwigoclient.piwigoApi.upload.network.NetworkUtils;
import delit.piwigoclient.ui.preferences.AutoUploadJobsConfig;

public class UploadServiceNetworkListener implements MyBroadcastEventListener {
    private final Context context;
    private final AutoUploadJobsConfig autoUploadJobsConfig;
    private boolean hasInternetAccess;
    private boolean isOnUnMeteredNetwork;

    public UploadServiceNetworkListener(Context context, AutoUploadJobsConfig autoUploadJobsConfig) {
        this.context = context;
        this.autoUploadJobsConfig = autoUploadJobsConfig;
    }

    @Override
    public void onNetworkChange(boolean internetAccess, boolean unMeteredNet, int linkUpstreamBandwidthKbps) {
        hasInternetAccess = internetAccess;
        isOnUnMeteredNetwork = unMeteredNet;
        if (hasInternetAccess && (!autoUploadJobsConfig.isUploadOnUnMeteredNetworkOnly(context) || isOnUnMeteredNetwork)) {
            BackgroundPiwigoUploadService.sendActionResume(context);
        } else {
            BackgroundPiwigoUploadService.sendActionPauseUpload(context);
        }
    }

    @Override
    public void onDeviceUnlocked() {
        BackgroundPiwigoUploadService.sendActionResume(context);
    }

    @Override
    public boolean isPermitUpload(NetworkUtils networkUtils) {
        isOnUnMeteredNetwork = networkUtils.isConnectedToWireless(context);
        hasInternetAccess = networkUtils.hasNetworkConnection(context);
        return hasInternetAccess && (!autoUploadJobsConfig.isUploadOnUnMeteredNetworkOnly(context) || isOnUnMeteredNetwork);
    }
}
