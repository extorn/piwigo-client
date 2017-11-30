package delit.piwigoclient.ui.album.view;

import android.app.AlertDialog;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.BottomSheetBehavior;
import android.support.design.widget.FloatingActionButton;
import android.support.v7.widget.AppCompatImageView;
import android.support.v7.widget.AppCompatTextView;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.StaggeredGridLayoutManager;
import android.util.DisplayMetrics;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.RelativeLayout;
import android.widget.Switch;
import android.widget.TextView;

import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdView;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import delit.piwigoclient.R;
import delit.piwigoclient.model.piwigo.Basket;
import delit.piwigoclient.model.piwigo.CategoryItem;
import delit.piwigoclient.model.piwigo.GalleryItem;
import delit.piwigoclient.model.piwigo.Group;
import delit.piwigoclient.model.piwigo.PiwigoAlbum;
import delit.piwigoclient.model.piwigo.PiwigoSessionDetails;
import delit.piwigoclient.model.piwigo.ResourceItem;
import delit.piwigoclient.model.piwigo.Username;
import delit.piwigoclient.piwigoApi.BasicPiwigoResponseListener;
import delit.piwigoclient.piwigoApi.PiwigoAccessService;
import delit.piwigoclient.piwigoApi.PiwigoResponseBufferingHandler;
import delit.piwigoclient.ui.AdsManager;
import delit.piwigoclient.ui.MainActivity;
import delit.piwigoclient.ui.common.ControllableBottomSheetBehavior;
import delit.piwigoclient.ui.common.CustomImageButton;
import delit.piwigoclient.ui.common.EndlessRecyclerViewScrollListener;
import delit.piwigoclient.ui.common.MyFragment;
import delit.piwigoclient.ui.common.UIHelper;
import delit.piwigoclient.ui.events.AlbumAlteredEvent;
import delit.piwigoclient.ui.events.AlbumDeletedEvent;
import delit.piwigoclient.ui.events.AlbumSelectedEvent;
import delit.piwigoclient.ui.events.AppLockedEvent;
import delit.piwigoclient.ui.events.AppUnlockedEvent;
import delit.piwigoclient.ui.events.trackable.AlbumCreateNeededEvent;
import delit.piwigoclient.ui.events.trackable.AlbumCreatedEvent;
import delit.piwigoclient.ui.events.trackable.GroupSelectionCompleteEvent;
import delit.piwigoclient.ui.events.trackable.GroupSelectionNeededEvent;
import delit.piwigoclient.ui.events.trackable.UsernameSelectionCompleteEvent;
import delit.piwigoclient.ui.events.trackable.UsernameSelectionNeededEvent;
import delit.piwigoclient.util.DisplayUtils;
import delit.piwigoclient.util.SetUtils;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;

/**
 * A fragment representing a list of Items.
 */
public class ViewAlbumFragment extends MyFragment {

    private static final String ARG_GALLERY = "gallery";
    private static final String STATE_GALLERY_MODEL = "GalleryModel";
    private static final String STATE_EDITING_ITEM_DETAILS = "editingItemDetails";
    private static final String STATE_INFORMATION_SHOWING = "informationShowing";
    private static final String STATE_CURRENT_USERS = "currentUsers";
    private static final String STATE_CURRENT_GROUPS = "currentGroups";
    private static final String STATE_GALLERY_DIRTY = "isGalleryDirty";
    private static final String STATE_GALLERY_ACTIVE_LOAD_THREADS = "activeLoadingThreads";
    private static final String STATE_GALLERY_LOADS_TO_RETRY = "retryLoadList";
    private static final String STATE_MOVED_RESOURCE_PARENT_UPDATE_NEEDED = "movedResourceParentUpdateRequired";
    public static final String STATE_UPDATE_ALBUM_DETAILS_PROGRESS = "updateAlbumDetailsProgress";
    public static final String STATE_USERNAME_SELECTION_WANTED_NEXT = "usernameSelectionWantedNext";
    public static final String STATE_DELETE_ACTION_DATA = "deleteActionData";
    private static final String STATE_USER_GUID = "userGuid";
    private static final int UPDATE_IN_PROGRESS = 1;
    private static final int UPDATE_SETTING_ADDING_PERMISSIONS = 2;
    private static final int UPDATE_SETTING_REMOVING_PERMISSIONS = 3;
    private static final int UPDATE_NOT_RUNNING = 0;

    private AlbumItemRecyclerViewAdapter viewAdapter;
    private FloatingActionButton retryActionButton;
    private ControllableBottomSheetBehavior<View> bottomSheetBehavior;
    private TextView galleryNameHeader;
    private TextView galleryDescriptionHeader;
    private ImageButton descriptionDropdownButton;
    private EditText galleryNameView;
    private EditText galleryDescriptionView;
    private CustomImageButton saveButton;
    private CustomImageButton discardButton;
    private CustomImageButton editButton;
    private CustomImageButton pasteButton;
    private CustomImageButton cutButton;
    private CustomImageButton deleteButton;
    private Switch galleryPrivacyStatusField;
    private TextView allowedGroupsField;
    private TextView allowedUsersField;
    private RelativeLayout bulkActionsContainer;
    private FloatingActionButton bulkActionButtonDelete;
    private FloatingActionButton bulkActionButtonCopy;
    private FloatingActionButton bulkActionButtonCut;
    private FloatingActionButton bulkActionButtonPaste;
    private View basketView;
    private TextView emptyGalleryLabel;
    private TextView allowedGroupsFieldLabel;
    private TextView allowedUsersFieldLabel;
    private CompoundButton.OnCheckedChangeListener checkedListener;
    private int albumsPerRow; // calculated each time view created.
    // Start fields maintained in saved session state.
    private CategoryItem gallery;
    private PiwigoAlbum galleryModel;
    private boolean editingItemDetails;
    private boolean informationShowing;
    private long[] currentUsers;
    private long[] currentGroups;
    private volatile boolean galleryIsDirty;
    private HashMap<Long, String> loadingMessageIds = new HashMap<>(2);
    private ArrayList<String> itemsToLoad = new ArrayList<>(0);
    private boolean movedResourceParentUpdateRequired;
    private HashSet<Long> userIdsInSelectedGroups;
    private int updateAlbumDetailsProgress = UPDATE_NOT_RUNNING;
    private boolean usernameSelectionWantedNext;
    private CustomImageButton addNewAlbumButton;
    private int colsOnScreen;
    private DeleteActionData deleteActionData;
    private long userGuid;


    /**
     * Mandatory empty constructor for the fragment manager to instantiate the
     * fragment (e.g. upon screen orientation changes).
     */
    public ViewAlbumFragment() {
    }

