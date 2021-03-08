package delit.piwigoclient.piwigoApi.upload;

import android.net.Uri;

public interface UriWatcher {

    void startWatching();

    void stopWatching();

    Uri getWatchedUri();
}
