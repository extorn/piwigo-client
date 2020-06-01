package delit.piwigoclient.ui.favorites;

import android.content.Context;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.AppCompatImageView;
import androidx.lifecycle.ViewModelProvider;
import androidx.lifecycle.ViewModelProviders;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.crashlytics.android.Crashlytics;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import delit.libs.ui.util.BundleUtils;
import delit.libs.ui.util.ParcelUtils;
import delit.libs.ui.view.recycler.BaseRecyclerViewAdapter;
import delit.libs.ui.view.recycler.EndlessRecyclerViewScrollListener;
import delit.libs.util.CollectionUtils;
import delit.piwigoclient.R;
import delit.piwigoclient.business.AlbumViewPreferences;
import delit.piwigoclient.business.ConnectionPreferences;
import delit.piwigoclient.model.piwigo.Basket;
import delit.piwigoclient.model.piwigo.CategoryItem;
import delit.piwigoclient.model.piwigo.GalleryItem;
import delit.piwigoclient.model.piwigo.PiwigoFavorites;
import delit.piwigoclient.model.piwigo.PiwigoSessionDetails;
import delit.piwigoclient.model.piwigo.PiwigoUtils;
import delit.piwigoclient.model.piwigo.ResourceItem;
import delit.piwigoclient.piwigoApi.BasicPiwigoResponseListener;
import delit.piwigoclient.piwigoApi.PiwigoResponseBufferingHandler;
import delit.piwigoclient.piwigoApi.handlers.BaseImageGetInfoResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.BaseImageUpdateInfoResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.BaseImagesGetResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.FavoritesGetImagesResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.FavoritesRemoveImageResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.GetMethodsAvailableResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.ImageDeleteResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.ImageGetInfoResponseHandler;
import delit.piwigoclient.ui.MainActivity;
import delit.piwigoclient.ui.album.view.AlbumItemRecyclerViewAdapter;
import delit.piwigoclient.ui.album.view.AlbumItemRecyclerViewAdapterPreferences;
import delit.piwigoclient.ui.common.FragmentUIHelper;
import delit.piwigoclient.ui.common.UIHelper;
import delit.piwigoclient.ui.common.fragment.MyFragment;
import delit.piwigoclient.ui.events.AlbumAlteredEvent;
import delit.piwigoclient.ui.events.AppLockedEvent;
import delit.piwigoclient.ui.events.AppUnlockedEvent;
import delit.piwigoclient.ui.events.FavoritesUpdatedEvent;
import delit.piwigoclient.ui.model.PiwigoFavoritesModel;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;

/**
 * A fragment representing a list of Items.
 */
public class ViewFavoritesFragment extends MyFragment<ViewFavoritesFragment> {

    private static final String STATE_FAVORITES_ACTIVE_LOAD_THREADS = "activeLoadingThreads";
    private static final String STATE_FAVORITES_LOADS_TO_RETRY = "retryLoadList";
    private static final String STATE_DELETE_ACTION_DATA = "deleteActionData";
    private static final String STATE_USER_GUID = "userGuid";
    private static final String STATE_FAVORITES_DIRTY = "favoritesIsDirty";

    private AlbumItemRecyclerViewAdapter viewAdapter;
    private ExtendedFloatingActionButton retryActionButton;
    private RelativeLayout bulkActionsContainer;
    private ExtendedFloatingActionButton bulkActionButtonDelete;
    // Start fields maintained in saved session state.
    private PiwigoFavorites favoritesModel;
    private final HashMap<Long, String> loadingMessageIds = new HashMap<>(2);
    private final ArrayList<String> itemsToLoad = new ArrayList<>(0);
    private int colsOnScreen;
    private DeleteActionData deleteActionData;
    private long userGuid;
    private AppCompatImageView actionIndicatorImg;
    private RecyclerView favoritesListView;
    private TextView emptyFavoritesLabel;
    private AlbumItemRecyclerViewAdapterPreferences viewPrefs;
    private boolean favoritesIsDirty;


    /**
     * Mandatory empty constructor for the fragment manager to instantiate the
     * fragment (e.g. upon screen orientation changes).
     */
    public ViewFavoritesFragment() {
    }

    public static ViewFavoritesFragment newInstance() {
        ViewFavoritesFragment fragment = new ViewFavoritesFragment();
        Bundle args = new Bundle();
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (getArguments() != null) {
        }
    }

    private float getScreenWidth() {
        DisplayMetrics dm = new DisplayMetrics();
        getActivity().getWindowManager().getDefaultDisplay().getMetrics(dm);
        return (float)dm.widthPixels / dm.xdpi;
    }