    public static ViewAlbumFragment newInstance(CategoryItem gallery) {
        ViewAlbumFragment fragment = new ViewAlbumFragment();
        Bundle args = new Bundle();
        args.putSerializable(ARG_GALLERY, gallery);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (getArguments() != null) {
            if (getArguments().containsKey(ARG_GALLERY)) {
                gallery = (CategoryItem) getArguments().getSerializable(ARG_GALLERY);
            }
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

    private int getDefaultAlbumColumnCount() {
        float screenWidth = getScreenWidth();
        int columnsToShow = Math.round(screenWidth - (screenWidth % 3)); // allow 3 inch per column
        return Math.max(1,columnsToShow);
    }

    private void fillGroupsField(TextView allowedGroupsField, Collection<Group> selectedGroups) {
        StringBuilder sb = new StringBuilder();
        Iterator<Group> groupIter = selectedGroups.iterator();
        while (groupIter.hasNext()) {
            sb.append(groupIter.next().getName());
            if (groupIter.hasNext()) {
                sb.append(", ");
            }
        }
        allowedGroupsField.setText(sb.toString());
    }

    private void fillUsernamesField(TextView allowedUsernamesField, Collection<Username> selectedUsernames) {
        StringBuilder sb = new StringBuilder();
        Iterator<Username> usernameIter = selectedUsernames.iterator();
        while (usernameIter.hasNext()) {
            sb.append(usernameIter.next().getUsername());
            if (usernameIter.hasNext()) {
                sb.append(", ");
            }
        }
        allowedUsernamesField.setText(sb.toString());
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putSerializable(ARG_GALLERY, gallery);
        outState.putSerializable(STATE_GALLERY_MODEL, galleryModel);
        outState.putBoolean(STATE_EDITING_ITEM_DETAILS, editingItemDetails);
        outState.putBoolean(STATE_INFORMATION_SHOWING, informationShowing);
        outState.putLongArray(STATE_CURRENT_GROUPS, currentGroups);
        outState.putLongArray(STATE_CURRENT_USERS, currentUsers);
        outState.putBoolean(STATE_GALLERY_DIRTY, galleryIsDirty);
        outState.putSerializable(STATE_GALLERY_ACTIVE_LOAD_THREADS, loadingMessageIds);
        outState.putSerializable(STATE_GALLERY_LOADS_TO_RETRY, itemsToLoad);
        outState.putBoolean(STATE_MOVED_RESOURCE_PARENT_UPDATE_NEEDED, movedResourceParentUpdateRequired);
        outState.putInt(STATE_UPDATE_ALBUM_DETAILS_PROGRESS, updateAlbumDetailsProgress);
        outState.putBoolean(STATE_USERNAME_SELECTION_WANTED_NEXT, usernameSelectionWantedNext);
        outState.putSerializable(STATE_DELETE_ACTION_DATA, deleteActionData);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull final LayoutInflater inflater, @Nullable final ViewGroup container, @Nullable Bundle savedInstanceState) {
        super.onCreateView(inflater, container, savedInstanceState);
        if(!PiwigoSessionDetails.isFullyLoggedIn()) {
            // force a reload of the gallery if the session has been destroyed.
            galleryIsDirty = true;
        } else if (savedInstanceState != null) {
            //restore saved state
            editingItemDetails = savedInstanceState.getBoolean(STATE_EDITING_ITEM_DETAILS);
            informationShowing = savedInstanceState.getBoolean(STATE_INFORMATION_SHOWING);
            currentUsers = savedInstanceState.getLongArray(STATE_CURRENT_USERS);
            currentGroups = savedInstanceState.getLongArray(STATE_CURRENT_GROUPS);
            galleryModel = (PiwigoAlbum) savedInstanceState.getSerializable(STATE_GALLERY_MODEL);
            gallery = (CategoryItem) savedInstanceState.getSerializable(ARG_GALLERY);
            // if galleryIsDirty then this fragment was updated while on the backstack - need to refresh it.
            userGuid = savedInstanceState.getLong(STATE_USER_GUID);
            galleryIsDirty = galleryIsDirty || PiwigoSessionDetails.getUserGuid() != userGuid;
            galleryIsDirty = galleryIsDirty || savedInstanceState.getBoolean(STATE_GALLERY_DIRTY);
            loadingMessageIds = (HashMap)savedInstanceState.getSerializable(STATE_GALLERY_ACTIVE_LOAD_THREADS);
            itemsToLoad = (ArrayList)savedInstanceState.getSerializable(STATE_GALLERY_LOADS_TO_RETRY);
            movedResourceParentUpdateRequired = savedInstanceState.getBoolean(STATE_MOVED_RESOURCE_PARENT_UPDATE_NEEDED);
            updateAlbumDetailsProgress = savedInstanceState.getInt(STATE_UPDATE_ALBUM_DETAILS_PROGRESS);
            usernameSelectionWantedNext = savedInstanceState.getBoolean(STATE_USERNAME_SELECTION_WANTED_NEXT);
            if(deleteActionData != null && deleteActionData.isEmpty()) {
                deleteActionData = null;
            } else {
                deleteActionData = (DeleteActionData) savedInstanceState.getSerializable(STATE_DELETE_ACTION_DATA);
            }
        }
        userGuid = PiwigoSessionDetails.getUserGuid();
        if(galleryModel == null) {
            galleryIsDirty = true;
            galleryModel = new PiwigoAlbum();
        }

        View view = inflater.inflate(R.layout.fragment_gallery, container, false);

        boolean useDarkMode = prefs.getBoolean(getString(R.string.preference_gallery_use_dark_mode_key), getResources().getBoolean(R.bool.preference_gallery_use_dark_mode_default));
        boolean showAlbumThumbnailsZoomed = prefs.getBoolean(getString(R.string.preference_gallery_show_album_thumbnail_zoomed_key), getResources().getBoolean(R.bool.preference_gallery_show_album_thumbnail_zoomed_default));

        if(useDarkMode) {
            view.setBackgroundColor(Color.BLACK);
        }

        initialiseBasketView(view);

        retryActionButton = view.findViewById(R.id.gallery_retryAction_actionButton);
        retryActionButton.setVisibility(GONE);
        retryActionButton.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                onReloadAlbum();
            }
        });

        emptyGalleryLabel = view.findViewById(R.id.album_empty_content);
        emptyGalleryLabel.setText(R.string.gallery_empty_text);
        emptyGalleryLabel.setVisibility(GONE);

        bulkActionsContainer = view.findViewById(R.id.gallery_actions_bulk_container);

        AlbumSelectedEvent event = new AlbumSelectedEvent(gallery);
        EventBus.getDefault().post(event);

        AdView adView = view.findViewById(R.id.gallery_adView);
        if(AdsManager.getInstance(getContext()).shouldShowAdverts()) {
            adView.setVisibility(VISIBLE);
            adView.loadAd(new AdRequest.Builder().build());
        } else {
            adView.setVisibility(GONE);
        }

        galleryNameHeader = view.findViewById(R.id.gallery_details_name_header);
        galleryDescriptionHeader = view.findViewById(R.id.gallery_details_description_header);
        descriptionDropdownButton = view.findViewById(R.id.gallery_details_description_dropdown_button);

        setGalleryHeadings();

        FloatingActionButton actionButton = view.findViewById(R.id.gallery_actionButton_details);

        View bottomSheet = view.findViewById(R.id.gallery_bottom_sheet);
        bottomSheetBehavior = ControllableBottomSheetBehavior.from(bottomSheet);

        int bottomSheetOffsetDp = prefs.getInt(getString(R.string.preference_gallery_detail_sheet_offset_key), getResources().getInteger(R.integer.preference_gallery_detail_sheet_offset_default));
        bottomSheetBehavior.setPeekHeight(DisplayUtils.dpToPx(getContext(), bottomSheetOffsetDp));

        if (gallery.isRoot()) {

            bottomSheet.setVisibility(GONE);
            actionButton.setVisibility(GONE);

        } else {

            bottomSheet.setVisibility(View.VISIBLE);
            actionButton.setVisibility(View.VISIBLE);
            actionButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    informationShowing = !informationShowing;
                    updateInformationShowingStatus();
                }
            });

            setupBottomSheet(bottomSheet);
        }

        int imagesOnScreen = selectBestColumnCountForScreenSize();
        albumsPerRow = getAlbumsPerRow();
        colsOnScreen = imagesOnScreen;


