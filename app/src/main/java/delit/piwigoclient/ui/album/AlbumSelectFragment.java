package delit.piwigoclient.ui.album;

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
import delit.piwigoclient.piwigoApi.PiwigoResponseBufferingHandler;
import delit.piwigoclient.piwigoApi.handlers.AlbumGetSubAlbumNamesResponseHandler;
import delit.piwigoclient.ui.common.ListViewLongSetSelectFragment;
import delit.piwigoclient.ui.common.recyclerview.BaseRecyclerViewAdapterPreferences;
import delit.piwigoclient.ui.events.trackable.AlbumSelectionCompleteEvent;

/**
 * Created by gareth on 26/05/17.
 */
public class AlbumSelectFragment extends ListViewLongSetSelectFragment<AvailableAlbumsListAdapter, AvailableAlbumsListAdapter.AvailableAlbumsListAdapterPreferences> {

    private static final String STATE_AVAILABLE_ITEMS = "availableItems";
    private ArrayList<CategoryItemStub> availableAlbums;

    public static AlbumSelectFragment newInstance(AvailableAlbumsListAdapter.AvailableAlbumsListAdapterPreferences prefs, int actionId, HashSet<Long> initialSelection) {
        AlbumSelectFragment fragment = new AlbumSelectFragment();
        fragment.setArguments(buildArgsBundle(prefs, actionId, initialSelection));
        return fragment;
    }

    @Override
    protected AvailableAlbumsListAdapter.AvailableAlbumsListAdapterPreferences createEmptyPrefs() {
        return new AvailableAlbumsListAdapter.AvailableAlbumsListAdapterPreferences();
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        getViewPrefs().storeToBundle(outState);
        outState.putSerializable(STATE_AVAILABLE_ITEMS, availableAlbums);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {

        View v = super.onCreateView(inflater, container, savedInstanceState);

        if(isServerConnectionChanged()) {
            // immediately leave this screen.
            getFragmentManager().popBackStack();
            return null;
        }

        if (savedInstanceState != null) {
            availableAlbums = (ArrayList<CategoryItemStub>) savedInstanceState.getSerializable(STATE_AVAILABLE_ITEMS);
            createEmptyPrefs().loadFromBundle(savedInstanceState);
        }

        return v;
    }

    @Override
    protected void setPageHeading(TextView headingField) {
        headingField.setText(R.string.albums_heading);
        headingField.setVisibility(View.VISIBLE);
    }

    @Override
    public void onResume() {
        super.onResume();

        if(isServerConnectionChanged()) {
            return;
        }

        populateListWithItems();
    }

    @Override
    protected void populateListWithItems() {
        if (availableAlbums == null) {
            addActiveServiceCall(R.string.progress_loading_albums, new AlbumGetSubAlbumNamesResponseHandler(CategoryItem.ROOT_ALBUM.getId(), true).invokeAsync(getContext()));
        } else if(getListAdapter() == null) {
            AvailableAlbumsListAdapter availableGalleries = new AvailableAlbumsListAdapter(getViewPrefs(), CategoryItem.ROOT_ALBUM, getContext());
            availableGalleries.clear();
            // leaving the root album out prevents it's selection (not wanted).
//            availableGalleries.add(CategoryItemStub.ROOT_GALLERY);
            availableGalleries.addAll(availableAlbums);
            ListView listView = getList();
            listView.setAdapter(availableGalleries);
            // clear checked items
            listView.clearChoices();
            if(isMultiSelectEnabled()) {
                listView.setChoiceMode(AbsListView.CHOICE_MODE_MULTIPLE);
            } else {
                listView.setChoiceMode(AbsListView.CHOICE_MODE_SINGLE);
            }

            for(Long selectedAlbum : getCurrentSelection()) {
                int itemPos = availableGalleries.getPosition(selectedAlbum);
                if(itemPos >= 0) {
                    listView.setItemChecked(itemPos, true);
                }
            }
            setListAdapter(availableGalleries);
            setAppropriateComponentState();
        }
    }



    @Override
    protected void onSelectActionComplete(HashSet<Long> selectedIdsSet) {
        HashSet<CategoryItemStub> selectedAlbums = new HashSet<>(selectedIdsSet.size());
        AvailableAlbumsListAdapter listAdapter = getListAdapter();
        for(Long selectedId : selectedIdsSet) {
            selectedAlbums.add(listAdapter.getItemById(selectedId));
        }
        EventBus.getDefault().post(new AlbumSelectionCompleteEvent(getActionId(), selectedIdsSet, selectedAlbums));
        // now pop this screen off the stack.
        if(isVisible()) {
            getFragmentManager().popBackStackImmediate();
        }
    }

    @Override
    protected BasicPiwigoResponseListener buildPiwigoResponseListener(Context context) {
        return new CustomPiwigoResponseListener();
    }

    private class CustomPiwigoResponseListener extends BasicPiwigoResponseListener {
        @Override
        public void onAfterHandlePiwigoResponse(PiwigoResponseBufferingHandler.Response response) {
            if (response instanceof PiwigoResponseBufferingHandler.PiwigoGetSubAlbumNamesResponse) {
                onAlbumsLoaded((PiwigoResponseBufferingHandler.PiwigoGetSubAlbumNamesResponse) response);
            } else {
                onListItemLoadFailed();
            }
        }
    }

    private void onAlbumsLoaded(final PiwigoResponseBufferingHandler.PiwigoGetSubAlbumNamesResponse response) {
        getUiHelper().dismissProgressDialog();
        availableAlbums = response.getAlbumNames();
        populateListWithItems();
    }
}
