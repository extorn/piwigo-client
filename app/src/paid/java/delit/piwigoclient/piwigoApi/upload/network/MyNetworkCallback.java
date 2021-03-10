package delit.piwigoclient.piwigoApi.upload.network;

import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
public class MyNetworkCallback extends ConnectivityManager.NetworkCallback {

    private final MyBroadcastEventListener listener;

    public MyNetworkCallback(MyBroadcastEventListener listener) {
        this.listener = listener;
    }

    @Override
    public void onCapabilitiesChanged(@NonNull Network network, @NonNull NetworkCapabilities networkCapabilities) {
        super.onCapabilitiesChanged(network, networkCapabilities);
        boolean unmeteredNet = networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED);
        boolean internetAccess = networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
        listener.onNetworkChange(internetAccess, unmeteredNet, networkCapabilities.getLinkUpstreamBandwidthKbps());
    }
}
