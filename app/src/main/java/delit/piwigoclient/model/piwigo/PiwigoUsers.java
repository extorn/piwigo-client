package delit.piwigoclient.model.piwigo;

/**
 * Created by gareth on 02/01/18.
 */

public class PiwigoUsers extends IdentifiablePagedList<User> {
    public PiwigoUsers() {
        super("User");
    }
}
