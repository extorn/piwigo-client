package delit.piwigoclient.ui.album.view;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.AppCompatCheckBox;
import androidx.appcompat.widget.AppCompatImageView;
import androidx.appcompat.widget.AppCompatTextView;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.ViewModelProviders;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.crashlytics.android.Crashlytics;
import com.google.android.gms.ads.AdView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.wunderlist.slidinglayer.CustomSlidingLayer;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import delit.libs.ui.util.BundleUtils;
import delit.libs.ui.util.ParcelUtils;
import delit.libs.ui.view.button.CustomImageButton;
import delit.libs.ui.view.recycler.BaseRecyclerViewAdapter;
import delit.libs.ui.view.recycler.EndlessRecyclerViewScrollListener;
import delit.libs.util.CollectionUtils;
import delit.libs.util.SetUtils;
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
import delit.piwigoclient.model.piwigo.ServerConfig;
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
import delit.piwigoclient.piwigoApi.handlers.AlbumSetStatusResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.AlbumThumbnailUpdatedResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.AlbumUpdateInfoResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.AlbumsGetFirstAvailableAlbumResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.BaseImageGetInfoResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.BaseImageUpdateInfoResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.BaseImagesGetResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.ImageChangeParentAlbumHandler;
import delit.piwigoclient.piwigoApi.handlers.ImageCopyToAlbumResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.ImageDeleteResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.ImageGetInfoResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.ImageSetPrivacyLevelResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.ImageUpdateInfoResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.ImagesGetResponseHandler;
import delit.piwigoclient.piwigoApi.handlers.UsernamesGetListResponseHandler;
import delit.piwigoclient.ui.AdsManager;
import delit.piwigoclient.ui.MainActivity;
import delit.piwigoclient.ui.PicassoFactory;
import delit.piwigoclient.ui.common.FragmentUIHelper;
import delit.piwigoclient.ui.common.UIHelper;
import delit.piwigoclient.ui.common.fragment.MyFragment;
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
import delit.piwigoclient.ui.model.PiwigoAlbumModel;

import static android.view.View.GONE;
import static android.view.View.INVISIBLE;
import static android.view.View.VISIBLE;
import static android.widget.AdapterView.INVALID_POSITION;

/**
 * A fragment representing a list of Items.
 */
public abstract class AbstractViewAlbumFragment extends MyFragment<AbstractViewAlbumFragment> {

    private static final String ARG_ALBUM = "album";
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
    private static final String STATE_DELETE_ACTION_DATA = "bulkResourceActionData";
    private static final String STATE_USER_GUID = "userGuid";
    private static final String STATE_ALBUMS_PER_ROW = "albumsPerRow";

    private static final int UPDATE_IN_PROGRESS = 1;
    private static final int UPDATE_SETTING_ADDING_PERMISSIONS = 2;
    private static final int UPDATE_SETTING_REMOVING_PERMISSIONS = 3;
    private static final int UPDATE_NOT_RUNNING = 0;
    public static final String TAG = "AbsViewAlbumFrag";
    public static final String RESUME_ACTION = "ALBUM";
    private static final String STATE_SELECTED_ITEMS = "selectedItemIds";
    private static transient PiwigoAlbumAdminList albumAdminList;
    private final HashMap<Long, String> loadingMessageIds = new HashMap<>(2);
    private final ArrayList<String> itemsToLoad = new ArrayList<>(0);
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
    private FloatingActionButton bulkActionButtonPermissions;
    private FloatingActionButton bulkActionButtonDelete;
    private FloatingActionButton bulkActionButtonCopy;
    private FloatingActionButton bulkActionButtonCut;
    private FloatingActionButton bulkActionButtonPaste;
    private View basketView;
    private TextView emptyGalleryLabel;
    private TextView allowedGroupsFieldLabel;
    private TextView allowedUsersFieldLabel;
    private CompoundButton.OnCheckedChangeListener privacyStatusFieldListener;
    // Start fields maintained in saved session state.
    private int albumsPerRow; // calculated each time view created.
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
    private BulkResourceActionData bulkResourceActionData;
    private long userGuid;
    private transient List<CategoryItem> adminCategories;
    private AppCompatImageView actionIndicatorImg;
    private RecyclerView galleryListView;
    private AlbumViewAdapterListener viewAdapterListener;
    private AlbumItemRecyclerViewAdapterPreferences viewPrefs;
    private CustomSlidingLayer bottomSheet;
    private EndlessRecyclerViewScrollListener galleryListViewScrollListener;
    private CategoryItem albumDetails;
    private boolean reopening;

    /**
     * Mandatory empty constructor for the fragment manager to instantiate the
     * fragment (e.g. upon screen orientation changes).
     */
    public AbstractViewAlbumFragment() {
    }

    public static AbstractViewAlbumFragment newInstance(CategoryItem album) {
        AbstractViewAlbumFragment fragment = new ViewAlbumFragment();
        fragment.addArguments(album);
        return fragment;
    }

    private void addArguments(CategoryItem album) {
        Bundle args = new Bundle();
        args.putParcelable(ARG_ALBUM, album);
        setArguments(args);
    }

    private static void deleteResourcesFromServerForever(FragmentUIHelper<AbstractViewAlbumFragment> uiHelper, final HashSet<Long> selectedItemIds, final HashSet<ResourceItem> selectedItems) {
        String msg = uiHelper.getContext().getString(R.string.alert_confirm_really_delete_items_from_server);
        uiHelper.showOrQueueDialogQuestion(R.string.alert_confirm_title, msg, R.string.button_cancel, R.string.button_ok, new DeleteResourceForeverAction<>(uiHelper, selectedItemIds, selectedItems));
    }

