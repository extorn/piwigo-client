package delit.piwigoclient.ui.events;

/**
 * Created by gareth on 12/06/17.
 */

public class CancelDownloadEvent {

    public final long messageId;

    public CancelDownloadEvent(long messageId) {
        this.messageId = messageId;
    }
}
