package delit.piwigoclient.ui.permissions.groups;

import android.content.Context;
import android.view.View;
import android.widget.TextView;

import java.util.ArrayList;

import delit.piwigoclient.R;
import delit.piwigoclient.model.piwigo.Group;
import delit.piwigoclient.ui.common.MultiSourceListAdapter;

/**
 * Created by gareth on 22/06/17.
 */
//TODO re-test group selection on Upload (create album, view album, etc).. check readonly mode too. Also toggle all button check for this and album permissions.
public class GroupsSelectionListAdapter extends MultiSourceListAdapter<Group> {


    public GroupsSelectionListAdapter(Context context, ArrayList<Group> availableItems, boolean enabled) {
        super(context, availableItems, enabled);
    }

    @Override
    public long getItemId(Group item) {
        return item.getId();
    }

    @Override
    protected void setViewContentForItemDisplay(View view, Group item, int levelInTreeOfItem) {
//        super.setViewContentForItemDisplay(view, item, levelInTreeOfItem);
        TextView textField = view.findViewById(R.id.permission_text);
        textField.setText(item.getName());
    }
}
