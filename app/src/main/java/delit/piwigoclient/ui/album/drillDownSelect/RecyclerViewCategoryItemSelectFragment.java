package delit.piwigoclient.ui.album.drillDownSelect;

import android.content.Context;
import android.os.Bundle;
import android.os.Parcelable;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.GridLayoutManager;

import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

import delit.libs.core.util.Logging;
import delit.libs.ui.util.BundleUtils;
import delit.libs.ui.view.AbstractBreadcrumbsView;
import delit.piwigoclient.R;
import delit.piwigoclient.business.AlbumViewPreferences;
import delit.piwigoclient.business.ConnectionPreferences;
import delit.piwigoclient.model.piwigo.CategoryItem;
import delit.piwigoclient.model.piwigo.CategoryItemStub;
import delit.piwigoclient.model.piwigo.PiwigoSessionDetails;
import delit.piwigoclient.model.piwigo.PiwigoUtils;
import delit.piwigoclient.model.piwigo.StaticCategoryItem;
import delit.piwigoclient.piwigoApi.BasicPiwigoResponseListener;
import delit.piwigoclient.piwigoApi.PiwigoResponseBufferingHandler;
import delit.piwigoclient.piwigoApi.handlers.AbstractPiwigoWsResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.AlbumGetChildAlbumsAdminResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.AlbumGetChildAlbumsResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.CommunityGetChildAlbumNamesResponseHandler;
import delit.piwigoclient.ui.album.CategoryBreadcrumbsView;
import delit.piwigoclient.ui.common.BackButtonHandler;
import delit.piwigoclient.ui.common.FragmentUIHelper;
import delit.piwigoclient.ui.common.fragment.LongSetSelectFragment;
import delit.piwigoclient.ui.common.fragment.RecyclerViewLongSetSelectFragment;
import delit.piwigoclient.ui.events.trackable.AlbumCreateNeededEvent;
import delit.piwigoclient.ui.events.trackable.AlbumCreatedEvent;
import delit.piwigoclient.ui.events.trackable.ExpandingAlbumSelectionCompleteEvent;

public class RecyclerViewCategoryItemSelectFragment<F extends RecyclerViewCategoryItemSelectFragment<F,FUIH>,FUIH extends FragmentUIHelper<FUIH,F>> extends RecyclerViewLongSetSelectFragment<F,FUIH,CategoryItemRecyclerViewAdapter<?,?,?>, CategoryItemViewAdapterPreferences, CategoryItem> implements BackButtonHandler {
    private static final String TAG = "RecViewCatItemSelFr";
    private static final String ACTIVE_ITEM = "RecyclerViewCategoryItemSelectFragment.activeCategory";
    private static final String STATE_LIST_VIEW_STATE = "RecyclerViewCategoryItemSelectFragment.listViewStates";
    private static final String STATE_ACTION_START_TIME = "RecyclerViewCategoryItemSelectFragment.actionStartTime";
    private CategoryItem rootAlbum;
    private CategoryBreadcrumbsView categoryPathView;
    private long startedActionAtTime;
    private CategoryItemRecyclerViewAdapter.NavigationListener navListener;
    private LinkedHashMap<Long, Parcelable> listViewStates = new LinkedHashMap<>(5); // one state for each level within the list (created and deleted on demand)
    private boolean adminDataLoaded;


    public static RecyclerViewCategoryItemSelectFragment<?,?> newInstance(CategoryItemViewAdapterPreferences prefs, int actionId) {
        RecyclerViewCategoryItemSelectFragment<?,?> fragment = new RecyclerViewCategoryItemSelectFragment<>();
        fragment.setArguments(RecyclerViewCategoryItemSelectFragment.buildArgsBundle(prefs, actionId));
        return fragment;
    }

    public static Bundle buildArgsBundle(CategoryItemViewAdapterPreferences prefs, int actionId) {
        Bundle args = LongSetSelectFragment.buildArgsBundle(prefs, actionId, null);
        return args;
    }

