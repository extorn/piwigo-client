package delit.piwigoclient.ui.album.listSelect;

import android.content.Context;
import android.os.Bundle;
import android.util.Log;
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

import delit.libs.core.util.Logging;
import delit.piwigoclient.R;
import delit.piwigoclient.model.piwigo.CategoryItem;
import delit.piwigoclient.model.piwigo.CategoryItemStub;
import delit.piwigoclient.piwigoApi.BasicPiwigoResponseListener;
import delit.piwigoclient.piwigoApi.PiwigoResponseBufferingHandler;
import delit.piwigoclient.piwigoApi.handlers.AlbumGetSubAlbumNamesResponseHandler;
import delit.piwigoclient.ui.common.FragmentUIHelper;
import delit.piwigoclient.ui.common.fragment.ListViewLongSetSelectFragment;
import delit.piwigoclient.ui.events.trackable.AlbumSelectionCompleteEvent;
import delit.piwigoclient.ui.permissions.AlbumSelectionListAdapterPreferences;

/**
 * Created by gareth on 26/05/17.
 */
//TODO - Migrate to using ASAP - ListViewLongSelectableSetSelectFragment
public class AlbumSelectFragment<F extends AlbumSelectFragment<F,FUIH>,FUIH extends FragmentUIHelper<FUIH,F>> extends ListViewLongSetSelectFragment<AvailableAlbumsListAdapter, AlbumSelectionListAdapterPreferences> {

    private static final String STATE_AVAILABLE_ITEMS = "availableItems";
    private static final String TAG = "AlbumSelFrag";
    private ArrayList<CategoryItemStub> availableAlbums;

    public static AlbumSelectFragment newInstance(AlbumSelectionListAdapterPreferences prefs, int actionId, HashSet<Long> initialSelection) {
        AlbumSelectFragment<?,?> fragment = new AlbumSelectFragment<>();
        fragment.setTheme(R.style.Theme_App_EditPages);
        fragment.setArguments(buildArgsBundle(prefs, actionId, initialSelection));
        return fragment;
    }

    @Override
    protected AlbumSelectionListAdapterPreferences loadPreferencesFromBundle(Bundle bundle) {
        return new AlbumSelectionListAdapterPreferences(bundle);
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
            loadPreferencesFromBundle(savedInstanceState);
        }

        return v;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        if (isServerConnectionChanged()) {
            // immediately leave this screen.
            Logging.log(Log.INFO, TAG, "removing from activity as server connection has changed");
            getParentFragmentManager().popBackStack();
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
                AvailableAlbumsListAdapter availableGalleries = new AvailableAlbumsListAdapter(getViewPrefs(), CategoryItem.ROOT_ALBUM, requireContext());
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
                } else {
                    HashSet<Long> initialSelection = getInitialSelection();
                    if (initialSelection != null) {
                        for (Long selectedAlbum : initialSelection) {
                            int itemPos = availableGalleries.getPosition(selectedAlbum);
                            if (itemPos >= 0) {
                                listView.setItemChecked(itemPos, true);
                            }
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
                Logging.log(Log.INFO, TAG, "removing from activity immediately as select action complete");
                getParentFragmentManager().popBackStackImmediate();
            }
        }
    }

    @Override
    protected BasicPiwigoResponseListener<FUIH,F> buildPiwigoResponseListener(Context context) {
        return new CustomPiwigoResponseListener<>();
    }

    void onAlbumsLoaded(final AlbumGetSubAlbumNamesResponseHandler.PiwigoGetSubAlbumNamesResponse response) {
        getUiHelper().hideProgressIndicator();
        availableAlbums = response.getAlbumNames();
        rerunRetrievalForFailedPages();
    }

    private static class CustomPiwigoResponseListener<F extends AlbumSelectFragment<F,FUIH>,FUIH extends FragmentUIHelper<FUIH,F>> extends BasicPiwigoResponseListener<FUIH,F> {


        @Override
        public void onAfterHandlePiwigoResponse(PiwigoResponseBufferingHandler.Response response) {
            if (response instanceof AlbumGetSubAlbumNamesResponseHandler.PiwigoGetSubAlbumNamesResponse) {
                getParent().onAlbumsLoaded((AlbumGetSubAlbumNamesResponseHandler.PiwigoGetSubAlbumNamesResponse) response);
            } else {
                getParent().onListItemLoadFailed();
            }
        }
    }
}
