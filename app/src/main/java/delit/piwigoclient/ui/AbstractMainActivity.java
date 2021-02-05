package delit.piwigoclient.ui;

import android.content.ActivityNotFoundException;
import android.content.ComponentCallbacks2;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Parcelable;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.loader.app.LoaderManager;

import com.google.android.material.appbar.AppBarLayout;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

import delit.libs.core.util.Logging;
import delit.libs.ui.util.BundleUtils;
import delit.libs.ui.util.DisplayUtils;
import delit.libs.ui.view.CustomToolbar;
import delit.libs.util.VersionUtils;
import delit.piwigoclient.BuildConfig;
import delit.piwigoclient.R;
import delit.piwigoclient.business.AlbumViewPreferences;
import delit.piwigoclient.business.AppPreferences;
import delit.piwigoclient.business.ConnectionPreferences;
import delit.piwigoclient.business.OtherPreferences;
import delit.piwigoclient.model.piwigo.Basket;
import delit.piwigoclient.model.piwigo.CategoryItem;
import delit.piwigoclient.model.piwigo.CategoryItemStub;
import delit.piwigoclient.model.piwigo.GalleryItem;
import delit.piwigoclient.model.piwigo.PictureResourceItem;
import delit.piwigoclient.model.piwigo.PiwigoSessionDetails;
import delit.piwigoclient.model.piwigo.ResourceContainer;
import delit.piwigoclient.model.piwigo.StaticCategoryItem;
import delit.piwigoclient.model.piwigo.VersionCompatability;
import delit.piwigoclient.model.piwigo.VideoResourceItem;
import delit.piwigoclient.ui.album.create.CreateAlbumFragment;
import delit.piwigoclient.ui.album.drillDownSelect.CategoryItemViewAdapterPreferences;
import delit.piwigoclient.ui.album.drillDownSelect.RecyclerViewCategoryItemSelectFragment;
import delit.piwigoclient.ui.album.listSelect.AlbumSelectFragment;
import delit.piwigoclient.ui.album.view.AbstractViewAlbumFragment;
import delit.piwigoclient.ui.album.view.ViewAlbumFragment;
import delit.piwigoclient.ui.common.ActivityUIHelper;
import delit.piwigoclient.ui.common.MyActivity;
import delit.piwigoclient.ui.common.fragment.MyFragment;
import delit.piwigoclient.ui.events.AlbumItemSelectedEvent;
import delit.piwigoclient.ui.events.AlbumSelectedEvent;
import delit.piwigoclient.ui.events.CancelDownloadEvent;
import delit.piwigoclient.ui.events.DownloadFileRequestEvent;
import delit.piwigoclient.ui.events.EulaAgreedEvent;
import delit.piwigoclient.ui.events.EulaNotAgreedEvent;
import delit.piwigoclient.ui.events.GenericLowMemoryEvent;
import delit.piwigoclient.ui.events.MemoryTrimmedEvent;
import delit.piwigoclient.ui.events.MemoryTrimmedRunningAppEvent;
import delit.piwigoclient.ui.events.NavigationItemSelectEvent;
import delit.piwigoclient.ui.events.PiwigoActivePluginsReceivedEvent;
import delit.piwigoclient.ui.events.PiwigoLoginSuccessEvent;
import delit.piwigoclient.ui.events.SlideshowEmptyEvent;
import delit.piwigoclient.ui.events.StatusBarChangeEvent;
import delit.piwigoclient.ui.events.ToolbarEvent;
import delit.piwigoclient.ui.events.ViewGroupEvent;
import delit.piwigoclient.ui.events.ViewUserEvent;
import delit.piwigoclient.ui.events.trackable.AlbumCreateNeededEvent;
import delit.piwigoclient.ui.events.trackable.AlbumCreatedEvent;
import delit.piwigoclient.ui.events.trackable.AlbumPermissionsSelectionNeededEvent;
import delit.piwigoclient.ui.events.trackable.AlbumSelectionNeededEvent;
import delit.piwigoclient.ui.events.trackable.ExpandingAlbumSelectionNeededEvent;
import delit.piwigoclient.ui.events.trackable.GroupSelectionNeededEvent;
import delit.piwigoclient.ui.events.trackable.UsernameSelectionNeededEvent;
import delit.piwigoclient.ui.permissions.AlbumSelectionListAdapterPreferences;
import delit.piwigoclient.ui.permissions.groups.GroupFragment;
import delit.piwigoclient.ui.permissions.groups.GroupRecyclerViewAdapter;
import delit.piwigoclient.ui.permissions.groups.GroupSelectFragment;
import delit.piwigoclient.ui.permissions.groups.GroupsListFragment;
import delit.piwigoclient.ui.permissions.users.UserFragment;
import delit.piwigoclient.ui.permissions.users.UsernameRecyclerViewAdapter;
import delit.piwigoclient.ui.permissions.users.UsernameSelectFragment;
import delit.piwigoclient.ui.permissions.users.UsersListFragment;
import delit.piwigoclient.ui.slideshow.AlbumVideoItemFragment;
import delit.piwigoclient.ui.slideshow.SlideshowFragment;
import delit.piwigoclient.ui.util.download.DownloadManager;
import delit.piwigoclient.util.MyDocumentProvider;
import hotchemi.android.rate.MyAppRate;