    private int getDefaultImagesColumnCount() {
        float screenWidth = getScreenWidth();
        int columnsToShow = Math.round(screenWidth - (screenWidth % 1)); // allow 1 inch per column
        return Math.max(1,columnsToShow);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        UIHelper.recycleImageViewContent(actionIndicatorImg);
    }

    @Override
    protected String buildPageHeading() {
        return getString(R.string.favorites_heading);
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (viewPrefs != null) {
            viewPrefs.storeToBundle(outState);
        }
        BundleUtils.writeMap(outState, STATE_FAVORITES_ACTIVE_LOAD_THREADS, loadingMessageIds);
        outState.putStringArrayList(STATE_FAVORITES_LOADS_TO_RETRY, itemsToLoad);
        outState.putParcelable(STATE_DELETE_ACTION_DATA, deleteActionData);
    }

    private AlbumItemRecyclerViewAdapterPreferences updateViewPrefs() {

//        PiwigoSessionDetails sessionDetails = PiwigoSessionDetails.getInstance(ConnectionPreferences.getActiveProfile());

        boolean showAlbumThumbnailsZoomed = AlbumViewPreferences.isShowAlbumThumbnailsZoomed(prefs, requireContext());


        boolean showResourceNames = AlbumViewPreferences.isShowResourceNames(prefs, requireContext());

        int recentlyAlteredThresholdAge = AlbumViewPreferences.getRecentlyAlteredMaxAgeMillis(prefs, requireContext());
        Date recentlyAlteredThresholdDate = new Date(System.currentTimeMillis() - recentlyAlteredThresholdAge);

        if(viewPrefs == null) {
            viewPrefs = new AlbumItemRecyclerViewAdapterPreferences();
        }

        viewPrefs.selectable(getMultiSelectionAllowed(), false);
        viewPrefs.setAllowItemSelection(false); // prevent selection until a long click enables it.
        viewPrefs.withShowingAlbumNames(showResourceNames);
        viewPrefs.withShowAlbumThumbnailsZoomed(showAlbumThumbnailsZoomed);
//        viewPrefs.withAlbumWidth(getScreenWidth() / albumsPerRow);
        viewPrefs.withRecentlyAlteredThresholdDate(recentlyAlteredThresholdDate);
        return viewPrefs;
    }

    private boolean getMultiSelectionAllowed() {
        PiwigoSessionDetails sessionDetails = PiwigoSessionDetails.getInstance(ConnectionPreferences.getActiveProfile());
        boolean captureActionClicks = sessionDetails != null && sessionDetails.isAdminUser();
        captureActionClicks |= (sessionDetails != null && sessionDetails.isFullyLoggedIn());
        captureActionClicks &= !isAppInReadOnlyMode();
        return captureActionClicks;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull final LayoutInflater inflater, @Nullable final ViewGroup container, @Nullable Bundle savedInstanceState) {

        super.onCreateView(inflater, container, savedInstanceState);

        return inflater.inflate(R.layout.fragment_favorites, container, false);

    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        ConnectionPreferences.ProfilePreferences connectionPrefs = ConnectionPreferences.getActiveProfile();
        PiwigoSessionDetails sessionDetails = PiwigoSessionDetails.getInstance(connectionPrefs);
        if(sessionDetails == null || !sessionDetails.isFullyLoggedIn()) {
            // force a reload of the favorites if the session has been destroyed.
            favoritesIsDirty = true;
        } else if (savedInstanceState != null) {
            //restore saved state
            viewPrefs = new AlbumItemRecyclerViewAdapterPreferences();
            viewPrefs.loadFromBundle(savedInstanceState);

            // if favoritesIsDirty then this fragment was updated while on the backstack - need to refresh it.
            userGuid = savedInstanceState.getLong(STATE_USER_GUID);
            favoritesIsDirty = favoritesIsDirty || PiwigoSessionDetails.getUserGuid(connectionPrefs) != userGuid;
            favoritesIsDirty = favoritesIsDirty || savedInstanceState.getBoolean(STATE_FAVORITES_DIRTY);
            loadingMessageIds.clear();
            BundleUtils.readMap(savedInstanceState, STATE_FAVORITES_ACTIVE_LOAD_THREADS, loadingMessageIds, null);
            CollectionUtils.addToCollectionNullSafe(itemsToLoad, savedInstanceState.getStringArrayList(STATE_FAVORITES_LOADS_TO_RETRY));
            if(deleteActionData != null && deleteActionData.isEmpty()) {
                deleteActionData = null;
            } else {
                deleteActionData = savedInstanceState.getParcelable(STATE_DELETE_ACTION_DATA);
            }
        }

        favoritesModel = new ViewModelProvider(requireActivity()).get("0", PiwigoFavoritesModel.class).getPiwigoFavorites().getValue();

        FavoritesUpdatedEvent favoritesUpdatedEvent = EventBus.getDefault().getStickyEvent(FavoritesUpdatedEvent.class);
        if(favoritesUpdatedEvent != null) {
            // updated the favorite tagged items
            // force reload of all favorites
            favoritesModel = null;
        }

        updateViewPrefs();

        userGuid = PiwigoSessionDetails.getUserGuid(ConnectionPreferences.getActiveProfile());
        if(favoritesModel == null) {
            favoritesIsDirty = true;
            favoritesModel = new ViewModelProvider(requireActivity()).get("0", PiwigoFavoritesModel.class).getPiwigoFavorites(new PiwigoFavorites.FavoritesSummaryDetails(0)).getValue();
        }

        if (isSessionDetailsChanged()) {

            if(!PiwigoSessionDetails.isFullyLoggedIn(ConnectionPreferences.getActiveProfile()) || (isSessionDetailsChanged() && !isServerConnectionChanged())){
                //trigger total screen refresh. Any errors will result in screen being closed.
                favoritesIsDirty = false;
                reloadFavoritesModel();
            } else {
                // immediately leave this screen.
                getParentFragmentManager().popBackStack();
            }
        }

        retryActionButton = view.findViewById(R.id.favorites_retryAction_actionButton);

        retryActionButton.hide();
        retryActionButton.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                onReloadAlbum();
            }
        });

        emptyFavoritesLabel = view.findViewById(R.id.favorites_empty_content);
        emptyFavoritesLabel.setText(R.string.favorites_empty_text);
        emptyFavoritesLabel.setVisibility(GONE);

        bulkActionsContainer = view.findViewById(R.id.favorites_actions_bulk_container);

        int imagesOnScreen = AlbumViewPreferences.getImagesToDisplayPerRow(getActivity(), prefs);
        colsOnScreen = imagesOnScreen;


