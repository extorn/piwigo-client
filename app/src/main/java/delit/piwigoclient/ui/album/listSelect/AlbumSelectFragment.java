package delit.piwigoclient.ui.album.listSelect;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.ListView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

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
import delit.piwigoclient.ui.events.trackable.AlbumSelectionCompleteEvent;

/**
 * Created by gareth on 26/05/17.
 */
//TODO - Migrate to using ASAP - ListViewLongSelectableSetSelectFragment
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
        outState.putParcelableArrayList(STATE_AVAILABLE_ITEMS, availableAlbums);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {

        View v = super.onCreateView(inflater, container, savedInstanceState);

        if (savedInstanceState != null) {
            availableAlbums = savedInstanceState.getParcelableArrayList(STATE_AVAILABLE_ITEMS);
            createEmptyPrefs().loadFromBundle(savedInstanceState);
        }

        return v;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        if (isServerConnectionChanged()) {
            // immediately leave this screen.
            getFragmentManager().popBackStack();
        }
    }

    @Override
    protected String buildPageHeading() {
        return getString(R.string.albums_heading);
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
        if (availableAlbums == null) {
            addActiveServiceCall(R.string.progress_loading_albums, new AlbumGetSubAlbumNamesResponseHandler(CategoryItem.ROOT_ALBUM.getId(), true));
        } else if (getListAdapter() == null) {
            synchronized (this) {
                AvailableAlbumsListAdapter availableGalleries = new AvailableAlbumsListAdapter(getViewPrefs(), CategoryItem.ROOT_ALBUM, getContext());
                availableGalleries.clear();
                // leaving the root album out prevents it's selection (not wanted).
//            availableGalleries.add(CategoryItemStub.ROOT_GALLERY);
                availableGalleries.addAll(availableAlbums);
                ListView listView = getList();
                listView.setAdapter(availableGalleries);
                // clear checked items
                listView.clearChoices();
                if (isMultiSelectEnabled()) {
                    listView.setChoiceMode(AbsListView.CHOICE_MODE_MULTIPLE);
                } else {
                    listView.setChoiceMode(AbsListView.CHOICE_MODE_SINGLE);
                }

                HashSet<Long> currentSelection = getCurrentSelection();
                if (currentSelection != null) {
                    for (Long selectedAlbum : currentSelection) {
                        int itemPos = availableGalleries.getPosition(selectedAlbum);
                        if (itemPos >= 0) {
                            listView.setItemChecked(itemPos, true);
                        }
                    }
                }
                setListAdapter(availableGalleries);
                setAppropriateComponentState();
            }
        }
    }

    @Override
    protected void onCancelChanges() {
        onSelectActionComplete(getInitialSelection());
    }

    @Override
    protected void onSelectActionComplete(HashSet<Long> selectedIdsSet) {
        synchronized (this) {
            // need to synchronise as the list adapter may be being rebuilt.
            HashSet<CategoryItemStub> selectedAlbums = new HashSet<>(selectedIdsSet.size());
            AvailableAlbumsListAdapter listAdapter = getListAdapter();
            if (listAdapter != null) {
                for (Long selectedId : selectedIdsSet) {
                    selectedAlbums.add(listAdapter.getItemById(selectedId));
                }
            }
            EventBus.getDefault().post(new AlbumSelectionCompleteEvent(getActionId(), selectedIdsSet, selectedAlbums));
            // now pop this screen off the stack.
            if (isVisible()) {
                getFragmentManager().popBackStackImmediate();
            }
        }
    }

    @Override
    protected BasicPiwigoResponseListener buildPiwigoResponseListener(Context context) {
        return new CustomPiwigoResponseListener();
    }

    private void onAlbumsLoaded(final AlbumGetSubAlbumNamesResponseHandler.PiwigoGetSubAlbumNamesResponse response) {
        getUiHelper().hideProgressIndicator();
        availableAlbums = response.getAlbumNames();
        rerunRetrievalForFailedPages();
    }

    private class CustomPiwigoResponseListener extends BasicPiwigoResponseListener {
        @Override
        public void onAfterHandlePiwigoResponse(PiwigoResponseBufferingHandler.Response response) {
            if (response instanceof AlbumGetSubAlbumNamesResponseHandler.PiwigoGetSubAlbumNamesResponse) {
                onAlbumsLoaded((AlbumGetSubAlbumNamesResponseHandler.PiwigoGetSubAlbumNamesResponse) response);
            } else {
                onListItemLoadFailed();
            }
        }
    }
}