    @Override
    @LayoutRes
    protected int getViewId() {
        return R.layout.fragment_lazy_album_selection_recycler_list;
    }

    @Override
    protected CategoryItemViewAdapterPreferences loadPreferencesFromBundle(Bundle bundle) {
        return new CategoryItemViewAdapterPreferences(bundle);
    }

    @Override
    protected boolean isNotAuthorisedToAlterState() {
        return isAppInReadOnlyMode(); // Non admin users can alter this since this may be for another profile entirely.
    }


    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if(getListAdapter() != null) {
            outState.putParcelable(ACTIVE_ITEM, getListAdapter().getActiveItem());
        }
        outState.putLong(STATE_ACTION_START_TIME, startedActionAtTime);
        BundleUtils.writeMap(outState, STATE_LIST_VIEW_STATE, listViewStates);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {

        View v = super.onCreateView(inflater, container, savedInstanceState);

        if (isNotAuthorisedToAlterState()) {
            getViewPrefs().readonly();
        }

        if (savedInstanceState != null) {
            startedActionAtTime = savedInstanceState.getLong(STATE_ACTION_START_TIME);
            listViewStates = BundleUtils.readMap(savedInstanceState, STATE_LIST_VIEW_STATE, new LinkedHashMap<>(), getClass().getClassLoader());
        }

        startedActionAtTime = System.currentTimeMillis();

        categoryPathView = v.findViewById(R.id.category_path);
        categoryPathView.setNavigationListener(new CategoryNavigationListener());

        navListener = (oldCategory, newCategory) -> {

            setBackButtonHandlerEnabled(!newCategory.isRoot());
            if (!newCategory.hasNonAdminCopyChildren() && (oldCategory == null || oldCategory.getId() != newCategory.getId())) {
                loadDataForAlbum(newCategory);
            }
            if (oldCategory != null) {
                listViewStates.put(oldCategory.getId(), getList().getLayoutManager() == null ? null : getList().getLayoutManager().onSaveInstanceState());
            }
            getList().scrollToPosition(0);

            buildBreadcrumbs(newCategory);
        };

        ExtendedFloatingActionButton newItemButton = getAddListItemButton();
        if(newItemButton != null) {
            newItemButton.setOnClickListener(v1 -> {
                CategoryItemRecyclerViewAdapter listAdapter = getListAdapter();
                if (listAdapter == null) {
                    Logging.log(Log.ERROR, TAG, "List adapter is null - weird");
                } else {
                    CategoryItem selectedAlbum = listAdapter.getActiveItem();
                    if (selectedAlbum != null) {
                        AlbumCreateNeededEvent event = new AlbumCreateNeededEvent(selectedAlbum.toStub());
                        getUiHelper().setTrackingRequest(event.getActionId());
                        EventBus.getDefault().post(event);
                    } else {
                        getUiHelper().showDetailedShortMsg(R.string.alert_error, R.string.please_select_a_parent_album);
                    }
                }
            });
        }

        if(rootAlbum == null) {
            loadData();
        } else {
            bindDataToView(savedInstanceState);
            buildBreadcrumbs(getListAdapter().getActiveItem());
        }
        return v;
    }

    private void buildBreadcrumbs(CategoryItem newCategory) {
        categoryPathView.populate(newCategory);

    }

