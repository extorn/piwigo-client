package delit.piwigoclient.ui.album.view;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.FloatingActionButton;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.AppCompatCheckBox;
import android.support.v7.widget.AppCompatImageView;
import android.support.v7.widget.AppCompatTextView;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.crashlytics.android.Crashlytics;
import com.google.android.gms.ads.AdView;
import com.wunderlist.slidinglayer.CustomSlidingLayer;

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
import java.util.Map;
import java.util.Set;

import delit.piwigoclient.BuildConfig;
import delit.piwigoclient.R;
import delit.piwigoclient.business.AlbumViewPreferences;
import delit.piwigoclient.business.ConnectionPreferences;
import delit.piwigoclient.model.piwigo.Basket;
import delit.piwigoclient.model.piwigo.CategoryItem;
import delit.piwigoclient.model.piwigo.GalleryItem;
import delit.piwigoclient.model.piwigo.Group;
import delit.piwigoclient.model.piwigo.PictureResourceItem;
import delit.piwigoclient.model.piwigo.PiwigoAlbum;
import delit.piwigoclient.model.piwigo.PiwigoAlbumAdminList;
import delit.piwigoclient.model.piwigo.PiwigoSessionDetails;
import delit.piwigoclient.model.piwigo.PiwigoUtils;
import delit.piwigoclient.model.piwigo.ResourceContainer;
import delit.piwigoclient.model.piwigo.ResourceItem;
import delit.piwigoclient.model.piwigo.Username;
import delit.piwigoclient.piwigoApi.BasicPiwigoResponseListener;
import delit.piwigoclient.piwigoApi.HttpConnectionCleanup;
import delit.piwigoclient.piwigoApi.PiwigoResponseBufferingHandler;
import delit.piwigoclient.piwigoApi.handlers.AlbumAddPermissionsResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.AlbumDeleteResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.AlbumGetPermissionsResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.AlbumGetSubAlbumsAdminResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.AlbumGetSubAlbumsResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.AlbumRemovePermissionsResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.AlbumThumbnailUpdatedResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.AlbumUpdateInfoResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.ImageChangeParentAlbumHandler;
import delit.piwigoclient.piwigoApi.handlers.ImageCopyToAlbumResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.ImageDeleteResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.ImageGetInfoResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.ImageUpdateInfoResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.ImagesGetResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.UsernamesGetListResponseHandler;
import delit.piwigoclient.ui.AdsManager;
import delit.piwigoclient.ui.MainActivity;
import delit.piwigoclient.ui.PicassoFactory;
import delit.piwigoclient.ui.common.UIHelper;
import delit.piwigoclient.ui.common.button.CustomImageButton;
import delit.piwigoclient.ui.common.fragment.MyFragment;
import delit.piwigoclient.ui.common.list.recycler.EndlessRecyclerViewScrollListener;
import delit.piwigoclient.ui.common.recyclerview.BaseRecyclerViewAdapter;
import delit.piwigoclient.ui.events.AlbumAlteredEvent;
import delit.piwigoclient.ui.events.AlbumDeletedEvent;
import delit.piwigoclient.ui.events.AlbumItemDeletedEvent;
import delit.piwigoclient.ui.events.AlbumSelectedEvent;
import delit.piwigoclient.ui.events.AppLockedEvent;
import delit.piwigoclient.ui.events.AppUnlockedEvent;
import delit.piwigoclient.ui.events.BadRequestUsesRedirectionServerEvent;
import delit.piwigoclient.ui.events.BadRequestUsingHttpToHttpsServerEvent;
import delit.piwigoclient.ui.events.PiwigoAlbumUpdatedEvent;
import delit.piwigoclient.ui.events.PiwigoLoginSuccessEvent;
import delit.piwigoclient.ui.events.trackable.AlbumCreateNeededEvent;
import delit.piwigoclient.ui.events.trackable.AlbumCreatedEvent;
import delit.piwigoclient.ui.events.trackable.GroupSelectionCompleteEvent;
import delit.piwigoclient.ui.events.trackable.GroupSelectionNeededEvent;
import delit.piwigoclient.ui.events.trackable.UsernameSelectionCompleteEvent;
import delit.piwigoclient.ui.events.trackable.UsernameSelectionNeededEvent;
import delit.piwigoclient.ui.slideshow.GalleryItemAdapter;
import delit.piwigoclient.util.SetUtils;

import static android.view.View.GONE;
import static android.view.View.INVISIBLE;
import static android.view.View.VISIBLE;

/**
 * A fragment representing a list of Items.
 */
public abstract class AbstractViewAlbumFragment extends MyFragment {

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
    private static final String STATE_UPDATE_ALBUM_DETAILS_PROGRESS = "updateAlbumDetailsProgress";
    private static final String STATE_USERNAME_SELECTION_WANTED_NEXT = "usernameSelectionWantedNext";
    private static final String STATE_DELETE_ACTION_DATA = "deleteActionData";
    private static final String STATE_USER_GUID = "userGuid";
    private static final String STATE_RECYCLER_LAYOUT = "recyclerLayout";

    private static final int UPDATE_IN_PROGRESS = 1;
    private static final int UPDATE_SETTING_ADDING_PERMISSIONS = 2;
    private static final int UPDATE_SETTING_REMOVING_PERMISSIONS = 3;
    private static final int UPDATE_NOT_RUNNING = 0;

    AlbumItemRecyclerViewAdapter viewAdapter;
    FloatingActionButton bulkActionButtonTag;
    private FloatingActionButton retryActionButton;
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
    private AppCompatCheckBox galleryPrivacyStatusField;
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

    private static transient PiwigoAlbumAdminList albumAdminList;
    private final HashMap<Long, String> loadingMessageIds = new HashMap<>(2);
    private final ArrayList<String> itemsToLoad = new ArrayList<>(0);
    private int albumsPerRow; // calculated each time view created.
    // Start fields maintained in saved session state.
    private PiwigoAlbum galleryModel;
    private boolean editingItemDetails;
    private boolean informationShowing;
    private long[] currentUsers;
    private long[] currentGroups;
    private boolean galleryIsDirty;
    private boolean movedResourceParentUpdateRequired;
    private HashSet<Long> userIdsInSelectedGroups;
    private int updateAlbumDetailsProgress = UPDATE_NOT_RUNNING;
    private boolean usernameSelectionWantedNext;
    private CustomImageButton addNewAlbumButton;
    private DeleteActionData deleteActionData;
    private long userGuid;
    private transient List<CategoryItem> adminCategories;
    private AppCompatImageView actionIndicatorImg;
    private RecyclerView galleryListView;
    private AlbumViewAdapterListener viewAdapterListener;
    private AlbumItemRecyclerViewAdapterPreferences viewPrefs;
    private CustomSlidingLayer bottomSheet;
    private EndlessRecyclerViewScrollListener galleryListViewScrollListener;

    /**
     * Mandatory empty constructor for the fragment manager to instantiate the
     * fragment (e.g. upon screen orientation changes).
     */
    public AbstractViewAlbumFragment() {
    }

