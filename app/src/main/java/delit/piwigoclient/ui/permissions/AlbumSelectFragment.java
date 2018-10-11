package delit.piwigoclient.ui.permissions;

import android.content.Context;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;
import android.widget.TextView;

import org.greenrobot.eventbus.EventBus;

import java.util.ArrayList;
import java.util.HashSet;

import delit.piwigoclient.R;
import delit.piwigoclient.model.piwigo.CategoryItem;
import delit.piwigoclient.model.piwigo.CategoryItemStub;
import delit.piwigoclient.piwigoApi.BasicPiwigoResponseListener;
import delit.piwigoclient.piwigoApi.PiwigoResponseBufferingHandler;
import delit.piwigoclient.piwigoApi.handlers.AlbumGetSubAlbumNamesResponseHandler;
import delit.piwigoclient.ui.common.fragment.ListViewLongSetSelectFragment;
import delit.piwigoclient.ui.common.recyclerview.BaseRecyclerViewAdapterPreferences;
import delit.piwigoclient.ui.common.util.BundleUtils;
import delit.piwigoclient.ui.events.trackable.AlbumPermissionsSelectionCompleteEvent;

/**
 * Created by gareth on 26/05/17.
 */

public class AlbumSelectFragment extends ListViewLongSetSelectFragment<AlbumSelectionListAdapter, BaseRecyclerViewAdapterPreferences> {

    private static final String STATE_INDIRECT_SELECTION = "indirectSelection";
    private static final String STATE_AVAILABLE_ITEMS = "availableItems";
    private ArrayList<CategoryItemStub> availableItems;
    private HashSet<Long> indirectSelection;

    public static AlbumSelectFragment newInstance(ArrayList<CategoryItemStub> availableAlbums, BaseRecyclerViewAdapterPreferences prefs, int actionId, HashSet<Long> indirectSelection, HashSet<Long> initialSelection) {
        AlbumSelectFragment fragment = new AlbumSelectFragment();
        Bundle args = buildArgsBundle(prefs, actionId, initialSelection);
        if (indirectSelection != null) {
            BundleUtils.putLongHashSet(args, STATE_INDIRECT_SELECTION, new HashSet<Long>(indirectSelection));
        } else {
            BundleUtils.putLongHashSet(args, STATE_INDIRECT_SELECTION, new HashSet<Long>());
        }
        args.putParcelableArrayList(STATE_AVAILABLE_ITEMS, availableAlbums);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Bundle args = getArguments();
        if (args != null) {
            indirectSelection = BundleUtils.getLongHashSet(args, STATE_INDIRECT_SELECTION);
            availableItems = args.getParcelableArrayList(STATE_AVAILABLE_ITEMS);
        }
    }

    @Override
    protected BaseRecyclerViewAdapterPreferences createEmptyPrefs() {
        return new BaseRecyclerViewAdapterPreferences();
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putParcelableArrayList(STATE_AVAILABLE_ITEMS, availableItems);
        BundleUtils.putLongHashSet(outState, STATE_INDIRECT_SELECTION, indirectSelection);
    }


    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {

        View v = super.onCreateView(inflater, container, savedInstanceState);

        if (isServerConnectionChanged()) {
            // immediately leave this screen.
            getFragmentManager().popBackStack();
            return null;
        }

        if (isNotAuthorisedToAlterState()) {
            getViewPrefs().readonly();
        }

        if (savedInstanceState != null) {
            availableItems = savedInstanceState.getParcelableArrayList(STATE_AVAILABLE_ITEMS);
            indirectSelection = BundleUtils.getLongHashSet(savedInstanceState, STATE_INDIRECT_SELECTION);
        }

        return v;
    }

    @Override
    protected String buildPageHeading() {
        return getString(R.string.access_rights_heading);
    }

    @Override
    protected void setPageHeading(TextView headingField) {
        headingField.setVisibility(View.GONE);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (isServerConnectionChanged()) {
            return;
        }
        rerunRetrievalForFailedPages();
    }

    @Override
    protected void rerunRetrievalForFailedPages() {
        if (availableItems == null) {
            //TODO FEATURE: Support albums list paging (load page size from settings)
            addActiveServiceCall(R.string.progress_loading_albums, new AlbumGetSubAlbumNamesResponseHandler(CategoryItem.ROOT_ALBUM.getId(), true).invokeAsync(getContext()));
        } else if (getListAdapter() == null) {
            AlbumSelectionListAdapter availableItemsAdapter = new AlbumSelectionListAdapter(getContext(), availableItems, indirectSelection, getViewPrefs());
            ListView listView = getList();
            availableItemsAdapter.linkToListView(listView, getInitialSelection(), getCurrentSelection());
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
        if (isVisible()) {
            getFragmentManager().popBackStackImmediate();
        }
    }

    @Override
    protected BasicPiwigoResponseListener buildPiwigoResponseListener(Context context) {
        return new CustomPiwigoResponseListener();
    }

    private void onSubGalleriesLoaded(final AlbumGetSubAlbumNamesResponseHandler.PiwigoGetSubAlbumNamesResponse response) {
        getUiHelper().hideProgressIndicator();
//        if (response.getItemsOnPage() == response.getPageSize()) {
//            //TODO FEATURE: Support groups paging
//            getUiHelper().showOrQueueMessage(R.string.alert_title_error_too_many_users, getString(R.string.alert_error_too_many_users_message));
//        }
        availableItems = response.getAlbumNames();
        rerunRetrievalForFailedPages();
    }

    private class CustomPiwigoResponseListener extends BasicPiwigoResponseListener {
        @Override
        public void onAfterHandlePiwigoResponse(PiwigoResponseBufferingHandler.Response response) {
            if (response instanceof AlbumGetSubAlbumNamesResponseHandler.PiwigoGetSubAlbumNamesResponse) {
                onSubGalleriesLoaded((AlbumGetSubAlbumNamesResponseHandler.PiwigoGetSubAlbumNamesResponse) response);
            } else {
                onListItemLoadFailed();
            }
        }
    }
}
