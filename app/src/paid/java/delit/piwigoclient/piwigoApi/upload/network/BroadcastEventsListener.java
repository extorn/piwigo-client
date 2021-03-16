package delit.piwigoclient.piwigoApi.upload.network;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;

/**
 * This is the old way of doing things.
 */
@Deprecated
public class BroadcastEventsListener extends BroadcastReceiver {

    private final MyBroadcastEventListener listener;

    public BroadcastEventsListener(MyBroadcastEventListener listener) {
        this.listener = listener;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if(Intent.ACTION_USER_PRESENT.equals(intent.getAction())) {
            handleDeviceUnlocked();
        } else if(ConnectivityManager.CONNECTIVITY_ACTION.equals(intent.getAction())) {
            handleNetworkStatusChanged(intent);
        } else if(WifiManager.NETWORK_STATE_CHANGED_ACTION.equals(intent.getAction())) {
            handleWifiEvent(context, intent);
        }
    }

    private void handleWifiEvent(Context context, Intent intent) {
        boolean hasWifi = false;
        boolean hasInternet = false;
        NetworkInfo networkInfo = intent.getParcelableExtra(WifiManager.EXTRA_NETWORK_INFO);
        if(networkInfo != null && networkInfo.isConnected()) {
            // Wifi is connected
            hasWifi = true;
        }
        final ConnectivityManager connMgr = (ConnectivityManager) context
                .getSystemService(Context.CONNECTIVITY_SERVICE);
        if(connMgr != null) {
            networkInfo = connMgr.getActiveNetworkInfo();
            hasInternet = networkInfo != null && networkInfo.isConnected();
        }

        listener.onNetworkChange(hasInternet, hasWifi, -1);
    }

    private void handleDeviceUnlocked() {
        listener.onDeviceUnlocked();
    }

    private void handleNetworkStatusChanged(Intent intent) {
        boolean hasWifi = false;
        boolean hasInternet;
        NetworkInfo networkInfo =
                intent.getParcelableExtra(ConnectivityManager.EXTRA_NETWORK_INFO);
        if(networkInfo != null && networkInfo.getType() == ConnectivityManager.TYPE_WIFI) {
            hasWifi = networkInfo.isConnected();
        }
        hasInternet = networkInfo != null && networkInfo.isConnected();
        listener.onNetworkChange(hasInternet, hasWifi, -1);
    }

    public IntentFilter getIntentFilter() {
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction("android.net.conn.CONNECTIVITY_CHANGE");
        intentFilter.addAction("android.intent.action.USER_PRESENT");
        return intentFilter;
    }

    public MyBroadcastEventListener getListener() {
        return listener;
    }
}
