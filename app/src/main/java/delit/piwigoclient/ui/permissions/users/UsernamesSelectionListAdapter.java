package delit.piwigoclient.ui.permissions.users;

import android.content.Context;
import android.view.View;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashSet;

import delit.piwigoclient.R;
import delit.piwigoclient.model.piwigo.Username;
import delit.piwigoclient.ui.common.MultiSourceListAdapter;

/**
 * Created by gareth on 22/06/17.
 */

public class UsernamesSelectionListAdapter extends MultiSourceListAdapter<Username> {


    public UsernamesSelectionListAdapter(Context context, ArrayList<Username> availableItems, HashSet<Long> indirectlySelectedItems, boolean isCheckable) {
        super(context, availableItems, indirectlySelectedItems, isCheckable);
    }

    @Override
    public long getItemId(Username item) {
        return item.getId();
    }

    @Override
    protected void setViewContentForItemDisplay(View view, Username item, int levelInTreeOfItem) {
//        super.setViewContentForItemDisplay(view, item, levelInTreeOfItem);
        TextView textField = view.findViewById(R.id.permission_text);
        textField.setText(item.getUsername());
    }
}