    private void bindDataToView(Bundle savedInstanceState) {

        categoryPathView.setRoot(rootAlbum);

        CategoryItem activeCategory = null;
        if (savedInstanceState != null) {
            activeCategory = savedInstanceState.getParcelable(ACTIVE_ITEM);
        }

        if(getListAdapter() == null) {

            final CategoryItemRecyclerViewAdapter viewAdapter = new CategoryItemRecyclerViewAdapter(rootAlbum, navListener, new CategoryItemRecyclerViewAdapter.MultiSelectStatusAdapter(), getViewPrefs());
            if (activeCategory != null) {
                viewAdapter.setActiveItem(activeCategory);
            } else {
                viewAdapter.setActiveItem(rootAlbum.findChild(getViewPrefs().getInitialRoot().getId()));
            }

            if (!viewAdapter.isItemSelectionAllowed()) {
                viewAdapter.toggleItemSelection();
            }

            viewAdapter.setInitiallySelectedItems();

            // will restore previous selection from state if any
            setListAdapter(viewAdapter);
        } else {
            getListAdapter().setActiveItem(getListAdapter().getActiveItem());
        }

        // call this here to ensure page reformats if orientation changes for example.
        getViewPrefs().withColumns(AlbumViewPreferences.getAlbumsToDisplayPerRow(getActivity(), getPrefs()));
        int colsOnScreen = getViewPrefs().getColumns();
        GridLayoutManager layoutMan = new GridLayoutManager(getContext(), colsOnScreen);
        getList().setLayoutManager(layoutMan);
        getList().setAdapter(getListAdapter());

    }

    private void loadData() {
        loadDataForAlbum(StaticCategoryItem.ROOT_ALBUM);
    }

    private void loadDataForAlbum(CategoryItem album) {
        ConnectionPreferences.ProfilePreferences connectionPrefs = ConnectionPreferences.getPreferences(getViewPrefs().getConnectionProfileKey(), prefs, requireContext());
        PiwigoSessionDetails sessionDetails = PiwigoSessionDetails.getInstance(connectionPrefs);

        if (PiwigoSessionDetails.isAdminUser(connectionPrefs)) {
            // invoke a call to admin list to pick up any missed.

            if (PiwigoSessionDetails.isAdminUser(connectionPrefs) && !adminDataLoaded) {
                // trigger admin category load
                AbstractPiwigoWsResponseHandler handler = new AlbumGetChildAlbumsAdminResponseHandler();
                handler.withConnectionPreferences(connectionPrefs);
                addActiveServiceCall(R.string.progress_loading_albums, handler);
            }
            // trigger non admin category load
            String preferredAlbumThumbnailSize = AlbumViewPreferences.getPreferredAlbumThumbnailSize(prefs, requireContext());
            AlbumGetChildAlbumsResponseHandler handler = new AlbumGetChildAlbumsResponseHandler(album, preferredAlbumThumbnailSize, true);
            handler.withConnectionPreferences(connectionPrefs);
            addActiveServiceCall(R.string.progress_loading_albums, handler);
        } else if (sessionDetails != null && sessionDetails.isCommunityApiAvailable()) {
            CommunityGetChildAlbumNamesResponseHandler handler = new CommunityGetChildAlbumNamesResponseHandler(album.getId(), true);
            handler.withConnectionPreferences(connectionPrefs);
            addActiveServiceCall(R.string.progress_loading_albums, handler);
        } else {
            String preferredAlbumThumbnailSize = AlbumViewPreferences.getPreferredAlbumThumbnailSize(prefs, requireContext());
            AlbumGetChildAlbumsResponseHandler handler = new AlbumGetChildAlbumsResponseHandler(album, preferredAlbumThumbnailSize, true);
            handler.withConnectionPreferences(connectionPrefs);
            addActiveServiceCall(R.string.progress_loading_albums, handler);
        }
    }

    @Override
    public boolean onBackButton() {
        if(getListAdapter() == null) {
            // might occur if the list isn't set up yet
            return false;
        }
        CategoryItem activeItem = getListAdapter().getActiveItem();
        listViewStates.remove(activeItem.getId());
        if(activeItem.isRoot()) {
            Logging.log(Log.ERROR, TAG, "Unable to handle back when root shown");
            return false;
        }
        CategoryItem parent = rootAlbum.findChild(activeItem.getParentId());
        if (parent.getName().isEmpty()) {
            return false;
        } else {
            getListAdapter().setActiveItem(parent);
            setBackButtonHandlerEnabled(!parent.isRoot());
            getList().getLayoutManager().onRestoreInstanceState(listViewStates.get(parent.getId()));
            return true;
        }
    }

