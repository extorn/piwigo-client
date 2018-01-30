package delit.piwigoclient.ui.permissions;

import android.content.Context;
import android.os.Bundle;
import android.support.annotation.NonNull;
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
import delit.piwigoclient.model.piwigo.CategoryItem;
import delit.piwigoclient.model.piwigo.CategoryItemStub;
import delit.piwigoclient.piwigoApi.BasicPiwigoResponseListener;
import delit.piwigoclient.piwigoApi.PiwigoAccessService;
import delit.piwigoclient.piwigoApi.PiwigoResponseBufferingHandler;
import delit.piwigoclient.ui.common.ListViewLongSetSelectFragment;
import delit.piwigoclient.ui.events.trackable.AlbumPermissionsSelectionCompleteEvent;

/**
 * Created by gareth on 26/05/17.
 */

public class AlbumSelectFragment extends ListViewLongSetSelectFragment<AlbumSelectionListAdapter> {

    private static final String STATE_INDIRECT_SELECTION = "indirectSelection";
    private static final String STATE_AVAILABLE_ITEMS = "availableItems";
    private ArrayList<CategoryItemStub> availableItems;
    private HashSet<Long> indirectSelection;

    public static AlbumSelectFragment newInstance(ArrayList<CategoryItemStub> availableAlbums, boolean multiSelectEnabled, boolean allowEditing, int actionId, HashSet<Long> indirectSelection, HashSet<Long> initialSelection) {
        AlbumSelectFragment fragment = new AlbumSelectFragment();
        Bundle args = buildArgsBundle(multiSelectEnabled, allowEditing, actionId, initialSelection);
        if(indirectSelection != null) {
            args.putSerializable(STATE_INDIRECT_SELECTION, new HashSet<>(indirectSelection));
        } else {
            args.putSerializable(STATE_INDIRECT_SELECTION, new HashSet<>());
        }
        args.putSerializable(STATE_AVAILABLE_ITEMS, availableAlbums);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Bundle args = getArguments();
        if (args != null) {
            indirectSelection = (HashSet<Long>) args.getSerializable(STATE_INDIRECT_SELECTION);
            availableItems = (ArrayList<CategoryItemStub>) args.getSerializable(STATE_AVAILABLE_ITEMS);
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putSerializable(STATE_AVAILABLE_ITEMS, availableItems);
        outState.putSerializable(STATE_INDIRECT_SELECTION, indirectSelection);
    }



    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {

        View v = super.onCreateView(inflater, container, savedInstanceState);

        if (savedInstanceState != null) {
            availableItems = (ArrayList) savedInstanceState.getSerializable(STATE_AVAILABLE_ITEMS);
            indirectSelection = (HashSet<Long>) savedInstanceState.getSerializable(STATE_INDIRECT_SELECTION);
        }

        return v;
    }

    @Override
    protected void setPageHeading(TextView headingField) {
        headingField.setText(R.string.access_rights_heading);
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
            //TODO FEATURE: Support albums list paging (load page size from settings)
            addActiveServiceCall(R.string.progress_loading_albums, PiwigoAccessService.startActionGetSubCategoryNames(CategoryItem.ROOT_ALBUM.getId(), true, getContext()));
        } else if(getListAdapter() == null) {
            //TODO use list item layout as per AvailableAlbumsListAdapter
//            int listItemLayout = isMultiSelectEnabled()? android.R.layout.simple_list_item_multiple_choice : android.R.layout.simple_list_item_single_choice;
            AlbumSelectionListAdapter availableItemsAdapter = new AlbumSelectionListAdapter(getContext(), availableItems, indirectSelection, isEditingEnabled());
            ListView listView = getList();
            listView.setAdapter(availableItemsAdapter);
            listView.requestLayout();
            if(isMultiSelectEnabled()) {
                listView.setChoiceMode(AbsListView.CHOICE_MODE_MULTIPLE);
            } else {
                listView.setChoiceMode(AbsListView.CHOICE_MODE_SINGLE);
            }

            for(Long selectedItemId : getCurrentSelection()) {
                int itemPos = availableItemsAdapter.getPosition(selectedItemId);
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
//        HashSet<CategoryItemStub> selectedItems = new HashSet<>(selectedIdsSet.size());
//        AlbumSelectionListAdapter listAdapter = getListAdapter();
//        for(Long selectedId : selectedIdsSet) {
//            selectedItems.add(listAdapter.getItemById(selectedId));
//        }
        EventBus.getDefault().post(new AlbumPermissionsSelectionCompleteEvent(getActionId(), selectedIdsSet));
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
            if (response instanceof PiwigoResponseBufferingHandler.PiwigoGetSubAlbumNamesResponse) {
                onSubGalleriesLoaded((PiwigoResponseBufferingHandler.PiwigoGetSubAlbumNamesResponse) response);
            } else {
                onListItemLoadFailed();
            }
        }
    }

    public void onSubGalleriesLoaded(final PiwigoResponseBufferingHandler.PiwigoGetSubAlbumNamesResponse response) {
        getUiHelper().dismissProgressDialog();
//        if (response.getItemsOnPage() == response.getPageSize()) {
//            //TODO FEATURE: Support groups paging
//            getUiHelper().showOrQueueDialogMessage(R.string.alert_title_error_too_many_users, getString(R.string.alert_error_too_many_users_message));
//        }
        availableItems = response.getAlbumNames();
        populateListWithItems();
    }
}
