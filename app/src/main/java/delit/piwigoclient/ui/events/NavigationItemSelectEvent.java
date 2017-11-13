package delit.piwigoclient.ui.events;

/**
 * Created by gareth on 12/06/17.
 */

public class NavigationItemSelectEvent {
    public final int navigationitemSelected;

    public NavigationItemSelectEvent(int navigationitemSelected) {
        this.navigationitemSelected = navigationitemSelected;
    }
}