    @Override
    protected void setPageHeading(TextView headingField) {
        // heading field not used (missing from layout)
    }

    @Override
    public void onResume() {
        super.onResume();
    }

    @Override
    protected void rerunRetrievalForFailedPages() {
    }

    @Override
    protected void onSelectActionComplete(HashSet<Long> selectedIdsSet) {
        CategoryItemRecyclerViewAdapter listAdapter = getListAdapter();
        if (listAdapter == null) {
            // unable to handle action as list adapter is null.
            return;
        }
        HashSet<CategoryItem> selectedItems = listAdapter.getSelectedItems();
        if(getViewPrefs().isAllowItemSelection() && !getViewPrefs().isMultiSelectionEnabled()) {
            boolean selectedIdsAreValid = false;
            if(!selectedItems.isEmpty()) {
                CategoryItem selectedItem = selectedItems.iterator().next();
                Long activeId = listAdapter.getActiveItem().getId(); // need this to be a long as it is comparing with a Long!
                if((activeId.equals(selectedItem.getParentId())
                    || activeId.equals(selectedItem.getId()))) {
                    // do nothing.
                    selectedIdsAreValid = true;
                }
            }
            if(!selectedIdsAreValid) {
                selectedItems = new HashSet<>(1);
                selectedItems.add(listAdapter.getActiveItem());
            }
        }
        long actionTimeMillis = System.currentTimeMillis() - startedActionAtTime;

        HashMap<CategoryItem, String> albumPaths = new HashMap<>();
        for(CategoryItem selectedItem : selectedItems) {
            albumPaths.put(selectedItem, rootAlbum.getAlbumPath(selectedItem));
        }
        EventBus.getDefault().post(new ExpandingAlbumSelectionCompleteEvent(getActionId(), PiwigoUtils.toSetOfIds(selectedItems), selectedItems, albumPaths));
        // now pop this screen off the stack.
        if (isVisible()) {
            Logging.log(Log.INFO, TAG, "removing from activity immediately as select action complete");
            getParentFragmentManager().popBackStackImmediate();
        }
    }

    @Override
    public void onUserActionCancelFileSelection() {
        long actionTimeMillis = System.currentTimeMillis() - startedActionAtTime;
        EventBus.getDefault().post(new ExpandingAlbumSelectionCompleteEvent(getActionId()));
        super.onUserActionCancelFileSelection();
    }

    @Override
    protected BasicPiwigoResponseListener<FUIH,F> buildPiwigoResponseListener(Context context) {
        return new CustomPiwigoResponseListener<>();
    }

    void onPiwigoResponseAlbumsLoaded(CategoryItem parentAlbum, final ArrayList<CategoryItem> albums, boolean isAdminList) {

        adminDataLoaded |= isAdminList;

        getUiHelper().hideProgressIndicator();
        if(rootAlbum == null) {
            rootAlbum = StaticCategoryItem.ROOT_ALBUM.toInstance();
        }
        CategoryItem localCopyOfAlbum = rootAlbum.findChild(parentAlbum.getId());

        if(localCopyOfAlbum.getChildAlbumCount() == 0) {
            localCopyOfAlbum.setChildAlbums(albums);
        } else {
            if(albums.size() == 1 && localCopyOfAlbum.getId() == albums.get(0).getId()) {
                // merge the children.
                localCopyOfAlbum.mergeChildrenWith(albums.get(0).getChildAlbums(), isAdminList);
            } else {
                // presume this is just a list of the children.
                localCopyOfAlbum.mergeChildrenWith(albums, isAdminList);
            }

        }
        bindDataToView(null);
    }

    private CategoryItem getActiveAlbum() {
        if(getListAdapter() != null) {
            return getListAdapter().getActiveItem();
        }
        if(rootAlbum == null) {
            rootAlbum = StaticCategoryItem.ROOT_ALBUM.toInstance();
        }
        return rootAlbum;
    }