import static android.provider.DocumentsContract.EXTRA_INITIAL_URI;

public abstract class AbstractMainActivity<A extends AbstractMainActivity<A, AUIH>, AUIH extends ActivityUIHelper<AUIH, A>> extends MyActivity<A, AUIH> implements ComponentCallbacks2 {

    private static final String STATE_CURRENT_ALBUM = "currentAlbum";
    private static final String STATE_BASKET = "basket";
    private static final String TAG = "mainActivity";
    private static final String STATE_DOWNLOAD_MANAGER = "DownloadManager";
    private static final String STATE_ACTIVE_PIWIGO_USERNAME = "ActiveUsername";
    private static final String STATE_ACTIVE_PIWIGO_SERVER = "ActiveServerUri";
    private final CustomBackStackListener backStackListener;
    // these fields are persisted.
    private CategoryItem currentAlbum = StaticCategoryItem.ROOT_ALBUM;
    private String onLoginActionMethodName = null;
    private final ArrayList<Parcelable> onLoginActionParams = new ArrayList<>();
    private Basket basket = new Basket();
    private CustomToolbar toolbar;
    private AppBarLayout appBar;
    private DownloadManager<AUIH, A> downloadManager;
    private String currentPiwigoServer;
    private String currentPiwigoUser;


    public AbstractMainActivity() {
        super(R.layout.activity_main);
        backStackListener = new CustomBackStackListener();
        getSupportFragmentManager().addOnBackStackChangedListener(backStackListener);
    }