    public static AbstractViewAlbumFragment newInstance(CategoryItem gallery) {
        AbstractViewAlbumFragment fragment = new ViewAlbumFragment();
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
                galleryModel = new PiwigoAlbum((CategoryItem) getArguments().getSerializable(ARG_GALLERY));
                galleryIsDirty = true;
            }
        }
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

    @Override
    public void onDestroy() {
        super.onDestroy();
        UIHelper.recycleImageViewContent(actionIndicatorImg);
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
        viewPrefs.storeToBundle(outState);
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
        outState.putLong(STATE_USER_GUID, userGuid);
        outState.putParcelable(STATE_RECYCLER_LAYOUT, galleryListView.getLayoutManager().onSaveInstanceState());
    }

    protected AlbumItemRecyclerViewAdapterPreferences updateViewPrefs() {

        boolean showAlbumThumbnailsZoomed = AlbumViewPreferences.isShowAlbumThumbnailsZoomed(prefs, getContext());

        boolean showResourceNames = AlbumViewPreferences.isShowResourceNames(prefs, getContext());

        int recentlyAlteredThresholdAge = AlbumViewPreferences.getRecentlyAlteredMaxAgeMillis(prefs, getContext());
        Date recentlyAlteredThresholdDate = new Date(System.currentTimeMillis() - recentlyAlteredThresholdAge);

        if (viewPrefs == null) {
            viewPrefs = new AlbumItemRecyclerViewAdapterPreferences();
        }

        String preferredThumbnailSize = AlbumViewPreferences.getPreferredResourceThumbnailSize(prefs,getContext());

        String preferredAlbumThumbnailSize = AlbumViewPreferences.getPreferredAlbumThumbnailSize(prefs, getContext());

        viewPrefs.selectable(true, false); // set multi select mode enabled (side effect is it enables selection
        viewPrefs.setAllowItemSelection(false); // prevent selection until a long click enables it.
        viewPrefs.withPreferredThumbnailSize(preferredThumbnailSize);
        viewPrefs.withPreferredAlbumThumbnailSize(preferredAlbumThumbnailSize);
        viewPrefs.withShowingAlbumNames(showResourceNames);
        viewPrefs.withShowAlbumThumbnailsZoomed(showAlbumThumbnailsZoomed);
        viewPrefs.withAlbumWidth(getScreenWidth(getActivity()) / albumsPerRow);
        viewPrefs.withRecentlyAlteredThresholdDate(recentlyAlteredThresholdDate);
        return viewPrefs;
    }

    private float getScreenWidth(Activity activity) {
        DisplayMetrics dm = new DisplayMetrics();
        activity.getWindowManager().getDefaultDisplay().getMetrics(dm);
        return (float) dm.widthPixels / dm.xdpi;
    }

    public AlbumItemRecyclerViewAdapterPreferences getViewPrefs() {
        return viewPrefs;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull final LayoutInflater inflater, @Nullable final ViewGroup container, @Nullable Bundle savedInstanceState) {

        super.onCreateView(inflater, container, savedInstanceState);

        View v = inflater.inflate(R.layout.fragment_gallery, container, false);
        Crashlytics.log(Log.DEBUG, getTag(), "view from album fragment - " + v);
        return v;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        if (!PiwigoSessionDetails.isFullyLoggedIn(ConnectionPreferences.getActiveProfile())) {
            // force a reload of the gallery if the session has been destroyed.
            galleryIsDirty = true;
        } else if (savedInstanceState != null) {
            //restore saved state
            viewPrefs = new AlbumItemRecyclerViewAdapterPreferences();
            viewPrefs.loadFromBundle(savedInstanceState);
            editingItemDetails = savedInstanceState.getBoolean(STATE_EDITING_ITEM_DETAILS);
            informationShowing = savedInstanceState.getBoolean(STATE_INFORMATION_SHOWING);
            currentUsers = savedInstanceState.getLongArray(STATE_CURRENT_USERS);
            currentGroups = savedInstanceState.getLongArray(STATE_CURRENT_GROUPS);
            galleryModel = (PiwigoAlbum) savedInstanceState.getSerializable(STATE_GALLERY_MODEL);
            // if galleryIsDirty then this fragment was updated while on the backstack - need to refresh it.
            userGuid = savedInstanceState.getLong(STATE_USER_GUID);
            galleryIsDirty = galleryIsDirty || PiwigoSessionDetails.getUserGuid(ConnectionPreferences.getActiveProfile()) != userGuid;
            galleryIsDirty = galleryIsDirty || savedInstanceState.getBoolean(STATE_GALLERY_DIRTY);
            SetUtils.setNotNull(loadingMessageIds, (HashMap<Long, String>) savedInstanceState.getSerializable(STATE_GALLERY_ACTIVE_LOAD_THREADS));
            Set<Long> messageIdsExpired = PiwigoResponseBufferingHandler.getDefault().getUnknownMessageIds(loadingMessageIds.keySet());
            if (messageIdsExpired.size() > 0) {
                for (Long messageId : messageIdsExpired) {
                    loadingMessageIds.remove(messageId);
                }
            }
            SetUtils.setNotNull(itemsToLoad, (ArrayList<String>) savedInstanceState.getSerializable(STATE_GALLERY_LOADS_TO_RETRY));
            movedResourceParentUpdateRequired = savedInstanceState.getBoolean(STATE_MOVED_RESOURCE_PARENT_UPDATE_NEEDED);
            updateAlbumDetailsProgress = savedInstanceState.getInt(STATE_UPDATE_ALBUM_DETAILS_PROGRESS);
            usernameSelectionWantedNext = savedInstanceState.getBoolean(STATE_USERNAME_SELECTION_WANTED_NEXT);
            if (deleteActionData != null && deleteActionData.isEmpty()) {
                deleteActionData = null;
            } else {
                deleteActionData = (DeleteActionData) savedInstanceState.getSerializable(STATE_DELETE_ACTION_DATA);
            }
            if(galleryListView != null) {
                galleryListView.getLayoutManager().onRestoreInstanceState(savedInstanceState.getParcelable(STATE_RECYCLER_LAYOUT));
            }
        } else {
            // fresh view of the root of the gallery - reset the admin list
            if (galleryModel.getContainerDetails() == CategoryItem.ROOT_ALBUM) {
                albumAdminList = null;
            }
        }

        // reset albums per row to get it recalculated on next use
        albumsPerRow = 0;

        updateViewPrefs();

        userGuid = PiwigoSessionDetails.getUserGuid(ConnectionPreferences.getActiveProfile());


        PiwigoAlbumUpdatedEvent albumUpdatedEvent = EventBus.getDefault().removeStickyEvent(PiwigoAlbumUpdatedEvent.class);
        if (albumUpdatedEvent != null && albumUpdatedEvent.getUpdatedAlbum() instanceof PiwigoAlbum) {
            // retrieved this from the slideshow.
            PiwigoAlbum eventModel = (PiwigoAlbum) albumUpdatedEvent.getUpdatedAlbum();
            if (eventModel.getId() == galleryModel.getId()) {
                galleryModel = eventModel;
            }
        }

        if (galleryListView != null && isSessionDetailsChanged()) {
            if (galleryModel.getContainerDetails() == CategoryItem.ROOT_ALBUM) {
                // Root album can just be reloaded.
                galleryIsDirty = true;
            } else {
                // If the page has been initialised already (not first visit), and the session token has changed, force moving to parent album.
                //TODO be cleverer - check if the website is the same (might be okay to try and reload the same album in that instance). N.b. would need to check for a 401 error
                getFragmentManager().popBackStack();
                return;
            }
        }

//        if (viewPrefs.isUseDarkMode()) {
//            view.setBackgroundColor(Color.BLACK);
//        }

        initialiseBasketView(view);

        retryActionButton = view.findViewById(R.id.gallery_retryAction_actionButton);
        PicassoFactory.getInstance().getPicassoSingleton(getContext()).load(R.drawable.ic_refresh_black_24dp).into(retryActionButton);

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

        EventBus.getDefault().post(new AlbumSelectedEvent(galleryModel.getContainerDetails()));

        AdView adView = view.findViewById(R.id.gallery_adView);
        if (AdsManager.getInstance().shouldShowAdverts()) {
            adView.setVisibility(VISIBLE);
            new AdsManager.MyBannerAdListener(adView);
        } else {
            adView.setVisibility(GONE);
        }

        galleryNameHeader = view.findViewById(R.id.gallery_details_name_header);

        galleryDescriptionHeader = view.findViewById(R.id.gallery_details_description_header);
        descriptionDropdownButton = view.findViewById(R.id.gallery_details_description_dropdown_button);

        setGalleryHeadings();

        bottomSheet = view.findViewById(R.id.slidingDetailBottomSheet);
        setupBottomSheet(bottomSheet);

//        viewInOrientation = getResources().getConfiguration().orientation;

        // Set the adapter
        galleryListView = view.findViewById(R.id.gallery_list);

        RecyclerView recyclerView = galleryListView;

        if (!galleryIsDirty) {
            emptyGalleryLabel.setVisibility(galleryModel.getItemCount() == 0 ? VISIBLE : GONE);
        }

        // need to wait for the gallery model to be initialised.

        int imagesDisplayedPerRow = AlbumViewPreferences.getImagesToDisplayPerRow(getActivity(), prefs);
        int albumsDisplayedPerRow = AlbumViewPreferences.getAlbumsToDisplayPerRow(getActivity(), prefs);
        int totalSpans = imagesDisplayedPerRow;
        if (totalSpans % albumsDisplayedPerRow > 0) {
            totalSpans *= albumsDisplayedPerRow;
        }
        int colsSpannedByAlbum = totalSpans / albumsDisplayedPerRow;
        int colsSpannedByImage = totalSpans / imagesDisplayedPerRow;
        albumsPerRow = albumsDisplayedPerRow;

        GridLayoutManager gridLayoutMan = new GridLayoutManager(getContext(), totalSpans);
        gridLayoutMan.setSpanSizeLookup(new SpanSizeLookup(galleryModel, totalSpans, colsSpannedByAlbum, colsSpannedByImage));

        recyclerView.setLayoutManager(gridLayoutMan);

        viewAdapterListener = new AlbumViewAdapterListener();

        viewAdapter = new AlbumItemRecyclerViewAdapter(getContext(), galleryModel, viewAdapterListener, viewPrefs);

        Basket basket = getBasket();

        setupBulkActionsControls(basket);

        recyclerView.setAdapter(viewAdapter);


        galleryListViewScrollListener = new EndlessRecyclerViewScrollListener(gridLayoutMan) {
            @Override
            public void onLoadMore(int page, int totalItemsCount, RecyclerView view) {
                int pageToLoad = galleryModel.getPagesLoaded();
                if (pageToLoad == 0 || galleryModel.isFullyLoaded()) {
                    // already load this one by default so lets not double load it (or we've already loaded all items).
                    return;
                }
                loadAlbumResourcesPage(pageToLoad);
            }
        };
        galleryListViewScrollListener.configure(galleryModel.getPagesLoaded(), galleryModel.getItemCount());
        recyclerView.addOnScrollListener(galleryListViewScrollListener);


        //display bottom sheet if needed
        updateInformationShowingStatus();
    }

    private boolean showBulkDeleteAction(Basket basket) {
        return PiwigoSessionDetails.isAdminUser(ConnectionPreferences.getActiveProfile()) && viewAdapter.isItemSelectionAllowed() && basket.getItemCount() == 0;
    }

    private boolean showBulkCopyAction(Basket basket) {
        return PiwigoSessionDetails.isAdminUser(ConnectionPreferences.getActiveProfile()) && viewAdapter.isItemSelectionAllowed() && (basket.getItemCount() == 0 || galleryModel.getContainerDetails().getId() == basket.getContentParentId());
    }

    private boolean showBulkCutAction(Basket basket) {
        return PiwigoSessionDetails.isAdminUser(ConnectionPreferences.getActiveProfile()) && viewAdapter.isItemSelectionAllowed() && (basket.getItemCount() == 0 || galleryModel.getContainerDetails().getId() == basket.getContentParentId());
    }

    private boolean showBulkPasteAction(Basket basket) {
        return PiwigoSessionDetails.isAdminUser(ConnectionPreferences.getActiveProfile()) && !viewAdapter.isItemSelectionAllowed() && basket.getItemCount() > 0 && galleryModel.getContainerDetails().getId() != CategoryItem.ROOT_ALBUM.getId() && galleryModel.getContainerDetails().getId() != basket.getContentParentId();
    }

    private boolean showBulkActionsContainer(Basket basket) {
        return viewAdapter.isItemSelectionAllowed() || getBasket().getItemCount() > 0;
    }

    protected void setupBulkActionsControls(Basket basket) {

        bulkActionsContainer.setVisibility(showBulkActionsContainer(basket) ? VISIBLE : GONE);

        bulkActionButtonTag = bulkActionsContainer.findViewById(R.id.gallery_action_tag_bulk);
        bulkActionButtonTag.setVisibility(View.GONE);

        bulkActionButtonDelete = bulkActionsContainer.findViewById(R.id.gallery_action_delete_bulk);
        PicassoFactory.getInstance().getPicassoSingleton(getContext()).load(R.drawable.ic_delete_black_24px).into(bulkActionButtonDelete);
        bulkActionButtonDelete.setVisibility(showBulkDeleteAction(basket) ? VISIBLE : GONE);
        bulkActionButtonDelete.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getActionMasked() == MotionEvent.ACTION_UP) {
                    onBulkActionDeleteButtonPressed();
                }
                return true; // consume the event
            }
        });

        bulkActionButtonCopy = bulkActionsContainer.findViewById(R.id.gallery_action_copy_bulk);
        PicassoFactory.getInstance().getPicassoSingleton(getContext()).load(R.drawable.ic_content_copy_black_24px).into(bulkActionButtonCopy);
        bulkActionButtonCopy.setVisibility(showBulkCopyAction(basket) ? VISIBLE : GONE);
        bulkActionButtonCopy.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                boolean bulkActionsAllowed = PiwigoSessionDetails.isAdminUser(ConnectionPreferences.getActiveProfile()) && !isAppInReadOnlyMode();
                if (bulkActionsAllowed && event.getActionMasked() == MotionEvent.ACTION_UP) {
                    addItemsToBasket(Basket.ACTION_COPY);
                }
                return true; // consume the event
            }
        });

        bulkActionButtonCut = bulkActionsContainer.findViewById(R.id.gallery_action_cut_bulk);
        PicassoFactory.getInstance().getPicassoSingleton(getContext()).load(R.drawable.ic_content_cut_black_24px).into(bulkActionButtonCut);
        bulkActionButtonCut.setVisibility(showBulkCutAction(basket) ? VISIBLE : GONE);
        bulkActionButtonCut.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                boolean bulkActionsAllowed = PiwigoSessionDetails.isAdminUser(ConnectionPreferences.getActiveProfile()) && !isAppInReadOnlyMode();
                if (bulkActionsAllowed && event.getActionMasked() == MotionEvent.ACTION_UP) {
                    addItemsToBasket(Basket.ACTION_CUT);
                }
                return true; // consume the event
            }
        });

        bulkActionButtonPaste = bulkActionsContainer.findViewById(R.id.gallery_action_paste_bulk);
        PicassoFactory.getInstance().getPicassoSingleton(getContext()).load(R.drawable.ic_content_paste_black_24dp).into(bulkActionButtonPaste);
        bulkActionButtonPaste.setVisibility(showBulkPasteAction(basket) ? VISIBLE : GONE);
        bulkActionButtonPaste.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                boolean bulkActionsAllowed = PiwigoSessionDetails.isAdminUser(ConnectionPreferences.getActiveProfile()) && !isAppInReadOnlyMode();
                if (bulkActionsAllowed && event.getActionMasked() == MotionEvent.ACTION_UP) {
                    final Basket basket = getBasket();
                    int msgPatternId = -1;
                    if (basket.getAction() == Basket.ACTION_COPY) {
                        msgPatternId = R.string.alert_confirm_copy_items_here_pattern;
                    } else if (basket.getAction() == Basket.ACTION_CUT) {
                        msgPatternId = R.string.alert_confirm_move_items_here_pattern;
                    }
                    String message = String.format(getString(msgPatternId), basket.getItemCount(), galleryModel.getContainerDetails().getName());

                    getUiHelper().showOrQueueDialogQuestion(R.string.alert_confirm_title, message, R.string.button_no, R.string.button_yes, new UIHelper.QuestionResultAdapter() {
                        @Override
                        public void onResult(AlertDialog dialog, Boolean positiveAnswer) {
                            if (Boolean.TRUE == positiveAnswer) {
                                if (basket.getAction() == Basket.ACTION_COPY) {
                                    HashSet<ResourceItem> itemsToCopy = basket.getContents();
                                    CategoryItem copyToAlbum = galleryModel.getContainerDetails();
                                    for (ResourceItem itemToCopy : itemsToCopy) {
                                        addActiveServiceCall(R.string.progress_copy_resources, new ImageCopyToAlbumResponseHandler<>(itemToCopy, copyToAlbum).invokeAsync(getContext()));
                                    }
                                } else if (basket.getAction() == Basket.ACTION_CUT) {
                                    HashSet<ResourceItem> itemsToMove = basket.getContents();
                                    CategoryItem moveToAlbum = galleryModel.getContainerDetails();
                                    for (ResourceItem itemToMove : itemsToMove) {
                                        addActiveServiceCall(R.string.progress_move_resources, new ImageChangeParentAlbumHandler(itemToMove, moveToAlbum).invokeAsync(getContext()));
                                    }
                                }
                            }
                        }
                    });
                }
                return true; // consume the event
            }
        });
    }

    private void loadAdminListOfAlbums() {
        long loadingMessageId = new AlbumGetSubAlbumsAdminResponseHandler().invokeAsync(getContext());
        loadingMessageIds.put(loadingMessageId, "AL");
        addNonBlockingActiveServiceCall(R.string.progress_loading_album_content, loadingMessageId);
    }

    private void onBulkActionDeleteButtonPressed() {
        boolean bulkActionsAllowed = PiwigoSessionDetails.isAdminUser(ConnectionPreferences.getActiveProfile()) && !isAppInReadOnlyMode();
        if (bulkActionsAllowed) {
            HashSet<Long> selectedItemIds = viewAdapter.getSelectedItemIds();
            if (deleteActionData != null && selectedItemIds.equals(deleteActionData.getSelectedItemIds())) {
                //continue with previous action
                onDeleteResources(deleteActionData);
            } else if (selectedItemIds.size() > 0) {
                HashSet<ResourceItem> selectedItems = viewAdapter.getSelectedItems();
                DeleteActionData deleteActionData = new DeleteActionData(selectedItemIds, selectedItems);
                if (!deleteActionData.isResourceInfoAvailable()) {
                    this.deleteActionData = deleteActionData;
                }
                onDeleteResources(deleteActionData);
            }
        }
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

        AppCompatImageView basketImage = basketView.findViewById(R.id.basket_image);
        PicassoFactory.getInstance().getPicassoSingleton(getContext()).load(R.drawable.ic_shopping_basket_black_24dp).into(basketImage);

        AppCompatImageView clearButton = basketView.findViewById(R.id.basket_clear_button);
        PicassoFactory.getInstance().getPicassoSingleton(getContext()).load(R.drawable.ic_delete_black_24px).into(clearButton);
        clearButton.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getActionMasked() == MotionEvent.ACTION_UP) {
                    Basket basket = getBasket();
                    basket.clear();
                    updateBasketDisplay(basket);
                }
                return true;
            }
        });
    }

    private Basket getBasket() {
        MainActivity activity = (MainActivity) getActivity();
        return activity.getBasket();
    }

    private void addItemsToBasket(int action) {
        Basket basket = getBasket();

        Set<Long> selectedItems = viewAdapter.getSelectedItemIds();
        for (GalleryItem item : galleryModel.getItems()) {
            if (selectedItems.contains(item.getId())) {
                if (item instanceof ResourceItem) {
                    basket.addItem(action, (ResourceItem) item, galleryModel.getContainerDetails());
                }
            }
        }
        updateBasketDisplay(basket);
    }

    protected boolean isPreventItemSelection() {
        if (isAppInReadOnlyMode() || !PiwigoSessionDetails.isAdminUser(ConnectionPreferences.getActiveProfile())) {
            return true;
        }
        return getBasket().getItemCount() > 0 && getBasket().getContentParentId() != galleryModel.getContainerDetails().getId();
    }

    protected void updateBasketDisplay(Basket basket) {

        if (viewAdapter.isMultiSelectionAllowed() && isPreventItemSelection()) {
            viewPrefs.withAllowMultiSelect(false);
            viewPrefs.setAllowItemSelection(false);
            viewAdapter.notifyDataSetChanged(); //TODO check this works (refresh the whole list, redrawing all with/without select box as appropriate)
        }

        int basketItemCount = basket.getItemCount();
        if (basketItemCount == 0) {
            basketView.setVisibility(GONE);
        } else {
            basketView.setVisibility(VISIBLE);
            AppCompatTextView basketItemCountField = basketView.findViewById(R.id.basket_item_count);
            basketItemCountField.setText(String.valueOf(basketItemCount));

            actionIndicatorImg = basketView.findViewById(R.id.basket_action_indicator);
            if (basket.getAction() == Basket.ACTION_COPY) {
                PicassoFactory.getInstance().getPicassoSingleton(getContext()).load(R.drawable.ic_content_copy_black_24px).into(actionIndicatorImg);
            } else {
                PicassoFactory.getInstance().getPicassoSingleton(getContext()).load(R.drawable.ic_content_cut_black_24px).into(actionIndicatorImg);
            }
        }

        displayControlsBasedOnSessionState();
        bulkActionsContainer.setVisibility(showBulkActionsContainer(basket) ? VISIBLE : GONE);
        bulkActionButtonDelete.setVisibility(showBulkDeleteAction(basket) ? VISIBLE : GONE);
        bulkActionButtonCopy.setVisibility(showBulkCopyAction(basket) ? VISIBLE : GONE);
        bulkActionButtonCut.setVisibility(showBulkCutAction(basket) ? VISIBLE : GONE);
        bulkActionButtonPaste.setVisibility(showBulkPasteAction(basket) ? VISIBLE : GONE);

    }

    private void onAlbumDeleteRequest(final CategoryItem album) {
        String msg = String.format(getString(R.string.alert_confirm_really_delete_album_from_server_pattern), album.getName());
        getUiHelper().showOrQueueDialogQuestion(R.string.alert_confirm_title, msg, R.string.button_no, R.string.button_yes, new UIHelper.QuestionResultAdapter() {
            @Override
            public void onResult(AlertDialog dialog, Boolean positiveAnswer) {
                if (Boolean.TRUE == positiveAnswer) {
                    if(album.getTotalPhotos() > 0 || album.getSubCategories() > 0) {
                        String msg = String.format(getString(R.string.alert_confirm_really_really_delete_album_from_server_pattern), album.getName(), album.getPhotoCount(), album.getSubCategories(), album.getTotalPhotos() - album.getPhotoCount());
                        getUiHelper().showOrQueueDialogQuestion(R.string.alert_confirm_title, msg, R.string.button_no, R.string.button_yes, new UIHelper.QuestionResultAdapter() {
                            @Override
                            public void onResult(AlertDialog dialog, Boolean positiveAnswer) {
                                if (Boolean.TRUE == positiveAnswer) {
                                    addActiveServiceCall(R.string.progress_delete_album, new AlbumDeleteResponseHandler(album.getId()).invokeAsync(getContext()));
                                }
                            }
                        });
                    } else {
                        addActiveServiceCall(R.string.progress_delete_album, new AlbumDeleteResponseHandler(album.getId()).invokeAsync(getContext()));
                    }
                }
            }
        });
    }

    private void onDeleteResources(final DeleteActionData deleteActionData) {
        final HashSet<ResourceItem> sharedResources = new HashSet<>();
        if (deleteActionData.isResourceInfoAvailable()) {
            //TODO currently, this won't work. No piwigo support
            for (ResourceItem item : deleteActionData.getSelectedItems()) {
                if (item.getLinkedAlbums().size() > 1) {
                    sharedResources.add(item);
                }
            }
        } else {
            String multimediaExtensionList = AlbumViewPreferences.getKnownMultimediaExtensions(prefs,getContext());
            for (ResourceItem item : deleteActionData.getItemsWithoutLinkedAlbumData()) {
                long messageId = new ImageGetInfoResponseHandler(item, multimediaExtensionList).invokeAsync(getContext());
                deleteActionData.trackMessageId(addActiveServiceCall(R.string.progress_loading_resource_details, messageId));
            }
            return;
        }
        if (sharedResources.size() > 0) {
            String msg = getString(R.string.alert_confirm_delete_items_from_server_or_just_unlink_them_from_this_album_pattern, sharedResources.size());
            getUiHelper().showOrQueueDialogQuestion(R.string.alert_confirm_title, msg, Integer.MIN_VALUE, R.string.button_unlink, R.string.button_cancel, R.string.button_delete, new UIHelper.QuestionResultAdapter() {

                @Override
                public void onResult(AlertDialog dialog, Boolean positiveAnswer) {
                    if (Boolean.TRUE == positiveAnswer) {
                        addActiveServiceCall(R.string.progress_delete_resources, new ImageDeleteResponseHandler(deleteActionData.getSelectedItemIds()).invokeAsync(getContext()));
                    } else if (Boolean.FALSE == positiveAnswer) {
                        HashSet<Long> itemIdsForPermanentDelete = new HashSet<>(deleteActionData.getSelectedItemIds());
                        HashSet<ResourceItem> itemsForPermananentDelete = new HashSet<>(deleteActionData.getSelectedItems());
                        for (ResourceItem item : sharedResources) {
                            itemIdsForPermanentDelete.remove(item.getId());
                            itemsForPermananentDelete.remove(item);
                            item.getLinkedAlbums().remove(galleryModel.getContainerDetails().getId());
                            addActiveServiceCall(R.string.progress_unlink_resources, new ImageUpdateInfoResponseHandler(item).invokeAsync(getContext()));
                        }
                        //now we need to delete the rest.
                        deleteResourcesFromServerForever(itemIdsForPermanentDelete, itemsForPermananentDelete);
                    }
                }
            });
        } else {
            deleteResourcesFromServerForever(deleteActionData.getSelectedItemIds(), deleteActionData.getSelectedItems());
        }

    }

    private void deleteResourcesFromServerForever(final HashSet<Long> selectedItemIds, final HashSet<? extends ResourceItem> selectedItems) {
        String msg = getString(R.string.alert_confirm_really_delete_items_from_server);
        getUiHelper().showOrQueueDialogQuestion(R.string.alert_confirm_title, msg, R.string.button_cancel, R.string.button_ok, new UIHelper.QuestionResultAdapter() {
            @Override
            public void onResult(AlertDialog dialog, Boolean positiveAnswer) {
                if (Boolean.TRUE == positiveAnswer) {
                    addActiveServiceCall(R.string.progress_delete_resources, new ImageDeleteResponseHandler(selectedItemIds).invokeAsync(getContext()));
                }
            }
        });
    }

    @Override
    protected String buildPageHeading() {
        CategoryItem catItem = galleryModel.getContainerDetails();
        if(CategoryItem.ROOT_ALBUM.equals(catItem)) {
            return getString(R.string.album_title_home);
        } else {
            String currentAlbumName = "... / " + galleryModel.getContainerDetails().getName();
            return currentAlbumName;
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        if (galleryListView == null) {
            //Resumed, but fragment initialisation cancelled for whatever reason.
            return;
        }

        if (galleryIsDirty) {
            reloadAlbumContent();
        } else if (itemsToLoad.size() > 0) {
            onReloadAlbum();
        } else {

            int spacerAlbumsNeeded = galleryModel.getSubAlbumCount() % albumsPerRow;
            if (spacerAlbumsNeeded > 0) {
                spacerAlbumsNeeded = albumsPerRow - spacerAlbumsNeeded;
            }
            galleryModel.setSpacerAlbumCount(spacerAlbumsNeeded);
            viewAdapter.notifyDataSetChanged();
        }

        updateBasketDisplay(getBasket());

    }

    private void reloadAlbumContent() {

        if (galleryIsDirty) {
            galleryIsDirty = false;
            if (loadingMessageIds.size() > 0) {
                // already a load in progress - ignore this call.
                //TODO be cleverer - check the time the call was invoked and queue another if needed.
                return;
            }
            galleryModel.clear();
            galleryListViewScrollListener.resetState();
            galleryListView.swapAdapter(viewAdapter, true);
//            viewAdapter.notifyDataSetChanged();

            loadAlbumSubCategories();
            loadAlbumResourcesPage(0);
            if (PiwigoSessionDetails.isAdminUser(ConnectionPreferences.getActiveProfile())) {
                boolean loadAdminList = false;
                if (albumAdminList == null) {
                    loadAdminList = true;
                } else {
                    if (galleryModel.getContainerDetails().equals(CategoryItem.ROOT_ALBUM)) {
                        adminCategories = albumAdminList.getAlbums();
                    } else {
                        CategoryItem adminCopyOfAlbum = null;
                        try {
                            albumAdminList.getAlbum(galleryModel.getContainerDetails());
                        } catch (IllegalStateException e) {
                            Crashlytics.logException(e);
                            if (BuildConfig.DEBUG) {
                                Log.d(getTag(), String.format("current container details (%1$s) not in admin list", galleryModel.getContainerDetails()));
                            } else {
                                Crashlytics.log(Log.ERROR, getTag(), String.format("current container details (%1$s) not in admin list", galleryModel.getContainerDetails()));

                            }
                        }
                        if (adminCopyOfAlbum != null) {
                            adminCategories = adminCopyOfAlbum.getChildAlbums();
                        } else {
                            // this admin list is outdated.
                            albumAdminList = null;
                            loadAdminList = true;
                        }
                    }
                }
                if (loadAdminList) {
                    loadAdminListOfAlbums();
                }
            }
        }
    }

    private void loadAlbumResourcesPage(int pageToLoad) {
        synchronized (loadingMessageIds) {
            galleryModel.acquirePageLoadLock();
            try {
                if (galleryModel.isPageLoadedOrBeingLoaded(pageToLoad)) {
                    return;
                }

                String sortOrder = AlbumViewPreferences.getResourceSortOrder(prefs,getContext());
                String multimediaExtensionList = AlbumViewPreferences.getKnownMultimediaExtensions(prefs,getContext());
                int pageSize = AlbumViewPreferences.getResourceRequestPageSize(prefs,getContext());
                long loadingMessageId = new ImagesGetResponseHandler(galleryModel.getContainerDetails(), sortOrder, pageToLoad, pageSize, multimediaExtensionList).invokeAsync(getContext());
                galleryModel.recordPageBeingLoaded(addNonBlockingActiveServiceCall(R.string.progress_loading_album_content, loadingMessageId), pageToLoad);
                loadingMessageIds.put(loadingMessageId, String.valueOf(pageToLoad));
            } finally {
                galleryModel.releasePageLoadLock();
            }
        }
    }

    private void loadAlbumSubCategories() {
        synchronized (loadingMessageIds) {
            ConnectionPreferences.ProfilePreferences connPrefs = ConnectionPreferences.getActiveProfile();
            PiwigoSessionDetails sessionDetails = PiwigoSessionDetails.getInstance(connPrefs);
//            if(sessionDetails != null && sessionDetails.isUseCommunityPlugin() && !sessionDetails.isGuest()) {
//                connPrefs = ConnectionPreferences.getActiveProfile().asGuest();
//            }
            long loadingMessageId = new AlbumGetSubAlbumsResponseHandler(galleryModel.getContainerDetails(), viewPrefs.getPreferredAlbumThumbnailSize(), false).invokeAsync(getContext(), connPrefs);
            loadingMessageIds.put(loadingMessageId, "C");
            addNonBlockingActiveServiceCall(R.string.progress_loading_album_content, loadingMessageId);
        }
    }

    private void updateInformationShowingStatus() {
        if (informationShowing) {
            if (currentGroups == null) {
                // haven't yet loaded the existing permissions - do this now.
                checkedListener.onCheckedChanged(galleryPrivacyStatusField, galleryPrivacyStatusField.isChecked());
            }
        }
    }

    private void setupBottomSheet(final CustomSlidingLayer bottomSheet) {
        bottomSheet.setOnInteractListener(new CustomSlidingLayer.OnInteractListener() {
            @Override
            public void onOpen() {

            }

            @Override
            public void onShowPreview() {

            }

            @Override
            public void onClose() {

            }

            @Override
            public void onOpened() {
                informationShowing = !informationShowing;
                updateInformationShowingStatus();
            }

            @Override
            public void onPreviewShowed() {

            }

            @Override
            public void onClosed() {
                informationShowing = !informationShowing;
                updateInformationShowingStatus();
            }
        });

        boolean visibleBottomSheet = PiwigoSessionDetails.isAdminUser(ConnectionPreferences.getActiveProfile()) || galleryModel.getContainerDetails() != CategoryItem.ROOT_ALBUM;
        bottomSheet.setVisibility(visibleBottomSheet ? View.VISIBLE : View.GONE);

        int editFieldVisibility = VISIBLE;
        if (galleryModel.getContainerDetails().isRoot()) {
            editFieldVisibility = GONE;
        }

        View editFields = bottomSheet.findViewById(R.id.gallery_details_edit_fields);
        editFields.setVisibility(editFieldVisibility);

        // always setting them up eliminates the chance they might be null.
        setupBottomSheetButtons(bottomSheet, editFieldVisibility);
        setupEditFields(editFields);
        if (!galleryModel.getContainerDetails().isRoot()) {
            fillGalleryEditFields();
        } else {
            displayControlsBasedOnSessionState();
            setEditItemDetailsControlsStatus();
        }

    }

    private void setupBottomSheetButtons(View bottomSheet, int editFieldsVisibility) {
        saveButton = bottomSheet.findViewById(R.id.gallery_details_save_button);
        saveButton.setVisibility(editFieldsVisibility);
        saveButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                galleryModel.getContainerDetails().setName(galleryNameView.getText().toString());
                galleryModel.getContainerDetails().setDescription(galleryDescriptionView.getText().toString());
                galleryModel.getContainerDetails().setPrivate(galleryPrivacyStatusField.isChecked());
                updateAlbumDetails();
            }
        });

        discardButton = bottomSheet.findViewById(R.id.gallery_details_discard_button);
        discardButton.setVisibility(editFieldsVisibility);
        discardButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                editingItemDetails = false;
                currentUsers = galleryModel.getContainerDetails().getUsers();
                currentGroups = galleryModel.getContainerDetails().getGroups();
                fillGalleryEditFields();
            }
        });


        editButton = bottomSheet.findViewById(R.id.gallery_details_edit_button);
        editButton.setVisibility(editFieldsVisibility);
        editButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                editingItemDetails = true;
                fillGalleryEditFields();
            }
        });

        addNewAlbumButton = bottomSheet.findViewById(R.id.album_add_new_album_button);
        addNewAlbumButton.setVisibility(VISIBLE);
        addNewAlbumButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                AlbumCreateNeededEvent event = new AlbumCreateNeededEvent(galleryModel.getContainerDetails().toStub());
                getUiHelper().setTrackingRequest(event.getActionId());
                EventBus.getDefault().post(event);
            }
        });

        deleteButton = bottomSheet.findViewById(R.id.gallery_action_delete);
        deleteButton.setVisibility(editFieldsVisibility);
        deleteButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onAlbumDeleteRequest(galleryModel.getContainerDetails());
            }
        });

        pasteButton = bottomSheet.findViewById(R.id.gallery_action_paste);
        pasteButton.setVisibility(editFieldsVisibility);
        pasteButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onMoveItem(galleryModel.getContainerDetails());
            }
        });

        cutButton = bottomSheet.findViewById(R.id.gallery_action_cut);
        cutButton.setVisibility(editFieldsVisibility);
        cutButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onCopyItem(galleryModel.getContainerDetails());
            }
        });
    }

    private void setupEditFields(View editFields) {
        galleryNameView = editFields.findViewById(R.id.gallery_details_name);
        galleryNameView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {

                if (galleryNameView.getLineCount() > galleryNameView.getMaxLines()) {
//                    bottomSheetBehavior.setAllowUserDragging(event.getActionMasked() == MotionEvent.ACTION_UP);
                }
                return false;
            }
        });
        galleryDescriptionView = editFields.findViewById(R.id.gallery_details_description);
        galleryDescriptionView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (galleryDescriptionView.getLineCount() > galleryDescriptionView.getMaxLines()) {
//                    bottomSheetBehavior.setAllowUserDragging(event.getActionMasked() == MotionEvent.ACTION_UP);
                }
                return false;
            }
        });

        allowedGroupsFieldLabel = editFields.findViewById(R.id.gallery_details_allowed_groups_label);
        allowedGroupsField = editFields.findViewById(R.id.gallery_details_allowed_groups);
        allowedGroupsField.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                HashSet<Long> groups = new HashSet<>();
                if (currentGroups != null) {
                    for (long groupId : currentGroups) {
                        groups.add(groupId);
                    }
                }
                GroupSelectionNeededEvent groupSelectionNeededEvent = new GroupSelectionNeededEvent(true, editingItemDetails, groups);
                getUiHelper().setTrackingRequest(groupSelectionNeededEvent.getActionId());
                EventBus.getDefault().post(groupSelectionNeededEvent);
            }
        });

        allowedUsersFieldLabel = editFields.findViewById(R.id.gallery_details_allowed_users_label);
        allowedUsersField = editFields.findViewById(R.id.gallery_details_allowed_users);
        allowedUsersField.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (currentGroups == null || currentGroups.length == 0 || userIdsInSelectedGroups != null) {
                    if (userIdsInSelectedGroups == null) {
                        userIdsInSelectedGroups = new HashSet<>(0);
                    }
                    HashSet<Long> preselectedUsernames = getSetFromArray(currentUsers);
                    UsernameSelectionNeededEvent usernameSelectionNeededEvent = new UsernameSelectionNeededEvent(true, editingItemDetails, userIdsInSelectedGroups, preselectedUsernames);
                    getUiHelper().setTrackingRequest(usernameSelectionNeededEvent.getActionId());
                    EventBus.getDefault().post(usernameSelectionNeededEvent);
                } else {
                    ArrayList<Long> selectedGroupIds = new ArrayList<>(currentGroups.length);
                    for (long groupId : currentGroups) {
                        selectedGroupIds.add(groupId);
                    }
                    usernameSelectionWantedNext = true;
                    addActiveServiceCall(R.string.progress_loading_group_details, new UsernamesGetListResponseHandler(selectedGroupIds, 0, 100).invokeAsync(getContext()));
                }
            }
        });

        galleryPrivacyStatusField = editFields.findViewById(R.id.gallery_details_status);
        galleryPrivacyStatusField.setChecked(galleryModel.getContainerDetails().isPrivate());
        checkedListener = new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    if (PiwigoSessionDetails.isAdminUser(ConnectionPreferences.getActiveProfile())) {
                        loadAlbumPermissions();
                    }
                }
                allowedGroupsFieldLabel.setVisibility(isChecked ? VISIBLE : GONE);
                allowedGroupsField.setVisibility(isChecked ? VISIBLE : GONE);
                allowedUsersFieldLabel.setVisibility(isChecked ? VISIBLE : GONE);
                allowedUsersField.setVisibility(isChecked ? VISIBLE : GONE);
            }
        };
        galleryPrivacyStatusField.setOnCheckedChangeListener(checkedListener);
    }

    private void loadAlbumPermissions() {
        if (!galleryModel.getContainerDetails().isRoot()) {
            // never want to load permissions for the root album (it's not legal to call this service with category id 0).
            addActiveServiceCall(R.string.progress_loading_album_permissions, new AlbumGetPermissionsResponseHandler(galleryModel.getContainerDetails()).invokeAsync(getContext()));
        }
    }

    private void fillGalleryEditFields() {
        if (galleryModel.getContainerDetails().getName() != null && !galleryModel.getContainerDetails().getName().isEmpty()) {
            galleryNameView.setText(galleryModel.getContainerDetails().getName());
        } else {
            galleryNameView.setText("");
        }
        if (galleryModel.getContainerDetails().getDescription() != null && !galleryModel.getContainerDetails().getDescription().isEmpty()) {
            galleryDescriptionView.setText(galleryModel.getContainerDetails().getDescription());
        } else {
            galleryDescriptionView.setText("");
        }
        allowedGroupsField.setText(R.string.click_to_view);
        allowedUsersField.setText(R.string.click_to_view);
        galleryPrivacyStatusField.setChecked(galleryModel.getContainerDetails().isPrivate());
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
        boolean visibleBottomSheet = PiwigoSessionDetails.isAdminUser(ConnectionPreferences.getActiveProfile()) || galleryModel.getContainerDetails() != CategoryItem.ROOT_ALBUM;
        bottomSheet.setVisibility(visibleBottomSheet ? View.VISIBLE : View.GONE);

        addNewAlbumButton.setEnabled(!editingItemDetails);

        galleryNameView.setEnabled(editingItemDetails);
        galleryDescriptionView.setEnabled(editingItemDetails);
        galleryPrivacyStatusField.setEnabled(editingItemDetails);
        allowedUsersField.setEnabled(true); // Always enabled (but is read only when not editing)
        allowedGroupsField.setEnabled(true); // Always enabled (but is read only when not editing)

        editButton.setVisibility(PiwigoSessionDetails.isAdminUser(ConnectionPreferences.getActiveProfile()) && !isAppInReadOnlyMode() && !editingItemDetails && galleryModel.getContainerDetails() != CategoryItem.ROOT_ALBUM ? VISIBLE : GONE);

        saveButton.setVisibility(editingItemDetails ? VISIBLE : GONE);
        saveButton.setEnabled(editingItemDetails);
        discardButton.setVisibility(editingItemDetails ? VISIBLE : GONE);
        discardButton.setEnabled(editingItemDetails);
    }

    private void displayControlsBasedOnSessionState() {
        if (PiwigoSessionDetails.isAdminUser(ConnectionPreferences.getActiveProfile()) && !isAppInReadOnlyMode()) {
            saveButton.setVisibility(galleryModel.getContainerDetails().isRoot() ? INVISIBLE : VISIBLE);
            discardButton.setVisibility(galleryModel.getContainerDetails().isRoot() ? INVISIBLE : VISIBLE);
            editButton.setVisibility(galleryModel.getContainerDetails().isRoot() ? INVISIBLE : VISIBLE);
            deleteButton.setVisibility(galleryModel.getContainerDetails().isRoot() ? INVISIBLE : VISIBLE);
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

        int visibility = PiwigoSessionDetails.isAdminUser(ConnectionPreferences.getActiveProfile()) ? VISIBLE : GONE;

        allowedGroupsFieldLabel.setVisibility(visibility);
        allowedGroupsField.setVisibility(visibility);
        allowedUsersFieldLabel.setVisibility(visibility);
        allowedUsersField.setVisibility(visibility);
    }

    private void setGalleryHeadings() {
        if (galleryModel.getContainerDetails().getName() != null && !galleryModel.getContainerDetails().getName().isEmpty() && CategoryItem.ROOT_ALBUM != galleryModel.getContainerDetails()) {
            galleryNameHeader.setText(galleryModel.getContainerDetails().getName());
            galleryNameHeader.setVisibility(View.VISIBLE);
        } else {
            galleryNameHeader.setVisibility(GONE);
        }


        if (galleryModel.getContainerDetails().getDescription() != null && !galleryModel.getContainerDetails().getDescription().isEmpty() && CategoryItem.ROOT_ALBUM != galleryModel.getContainerDetails()) {
            galleryDescriptionHeader.setText(galleryModel.getContainerDetails().getDescription());
        }

        if (galleryModel.getContainerDetails().getDescription() != null && !galleryModel.getContainerDetails().getDescription().isEmpty()) {
            galleryDescriptionHeader.setVisibility(VISIBLE);
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
            galleryDescriptionHeader.setVisibility(GONE);
            descriptionDropdownButton.setVisibility(GONE);
        }
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
        galleryModel = null;
        currentUsers = null;
        currentGroups = null;
        if (galleryListView != null) {
            galleryListView.setAdapter(null);
        }
    }

    @Override
    protected BasicPiwigoResponseListener buildPiwigoResponseListener(Context context) {
        return new CustomPiwigoResponseListener();
    }

    protected void onPiwigoUpdateResourceInfoResponse(PiwigoResponseBufferingHandler.PiwigoUpdateResourceInfoResponse response) {
        if (!viewAdapterListener.handleAlbumThumbnailInfoLoaded(response.getMessageId(), response.getPiwigoResource())) {
            if (deleteActionData != null && deleteActionData.isTrackingMessageId(response.getMessageId())) {
                //currently mid delete of resources.
                onResourceUnlinked(response);
            } else {
                onResourceMoved(response);
            }
        }
    }

    private void onAdminListOfAlbumsLoaded(PiwigoResponseBufferingHandler.PiwigoGetSubAlbumsAdminResponse response) {
        albumAdminList = response.getAdminList();
        try {
            adminCategories = albumAdminList.getDirectChildrenOfAlbum(galleryModel.getContainerDetails().getParentageChain(), galleryModel.getContainerDetails().getId());
        } catch(IllegalStateException e) {
            getUiHelper().showOrQueueDialogMessage(R.string.alert_error, getString(R.string.alert_error_album_no_longer_on_server), new UIHelper.QuestionResultAdapter() {
            });
            return;
        }

        // will only run if the album was found on the server
        if (!loadingMessageIds.containsValue("C")) {
            // categories have finished loading. Let's superimpose those not already present.
            boolean changed = galleryModel.addMissingAlbums(adminCategories);
            if (changed) {
                galleryModel.updateSpacerAlbumCount(albumsPerRow);
                viewAdapter.notifyDataSetChanged();
            }
        }

    }

    private void onResourceUnlinked(PiwigoResponseBufferingHandler.PiwigoUpdateResourceInfoResponse response) {
        if(deleteActionData.removeProcessedResource(response.getPiwigoResource())) {
            deleteActionData = null;
        }
    }

    protected void onResourceInfoRetrieved(PiwigoResponseBufferingHandler.PiwigoResourceInfoRetrievedResponse response) {
        if (deleteActionData != null && deleteActionData.isTrackingMessageId(response.getMessageId())) {
            this.deleteActionData.updateLinkedAlbums(response.getResource());
            if(this.deleteActionData.isResourceInfoAvailable()) {
                onDeleteResources(deleteActionData);
            }
        }
    }

    private HashSet<Long> buildPreselectedUserIds(List<Username> selectedUsernames) {
        HashSet<Long> preselectedUsernames = PiwigoUtils.toSetOfIds(selectedUsernames);
        if (selectedUsernames == null) {
            preselectedUsernames = new HashSet<>(0);
        }
        return preselectedUsernames;
    }

    private void onUsernamesRetrievedForSelectedGroups(PiwigoResponseBufferingHandler.PiwigoGetUsernamesListResponse response) {
        if (response.getItemsOnPage() == response.getPageSize()) {
            getUiHelper().showOrQueueDialogMessage(R.string.alert_error, getString(R.string.alert_error_too_many_users_message));
        } else {
            ArrayList<Username> usernames = response.getUsernames();
            userIdsInSelectedGroups = buildPreselectedUserIds(usernames);

            HashSet<Long> preselectedUsernames = getSetFromArray(currentUsers);

            if (usernameSelectionWantedNext) {
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
        if (response.getAlbumParentIdAltered() != null && response.getAlbumParentIdAltered() == galleryModel.getContainerDetails().getId()) {
            // need to refresh this gallery content.
            galleryIsDirty = true;
            reloadAlbumContent();
        }
    }

    private void onResourceMoved(PiwigoResponseBufferingHandler.PiwigoUpdateResourceInfoResponse response) {

        Basket basket = getBasket();
        if(basket == null) {
            Crashlytics.log("Basket is null when expecting to handle onResourceMoved event");
            return;
        }
        CategoryItem movedParent = basket.getContentParent();
        basket.removeItem(response.getPiwigoResource());
        movedParent.reducePhotoCount();
        if (movedParent.getRepresentativePictureId() != null && response.getPiwigoResource().getId() == movedParent.getRepresentativePictureId()) {
            if (movedParent.getPhotoCount() > 0) {
                movedResourceParentUpdateRequired = true;
            }
        }
        if (basket.getItemCount() == 0 && movedResourceParentUpdateRequired) {
            movedResourceParentUpdateRequired = false;
            addActiveServiceCall(R.string.progress_move_resources, new AlbumThumbnailUpdatedResponseHandler(movedParent.getId(), movedParent.getParentId(), null).invokeAsync(getContext()));
        }

        updateBasketDisplay(basket);


        List<Long> parentageChain = galleryModel.getContainerDetails().getParentageChain();
        if(!parentageChain.isEmpty()) {
            EventBus.getDefault().post(new AlbumAlteredEvent(parentageChain.get(0), galleryModel.getContainerDetails().getId()));
            for (int i = 1; i < parentageChain.size(); i++) {
                EventBus.getDefault().post(new AlbumAlteredEvent(parentageChain.get(i), parentageChain.get(i - 1)));
            }
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
        if (deleteActionData != null && deleteActionData.removeProcessedResources(response.getDeletedItemIds())) {
            deleteActionData = null;
        }
        reloadAlbumContent();
        // Now ensure any parents are also updated when next shown
        List<Long> parentageChain = galleryModel.getContainerDetails().getParentageChain();
        if(!parentageChain.isEmpty()) {
            EventBus.getDefault().post(new AlbumAlteredEvent(parentageChain.get(0), galleryModel.getContainerDetails().getId()));
            for (int i = 1; i < parentageChain.size(); i++) {
                EventBus.getDefault().post(new AlbumAlteredEvent(parentageChain.get(i), parentageChain.get(i - 1)));
            }
        }
    }

    private void updateAlbumDetails() {
        switch (updateAlbumDetailsProgress) {
            case UPDATE_IN_PROGRESS:
            case UPDATE_NOT_RUNNING:
                updateAlbumDetailsProgress = UPDATE_IN_PROGRESS;
                addActiveServiceCall(R.string.gallery_details_updating_progress_title, new AlbumUpdateInfoResponseHandler(galleryModel.getContainerDetails()).invokeAsync(getContext()));
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

    private void onReloadAlbum() {
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
                    case "AL":
                        loadAdminListOfAlbums();
                        break;
                    default:
                        int page = Integer.valueOf(itemToLoad);
                        loadAlbumResourcesPage(page);
                        break;
                }
            }
        }
    }

    private void onGetSubGalleries(final PiwigoResponseBufferingHandler.PiwigoGetSubAlbumsResponse response) {

        synchronized (this) {
            if (galleryModel.getContainerDetails().isRoot()) {
                galleryModel.updateMaxExpectedItemCount(response.getAlbums().size());
            }
            if(response.getAlbums().size() > 1) {
                if(!galleryModel.containsItem(CategoryItem.ALBUM_HEADING)) {
                    galleryModel.addItem(CategoryItem.ALBUM_HEADING);
                }
            }
            for (CategoryItem item : response.getAlbums()) {
                if (item.getId() != galleryModel.getContainerDetails().getId()) {
                    galleryModel.addItem(item);
                } else {
                    // copy the extra data across not retrieved by default.
                    item.setGroups(galleryModel.getContainerDetails().getGroups());
                    item.setUsers(galleryModel.getContainerDetails().getUsers());
                    // now update the reference.
                    galleryModel.setContainerDetails(item);
                }
            }
            if (PiwigoSessionDetails.isAdminUser(ConnectionPreferences.getActiveProfile()) && !loadingMessageIds.containsValue("AL")) {
                // admin album list has already finished loading. Let's superimpose those not already present.
                // sink changed value - don't care here.
                galleryModel.addMissingAlbums(adminCategories);
            }
            galleryModel.updateSpacerAlbumCount(albumsPerRow);
            viewAdapter.notifyDataSetChanged();
        }
        emptyGalleryLabel.setVisibility(getUiHelper().getActiveServiceCallCount() == 0 && galleryModel.getItemCount() == 0 ? VISIBLE : GONE);
    }

    private void onGetResources(final PiwigoResponseBufferingHandler.PiwigoGetResourcesResponse response) {
        synchronized (this) {
            if(response.getPage() == 0 && response.getResources().size() > 0) {
                if(!galleryModel.containsItem(CategoryItem.PICTURE_HEADING)) {
                    galleryModel.addItem(CategoryItem.PICTURE_HEADING);
                }
            }
            galleryModel.addItemPage(response.getPage(), response.getPageSize(), response.getResources());
            viewAdapter.notifyDataSetChanged();
        }
        emptyGalleryLabel.setVisibility(getUiHelper().getActiveServiceCallCount() == 0 && galleryModel.getItemCount() == 0 ? VISIBLE : GONE);
    }

    private void onAlbumContentAltered(final PiwigoResponseBufferingHandler.PiwigoUpdateAlbumContentResponse response) {
        List<Long> parentageChain = galleryModel.getContainerDetails().getParentageChain();
        if(!parentageChain.isEmpty()) {
            EventBus.getDefault().post(new AlbumAlteredEvent(parentageChain.get(0), galleryModel.getContainerDetails().getId()));
            for (int i = 1; i < parentageChain.size(); i++) {
                EventBus.getDefault().post(new AlbumAlteredEvent(parentageChain.get(i), parentageChain.get(i - 1)));
            }
        }
        galleryIsDirty = true;
        //we've altered the album content (now update this album view to reflect the server content)
        reloadAlbumContent();
    }

    private void onAlbumInfoAltered(final PiwigoResponseBufferingHandler.PiwigoUpdateAlbumInfoResponse response) {
        galleryModel.setContainerDetails(response.getAlbum());
        updateAlbumPermissions();
    }

    private void onAlbumPermissionsAdded(PiwigoResponseBufferingHandler.PiwigoAddAlbumPermissionsResponse response) {
        HashSet<Long> newGroupsSet = SetUtils.asSet(galleryModel.getContainerDetails().getGroups());
        newGroupsSet.addAll(response.getGroupIdsAffected());
        galleryModel.getContainerDetails().setGroups(SetUtils.asArray(newGroupsSet));
        HashSet<Long> newUsersSet = SetUtils.asSet(galleryModel.getContainerDetails().getUsers());
        newUsersSet.addAll(response.getUserIdsAffected());
        galleryModel.getContainerDetails().setUsers(SetUtils.asArray(newUsersSet));

        if (!removeAlbumPermissions()) {
            onAlbumUpdateFinished();
        }
    }

    private void onAlbumPermissionsRemoved(PiwigoResponseBufferingHandler.PiwigoRemoveAlbumPermissionsResponse response) {
        HashSet<Long> newGroupsSet = SetUtils.asSet(galleryModel.getContainerDetails().getGroups());
        newGroupsSet.removeAll(response.getGroupIdsAffected());
        galleryModel.getContainerDetails().setGroups(SetUtils.asArray(newGroupsSet));
        HashSet<Long> newUsersSet = SetUtils.asSet(galleryModel.getContainerDetails().getUsers());
        newUsersSet.removeAll(response.getUserIdsAffected());
        galleryModel.getContainerDetails().setUsers(SetUtils.asArray(newUsersSet));

        onAlbumUpdateFinished();
    }

    private void updateAlbumPermissions() {
        if (galleryModel.getContainerDetails().isPrivate()) {
            if (!addingAlbumPermissions() && !removeAlbumPermissions()) {
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
        final HashSet<Long> newlyAddedGroups = SetUtils.difference(wantedAlbumGroups, SetUtils.asSet(galleryModel.getContainerDetails().getGroups()));
        // Get all users newly added to the list of permissions
        HashSet<Long> wantedAlbumUsers = SetUtils.asSet(currentUsers);
        final HashSet<Long> newlyAddedUsers = SetUtils.difference(wantedAlbumUsers, SetUtils.asSet(galleryModel.getContainerDetails().getUsers()));

        PiwigoSessionDetails sessionDetails = PiwigoSessionDetails.getInstance(ConnectionPreferences.getActiveProfile());
        long currentLoggedInUserId = sessionDetails.getUserId();

        Set<Long> thisUsersGroupsWithoutAccess = SetUtils.difference(sessionDetails.getGroupMemberships(), wantedAlbumGroups);

        if (currentLoggedInUserId >= 0) {
            boolean noGroupAccess = thisUsersGroupsWithoutAccess.size() == sessionDetails.getGroupMemberships().size();
            boolean noUserAccess = !wantedAlbumUsers.contains(currentLoggedInUserId);

            if (noGroupAccess && noUserAccess && !sessionDetails.isAdminUser()) {
                //You're attempting to remove your own permission to access this album. Add it back in.
                currentUsers = Arrays.copyOf(currentUsers, currentUsers.length + 1);
                currentUsers[currentUsers.length - 1] = currentLoggedInUserId;
                wantedAlbumUsers.add(currentLoggedInUserId);
                // update the ui.
                allowedUsersField.setText(String.format(getString(R.string.click_to_view_pattern), currentUsers.length));
                int msgId = R.string.alert_information_own_user_readded_to_permissions_list;
                getUiHelper().showOrQueueDialogMessage(R.string.alert_information, getString(msgId), R.string.button_ok, false, new UIHelper.QuestionResultAdapter() {

                    @Override
                    public void onResult(AlertDialog dialog, Boolean positiveAnswer) {
                        if (Boolean.TRUE == positiveAnswer) {
                            addingAlbumPermissions();
                        }
                    }
                });
                return true;
            }

            if (newlyAddedGroups.size() > 0 || newlyAddedUsers.size() > 0) {

                if (galleryModel.getContainerDetails().getSubCategories() > 0) {
                    if (currentLoggedInUserId >= 0 && newlyAddedUsers.contains(currentLoggedInUserId)) {
                        //we're having to force add this user explicitly therefore for safety we need to apply the change recursively
                        String msg = String.format(getString(R.string.alert_information_add_album_permissions_recursively_pattern), galleryModel.getContainerDetails().getSubCategories());
                        getUiHelper().showOrQueueDialogMessage(R.string.alert_information, msg, R.string.button_ok, false, new UIHelper.QuestionResultAdapter() {

                            @Override
                            public void onResult(AlertDialog dialog, Boolean positiveAnswer) {
                                if (Boolean.TRUE == positiveAnswer) {
                                    addActiveServiceCall(R.string.gallery_details_updating_progress_title, new AlbumAddPermissionsResponseHandler(galleryModel.getContainerDetails(), newlyAddedGroups, newlyAddedUsers, true).invokeAsync(getContext()));
                                }
                            }
                        });
                    } else {

                        String msg = String.format(getString(R.string.alert_confirm_add_album_permissions_recursively_pattern), newlyAddedGroups.size(), newlyAddedUsers.size(), galleryModel.getContainerDetails().getSubCategories());
                        getUiHelper().showOrQueueDialogQuestion(R.string.alert_confirm_title, msg, R.string.button_no, R.string.button_yes, new UIHelper.QuestionResultAdapter() {

                            @Override
                            public void onResult(AlertDialog dialog, Boolean positiveAnswer) {
                                if (Boolean.TRUE == positiveAnswer) {
                                    addActiveServiceCall(R.string.gallery_details_updating_progress_title, new AlbumAddPermissionsResponseHandler(galleryModel.getContainerDetails(), newlyAddedGroups, newlyAddedUsers, true).invokeAsync(getContext()));
                                } else {
                                    addActiveServiceCall(R.string.gallery_details_updating_progress_title, new AlbumAddPermissionsResponseHandler(galleryModel.getContainerDetails(), newlyAddedGroups, newlyAddedUsers, false).invokeAsync(getContext()));
                                }
                            }
                        });
                    }
                } else {
                    // no need to be recursive as this album is a leaf node.
                    addActiveServiceCall(R.string.gallery_details_updating_progress_title, new AlbumAddPermissionsResponseHandler(galleryModel.getContainerDetails(), newlyAddedGroups, newlyAddedUsers, false).invokeAsync(getContext()));
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
        final HashSet<Long> newlyRemovedGroups = SetUtils.difference(SetUtils.asSet(galleryModel.getContainerDetails().getGroups()), SetUtils.asSet(currentGroups));
        // Get all users newly added to the list of permissions
        HashSet<Long> currentAlbumUsers = SetUtils.asSet(galleryModel.getContainerDetails().getUsers());
        final HashSet<Long> newlyRemovedUsers = SetUtils.difference(currentAlbumUsers, SetUtils.asSet(currentUsers));

        if (newlyRemovedGroups.size() > 0 || newlyRemovedUsers.size() > 0) {

            if (galleryModel.getContainerDetails().getSubCategories() > 0) {
                String message = String.format(getString(R.string.alert_confirm_really_remove_album_permissions_pattern), newlyRemovedGroups.size(), newlyRemovedUsers.size());
                getUiHelper().showOrQueueDialogQuestion(R.string.alert_confirm_title, message, R.string.button_no, R.string.button_yes, new UIHelper.QuestionResultAdapter() {

                    @Override
                    public void onResult(AlertDialog dialog, Boolean positiveAnswer) {
                        if (Boolean.TRUE == positiveAnswer) {
                            addActiveServiceCall(R.string.gallery_details_updating_progress_title, new AlbumRemovePermissionsResponseHandler(galleryModel.getContainerDetails(), newlyRemovedGroups, newlyRemovedUsers).invokeAsync(getContext()));
                        } else {
                            onAlbumUpdateFinished();
                        }
                    }
                });
            } else {
                addActiveServiceCall(R.string.gallery_details_updating_progress_title, new AlbumRemovePermissionsResponseHandler(galleryModel.getContainerDetails(), newlyRemovedGroups, newlyRemovedUsers).invokeAsync(getContext()));
            }

            return true;
        } else {
            return false;
        }
    }

    private void onAlbumUpdateFinished() {
        updateAlbumDetailsProgress = UPDATE_NOT_RUNNING;
        setGalleryHeadings();
        getUiHelper().hideProgressIndicator();
        if (editingItemDetails) {
            editingItemDetails = false;
            currentUsers = galleryModel.getContainerDetails().getUsers();
            currentGroups = galleryModel.getContainerDetails().getGroups();
            fillGalleryEditFields();
        }
    }

    private void onAlbumStatusUpdated(PiwigoResponseBufferingHandler.PiwigoSetAlbumStatusResponse response) {
        updateAlbumPermissions();
    }

    private void onAlbumPermissionsRetrieved(PiwigoResponseBufferingHandler.PiwigoAlbumPermissionsRetrievedResponse response) {

        galleryModel.setContainerDetails(response.getAlbum());
        currentUsers = this.galleryModel.getContainerDetails().getUsers();
        currentGroups = this.galleryModel.getContainerDetails().getGroups();
        allowedUsersField.setText(String.format(getString(R.string.click_to_view_pattern), currentUsers.length));
        allowedGroupsField.setText(String.format(getString(R.string.click_to_view_pattern), currentGroups.length));
    }

    private void onAlbumDeleted(PiwigoResponseBufferingHandler.PiwigoAlbumDeletedResponse response) {
        boolean exitFragment = false;
        CategoryItem galleryDetails = galleryModel.getContainerDetails();
        if (galleryDetails.getId() == response.getAlbumId()) {
            // we've deleted the current album.
            AlbumDeletedEvent event = new AlbumDeletedEvent(galleryDetails);
            EventBus.getDefault().post(event);
            exitFragment = true;
        } else {
            if (albumAdminList != null) {
                CategoryItem adminCopyOfCurrentGallery = albumAdminList.getAlbum(galleryDetails);
                if (adminCopyOfCurrentGallery != null) {
                    boolean removedFromAdminList = adminCopyOfCurrentGallery.removeChildAlbum(response.getAlbumId());
                    adminCategories = adminCopyOfCurrentGallery.getChildAlbums();
                }
                galleryDetails.removeChildAlbum(response.getAlbumId()); // will return false if it was the admin copy (unlikely but do after to be sure).
            }
            //we've deleted a child album (now update this album view to reflect the server content)
            galleryIsDirty = true;
            reloadAlbumContent();
        }
        List<Long> parentageChain = galleryDetails.getParentageChain();
        if(!parentageChain.isEmpty()) {
            EventBus.getDefault().post(new AlbumAlteredEvent(parentageChain.get(0), galleryDetails.getId()));
            for (int i = 1; i < parentageChain.size(); i++) {
                EventBus.getDefault().post(new AlbumAlteredEvent(parentageChain.get(i), parentageChain.get(i - 1)));
            }
        }
        if(exitFragment) {
            getFragmentManager().popBackStack();
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(UsernameSelectionCompleteEvent usernameSelectionCompleteEvent) {

        PiwigoSessionDetails sessionDetails = PiwigoSessionDetails.getInstance(ConnectionPreferences.getActiveProfile());

        if (getUiHelper().isTrackingRequest(usernameSelectionCompleteEvent.getActionId())) {
            long[] selectedUsersArr = new long[usernameSelectionCompleteEvent.getCurrentSelection().size()];
            int i = 0;
            long currentLoggedInUserId = sessionDetails.getUserId();
            boolean currentUserExplicitlyPresent = false;
            for (Username user : usernameSelectionCompleteEvent.getSelectedItems()) {
                selectedUsersArr[i++] = user.getId();
                if (currentLoggedInUserId == user.getId()) {
                    currentUserExplicitlyPresent = true;
                }
            }
            currentUsers = selectedUsersArr;
            HashSet<Long> wantedAlbumGroups = SetUtils.asSet(currentGroups);
            HashSet<Long> currentUsersGroupMemberships = sessionDetails.getGroupMemberships();
            Set<Long> thisUsersGroupsWithoutAccess = SetUtils.difference(currentUsersGroupMemberships, wantedAlbumGroups);
            boolean noGroupAccess = thisUsersGroupsWithoutAccess.size() == currentUsersGroupMemberships.size();
            if (currentLoggedInUserId >= 0 && noGroupAccess && !currentUserExplicitlyPresent && !sessionDetails.isAdminUser()) {
                //You've attempted to remove your own permission to access this album. Adding it back in.
                currentUsers = Arrays.copyOf(currentUsers, currentUsers.length + 1);
                currentUsers[currentUsers.length - 1] = currentLoggedInUserId;
                getUiHelper().showOrQueueDialogMessage(R.string.alert_information, getString(R.string.alert_information_own_user_readded_to_permissions_list));
            }
            fillUsernamesField(allowedUsersField, usernameSelectionCompleteEvent.getSelectedItems());
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(AlbumCreatedEvent event) {
        if (getUiHelper().isTrackingRequest(event.getActionId())) {
            galleryIsDirty = true;
            if (isResumed()) {
                reloadAlbumContent();
            }
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(GroupSelectionCompleteEvent groupSelectionCompleteEvent) {
        if (getUiHelper().isTrackingRequest(groupSelectionCompleteEvent.getActionId())) {
            long[] selectedGroupsArr = new long[groupSelectionCompleteEvent.getCurrentSelection().size()];
            int i = 0;
            for (Group group : groupSelectionCompleteEvent.getSelectedItems()) {
                selectedGroupsArr[i++] = group.getId();
            }
            currentGroups = selectedGroupsArr;
            userIdsInSelectedGroups = null;
            fillGroupsField(allowedGroupsField, groupSelectionCompleteEvent.getSelectedItems());

            ArrayList<Long> selectedGroupIds = new ArrayList<>(currentGroups.length);
            for (long groupId : currentGroups) {
                selectedGroupIds.add(groupId);
            }
            if (selectedGroupIds.size() == 0) {
                userIdsInSelectedGroups = new HashSet<>(0);
            } else {
                addActiveServiceCall(R.string.progress_loading_group_details, new UsernamesGetListResponseHandler(selectedGroupIds, 0, 100).invokeAsync(getContext()));
            }
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(PiwigoLoginSuccessEvent event) {
        displayControlsBasedOnSessionState();
        setEditItemDetailsControlsStatus();
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(AppLockedEvent event) {
        if (isResumed()) {
            if (editingItemDetails) {
                discardButton.callOnClick();
            } else {
                if (!galleryModel.getContainerDetails().isRoot()) {
                    displayControlsBasedOnSessionState();
                }
            }
            if (viewAdapter.isMultiSelectionAllowed() && isPreventItemSelection()) {
                viewPrefs.withAllowMultiSelect(false);
                viewPrefs.setAllowItemSelection(false);
                viewAdapter.notifyDataSetChanged(); //TODO check this does what it should...
            }

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
        if (isResumed()) {
            if (!galleryModel.getContainerDetails().isRoot()) {
                displayControlsBasedOnSessionState();
                setEditItemDetailsControlsStatus();
            }
            if (viewAdapter.isMultiSelectionAllowed() && isPreventItemSelection()) {
                viewPrefs.withAllowMultiSelect(false);
                viewPrefs.setAllowItemSelection(false);
                viewAdapter.notifyDataSetChanged(); //TODO check this does what it should...
            }
        } else {
            // if not showing, just flush the state and rebuild the page
            galleryIsDirty = true;
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(final BadRequestUsingHttpToHttpsServerEvent event) {
        getUiHelper().showOrQueueDialogQuestion(R.string.alert_question_title, getString(R.string.alert_bad_request_http_to_https), R.string.button_no, R.string.button_yes, new UIHelper.QuestionResultAdapter() {

            @Override
            public void onResult(AlertDialog dialog, Boolean positiveAnswer) {
                if (positiveAnswer != null && positiveAnswer) {
                    event.getConnectionPreferences().setForceHttps(prefs, getContext(), true);
                }
            }
        });
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(final BadRequestUsesRedirectionServerEvent event) {
        getUiHelper().showOrQueueDialogQuestion(R.string.alert_question_title, getString(R.string.alert_bad_request_follow_redirects), R.string.button_no, R.string.button_yes, new UIHelper.QuestionResultAdapter() {

            @Override
            public void onResult(AlertDialog dialog, Boolean positiveAnswer) {
                if (positiveAnswer != null && positiveAnswer) {
                    getUiHelper().addActiveServiceCall(getString(R.string.loading_new_server_configuration), new HttpConnectionCleanup(event.getConnectionPreferences(), getContext()).start());
                    event.getConnectionPreferences().setFollowHttpRedirects(prefs, getContext(), true);
                }
            }
        });
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(AlbumAlteredEvent albumAlteredEvent) {
        if (galleryModel != null && albumAlteredEvent.isRelevant(galleryModel.getContainerDetails().getId())) {
            galleryIsDirty = true;
            if (isResumed()) {
                reloadAlbumContent();
            }
            if(albumAlteredEvent.isCascadeToParents()) {
                List<Long> parentageChain = galleryModel.getContainerDetails().getParentageChain();
                if(!parentageChain.isEmpty()) {
                    EventBus.getDefault().post(new AlbumAlteredEvent(parentageChain.get(0), galleryModel.getContainerDetails().getId()));
                    for (int i = 1; i < parentageChain.size(); i++) {
                        EventBus.getDefault().post(new AlbumAlteredEvent(parentageChain.get(i), parentageChain.get(i - 1)));
                    }
                }
            }
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(AlbumItemDeletedEvent event) {
        if(event.item.getParentId() == galleryModel.getId()) {
            int fullGalleryIdx = galleryModel.getItemIdx(event.item);
            // now removed from the backing gallery too.
            galleryModel.remove(fullGalleryIdx);
        }
    }

    private static class DeleteActionData implements Serializable {
        private static final long serialVersionUID = 835907412196288214L;
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
            for (Iterator<ResourceItem> it = selectedItems.iterator(); it.hasNext(); ) {
                ResourceItem r = it.next();
                if (deletedItemIds.contains(r.getId())) {
                    it.remove();
                }
            }
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
    }

    protected class CustomPiwigoResponseListener extends BasicPiwigoResponseListener {

        @Override
        public void onBeforeHandlePiwigoResponse(PiwigoResponseBufferingHandler.Response response) {
            if (isVisible()) {
                updateActiveSessionDetails();
            }
            super.onBeforeHandlePiwigoResponse(response);
        }

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
                } else if (response instanceof PiwigoResponseBufferingHandler.PiwigoGetSubAlbumsAdminResponse) {
                    onAdminListOfAlbumsLoaded((PiwigoResponseBufferingHandler.PiwigoGetSubAlbumsAdminResponse) response);
                } else if (response instanceof PiwigoResponseBufferingHandler.PiwigoUpdateAlbumContentResponse) {
                    onAlbumContentAltered((PiwigoResponseBufferingHandler.PiwigoUpdateAlbumContentResponse) response);
                } else if (response instanceof PiwigoResponseBufferingHandler.PiwigoUpdateResourceInfoResponse) {
                    onPiwigoUpdateResourceInfoResponse((PiwigoResponseBufferingHandler.PiwigoUpdateResourceInfoResponse) response);
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
                    onResourceInfoRetrieved((PiwigoResponseBufferingHandler.PiwigoResourceInfoRetrievedResponse) response);
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
                        switch (failedCall) {
                            case "U":
                                emptyGalleryLabel.setText(R.string.gallery_update_failed_text);
                                break;
                            case "P":
                                emptyGalleryLabel.setText(R.string.gallery_permissions_load_failed_text);
                                break;
                            case "AL":
                                emptyGalleryLabel.setText(R.string.gallery_admin_albums_list_load_failed_text);
                                break;
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

    private class SpanSizeLookup extends GridLayoutManager.SpanSizeLookup {

        private final int totalSpans;
        private final int spansPerAlbum;
        private final int spansPerImage;
        private final ResourceContainer<CategoryItem, GalleryItem> galleryModel;

        public SpanSizeLookup(ResourceContainer<CategoryItem, GalleryItem> galleryModel, int totalSpans, int spansPerAlbum, int spansPerImage) {
            this.totalSpans = totalSpans;
            this.spansPerAlbum = spansPerAlbum;
            this.spansPerImage = spansPerImage;
            this.galleryModel = galleryModel;
        }

        @Override
        public int getSpanSize(int position) {
            // ensure that app cannot crash due to position being out of bounds.
            //FIXME - why would position be outside model size? What happens next now it doesn't crash here?
            if (position < 0 || galleryModel.getItemCount() <= position) {
                return 1;
            }

            int itemType = galleryModel.getItemByIdx(position).getType();
            switch (itemType) {
                case GalleryItem.ALBUM_HEADING_TYPE:
                    return totalSpans;
                case GalleryItem.CATEGORY_TYPE:
                    return spansPerAlbum;
                case GalleryItem.PICTURE_RESOURCE_TYPE:
                    return spansPerImage;
                case GalleryItem.PICTURE_HEADING_TYPE:
                    return totalSpans;
                case GalleryItem.VIDEO_RESOURCE_TYPE:
                    return spansPerImage;
                default:
                    return totalSpans;
            }
        }
    }

    private class AlbumViewAdapterListener extends AlbumItemRecyclerViewAdapter.MultiSelectStatusAdapter {

        private Map<Long, CategoryItem> albumThumbnailLoadActions = new HashMap<>();

        public Map<Long, CategoryItem> getAlbumThumbnailLoadActions() {
            return albumThumbnailLoadActions;
        }

        public void setAlbumThumbnailLoadActions(Map<Long, CategoryItem> albumThumbnailLoadActions) {
            this.albumThumbnailLoadActions = albumThumbnailLoadActions;
        }

        @Override
        public void onMultiSelectStatusChanged(BaseRecyclerViewAdapter adapter, boolean multiSelectEnabled) {
//            bulkActionsContainer.setVisibility(multiSelectEnabled?VISIBLE:GONE);
        }

        @Override
        public void onItemSelectionCountChanged(BaseRecyclerViewAdapter adapter, int size) {
            bulkActionsContainer.setVisibility(size > 0 || getBasket().getItemCount() > 0 ? VISIBLE : GONE);
            updateBasketDisplay(getBasket());
        }

        @Override
        public void onCategoryLongClick(CategoryItem album) {
            onAlbumDeleteRequest(album);
        }


        @Override
        public void notifyAlbumThumbnailInfoLoadNeeded(CategoryItem mItem) {
            PictureResourceItem resourceItem = new PictureResourceItem(mItem.getRepresentativePictureId(), null, null, null, null, null);
            String multimediaExtensionList = AlbumViewPreferences.getKnownMultimediaExtensions(prefs, getContext());
            ImageGetInfoResponseHandler handler = new ImageGetInfoResponseHandler<>(resourceItem, multimediaExtensionList);
            long messageId = handler.invokeAsync(getContext());
            albumThumbnailLoadActions.put(messageId, mItem);
            getUiHelper().addBackgroundServiceCall(messageId);
        }

        public boolean handleAlbumThumbnailInfoLoaded(long messageId, ResourceItem thumbnailResource) {
            CategoryItem item = albumThumbnailLoadActions.remove(messageId);
            if (item == null) {
                return false;
            }
            item.setThumbnailUrl(thumbnailResource.getThumbnailUrl());

            RecyclerView.ViewHolder vh = galleryListView.findViewHolderForItemId(item.getId());
            if (vh != null) {
                // item currently displaying.
                viewAdapter.redrawItem((AlbumItemViewHolder) vh, item);
            }

            return true;
        }
    }
}
