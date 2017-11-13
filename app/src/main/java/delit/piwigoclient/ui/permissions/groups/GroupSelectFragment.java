package delit.piwigoclient.ui.permissions.groups;

import android.content.Context;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.ListView;
import android.widget.TextView;

import org.greenrobot.eventbus.EventBus;

import java.util.ArrayList;
import java.util.HashSet;

import delit.piwigoclient.R;
import delit.piwigoclient.model.piwigo.Group;
import delit.piwigoclient.piwigoApi.BasicPiwigoResponseListener;
import delit.piwigoclient.piwigoApi.PiwigoAccessService;
import delit.piwigoclient.piwigoApi.PiwigoResponseBufferingHandler;
import delit.piwigoclient.ui.common.LongSetSelectFragment;
import delit.piwigoclient.ui.events.trackable.GroupSelectionCompleteEvent;

/**
 * Created by gareth on 26/05/17.
 */

public class GroupSelectFragment extends LongSetSelectFragment<GroupsSelectionListAdapter> {

    private static final String STATE_AVAILABLE_ITEMS = "availableItems";
    private ArrayList<Group> availableItems;

    public static GroupSelectFragment newInstance(boolean multiSelectEnabled, boolean allowEditing, int actionId, HashSet<Long> initialSelection) {
        GroupSelectFragment fragment = new GroupSelectFragment();
        fragment.setArguments(buildArgsBundle(multiSelectEnabled, allowEditing, actionId, initialSelection));
        return fragment;
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putSerializable(STATE_AVAILABLE_ITEMS, availableItems);
    }



    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {

        View v = super.onCreateView(inflater, container, savedInstanceState);

        if (savedInstanceState != null) {
            availableItems = (ArrayList) savedInstanceState.getSerializable(STATE_AVAILABLE_ITEMS);
        }

        return v;
    }

    @Override
    protected void setPageHeading(TextView headingField) {
        headingField.setText(R.string.groups_heading);
        headingField.setVisibility(View.VISIBLE);
    }

    @Override
    public void onResume() {
        super.onResume();
        populateListWithItems();
    }

    @Override
    protected void populateListWithItems() {
        if (availableItems == null) {
            //TODO FEATURE: Support groups paging (load page size from settings)
            addActiveServiceCall(R.string.progress_loading_groups, PiwigoAccessService.startActionGetGroupsList(0, 100, this.getContext()));
        } else if(getListAdapter() == null) {
            //TODO use list item layout as per AvailableAlbumsListAdapter
//            int listItemLayout = isMultiSelectEnabled()? android.R.layout.simple_list_item_multiple_choice : android.R.layout.simple_list_item_single_choice;

            GroupsSelectionListAdapter availableItemsAdapter = new GroupsSelectionListAdapter(getContext(), availableItems, isEditingEnabled());
            ListView listView = getList();
            listView.setAdapter(availableItemsAdapter);
            listView.requestLayout();
            if(isMultiSelectEnabled()) {
                listView.setChoiceMode(AbsListView.CHOICE_MODE_MULTIPLE);
            } else {
                listView.setChoiceMode(AbsListView.CHOICE_MODE_SINGLE);
            }

            for(Long selectedGroup : getCurrentSelection()) {
                int itemPos = availableItemsAdapter.getPosition(selectedGroup);
                if(itemPos >= 0) {
                    listView.setItemChecked(itemPos, true);
                }
            }

            setListAdapter(availableItemsAdapter);
            setAppropriateComponentState();
        }
    }

    @Override
    protected void onSelectActionComplete(HashSet<Long> selectedIdsSet) {
        HashSet<Group> selectedItems = new HashSet<>(selectedIdsSet.size());
        GroupsSelectionListAdapter listAdapter = getListAdapter();
        for(Long selectedId : selectedIdsSet) {
            selectedItems.add(listAdapter.getItemById(selectedId));
        }
        EventBus.getDefault().post(new GroupSelectionCompleteEvent(getActionId(), selectedIdsSet, selectedItems));
        // now pop this screen off the stack.
        getFragmentManager().popBackStackImmediate();
    }

    @Override
    protected BasicPiwigoResponseListener buildPiwigoResponseListener(Context context) {
        return new CustomPiwigoResponseListener();
    }

    private class CustomPiwigoResponseListener extends BasicPiwigoResponseListener {
        @Override
        public void onAfterHandlePiwigoResponse(PiwigoResponseBufferingHandler.Response response) {
            if (response instanceof PiwigoResponseBufferingHandler.PiwigoGetGroupsListRetrievedResponse) {
                onGroupsLoaded((PiwigoResponseBufferingHandler.PiwigoGetGroupsListRetrievedResponse) response);
            } else {
                onListItemLoadFailed();
            }
        }
    }

    public void onGroupsLoaded(final PiwigoResponseBufferingHandler.PiwigoGetGroupsListRetrievedResponse response) {
        getUiHelper().dismissProgressDialog();
        if (response.getItemsOnPage() == response.getPageSize()) {
            //TODO FEATURE: Support groups paging
            getUiHelper().showOrQueueDialogMessage(R.string.alert_title_error_too_many_groups, getString(R.string.alert_error_too_many_groups_message));
        }
        availableItems = new ArrayList<>(response.getGroups());
        populateListWithItems();
    }
}
