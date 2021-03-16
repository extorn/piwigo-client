package delit.piwigoclient.piwigoApi.upload.network;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkInfo;
import android.os.Build;

import androidx.annotation.NonNull;

import java.util.Objects;

public class NetworkUtils {
    @SuppressWarnings("deprecation")
    private BroadcastEventsListener networkStatusChangeListener;

    public boolean hasNetworkConnection(@NonNull Context context) {
        ConnectivityManager cm =
                (ConnectivityManager)context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo;
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Network activeNetwork = Objects.requireNonNull(cm).getActiveNetwork();
            networkInfo = cm.getNetworkInfo(activeNetwork);
        } else {
            networkInfo = Objects.requireNonNull(cm).getActiveNetworkInfo();
        }
        return networkInfo != null && networkInfo.isConnected();
    }

    public boolean isConnectedToWireless(@NonNull Context context) {
        ConnectivityManager cm =
                (ConnectivityManager)context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo;
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Network activeNetwork = Objects.requireNonNull(cm).getActiveNetwork();
            networkInfo = cm.getNetworkInfo(activeNetwork);
        } else {
            networkInfo = Objects.requireNonNull(cm).getActiveNetworkInfo();
        }
        if (networkInfo != null && networkInfo.getType() == ConnectivityManager.TYPE_WIFI) {
            return networkInfo.isConnected();
        }
        return false;
    }

    public void registerBroadcastReceiver(@NonNull Context context, @NonNull MyBroadcastEventListener networkListener) {

        ConnectivityManager cm = (ConnectivityManager)context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            cm.registerDefaultNetworkCallback(new MyNetworkCallback(networkListener));
        } else {
            //noinspection deprecation
            networkStatusChangeListener = new BroadcastEventsListener(networkListener);
            context.registerReceiver(networkStatusChangeListener, networkStatusChangeListener.getIntentFilter());
        }
    }
    public void unregisterBroadcastReceiver(Context context) {
        if(networkStatusChangeListener != null) {
            context.unregisterReceiver(networkStatusChangeListener);
        }
    }

    public boolean isOkayToUpload() {
        if(networkStatusChangeListener == null) {
            return true;
        } else {
            return networkStatusChangeListener.getListener().isPermitUpload(this);
        }
    }
}