    @Override
    protected void onDestroy() {
        getSupportFragmentManager().removeOnBackStackChangedListener(backStackListener);
        super.onDestroy();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {

//        if(BuildConfig.DEBUG) {
//            getSupportFragmentManager().enableDebugLogging(true);
//        }
        super.onSaveInstanceState(outState);
        LoaderManager.getInstance(this).getLoader(0); //TODO is this needed?
        outState.putParcelable(STATE_CURRENT_ALBUM, currentAlbum);
        outState.putParcelable(STATE_BASKET, basket);
        outState.putParcelable(STATE_DOWNLOAD_MANAGER, downloadManager);
        outState.putString(STATE_ACTIVE_PIWIGO_SERVER, ConnectionPreferences.getActiveProfile().getPiwigoServerAddress(getSharedPrefs(),this));
        outState.putString(STATE_ACTIVE_PIWIGO_USERNAME, ConnectionPreferences.getActiveProfile().getPiwigoUsername(getSharedPrefs(),this));

        if(BuildConfig.DEBUG) {
//            getSupportFragmentManager().enableDebugLogging(false);
            BundleUtils.logSizeVerbose("Current Main Activity", outState);
        }
    }

    @Override
    protected String getDesiredLanguage(Context context) {
        return AppPreferences.getDesiredLanguage(getSharedPrefs(context), context);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null) {
            currentAlbum = savedInstanceState.getParcelable(STATE_CURRENT_ALBUM);
            basket = savedInstanceState.getParcelable(STATE_BASKET);
            downloadManager = savedInstanceState.getParcelable(STATE_DOWNLOAD_MANAGER);
            currentPiwigoServer = savedInstanceState.getString(STATE_ACTIVE_PIWIGO_SERVER);
            currentPiwigoUser = savedInstanceState.getString(STATE_ACTIVE_PIWIGO_USERNAME);
            if(downloadManager == null) {
                downloadManager = new DownloadManager<>(getUiHelper());
            } else {
                downloadManager.withUiHelper(getUiHelper());
            }
        } else {
            downloadManager = new DownloadManager<>(getUiHelper());
        }


        toolbar = findViewById(R.id.toolbar);

        setSupportActionBar(toolbar);
        appBar = findViewById(R.id.appbar);

        DrawerLayout drawer = configureDrawer(toolbar);
        if (hasNotAcceptedEula()) {
            drawer.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED);
        } else {
            drawer.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED);
        }

        Intent intent = getIntent();
        String action = intent.getAction();
        boolean actionHandled = false;
        if(action != null) {
            if(action.equals("delit.piwigoclient.VIEW_TOP_TIPS")) {
                showTopTips();
                actionHandled = true;
            } else if(action.equals("delit.piwigoclient.VIEW_USERS")) {
                showUsers();
                actionHandled = true;
            } else if(action.equals("delit.piwigoclient.VIEW_GROUPS")) {
                showGroups();
                actionHandled = true;
            }
            intent.setAction(null);
        }

        if ((!actionHandled && savedInstanceState == null)) {
            if (hasNotAcceptedEula()) {
                showEula();
            } else if (ConnectionPreferences.getActiveProfile().getTrimmedNonNullPiwigoServerAddress(prefs, getApplicationContext()).isEmpty()) {
                showPreferences();
            } else {
                showGallery(currentAlbum);
            }
        }

        configureAndShowRateAppReminderIfNeeded();
    }

    @Override
    protected void onPause() {
        super.onPause();
        currentPiwigoServer = ConnectionPreferences.getActiveProfile().getPiwigoServerAddress(getSharedPrefs(),this);
        currentPiwigoUser = ConnectionPreferences.getActiveProfile().getPiwigoUsername(getSharedPrefs(),this);
    }

    @Override
    public void onResume() {
        super.onResume();
        if(serverConnectionHasChanged()) {
            // we're in the wrong server or
            currentAlbum = null;
            // need to remove historic fragments - not valid.
            clearBackStack();
            showGallery(StaticCategoryItem.ROOT_ALBUM);
        }
    }

    private boolean serverConnectionHasChanged() {
        if(currentPiwigoServer == null) {
            return false; // haven't opened any server yet
        }
        String connectedServer = ConnectionPreferences.getActiveProfile().getPiwigoServerAddress(getSharedPrefs(), this);
        String connectedUsername = ConnectionPreferences.getActiveProfile().getPiwigoUsername(getSharedPrefs(), this);
        return !(Objects.equals(currentPiwigoUser, connectedUsername) && Objects.equals(currentPiwigoServer, connectedServer));
    }

    private void configureAndShowRateAppReminderIfNeeded() {
        MyAppRate.instance()
                .setInstallDays(10) // default 10, 0 means install day.
                .setLaunchTimes(10) // default 10
                .setRemindInterval(30) // default 1
                .setShowLaterButton(true) // default true
                .setDebug(false) // default false
                .monitor(getApplicationContext());

        // Show a dialog if meets conditions
        MyAppRate.showRateDialogIfMeetsConditions(this);
    }

    public Basket getBasket() {
        return basket;
    }

    @Override
    public void onBackPressed() {

        int backstackCount = getSupportFragmentManager().getBackStackEntryCount();

        if (hasNotAcceptedEula()) {
            // exit immediately.
            finish();
            return;
        }

        DrawerLayout drawer = findViewById(R.id.drawer_layout);
        if (drawer.isDrawerOpen(GravityCompat.START)) {
            drawer.closeDrawer(GravityCompat.START);
        } else {
            // pop the current fragment off, close app if it is the last one
            boolean blockDefaultBackOperation = false;
            if (backstackCount == 1) {
                Fragment currentFragment = getActiveFragment();
                if (currentFragment instanceof ViewAlbumFragment && currentAlbum != null && !currentAlbum.isRoot()) {
                    // get the next album to show
                    CategoryItem nextAlbumToShow = ((ViewAlbumFragment<?,?>) currentFragment).getParentAlbum();
                    if (nextAlbumToShow != null) {
                        Logging.log(Log.INFO, TAG, "removing from activity to show next (parent) album");
                        getSupportFragmentManager().popBackStack();
                        // open this fragment again, but with new album
                        showGallery(nextAlbumToShow);
                        blockDefaultBackOperation = true;
                    }
                }
            }
            if (!blockDefaultBackOperation) {
                doDefaultBackOperation();
            }
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.piwigo_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (hasNotAcceptedEula()) {
            getUiHelper().showDetailedMsg(R.string.alert_error, R.string.please_read_and_agree_with_eula_first);
            return true;
        }
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        if (id == R.id.action_settings) {
            showPreferences();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    public static Intent buildShowGalleryIntent(Context context) {
        Intent intent = new Intent(Intent.ACTION_VIEW, null, context.getApplicationContext(), MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
//            intent.putExtra(INTENT_DATA_CURRENT_ALBUM, currentAlbum);
        return intent;
    }

    public static Intent buildShowGroupsIntent(Context context) {
        Intent intent = new Intent("delit.piwigoclient.VIEW_GROUPS", null, context.getApplicationContext(), MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        return intent;
    }

    public static Intent buildShowUsersIntent(Context context) {
        Intent intent = new Intent("delit.piwigoclient.VIEW_USERS", null, context.getApplicationContext(), MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        return intent;
    }

    public static Intent buildShowTopTipsIntent(Context context) {
        Intent intent = new Intent("delit.piwigoclient.VIEW_TOP_TIPS", null, context.getApplicationContext(), MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return intent;
    }

    private void showPreferences() {
        try {
            startActivity(PreferencesActivity.buildIntent(this));
        } catch(ActivityNotFoundException e) {
            Logging.recordException(e);
        }
    }

    private void showEula() {
        showFragmentNow(EulaFragment.newInstance());
    }

    protected abstract void showFavorites();

    protected abstract void showOrphans();

    private void showGallery(final CategoryItem gallery) {
        boolean restore = false;
        if (gallery != null && gallery.isRoot()) {
            // check if we've shown any albums before. If so, pop everything off the stack.
            if (null == getSupportFragmentManager().findFragmentByTag(ViewAlbumFragment.class.getName())) {
                // we're opening the activity freshly.

                // check for reopen details and use them instead if possible.
                if (AbstractViewAlbumFragment.canHandleReopenAction(getUiHelper())) {
                    restore = true;
                }
            }
        }
        AdsManager.getInstance(this).showAlbumBrowsingAdvertIfAppropriate(this);

        if (restore) {
            showFragmentNow(new ViewAlbumFragment<>(), !gallery.isRoot());
        } else {
            showFragmentNow(ViewAlbumFragment.newInstance(gallery), gallery != null && !gallery.isRoot());
        }
    }


//TODO add some sort of cancel operation  if the activity is backgrounded perhaps.
//    @Override
//    public void onDetach() {
//        if (activeDownloadAction != null) {
//            EventBus.getDefault().post(new CancelDownloadEvent(activeDownloadAction.getActiveDownloadActionId()));
//        }
//        super.onDetach();
//    }
//

    @Subscribe(threadMode = ThreadMode.MAIN_ORDERED)
    public void onEvent(DownloadFileRequestEvent event) {
        downloadManager.onEvent(event);
    }

    @Subscribe(threadMode = ThreadMode.MAIN_ORDERED)
    public void onEvent(CancelDownloadEvent event) {
        downloadManager.onEvent(event);
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(AlbumItemSelectedEvent event) {

        Fragment newFragment = null;

        ResourceContainer<?, GalleryItem> albumOpen = event.getResourceContainer();
        GalleryItem selectedItem = event.getSelectedItem();

        if (selectedItem instanceof CategoryItem) {
            currentAlbum = (CategoryItem) selectedItem;
            showGallery(currentAlbum);
        } else {
            boolean showVideosInSlideshow = AlbumViewPreferences.isIncludeVideosInSlideshow(prefs, this);
            boolean allowVideoPlayback = AlbumViewPreferences.isVideoPlaybackEnabled(prefs, this);
            if (selectedItem instanceof VideoResourceItem) {
                if (showVideosInSlideshow) {
                    newFragment = new SlideshowFragment<>();
                    newFragment.setArguments(SlideshowFragment.buildArgs(event.getModelType(), albumOpen, selectedItem));
                } else if (allowVideoPlayback) {
                    newFragment = new AlbumVideoItemFragment<>();
                    newFragment.setArguments(AlbumVideoItemFragment.buildStandaloneArgs(event.getModelType(), albumOpen.getId(), selectedItem.getId(), 1, 1, 1, true));
                    ((AlbumVideoItemFragment) newFragment).onPageSelected();
                }
            } else if (selectedItem instanceof PictureResourceItem) {
                newFragment = new SlideshowFragment<>();
                newFragment.setArguments(SlideshowFragment.buildArgs(event.getModelType(), albumOpen, selectedItem));
            }
        }

        if (newFragment != null) {
            showFragmentNow(newFragment);
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public final void onNavigationItemSelected(NavigationItemSelectEvent event) {
        // Handle navigation view item clicks here.
        int id = event.navigationitemSelected;

        if (id == R.id.nav_upload) {
            showUpload();
        } else if (id == R.id.nav_download) {
            showDownloads();
        } else if (id == R.id.nav_groups) {
            showGroups();
        } else if (id == R.id.nav_tags) {
            showTags();
        } else if (id == R.id.nav_users) {
            showUsers();
        } else if (id == R.id.nav_top_tips) {
            showTopTips();
        } else if (id == R.id.nav_gallery) {
            showGallery(StaticCategoryItem.ROOT_ALBUM);
        } else if(id == R.id.nav_orphans) {
            showOrphans();
        } else if (id == R.id.nav_favorites) {
            showFavorites();
        } else if (id == R.id.nav_about) {
            showAboutFragment();
        } else if (id == R.id.nav_oss_licences) {
            showLicencesFragment();
        } else if (id == R.id.nav_settings) {
            showPreferences();
        } else if (id == R.id.nav_eula) {
            showEula();
        } else if (id == R.id.nav_privacy) {
            showPrivacy();
        } else {
            onNavigationItemSelected(event, id);
        }

        DrawerLayout drawer = findViewById(R.id.drawer_layout);
        drawer.closeDrawer(GravityCompat.START);
    }

    public void openFolder(Uri uri){
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            intent.putExtra(EXTRA_INITIAL_URI, uri);
        }
        intent.putExtra(Intent.EXTRA_LOCAL_ONLY, true);
        try {
            startActivity(Intent.createChooser(intent, getString(R.string.view_exported_files)));
        } catch(ActivityNotFoundException e) {
            Logging.recordException(e);
        }
    }

    private void showDownloads() {
        Uri downloadFolder = AppPreferences.getAppDownloadFolder(getSharedPrefs(), this).getUri();
        if(MyDocumentProvider.ownsUri(this, downloadFolder)) {
            downloadFolder = MyDocumentProvider.getRootDocUri();
        }
        openFolder(downloadFolder);
    }

    protected void showPrivacy() {
        AdsManager.getInstance(this).showPrivacyForm(this);
    }

    protected void onNavigationItemSelected(NavigationItemSelectEvent event, @IdRes int itemId) {
    }

    private void showTopTips() {
        TopTipsFragment<?,?> fragment = TopTipsFragment.newInstance();
        showFragmentNow(fragment);
    }

    private void showAboutFragment() {
        AboutFragment<?,?> fragment = AboutFragment.newInstance();
        showFragmentNow(fragment);
    }

    private void showLicencesFragment() {
        LicencesFragment<?,?> fragment = LicencesFragment.newInstance();
        showFragmentNow(fragment);
    }

    private void showUpload() {
        try {
            startActivity(UploadActivity.buildIntent(this, currentAlbum.toStub()));
        } catch(ActivityNotFoundException e) {
            Logging.recordException(e);
        }
    }

    private void showGroups() {
        GroupsListFragment<?,?> fragment = GroupsListFragment.newInstance();
        showFragmentNow(fragment);
    }

    public void setOnLoginActionMethodName(String onLoginActionMethodName) {
        this.onLoginActionMethodName = onLoginActionMethodName;
    }

    protected abstract void showTags();

    private void showAlbumPermissions(final ArrayList<CategoryItemStub> availableAlbums, final HashSet<Long> directAlbumPermissions, final HashSet<Long> indirectAlbumPermissions, boolean allowEdit, int actionId) {
        AlbumSelectionListAdapterPreferences adapterPreferences = new AlbumSelectionListAdapterPreferences(allowEdit);
        delit.piwigoclient.ui.permissions.AlbumSelectFragment<?,?> fragment = delit.piwigoclient.ui.permissions.AlbumSelectFragment.newInstance(availableAlbums, adapterPreferences, actionId, indirectAlbumPermissions, directAlbumPermissions);
        showFragmentNow(fragment);
    }

    private void showUsers() {
        showFragmentNow(UsersListFragment.newInstance());
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(AlbumSelectedEvent event) {
        currentAlbum = event.getAlbum();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);

        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.KITKAT) {
            return; // don't mess with the status bar
        }

        View v = getWindow().getDecorView();
        v.setFitsSystemWindows(!hasFocus);

        if (hasFocus) {
            DisplayUtils.setUiFlags(this, AppPreferences.isAlwaysShowNavButtons(prefs, this), AppPreferences.isAlwaysShowStatusBar(prefs, this));
            Logging.log(Log.ERROR, TAG, "hiding status bar!");
        } else {
            Logging.log(Log.ERROR, TAG, "showing status bar!");
        }

//        v.requestApplyInsets(); // is this needed
        EventBus.getDefault().post(new StatusBarChangeEvent(!hasFocus));
    }

    @Subscribe(threadMode = ThreadMode.MAIN_ORDERED)
    public void onEvent(final AlbumCreateNeededEvent event) {

        CreateAlbumFragment<?,?> fragment = CreateAlbumFragment.newInstance(event.getActionId(), event.getParentAlbum());
        showFragmentNow(fragment);
    }

    public static Uri toContentUri(@NonNull Context context, @NonNull Uri uri) {
        if ("file".equals(uri.getScheme())) {
            return androidx.core.content.FileProvider.getUriForFile(context, BuildConfig.FILE_PROVIDER_AUTHORITY, new File(uri.getPath()));
        } else {
            return uri;
        }
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
    }

    /**
     * Release memory when the UI becomes hidden or when system resources become low.
     *
     * @param level the memory-related event that was raised.
     */
    @Override
    public void onTrimMemory(int level) {

        boolean cacheCleared;
        // Determine which lifecycle or system event was raised.
        switch (level) {

            case ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN:

                //TODO do something useful?

                /*
                   Release any UI objects that currently hold memory.

                   The user interface has moved to the background.
                */

                break;

            case ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE:
            case ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW:
            case ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL:

                cacheCleared = PicassoFactory.getInstance().clearPicassoCache(getApplicationContext());
                EventBus.getDefault().post(new MemoryTrimmedRunningAppEvent(level, cacheCleared));

                /*
                   Release any memory that your app doesn't need to run.

                   The device is running low on memory while the app is running.
                   The event raised indicates the severity of the memory-related event.
                   If the event is TRIM_MEMORY_RUNNING_CRITICAL, then the system will
                   begin killing background processes.
                */

                break;

            case ComponentCallbacks2.TRIM_MEMORY_BACKGROUND:
            case ComponentCallbacks2.TRIM_MEMORY_MODERATE:
            case ComponentCallbacks2.TRIM_MEMORY_COMPLETE:

                cacheCleared = PicassoFactory.getInstance().clearPicassoCache(getApplicationContext());
                EventBus.getDefault().post(new MemoryTrimmedEvent(level, cacheCleared));
                /*
                   Release as much memory as the process can.

                   The app is on the LRU list and the system is running low on memory.
                   The event raised indicates where the app sits within the LRU list.
                   If the event is TRIM_MEMORY_COMPLETE, the process will be one of
                   the first to be terminated.
                */

                break;

            default:

                cacheCleared = PicassoFactory.getInstance().clearPicassoCache(getApplicationContext());
                EventBus.getDefault().post(new GenericLowMemoryEvent(level, cacheCleared));

                /*
                  Release any non-critical data structures.

                  The app received an unrecognized memory level value
                  from the system. Treat this as a generic low-memory message.
                */
                break;
        }

    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(GenericLowMemoryEvent event) {
        if (event.isPicassoCacheCleared()) {
            showLowMemoryWarningMessage(R.string.alert_warning_lowMemory_message, event.getMemoryLevel());
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(MemoryTrimmedRunningAppEvent event) {
        if (event.isPicassoCacheCleared()) {
            showLowMemoryWarningMessage(R.string.alert_warning_lowMemory_message, event.getMemoryLevel());
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(MemoryTrimmedEvent event) {
        if (event.isPicassoCacheCleared()) {
            showLowMemoryWarningMessage(R.string.alert_warning_lowMemory_message, event.getMemoryLevel());
        }
    }

    private void showLowMemoryWarningMessage(int messageId, int memoryLevel) {
        getUiHelper().showDetailedShortMsg(R.string.alert_warning, getString(messageId, memoryLevel));
    }

    @Subscribe(threadMode = ThreadMode.MAIN_ORDERED)
    public void onEvent(final UsernameSelectionNeededEvent event) {
        UsernameRecyclerViewAdapter.UsernameRecyclerViewAdapterPreferences prefs = new UsernameRecyclerViewAdapter.UsernameRecyclerViewAdapterPreferences(event.isAllowEditing(), event.isAllowMultiSelect(), event.isInitialSelectionLocked());
        UsernameSelectFragment<?,?> fragment = UsernameSelectFragment.newInstance(prefs, event.getActionId(), event.getIndirectSelection(), event.getInitialSelection());
        showFragmentNow(fragment);
    }

    @Subscribe(threadMode = ThreadMode.MAIN_ORDERED)
    public void onEvent(ViewUserEvent event) {
        UserFragment<?,?> fragment = UserFragment.newInstance(event.getUser());
        showFragmentNow(fragment);
    }

    private void showAlbumSelectionFragment(int actionId, AlbumSelectionListAdapterPreferences prefs, HashSet<Long> currentSelection) {
        AlbumSelectFragment<?,?> fragment = AlbumSelectFragment.newInstance(prefs, actionId, currentSelection);
        showFragmentNow(fragment);
    }

    @Subscribe(threadMode = ThreadMode.MAIN_ORDERED)
    public void onEvent(ViewGroupEvent event) {
        GroupFragment<?,?> fragment = GroupFragment.newInstance(event.getGroup());
        showFragmentNow(fragment);
    }

    @Subscribe(threadMode = ThreadMode.MAIN_ORDERED)
    public void onEvent(final GroupSelectionNeededEvent event) {
        GroupRecyclerViewAdapter.GroupViewAdapterPreferences prefs = new GroupRecyclerViewAdapter.GroupViewAdapterPreferences(event.isAllowEditing(), event.isAllowMultiSelect(), event.isInitialSelectionLocked());
        GroupSelectFragment<?,?> fragment = GroupSelectFragment.newInstance(prefs, event.getActionId(), event.getInitialSelection());
        showFragmentNow(fragment);
    }


    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(EulaNotAgreedEvent event) {
        // exit the app now.
        finishAffinity();
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(EulaAgreedEvent event) {
        // unlock the drawer.
        DrawerLayout drawer = findViewById(R.id.drawer_layout);
        drawer.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED);

        if (ConnectionPreferences.getActiveProfile().getTrimmedNonNullPiwigoServerAddress(prefs, getApplicationContext()).isEmpty()) {
            showPreferences();
        } else {
            showGallery(StaticCategoryItem.ROOT_ALBUM);
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN_ORDERED)
    public void onEvent(AlbumPermissionsSelectionNeededEvent event) {
        showAlbumPermissions(event.getAvailableAlbums(), event.getDirectAlbumPermissions(), event.getIndirectAlbumPermissions(), event.isAllowEdit(), event.getActionId());
    }

    private boolean invokeStoredActionIfAvailable() {

        boolean invoked = invokeStoredActionIfAvailableOnClass(AbstractMainActivity.class);
        if (!invoked) {
            invoked = invokeStoredActionIfAvailableOnClass(MainActivity.class);
        }
        return invoked;
    }

    private boolean invokeStoredActionIfAvailableOnClass(Class<?> c) {

        boolean invoked = false;
        boolean actionAvailable = onLoginActionMethodName != null;
        if (actionAvailable) {
            Method[] methods = c.getDeclaredMethods();
            for (Method m : methods) {
                if (m.getName().equals(onLoginActionMethodName)) {
                    onLoginActionMethodName = null;
                    try {
                        invoked = true;
                        Object[] params = onLoginActionParams.toArray();
                        onLoginActionParams.clear();
                        m.invoke(this, params);
                        break;
                    } catch (IllegalAccessException e) {
                        Logging.recordException(e);
                        throw new RuntimeException("Error running post login action ", e);
                    } catch (InvocationTargetException e) {
                        Logging.recordException(e);
                        throw new RuntimeException("Error running post login action ", e);
                    }
                }
            }
        }
        return invoked;
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(ToolbarEvent event) {
        if(!this.equals(event.getActivity())) {
            return;
        }
        if(event.getSpannableTitle() != null) {
            toolbar.setSpannableTitle(event.getSpannableTitle());
        } else {
            toolbar.setTitle(event.getTitle());
        }
        if(event.isExpandToolbarView()) {
            appBar.setExpanded(true, event.getTitle()!= null);
        } else if(event.isContractToolbarView()) {
            appBar.setExpanded(false, event.getTitle()!= null);
        }
        appBar.setEnabled(event.getTitle()!= null);
    }
//
//    @Override
//    protected void onNewIntent(Intent intent) {
//        super.onNewIntent(intent);
//        this.setIntent(intent);
//    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(PiwigoActivePluginsReceivedEvent event) {
        Logging.addContext(this, event.getCredentials().getSessionDebugInfoMap());
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(PiwigoLoginSuccessEvent event) {

        PiwigoSessionDetails sessionDetails = PiwigoSessionDetails.getInstance(ConnectionPreferences.getActiveProfile());
        Logging.addContext(this, sessionDetails.getSessionDebugInfoMap());
        Logging.addContext(this,"app.language", AppPreferences.getDesiredLanguage(getSharedPrefs(), this));

        if (event.isChangePage() && !invokeStoredActionIfAvailable()) {
            // If nothing specified, show the root gallery.
            showGallery(StaticCategoryItem.ROOT_ALBUM);
        } else {
            //TODO notify all pages that need it that they need to be reloaded - i.e. flush them out of the fragment manager or send an event forcing reload.
        }
        AdsManager.getInstance(this).updateShowAdvertsSetting(this);
        VersionCompatability.INSTANCE.runTests();

        boolean showUserWarning = OtherPreferences.getAndUpdateLastWarnedAboutVersionOrFeatures(prefs, this);

        if (!VersionCompatability.INSTANCE.isSupportedVersion()) {
            String serverVersion = VersionCompatability.INSTANCE.getServerVersionString();
            String minimumVersion = VersionCompatability.INSTANCE.getMinimumTestedVersionString();
            getUiHelper().showDetailedMsg(R.string.alert_warning, getString(R.string.alert_error_unsupported_piwigo_version_pattern, serverVersion, minimumVersion));
        }

        if(sessionDetails.isPiwigoClientPluginInstalled() && !VersionUtils.versionExceeds(VersionUtils.parseVersionString(BuildConfig.PIWIGO_CLIENT_WS_VERSION), VersionUtils.parseVersionString(sessionDetails.getPiwigoClientPluginVersion()))) {
            if(sessionDetails.getServerUrl().equalsIgnoreCase("https://piwigo.com"))
            getUiHelper().showDetailedMsg(R.string.alert_warning, getString(R.string.alert_error_unsupported_piwigo_client_ws_ext_version_please_update_it));
        }

        if(showUserWarning && !VersionCompatability.INSTANCE.isFavoritesEnabled()) {
            getUiHelper().showDetailedMsg(R.string.alert_warning, getString(R.string.alert_error_unsupported_features_pattern));
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(SlideshowEmptyEvent event) {
        Logging.log(Log.INFO, TAG, "removing from activity immediately as slideshow empty event rxd");
        getSupportFragmentManager().popBackStackImmediate();
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(AlbumCreatedEvent event) {
        Logging.log(Log.INFO, TAG, "removing from activity immediately as album created event rxd");
        getSupportFragmentManager().popBackStackImmediate();
    }

    @Subscribe(threadMode = ThreadMode.MAIN_ORDERED)
    public void onEvent(AlbumSelectionNeededEvent event) {
        AlbumSelectionListAdapterPreferences adapterPreferences = new AlbumSelectionListAdapterPreferences(false, true, event.isAllowEditing(), event.isAllowMultiSelect(), event.isInitialSelectionLocked());
        showAlbumSelectionFragment(event.getActionId(), adapterPreferences, event.getInitialSelection());
    }

    @Subscribe(threadMode = ThreadMode.MAIN_ORDERED)
    public void onEvent(ExpandingAlbumSelectionNeededEvent event) {
//        ExpandableAlbumsListAdapter.ExpandableAlbumsListAdapterPreferences prefs = new ExpandableAlbumsListAdapter.ExpandableAlbumsListAdapterPreferences();
//        AlbumSelectExpandableFragment f = AlbumSelectExpandableFragment.newInstance(prefs, event.getActionId(), event.getInitialSelection());
        CategoryItemViewAdapterPreferences prefs = new CategoryItemViewAdapterPreferences(event.getInitialRoot(), event.isAllowEditing(), event.getInitialSelection(), event.isAllowMultiSelect(), event.isInitialSelectionLocked());
        prefs.withConnectionProfile(event.getConnectionProfileName());
        RecyclerViewCategoryItemSelectFragment<?,?> f = RecyclerViewCategoryItemSelectFragment.newInstance(prefs, event.getActionId());
        showFragmentNow(f);
    }


    private class CustomBackStackListener implements FragmentManager.OnBackStackChangedListener {
        @Override
        public void onBackStackChanged() {
            List<Fragment> fragmentList = getSupportFragmentManager().getFragments();
            if(!fragmentList.isEmpty()) {
                ((MyFragment<?,?>)fragmentList.get(fragmentList.size()-1)).updatePageTitle();
            }
        }
    }
}