    public static boolean canHandleReopenAction(UIHelper uiHelper) {
        SharedPreferences resumePrefs = uiHelper.getResumePrefs();
        if (AbstractViewAlbumFragment.RESUME_ACTION.equals(resumePrefs.getString("reopenAction", null))) {
            ConnectionPreferences.ProfilePreferences activeProfile = ConnectionPreferences.getActiveProfile();
            if (activeProfile.getProfileId(uiHelper.getPrefs(), uiHelper.getContext()).equals(resumePrefs.getString("reopenProfileId", null))) {
                // Can handle it. Lets try.
                ArrayList<Long> albumPath = CollectionUtils.longsFromCsvList(resumePrefs.getString("reopenAlbumPath", null));
                if (albumPath.size() > 1) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (getArguments() != null) {
            loadModelFromArguments();
        } else {
            // restore previous viewed album.
            SharedPreferences resumePrefs = getUiHelper().getResumePrefs();
            if (AbstractViewAlbumFragment.RESUME_ACTION.equals(resumePrefs.getString("reopenAction", null))) {
                ArrayList<Long> albumPath = CollectionUtils.longsFromCsvList(resumePrefs.getString("reopenAlbumPath", null));
                reopening = true;
                String preferredAlbumThumbnailSize = AlbumViewPreferences.getPreferredAlbumThumbnailSize(prefs, requireContext());
                AlbumsGetFirstAvailableAlbumResponseHandler handler = new AlbumsGetFirstAvailableAlbumResponseHandler(albumPath, preferredAlbumThumbnailSize);
                getUiHelper().addActionOnResponse(addNonBlockingActiveServiceCall(R.string.progress_loading_album_content, handler), new LoadAlbumTreeAction());
            } else {
                throw new IllegalStateException("Unable to resume album fragment - no resume details stored");
            }
        }
    }

    protected void loadModelFromArguments() {
        albumDetails = getArguments().getParcelable(ARG_ALBUM);
        if (albumDetails == null) {
            throw new IllegalStateException("album details are null for some reason");
        }
        albumDetails.forcePermissionsReload();
        galleryModel = ViewModelProviders.of(requireActivity()).get("" + albumDetails.getId(), PiwigoAlbumModel.class).getPiwigoAlbum(albumDetails).getValue();

        galleryModel.setContainerDetails(albumDetails);
        galleryIsDirty = true;
    }

    protected void onReopenModelRetrieved(CategoryItem rootAlbum, CategoryItem album) {
        try {
            reopening = false;
            galleryIsDirty = true;
            albumDetails = album;
            addArguments(albumDetails); // ensure we don't reopen next time - just handle as usual!

            // add the album path to the ViewModelProvider
            CategoryItem thisAlbum = album;
            do {
                Long albumParentId = thisAlbum.getParentId();
                if (albumParentId != null) {
                    CategoryItem catItem = rootAlbum.findChild(albumParentId);
                    catItem.removeChildAlbum(thisAlbum);
                    ViewModelProviders.of(requireActivity()).get("" + catItem.getId(), PiwigoAlbumModel.class).getPiwigoAlbum(catItem);
                    thisAlbum = catItem;
                } else {
                    Crashlytics.log(Log.WARN, TAG, "Attempt to get parent album for album with id " + album.getId());
                }
            } while (!thisAlbum.isRoot());
            // now add the album to the ViewModelProvider and then get the current value
            galleryModel = ViewModelProviders.of(requireActivity()).get("" + albumDetails.getId(), PiwigoAlbumModel.class).getPiwigoAlbum(albumDetails).getValue();
            if (galleryModel == null) {
                Crashlytics.log(Log.ERROR, TAG, "Gallery model is unexpectedly null on reopening model with album " + album);
            }
            populateViewFromModelEtc(requireView(), null);
            populateViewFromModelEtcOnResume();
            // below needed? called on re-login so prob not
//            loadAlbumPermissionsIfNeeded();
//            displayControlsBasedOnSessionState();
//            setEditItemDetailsControlsStatus();
            updatePageTitle();
        } catch (IllegalStateException e) {
            // do nothing - if not attached - app likely closed again.
        }
    }

    private void fillGroupsField(TextView allowedGroupsField, Collection<Group> selectedGroups) {
        if(selectedGroups.size() == 0) {
            allowedGroupsField.setText(getString(R.string.click_to_view_pattern, 0));
        } else {
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
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        UIHelper.recycleImageViewContent(actionIndicatorImg);
        galleryModel = null;
        currentUsers = null;
        currentGroups = null;
        if (galleryListView != null) {
            galleryListView.setAdapter(null);
        }
    }

    private void fillUsernamesField(TextView allowedUsernamesField, Collection<Username> selectedUsernames) {
        if(selectedUsernames.size() == 0) {
            allowedUsernamesField.setText(getString(R.string.click_to_view_pattern, 0));
        } else {
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
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if(viewPrefs != null) {
            viewPrefs.storeToBundle(outState);
        }
        outState.putBoolean(STATE_EDITING_ITEM_DETAILS, editingItemDetails);
        outState.putBoolean(STATE_INFORMATION_SHOWING, informationShowing);
        outState.putLongArray(STATE_CURRENT_GROUPS, currentGroups);
        outState.putLongArray(STATE_CURRENT_USERS, currentUsers);
        outState.putBoolean(STATE_GALLERY_DIRTY, galleryIsDirty);
        BundleUtils.writeMap(outState, STATE_GALLERY_ACTIVE_LOAD_THREADS, loadingMessageIds);
        outState.putStringArrayList(STATE_GALLERY_LOADS_TO_RETRY, itemsToLoad);
        outState.putBoolean(STATE_MOVED_RESOURCE_PARENT_UPDATE_NEEDED, movedResourceParentUpdateRequired);
        outState.putInt(STATE_UPDATE_ALBUM_DETAILS_PROGRESS, updateAlbumDetailsProgress);
        outState.putBoolean(STATE_USERNAME_SELECTION_WANTED_NEXT, usernameSelectionWantedNext);
        outState.putParcelable(STATE_DELETE_ACTION_DATA, bulkResourceActionData);
        outState.putLong(STATE_USER_GUID, userGuid);
        outState.putInt(STATE_ALBUMS_PER_ROW, albumsPerRow);
        if (viewAdapter != null) {
            BundleUtils.putLongHashSet(outState, STATE_SELECTED_ITEMS, viewAdapter.getSelectedItemIds());
        }

        if (BuildConfig.DEBUG) {
            BundleUtils.logSize("ViewAlbumFragment", outState);
        }
    }

    /**
     * is a reload occuring or about to right now
     *
     * @return
     */
    protected boolean isAlbumDataLoading() {
        return loadingMessageIds.size() > 0;
    }

    protected AlbumItemRecyclerViewAdapterPreferences updateViewPrefs() {

        boolean showAlbumThumbnailsZoomed = AlbumViewPreferences.isShowAlbumThumbnailsZoomed(prefs, getContext());

        boolean showResourceNames = AlbumViewPreferences.isShowResourceNames(prefs, getContext());

        int recentlyAlteredThresholdAge = AlbumViewPreferences.getRecentlyAlteredMaxAgeMillis(prefs, getContext());
        Date recentlyAlteredThresholdDate = new Date(System.currentTimeMillis() - recentlyAlteredThresholdAge);

        if (viewPrefs == null) {
            viewPrefs = new AlbumItemRecyclerViewAdapterPreferences();
            viewPrefs.selectable(true, false); // set multi select mode enabled (side effect is it enables selection
            viewPrefs.setAllowItemSelection(false); // prevent selection until a long click enables it.
        }

        String preferredThumbnailSize = AlbumViewPreferences.getPreferredResourceThumbnailSize(prefs,getContext());

        String preferredAlbumThumbnailSize = AlbumViewPreferences.getPreferredAlbumThumbnailSize(prefs, getContext());

        viewPrefs.withPreferredThumbnailSize(preferredThumbnailSize);
        viewPrefs.withPreferredAlbumThumbnailSize(preferredAlbumThumbnailSize);
        viewPrefs.withShowingAlbumNames(showResourceNames);
        viewPrefs.withShowAlbumThumbnailsZoomed(showAlbumThumbnailsZoomed);
        viewPrefs.withAlbumWidth(getScreenWidth(requireActivity()) / albumsPerRow);
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
        cacheViewComponentReferences(view);

        if (!reopening) {
            populateViewFromModelEtc(view, savedInstanceState);
        } else {
            descriptionDropdownButton.setVisibility(GONE);
            Basket basket = getBasket();
            initialiseBasketView(view);
            setupBottomSheet(bottomSheet);
            setupBulkActionsControls(basket);
            updateBasketDisplay(basket);
            retryActionButton.hide();
        }
    }

    private void populateViewFromModelEtc(@NonNull View view, @Nullable Bundle savedInstanceState) {

        if (!PiwigoSessionDetails.isFullyLoggedIn(ConnectionPreferences.getActiveProfile())) {
            // force a reload of the gallery if the session has been destroyed.
            galleryIsDirty = true;
            // reset albums per row to get it recalculated on next use
            albumsPerRow = 0;
        } else if (savedInstanceState != null) {
            //restore saved state
            viewPrefs = new AlbumItemRecyclerViewAdapterPreferences();
            viewPrefs.loadFromBundle(savedInstanceState);
            editingItemDetails = savedInstanceState.getBoolean(STATE_EDITING_ITEM_DETAILS);
            informationShowing = savedInstanceState.getBoolean(STATE_INFORMATION_SHOWING);
            currentUsers = savedInstanceState.getLongArray(STATE_CURRENT_USERS);
            currentGroups = savedInstanceState.getLongArray(STATE_CURRENT_GROUPS);
            // if galleryIsDirty then this fragment was updated while on the backstack - need to refresh it.
            userGuid = savedInstanceState.getLong(STATE_USER_GUID);
            galleryIsDirty = galleryIsDirty || PiwigoSessionDetails.getUserGuid(ConnectionPreferences.getActiveProfile()) != userGuid;
            galleryIsDirty = galleryIsDirty || savedInstanceState.getBoolean(STATE_GALLERY_DIRTY);
            loadingMessageIds.clear();
            BundleUtils.readMap(savedInstanceState, STATE_GALLERY_ACTIVE_LOAD_THREADS, loadingMessageIds, null);
            Set<Long> messageIdsExpired = PiwigoResponseBufferingHandler.getDefault().getUnknownMessageIds(loadingMessageIds.keySet());
            if (messageIdsExpired.size() > 0) {
                for (Long messageId : messageIdsExpired) {
                    loadingMessageIds.remove(messageId);
                }
            }
            CollectionUtils.addToCollectionNullSafe(itemsToLoad, savedInstanceState.getStringArrayList(STATE_GALLERY_LOADS_TO_RETRY));
            movedResourceParentUpdateRequired = savedInstanceState.getBoolean(STATE_MOVED_RESOURCE_PARENT_UPDATE_NEEDED);
            updateAlbumDetailsProgress = savedInstanceState.getInt(STATE_UPDATE_ALBUM_DETAILS_PROGRESS);
            usernameSelectionWantedNext = savedInstanceState.getBoolean(STATE_USERNAME_SELECTION_WANTED_NEXT);
            if (bulkResourceActionData != null && bulkResourceActionData.isEmpty()) {
                bulkResourceActionData = null;
            } else {
                bulkResourceActionData = savedInstanceState.getParcelable(STATE_DELETE_ACTION_DATA);
            }
            albumsPerRow = savedInstanceState.getInt(STATE_ALBUMS_PER_ROW);
        } else {
            // fresh view of the root of the gallery - reset the admin list
            if (galleryModel.getContainerDetails().isRoot()) {
                albumAdminList = null;
            }
        }

        updateViewPrefs();

        // notify user once and only once per app session
        getUiHelper().doOnce("currentPreferredResourceThumbnailSize", viewPrefs.getPreferredThumbnailSize(), new Runnable() {
            @Override
            public void run() {
                getUiHelper().showDetailedMsg(R.string.alert_information, getString(R.string.alert_message_showing_image_thumbnails_of_size, viewPrefs.getPreferredThumbnailSize()));
            }
        });
        getUiHelper().doOnce("currentPreferredAlbumThumbnailSize", viewPrefs.getPreferredAlbumThumbnailSize(), new Runnable() {
            @Override
            public void run() {
                getUiHelper().showDetailedMsg(R.string.alert_information, getString(R.string.alert_message_showing_album_thumbnails_of_size, viewPrefs.getPreferredAlbumThumbnailSize()));
            }
        });
        userGuid = PiwigoSessionDetails.getUserGuid(ConnectionPreferences.getActiveProfile());


        PiwigoAlbumUpdatedEvent albumUpdatedEvent = EventBus.getDefault().removeStickyEvent(PiwigoAlbumUpdatedEvent.class);
        if (albumUpdatedEvent != null && albumUpdatedEvent.getUpdatedAlbum() instanceof PiwigoAlbum) {
            // retrieved this from the slideshow.
            PiwigoAlbum eventModel = (PiwigoAlbum) albumUpdatedEvent.getUpdatedAlbum();
            if (eventModel.getId() == galleryModel.getId()) {
                galleryModel = eventModel;
            }
        }

        if (galleryModel != null) {
            galleryModel.setAlbumSortOrder(AlbumViewPreferences.getAlbumChildAlbumsSortOrder(prefs, getContext()));
        } else {
            Crashlytics.log(Log.WARN, getTag(), "Attempt to set album sort order but album model is still null");
        }

        retryActionButton.hide();
        retryActionButton.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                onReloadAlbum();
            }
        });

        emptyGalleryLabel.setText(R.string.gallery_empty_text);
        if (!galleryIsDirty) {
            emptyGalleryLabel.setVisibility(galleryModel.getItemCount() == 0 ? VISIBLE : GONE);
        }

        initialiseBasketView(view);

        if(galleryModel != null) {
            EventBus.getDefault().post(new AlbumSelectedEvent(galleryModel.getContainerDetails()));
        }

        AdView adView = view.findViewById(R.id.gallery_adView);
        if (AdsManager.getInstance().shouldShowAdverts()) {
            adView.setVisibility(VISIBLE);
            new AdsManager.MyBannerAdListener(adView);
        } else {
            adView.setVisibility(GONE);
        }

        setGalleryHeadings();


        setupBottomSheet(bottomSheet);

//        viewInOrientation = getResources().getConfiguration().orientation;


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

        galleryListView.setLayoutManager(gridLayoutMan);

        viewAdapterListener = new AlbumViewAdapterListener();

        viewAdapter = new AlbumItemRecyclerViewAdapter(getContext(), PiwigoAlbumModel.class, galleryModel, viewAdapterListener, viewPrefs);

        if (savedInstanceState != null) {
            viewAdapter.setInitiallySelectedItems(new HashSet<Long>());
            viewAdapter.setSelectedItems(BundleUtils.getLongHashSet(savedInstanceState, STATE_SELECTED_ITEMS));
        }

        Basket basket = getBasket();

        setupBulkActionsControls(basket);

        galleryListView.setAdapter(viewAdapter);


        galleryListViewScrollListener = new AlbumScrollListener(gridLayoutMan);
        galleryListViewScrollListener.configure(galleryModel.getPagesLoaded(), galleryModel.getItemCount());
        galleryListView.addOnScrollListener(galleryListViewScrollListener);

        //display bottom sheet if needed
        updateInformationShowingStatus();

        if (isSessionDetailsChanged()) {

            if(!PiwigoSessionDetails.isFullyLoggedIn(ConnectionPreferences.getActiveProfile()) || (isSessionDetailsChanged() && !isServerConnectionChanged())){
                //trigger total screen refresh. Any errors will result in screen being closed.
                galleryIsDirty = true;
                reloadAlbumContent();
            } else {
                if (galleryModel.getContainerDetails().isRoot()) {
                    // root of gallery can always be refreshed successfully.
                    galleryIsDirty = true;
                } else {
                    // immediately leave this screen.
                    getParentFragmentManager().popBackStack();
                }
            }
        }
    }

    protected void cacheViewComponentReferences(@NonNull View view) {

        bottomSheet = view.findViewById(R.id.slidingDetailBottomSheet);

        // store references and initialise anything vital to the page (and used when loading data for example)
        retryActionButton = view.findViewById(R.id.gallery_retryAction_actionButton);

        emptyGalleryLabel = view.findViewById(R.id.album_empty_content);

        bulkActionsContainer = view.findViewById(R.id.gallery_actions_bulk_container);

        galleryNameHeader = view.findViewById(R.id.gallery_details_name_header);
        galleryNameHeader.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                bottomSheet.openLayer(true);
            }
        });

        galleryDescriptionHeader = view.findViewById(R.id.gallery_details_description_header);
        descriptionDropdownButton = view.findViewById(R.id.gallery_details_description_dropdown_button);

        // Set the adapter
        galleryListView = view.findViewById(R.id.gallery_list);
    }

    private HashSet<ResourceItem> getSelectedItemsNoException() {
        try {
            return getSelectedItems();
        } catch (IllegalStateException e) {
            return new HashSet<>(0);
        }
    }

    private boolean showBulkDeleteAction(Basket basket) {
        return PiwigoSessionDetails.isAdminUser(ConnectionPreferences.getActiveProfile()) && viewAdapter.isItemSelectionAllowed() && (getSelectedItemsNoException().size() > 0 && basket.isEmpty());
    }

    private boolean showBulkPermissionsAction(Basket basket) {
        return PiwigoSessionDetails.isAdminUser(ConnectionPreferences.getActiveProfile()) && viewAdapter.isItemSelectionAllowed() && (getSelectedItemsNoException().size() > 0 && basket.isEmpty());
    }

    private boolean showBulkCopyAction(Basket basket) {
        return PiwigoSessionDetails.isAdminUser(ConnectionPreferences.getActiveProfile()) && viewAdapter.isItemSelectionAllowed() && ((getSelectedItemsNoException().size() > 0 && basket.isEmpty()) || (galleryModel.getContainerDetails().getId() == basket.getContentParentId() && basket.getAction() == Basket.ACTION_COPY));
    }

    private boolean showBulkCutAction(Basket basket) {
        return PiwigoSessionDetails.isAdminUser(ConnectionPreferences.getActiveProfile()) && viewAdapter.isItemSelectionAllowed() && ((getSelectedItemsNoException().size() > 0 && basket.isEmpty()) || (galleryModel.getContainerDetails().getId() == basket.getContentParentId() && basket.getAction() == Basket.ACTION_CUT));
    }

    private boolean showBulkPasteAction(Basket basket) {
        return PiwigoSessionDetails.isAdminUser(ConnectionPreferences.getActiveProfile()) && !viewAdapter.isItemSelectionAllowed() && basket.getItemCount() > 0 && galleryModel.getContainerDetails().getId() != CategoryItem.ROOT_ALBUM.getId() && galleryModel.getContainerDetails().getId() != basket.getContentParentId();
    }

    private boolean showBulkActionsContainer(Basket basket) {
        return viewAdapter != null && (viewAdapter.isItemSelectionAllowed() || !getBasket().isEmpty());
    }

    protected void setupBulkActionsControls(Basket basket) {

        bulkActionsContainer.setVisibility(showBulkActionsContainer(basket) ? VISIBLE : GONE);

        bulkActionButtonPermissions = bulkActionsContainer.findViewById(R.id.gallery_action_permissions_bulk);
        bulkActionButtonTag = bulkActionsContainer.findViewById(R.id.gallery_action_tag_bulk);
        bulkActionButtonDelete = bulkActionsContainer.findViewById(R.id.gallery_action_delete_bulk);
        bulkActionButtonCopy = bulkActionsContainer.findViewById(R.id.gallery_action_copy_bulk);
        bulkActionButtonCut = bulkActionsContainer.findViewById(R.id.gallery_action_cut_bulk);
        bulkActionButtonPaste = bulkActionsContainer.findViewById(R.id.gallery_action_paste_bulk);

        bulkActionButtonPermissions.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getActionMasked() == MotionEvent.ACTION_UP) {
                    onBulkActionPermissionsButtonPressed();
                }
                return true; // consume the event
            }
        });
        bulkActionButtonDelete.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getActionMasked() == MotionEvent.ACTION_UP) {
                    onBulkActionDeleteButtonPressed();
                }
                return true; // consume the event
            }
        });
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
                    String message = getString(msgPatternId, basket.getItemCount(), galleryModel.getContainerDetails().getName());
                    getUiHelper().showOrQueueDialogQuestion(R.string.alert_confirm_title, message, R.string.button_no, R.string.button_yes, new BasketAction(getUiHelper()));
                }
                return true; // consume the event
            }
        });

        if (!reopening && showBulkPermissionsAction(basket)) {
            bulkActionButtonPermissions.show();
        } else {
            bulkActionButtonPermissions.hide();
        }


        bulkActionButtonTag.hide();

        if (!isAlbumDataLoading()) {
            // if gallery is dirty, then the album contents are being reloaded and won't yet be available. This method is recalled once it is

            if (!reopening && showBulkDeleteAction(basket)) {
                bulkActionButtonDelete.show();
            } else {
                bulkActionButtonDelete.hide();
            }
            if (!reopening && showBulkCopyAction(basket)) {
                bulkActionButtonCopy.show();
            } else {
                bulkActionButtonCopy.hide();
            }
            if (!reopening && showBulkCutAction(basket)) {
                bulkActionButtonCut.show();
            } else {
                bulkActionButtonCut.hide();
            }
            if (!reopening && showBulkPasteAction(basket)) {
                bulkActionButtonPaste.show();
            } else {
                bulkActionButtonPaste.hide();
            }
        }
    }

    private void loadAdminListOfAlbums() {
        long loadingMessageId = addNonBlockingActiveServiceCall(R.string.progress_loading_album_content, new AlbumGetSubAlbumsAdminResponseHandler());
        loadingMessageIds.put(loadingMessageId, "AL");
    }

    private void onUpdateImagePermissionsButtonClick(HashSet<Long> imageIds, HashSet<ResourceItem> selectedItems) {
        BulkResourceActionData bulkActionData = new BulkResourceActionData(imageIds, selectedItems, BulkResourceActionData.ACTION_UPDATE_PERMISSIONS);
        this.bulkResourceActionData = bulkActionData;
        onUpdateImagePermissions(bulkActionData);
    }

    private void onUpdateImagePermissions(BulkResourceActionData bulkActionData) {
        final HashSet<ResourceItem> resourcesReadyForAction = new HashSet<>();
        if (bulkResourceActionData.isResourceInfoAvailable()) {
            for (ResourceItem item : bulkResourceActionData.getSelectedItems()) {
                if (item.getPrivacyLevel() >= 0) {
                    resourcesReadyForAction.add(item);
                }
            }
        } else {
            bulkResourceActionData.getResourcesInfoIfNeeded(this);
            return;
        }
        if (resourcesReadyForAction.size() > 0) {
            getUiHelper().showOrQueueDialogQuestion(R.string.alert_bulk_image_permissions_title, getString(R.string.alert_bulk_image_permissions_message, resourcesReadyForAction.size()), R.layout.layout_bulk_image_permissions, R.string.button_cancel, Integer.MIN_VALUE, R.string.button_ok, new BulkImagePermissionsListener(bulkResourceActionData.getSelectedItemIds(), getUiHelper()));
        }
    }

    protected HashSet<ResourceItem> getSelectedItems() {
        return viewAdapter.getSelectedItems();
    }

    private void updateResourcePermissions(final HashSet<Long> imageIds, final byte privacyLevel) {

        // copy this to avoid remote possibility of a concurrent modification exception.
        HashSet<Long> itemIds = new HashSet<>(imageIds);
        for (Long imageId : itemIds) {
            ResourceItem item = (ResourceItem) getGalleryModel().getItemById(imageId);
            if (item.getPrivacyLevel() != privacyLevel) {
                // update item on server
                getUiHelper().getParent().getBulkResourceActionData().trackMessageId(addActiveServiceCall(R.string.progress_resource_details_updating, new ImageSetPrivacyLevelResponseHandler(item, privacyLevel)));
            }
        }
    }

    private void onDeleteResources(final BulkResourceActionData deleteActionData) {
        final HashSet<ResourceItem> sharedResources = new HashSet<>();
        if (deleteActionData.isResourceInfoAvailable()) {
            //TODO currently, this won't work. No piwigo support
            for (ResourceItem item : deleteActionData.getSelectedItems()) {
                if (item.getLinkedAlbums().size() > 1) {
                    sharedResources.add(item);
                }
            }
        } else {
            deleteActionData.getResourcesInfoIfNeeded(this);
            return;
        }
        if (sharedResources.size() > 0) {
            String msg = getString(R.string.alert_confirm_delete_items_from_server_or_just_unlink_them_from_this_album_pattern, sharedResources.size());
            getUiHelper().showOrQueueDialogQuestion(R.string.alert_confirm_title, msg, Integer.MIN_VALUE, R.string.button_unlink, R.string.button_cancel, R.string.button_delete, new DeleteSharedResourcesAction(getUiHelper(), sharedResources));
        } else {
            deleteResourcesFromServerForever(getUiHelper(), deleteActionData.getSelectedItemIds(), deleteActionData.getSelectedItems());
        }

    }

    private void onBulkActionPermissionsButtonPressed() {
        boolean bulkActionsAllowed = PiwigoSessionDetails.isAdminUser(ConnectionPreferences.getActiveProfile()) && !isAppInReadOnlyMode();
        if (bulkActionsAllowed) {
            HashSet<Long> selectedItemIds = viewAdapter.getSelectedItemIds();
            if (selectedItemIds.size() > 0) {
                onUpdateImagePermissionsButtonClick(selectedItemIds, viewAdapter.getSelectedItems());
            }
        }
    }

    private void onBulkActionDeleteButtonPressed() {
        boolean bulkActionsAllowed = PiwigoSessionDetails.isAdminUser(ConnectionPreferences.getActiveProfile()) && !isAppInReadOnlyMode();
        if (bulkActionsAllowed) {
            HashSet<Long> selectedItemIds = viewAdapter.getSelectedItemIds();
            if (bulkResourceActionData != null && selectedItemIds.equals(bulkResourceActionData.getSelectedItemIds())) {
                //continue with previous action
                onDeleteResources(bulkResourceActionData);
            } else if (selectedItemIds.size() > 0) {
                HashSet<ResourceItem> selectedItems = viewAdapter.getSelectedItems();
                BulkResourceActionData deleteActionData = new BulkResourceActionData(selectedItemIds, selectedItems, BulkResourceActionData.ACTION_DELETE);
                if (!deleteActionData.isResourceInfoAvailable()) {
                    this.bulkResourceActionData = deleteActionData;
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

        AppCompatImageView clearButton = basketView.findViewById(R.id.basket_clear_button);
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

        if (viewAdapter != null && viewAdapter.isMultiSelectionAllowed() && isPreventItemSelection()) {
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

        if (!isAlbumDataLoading() && viewAdapter != null) {

            if (showBulkDeleteAction(basket)) {
                bulkActionButtonDelete.show();
            } else {
                bulkActionButtonDelete.hide();
            }
            if (showBulkCopyAction(basket)) {
                bulkActionButtonCopy.show();
            } else {
                bulkActionButtonCopy.hide();
            }
            if (showBulkCutAction(basket)) {
                bulkActionButtonCut.show();
            } else {
                bulkActionButtonCut.hide();
            }
            if (showBulkPasteAction(basket)) {
                bulkActionButtonPaste.show();
            } else {
                bulkActionButtonPaste.hide();
            }
            if (showBulkPermissionsAction(basket)) {
                bulkActionButtonPermissions.show();
            } else {
                bulkActionButtonPermissions.hide();
            }

        }

    }

    private void loadAlbumResourcesPage(int pageToLoad) {
        synchronized (loadingMessageIds) {
            galleryModel.acquirePageLoadLock();
            try {
                int pageSize = AlbumViewPreferences.getResourceRequestPageSize(prefs, getContext());
                int pageToActuallyLoad = getPageToActuallyLoad(pageToLoad, pageSize);
                if (pageToActuallyLoad < 0) {
                    // the sort order is inverted so we know for a fact this page is invalid.
                    return;
                }

                if (galleryModel.isPageLoadedOrBeingLoaded(pageToActuallyLoad)) {
                    return;
                }

                String sortOrder = AlbumViewPreferences.getResourceSortOrder(prefs, getContext());
                Set<String> multimediaExtensionList = AlbumViewPreferences.getKnownMultimediaExtensions(prefs, getContext());


                long loadingMessageId = addNonBlockingActiveServiceCall(R.string.progress_loading_album_content, new ImagesGetResponseHandler(galleryModel.getContainerDetails(), sortOrder, pageToActuallyLoad, pageSize, multimediaExtensionList));
                galleryModel.recordPageBeingLoaded(loadingMessageId, pageToActuallyLoad);
                loadingMessageIds.put(loadingMessageId, String.valueOf(pageToLoad));
            } finally {
                galleryModel.releasePageLoadLock();
            }
        }
    }

    private int getPageToActuallyLoad(int pageRequested, int pageSize) {
        boolean invertSortOrder = AlbumViewPreferences.getResourceSortOrderInverted(prefs, getContext());

        galleryModel.setRetrieveItemsInReverseOrder(invertSortOrder);
        int pageToActuallyLoad = pageRequested;
        if (invertSortOrder) {
            int pagesOfPhotos = galleryModel.getContainerDetails().getPagesOfPhotos(pageSize);
            pageToActuallyLoad = pagesOfPhotos - pageRequested;
        }
        return pageToActuallyLoad;
    }

    private void onAlbumDeleteRequest(final CategoryItem album) {
        String msg = getString(R.string.alert_confirm_really_delete_album_from_server_pattern, album.getName());
        getUiHelper().showOrQueueDialogQuestion(R.string.alert_confirm_title, msg, R.string.button_no, R.string.button_yes, new DeleteAlbumAction(getUiHelper(), album));
    }

    private void loadAlbumSubCategories() {
        synchronized (loadingMessageIds) {
            ConnectionPreferences.ProfilePreferences connPrefs = ConnectionPreferences.getActiveProfile();
            PiwigoSessionDetails sessionDetails = PiwigoSessionDetails.getInstance(connPrefs);
//            if(sessionDetails != null && sessionDetails.isUseCommunityPlugin() && !sessionDetails.isGuest()) {
//                connPrefs = ConnectionPreferences.getActiveProfile().asGuest();
//            }
            loadingMessageIds.put(addNonBlockingActiveServiceCall(R.string.progress_loading_album_content, new AlbumGetSubAlbumsResponseHandler(galleryModel.getContainerDetails(), viewPrefs.getPreferredAlbumThumbnailSize(), false)), "C");
        }
    }

    private BulkResourceActionData getBulkResourceActionData() {
        return bulkResourceActionData;
    }

    private void setupEditFields(View editFields) {
        galleryNameView = editFields.findViewById(R.id.gallery_details_name);
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
                    addActiveServiceCall(R.string.progress_loading_group_details, new UsernamesGetListResponseHandler(selectedGroupIds, 0, 100));
                }
            }
        });

        galleryPrivacyStatusField = editFields.findViewById(R.id.gallery_details_status);
        privacyStatusFieldListener = new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (!reopening && isChecked && !galleryModel.getContainerDetails().isPrivate()) {
                    // when reopening, this will be called once the gallery model has been loaded.
                    loadAlbumPermissionsIfNeeded();
                }
                allowedGroupsFieldLabel.setVisibility(isChecked ? VISIBLE : INVISIBLE);
                allowedGroupsField.setVisibility(isChecked ? VISIBLE : INVISIBLE);
                allowedUsersFieldLabel.setVisibility(isChecked ? VISIBLE : INVISIBLE);
                allowedUsersField.setVisibility(isChecked ? VISIBLE : INVISIBLE);
            }
        };
        galleryPrivacyStatusField.setOnCheckedChangeListener(privacyStatusFieldListener);
    }

    private void loadAlbumPermissionsIfNeeded() {
        if (galleryModel != null
                && PiwigoSessionDetails.isAdminUser(ConnectionPreferences.getActiveProfile())
                && !galleryModel.getContainerDetails().isRoot()
                && !galleryModel.getContainerDetails().isPermissionsLoaded()) {
            // never want to load permissions for the root album (it's not legal to call this service with category id 0).
            addActiveServiceCall(R.string.progress_loading_album_permissions, new AlbumGetPermissionsResponseHandler(galleryModel.getContainerDetails()));
        }
    }

    @Override
    protected String buildPageHeading() {
        if (galleryModel != null) {
            CategoryItem catItem = galleryModel.getContainerDetails();
            if (catItem.isRoot()) {
                PiwigoSessionDetails sessionDetails = PiwigoSessionDetails.getInstance(ConnectionPreferences.getActiveProfile());
                if (sessionDetails != null) {
                    ServerConfig serverConfig = sessionDetails.getServerConfig();
                    if (serverConfig != null) {
                        return serverConfig.getGalleryTitle();
                    }
                }
                return getString(R.string.album_title_home);
            } else {
                String currentAlbumName = "... / " + catItem.getName();
                return currentAlbumName;
            }
        } else if (reopening) {
            SharedPreferences resumePrefs = getUiHelper().getResumePrefs();
            if (AbstractViewAlbumFragment.RESUME_ACTION.equals(resumePrefs.getString("reopenAction", null))) {
                return resumePrefs.getString("reopenAlbumName", "");
            }
        }
        return "";
    }

    @Override
    public void onResume() {
        super.onResume();

        if (galleryListView == null) {
            //Resumed, but fragment initialisation cancelled for whatever reason.
            return;
        }

        if (!reopening) {
            populateViewFromModelEtcOnResume();
        }
    }

    protected void populateViewFromModelEtcOnResume() {
        ConnectionPreferences.ProfilePreferences activeProfile = ConnectionPreferences.getActiveProfile();
        String profileId = activeProfile.getProfileId(getPrefs(), getContext());


        if (galleryModel == null) {
            if (getArguments() != null) {
                loadModelFromArguments(); // restoring view.
            } else {
                Crashlytics.log(Log.ERROR, TAG, "Gallery model is null, but there are no arguments available from which to load one - unable to populate view");
                return;
            }
        }


        // why would this ever be null?
        List<Long> fullAlbumPath = galleryModel.getContainerDetails().getFullPath();
        SharedPreferences resumePrefs = getUiHelper().getResumePrefs();
        SharedPreferences.Editor editor = resumePrefs.edit();
        editor.clear();
        editor.putString("reopenAction", RESUME_ACTION);
        editor.putString("reopenAlbumPath", CollectionUtils.toCsvList(fullAlbumPath));
        editor.putString("reopenProfileId", profileId);
        editor.putString("reopenAlbumName", buildPageHeading());
        editor.commit();


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

        getUiHelper().showUserHint(TAG, 1, R.string.hint_album_view);
    }

    @Subscribe(threadMode = ThreadMode.MAIN_ORDERED)
    public void onEvent(PiwigoLoginSuccessEvent event) {
        if (!reopening) {
            loadAlbumPermissionsIfNeeded();
            displayControlsBasedOnSessionState();
            setEditItemDetailsControlsStatus();
            updatePageTitle();
        }
    }

    private void reloadAlbumContent() {

        if (galleryIsDirty) {
            emptyGalleryLabel.setVisibility(GONE);
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
            loadAlbumPermissionsIfNeeded();
            if (PiwigoSessionDetails.isAdminUser(ConnectionPreferences.getActiveProfile())) {
                boolean loadAdminList = false;
                if (albumAdminList == null) {
                    loadAdminList = true;
                } else {
                    if (galleryModel.getContainerDetails().isRoot()) {
                        adminCategories = albumAdminList.getAlbums();
                    } else {
                        CategoryItem adminCopyOfAlbum = null;
                        try {
                            adminCopyOfAlbum = albumAdminList.getAlbum(galleryModel.getContainerDetails());
                        } catch (IllegalStateException e) {
                            Crashlytics.logException(e);
                            Crashlytics.log(Log.ERROR, getTag(), String.format("current container details (%1$s) not in admin list", galleryModel.getContainerDetails()));
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

    private void onResourceMoved(BaseImageUpdateInfoResponseHandler.PiwigoUpdateResourceInfoResponse response) {
        Crashlytics.log(String.format("deleting 1 album items from the UI display after moving it to different album"));
        Basket basket = getBasket();
        if (basket == null) {
            Crashlytics.log("Basket is null when expecting to handle onResourceMoved event");
            return;
        }
        CategoryItem movedParent = basket.getContentParent();
        boolean itemRemoved = basket.removeItem(response.getPiwigoResource());
        if (itemRemoved) {
            movedParent.reducePhotoCount();
            if (movedParent.getRepresentativePictureId() != null && response.getPiwigoResource().getId() == movedParent.getRepresentativePictureId()) {
                if (movedParent.getPhotoCount() > 0) {
                    movedResourceParentUpdateRequired = true;
                }
            }
            if (basket.getItemCount() == 0 && movedResourceParentUpdateRequired) {
                movedResourceParentUpdateRequired = false;
                addActiveServiceCall(R.string.progress_move_resources, new AlbumThumbnailUpdatedResponseHandler(movedParent.getId(), movedParent.getParentId(), null));
            }

            updateBasketDisplay(basket);

            // Now ensure any parents are also updated when next shown
            notifyAllParentAlbumsOfContentChange();

            //we've altered the album content (now update this album view to reflect the server content)
            galleryIsDirty = true;
            reloadAlbumContent();
        } else {
            Crashlytics.log(Log.ERROR, getTag(), "processed onResourceMoved but basket (" + basket.getItemCount() + " items) does not contain resource");
        }
    }

    private void updateAlbumDetails() {
        switch (updateAlbumDetailsProgress) {
            case UPDATE_IN_PROGRESS:
            case UPDATE_NOT_RUNNING:
                updateAlbumDetailsProgress = UPDATE_IN_PROGRESS;
                addActiveServiceCall(R.string.gallery_details_updating_progress_title, new AlbumUpdateInfoResponseHandler(galleryModel.getContainerDetails()));
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

    private void updateInformationShowingStatus() {
        if (informationShowing) {
            if (currentGroups == null) {
                // haven't yet loaded the existing permissions - do this now.
                privacyStatusFieldListener.onCheckedChanged(galleryPrivacyStatusField, galleryPrivacyStatusField.isChecked());
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

        boolean visibleBottomSheet = PiwigoSessionDetails.isAdminUser(ConnectionPreferences.getActiveProfile()) || (galleryModel != null && galleryModel.getContainerDetails() != CategoryItem.ROOT_ALBUM);
        bottomSheet.setVisibility(visibleBottomSheet ? View.VISIBLE : View.GONE);

        int editFieldVisibility = VISIBLE;
        if (galleryModel == null || galleryModel.getContainerDetails().isRoot()) {
            editFieldVisibility = GONE;
        }

        View editFields = bottomSheet.findViewById(R.id.gallery_details_edit_fields);
        editFields.setVisibility(editFieldVisibility);

        // always setting them up eliminates the chance they might be null.
        setupBottomSheetButtons(bottomSheet, editFieldVisibility);
        setupEditFields(editFields);
        if (galleryModel != null && !galleryModel.getContainerDetails().isRoot()) {
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
                allowedUsersField.setText(getString(R.string.click_to_view_pattern, currentUsers.length));
                int msgId = R.string.alert_information_own_user_readded_to_permissions_list;
                getUiHelper().showOrQueueDialogMessage(R.string.alert_information, getString(msgId), R.string.button_ok, false, new AddingAlbumPermissionsAction(getUiHelper()));
                return true;
            }

            if (newlyAddedGroups.size() > 0 || newlyAddedUsers.size() > 0) {

                if (galleryModel.getContainerDetails().getSubCategories() > 0) {
                    final CategoryItem currentCategoryDetails = galleryModel.getContainerDetails();

                    if (newlyAddedUsers.contains(currentLoggedInUserId)) {
                        //we're having to force add this user explicitly therefore for safety we need to apply the change recursively
                        String msg = getString(R.string.alert_information_add_album_permissions_recursively_pattern, galleryModel.getContainerDetails().getSubCategories());
                        getUiHelper().showOrQueueDialogMessage(R.string.alert_information, msg, R.string.button_ok, false, new AddingChildPermissionsAction(getUiHelper(), newlyAddedGroups, newlyAddedUsers));
                    } else {

                        String msg = getString(R.string.alert_confirm_add_album_permissions_recursively_pattern, newlyAddedGroups.size(), newlyAddedUsers.size(), galleryModel.getContainerDetails().getSubCategories());
                        getUiHelper().showOrQueueDialogQuestion(R.string.alert_confirm_title, msg, R.string.button_no, R.string.button_yes, new AddAccessToAlbumAction(getUiHelper(), newlyAddedGroups, newlyAddedUsers));
                    }
                } else {
                    // no need to be recursive as this album is a leaf node.
                    addActiveServiceCall(R.string.gallery_details_updating_progress_title, new AlbumAddPermissionsResponseHandler(galleryModel.getContainerDetails(), newlyAddedGroups, newlyAddedUsers, false));
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
                String message = getString(R.string.alert_confirm_really_remove_album_permissions_pattern, newlyRemovedGroups.size(), newlyRemovedUsers.size());
                getUiHelper().showOrQueueDialogQuestion(R.string.alert_confirm_title, message, R.string.button_no, R.string.button_yes, new RemoveAccessToAlbumAction(getUiHelper(), newlyRemovedGroups, newlyRemovedUsers));
            } else {
                addActiveServiceCall(R.string.gallery_details_updating_progress_title, new AlbumRemovePermissionsResponseHandler(galleryModel.getContainerDetails(), newlyRemovedGroups, newlyRemovedUsers));
            }

            return true;
        } else {
            return false;
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
            allowedUsersField.setText(getString(R.string.click_to_view_pattern, currentUsers.length));
        }
        if (currentGroups != null) {
            allowedGroupsField.setText(getString(R.string.click_to_view_pattern, currentGroups.length));
        }
        displayControlsBasedOnSessionState();
        setEditItemDetailsControlsStatus();
    }

    private void setEditItemDetailsControlsStatus() {
        if (galleryModel == null) {
            return; // if reopening page (will be set once data loaded)
        }
        boolean visibleBottomSheet = PiwigoSessionDetails.isAdminUser(ConnectionPreferences.getActiveProfile()) || !galleryModel.getContainerDetails().isRoot();
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
            boolean showPersistenceControls = galleryModel != null && !galleryModel.getContainerDetails().isRoot();
            saveButton.setVisibility(showPersistenceControls ? VISIBLE : INVISIBLE);
            discardButton.setVisibility(showPersistenceControls ? VISIBLE : INVISIBLE);
            editButton.setVisibility(showPersistenceControls ? VISIBLE : INVISIBLE);
            deleteButton.setVisibility(showPersistenceControls ? VISIBLE : INVISIBLE);
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
        if(galleryModel == null) {
            return;
        }
        CategoryItem currentAlbum = galleryModel.getContainerDetails();
        if (currentAlbum != null && currentAlbum.getName() != null && !currentAlbum.getName().isEmpty() && !currentAlbum.isRoot()) {
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
        getUiHelper().showDetailedMsg(R.string.alert_error, getString(R.string.alert_error_unimplemented));
    }

    private void onCopyItem(CategoryItem model) {
        //TODO implement this
        getUiHelper().showDetailedMsg(R.string.alert_error, getString(R.string.alert_error_unimplemented));
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

    protected void onPiwigoUpdateResourceInfoResponse(BaseImageUpdateInfoResponseHandler.PiwigoUpdateResourceInfoResponse response) {
        if (!viewAdapterListener.handleAlbumThumbnailInfoLoaded(response.getMessageId(), response.getPiwigoResource())) {
            if (bulkResourceActionData != null && bulkResourceActionData.isTrackingMessageId(response.getMessageId())) {
                switch (bulkResourceActionData.getAction()) {
                    case BulkResourceActionData.ACTION_DELETE:
                        //currently mid delete of resources.
                        onResourceUpdateProcessed(response);
                        break;
                    case BulkResourceActionData.ACTION_UPDATE_PERMISSIONS:
                        onResourceUpdateProcessed(response);
                        break;
                    default:
                        Crashlytics.log(Log.WARN, getTag(), "unsupported bulk resource action type");
                }
            } else {
                onResourceMoved(response);
            }
        }
    }

    private void onAdminListOfAlbumsLoaded(AlbumGetSubAlbumsAdminResponseHandler.PiwigoGetSubAlbumsAdminResponse response) {
        albumAdminList = response.getAdminList();
        try {
            adminCategories = albumAdminList.getDirectChildrenOfAlbum(galleryModel.getContainerDetails().getParentageChain(), galleryModel.getContainerDetails().getId());
        } catch(IllegalStateException e) {
            Crashlytics.logException(e);
            getUiHelper().showOrQueueDialogMessage(R.string.alert_error, getString(R.string.alert_error_album_no_longer_on_server), new AlbumNoLongerExistsAction(getUiHelper()));
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

    private void onResourceUpdateProcessed(PiwigoResponseBufferingHandler.PiwigoResourceItemResponse response) {
        if (bulkResourceActionData.removeProcessedResource(response.getPiwigoResource())) {
            bulkResourceActionData = null;
            viewAdapter.clearSelectedItemIds();
            viewAdapter.toggleItemSelection();
        }
    }

    protected void onResourceInfoRetrieved(BaseImageGetInfoResponseHandler.PiwigoResourceInfoRetrievedResponse response) {
        if (bulkResourceActionData != null && bulkResourceActionData.isTrackingMessageId(response.getMessageId())) {
            this.bulkResourceActionData.updateLinkedAlbums(response.getResource());
            if (this.bulkResourceActionData.isResourceInfoAvailable()) {
                switch (bulkResourceActionData.getAction()) {
                    case BulkResourceActionData.ACTION_DELETE:
                        onDeleteResources(bulkResourceActionData);
                        break;
                    case BulkResourceActionData.ACTION_UPDATE_PERMISSIONS:
                        onUpdateImagePermissions(bulkResourceActionData);
                        break;
                }

            } else {
                // this will load the next batch of resource infos...
                bulkResourceActionData.getResourcesInfoIfNeeded(this);
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

    private void onUsernamesRetrievedForSelectedGroups(UsernamesGetListResponseHandler.PiwigoGetUsernamesListResponse response) {
        if (response.getItemsOnPage() == response.getPageSize()) {
            getUiHelper().showOrQueueDialogMessage(R.string.alert_error, getString(R.string.alert_error_too_many_users_message));
        } else {
            ArrayList<Username> usernames = response.getUsernames();
            userIdsInSelectedGroups = buildPreselectedUserIds(usernames);

            if (usernameSelectionWantedNext) {
                usernameSelectionWantedNext = false;
                HashSet<Long> preselectedUsernames = getSetFromArray(currentUsers);
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

    private void onThumbnailUpdated(AlbumThumbnailUpdatedResponseHandler.PiwigoAlbumThumbnailUpdatedResponse response) {
        if (response.getAlbumParentIdAltered() != null && response.getAlbumParentIdAltered() == galleryModel.getContainerDetails().getId()) {
            // need to refresh this gallery content.
            galleryIsDirty = true;
            reloadAlbumContent();
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN_ORDERED)
    public void onEvent(final GroupSelectionCompleteEvent groupSelectionCompleteEvent) {
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
                addActiveServiceCall(R.string.progress_loading_group_details, new UsernamesGetListResponseHandler(selectedGroupIds, 0, 100));
            }
        }
    }

    private void onResourcesDeleted(ImageDeleteResponseHandler.PiwigoDeleteImageResponse response) {
        Crashlytics.log(String.format(Locale.getDefault(), "deleting %1$d album items from the UI display", response.getDeletedItemIds().size()));
        // clear the selection
        viewAdapter.clearSelectedItemIds();
        viewAdapter.toggleItemSelection();
        // now update this album view to reflect the server content
        if (bulkResourceActionData != null && bulkResourceActionData.removeProcessedResources(response.getDeletedItemIds())) {
            bulkResourceActionData = null;
        }
        // Now ensure any parents are also updated when next shown
        notifyAllParentAlbumsOfContentChange();

        galleryIsDirty = true;
        reloadAlbumContent();

    }

    private void onReloadAlbum() {
        retryActionButton.hide();
        emptyGalleryLabel.setVisibility(GONE);
        synchronized (itemsToLoad) {
            while (itemsToLoad.size() > 0) {
                String itemToLoad = itemsToLoad.remove(0);
                switch (itemToLoad) {
                    case "C":
                        loadAlbumSubCategories();
                        break;
                    case "P":
                        loadAlbumPermissionsIfNeeded();
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

    private void onListOfAlbumsLoaded(final AlbumGetSubAlbumsResponseHandler.PiwigoGetSubAlbumsResponse response) {

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

    private void onGetResources(final BaseImagesGetResponseHandler.PiwigoGetResourcesResponse response) {
        synchronized (this) {
            boolean invertSortOrder = AlbumViewPreferences.getResourceSortOrderInverted(prefs, getContext());
            int pageSize = AlbumViewPreferences.getResourceRequestPageSize(prefs, getContext());
            int firstPage = invertSortOrder ? galleryModel.getContainerDetails().getPagesOfPhotos(pageSize) : 0;

            if (response.getPage() == firstPage && response.getResources().size() > 0) {
                if(!galleryModel.containsItem(CategoryItem.PICTURE_HEADING)) {
                    galleryModel.addItem(CategoryItem.PICTURE_HEADING);
                }
            }
            galleryModel.addItemPage(response.getPage(), response.getPageSize(), response.getResources());
            viewAdapter.notifyDataSetChanged();
        }
        emptyGalleryLabel.setVisibility(getUiHelper().getActiveServiceCallCount() == 0 && galleryModel.getItemCount() == 0 ? VISIBLE : GONE);
    }

    private void notifyAllParentAlbumsOfContentChange() {
        List<Long> parentageChain = galleryModel.getContainerDetails().getParentageChain();
        if(!parentageChain.isEmpty()) {
            for (int i = 1; i < parentageChain.size(); i++) {
                EventBus.getDefault().post(new AlbumAlteredEvent(parentageChain.get(i - 1), parentageChain.get(i)));
            }
            EventBus.getDefault().post(new AlbumAlteredEvent(parentageChain.get(parentageChain.size() - 1), galleryModel.getContainerDetails().getId()));
        }
    }

    private void onAlbumContentAltered(final ImageCopyToAlbumResponseHandler.PiwigoUpdateAlbumContentResponse response) {
        getBasket().removeItem(response.getPiwigoResource());
        notifyAllParentAlbumsOfContentChange();
        galleryIsDirty = true;
        //we've altered the album content (now update this album view to reflect the server content)
        reloadAlbumContent();
    }

    private void onAlbumInfoAltered(final AlbumUpdateInfoResponseHandler.PiwigoUpdateAlbumInfoResponse response) {
        galleryModel.setContainerDetails(response.getAlbum());
        setGalleryHeadings();
        updateAlbumPermissions();
    }

    private void onAlbumPermissionsAdded(AlbumAddPermissionsResponseHandler.PiwigoAddAlbumPermissionsResponse response) {
        HashSet<Long> newGroupsSet = SetUtils.asSet(galleryModel.getContainerDetails().getGroups());
        newGroupsSet.addAll(response.getGroupIdsAffected());
        galleryModel.getContainerDetails().setGroups(CollectionUtils.asLongArray(newGroupsSet));
        HashSet<Long> newUsersSet = SetUtils.asSet(galleryModel.getContainerDetails().getUsers());
        newUsersSet.addAll(response.getUserIdsAffected());
        galleryModel.getContainerDetails().setUsers(CollectionUtils.asLongArray(newUsersSet));

        if (!removeAlbumPermissions()) {
            onAlbumUpdateFinished();
        }
    }

    private void onAlbumPermissionsRemoved(AlbumRemovePermissionsResponseHandler.PiwigoRemoveAlbumPermissionsResponse response) {
        HashSet<Long> newGroupsSet = SetUtils.asSet(galleryModel.getContainerDetails().getGroups());
        newGroupsSet.removeAll(response.getGroupIdsAffected());
        galleryModel.getContainerDetails().setGroups(CollectionUtils.asLongArray(newGroupsSet));
        HashSet<Long> newUsersSet = SetUtils.asSet(galleryModel.getContainerDetails().getUsers());
        newUsersSet.removeAll(response.getUserIdsAffected());
        galleryModel.getContainerDetails().setUsers(CollectionUtils.asLongArray(newUsersSet));

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

    private void onAlbumStatusUpdated(AlbumSetStatusResponseHandler.PiwigoSetAlbumStatusResponse response) {
        updateAlbumPermissions();
    }

    private void onAlbumPermissionsRetrieved(AlbumGetPermissionsResponseHandler.PiwigoAlbumPermissionsRetrievedResponse response) {

        galleryModel.setContainerDetails(response.getAlbum());
        currentUsers = this.galleryModel.getContainerDetails().getUsers();
        currentGroups = this.galleryModel.getContainerDetails().getGroups();
        allowedUsersField.setText(getString(R.string.click_to_view_pattern, currentUsers.length));
        allowedGroupsField.setText(getString(R.string.click_to_view_pattern, currentGroups.length));
    }

    private void onAlbumDeleted(AlbumDeleteResponseHandler.PiwigoAlbumDeletedResponse response) {
        boolean exitFragment = false;
        CategoryItem galleryDetails = galleryModel.getContainerDetails();
        if (galleryDetails.getId() == response.getAlbumId()) {
            // we've deleted the current album.
            AlbumDeletedEvent event = new AlbumDeletedEvent(galleryDetails);
            EventBus.getDefault().post(event);
            exitFragment = true;
        } else {
            if (albumAdminList != null) {
                albumAdminList.removeAlbumById(galleryDetails, response.getAlbumId());
                adminCategories = albumAdminList.getDirectChildrenOfAlbum(galleryDetails);
            }
            galleryDetails.removeChildAlbum(response.getAlbumId()); // will return false if it was the admin copy (unlikely but do after to be sure).
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
            getParentFragmentManager().popBackStack();
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN_ORDERED)
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
                getUiHelper().showDetailedMsg(R.string.alert_information, getString(R.string.alert_information_own_user_readded_to_permissions_list));
            }
            fillUsernamesField(allowedUsersField, usernameSelectionCompleteEvent.getSelectedItems());
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN_ORDERED)
    public void onEvent(AlbumCreatedEvent event) {
        if (getUiHelper().isTrackingRequest(event.getActionId())) {
            galleryIsDirty = true;
            if (isResumed()) {
                reloadAlbumContent();
            }
        }
    }

    public CategoryItem getParentAlbum() {
        if (albumDetails == null) {
            Crashlytics.log(Log.WARN, TAG, "Attempt to reopen parent but is null");
            return null;
        }
        PiwigoAlbum nextPiwigoAlbum = ViewModelProviders.of(requireActivity()).get("" + albumDetails.getParentId(), PiwigoAlbumModel.class).getModel();
        if (nextPiwigoAlbum == null) {
            Crashlytics.log(Log.WARN, TAG, "Attempt to reopen parent but parent is not available. Returning null");
            return null;
        }
        return nextPiwigoAlbum.getContainerDetails();
    }

    private static class LoadAlbumTreeAction extends UIHelper.Action<FragmentUIHelper<AbstractViewAlbumFragment>, AbstractViewAlbumFragment, AlbumsGetFirstAvailableAlbumResponseHandler.PiwigoGetAlbumTreeResponse> {
        @Override
        public boolean onSuccess(FragmentUIHelper<AbstractViewAlbumFragment> uiHelper, AlbumsGetFirstAvailableAlbumResponseHandler.PiwigoGetAlbumTreeResponse response) {
            CategoryItem currentItem = response.getAlbumTreeRoot();

            // cache the retrieved category tree into the model
            if (currentItem != null) {
                FragmentActivity activity = uiHelper.getParent().requireActivity();
                for (Long albumId : response.getAlbumPath()) {
                    if (albumId.equals(CategoryItem.ROOT_ALBUM.getId())) {
                        continue;
                    }
                    ViewModelProviders.of(activity).get("" + currentItem.getId(), PiwigoAlbumModel.class).getPiwigoAlbum(currentItem).getValue();
                    try {
                        currentItem = currentItem.getChild(albumId);
                    } catch (IllegalStateException e) {
                        // thrown if no child albums were set. This should never occur really since it was a success but the root could now theoretically be empty.
                    }
                    if (currentItem == null) {
                        break; // were unable to load this item.
                    }
                }
            } else {
                Crashlytics.log(Log.ERROR, TAG, "album tree retrieved, but albumTreeRoot is null");
            }

            // now reopent the model
            uiHelper.getParent().onReopenModelRetrieved(response.getAlbumTreeRoot(), response.getDeepestAlbumOnDesiredPath());
            return true; // to close the progress indicator
        }

        @Override
        public boolean onFailure(FragmentUIHelper<AbstractViewAlbumFragment> uiHelper, PiwigoResponseBufferingHandler.ErrorResponse response) {
            uiHelper.getParent().onReopenModelRetrieved(CategoryItem.ROOT_ALBUM.clone(), CategoryItem.ROOT_ALBUM.clone());
            return true; // to close the progress indicator
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN_ORDERED)
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

    @Subscribe(threadMode = ThreadMode.MAIN_ORDERED)
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

    @Subscribe(threadMode = ThreadMode.MAIN_ORDERED)
    public void onEvent(final BadRequestUsingHttpToHttpsServerEvent event) {
        final ConnectionPreferences.ProfilePreferences connectionPreferences = event.getConnectionPreferences();
        String failedUriPath = event.getFailedUri().toString();
        getUiHelper().showOrQueueEnhancedDialogQuestion(R.string.alert_question_title, getString(R.string.alert_bad_request_http_to_https), failedUriPath, R.string.button_no, R.string.button_yes, new BadHttpProtocolAction(getUiHelper(), connectionPreferences));
    }

    @Subscribe(threadMode = ThreadMode.MAIN_ORDERED)
    public void onEvent(final BadRequestUsesRedirectionServerEvent event) {
        final ConnectionPreferences.ProfilePreferences connectionPreferences = event.getConnectionPreferences();
        String failedUriPath = event.getFailedUri().toString();
        getUiHelper().showOrQueueEnhancedDialogQuestion(R.string.alert_question_title, getString(R.string.alert_bad_request_follow_redirects), failedUriPath, R.string.button_no, R.string.button_yes, new BadRequestRedirectionAction(getUiHelper(), connectionPreferences));
    }

    @Subscribe(threadMode = ThreadMode.MAIN_ORDERED)
    public void onEvent(AlbumAlteredEvent albumAlteredEvent) {
        if (galleryModel != null && albumAlteredEvent.isRelevant(galleryModel.getContainerDetails().getId())) {
            galleryIsDirty = true;
            if (isResumed()) {
                //TODO Do something more fine grained to avoid refreshing the entire album view!
                reloadAlbumContent();
            }
            if (albumAlteredEvent.isCascadeToParents()) {
                notifyAllParentAlbumsOfContentChange();
            }
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN_ORDERED)
    public void onEvent(AlbumDeletedEvent event) {
        if (event.getItem().getParentId() == galleryModel.getId()) {
            int fullGalleryIdx = galleryModel.getItemIdx(event.getItem());
            // now removed from the backing gallery too.
            galleryModel.remove(fullGalleryIdx);
            // update all parent albums in case affected
            notifyAllParentAlbumsOfContentChange();
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN_ORDERED)
    public void onEvent(AlbumItemDeletedEvent event) {
        if (event.item.getParentId() == galleryModel.getId()) {
            int fullGalleryIdx = galleryModel.getItemIdx(event.item);
            // now removed from the backing gallery too.
            galleryModel.remove(fullGalleryIdx);
            // update all parent albums in case affected
            notifyAllParentAlbumsOfContentChange();
        }
    }

    protected PiwigoAlbum getGalleryModel() {
        return galleryModel;
    }

    private static final class BulkImagePermissionsListener extends UIHelper.QuestionResultAdapter<FragmentUIHelper<AbstractViewAlbumFragment>> {

        private final HashSet<Long> imageIds;
        private Spinner privacyLevelSpinner;

        BulkImagePermissionsListener(HashSet<Long> imageIds, FragmentUIHelper<AbstractViewAlbumFragment> uiHelper) {
            super(uiHelper);
            this.imageIds = imageIds;
        }

        @Override
        public void onPopulateDialogView(LinearLayout dialogView, int layoutId) {

            privacyLevelSpinner = dialogView.findViewById(R.id.privacy_level);
            // Create an ArrayAdapter using the string array and a default spinner layout
            ArrayAdapter<CharSequence> privacyLevelOptionsAdapter = ArrayAdapter.createFromResource(dialogView.getContext(),
                    R.array.privacy_levels_groups_array, android.R.layout.simple_spinner_item);
            // Specify the layout to use when the list of choices appears
            privacyLevelOptionsAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            // Apply the adapter to the spinner
            privacyLevelSpinner.setAdapter(privacyLevelOptionsAdapter);

            byte privacyLevel = -1;
            for (ResourceItem item : getUiHelper().getParent().getSelectedItems()) {
                if (privacyLevel < 0) {
                    privacyLevel = item.getPrivacyLevel();
                }
                if (privacyLevel != item.getPrivacyLevel()) {
                    // privacy levels differ
                    privacyLevel = -1;
                    break;
                }
            }
            setPrivacyLevelSpinnerOption(privacyLevelSpinner, privacyLevel);

        }

        private void setPrivacyLevelSpinnerOption(Spinner privacyLevelSpinner, int value) {

            int[] values = privacyLevelSpinner.getResources().getIntArray(R.array.privacy_levels_values_array);
            int selectionIdx = -1;
            for (int i = 0; i < values.length; i++) {
                if (values[i] == value) {
                    selectionIdx = i;
                    break;
                }
            }
            privacyLevelSpinner.setSelection(selectionIdx);
        }

        @Override
        public void onResult(AlertDialog dialog, Boolean positiveAnswer) {
            if (Boolean.TRUE.equals(positiveAnswer)) {
                byte privacyLevel = getPrivacyLevelValue();
                if (privacyLevel >= 0) {
                    getUiHelper().getParent().updateResourcePermissions(imageIds, privacyLevel);
                }
            }
        }

        private byte getPrivacyLevelValue() {
            int selectedIdx = privacyLevelSpinner.getSelectedItemPosition();
            if (selectedIdx == INVALID_POSITION) {
                return INVALID_POSITION;
            }
            return (byte) privacyLevelSpinner.getResources().getIntArray(R.array.privacy_levels_values_array)[selectedIdx];
        }
    }

    private static class AlbumNoLongerExistsAction extends UIHelper.QuestionResultAdapter<FragmentUIHelper<AbstractViewAlbumFragment>> {
        public AlbumNoLongerExistsAction(FragmentUIHelper<AbstractViewAlbumFragment> uiHelper) {
            super(uiHelper);
        }

        @Override
        public void onDismiss(AlertDialog dialog) {
            super.onDismiss(dialog);
            //TODO getFragmentManager means this fragment needs to be serialised!
            getUiHelper().getParent().getParentFragmentManager().popBackStack();
        }
    }

    private static class BasketAction extends UIHelper.QuestionResultAdapter<FragmentUIHelper<AbstractViewAlbumFragment>> {
        BasketAction(FragmentUIHelper<AbstractViewAlbumFragment> uiHelper) {
            super(uiHelper);
        }

        @Override
        public void onResult(AlertDialog dialog, Boolean positiveAnswer) {
            AbstractViewAlbumFragment fragment = getUiHelper().getParent();
            Basket basket = fragment.getBasket();
            final int basketAction = basket.getAction();
            final HashSet<ResourceItem> basketContent = basket.getContents();
            final CategoryItem containerDetails = fragment.getGalleryModel().getContainerDetails();

            if (Boolean.TRUE == positiveAnswer) {
                if (basketAction == Basket.ACTION_COPY) {
                    HashSet<ResourceItem> itemsToCopy = basketContent;
                    CategoryItem copyToAlbum = containerDetails;
                    for (ResourceItem itemToCopy : itemsToCopy) {
                        getUiHelper().addActiveServiceCall(R.string.progress_copy_resources, new ImageCopyToAlbumResponseHandler<>(itemToCopy, copyToAlbum));
                    }
                } else if (basketAction == Basket.ACTION_CUT) {
                    HashSet<ResourceItem> itemsToMove = basketContent;
                    CategoryItem moveToAlbum = containerDetails;
                    for (ResourceItem itemToMove : itemsToMove) {
                        getUiHelper().addActiveServiceCall(R.string.progress_move_resources, new ImageChangeParentAlbumHandler(itemToMove, moveToAlbum));
                    }
                }
            }
        }
    }

    private static class DeleteAlbumAction extends UIHelper.QuestionResultAdapter {
        private final CategoryItem album;

        public DeleteAlbumAction(UIHelper uiHelper, CategoryItem album) {
            super(uiHelper);
            this.album = album;
        }

        @Override
        public void onResult(AlertDialog dialog, Boolean positiveAnswer) {
            if (Boolean.TRUE == positiveAnswer) {
                if (album.getTotalPhotos() > 0 || album.getSubCategories() > 0) {
                    String msg = String.format(getContext().getString(R.string.alert_confirm_really_really_delete_album_from_server_pattern), album.getName(), album.getPhotoCount(), album.getSubCategories(), album.getTotalPhotos() - album.getPhotoCount());
                    getUiHelper().showOrQueueDialogQuestion(R.string.alert_confirm_title, msg, R.string.button_no, R.string.button_yes, new ReallyDeleteAlbumAction(getUiHelper(), album));
                } else {
                    getUiHelper().addActiveServiceCall(R.string.progress_delete_album, new AlbumDeleteResponseHandler(album.getId()));
                }
            }
        }

        private static class ReallyDeleteAlbumAction extends UIHelper.QuestionResultAdapter {
            private final CategoryItem album;

            public ReallyDeleteAlbumAction(UIHelper uiHelper, CategoryItem album) {
                super(uiHelper);
                this.album = album;
            }

            @Override
            public void onResult(AlertDialog dialog, Boolean positiveAnswer) {
                if (Boolean.TRUE == positiveAnswer) {
                    getUiHelper().addActiveServiceCall(R.string.progress_delete_album, new AlbumDeleteResponseHandler(album.getId()));
                }
            }
        }
    }

    private static class DeleteSharedResourcesAction extends UIHelper.QuestionResultAdapter<FragmentUIHelper<AbstractViewAlbumFragment>> {

        private HashSet<ResourceItem> sharedResources;

        public DeleteSharedResourcesAction(FragmentUIHelper<AbstractViewAlbumFragment> uiHelper, HashSet<ResourceItem> sharedResources) {
            super(uiHelper);
            this.sharedResources = sharedResources;
        }

        @Override
        public void onResult(AlertDialog dialog, Boolean positiveAnswer) {
            AbstractViewAlbumFragment fragment = getUiHelper().getParent();
            BulkResourceActionData deleteActionData = fragment.getBulkResourceActionData();

            if (Boolean.TRUE == positiveAnswer) {
                deleteActionData.trackMessageId(getUiHelper().addActiveServiceCall(R.string.progress_delete_resources, new ImageDeleteResponseHandler(deleteActionData.getSelectedItemIds(), deleteActionData.selectedItems)));
            } else if (Boolean.FALSE == positiveAnswer) {
                final long currentAlbumId = fragment.getGalleryModel().getContainerDetails().getId();
                HashSet<Long> itemIdsForPermanentDelete = new HashSet<>(deleteActionData.getSelectedItemIds());
                HashSet<ResourceItem> itemsForPermananentDelete = new HashSet<>(deleteActionData.getSelectedItems());
                for (ResourceItem item : sharedResources) {
                    itemIdsForPermanentDelete.remove(item.getId());
                    itemsForPermananentDelete.remove(item);
                    item.getLinkedAlbums().remove(currentAlbumId);
                    deleteActionData.trackMessageId(getUiHelper().addActiveServiceCall(R.string.progress_unlink_resources, new ImageUpdateInfoResponseHandler(item, true)));
                }
                //now we need to delete the rest.
                deleteResourcesFromServerForever(getUiHelper(), itemIdsForPermanentDelete, itemsForPermananentDelete);
            }
        }
    }

    private static class DeleteResourceForeverAction<T extends ResourceItem> extends UIHelper.QuestionResultAdapter<FragmentUIHelper<AbstractViewAlbumFragment>> {

        private HashSet<Long> selectedItemIds;
        private HashSet<T> selectedItems;

        public DeleteResourceForeverAction(FragmentUIHelper<AbstractViewAlbumFragment> uiHelper, HashSet<Long> selectedItemIds, HashSet<T> selectedItems) {
            super(uiHelper);
            this.selectedItemIds = selectedItemIds;
            this.selectedItems = selectedItems;
        }

        @Override
        public void onResult(AlertDialog dialog, Boolean positiveAnswer) {
            if (Boolean.TRUE == positiveAnswer) {
                BulkResourceActionData currentBulkResourceActionData = getUiHelper().getParent().getBulkResourceActionData();
                if (currentBulkResourceActionData != null) {
                    currentBulkResourceActionData.trackMessageId(getUiHelper().addActiveServiceCall(R.string.progress_delete_resources, new ImageDeleteResponseHandler<T>(selectedItemIds, selectedItems)));
                }
            }
        }
    }

    private static class AddAccessToAlbumAction extends UIHelper.QuestionResultAdapter<FragmentUIHelper<AbstractViewAlbumFragment>> {
        private HashSet<Long> newlyAddedGroups;
        private HashSet<Long> newlyAddedUsers;

        public AddAccessToAlbumAction(FragmentUIHelper uiHelper, HashSet<Long> newlyAddedGroups, HashSet<Long> newlyAddedUsers) {
            super(uiHelper);
            this.newlyAddedGroups = newlyAddedGroups;
            this.newlyAddedUsers = newlyAddedUsers;
        }

        @Override
        public void onResult(AlertDialog dialog, Boolean positiveAnswer) {
            AbstractViewAlbumFragment fragment = getUiHelper().getParent();
            final CategoryItem currentCategoryDetails = fragment.getGalleryModel().getContainerDetails();
            if (Boolean.TRUE == positiveAnswer) {
                getUiHelper().addActiveServiceCall(R.string.gallery_details_updating_progress_title, new AlbumAddPermissionsResponseHandler(currentCategoryDetails, newlyAddedGroups, newlyAddedUsers, true));
            } else {
                getUiHelper().addActiveServiceCall(R.string.gallery_details_updating_progress_title, new AlbumAddPermissionsResponseHandler(currentCategoryDetails, newlyAddedGroups, newlyAddedUsers, false));
            }
        }
    }

    private static class AddingChildPermissionsAction extends UIHelper.QuestionResultAdapter<FragmentUIHelper<AbstractViewAlbumFragment>> {
        private HashSet<Long> newlyAddedGroups;
        private HashSet<Long> newlyAddedUsers;

        public AddingChildPermissionsAction(FragmentUIHelper uiHelper, HashSet<Long> newlyAddedGroups, HashSet<Long> newlyAddedUsers) {
            super(uiHelper);
            this.newlyAddedGroups = newlyAddedGroups;
            this.newlyAddedUsers = newlyAddedUsers;
        }

        @Override
        public void onResult(AlertDialog dialog, Boolean positiveAnswer) {
            if (Boolean.TRUE == positiveAnswer) {
                AbstractViewAlbumFragment fragment = getUiHelper().getParent();
                final CategoryItem currentCategoryDetails = fragment.getGalleryModel().getContainerDetails();
                getUiHelper().addActiveServiceCall(R.string.gallery_details_updating_progress_title, new AlbumAddPermissionsResponseHandler(currentCategoryDetails, newlyAddedGroups, newlyAddedUsers, true));
            }
        }
    }

    private static class RemoveAccessToAlbumAction extends UIHelper.QuestionResultAdapter<FragmentUIHelper<AbstractViewAlbumFragment>> {
        private HashSet<Long> newlyRemovedGroups;
        private HashSet<Long> newlyRemovedUsers;

        public RemoveAccessToAlbumAction(FragmentUIHelper<AbstractViewAlbumFragment> uiHelper, HashSet<Long> newlyRemovedGroups, HashSet<Long> newlyRemovedUsers) {
            super(uiHelper);
            this.newlyRemovedGroups = newlyRemovedGroups;
            this.newlyRemovedUsers = newlyRemovedUsers;
        }

        @Override
        public void onResult(AlertDialog dialog, Boolean positiveAnswer) {
            if (Boolean.TRUE == positiveAnswer) {
                AbstractViewAlbumFragment fragment = getUiHelper().getParent();
                PiwigoAlbum galleryModel = fragment.getGalleryModel();
                getUiHelper().addActiveServiceCall(R.string.gallery_details_updating_progress_title, new AlbumRemovePermissionsResponseHandler(galleryModel.getContainerDetails(), newlyRemovedGroups, newlyRemovedUsers));
            } else {
                getUiHelper().getParent().onAlbumUpdateFinished();
            }
        }
    }

    private static class BadHttpProtocolAction extends UIHelper.QuestionResultAdapter<FragmentUIHelper<AbstractViewAlbumFragment>> {
        private final ConnectionPreferences.ProfilePreferences connectionPreferences;

        public BadHttpProtocolAction(FragmentUIHelper<AbstractViewAlbumFragment> uiHelper, ConnectionPreferences.ProfilePreferences connectionPreferences) {
            super(uiHelper);
            this.connectionPreferences = connectionPreferences;
        }

        @Override
        public void onResult(AlertDialog dialog, Boolean positiveAnswer) {
            if (positiveAnswer != null && positiveAnswer) {
                AbstractViewAlbumFragment fragment = getUiHelper().getParent();
                SharedPreferences prefs = fragment.getPrefs();
                connectionPreferences.setForceHttps(prefs, getContext(), true);
            }
        }
    }

    private static class BadRequestRedirectionAction extends UIHelper.QuestionResultAdapter<FragmentUIHelper<AbstractViewAlbumFragment>> {
        private final ConnectionPreferences.ProfilePreferences connectionPreferences;

        public BadRequestRedirectionAction(FragmentUIHelper uiHelper, ConnectionPreferences.ProfilePreferences connectionPreferences) {
            super(uiHelper);
            this.connectionPreferences = connectionPreferences;
        }

        @Override
        public void onResult(AlertDialog dialog, Boolean positiveAnswer) {
            if (positiveAnswer != null && positiveAnswer) {
                AbstractViewAlbumFragment fragment = getUiHelper().getParent();
                SharedPreferences prefs = fragment.getPrefs();
                connectionPreferences.setFollowHttpRedirects(prefs, getContext(), true);
                //hard reset all http clients! No other solution sadly.
                getUiHelper().addActiveServiceCall(getContext().getString(R.string.loading_new_server_configuration), new HttpConnectionCleanup(connectionPreferences, getContext()).start(), "httpCleanup");
            }
        }
    }

    private static class BulkResourceActionData implements Parcelable {
        public final static int ACTION_DELETE = 1;
        public final static int ACTION_UPDATE_PERMISSIONS = 2;
        public static final Creator<BulkResourceActionData> CREATOR = new Creator<BulkResourceActionData>() {
            @Override
            public BulkResourceActionData createFromParcel(Parcel in) {
                return new BulkResourceActionData(in);
            }

            @Override
            public BulkResourceActionData[] newArray(int size) {
                return new BulkResourceActionData[size];
            }
        };
        final HashSet<Long> selectedItemIds;
        final HashSet<Long> itemsUpdated;
        final HashSet<ResourceItem> selectedItems;
        boolean resourceInfoAvailable;
        private ArrayList<Long> trackedMessageIds = new ArrayList<>();
        private int action;

        public BulkResourceActionData(HashSet<Long> selectedItemIds, HashSet<ResourceItem> selectedItems, int action) {
            this.selectedItemIds = selectedItemIds;
            this.selectedItems = selectedItems;
            this.resourceInfoAvailable = false; //FIXME when Piwigo provides this info as standard, this can be removed and the method simplified.
            itemsUpdated = new HashSet<>(selectedItemIds.size());
            this.action = action;
        }

        public BulkResourceActionData(Parcel in) {
            selectedItemIds = ParcelUtils.readLongSet(in);
            itemsUpdated = ParcelUtils.readLongSet(in);
            selectedItems = ParcelUtils.readHashSet(in, getClass().getClassLoader());
            resourceInfoAvailable = ParcelUtils.readBool(in);
            trackedMessageIds = ParcelUtils.readLongArrayList(in);
            action = in.readInt();
        }

        public int getAction() {
            return action;
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            ParcelUtils.writeLongSet(dest, selectedItemIds);
            ParcelUtils.writeLongSet(dest, itemsUpdated);
            ParcelUtils.writeSet(dest, selectedItems);
            ParcelUtils.writeBool(dest, resourceInfoAvailable);
            ParcelUtils.writeLongArrayList(dest, trackedMessageIds);
            dest.writeInt(action);
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

        private final static int maxHttpRequestsQueued = 20;

        @Override
        public int describeContents() {
            return 0;
        }

        public int getTrackedMessageIdsCount() {
            return trackedMessageIds.size();
        }

        public void getResourcesInfoIfNeeded(AbstractViewAlbumFragment fragment) {
            Set<String> multimediaExtensionList = AlbumViewPreferences.getKnownMultimediaExtensions(fragment.getPrefs(), fragment.requireContext());
            int simultaneousCalls = trackedMessageIds.size();
            if (maxHttpRequestsQueued > simultaneousCalls) {
                for (ResourceItem item : getItemsWithoutLinkedAlbumData()) {
                    simultaneousCalls++;
                    trackMessageId(fragment.addActiveServiceCall(R.string.progress_loading_resource_details, new ImageGetInfoResponseHandler<>(item, multimediaExtensionList)));
                    if (simultaneousCalls >= maxHttpRequestsQueued) {
                        break;
                    }
                }
            }
        }
    }

    private static class AddingAlbumPermissionsAction extends UIHelper.QuestionResultAdapter<FragmentUIHelper<AbstractViewAlbumFragment>> {
        public AddingAlbumPermissionsAction(FragmentUIHelper<AbstractViewAlbumFragment> uiHelper) {
            super(uiHelper);
        }

        @Override
        public void onResult(AlertDialog dialog, Boolean positiveAnswer) {
            if (Boolean.TRUE == positiveAnswer) {
                AbstractViewAlbumFragment fragment = getUiHelper().getParent();
                fragment.addingAlbumPermissions();
            }
        }
    }

    private static class SpanSizeLookup extends GridLayoutManager.SpanSizeLookup {

        private final int totalSpans;
        private final int spansPerAlbum;
        private final int spansPerImage;
        private final ResourceContainer<CategoryItem, GalleryItem> galleryModel;

        SpanSizeLookup(ResourceContainer<CategoryItem, GalleryItem> galleryModel, int totalSpans, int spansPerAlbum, int spansPerImage) {
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

                if (response instanceof ImageDeleteResponseHandler.PiwigoDeleteImageResponse) {
                    onResourcesDeleted((ImageDeleteResponseHandler.PiwigoDeleteImageResponse) response);
                } else if (response instanceof AlbumGetSubAlbumsResponseHandler.PiwigoGetSubAlbumsResponse) {
                    onListOfAlbumsLoaded((AlbumGetSubAlbumsResponseHandler.PiwigoGetSubAlbumsResponse) response);
                } else if (response instanceof BaseImagesGetResponseHandler.PiwigoGetResourcesResponse) {
                    onGetResources((BaseImagesGetResponseHandler.PiwigoGetResourcesResponse) response);
                } else if (response instanceof AlbumDeleteResponseHandler.PiwigoAlbumDeletedResponse) {
                    onAlbumDeleted((AlbumDeleteResponseHandler.PiwigoAlbumDeletedResponse) response);
                } else if (response instanceof AlbumGetPermissionsResponseHandler.PiwigoAlbumPermissionsRetrievedResponse) {
                    onAlbumPermissionsRetrieved((AlbumGetPermissionsResponseHandler.PiwigoAlbumPermissionsRetrievedResponse) response);
                } else if (response instanceof AlbumUpdateInfoResponseHandler.PiwigoUpdateAlbumInfoResponse) {
                    onAlbumInfoAltered((AlbumUpdateInfoResponseHandler.PiwigoUpdateAlbumInfoResponse) response);
                } else if (response instanceof AlbumGetSubAlbumsAdminResponseHandler.PiwigoGetSubAlbumsAdminResponse) {
                    onAdminListOfAlbumsLoaded((AlbumGetSubAlbumsAdminResponseHandler.PiwigoGetSubAlbumsAdminResponse) response);
                } else if (response instanceof ImageCopyToAlbumResponseHandler.PiwigoUpdateAlbumContentResponse) {
                    onAlbumContentAltered((ImageCopyToAlbumResponseHandler.PiwigoUpdateAlbumContentResponse) response);
                } else if (response instanceof BaseImageUpdateInfoResponseHandler.PiwigoUpdateResourceInfoResponse) {
                    onPiwigoUpdateResourceInfoResponse((BaseImageUpdateInfoResponseHandler.PiwigoUpdateResourceInfoResponse) response);
                } else if (response instanceof AlbumThumbnailUpdatedResponseHandler.PiwigoAlbumThumbnailUpdatedResponse) {
                    onThumbnailUpdated((AlbumThumbnailUpdatedResponseHandler.PiwigoAlbumThumbnailUpdatedResponse) response);
                } else if (response instanceof UsernamesGetListResponseHandler.PiwigoGetUsernamesListResponse) {
                    onUsernamesRetrievedForSelectedGroups((UsernamesGetListResponseHandler.PiwigoGetUsernamesListResponse) response);
                } else if (response instanceof AlbumSetStatusResponseHandler.PiwigoSetAlbumStatusResponse) {
                    onAlbumStatusUpdated((AlbumSetStatusResponseHandler.PiwigoSetAlbumStatusResponse) response);
                } else if (response instanceof AlbumAddPermissionsResponseHandler.PiwigoAddAlbumPermissionsResponse) {
                    onAlbumPermissionsAdded((AlbumAddPermissionsResponseHandler.PiwigoAddAlbumPermissionsResponse) response);
                } else if (response instanceof AlbumRemovePermissionsResponseHandler.PiwigoRemoveAlbumPermissionsResponse) {
                    onAlbumPermissionsRemoved((AlbumRemovePermissionsResponseHandler.PiwigoRemoveAlbumPermissionsResponse) response);
                } else if (response instanceof BaseImageGetInfoResponseHandler.PiwigoResourceInfoRetrievedResponse) {
                    onResourceInfoRetrieved((BaseImageGetInfoResponseHandler.PiwigoResourceInfoRetrievedResponse) response);
                } else if (response instanceof AlbumsGetFirstAvailableAlbumResponseHandler.PiwigoGetAlbumTreeResponse) {
                    // do nothing. This is handled. just dont want it to be registered as an error.
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
                            retryActionButton.show();
                        }
                    }
                }
                loadingMessageIds.remove(response.getMessageId());
            }
        }
    }

    private final class AlbumScrollListener extends EndlessRecyclerViewScrollListener {

        public AlbumScrollListener(GridLayoutManager gridLayoutMan) {
            super(gridLayoutMan);
        }

        @Override
        public void onLoadMore(int requestedPage, int totalItemsCount, RecyclerView view) {
            int pageToLoad = requestedPage;

            int pageSize = AlbumViewPreferences.getResourceRequestPageSize(prefs, getContext());
            int pageToActuallyLoad = getPageToActuallyLoad(pageToLoad, pageSize);

            if (galleryModel.isPageLoadedOrBeingLoaded(pageToActuallyLoad) || galleryModel.isFullyLoaded()) {
                Integer missingPage = galleryModel.getAMissingPage();
                if (missingPage != null) {
                    pageToLoad = missingPage;
                } else {
                    // already load this one by default so lets not double load it (or we've already loaded all items).
                    return;
                }
            }
            loadAlbumResourcesPage(pageToLoad);
        }
    }

    private class AlbumViewAdapterListener extends AlbumItemRecyclerViewAdapter.AlbumItemMultiSelectStatusAdapter {

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
        protected void onCategoryClick(CategoryItem item) {
            if (viewAdapter.isItemSelectionAllowed()) {
                viewAdapter.toggleItemSelection();
            }
        }

        @Override
        public void notifyAlbumThumbnailInfoLoadNeeded(CategoryItem mItem) {
            PictureResourceItem resourceItem = new PictureResourceItem(mItem.getRepresentativePictureId(), null, null, null, null, null);
            Set<String> multimediaExtensionList = AlbumViewPreferences.getKnownMultimediaExtensions(prefs, getContext());
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
