package delit.piwigoclient.ui.permissions.groups;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageButton;
import android.widget.TextView;

import java.util.List;

import delit.piwigoclient.R;
import delit.piwigoclient.model.piwigo.Group;

public class GroupsListAdapter extends ArrayAdapter<Group> {

    private GroupActionListener listener;

    public GroupsListAdapter(Context context, List<Group> groups, GroupActionListener listener) {
        super(context, R.layout.checkable_list_text, R.id.txt, groups);
        this.listener = listener;
    }

    public boolean hasStableIds() {
        return true;
    }

    @Override
    public long getItemId(int position) {
        return getItem(position).getId();
    }

    @Override
    public View getView(final int position, View convertView, ViewGroup parent) {
//        View v = super.getView(position, view, parent); (just does a toString on the items.)
        //Override the toString text.
        View v;
        if (convertView == null) {
            LayoutInflater inflator = LayoutInflater.from(getContext());
            v = inflator.inflate(R.layout.actionable_list_item_layout, parent, false);
        } else {
            v = convertView;
        }
        final Group thisItem = getItem(position);
        TextView txtTitle = (TextView) v.findViewById(R.id.name);
        txtTitle.setText(thisItem.getName());

        TextView detailsTitle = (TextView) v.findViewById(R.id.details);
        detailsTitle.setText(String.format(getContext().getString(R.string.group_members_pattern), thisItem.getMemberCount()));

        ImageButton deleteButton = (ImageButton) v.findViewById(R.id.list_item_delete_button);
        deleteButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onDeleteItem(position, thisItem, v);
            }
        });

        return v;
    }

    private void onDeleteItem(int position, Group thisItem, View v) {
        listener.onDeleteItem(position, thisItem);
    }

    public interface GroupActionListener {
        void onDeleteItem(int position, Group thisItem);
    }
}
