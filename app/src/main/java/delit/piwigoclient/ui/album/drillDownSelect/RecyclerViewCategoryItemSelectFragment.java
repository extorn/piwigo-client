package delit.piwigoclient.ui.album.drillDownSelect;

import android.content.Context;
import android.os.Bundle;
import android.os.Parcelable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.widget.TextViewCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import delit.piwigoclient.R;
import delit.piwigoclient.business.AlbumViewPreferences;
import delit.piwigoclient.business.ConnectionPreferences;
import delit.piwigoclient.model.piwigo.CategoryItem;
import delit.piwigoclient.model.piwigo.CategoryItemStub;
import delit.piwigoclient.model.piwigo.PiwigoSessionDetails;
import delit.piwigoclient.model.piwigo.PiwigoUtils;
import delit.piwigoclient.piwigoApi.BasicPiwigoResponseListener;
import delit.piwigoclient.piwigoApi.PiwigoResponseBufferingHandler;
import delit.piwigoclient.piwigoApi.handlers.AbstractPiwigoWsResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.AlbumGetSubAlbumsAdminResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.AlbumGetSubAlbumsResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.CommunityGetSubAlbumNamesResponseHandler;
import delit.piwigoclient.ui.common.BackButtonHandler;
import delit.piwigoclient.ui.common.FlowLayout;
import delit.piwigoclient.ui.common.button.CustomImageButton;
import delit.piwigoclient.ui.common.fragment.LongSetSelectFragment;
import delit.piwigoclient.ui.common.fragment.RecyclerViewLongSetSelectFragment;
import delit.piwigoclient.ui.common.util.BundleUtils;
import delit.piwigoclient.ui.events.trackable.AlbumCreateNeededEvent;
import delit.piwigoclient.ui.events.trackable.AlbumCreatedEvent;
import delit.piwigoclient.ui.events.trackable.ExpandingAlbumSelectionCompleteEvent;

import static android.view.View.NO_ID;

public class RecyclerViewCategoryItemSelectFragment extends RecyclerViewLongSetSelectFragment<CategoryItemRecyclerViewAdapter, CategoryItemViewAdapterPreferences> implements BackButtonHandler {
    private static final String ACTIVE_ITEM = "RecyclerViewCategoryItemSelectFragment.activeCategory";
    private static final String STATE_LIST_VIEW_STATE = "RecyclerViewCategoryItemSelectFragment.listViewStates";
    private static final String STATE_ACTION_START_TIME = "RecyclerViewCategoryItemSelectFragment.actionStartTime";
    private CategoryItem rootAlbum;
    private FlowLayout categoryPathView;
    private long startedActionAtTime;
    private CategoryItemRecyclerViewAdapter.NavigationListener navListener;
    private LinkedHashMap<Long, Parcelable> listViewStates; // one state for each level within the list (created and deleted on demand)


