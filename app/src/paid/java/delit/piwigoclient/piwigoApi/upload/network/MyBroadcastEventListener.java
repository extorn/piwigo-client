package delit.piwigoclient.piwigoApi.upload.network;

public interface MyBroadcastEventListener {
    void onHasExternalPowerChange(boolean hasExternalPower);

    void onNetworkChange(boolean internetAccess, boolean unmeteredNet, int linkUpstreamBandwidthKbps);

    void onDeviceUnlocked();

    boolean isPermitUpload(NetworkUtils networkUtils);
}
