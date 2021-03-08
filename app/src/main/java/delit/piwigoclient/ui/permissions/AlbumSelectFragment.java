package delit.piwigoclient.ui.permissions;

import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.greenrobot.eventbus.EventBus;

import java.util.ArrayList;
import java.util.HashSet;

import delit.libs.core.util.Logging;
import delit.libs.ui.util.BundleUtils;
import delit.piwigoclient.R;
import delit.piwigoclient.model.piwigo.CategoryItemStub;
import delit.piwigoclient.model.piwigo.StaticCategoryItem;
import delit.piwigoclient.piwigoApi.BasicPiwigoResponseListener;
import delit.piwigoclient.piwigoApi.PiwigoResponseBufferingHandler;
import delit.piwigoclient.piwigoApi.handlers.AlbumGetChildAlbumNamesResponseHandler;
import delit.piwigoclient.ui.common.FragmentUIHelper;
import delit.piwigoclient.ui.common.fragment.ListViewLongSelectableSetSelectFragment;
import delit.piwigoclient.ui.events.trackable.AlbumPermissionsSelectionCompleteEvent;

/**
 * Created by gareth on 26/05/17.
 */

public class AlbumSelectFragment<F extends AlbumSelectFragment<F,FUIH>, FUIH extends FragmentUIHelper<FUIH,F>> extends ListViewLongSelectableSetSelectFragment<F,FUIH,AlbumSelectionListAdapter, AlbumSelectionListAdapterPreferences> {

    private static final String STATE_INDIRECT_SELECTION = "indirectSelection";
    private static final String STATE_AVAILABLE_ITEMS = "availableItems";
    private static final String TAG = "AlbumSelect";
    private ArrayList<CategoryItemStub> availableItems;
    private HashSet<Long> indirectSelection;

    public static <F extends AlbumSelectFragment<F,FUIH>, FUIH extends FragmentUIHelper<FUIH,F>> F newInstance(ArrayList<CategoryItemStub> availableAlbums, AlbumSelectionListAdapterPreferences prefs, int actionId, HashSet<Long> indirectSelection, HashSet<Long> initialSelection) {
        F fragment = (F) new AlbumSelectFragment<>();
        fragment.setTheme(R.style.Theme_App_EditPages);
        Bundle args = buildArgsBundle(prefs, actionId, initialSelection);
        if (indirectSelection != null) {
            BundleUtils.putLongHashSet(args, STATE_INDIRECT_SELECTION, new HashSet<>(indirectSelection));
        } else {
            BundleUtils.putLongHashSet(args, STATE_INDIRECT_SELECTION, new HashSet<>());
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
    protected AlbumSelectionListAdapterPreferences loadPreferencesFromBundle(Bundle bundle) {
        return new AlbumSelectionListAdapterPreferences(bundle);
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
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        if (isServerConnectionChanged()) {
            // immediately leave this screen.
            Logging.log(Log.INFO, TAG, "Unable to show album select page - removing from activity");
            getParentFragmentManager().popBackStack();
        }
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
            addActiveServiceCall(R.string.progress_loading_albums, new AlbumGetChildAlbumNamesResponseHandler(StaticCategoryItem.ROOT_ALBUM.getId(), !getViewPrefs().isFlattenAlbumHierarchy()));
        } else if (getListAdapter() == null) {
            AlbumSelectionListAdapter availableItemsAdapter = new AlbumSelectionListAdapter(availableItems, indirectSelection, getViewPrefs());
            ListView listView = getList();
            availableItemsAdapter.linkToListView(listView, getInitialSelection(), getCurrentSelection());
            setListAdapter(availableItemsAdapter);
            setAppropriateComponentState();
        }
    }

    @Override
    protected void onUserActionCancelFileSelection() {
        super.onUserActionCancelFileSelection();
    }

    @Override
    protected void onSelectActionComplete(HashSet<Long> selectedIdsSet) {
        int selectedItemCount = selectedIdsSet == null ? 0 : selectedIdsSet.size();
        HashSet<String> selectedItemNamesSet = new HashSet<>(selectedItemCount);
        HashSet<Long> selectedItemIdsSet = new HashSet<>(selectedItemCount);

        if (selectedIdsSet != null) {
            AlbumSelectionListAdapter listAdapter = getListAdapter();
            for (Long selectedId : selectedIdsSet) {
                selectedItemNamesSet.add(listAdapter.getItemById(selectedId).getName());
                selectedItemIdsSet.add(selectedId);
            }
        }
        EventBus.getDefault().post(new AlbumPermissionsSelectionCompleteEvent(getActionId(), selectedItemIdsSet, selectedItemNamesSet));

        // now pop this screen off the stack.
        if (isVisible()) {
            Logging.log(Log.INFO, TAG, "removing from activity immediately as select action complete");
            getParentFragmentManager().popBackStackImmediate();
        }
    }

    @Override
    protected BasicPiwigoResponseListener<FUIH,F> buildPiwigoResponseListener(Context context) {
        return new CustomPiwigoResponseListener<>();
    }

    protected void onSubGalleriesLoaded(final AlbumGetChildAlbumNamesResponseHandler.PiwigoGetSubAlbumNamesResponse response) {
        getUiHelper().hideProgressIndicator();
//        if (response.getItemsOnPage() == response.getPageSize()) {
//            //TODO FEATURE: Support groups paging
//            getUiHelper().showOrQueueMessage(R.string.alert_title_error_too_many_users, getString(R.string.alert_error_too_many_users_message));
//        }
        availableItems = response.getAlbumNames();
        rerunRetrievalForFailedPages();
    }

    private static class CustomPiwigoResponseListener<F extends AlbumSelectFragment<F,FUIH>, FUIH extends FragmentUIHelper<FUIH,F>> extends BasicPiwigoResponseListener<FUIH,F> {
        @Override
        public void onAfterHandlePiwigoResponse(PiwigoResponseBufferingHandler.Response response) {
            if (response instanceof AlbumGetChildAlbumNamesResponseHandler.PiwigoGetSubAlbumNamesResponse) {
                getParent().onSubGalleriesLoaded((AlbumGetChildAlbumNamesResponseHandler.PiwigoGetSubAlbumNamesResponse) response);
            } else {
                getParent().onListItemLoadFailed();
            }
        }
    }
}