//        viewInOrientation = getResources().getConfiguration().orientation;

        // Set the adapter
        favoritesListView = view.findViewById(R.id.favorites_list);

        RecyclerView recyclerView = favoritesListView;

        if (!favoritesIsDirty) {
            emptyFavoritesLabel.setVisibility(favoritesModel.getItemCount() == 0 ? VISIBLE : GONE);
        }

        // need to wait for the favorites model to be initialised.
        GridLayoutManager gridLayoutMan = new GridLayoutManager(getContext(), colsOnScreen);
        int colsPerImage = colsOnScreen / imagesOnScreen;
        gridLayoutMan.setSpanSizeLookup(new SpanSizeLookup(favoritesModel, colsPerImage));

        recyclerView.setLayoutManager(gridLayoutMan);


        viewAdapter = new AlbumItemRecyclerViewAdapter(getContext(), PiwigoFavoritesModel.class, favoritesModel, new AlbumViewAdapterListener(), viewPrefs);

        bulkActionsContainer.setVisibility(viewAdapter.isItemSelectionAllowed()?VISIBLE:GONE);

        bulkActionButtonDelete = bulkActionsContainer.findViewById(R.id.favorites_action_delete_bulk);
        if(viewAdapter.isItemSelectionAllowed()) {
            bulkActionButtonDelete.show();
        } else {
            bulkActionButtonDelete.hide();
        }
        bulkActionButtonDelete.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if(event.getActionMasked() == MotionEvent.ACTION_UP) {
                    onBulkActionDeleteButtonPressed();
                }
                return true; // consume the event
            }
        });

        recyclerView.setAdapter(viewAdapter);


        EndlessRecyclerViewScrollListener scrollListener = new EndlessRecyclerViewScrollListener(gridLayoutMan) {
            @Override
            public void onLoadMore(int page, int totalItemsCount, RecyclerView view) {
                int pageToLoad = page;

                int pageSize = AlbumViewPreferences.getResourceRequestPageSize(prefs, requireContext());
                int pageToActuallyLoad = getPageToActuallyLoad(pageToLoad, pageSize);

                if (favoritesModel.isPageLoadedOrBeingLoaded(pageToActuallyLoad) || favoritesModel.isFullyLoaded()) {
                    Integer missingPage = favoritesModel.getAMissingPage();
                    if(missingPage != null) {
                        pageToLoad = missingPage;
                    } else {
                        // already load this one by default so lets not double load it (or we've already loaded all items).
                        return;
                    }
                }
                loadAlbumResourcesPage(pageToLoad);
            }
        };
        scrollListener.configure(favoritesModel.getPagesLoaded(), favoritesModel.getItemCount());
        recyclerView.addOnScrollListener(scrollListener);
    }

    private void reloadFavoritesModel() {
        favoritesModel = new ViewModelProvider(requireActivity()).get("0", PiwigoFavoritesModel.class).getPiwigoFavorites(new PiwigoFavorites.FavoritesSummaryDetails(0)).getValue();
        loadAlbumResourcesPage(0);
    }

    private void onBulkActionDeleteButtonPressed() {
        if(!isAppInReadOnlyMode()) {
            HashSet<Long> selectedItemIds = viewAdapter.getSelectedItemIds();
            if(deleteActionData != null && selectedItemIds.equals(deleteActionData.getSelectedItemIds())) {
                //continue with previous action
                onDeleteResources(deleteActionData);
            } else if(selectedItemIds.size() > 0) {
                HashSet<ResourceItem> selectedItems = viewAdapter.getSelectedItems();
                DeleteActionData deleteActionData = new DeleteActionData(selectedItemIds, selectedItems);
                if(!deleteActionData.isResourceInfoAvailable()) {
                    this.deleteActionData = deleteActionData;
                }
                onDeleteResources(deleteActionData);
            }
        }
    }

    private Basket getBasket() {
        MainActivity activity = (MainActivity)requireActivity();
        return activity.getBasket();
    }

    private static class DeleteActionData implements Parcelable {
        final HashSet<Long> selectedItemIds;
        final HashSet<Long> itemsUpdated;
        final HashSet<ResourceItem> selectedItems;
        boolean resourceInfoAvailable;
        private ArrayList<Long> trackedMessageIds = new ArrayList<>();

        public DeleteActionData(HashSet<Long> selectedItemIds, HashSet<ResourceItem> selectedItems) {
            this.selectedItemIds = selectedItemIds;
            this.selectedItems = selectedItems;
            this.resourceInfoAvailable = false; //FIXME when Piwigo provides this info as standard, this can be removed and the method simplified.
            itemsUpdated = new HashSet<>(selectedItemIds.size());
        }

        public DeleteActionData(Parcel in) {
            selectedItemIds = ParcelUtils.readLongSet(in);
            itemsUpdated = ParcelUtils.readLongSet(in);
            selectedItems = ParcelUtils.readHashSet(in, getClass().getClassLoader());
            resourceInfoAvailable = ParcelUtils.readBool(in);
            trackedMessageIds = ParcelUtils.readLongArrayList(in);
        }

        public static final Creator<DeleteActionData> CREATOR = new Creator<ViewFavoritesFragment.DeleteActionData>() {
            @Override
            public ViewFavoritesFragment.DeleteActionData createFromParcel(Parcel in) {
                return new ViewFavoritesFragment.DeleteActionData(in);
            }

            @Override
            public ViewFavoritesFragment.DeleteActionData[] newArray(int size) {
                return new ViewFavoritesFragment.DeleteActionData[size];
            }
        };

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            ParcelUtils.writeLongSet(dest, selectedItemIds);
            ParcelUtils.writeLongSet(dest, itemsUpdated);
            ParcelUtils.writeSet(dest, selectedItems);
            ParcelUtils.writeBool(dest, resourceInfoAvailable);
            ParcelUtils.writeLongArrayList(dest, trackedMessageIds);
        }

        public void updateLinkedAlbums(ResourceItem item) {
            itemsUpdated.add(item.getId());
            if (itemsUpdated.size() == selectedItemIds.size()) {
                resourceInfoAvailable = true;
            }
            selectedItems.add(item); // will replace the previous with this one.
        }

        public boolean isResourceInfoAvailable() {
            return resourceInfoAvailable;
        }

        public HashSet<Long> getSelectedItemIds() {
            return selectedItemIds;
        }

        public HashSet<ResourceItem> getSelectedItems() {
            return selectedItems;
        }

        public void clear() {
            selectedItemIds.clear();
            selectedItems.clear();
            itemsUpdated.clear();
        }

        public Set<ResourceItem> getItemsWithoutLinkedAlbumData() {
            if (itemsUpdated.size() == 0) {
                return selectedItems;
            }
            Set<ResourceItem> itemsWithoutLinkedAlbumData = new HashSet<>();
            for (ResourceItem r : selectedItems) {
                if (!itemsUpdated.contains(r.getId())) {
                    itemsWithoutLinkedAlbumData.add(r);
                }
            }
            return itemsWithoutLinkedAlbumData;
        }

        public boolean removeProcessedResource(ResourceItem resource) {
            selectedItemIds.remove(resource.getId());
            selectedItems.remove(resource);
            itemsUpdated.remove(resource.getId());
            return selectedItemIds.size() == 0;
        }

        public boolean removeProcessedResources(HashSet<Long> deletedItemIds) {
            for (Long deletedResourceId : deletedItemIds) {
                selectedItemIds.remove(deletedResourceId);
                itemsUpdated.remove(deletedResourceId);
            }
            PiwigoUtils.removeAll(selectedItems, deletedItemIds);
            return selectedItemIds.size() == 0;
        }

        public boolean isEmpty() {
            return selectedItemIds.isEmpty();
        }

        public void trackMessageId(long messageId) {
            trackedMessageIds.add(messageId);
        }

        public boolean isTrackingMessageId(long messageId) {
            return trackedMessageIds.remove(messageId);
        }

        @Override
        public int describeContents() {
            return 0;
        }
    }
    
    private void onDeleteResources(final DeleteActionData deleteActionData) {

        if (!deleteActionData.isResourceInfoAvailable()) {
            Set<String> multimediaExtensionList = ConnectionPreferences.getActiveProfile().getKnownMultimediaExtensions(prefs, requireContext());
            for (ResourceItem item : deleteActionData.getItemsWithoutLinkedAlbumData()) {
                deleteActionData.trackMessageId(addActiveServiceCall(R.string.progress_loading_resource_details, new ImageGetInfoResponseHandler(item, multimediaExtensionList)));
            }
            return;
        }

        UIHelper.QuestionResultListener dialogListener = new OnDeleteFavoritesAction(getUiHelper(), deleteActionData.getSelectedItemIds(), deleteActionData.getSelectedItems());

        PiwigoSessionDetails sessionDetails = PiwigoSessionDetails.getInstance(ConnectionPreferences.getActiveProfile());
        boolean isFavoritesAlterationSupported = sessionDetails != null && sessionDetails.isPiwigoClientPluginInstalled();

        if(isFavoritesAlterationSupported) {
            String msg = getString(R.string.alert_confirm_delete_items_from_server_or_just_remove_from_favorites_list_pattern, deleteActionData.getSelectedItemIds().size());
            getUiHelper().showOrQueueDialogQuestion(R.string.alert_confirm_title, msg, Integer.MIN_VALUE, R.string.button_unfavorite, R.string.button_cancel, R.string.button_delete, dialogListener);
        }
    }

    private void loadAlbumResourcesPage(int pageToLoad) {
        synchronized (loadingMessageIds) {
            favoritesModel.acquirePageLoadLock();
            try {
                int pageSize = AlbumViewPreferences.getResourceRequestPageSize(prefs, requireContext());
                int pageToActuallyLoad = getPageToActuallyLoad(pageToLoad, pageSize);
                if (pageToActuallyLoad < 0) {
                    // the sort order is inverted so we know for a fact this page is invalid.
                    return;
                }
                if (favoritesModel.isPageLoadedOrBeingLoaded(pageToActuallyLoad)) {
                    return;
                }


                String sortOrder = AlbumViewPreferences.getResourceSortOrder(prefs, requireContext());
                Set<String> multimediaExtensionList = ConnectionPreferences.getActiveProfile().getKnownMultimediaExtensions(prefs, requireContext());

                long loadingMessageId = addNonBlockingActiveServiceCall(R.string.progress_loading_album_content, new FavoritesGetImagesResponseHandler(sortOrder, pageToActuallyLoad, pageSize, multimediaExtensionList));
                favoritesModel.recordPageBeingLoaded(loadingMessageId, pageToActuallyLoad);
                loadingMessageIds.put(loadingMessageId, String.valueOf(pageToLoad));
            } finally {
                favoritesModel.releasePageLoadLock();
            }
        }
    }

    private int getPageToActuallyLoad(int pageRequested, int pageSize) {
//        boolean invertSortOrder = AlbumViewPreferences.getResourceSortOrderInverted(prefs, getContext());
//        favoritesModel.setRetrieveItemsInReverseOrder(invertSortOrder);
        int pageToActuallyLoad = pageRequested;
//        if (invertSortOrder) {
//            int pagesOfPhotos = favoritesModel.getContainerDetails().getPagesOfPhotos(pageSize);
//            pageToActuallyLoad = pagesOfPhotos - pageRequested;
//        }
        return pageToActuallyLoad;
    }

    private BaseRecyclerViewAdapter getViewAdapter() {
        return viewAdapter;
    }

    private void deleteResourcesFromServerForever(final HashSet<Long> selectedItemIds, final HashSet<? extends ResourceItem> selectedItems) {
        String msg = getString(R.string.alert_confirm_really_delete_items_from_server);
        getUiHelper().showOrQueueDialogQuestion(R.string.alert_confirm_title, msg, R.string.button_cancel, R.string.button_ok, new OnDeleteFavoritesForeverAction(getUiHelper(), selectedItemIds, selectedItems));
    }

    private static class OnDeleteFavoritesAction extends UIHelper.QuestionResultAdapter<FragmentUIHelper<ViewFavoritesFragment>> {
        private HashSet<Long> selectedItemIds;
        private HashSet<ResourceItem> selectedItems;

        public OnDeleteFavoritesAction(FragmentUIHelper<ViewFavoritesFragment> uiHelper, HashSet<Long> selectedItemIds, HashSet<ResourceItem> selectedItems) {
            super(uiHelper);
            this.selectedItemIds = selectedItemIds;
            this.selectedItems = selectedItems;
        }

        @Override
        public void onResult(AlertDialog dialog, Boolean positiveAnswer) {
            ViewFavoritesFragment fragment = getUiHelper().getParent();
            fragment.getViewAdapter().toggleItemSelection();
            if (Boolean.TRUE == positiveAnswer) {
                HashSet<Long> itemIdsForPermanentDelete = new HashSet<>(selectedItemIds);
                HashSet<ResourceItem> itemsForPermanentDelete = new HashSet<>(selectedItems);
                fragment.deleteResourcesFromServerForever(itemIdsForPermanentDelete, itemsForPermanentDelete);
            } else if (Boolean.FALSE == positiveAnswer) { // Negative answer
                PiwigoSessionDetails sessionDetails = PiwigoSessionDetails.getInstance(ConnectionPreferences.getActiveProfile());
                boolean allowFavoritesEdit = !fragment.isAppInReadOnlyMode() && sessionDetails != null;
                for (ResourceItem item : selectedItems) {
                    if (allowFavoritesEdit) {
                        getUiHelper().addActiveServiceCall(R.string.progress_remove_favorite_resources, new FavoritesRemoveImageResponseHandler(item));
                    }
                }
            } else {
                // Neutral (cancel button) - do nothing
            }
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        if(favoritesListView == null) {
            //Resumed, but fragment initialisation cancelled for whatever reason.
            return;
        }

        if (favoritesIsDirty) {
            reloadAlbumContent();
        } else if(itemsToLoad.size() > 0) {
            onReloadAlbum();
        }
    }

    private void reloadAlbumContent() {
        if(favoritesIsDirty) {
            favoritesIsDirty = false;
            if(loadingMessageIds.size() > 0) {
                // already a load in progress - ignore this call.
                //TODO be cleverer - check the time the call was invoked and queue another if needed.
                return;
            }
            favoritesModel.clear();
            viewAdapter.notifyDataSetChanged();
            loadAlbumResourcesPage(0);

        }
    }

    private static class OnDeleteFavoritesForeverAction extends UIHelper.QuestionResultAdapter {

        private final HashSet<Long> selectedItemIds;
        private final HashSet<? extends ResourceItem> selectedItems;

        public OnDeleteFavoritesForeverAction(UIHelper uiHelper, HashSet<Long> selectedItemIds, HashSet<? extends ResourceItem> selectedItems) {
            super(uiHelper);
            this.selectedItemIds = selectedItemIds;
            this.selectedItems = selectedItems;
        }

        @Override
        public void onResult(AlertDialog dialog, Boolean positiveAnswer) {
            if (Boolean.TRUE == positiveAnswer) {
                getUiHelper().addActiveServiceCall(R.string.progress_delete_resources, new ImageDeleteResponseHandler(selectedItemIds, selectedItems));
            }
        }
    }

    private void displayControlsBasedOnSessionState() {
    }

    private void updateBasketDisplay(Basket basket) {
        if(viewPrefs.isAllowItemSelection()) {
            bulkActionButtonDelete.show();
        } else {
            bulkActionButtonDelete.hide();
        }
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        EventBus.getDefault().register(this);
    }

    @Override
    public void onDetach() {
        super.onDetach();
        EventBus.getDefault().unregister(this);
        favoritesModel = null;
        if(favoritesListView != null) {
            favoritesListView.setAdapter(null);
        }
    }

    @Override
    protected BasicPiwigoResponseListener buildPiwigoResponseListener(Context context) {
        return new CustomPiwigoResponseListener();
    }

    private void onFavoritesUpdateResponse(FavoritesRemoveImageResponseHandler.PiwigoRemoveFavoriteResponse rsp) {
        ResourceItem r = rsp.getPiwigoResource();
        onItemRemovedFromFavorites(r);
        /*if(rsp.hasError()) {
            String errorMsg = getString(R.string.error_updating_favorites_pattern, rsp.getPiwigoResource().getName(), rsp.getError());
            getUiHelper().showOrQueueDialogMessage(R.string.alert_error, errorMsg);
            rsp.getPiwigoResource().setFavorite(true); // removal failed, re-add this favorites.
        } else {
            ResourceItem r = rsp.getPiwigoResource();
            onItemRemovedFromFavorites(r);
        }*/
    }

    private class CustomPiwigoResponseListener extends BasicPiwigoResponseListener {

        @Override
        public void onBeforeHandlePiwigoResponse(PiwigoResponseBufferingHandler.Response response) {
            if(isVisible()) {
                updateActiveSessionDetails();
            }
            super.onBeforeHandlePiwigoResponse(response);
        }

        @Override
        public void onAfterHandlePiwigoResponse(PiwigoResponseBufferingHandler.Response response) {
            synchronized (loadingMessageIds) {

                if (response instanceof BaseImagesGetResponseHandler.PiwigoGetResourcesResponse) {
                    onGetResources((BaseImagesGetResponseHandler.PiwigoGetResourcesResponse) response);
                } else if (response instanceof BaseImageGetInfoResponseHandler.PiwigoResourceInfoRetrievedResponse) {
                    onResourceInfoRetrieved((BaseImageGetInfoResponseHandler.PiwigoResourceInfoRetrievedResponse) response);
                } else if(response instanceof ImageDeleteResponseHandler.PiwigoDeleteImageResponse) {
                    for(ResourceItem r : ((ImageDeleteResponseHandler.PiwigoDeleteImageResponse) response).getDeletedItems()) {
                        onItemRemovedFromServer(r);
                    }
                } else if (response instanceof BaseImageUpdateInfoResponseHandler.PiwigoUpdateResourceInfoResponse) {
                    ResourceItem r = ((BaseImageUpdateInfoResponseHandler.PiwigoUpdateResourceInfoResponse) response).getPiwigoResource();
                    onItemRemovedFromFavorites(r);
                } else if (response instanceof FavoritesRemoveImageResponseHandler.PiwigoRemoveFavoriteResponse) {
                    onFavoritesUpdateResponse(((FavoritesRemoveImageResponseHandler.PiwigoRemoveFavoriteResponse) response));
                } else if (response instanceof GetMethodsAvailableResponseHandler.PiwigoGetMethodsAvailableResponse) {
                    viewPrefs.withAllowMultiSelect(getMultiSelectionAllowed());
                } else {
                    String failedCall = loadingMessageIds.get(response.getMessageId());
                    synchronized (itemsToLoad) {
                        itemsToLoad.add(failedCall);
                        emptyFavoritesLabel.setText(R.string.favorites_content_load_failed_text);
                        if (itemsToLoad.size() > 0) {
                            emptyFavoritesLabel.setVisibility(VISIBLE);
                            retryActionButton.show();
                            favoritesModel.acquirePageLoadLock();
                            try {
                                favoritesModel.recordPageLoadFailed(response.getMessageId());
                            } finally {
                                favoritesModel.releasePageLoadLock();
                            }
                        }
                    }
                }
                loadingMessageIds.remove(response.getMessageId());
            }
        }
    }

    private void onItemRemovedFromServer(ResourceItem r) {
        onItemRemovedFromFavorites(r);
        for(Long albumId : r.getLinkedAlbums()) {
            EventBus.getDefault().post(new AlbumAlteredEvent(albumId, r.getId(), true));
        }
    }

    private void onItemRemovedFromFavorites(ResourceItem r) {
        int itemIdx = favoritesModel.getItemIdx(r);
        favoritesModel.remove(itemIdx);
        viewAdapter.notifyItemRemoved(itemIdx);
        if(deleteActionData.removeProcessedResource(r)) {
            deleteActionData = null;
        }
        for(Long albumId : r.getLinkedAlbums()) {
            EventBus.getDefault().post(new AlbumAlteredEvent(albumId, r.getId(), true));
        }
    }

    private void onReloadAlbum() {
        retryActionButton.hide();
        emptyFavoritesLabel.setVisibility(GONE);
        synchronized (itemsToLoad) {
            while (itemsToLoad.size() > 0) {
                String itemToLoad = itemsToLoad.remove(0);
                if(itemToLoad != null) {
//                    switch (itemToLoad) {
//                        default:
                        int page = Integer.valueOf(itemToLoad);
                        loadAlbumResourcesPage(page);
//                        break;
//                    }
                } else {
                    Crashlytics.log(Log.ERROR, getTag(), "User told favorites page failed to load, but nothing to retry loading!");
                }
            }
        }
    }

    private void onGetResources(final BaseImagesGetResponseHandler.PiwigoGetResourcesResponse response) {
        synchronized (this) {
            favoritesModel.getContainerDetails().setPhotoCount(response.getTotalResourceCount());
            favoritesModel.addItemPage(response.getPage(), response.getPageSize(), response.getResources());
            if (favoritesModel.isFullyLoaded() && favoritesModel.getItemCount() == 0) {
                emptyFavoritesLabel.setText(R.string.favorites_empty_text);
                emptyFavoritesLabel.setVisibility(VISIBLE);
            }
            viewAdapter.notifyDataSetChanged();
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN_ORDERED)
    public void onEvent(AppLockedEvent event) {
        if(isResumed()) {
            displayControlsBasedOnSessionState();
            /*boolean captureActionClicks = PiwigoSessionDetails.isAdminUser(ConnectionPreferences.getActiveProfile()) && !isAppInReadOnlyMode();
            viewAdapter.setCaptureActionClicks(captureActionClicks);*/
        } else {
            // if not showing, just flush the state and rebuild the page
            favoritesIsDirty = true;
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN_ORDERED)
    public void onEvent(AppUnlockedEvent event) {
        if(isResumed()) {
            displayControlsBasedOnSessionState();
            /*boolean captureActionClicks = PiwigoSessionDetails.isAdminUser(ConnectionPreferences.getActiveProfile()) && !isAppInReadOnlyMode();
            viewAdapter.setCaptureActionClicks(captureActionClicks);*/
        } else {
            // if not showing, just flush the state and rebuild the page
            favoritesIsDirty = true;
        }
    }

    private class SpanSizeLookup extends GridLayoutManager.SpanSizeLookup {

        private final int colsPerImage;
        private final PiwigoFavorites favoritesModel;

        public SpanSizeLookup(PiwigoFavorites favoritesModel, int colsPerImage) {
            this.colsPerImage = colsPerImage;
            this.favoritesModel = favoritesModel;
        }

        @Override
        public int getSpanSize(int position) {
            // ensure that app cannot crash due to position being out of bounds.
            //FIXME - why would position be outside model size? What happens next now it doesn't crash here?
            if(position < 0 || favoritesModel.getItemCount() <= position) {
                return 1;
            }

            int itemType = favoritesModel.getItemByIdx(position).getType();
            switch(itemType) {
                case GalleryItem.PICTURE_RESOURCE_TYPE:
                    return colsPerImage;
                case GalleryItem.VIDEO_RESOURCE_TYPE:
                    return colsPerImage;
                default:
                    return colsOnScreen;
            }
        }
    }

    private void onResourceInfoRetrieved(BaseImageGetInfoResponseHandler.PiwigoResourceInfoRetrievedResponse response) {
        if(this.deleteActionData.isTrackingMessageId(response.getMessageId())) {
            this.deleteActionData.updateLinkedAlbums(response.getResource());
            if (this.deleteActionData.isResourceInfoAvailable()) {
                onDeleteResources(deleteActionData);
            }
        }
    }

    private class AlbumViewAdapterListener extends AlbumItemRecyclerViewAdapter.AlbumItemMultiSelectStatusAdapter {

        @Override
        public void onMultiSelectStatusChanged(BaseRecyclerViewAdapter adapter, boolean multiSelectEnabled) {
//            bulkActionsContainer.setVisibility(multiSelectEnabled?VISIBLE:GONE);
        }

        @Override
        public void onItemSelectionCountChanged(BaseRecyclerViewAdapter adapter, int size) {
            bulkActionsContainer.setVisibility(size > 0?VISIBLE:GONE);
//            bulkActionsContainer.setVisibility(size > 0 || getBasket().getItemCount() > 0 ?VISIBLE:GONE);
            updateBasketDisplay(getBasket());
        }

        @Override
        protected void onCategoryClick(CategoryItem item) {
            if (viewAdapter.isItemSelectionAllowed()) {
                viewAdapter.toggleItemSelection();
            }
        }

        @Override
        public void onCategoryLongClick(CategoryItem album) {
        }

        @Override
        public void notifyAlbumThumbnailInfoLoadNeeded(CategoryItem mItem) {
        }

    }

}