//        viewInOrientation = getResources().getConfiguration().orientation;

        // Set the adapter
        View galleryListView = view.findViewById(R.id.gallery_list);

        boolean showLargeAlbumThumbnails = prefs.getBoolean(getString(R.string.preference_gallery_show_large_thumbnail_key), getResources().getBoolean(R.bool.preference_gallery_show_large_thumbnail_default));

        RecyclerView recyclerView = (RecyclerView) galleryListView;

        if (!galleryIsDirty) {
            emptyGalleryLabel.setVisibility(galleryModel.getItems().size() == 0 ? VISIBLE : GONE);
        }

        boolean useMasonryStyle = prefs.getBoolean(getString(R.string.preference_gallery_masonry_view_key), getResources().getBoolean(R.bool.preference_gallery_masonry_view_default));

        // need to wait for the gallery model to be initialised.
        RecyclerView.LayoutManager gridLayoutMan;
        if(useMasonryStyle) {
            gridLayoutMan = new StaggeredGridLayoutManager(colsOnScreen, StaggeredGridLayoutManager.VERTICAL);
        } else {
            if(imagesOnScreen % albumsPerRow > 0) {
                colsOnScreen = imagesOnScreen * albumsPerRow;
            }
            gridLayoutMan = new GridLayoutManager(getContext(), colsOnScreen);
        }

        recyclerView.setLayoutManager(gridLayoutMan);

        if(!useMasonryStyle) {
            int colsPerAlbum = colsOnScreen / albumsPerRow;
            int colsPerImage = colsOnScreen / imagesOnScreen;
            ((GridLayoutManager)gridLayoutMan).setSpanSizeLookup(new SpanSizeLookup(galleryModel, colsPerAlbum, colsPerImage));
        }

        int recentlyAlteredThresholdAge = prefs.getInt(getString(R.string.preference_gallery_recentlyAlteredAgeMillis_key), getResources().getInteger(R.integer.preference_gallery_recentlyAlteredAgeMillis_default));
        Date recentlyAlteredThresholdDate = new Date(System.currentTimeMillis() - recentlyAlteredThresholdAge);

        boolean captureActionClicks = PiwigoSessionDetails.isAdminUser() && !isAppInReadOnlyMode();
        captureActionClicks &= (getBasket().getItemCount() == 0 || getBasket().getContentParentId() == gallery.getId());

        viewAdapter = new AlbumItemRecyclerViewAdapter(galleryModel, recentlyAlteredThresholdDate, new AlbumItemRecyclerViewAdapter.MultiSelectStatusListener() {
            @Override
            public void onMultiSelectStatusChanged(boolean multiSelectEnabled) {
//                bulkActionsContainer.setVisibility(multiSelectEnabled?VISIBLE:GONE);
            }

            @Override
            public void onItemSelectionCountChanged(int size) {
                bulkActionsContainer.setVisibility(size > 0 || getBasket().getItemCount() > 0 ?VISIBLE:GONE);
                updateBasketDisplay(getBasket());
            }

            @Override
            public void onCategoryLongClick(CategoryItem album) {
                onAlbumDeleteRequest(album);
            }
        }, captureActionClicks);

        boolean showResourceNames = prefs.getBoolean(getString(R.string.preference_gallery_show_image_name_key), getResources().getBoolean(R.bool.preference_gallery_show_image_name_default));

        viewAdapter.setUseDarkMode(useDarkMode);
        viewAdapter.setShowLargeAlbumThumbnails(showLargeAlbumThumbnails);
        viewAdapter.setShowAlbumThumbnailsZoomed(showAlbumThumbnailsZoomed);
        viewAdapter.setMasonryStyle(useMasonryStyle);
        viewAdapter.setAlbumWidth(getScreenWidth() / albumsPerRow);
        viewAdapter.setShowResourceNames(showResourceNames);

        Basket basket = getBasket();

        bulkActionsContainer.setVisibility(viewAdapter.isItemSelectionAllowed()||getBasket().getItemCount() > 0?VISIBLE:GONE);

        bulkActionButtonDelete = bulkActionsContainer.findViewById(R.id.gallery_action_delete_bulk);
        bulkActionButtonDelete.setVisibility(viewAdapter.isItemSelectionAllowed() && basket.getItemCount() == 0?VISIBLE:GONE);
        bulkActionButtonDelete.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if(event.getActionMasked() == MotionEvent.ACTION_UP) {
                    onBulkActionDeleteButtonPressed();
                }
                return true; // consume the event
            }
        });

        bulkActionButtonCopy = bulkActionsContainer.findViewById(R.id.gallery_action_copy_bulk);
        bulkActionButtonCopy.setVisibility(viewAdapter.isItemSelectionAllowed() && (basket.getItemCount() == 0 || gallery.getId() == basket.getContentParentId())?VISIBLE:GONE);
        bulkActionButtonCopy.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                boolean bulkActionsAllowed = PiwigoSessionDetails.isAdminUser() && !isAppInReadOnlyMode();
                if(bulkActionsAllowed && event.getActionMasked() == MotionEvent.ACTION_UP){
                    addItemsToBasket(Basket.ACTION_COPY);
                }
                return true; // consume the event
            }
        });

        bulkActionButtonCut = bulkActionsContainer.findViewById(R.id.gallery_action_cut_bulk);
        bulkActionButtonCut.setVisibility(viewAdapter.isItemSelectionAllowed() && (basket.getItemCount() == 0 || gallery.getId() == basket.getContentParentId())?VISIBLE:GONE);
        bulkActionButtonCut.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                boolean bulkActionsAllowed = PiwigoSessionDetails.isAdminUser() && !isAppInReadOnlyMode();
                if(bulkActionsAllowed && event.getActionMasked() == MotionEvent.ACTION_UP){
                    addItemsToBasket(Basket.ACTION_CUT);
                }
                return true; // consume the event
            }
        });

        bulkActionButtonPaste = bulkActionsContainer.findViewById(R.id.gallery_action_paste_bulk);
        bulkActionButtonPaste.setVisibility(!viewAdapter.isItemSelectionAllowed() && basket.getItemCount() > 0 && gallery.getId() != PiwigoAlbum.ROOT_ALBUM.getId() && gallery.getId() != basket.getContentParentId()?VISIBLE:GONE);
        bulkActionButtonPaste.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                boolean bulkActionsAllowed = PiwigoSessionDetails.isAdminUser() && !isAppInReadOnlyMode();
                if(bulkActionsAllowed && event.getActionMasked() == MotionEvent.ACTION_UP){
                    final Basket basket = getBasket();
                    int msgPatternId = -1;
                    if(basket.getAction() == Basket.ACTION_COPY) {
                        msgPatternId = R.string.alert_confirm_copy_items_here_pattern;
                    } else if(basket.getAction() == Basket.ACTION_CUT) {
                        msgPatternId = R.string.alert_confirm_move_items_here_pattern;
                    }
                    String message = String.format(getString(msgPatternId), basket.getItemCount(), gallery.getName());

                    getUiHelper().showOrQueueDialogQuestion(R.string.alert_confirm_title, message, R.string.button_no, R.string.button_yes, new UIHelper.QuestionResultListener() {
                        @Override
                        public void onDismiss(AlertDialog dialog) {

                        }

                        @Override
                        public void onResult(AlertDialog dialog, Boolean positiveAnswer) {
                            if(Boolean.TRUE == positiveAnswer) {
                                if(basket.getAction() == Basket.ACTION_COPY) {
                                    HashSet<ResourceItem> itemsToCopy = basket.getContents();
                                    CategoryItem copyToAlbum = gallery;
                                    for(ResourceItem itemToCopy : itemsToCopy) {
                                        addActiveServiceCall(R.string.progress_copy_resources, PiwigoAccessService.startActionCopyItemsToAlbum(itemToCopy, copyToAlbum, getContext()));
                                    }
                                } else if(basket.getAction() == Basket.ACTION_CUT) {
                                    HashSet<ResourceItem> itemsToMove = basket.getContents();
                                    CategoryItem moveToAlbum = gallery;
                                    for(ResourceItem itemToMove : itemsToMove) {
                                        addActiveServiceCall(R.string.progress_move_resources, PiwigoAccessService.startActionMoveItemsToAlbum(itemToMove, moveToAlbum, getContext()));
                                    }
                                }
                            }
                        }
                    });
                }
                return true; // consume the event
            }
        });

        recyclerView.setAdapter(viewAdapter);


        EndlessRecyclerViewScrollListener scrollListener = new EndlessRecyclerViewScrollListener(gridLayoutMan) {
            @Override
            public void onLoadMore(int page, int totalItemsCount, RecyclerView view) {
                int pageToLoad = galleryModel.getPagesLoaded();
                if (pageToLoad == 0 || galleryModel.getResourcesCount() == gallery.getTotalPhotos()) {
                    // already load this one by default so lets not double load it (or we've already loaded all items).
                    return;
                }
                loadAlbumResourcesPage(pageToLoad);
            }
        };
        scrollListener.configure(galleryModel.getPagesLoaded(), galleryModel.getItems().size());
        recyclerView.addOnScrollListener(scrollListener);


        //display bottom sheet if needed
        updateInformationShowingStatus();

        return view;
    }

    private void onBulkActionDeleteButtonPressed() {
        boolean bulkActionsAllowed = PiwigoSessionDetails.isAdminUser() && !isAppInReadOnlyMode();
        if(bulkActionsAllowed){
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

    private int getAlbumsPerRow() {
        int albumsPerRow = getDefaultAlbumColumnCount();
        if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
            albumsPerRow = prefs.getInt(getString(R.string.preference_gallery_albums_preferredColumnsLandscape_key), albumsPerRow);
        } else {
            albumsPerRow = prefs.getInt(getString(R.string.preference_gallery_albums_preferredColumnsPortrait_key), albumsPerRow);
        }
        return albumsPerRow;
    }

    private void initialiseBasketView(View v) {
        basketView = v.findViewById(R.id.basket);

        basketView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                // sink events without action.
                return true;
            }
        });

        AppCompatImageView clearButton = basketView.findViewById(R.id.basket_clear_button);
        clearButton.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if(event.getActionMasked() == MotionEvent.ACTION_UP) {
                    Basket basket = getBasket();
                    basket.clear();
                    updateBasketDisplay(basket);
                }
                return true;
            }
        });
    }

    private Basket getBasket() {
        MainActivity activity = (MainActivity)getActivity();
        Basket basket = activity.getBasket();
        return basket;
    }

    private void addItemsToBasket(int action) {
        Basket basket = getBasket();

        Set<Long> selectedItems = viewAdapter.getSelectedItemIds();
        for(GalleryItem item : galleryModel.getItems()) {
            if(selectedItems.contains(item.getId())) {
                basket.addItem(action, (ResourceItem)item, gallery);
            }
        }
        updateBasketDisplay(basket);
    }

    private void updateBasketDisplay(Basket basket) {

        boolean captureActionClicks = PiwigoSessionDetails.isAdminUser() && !isAppInReadOnlyMode();
        captureActionClicks &= (getBasket().getItemCount() == 0 || getBasket().getContentParentId() == gallery.getId());
        viewAdapter.setCaptureActionClicks(captureActionClicks);

        int basketItemCount = basket.getItemCount();
        if(basketItemCount == 0) {
            basketView.setVisibility(GONE);
        } else {
            basketView.setVisibility(VISIBLE);
            AppCompatTextView basketItemCountField = basketView.findViewById(R.id.basket_item_count);
            basketItemCountField.setText(String.valueOf(basketItemCount));

            AppCompatImageView actionIndicatorImg = basketView.findViewById(R.id.basket_action_indicator);
            if(basket.getAction() == Basket.ACTION_COPY) {
                actionIndicatorImg.setImageResource(R.drawable.ic_content_copy_black_24px);
            } else {
                actionIndicatorImg.setImageResource(R.drawable.ic_content_cut_black_24px);
            }
        }

        bulkActionsContainer.setVisibility(viewAdapter.isItemSelectionAllowed()||getBasket().getItemCount() > 0?VISIBLE:GONE);
        bulkActionButtonDelete.setVisibility(viewAdapter.isItemSelectionAllowed()&& basket.getItemCount() == 0?VISIBLE:GONE);
        bulkActionButtonCopy.setVisibility(viewAdapter.isItemSelectionAllowed()&& (basket.getItemCount() == 0 || gallery.getId() == basket.getContentParentId())?VISIBLE:GONE);
        bulkActionButtonCut.setVisibility(viewAdapter.isItemSelectionAllowed()&& (basket.getItemCount() == 0 || gallery.getId() == basket.getContentParentId())?VISIBLE:GONE);
        bulkActionButtonPaste.setVisibility(!viewAdapter.isItemSelectionAllowed() && basket.getItemCount() > 0 && gallery.getId() != PiwigoAlbum.ROOT_ALBUM.getId() && gallery.getId() != basket.getContentParentId()?VISIBLE:GONE);

    }

    private void onAlbumDeleteRequest(final CategoryItem album) {
        String msg = String.format(getString(R.string.alert_confirm_really_delete_album_from_server_pattern),album.getName());
        getUiHelper().showOrQueueDialogQuestion(R.string.alert_confirm_title, msg, R.string.button_no, R.string.button_yes, new UIHelper.QuestionResultListener() {
            @Override
            public void onDismiss(AlertDialog dialog) {

            }

            @Override
            public void onResult(AlertDialog dialog, Boolean positiveAnswer) {
                if(Boolean.TRUE == positiveAnswer) {
                    String msg = String.format(getString(R.string.alert_confirm_really_really_delete_album_from_server_pattern),album.getName(), album.getPhotoCount(), album.getSubCategories(), album.getTotalPhotos() - album.getPhotoCount());
                    getUiHelper().showOrQueueDialogQuestion(R.string.alert_confirm_title, msg, R.string.button_no, R.string.button_yes, new UIHelper.QuestionResultListener() {
                        @Override
                        public void onDismiss(AlertDialog dialog) {
                        }

                        @Override
                        public void onResult(AlertDialog dialog, Boolean positiveAnswer) {
                            if(Boolean.TRUE == positiveAnswer) {
                                addActiveServiceCall(R.string.progress_delete_album, PiwigoAccessService.startActionDeleteGallery(album.getId(),getContext()));
                            }
                        }
                    });
                }
            }
        });
    }

    private static class DeleteActionData implements Serializable {
        final HashSet<Long> selectedItemIds;
        final HashSet<Long> itemsUpdated;
        final HashSet<ResourceItem> selectedItems;
        boolean resourceInfoAvailable;
        private ResourceItem[] itemsWithoutLinkedAlbumData;

        public DeleteActionData(HashSet<Long> selectedItemIds, HashSet<ResourceItem> selectedItems) {
            this.selectedItemIds = selectedItemIds;
            this.selectedItems = selectedItems;
            this.resourceInfoAvailable = false; //FIXME when Piwigo provides this info as standard, this can be removed and the method simplified.
            itemsUpdated = new HashSet<>(selectedItemIds.size());
        }

        public void updateLinkedAlbums(ResourceItem item) {
            itemsUpdated.add(item.getId());
            if(itemsUpdated.size() == selectedItemIds.size()) {
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
            if(itemsUpdated.size() == 0) {
                return selectedItems;
            }
            Set<ResourceItem> itemsWithoutLinkedAlbumData = new HashSet<>();
            for(ResourceItem r : selectedItems) {
                if(!itemsUpdated.contains(r.getId())) {
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
            for(Long deletedResourceId : deletedItemIds) {
                selectedItemIds.remove(deletedResourceId);
                itemsUpdated.remove(deletedResourceId);
            }
            for (Iterator<ResourceItem> it = selectedItems.iterator(); it.hasNext(); ) {
                ResourceItem r = it.next();
                if(deletedItemIds.contains(r.getId())) {
                    it.remove();
                }
            }
            return selectedItemIds.size() == 0;
        }

        public boolean isEmpty() {
            return selectedItemIds.isEmpty();
        }
    }

    private void onDeleteResources(final DeleteActionData deleteActionData) {
        final HashSet<ResourceItem> sharedResources = new HashSet();
        if (deleteActionData.isResourceInfoAvailable()) {
            //TODO currently, this won't work. No piwigo support
            for (ResourceItem item : deleteActionData.getSelectedItems()) {
                if (item.getLinkedAlbums().size() > 1) {
                    sharedResources.add(item);
                }
            }
        } else {
            for (ResourceItem item : deleteActionData.getItemsWithoutLinkedAlbumData()) {
                addActiveServiceCall(R.string.progress_loading_resource_details, PiwigoAccessService.startActionGetResourceInfo(item, getContext()));
            }
            return;
        }
        if (sharedResources.size() > 0) {
            String msg = getString(R.string.alert_confirm_delete_items_from_server_or_just_unlink_them_from_this_album_pattern, sharedResources.size());
            getUiHelper().showOrQueueDialogQuestion(R.string.alert_confirm_title, msg, Integer.MIN_VALUE, R.string.button_unlink, R.string.button_cancel, R.string.button_delete, new UIHelper.QuestionResultListener() {
                @Override
                public void onDismiss(AlertDialog dialog) {

                }

                @Override
                public void onResult(AlertDialog dialog, Boolean positiveAnswer) {
                    if (Boolean.TRUE == positiveAnswer) {
                        addActiveServiceCall(R.string.progress_delete_resources, PiwigoAccessService.startActionDeleteGalleryItemsFromServer(deleteActionData.getSelectedItemIds(), getContext()));
                    } else if (Boolean.FALSE == positiveAnswer) {
                        HashSet<Long> itemIdsForPermananentDelete = (HashSet<Long>) deleteActionData.getSelectedItemIds().clone();
                        HashSet<ResourceItem> itemsForPermananentDelete = (HashSet<ResourceItem>) deleteActionData.getSelectedItems().clone();
                        for (ResourceItem item : sharedResources) {
                            itemIdsForPermananentDelete.remove(item.getId());
                            itemsForPermananentDelete.remove(item);
                            item.getLinkedAlbums().remove(gallery.getId());
                            addActiveServiceCall(R.string.progress_unlink_resources, PiwigoAccessService.startActionUpdateResourceInfo(item, getContext()));
                        }
                        //now we need to delete the rest.
                        deleteResourcesFromServerForever(itemIdsForPermananentDelete, itemsForPermananentDelete);
                    }
                }
            });
        } else {
            deleteResourcesFromServerForever(deleteActionData.getSelectedItemIds(), deleteActionData.getSelectedItems());
        }

    }

    public void deleteResourcesFromServerForever(final HashSet<Long> selectedItemIds, final HashSet<? extends ResourceItem> selectedItems) {
        String msg = getString(R.string.alert_confirm_really_delete_items_from_server);
        getUiHelper().showOrQueueDialogQuestion(R.string.alert_confirm_title, msg, R.string.button_cancel, R.string.button_ok, new UIHelper.QuestionResultListener() {
            @Override
            public void onDismiss(AlertDialog dialog) {

            }

            @Override
            public void onResult(AlertDialog dialog, Boolean positiveAnswer) {
                if(Boolean.TRUE == positiveAnswer) {
                    addActiveServiceCall(R.string.progress_delete_resources, PiwigoAccessService.startActionDeleteGalleryItemsFromServer(selectedItemIds, getContext()));
                }
            }
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        if (galleryIsDirty) {
            reloadAlbumContent();
        } else if(itemsToLoad.size() > 0) {
            onReloadAlbum();
        } else {
            int spacerAlbumsNeeded = galleryModel.getSubAlbumCount() % albumsPerRow;
            if(spacerAlbumsNeeded > 0) {
                spacerAlbumsNeeded = albumsPerRow - spacerAlbumsNeeded;
            }
            galleryModel.setSpacerAlbumCount(spacerAlbumsNeeded);
            viewAdapter.notifyDataSetChanged();
        }

        updateBasketDisplay(getBasket());

    }

    private void reloadAlbumContent() {

        if(galleryIsDirty) {
            galleryIsDirty = false;
            if(loadingMessageIds.size() > 0) {
                // already a load in progress - ignore this call.
                //TODO be cleverer - check the time the call was invoked and queue another if needed.
                return;
            }
            galleryModel.clear();
            viewAdapter.notifyDataSetChanged();
            loadAlbumSubCategories();
            loadAlbumResourcesPage(0);
        }

    }

    private void loadAlbumResourcesPage(int pageToLoad) {
        synchronized (loadingMessageIds) {
            Set<String> activeCalls = new HashSet<>(loadingMessageIds.values());
            if (activeCalls.contains(String.valueOf(pageToLoad))) {
                // already loading this page, ignore the request.
                return;
            }
            String sortOrder = prefs.getString(getString(R.string.preference_gallery_sortOrder_key), getString(R.string.preference_gallery_sortOrder_default));
            String multimediaExtensionList = PreferenceManager.getDefaultSharedPreferences(getContext()).getString(getString(R.string.preference_piwigo_playable_media_extensions_key), getString(R.string.preference_piwigo_playable_media_extensions_default));
            int pageSize = prefs.getInt(getString(R.string.preference_album_request_pagesize_key), getResources().getInteger(R.integer.preference_album_request_pagesize_default));
            long loadingMessageId = PiwigoAccessService.startActionGetResources(gallery, sortOrder, pageToLoad, pageSize, multimediaExtensionList, getContext());
            loadingMessageIds.put(loadingMessageId, String.valueOf(pageToLoad));
            addActiveServiceCall(R.string.progress_loading_album_content, loadingMessageId);
        }
    }

    private void loadAlbumSubCategories() {
        synchronized (loadingMessageIds) {
            boolean recursive = false;
            long loadingMessageId = PiwigoAccessService.startActionGetSubCategories(gallery, recursive, getContext());
            loadingMessageIds.put(loadingMessageId, "C");
            addActiveServiceCall(R.string.progress_loading_album_content, loadingMessageId);
        }
    }

    private void updateInformationShowingStatus() {
        if (informationShowing  ) {
            bottomSheetBehavior.setState(BottomSheetBehavior.STATE_EXPANDED);
            if (currentGroups == null) {
                // haven't yet loaded the existing permissions - do this now.
                checkedListener.onCheckedChanged(galleryPrivacyStatusField, galleryPrivacyStatusField.isChecked());
            }
        } else {
            bottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
        }
    }

    private void setupBottomSheet(final View bottomSheet) {
        galleryNameView = bottomSheet.findViewById(R.id.gallery_details_name);
        galleryNameView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {

                if (galleryNameView.getLineCount() > galleryNameView.getMaxLines()) {
                    bottomSheetBehavior.setAllowUserDragging(event.getActionMasked() == MotionEvent.ACTION_UP);
                }
                return false;
            }
        });
        galleryDescriptionView = bottomSheet.findViewById(R.id.gallery_details_description);
        galleryDescriptionView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (galleryDescriptionView.getLineCount() > galleryDescriptionView.getMaxLines()) {
                    bottomSheetBehavior.setAllowUserDragging(event.getActionMasked() == MotionEvent.ACTION_UP);
                }
                return false;
            }
        });

        saveButton = bottomSheet.findViewById(R.id.gallery_details_save_button);
        saveButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                gallery.setName(galleryNameView.getText().toString());
                gallery.setDescription(galleryDescriptionView.getText().toString());
                gallery.setPrivate(galleryPrivacyStatusField.isChecked());
                updateAlbumDetails();
            }
        });
        discardButton = bottomSheet.findViewById(R.id.gallery_details_discard_button);
        discardButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                editingItemDetails = false;
                currentUsers = gallery.getUsers();
                currentGroups = gallery.getGroups();
                fillGalleryEditFields();
            }
        });
        editButton = bottomSheet.findViewById(R.id.gallery_details_edit_button);
        editButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                editingItemDetails = true;
                fillGalleryEditFields();
            }
        });

        addNewAlbumButton = bottomSheet.findViewById(R.id.album_add_new_album_button);
        addNewAlbumButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                AlbumCreateNeededEvent event = new AlbumCreateNeededEvent(gallery.toStub());
                getUiHelper().setTrackingRequest(event.getActionId());
                EventBus.getDefault().post(event);
            }
        });

        View galleryEditFieldsView = bottomSheet.findViewById(R.id.gallery_details_edit_fields);

        if (PiwigoAlbum.ROOT_ALBUM == gallery) {
            galleryEditFieldsView.setVisibility(GONE);

        } else {
            galleryEditFieldsView.setVisibility(View.VISIBLE);
        }

        allowedGroupsFieldLabel = bottomSheet.findViewById(R.id.gallery_details_allowed_groups_label);
        allowedGroupsField = bottomSheet.findViewById(R.id.gallery_details_allowed_groups);
        allowedGroupsField.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                HashSet<Long> groups = new HashSet<>();
                if(currentGroups != null) {
                    for (long groupId : currentGroups) {
                        groups.add(groupId);
                    }
                }
                GroupSelectionNeededEvent groupSelectionNeededEvent = new GroupSelectionNeededEvent(true, editingItemDetails, groups);
                getUiHelper().setTrackingRequest(groupSelectionNeededEvent.getActionId());
                EventBus.getDefault().post(groupSelectionNeededEvent);
            }
        });

        allowedUsersFieldLabel = bottomSheet.findViewById(R.id.gallery_details_allowed_users_label);
        allowedUsersField = bottomSheet.findViewById(R.id.gallery_details_allowed_users);
        allowedUsersField.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(currentGroups == null || currentGroups.length == 0 || userIdsInSelectedGroups != null) {
                    if(userIdsInSelectedGroups == null) {
                        userIdsInSelectedGroups = new HashSet<>(0);
                    }
                    HashSet<Long> preselectedUsernames = getSetFromArray(currentUsers);
                    UsernameSelectionNeededEvent usernameSelectionNeededEvent = new UsernameSelectionNeededEvent(true, editingItemDetails, userIdsInSelectedGroups, preselectedUsernames);
                    getUiHelper().setTrackingRequest(usernameSelectionNeededEvent.getActionId());
                    EventBus.getDefault().post(usernameSelectionNeededEvent);
                } else {
                    ArrayList<Long> selectedGroupIds = new ArrayList<>(currentGroups.length);
                    for(long groupId : currentGroups) {
                        selectedGroupIds.add(groupId);
                    }
                    usernameSelectionWantedNext = true;
                    addActiveServiceCall(R.string.progress_loading_group_details,PiwigoAccessService.startActionGetUsernamesList(selectedGroupIds, 0, 100, getContext()));
                }
            }
        });

        galleryPrivacyStatusField = bottomSheet.findViewById(R.id.gallery_details_status);
        galleryPrivacyStatusField.setChecked(gallery.isPrivate());
        checkedListener = new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    if(PiwigoSessionDetails.isAdminUser()) {
                        loadAlbumPermissions();
                    }
                }
                allowedGroupsFieldLabel.setVisibility(isChecked?VISIBLE:GONE);
                allowedGroupsField.setVisibility(isChecked?VISIBLE:GONE);
                allowedUsersFieldLabel.setVisibility(isChecked?VISIBLE:GONE);
                allowedUsersField.setVisibility(isChecked?VISIBLE:GONE);
            }
        };
        galleryPrivacyStatusField.setOnCheckedChangeListener(checkedListener);

        deleteButton = bottomSheet.findViewById(R.id.gallery_action_delete);
        deleteButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onAlbumDeleteRequest(gallery);
            }
        });

        pasteButton = bottomSheet.findViewById(R.id.gallery_action_paste);
        pasteButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onMoveItem(gallery);
            }
        });
        cutButton = bottomSheet.findViewById(R.id.gallery_action_cut);
        cutButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onCopyItem(gallery);
            }
        });

        fillGalleryEditFields();
    }

    private void loadAlbumPermissions() {
        addActiveServiceCall(R.string.progress_loading_album_permissions, PiwigoAccessService.startActionGetAlbumPermissions(gallery, getContext()));
    }

    private void fillGalleryEditFields() {
        if (gallery.getName() != null && !gallery.getName().isEmpty()) {
            galleryNameView.setText(gallery.getName());
        } else {
            galleryNameView.setText("");
        }
        if (gallery.getDescription() != null && !gallery.getDescription().isEmpty()) {
            galleryDescriptionView.setText(gallery.getDescription());
        } else {
            galleryDescriptionView.setText("");
        }
        allowedGroupsField.setText(R.string.click_to_view);
        allowedUsersField.setText(R.string.click_to_view);
        galleryPrivacyStatusField.setChecked(gallery.isPrivate());
        if (currentUsers != null) {
            allowedUsersField.setText(String.format(getString(R.string.click_to_view_pattern), currentUsers.length));
        }
        if (currentGroups != null) {
            allowedGroupsField.setText(String.format(getString(R.string.click_to_view_pattern), currentGroups.length));
        }
        displayControlsBasedOnSessionState();
        setEditItemDetailsControlsStatus();
    }

    private void setEditItemDetailsControlsStatus() {
        addNewAlbumButton.setEnabled(!editingItemDetails);

        galleryNameView.setEnabled(editingItemDetails);
        galleryDescriptionView.setEnabled(editingItemDetails);
        galleryPrivacyStatusField.setEnabled(editingItemDetails);
        allowedUsersField.setEnabled(true); // Always enabled (but is read only when not editing)
        allowedGroupsField.setEnabled(true); // Always enabled (but is read only when not editing)

        editButton.setVisibility(PiwigoSessionDetails.isAdminUser() && !isAppInReadOnlyMode() && !editingItemDetails ? VISIBLE : GONE);

        saveButton.setVisibility(editingItemDetails ? VISIBLE : GONE);
        saveButton.setEnabled(editingItemDetails);
        discardButton.setVisibility(editingItemDetails ? VISIBLE : GONE);
        discardButton.setEnabled(editingItemDetails);
    }

    private void displayControlsBasedOnSessionState() {
        if (PiwigoSessionDetails.isAdminUser() && !isAppInReadOnlyMode()) {
            saveButton.setVisibility(VISIBLE);
            discardButton.setVisibility(VISIBLE);
            editButton.setVisibility(VISIBLE);
            deleteButton.setVisibility(VISIBLE);
            addNewAlbumButton.setVisibility(VISIBLE);
            //TODO make visible once functionality written.
            cutButton.setVisibility(GONE);
            pasteButton.setVisibility(GONE);
        } else {
            addNewAlbumButton.setVisibility(GONE);
            saveButton.setVisibility(GONE);
            discardButton.setVisibility(GONE);
            editButton.setVisibility(GONE);
            deleteButton.setVisibility(GONE);
            cutButton.setVisibility(GONE);
            pasteButton.setVisibility(GONE);
        }

        int visibility = PiwigoSessionDetails.isAdminUser()? VISIBLE : GONE;

        allowedGroupsFieldLabel.setVisibility(visibility);
        allowedGroupsField.setVisibility(visibility);
        allowedUsersFieldLabel.setVisibility(visibility);
        allowedUsersField.setVisibility(visibility);
    }

    private void setGalleryHeadings() {
        if (gallery.getName() != null && !gallery.getName().isEmpty() && PiwigoAlbum.ROOT_ALBUM != gallery) {
            galleryNameHeader.setText(gallery.getName());
            galleryNameHeader.setVisibility(View.VISIBLE);
        } else {
            galleryNameHeader.setVisibility(GONE);
        }


        if (gallery.getDescription() != null && !gallery.getDescription().isEmpty() && PiwigoAlbum.ROOT_ALBUM != gallery) {
            galleryDescriptionHeader.setText(gallery.getDescription());
        }
        galleryDescriptionHeader.setVisibility(GONE);


        if (gallery.getDescription() != null && !gallery.getDescription().isEmpty()) {
            descriptionDropdownButton.setVisibility(View.VISIBLE);
            descriptionDropdownButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (galleryDescriptionHeader.getVisibility() == GONE) {
                        galleryDescriptionHeader.setVisibility(View.VISIBLE);
                    } else {
                        galleryDescriptionHeader.setVisibility(GONE);
                    }
                }
            });
        } else {
            descriptionDropdownButton.setVisibility(GONE);
        }
    }

    private int selectBestColumnCountForScreenSize() {
        int mColumnCount = getDefaultImagesColumnCount();
        if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
            mColumnCount = prefs.getInt(getString(R.string.preference_gallery_images_preferredColumnsLandscape_key), mColumnCount);
        } else {
            mColumnCount = prefs.getInt(getString(R.string.preference_gallery_images_preferredColumnsPortrait_key), mColumnCount);
        }
        return Math.max(1,mColumnCount);
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        EventBus.getDefault().register(this);
    }

    private void onMoveItem(CategoryItem model) {
        //TODO implement this
        getUiHelper().showOrQueueDialogMessage(R.string.alert_error, getString(R.string.alert_error_unimplemented));
    }

    private void onCopyItem(CategoryItem model) {
        //TODO implement this
        getUiHelper().showOrQueueDialogMessage(R.string.alert_error, getString(R.string.alert_error_unimplemented));
    }

    @Override
    public void onDetach() {
        super.onDetach();
        EventBus.getDefault().unregister(this);
    }

    @Override
    protected BasicPiwigoResponseListener buildPiwigoResponseListener(Context context) {
        return new CustomPiwigoResponseListener();
    }

    private class CustomPiwigoResponseListener extends BasicPiwigoResponseListener {
        @Override
        public void onAfterHandlePiwigoResponse(PiwigoResponseBufferingHandler.Response response) {
            synchronized (loadingMessageIds) {

                if (response instanceof PiwigoResponseBufferingHandler.PiwigoDeleteImageResponse) {
                    onResourcesDeleted((PiwigoResponseBufferingHandler.PiwigoDeleteImageResponse) response);
                } else if (response instanceof PiwigoResponseBufferingHandler.PiwigoGetSubAlbumsResponse) {
                    onGetSubGalleries((PiwigoResponseBufferingHandler.PiwigoGetSubAlbumsResponse) response);
                } else if (response instanceof PiwigoResponseBufferingHandler.PiwigoGetResourcesResponse) {
                    onGetResources((PiwigoResponseBufferingHandler.PiwigoGetResourcesResponse) response);
                } else if (response instanceof PiwigoResponseBufferingHandler.PiwigoAlbumDeletedResponse) {
                    onAlbumDeleted((PiwigoResponseBufferingHandler.PiwigoAlbumDeletedResponse) response);
                } else if (response instanceof PiwigoResponseBufferingHandler.PiwigoAlbumPermissionsRetrievedResponse) {
                    onAlbumPermissionsRetrieved((PiwigoResponseBufferingHandler.PiwigoAlbumPermissionsRetrievedResponse) response);
                } else if (response instanceof PiwigoResponseBufferingHandler.PiwigoUpdateAlbumInfoResponse) {
                    onAlbumInfoAltered((PiwigoResponseBufferingHandler.PiwigoUpdateAlbumInfoResponse) response);
                } else if (response instanceof PiwigoResponseBufferingHandler.PiwigoUpdateAlbumContentResponse) {
                    onAlbumContentAltered((PiwigoResponseBufferingHandler.PiwigoUpdateAlbumContentResponse) response);
                } else if (response instanceof PiwigoResponseBufferingHandler.PiwigoUpdateResourceInfoResponse) {
                    if (deleteActionData != null && !deleteActionData.isEmpty()) {
                        //currently mid delete of resources.
                        onResourceUnlinked((PiwigoResponseBufferingHandler.PiwigoUpdateResourceInfoResponse) response);
                    } else {
                        onResourceMoved((PiwigoResponseBufferingHandler.PiwigoUpdateResourceInfoResponse) response);
                    }
                } else if (response instanceof PiwigoResponseBufferingHandler.PiwigoAlbumThumbnailUpdatedResponse) {
                    onThumbnailUpdated((PiwigoResponseBufferingHandler.PiwigoAlbumThumbnailUpdatedResponse) response);
                } else if (response instanceof PiwigoResponseBufferingHandler.PiwigoGetUsernamesListResponse) {
                    onUsernamesRetrievedForSelectedGroups((PiwigoResponseBufferingHandler.PiwigoGetUsernamesListResponse) response);
                } else if (response instanceof PiwigoResponseBufferingHandler.PiwigoSetAlbumStatusResponse) {
                    onAlbumStatusUpdated((PiwigoResponseBufferingHandler.PiwigoSetAlbumStatusResponse) response);
                } else if (response instanceof PiwigoResponseBufferingHandler.PiwigoAddAlbumPermissionsResponse) {
                    onAlbumPermissionsAdded((PiwigoResponseBufferingHandler.PiwigoAddAlbumPermissionsResponse) response);
                } else if (response instanceof PiwigoResponseBufferingHandler.PiwigoRemoveAlbumPermissionsResponse) {
                    onAlbumPermissionsRemoved((PiwigoResponseBufferingHandler.PiwigoRemoveAlbumPermissionsResponse) response);
                } else if (response instanceof PiwigoResponseBufferingHandler.PiwigoResourceInfoRetrievedResponse) {
                    onResouceInfoRetrieved((PiwigoResponseBufferingHandler.PiwigoResourceInfoRetrievedResponse) response);
                } else {
                    String failedCall = loadingMessageIds.get(response.getMessageId());
                    if (failedCall == null) {
                        if (editingItemDetails) {
                            failedCall = "U";
                        } else {
                            failedCall = "P";
                        }
                    }
                    synchronized (itemsToLoad) {
                        itemsToLoad.add(failedCall);
                        switch(failedCall) {
                            case "U":
                                emptyGalleryLabel.setText(R.string.gallery_update_failed_text);
                                break;
                            case "P":
                                emptyGalleryLabel.setText(R.string.gallery_permissions_load_failed_text);
                            default:
                                // Could be 'C' or a number of current image page being loaded.
                                emptyGalleryLabel.setText(R.string.gallery_album_content_load_failed_text);
                                break;
                        }
                        if (itemsToLoad.size() > 0) {
                            emptyGalleryLabel.setVisibility(VISIBLE);
                            retryActionButton.setVisibility(VISIBLE);
                        }
                    }
                }
                loadingMessageIds.remove(response.getMessageId());
            }
        }
    }

    private void onResourceUnlinked(PiwigoResponseBufferingHandler.PiwigoUpdateResourceInfoResponse response) {
        deleteActionData.removeProcessedResource(response.getResource());
    }

    private void onResouceInfoRetrieved(PiwigoResponseBufferingHandler.PiwigoResourceInfoRetrievedResponse response) {
        this.deleteActionData.updateLinkedAlbums(response.getResource());
        this.deleteActionData.isResourceInfoAvailable();
        onDeleteResources(deleteActionData);
    }


    private HashSet<Long> buildPreselectedUserIds(List<Username> selectedUsernames) {
        HashSet<Long> preselectedUsernames;
        if (selectedUsernames != null) {
            preselectedUsernames = new HashSet<>(selectedUsernames.size());
            int i = 0;
            for (Username u : selectedUsernames) {
                preselectedUsernames.add(u.getId());
            }
        } else {
            preselectedUsernames = new HashSet<>(0);
        }
        return preselectedUsernames;
    }

    private void onUsernamesRetrievedForSelectedGroups(PiwigoResponseBufferingHandler.PiwigoGetUsernamesListResponse response) {
        if(response.getItemsOnPage() == response.getPageSize()) {
            getUiHelper().showOrQueueDialogMessage(R.string.alert_error, getString(R.string.alert_error_too_many_users_message));
        } else {
            ArrayList<Username> usernames = response.getUsernames();
            userIdsInSelectedGroups = buildPreselectedUserIds(usernames);

            HashSet<Long> preselectedUsernames = getSetFromArray(currentUsers);

            if(usernameSelectionWantedNext) {
                usernameSelectionWantedNext = false;
                UsernameSelectionNeededEvent usernameSelectionNeededEvent = new UsernameSelectionNeededEvent(true, editingItemDetails, userIdsInSelectedGroups, preselectedUsernames);
                getUiHelper().setTrackingRequest(usernameSelectionNeededEvent.getActionId());
                EventBus.getDefault().post(usernameSelectionNeededEvent);
            }
        }
    }

    private HashSet<Long> getSetFromArray(long[] itemIds) {
        HashSet<Long> itemIdsSet;
        itemIdsSet = new HashSet<>(itemIds != null ? itemIds.length : 0);
        if (itemIds != null) {
            int i = 0;
            for (long id : itemIds) {
                itemIdsSet.add(id);
            }
        }
        return itemIdsSet;
    }

    private void onThumbnailUpdated(PiwigoResponseBufferingHandler.PiwigoAlbumThumbnailUpdatedResponse response) {
        if(response.getAlbumParentIdAltered() != null && response.getAlbumParentIdAltered() == gallery.getId()) {
            // need to refresh this gallery content.
            galleryIsDirty = true;
            reloadAlbumContent();
        }
    }

    private void onResourceMoved(PiwigoResponseBufferingHandler.PiwigoUpdateResourceInfoResponse response) {

        Basket basket = getBasket();
        CategoryItem movedParent = basket.getContentParent();
        basket.removeItem(response.getResource());
        movedParent.reducePhotoCount();
        if(movedParent.getRepresentativePictureId() != null && response.getResource().getId() == movedParent.getRepresentativePictureId()) {
            if(movedParent.getPhotoCount() > 0) {
                movedResourceParentUpdateRequired = true;
            }
        }
        if (basket.getItemCount() == 0 && movedResourceParentUpdateRequired) {
            movedResourceParentUpdateRequired = false;
            addActiveServiceCall(R.string.progress_move_resources, PiwigoAccessService.startActionUpdateAlbumThubnail(movedParent.getId(), movedParent.getParentId(), null, getContext()));
        }

        updateBasketDisplay(basket);


        for(Long itemParent : gallery.getParentageChain()) {
            EventBus.getDefault().post(new AlbumAlteredEvent(itemParent));
        }
        //we've altered the album content (now update this album view to reflect the server content)
        galleryIsDirty = true;
        reloadAlbumContent();
    }

    private void onResourcesDeleted(PiwigoResponseBufferingHandler.PiwigoDeleteImageResponse response) {
        // clear the selection
        viewAdapter.clearSelectedItemIds();
        viewAdapter.toggleItemSelection();
        // now update this album view to reflect the server content
        galleryIsDirty = true;
        if(deleteActionData.removeProcessedResources(response.getDeletedItemIds())) {
            deleteActionData = null;
        }
        reloadAlbumContent();
        // Now ensure any parents are also updated when next shown
        for(Long itemParent : gallery.getParentageChain()) {
            EventBus.getDefault().post(new AlbumAlteredEvent(itemParent));
        }
    }

    public void updateAlbumDetails() {
        switch(updateAlbumDetailsProgress) {
            case UPDATE_IN_PROGRESS:
            case UPDATE_NOT_RUNNING:
                updateAlbumDetailsProgress = UPDATE_IN_PROGRESS;
                addActiveServiceCall(R.string.gallery_details_updating_progress_title, PiwigoAccessService.startActionUpdateGalleryInfo(gallery, getContext()));
                break;
            case UPDATE_SETTING_ADDING_PERMISSIONS:
                addingAlbumPermissions();
            case UPDATE_SETTING_REMOVING_PERMISSIONS:
                removeAlbumPermissions();
                break;
            default:
                // unsupported state
                throw new RuntimeException("Unsupported state");
        }
    }

    public void onReloadAlbum() {
        retryActionButton.setVisibility(GONE);
        emptyGalleryLabel.setVisibility(GONE);
        synchronized (itemsToLoad) {
            while (itemsToLoad.size() > 0) {
                String itemToLoad = itemsToLoad.remove(0);
                switch (itemToLoad) {
                    case "C":
                        loadAlbumSubCategories();
                        break;
                    case "P":
                        loadAlbumPermissions();
                        break;
                    case "U":
                        updateAlbumDetails();
                        break;
                    default:
                        int page = Integer.valueOf(itemToLoad);
                        loadAlbumResourcesPage(page);
                        break;
                }
            }
        }
    }

    public void onGetSubGalleries(final PiwigoResponseBufferingHandler.PiwigoGetSubAlbumsResponse response) {

        synchronized (galleryModel) {
//            galleryModel.addItem(CategoryItem.ADVERT);
            for (CategoryItem item : response.getAlbums()) {
                if (item.getId() != gallery.getId()) {
                    galleryModel.addItem(item);
                } else {
                    // copy the extra data across not retrieved by default.
                    item.setGroups(gallery.getGroups());
                    item.setUsers(gallery.getUsers());
                    // now update the reference.
                    gallery = item;
                }
            }
            int spacerAlbumsNeeded = galleryModel.getSubAlbumCount() % albumsPerRow;
            if(spacerAlbumsNeeded > 0) {
                spacerAlbumsNeeded = albumsPerRow - spacerAlbumsNeeded;
            }
            galleryModel.setSpacerAlbumCount(spacerAlbumsNeeded);
            viewAdapter.notifyDataSetChanged();
        }
    }

    public void onGetResources(final PiwigoResponseBufferingHandler.PiwigoGetResourcesResponse response) {
        synchronized (galleryModel) {
            galleryModel.addItemPage(response.getPage(), response.getPageSize(), response.getResources());
            viewAdapter.notifyDataSetChanged();
        }
    }

    public void onAlbumContentAltered(final PiwigoResponseBufferingHandler.PiwigoUpdateAlbumContentResponse response) {
        for(Long itemParent : gallery.getParentageChain()) {
            EventBus.getDefault().post(new AlbumAlteredEvent(itemParent));
        }
        galleryIsDirty = true;
        //we've altered the album content (now update this album view to reflect the server content)
        reloadAlbumContent();
    }

    public void onAlbumInfoAltered(final PiwigoResponseBufferingHandler.PiwigoUpdateAlbumInfoResponse response) {
        gallery = response.getAlbum();
        updateAlbumPermissions();
    }

    private void onAlbumPermissionsAdded(PiwigoResponseBufferingHandler.PiwigoAddAlbumPermissionsResponse response) {
        HashSet<Long> newGroupsSet = SetUtils.asSet(gallery.getGroups());
        newGroupsSet.addAll(response.getGroupIdsAffected());
        gallery.setGroups(SetUtils.asArray(newGroupsSet));
        HashSet<Long> newUsersSet = SetUtils.asSet(gallery.getUsers());
        newUsersSet.addAll(response.getUserIdsAffected());
        gallery.setUsers(SetUtils.asArray(newUsersSet));

        if(!removeAlbumPermissions()) {
            onAlbumUpdateFinished();
        }
    }

    private void onAlbumPermissionsRemoved(PiwigoResponseBufferingHandler.PiwigoRemoveAlbumPermissionsResponse response) {
        HashSet<Long> newGroupsSet = SetUtils.asSet(gallery.getGroups());
        newGroupsSet.removeAll(response.getGroupIdsAffected());
        gallery.setGroups(SetUtils.asArray(newGroupsSet));
        HashSet<Long> newUsersSet = SetUtils.asSet(gallery.getUsers());
        newUsersSet.removeAll(response.getUserIdsAffected());
        gallery.setUsers(SetUtils.asArray(newUsersSet));

        onAlbumUpdateFinished();
    }

    private void updateAlbumPermissions() {
        if(gallery.isPrivate()) {
            if(!addingAlbumPermissions() && !removeAlbumPermissions()) {
                onAlbumUpdateFinished();
            }
        } else {
            onAlbumUpdateFinished();
        }
    }

    private boolean addingAlbumPermissions() {
        updateAlbumDetailsProgress = UPDATE_SETTING_ADDING_PERMISSIONS;
        // Get all groups newly added to the list of permissions
        HashSet<Long> wantedAlbumGroups = SetUtils.asSet(currentGroups);
        final HashSet<Long> newlyAddedGroups = SetUtils.difference(wantedAlbumGroups, SetUtils.asSet(gallery.getGroups()));
        // Get all users newly added to the list of permissions
        HashSet<Long> wantedAlbumUsers = SetUtils.asSet(currentUsers);
        final HashSet<Long> newlyAddedUsers = SetUtils.difference(wantedAlbumUsers, SetUtils.asSet(gallery.getUsers()));

        PiwigoSessionDetails sessionDetails = PiwigoSessionDetails.getInstance();
        long currentLoggedInUserId = sessionDetails.getUserId();

        Set<Long> thisUsersGroupsWithoutAccess = SetUtils.difference(sessionDetails.getGroupMemberships(), wantedAlbumGroups);

        if(currentLoggedInUserId >= 0) {
            boolean noGroupAccess = thisUsersGroupsWithoutAccess.size() == sessionDetails.getGroupMemberships().size();
            boolean noUserAccess = !wantedAlbumUsers.contains(currentLoggedInUserId);

            if (noGroupAccess && noUserAccess) {
                //Argh! You're attemting to remove your own permission to access this album. Add it back in.
                currentUsers = Arrays.copyOf(currentUsers, currentUsers.length + 1);
                currentUsers[currentUsers.length - 1] = currentLoggedInUserId;
                wantedAlbumUsers.add(currentLoggedInUserId);
                // update the ui.
                allowedUsersField.setText(String.format(getString(R.string.click_to_view_pattern), currentUsers.length));
                int msgId = R.string.alert_information_own_user_readded_to_permissions_list;
                getUiHelper().showOrQueueDialogMessage(R.string.alert_information, getString(msgId), R.string.button_ok, false, new UIHelper.QuestionResultListener() {
                    @Override
                    public void onDismiss(AlertDialog dialog) {
                    }

                    @Override
                    public void onResult(AlertDialog dialog, Boolean positiveAnswer) {
                        if(Boolean.TRUE == positiveAnswer) {
                            addingAlbumPermissions();
                        }
                    }
                });
                return true;
            }

            if (newlyAddedGroups.size() > 0 || newlyAddedUsers.size() > 0) {

                if (gallery.getSubCategories() > 0) {
                    if (currentLoggedInUserId >= 0 && newlyAddedUsers.contains(currentLoggedInUserId)) {
                        //we're having to force add this user explicitly therefore for safety we need to apply the change recursively
                        String msg = String.format(getString(R.string.alert_information_add_album_permissions_recursively_pattern), gallery.getSubCategories());
                        getUiHelper().showOrQueueDialogMessage(R.string.alert_information, msg, R.string.button_ok, false, new UIHelper.QuestionResultListener() {
                            @Override
                            public void onDismiss(AlertDialog dialog) {

                            }

                            @Override
                            public void onResult(AlertDialog dialog, Boolean positiveAnswer) {
                                if(Boolean.TRUE == positiveAnswer) {
                                    addActiveServiceCall(R.string.gallery_details_updating_progress_title, PiwigoAccessService.startActionAddAlbumPermissions(gallery, newlyAddedGroups, newlyAddedUsers, true, getContext()));
                                }
                            }
                        });
                    } else {

                        String msg = String.format(getString(R.string.alert_confirm_add_album_permissions_recursively_pattern), newlyAddedGroups.size(), newlyAddedUsers.size(), gallery.getSubCategories());
                        getUiHelper().showOrQueueDialogQuestion(R.string.alert_confirm_title, msg, R.string.button_no, R.string.button_yes, new UIHelper.QuestionResultListener() {
                            @Override
                            public void onDismiss(AlertDialog dialog) {

                            }

                            @Override
                            public void onResult(AlertDialog dialog, Boolean positiveAnswer) {
                                if(Boolean.TRUE == positiveAnswer) {
                                    addActiveServiceCall(R.string.gallery_details_updating_progress_title, PiwigoAccessService.startActionAddAlbumPermissions(gallery, newlyAddedGroups, newlyAddedUsers, true, getContext()));
                                } else {
                                    addActiveServiceCall(R.string.gallery_details_updating_progress_title, PiwigoAccessService.startActionAddAlbumPermissions(gallery, newlyAddedGroups, newlyAddedUsers, false, getContext()));
                                }
                            }
                        });
                    }
                } else {
                    // no need to be recursive as this album is a leaf node.
                    addActiveServiceCall(R.string.gallery_details_updating_progress_title, PiwigoAccessService.startActionAddAlbumPermissions(gallery, newlyAddedGroups, newlyAddedUsers, false, getContext()));
                }
                return true;
            } else {
                return false;
            }
        } else {
            return false;
        }
    }

    private boolean removeAlbumPermissions() {
        updateAlbumDetailsProgress = UPDATE_SETTING_REMOVING_PERMISSIONS;
        // Get all groups newly added to the list of permissions
        final HashSet<Long> newlyRemovedGroups = SetUtils.difference(SetUtils.asSet(gallery.getGroups()), SetUtils.asSet(currentGroups));
        // Get all users newly added to the list of permissions
        HashSet<Long> currentAlbumUsers = SetUtils.asSet(gallery.getUsers());
        final HashSet<Long> newlyRemovedUsers = SetUtils.difference(currentAlbumUsers, SetUtils.asSet(currentUsers));

        if(newlyRemovedGroups.size() > 0 || newlyRemovedUsers.size() > 0) {

            if(gallery.getSubCategories() > 0) {
                String message = String.format(getString(R.string.alert_confirm_really_remove_album_permissions_pattern), newlyRemovedGroups.size(), newlyRemovedUsers.size());
                getUiHelper().showOrQueueDialogQuestion(R.string.alert_confirm_title, message, R.string.button_no, R.string.button_yes, new UIHelper.QuestionResultListener() {
                    @Override
                    public void onDismiss(AlertDialog dialog) {
                    }

                    @Override
                    public void onResult(AlertDialog dialog, Boolean positiveAnswer) {
                        if(Boolean.TRUE == positiveAnswer) {
                            addActiveServiceCall(R.string.gallery_details_updating_progress_title, PiwigoAccessService.startActionRemoveAlbumPermissions(gallery, newlyRemovedGroups, newlyRemovedUsers, getContext()));
                        } else {
                            onAlbumUpdateFinished();
                        }
                    }
                });
            } else {
                addActiveServiceCall(R.string.gallery_details_updating_progress_title, PiwigoAccessService.startActionRemoveAlbumPermissions(gallery, newlyRemovedGroups, newlyRemovedUsers, getContext()));
            }

            return true;
        } else {
            return false;
        }
    }

    private void onAlbumUpdateFinished() {
        updateAlbumDetailsProgress = UPDATE_NOT_RUNNING;
        setGalleryHeadings();
        getUiHelper().dismissProgressDialog();
        if (editingItemDetails) {
            editingItemDetails = false;
            currentUsers = gallery.getUsers();
            currentGroups = gallery.getGroups();
            fillGalleryEditFields();
        }
    }

    private void onAlbumStatusUpdated(PiwigoResponseBufferingHandler.PiwigoSetAlbumStatusResponse response) {
        updateAlbumPermissions();
    }

    public void onAlbumPermissionsRetrieved(PiwigoResponseBufferingHandler.PiwigoAlbumPermissionsRetrievedResponse response) {

        this.gallery = response.getAlbum();
        currentUsers = this.gallery.getUsers();
        currentGroups = this.gallery.getGroups();
        allowedUsersField.setText(String.format(getString(R.string.click_to_view_pattern), currentUsers.length));
        allowedGroupsField.setText(String.format(getString(R.string.click_to_view_pattern), currentGroups.length));
    }

    public void onAlbumDeleted(PiwigoResponseBufferingHandler.PiwigoAlbumDeletedResponse response) {
        if(gallery.getId() == response.getAlbumId()) {
            // we've deleted the current album.
            AlbumDeletedEvent event = new AlbumDeletedEvent(gallery);
            EventBus.getDefault().post(event);
        } else {
            for(Long itemParent : gallery.getParentageChain()) {
                EventBus.getDefault().post(new AlbumAlteredEvent(itemParent));
            }
            //we've deleted a child album (now update this album view to reflect the server content)
            galleryIsDirty = true;
            reloadAlbumContent();
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(UsernameSelectionCompleteEvent usernameSelectionCompleteEvent) {

        if(getUiHelper().isTrackingRequest(usernameSelectionCompleteEvent.getActionId())) {
            long[] selectedUsersArr = new long[usernameSelectionCompleteEvent.getCurrentSelection().size()];
            int i = 0;
            long currentLoggedInUserId = PiwigoSessionDetails.getInstance().getUserId();
            boolean currentUserExplicitlyPresent = false;
            for (Username user : usernameSelectionCompleteEvent.getSelectedItems()) {
                selectedUsersArr[i++] = user.getId();
                if(currentLoggedInUserId == user.getId()) {
                    currentUserExplicitlyPresent = true;
                }
            }
            currentUsers = selectedUsersArr;
            HashSet<Long> wantedAlbumGroups = SetUtils.asSet(currentGroups);
            HashSet<Long> currentUsersGroupMemberships = PiwigoSessionDetails.getInstance().getGroupMemberships();
            Set<Long> thisUsersGroupsWithoutAccess = SetUtils.difference(currentUsersGroupMemberships, wantedAlbumGroups);
            boolean noGroupAccess = thisUsersGroupsWithoutAccess.size() == currentUsersGroupMemberships.size();
            if(currentLoggedInUserId >= 0 && noGroupAccess && !currentUserExplicitlyPresent) {
                //Argh! You've attemted to remove your own permission to access this album. Adding it back in.
                currentUsers = Arrays.copyOf(currentUsers, currentUsers.length + 1);
                currentUsers[currentUsers.length - 1] = currentLoggedInUserId;
                getUiHelper().showOrQueueDialogMessage(R.string.alert_information, getString(R.string.alert_information_own_user_readded_to_permissions_list));
            }
            fillUsernamesField(allowedUsersField, usernameSelectionCompleteEvent.getSelectedItems());
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(AlbumCreatedEvent event) {
        if(getUiHelper().isTrackingRequest(event.getActionId())) {
            galleryIsDirty = true;
            if(isResumed()) {
                reloadAlbumContent();
            }
            for(Long itemParent : gallery.getParentageChain()) {
                EventBus.getDefault().post(new AlbumAlteredEvent(itemParent));
            }
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(GroupSelectionCompleteEvent groupSelectionCompleteEvent) {
        if(getUiHelper().isTrackingRequest(groupSelectionCompleteEvent.getActionId())) {
            long[] selectedGroupsArr = new long[groupSelectionCompleteEvent.getCurrentSelection().size()];
            int i = 0;
            for (Group group : groupSelectionCompleteEvent.getSelectedItems()) {
                selectedGroupsArr[i++] = group.getId();
            }
            currentGroups = selectedGroupsArr;
            userIdsInSelectedGroups = null;
            fillGroupsField(allowedGroupsField, groupSelectionCompleteEvent.getSelectedItems());

            ArrayList<Long> selectedGroupIds = new ArrayList<>(currentGroups.length);
            for(long groupId : currentGroups) {
                selectedGroupIds.add(groupId);
            }
            if(selectedGroupIds.size() == 0) {
                userIdsInSelectedGroups = new HashSet<>(0);
            } else {
                addActiveServiceCall(R.string.progress_loading_group_details, PiwigoAccessService.startActionGetUsernamesList(selectedGroupIds, 0, 100, getContext()));
            }
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(AppLockedEvent event) {
        if(isResumed()) {
            if (editingItemDetails) {
                discardButton.callOnClick();
            } else {
                if (!gallery.isRoot()) {
                    displayControlsBasedOnSessionState();
                }
            }
            boolean captureActionClicks = PiwigoSessionDetails.isAdminUser() && !isAppInReadOnlyMode();
            viewAdapter.setCaptureActionClicks(captureActionClicks);

            Basket basket = getBasket();
            basket.clear();
            updateBasketDisplay(basket);
        } else {
            // if not showing, just flush the state and rebuild the page
            galleryIsDirty = true;
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(AppUnlockedEvent event) {
        if(isResumed()) {
            if (!gallery.isRoot()) {
                displayControlsBasedOnSessionState();
                setEditItemDetailsControlsStatus();
            }
            boolean captureActionClicks = PiwigoSessionDetails.isAdminUser() && !isAppInReadOnlyMode();
            viewAdapter.setCaptureActionClicks(captureActionClicks);
        } else {
            // if not showing, just flush the state and rebuild the page
            galleryIsDirty = true;
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(AlbumAlteredEvent albumAlteredEvent) {
        if (gallery != null && gallery.getId() == albumAlteredEvent.id) {
            galleryIsDirty = true;
            if(isResumed()) {
                reloadAlbumContent();
            }
        }
    }

    private class SpanSizeLookup extends GridLayoutManager.SpanSizeLookup {

        private final int colsPerAlbum;
        private final int colsPerImage;
        private final PiwigoAlbum galleryModel;

        public SpanSizeLookup(PiwigoAlbum galleryModel, int colsPerAlbum, int colsPerImage) {
            this.colsPerAlbum = colsPerAlbum;
            this.colsPerImage = colsPerImage;
            this.galleryModel = galleryModel;
        }

        @Override
        public int getSpanSize(int position) {
            int itemType = galleryModel.getItems().get(position).getType();
            switch(itemType) {
                case GalleryItem.CATEGORY_ADVERT_TYPE:
                    return colsOnScreen;
                case GalleryItem.CATEGORY_TYPE:
                    return colsPerAlbum;
                case GalleryItem.PICTURE_RESOURCE_TYPE:
                    return colsPerImage;
                case GalleryItem.RESOURCE_ADVERT_TYPE:
                    return colsOnScreen;
                case GalleryItem.VIDEO_RESOURCE_TYPE:
                    return colsPerImage;
                default:
                    return colsOnScreen;
            }
        }
    }
}
