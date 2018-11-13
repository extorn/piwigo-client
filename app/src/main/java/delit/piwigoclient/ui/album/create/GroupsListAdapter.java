package delit.piwigoclient.ui.album.create;

import android.content.Context;
import androidx.annotation.NonNull;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import java.util.List;

import delit.piwigoclient.R;
import delit.piwigoclient.model.piwigo.Group;

public class GroupsListAdapter extends ArrayAdapter<Group> {

    public GroupsListAdapter(Context context, List<Group> groups) {
        super(context, R.layout.layout_checkable_list_text, R.id.txt, groups);
    }

    public boolean hasStableIds() {
        return true;
    }

    @Override
    public long getItemId(int position) {
        return getItem(position).getId();
    }

    @NonNull
    @Override
    public View getView(int position, View convertView, @NonNull ViewGroup parent) {
//        View v = super.getView(position, view, parent); (just does a toString on the items.)
        //Override the toString text.
        View v;
        if (convertView == null) {
            LayoutInflater inflator = LayoutInflater.from(getContext());
            v = inflator.inflate(R.layout.layout_checkable_list_text, parent, false);
        } else {
            v = convertView;
        }
        Group thisItem = getItem(position);
        TextView txtTitle = v.findViewById(R.id.txt);
        txtTitle.setText(thisItem.getName());
        return v;
    }
}