    public static RecyclerViewCategoryItemSelectFragment newInstance(CategoryItemViewAdapterPreferences prefs, int actionId) {
        RecyclerViewCategoryItemSelectFragment fragment = new RecyclerViewCategoryItemSelectFragment();
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
    protected CategoryItemViewAdapterPreferences createEmptyPrefs() {
        return new CategoryItemViewAdapterPreferences();
    }

    @Override
    protected boolean isNotAuthorisedToAlterState() {
        return isAppInReadOnlyMode(); // Non admin users can alter this since this may be for another profile entirely.
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putParcelable(ACTIVE_ITEM, getListAdapter().getActiveItem());
        outState.putLong(STATE_ACTION_START_TIME, startedActionAtTime);
        outState.putSerializable(STATE_LIST_VIEW_STATE, listViewStates);
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
            listViewStates = BundleUtils.getSerializable(savedInstanceState, STATE_LIST_VIEW_STATE, LinkedHashMap.class);
        }

        startedActionAtTime = System.currentTimeMillis();

        categoryPathView = v.findViewById(R.id.category_path);

        navListener = new CategoryItemRecyclerViewAdapter.NavigationListener() {

            @Override
            public void onCategoryOpened(CategoryItem oldCategory, CategoryItem newCategory) {

                if(oldCategory != null) {
                    if (listViewStates == null) {
                        listViewStates = new LinkedHashMap<>(5);
                    }
                    listViewStates.put(oldCategory.getId(), getList().getLayoutManager() == null ? null : getList().getLayoutManager().onSaveInstanceState());
                }
                getList().scrollToPosition(0);

                buildBreadcrumbs(newCategory);

            }
        };

        CustomImageButton newItemButton = getAddListItemButton();
        newItemButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                AlbumCreateNeededEvent event = new AlbumCreateNeededEvent(getListAdapter().getActiveItem().toStub());
                getUiHelper().setTrackingRequest(event.getActionId());
                EventBus.getDefault().post(event);
            }
        });

        if(rootAlbum == null) {
            loadData();
        } else {
            bindDataToView(savedInstanceState);
            buildBreadcrumbs(getListAdapter().getActiveItem());
        }
        return v;
    }

    private void buildBreadcrumbs(CategoryItem newCategory) {
        categoryPathView.removeAllViews();

        List<CategoryItem> pathItems = rootAlbum.getFullPath(newCategory);
        TextView pathItem = null;
        int idx = 0;
        for(final CategoryItem pathItemCategory : pathItems) {
            idx++;
            int lastId = NO_ID;
            if(pathItem != null) {
                lastId = pathItem.getId();
            }
            pathItem = new TextView(getContext());
            pathItem.setId(View.generateViewId());
            TextViewCompat.setTextAppearance(pathItem, R.style.Custom_TextAppearance_AppCompat_Body2_Clickable);
            pathItem.setText(pathItemCategory.getName());
            categoryPathView.addView(pathItem);

            pathItem.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    TextView tv = (TextView) v;
                    getListAdapter().setActiveItem(pathItemCategory);
                    if(listViewStates != null) {
                        Iterator<Map.Entry<Long, Parcelable>> iter = listViewStates.entrySet().iterator();
                        Map.Entry<Long, Parcelable> item;
                        while (iter.hasNext()) {
                            item = iter.next();
                            if (item.getKey() == pathItemCategory.getId()) {
                                getList().getLayoutManager().onRestoreInstanceState(item.getValue());
                                iter.remove();
                                while (iter.hasNext()) {
                                    iter.next();
                                    iter.remove();
                                }
                            }
                        }
                    }
                }
            });

            if(idx < pathItems.size()) {
                TextView pathItemSeperator = new TextView(getContext());
                TextViewCompat.setTextAppearance(pathItemSeperator, R.style.TextAppearance_AppCompat_Body2);
                pathItemSeperator.setText("/");
                pathItemSeperator.setId(View.generateViewId());
                categoryPathView.addView(pathItemSeperator);
                pathItem = pathItemSeperator;
            }
        }
    }

    private void bindDataToView(Bundle savedInstanceState) {

        CategoryItem activeCategory = null;
        if (savedInstanceState != null) {
            activeCategory = savedInstanceState.getParcelable(ACTIVE_ITEM);
        }

        if(getListAdapter() == null) {

            final CategoryItemRecyclerViewAdapter viewAdapter = new CategoryItemRecyclerViewAdapter(rootAlbum, navListener, new CategoryItemRecyclerViewAdapter.MultiSelectStatusAdapter<CategoryItem>(), getViewPrefs());
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
        }

        // call this here to ensure page reformats if orientation changes for example.
        getViewPrefs().withColumns(AlbumViewPreferences.getAlbumsToDisplayPerRow(getActivity(), getPrefs()));
        int colsOnScreen = Math.max(getViewPrefs().getColumns(), getViewPrefs().getColumns());
        GridLayoutManager layoutMan = new GridLayoutManager(getContext(), colsOnScreen);
        getList().setLayoutManager(layoutMan);
        getList().setAdapter(getListAdapter());
    }

    private void loadData() {
        ConnectionPreferences.ProfilePreferences connectionPrefs = ConnectionPreferences.getPreferences(getViewPrefs().getConnectionProfileKey(), prefs, getContext());
        PiwigoSessionDetails sessionDetails = PiwigoSessionDetails.getInstance(connectionPrefs);

        if (PiwigoSessionDetails.isAdminUser(connectionPrefs)) {
            //NOTE: No thumbnail url is provided with this call... Maybe need to run a standard call too as for the main albums then merge?
            AbstractPiwigoWsResponseHandler handler = new AlbumGetSubAlbumsAdminResponseHandler();
            handler.withConnectionPreferences(connectionPrefs);
            addActiveServiceCall(R.string.progress_loading_albums, handler.invokeAsync(getContext()));
            String preferredAlbumThumbnailSize = AlbumViewPreferences.getPreferredAlbumThumbnailSize(prefs, getContext());

            handler = new AlbumGetSubAlbumsResponseHandler(CategoryItem.ROOT_ALBUM, preferredAlbumThumbnailSize, true);
            handler.withConnectionPreferences(connectionPrefs);
            addActiveServiceCall(R.string.progress_loading_albums, handler.invokeAsync(getContext()));
        } else if (sessionDetails != null && sessionDetails.isUseCommunityPlugin()) {
            CommunityGetSubAlbumNamesResponseHandler handler = new CommunityGetSubAlbumNamesResponseHandler(CategoryItem.ROOT_ALBUM.getId(), true);
            handler.withConnectionPreferences(connectionPrefs);
            addActiveServiceCall(R.string.progress_loading_albums, handler.invokeAsync(getContext()));
        } else {
            String preferredAlbumThumbnailSize = AlbumViewPreferences.getPreferredAlbumThumbnailSize(prefs, getContext());
            AlbumGetSubAlbumsResponseHandler handler = new AlbumGetSubAlbumsResponseHandler(CategoryItem.ROOT_ALBUM, preferredAlbumThumbnailSize, true);
            handler.withConnectionPreferences(connectionPrefs);
            addActiveServiceCall(R.string.progress_loading_albums, handler.invokeAsync(getContext()));
        }
    }

    @Override
    public boolean onBackButton() {
        listViewStates.remove(getListAdapter().getActiveItem().getId());
        CategoryItem parent = rootAlbum.findChild(getListAdapter().getActiveItem().getParentId());
        if (parent.getName().isEmpty()) {
            return false;
        } else {
            getListAdapter().setActiveItem(parent);
            getList().getLayoutManager().onRestoreInstanceState(listViewStates.get(parent.getId()));
            return true;
        }
    }

    @Override
    protected void setPageHeading(TextView headingField) {
        // heading field not used (missing from layout)
//        headingField.setText(R.string.file_selection_heading);
//        headingField.setVisibility(View.VISIBLE);
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
        HashSet<CategoryItem> selectedItems = listAdapter.getSelectedItems();
        if(getViewPrefs().isAllowItemSelection() && !getViewPrefs().isMultiSelectionEnabled()) {
            boolean selectedIdsAreValid = false;
            if(!selectedItems.isEmpty()) {
                CategoryItem selectedItem = selectedItems.iterator().next();
                Long activeId = listAdapter.getActiveItem().getId(); // need this to be a long as it is comparing with a Long!
                if(activeId != null
                 && (activeId.equals(selectedItem.getParentId())
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
            getFragmentManager().popBackStackImmediate();
        }
    }

    @Override
    public void onCancelChanges() {
        long actionTimeMillis = System.currentTimeMillis() - startedActionAtTime;
        EventBus.getDefault().post(new ExpandingAlbumSelectionCompleteEvent(getActionId()));
        super.onCancelChanges();
    }

    @Override
    protected BasicPiwigoResponseListener buildPiwigoResponseListener(Context context) {
        return new CustomPiwigoResponseListener();
    }

    private void onAlbumsLoaded(final ArrayList<CategoryItem> albums, boolean isAdminList) {
        getUiHelper().hideProgressIndicator();
        if(rootAlbum == null) {
            rootAlbum = CategoryItem.ROOT_ALBUM.clone();
        }
        if(rootAlbum.getChildAlbumCount() == 0) {
            rootAlbum.setChildAlbums(albums);
        } else {
            rootAlbum.mergeChildrenWith(albums, isAdminList);
            CategoryItem activeItem = getListAdapter().getActiveItem();
            getListAdapter().setActiveItem(rootAlbum.findChild(activeItem.getId()));
        }
        bindDataToView(null);
    }

    @Subscribe(threadMode = ThreadMode.MAIN_ORDERED)
    public void onEvent(AlbumCreatedEvent event) {
        if (getUiHelper().isTrackingRequest(event.getActionId())) {
            CategoryItem newItem = event.getAlbumDetail();
            getListAdapter().getActiveItem().addChildAlbum(newItem);
            getListAdapter().addItem(newItem);
        }
    }

    private class CustomPiwigoResponseListener extends BasicPiwigoResponseListener {
        @Override
        public void onAfterHandlePiwigoResponse(PiwigoResponseBufferingHandler.Response response) {
            if (response instanceof AlbumGetSubAlbumsResponseHandler.PiwigoGetSubAlbumsResponse) {
                onAlbumsLoaded(((AlbumGetSubAlbumsResponseHandler.PiwigoGetSubAlbumsResponse) response).getAlbums(), false);
            } else if (response instanceof AlbumGetSubAlbumsAdminResponseHandler.PiwigoGetSubAlbumsAdminResponse) {
                onAlbumsLoaded(((AlbumGetSubAlbumsAdminResponseHandler.PiwigoGetSubAlbumsAdminResponse) response).getAdminList().getAlbums(), true);
            } else if(response instanceof CommunityGetSubAlbumNamesResponseHandler.PiwigoCommunityGetSubAlbumNamesResponse) {

                ArrayList<CategoryItemStub> albumNames = ((CommunityGetSubAlbumNamesResponseHandler.PiwigoCommunityGetSubAlbumNamesResponse) response).getAlbumNames();
                ArrayList<CategoryItem> albums = CategoryItem.newListFromStubs(albumNames);
                onAlbumsLoaded(albums, false);
            }
        }
    }
}