    @Subscribe(threadMode = ThreadMode.MAIN_ORDERED)
    public void onEvent(AlbumCreatedEvent event) {
        if (getUiHelper().isTrackingRequest(event.getActionId())) {
            CategoryItem newItem = event.getAlbumDetail();
            getListAdapter().getActiveItem().addChildAlbum(newItem);
            getListAdapter().addItem(newItem);
        }
    }

    private static class CustomPiwigoResponseListener<F extends RecyclerViewCategoryItemSelectFragment<F,FUIH>,FUIH extends FragmentUIHelper<FUIH,F>> extends BasicPiwigoResponseListener<FUIH,F> {


        @Override
        public void onAfterHandlePiwigoResponse(PiwigoResponseBufferingHandler.Response response) {
            if (response instanceof AlbumGetChildAlbumsResponseHandler.PiwigoGetSubAlbumsResponse) {
                getParent().onPiwigoResponseAlbumsLoaded(((AlbumGetChildAlbumsResponseHandler.PiwigoGetSubAlbumsResponse) response).getParentAlbum(), ((AlbumGetChildAlbumsResponseHandler.PiwigoGetSubAlbumsResponse) response).getAlbums(), false);
            } else if (response instanceof AlbumGetChildAlbumsAdminResponseHandler.PiwigoGetSubAlbumsAdminResponse) {
                getParent().onPiwigoResponseAlbumsLoaded(StaticCategoryItem.ROOT_ALBUM, ((AlbumGetChildAlbumsAdminResponseHandler.PiwigoGetSubAlbumsAdminResponse) response).getAdminList().getAlbums(), true);
            } else if(response instanceof CommunityGetChildAlbumNamesResponseHandler.PiwigoCommunityGetSubAlbumNamesResponse) {

                ArrayList<CategoryItemStub> albumNames = ((CommunityGetChildAlbumNamesResponseHandler.PiwigoCommunityGetSubAlbumNamesResponse) response).getAlbumNames();
                ArrayList<CategoryItem> albums = CategoryItem.newListFromStubs(albumNames);
                getParent().onPiwigoResponseAlbumsLoaded(((AlbumGetChildAlbumsResponseHandler.PiwigoGetSubAlbumsResponse) response).getParentAlbum(), albums, false);
            }
        }
    }

    public class CategoryNavigationListener implements AbstractBreadcrumbsView.NavigationListener<CategoryItem> {
        @Override
        public void onBreadcrumbClicked(CategoryItem pathItem) {
            CategoryItem currentItem = getListAdapter().getActiveItem();
            if(currentItem != null) {
                listViewStates.put(currentItem.getId(), getList().getLayoutManager() == null ? null : getList().getLayoutManager().onSaveInstanceState());
            }
            getList().scrollToPosition(0);

            if(getListAdapter().getActiveItem().equals(pathItem)) {
                //getListAdapter().rebuildContentView();
                // DO nothing
            } else {
                setBackButtonHandlerEnabled(!pathItem.isRoot());
                boolean folderChanged = getListAdapter().setActiveItem(pathItem);
                if(folderChanged && listViewStates != null) {
                    Iterator<Map.Entry<Long, Parcelable>> iter = listViewStates.entrySet().iterator();
                    Map.Entry<Long, Parcelable> item;
                    boolean listContentLoaded = false;
                    while (iter.hasNext()) {
                        item = iter.next();
                        if (item.getKey().equals(pathItem.getId())) {
                            if (getList().getLayoutManager() != null) {
                                Parcelable state = item.getValue();
                                getList().getLayoutManager().onRestoreInstanceState(state);
                                listContentLoaded = state != null;
                            } else {
                                Logging.log(Log.WARN, TAG, "Unable to update list as layout manager is null");
                            }
                            iter.remove();
                            while (iter.hasNext()) {
                                iter.next();
                                iter.remove();
                            }
                        }
                    }
                    if(!listContentLoaded) {
                        loadDataForAlbum(getListAdapter().getActiveItem());
                    }
                }
            }
        }
    }
}
