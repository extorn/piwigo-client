package delit.piwigoclient.ui.permissions.users;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageButton;
import android.widget.TextView;

import java.util.Arrays;
import java.util.List;

import delit.piwigoclient.R;
import delit.piwigoclient.model.piwigo.User;

public class UsersListAdapter extends ArrayAdapter<User> {

    private final List<String> userTypes;
    private final List<String> userTypeValues;
    UserActionListener listener;

    public UsersListAdapter(Context context, List<User> users, UserActionListener listener) {
        super(context, R.layout.checkable_list_text, R.id.txt, users);
        this.listener = listener;
        userTypes = Arrays.asList(context.getResources().getStringArray(R.array.user_types_array));
        userTypeValues = Arrays.asList(context.getResources().getStringArray(R.array.user_types_values_array));
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
        final User thisItem = getItem(position);
        TextView txtTitle = (TextView) v.findViewById(R.id.name);
        txtTitle.setText(thisItem.getUsername());

        TextView detailsTitle = (TextView) v.findViewById(R.id.details);

        String userType = userTypes.get(userTypeValues.indexOf(thisItem.getUserType()));
        detailsTitle.setText(userType);

        ImageButton deleteButton = (ImageButton) v.findViewById(R.id.list_item_delete_button);
        deleteButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onDeleteItem(position, thisItem, v);
            }
        });

        return v;
    }

    private void onDeleteItem(int position, User thisItem, View v) {
        listener.onDeleteItem(position, thisItem);
    }

    public interface UserActionListener {
        void onDeleteItem(int position, User thisItem);
    }
}
