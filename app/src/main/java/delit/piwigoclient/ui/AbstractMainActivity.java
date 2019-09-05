package delit.piwigoclient.ui;

import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.ComponentCallbacks2;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import androidx.annotation.IdRes;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.widget.Toolbar;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import androidx.loader.app.LoaderManager;

import com.crashlytics.android.Crashlytics;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.material.appbar.AppBarLayout;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.io.File;
import java.io.Serializable;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;

import delit.libs.ui.util.BundleUtils;
import delit.libs.ui.util.DisplayUtils;
import delit.libs.ui.view.recycler.BaseRecyclerViewAdapterPreferences;
import delit.libs.util.VersionUtils;
import delit.piwigoclient.BuildConfig;
import delit.piwigoclient.R;
import delit.piwigoclient.business.ConnectionPreferences;
import delit.piwigoclient.business.OtherPreferences;
import delit.piwigoclient.model.piwigo.Basket;
import delit.piwigoclient.model.piwigo.CategoryItem;
import delit.piwigoclient.model.piwigo.CategoryItemStub;
import delit.piwigoclient.model.piwigo.GalleryItem;
import delit.piwigoclient.model.piwigo.PictureResourceItem;
import delit.piwigoclient.model.piwigo.PiwigoSessionDetails;
import delit.piwigoclient.model.piwigo.ResourceContainer;
import delit.piwigoclient.model.piwigo.VersionCompatability;
import delit.piwigoclient.model.piwigo.VideoResourceItem;
import delit.piwigoclient.ui.album.create.CreateAlbumFragment;
import delit.piwigoclient.ui.album.drillDownSelect.CategoryItemViewAdapterPreferences;
import delit.piwigoclient.ui.album.drillDownSelect.RecyclerViewCategoryItemSelectFragment;
import delit.piwigoclient.ui.album.listSelect.AlbumSelectFragment;
import delit.piwigoclient.ui.album.listSelect.AvailableAlbumsListAdapter;
import delit.piwigoclient.ui.album.view.ViewAlbumFragment;
import delit.piwigoclient.ui.common.MyActivity;
import delit.piwigoclient.ui.events.AlbumItemSelectedEvent;
import delit.piwigoclient.ui.events.AlbumSelectedEvent;
import delit.piwigoclient.ui.events.EulaAgreedEvent;
import delit.piwigoclient.ui.events.EulaNotAgreedEvent;
import delit.piwigoclient.ui.events.GenericLowMemoryEvent;
import delit.piwigoclient.ui.events.MemoryTrimmedEvent;
import delit.piwigoclient.ui.events.MemoryTrimmedRunningAppEvent;
import delit.piwigoclient.ui.events.NavigationItemSelectEvent;
import delit.piwigoclient.ui.events.PiwigoLoginSuccessEvent;
import delit.piwigoclient.ui.events.SlideshowEmptyEvent;
import delit.piwigoclient.ui.events.StatusBarChangeEvent;
import delit.piwigoclient.ui.events.ThemeAlteredEvent;
import delit.piwigoclient.ui.events.ToolbarEvent;
import delit.piwigoclient.ui.events.ViewGroupEvent;
import delit.piwigoclient.ui.events.ViewUserEvent;
import delit.piwigoclient.ui.events.trackable.AlbumCreateNeededEvent;
import delit.piwigoclient.ui.events.trackable.AlbumCreatedEvent;
import delit.piwigoclient.ui.events.trackable.AlbumPermissionsSelectionNeededEvent;
import delit.piwigoclient.ui.events.trackable.AlbumSelectionNeededEvent;
import delit.piwigoclient.ui.events.trackable.ExpandingAlbumSelectionNeededEvent;
import delit.piwigoclient.ui.events.trackable.FileSelectionCompleteEvent;
import delit.piwigoclient.ui.events.trackable.FileSelectionNeededEvent;
import delit.piwigoclient.ui.events.trackable.GroupSelectionNeededEvent;
import delit.piwigoclient.ui.events.trackable.UsernameSelectionNeededEvent;
import delit.piwigoclient.ui.permissions.groups.GroupFragment;
import delit.piwigoclient.ui.permissions.groups.GroupSelectFragment;
import delit.piwigoclient.ui.permissions.groups.GroupsListFragment;
import delit.piwigoclient.ui.permissions.users.UserFragment;
import delit.piwigoclient.ui.permissions.users.UsernameSelectFragment;
import delit.piwigoclient.ui.permissions.users.UsersListFragment;
import delit.piwigoclient.ui.preferences.PreferencesFragment;
import delit.piwigoclient.ui.slideshow.AlbumVideoItemFragment;
import delit.piwigoclient.ui.slideshow.SlideshowFragment;
import hotchemi.android.rate.MyAppRate;

public abstract class AbstractMainActivity<T extends AbstractMainActivity<T>> extends MyActivity<T> implements ComponentCallbacks2 {

    private static final String STATE_CURRENT_ALBUM = "currentAlbum";
    private static final String STATE_BASKET = "basket";
    private static final String TAG = "mainActivity";
    private static final int FILE_SELECTION_INTENT_REQUEST = 10101;
    private static final int OPEN_GOOGLE_PLAY_INTENT_REQUEST = 10102;
    // these fields are persisted.
    private CategoryItem currentAlbum = CategoryItem.ROOT_ALBUM;
    private String onLoginActionMethodName = null;
    private ArrayList<Serializable> onLoginActionParams = new ArrayList<>();
    private Basket basket = new Basket();
    private Toolbar toolbar;
    private AppBarLayout appBar;

    public static void performNoBackStackTransaction(final FragmentManager fragmentManager, String tag, Fragment fragment) {
        final int newBackStackLength = fragmentManager.getBackStackEntryCount() + 1;

        fragmentManager.beginTransaction()
                .replace(R.id.main_view, fragment, tag)
                .addToBackStack(tag)
                .commit();

        fragmentManager.addOnBackStackChangedListener(new FragmentManager.OnBackStackChangedListener() {
            @Override
            public void onBackStackChanged() {
                int nowCount = fragmentManager.getBackStackEntryCount();
                if (newBackStackLength != nowCount) {
                    // we don't really care if going back or forward. we already performed the logic here.
                    fragmentManager.removeOnBackStackChangedListener(this);

                    if (newBackStackLength > nowCount) { // user pressed back
                        fragmentManager.popBackStackImmediate();
                    }
                }
            }
        });
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        LoaderManager.getInstance(this).getLoader(0);
        outState.putParcelable(STATE_CURRENT_ALBUM, currentAlbum);
        outState.putParcelable(STATE_BASKET, basket);
//        if(BuildConfig.DEBUG) {
//            getSupportFragmentManager().enableDebugLogging(true);
//        }
        super.onSaveInstanceState(outState);
        if(BuildConfig.DEBUG) {
//            getSupportFragmentManager().enableDebugLogging(false);
            BundleUtils.logSizeVerbose("Current Main Activity", outState);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null) {
            currentAlbum = savedInstanceState.getParcelable(STATE_CURRENT_ALBUM);
            basket = savedInstanceState.getParcelable(STATE_BASKET);
        }

        setContentView(R.layout.activity_main);
        toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        appBar = findViewById(R.id.appbar);
        /*
        Floating action button (all screens!) - if wanted

        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(pkg View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Snackbar.makeSnackbar(view, "Replace with your own action", Snackbar.LENGTH_LONG)
                        .setAction("Action", null).show();
            }
        });*/

        DrawerLayout drawer = findViewById(R.id.drawer_layout);

        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawer, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        drawer.addDrawerListener(toggle);
        toggle.syncState();

        if (!hasAgreedToEula()) {
            drawer.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED);
        } else {
            drawer.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED);
        }

        if (savedInstanceState == null) {
            if (!hasAgreedToEula()) {
                showEula();
            } else if (ConnectionPreferences.getActiveProfile().getTrimmedNonNullPiwigoServerAddress(prefs, getApplicationContext()).isEmpty()) {
                showPreferences();
            } else {
                showGallery(currentAlbum);
            }
        }

        configureAndShowRateAppReminderIfNeeded();
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
    public void onResume() {
        super.onResume();
        GoogleApiAvailability googleApi = GoogleApiAvailability.getInstance();
        int result = googleApi.isGooglePlayServicesAvailable(getApplicationContext());
        Dialog dialog = googleApi.getErrorDialog(this, result, OPEN_GOOGLE_PLAY_INTENT_REQUEST);
        if(dialog != null) {
            dialog.show();
        }
    }

    @Override
    public void onBackPressed() {

        int backstackCount = getSupportFragmentManager().getBackStackEntryCount();

        boolean preferencesShowing;
        Fragment myFragment = getSupportFragmentManager().findFragmentByTag(PreferencesFragment.class.getName());
        preferencesShowing = myFragment != null && myFragment.isVisible();

        if (!hasAgreedToEula() && !preferencesShowing) {
            // exit immediately.
            finish();
            return;
        }

        DrawerLayout drawer = findViewById(R.id.drawer_layout);
        if (drawer.isDrawerOpen(GravityCompat.START)) {
            drawer.closeDrawer(GravityCompat.START);
        } else if (preferencesShowing) {

            ConnectionPreferences.ProfilePreferences connectionPrefs = ConnectionPreferences.getActiveProfile();

            if (!"".equals(connectionPrefs.getTrimmedNonNullPiwigoServerAddress(prefs, getApplicationContext()))) {
                doDefaultBackOperation();
            } else {
                // pop the current fragment off, close app if it is the last one
                doDefaultBackOperation();
            }
        } else {
            // pop the current fragment off, close app if it is the last one
            doDefaultBackOperation();
        }
    }

    private void doDefaultBackOperation() {
        if (getSupportFragmentManager().getBackStackEntryCount() > 0) {
            // pop the current fragment off
            getSupportFragmentManager().popBackStack();
            // get the next fragment
            int i = getSupportFragmentManager().getBackStackEntryCount() - 2;
            // if there are no fragments left, do default back operation (i.e. close app)
            if (i < 0) {
                super.onBackPressed();
            }
        } else {
            super.onBackPressed();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.piwigo_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (!hasAgreedToEula()) {
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

    private void showPreferences() {
//        Intent i = new Intent(this, PreferencesActivity.class);
//        startActivity(i);
        PreferencesFragment fragment = new PreferencesFragment();
        showFragmentNow(fragment);
    }

    private void showEula() {
        showFragmentNow(EulaFragment.newInstance());
    }

    protected abstract void showFavorites();

    private void showGallery(final CategoryItem gallery) {
        if (CategoryItem.ROOT_ALBUM.equals(gallery)) {
            // check if we've shown any albums before. If so, pop everything off the stack.
            removeFragmentsFromHistory(ViewAlbumFragment.class, true);
        }
        showFragmentNow(ViewAlbumFragment.newInstance(gallery), true);
        AdsManager.getInstance().showAlbumBrowsingAdvertIfAppropriate();
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public final void onNavigationItemSelected(NavigationItemSelectEvent event) {
        // Handle navigation view item clicks here.
        int id = event.navigationitemSelected;

        switch (id) {
            case R.id.nav_upload:
                showUpload();
                break;
            case R.id.nav_groups:
                showGroups();
                break;
            case R.id.nav_tags:
                showTags();
                break;
            case R.id.nav_users:
                showUsers();
                break;
            case R.id.nav_top_tips:
                showTopTips();
                break;
            case R.id.nav_gallery:
                showGallery(CategoryItem.ROOT_ALBUM);
                break;
            case R.id.nav_favorites:
                showFavorites();
                break;
            case R.id.nav_about:
                showAboutFragment();
                break;
            case R.id.nav_oss_licences:
                showLicencesFragment();
                break;
            case R.id.nav_settings:
                showPreferences();
                break;
            case R.id.nav_eula:
                showEula();
                break;
            default:
                onNavigationItemSelected(event, id);
        }

        DrawerLayout drawer = findViewById(R.id.drawer_layout);
        drawer.closeDrawer(GravityCompat.START);
    }

    protected void onNavigationItemSelected(NavigationItemSelectEvent event, @IdRes int itemId) {
    }

    private void showTopTips() {
        TopTipsFragment fragment = TopTipsFragment.newInstance();
        showFragmentNow(fragment);
    }

    private void showAboutFragment() {
        AboutFragment fragment = AboutFragment.newInstance();
        showFragmentNow(fragment);
    }

    private void showLicencesFragment() {
        LicencesFragment fragment = LicencesFragment.newInstance();
        showFragmentNow(fragment);
    }

    private void showUpload() {
        try {
            startActivity(UploadActivity.buildIntent(this, currentAlbum.toStub()));
        } catch(ActivityNotFoundException e) {
            Crashlytics.logException(e);
        }
    }

    private void showGroups() {
        GroupsListFragment fragment = GroupsListFragment.newInstance();
        showFragmentNow(fragment);
    }

    public void setOnLoginActionMethodName(String onLoginActionMethodName) {
        this.onLoginActionMethodName = onLoginActionMethodName;
    }

    protected abstract void showTags();

    private void showAlbumPermissions(final ArrayList<CategoryItemStub> availableAlbums, final HashSet<Long> directAlbumPermissions, final HashSet<Long> indirectAlbumPermissions, boolean allowEdit, int actionId) {
        BaseRecyclerViewAdapterPreferences prefs = new BaseRecyclerViewAdapterPreferences().selectable(true, false);
        if (!allowEdit) {
            prefs.readonly();
        }
        delit.piwigoclient.ui.permissions.AlbumSelectFragment fragment = delit.piwigoclient.ui.permissions.AlbumSelectFragment.newInstance(availableAlbums, prefs, actionId, indirectAlbumPermissions, directAlbumPermissions);
        showFragmentNow(fragment);
    }

    private void showUsers() {
        showFragmentNow(UsersListFragment.newInstance());
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(AlbumSelectedEvent event) {
        currentAlbum = event.getAlbum();
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
            boolean showVideosInSlideshow = prefs.getBoolean(getString(R.string.preference_gallery_include_videos_in_slideshow_key), getResources().getBoolean(R.bool.preference_gallery_include_videos_in_slideshow_default));
            boolean allowVideoPlayback = prefs.getBoolean(getString(R.string.preference_gallery_enable_video_playback_key), getResources().getBoolean(R.bool.preference_gallery_enable_video_playback_default));
            if (selectedItem instanceof VideoResourceItem) {
                if (showVideosInSlideshow) {
                    newFragment = new SlideshowFragment();
                    newFragment.setArguments(SlideshowFragment.buildArgs(event.getModelType(), albumOpen, selectedItem));
                } else if (allowVideoPlayback) {
                    newFragment = new AlbumVideoItemFragment();
                    newFragment.setArguments(AlbumVideoItemFragment.buildStandaloneArgs(event.getModelType(), albumOpen.getId(), selectedItem.getId(), 1, 1, 1, true));
                    ((AlbumVideoItemFragment) newFragment).onPageSelected();
                }
            } else if (selectedItem instanceof PictureResourceItem) {
                newFragment = new SlideshowFragment();
                newFragment.setArguments(SlideshowFragment.buildArgs(event.getModelType(), albumOpen, selectedItem));
            }
        }

        if (newFragment != null) {
            showFragmentNow(newFragment);
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN_ORDERED)
    public void onEvent(final AlbumCreateNeededEvent event) {

        CreateAlbumFragment fragment = CreateAlbumFragment.newInstance(event.getActionId(), event.getParentAlbum());
        showFragmentNow(fragment);
    }

    @Subscribe(threadMode = ThreadMode.MAIN_ORDERED)
    public void onEvent(FileSelectionNeededEvent event) {
        Intent intent = new Intent(this, FileSelectActivity.class);
        intent.putExtra(FileSelectActivity.INTENT_DATA, event);
        setTrackedIntent(event.getActionId(), FILE_SELECTION_INTENT_REQUEST);
        startActivityForResult(intent, event.getActionId());
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

            DisplayUtils.hideAndroidStatusBar(this);
            Crashlytics.log(Log.ERROR, TAG, "hiding status bar!");
        } else {
            Crashlytics.log(Log.ERROR, TAG, "showing status bar!");
        }

        if (v != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH) {
                v.requestApplyInsets();
            } else {
                v.requestFitSystemWindows();
            }
        }
        EventBus.getDefault().post(new StatusBarChangeEvent(!hasFocus));
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {

        if (getTrackedIntentType(requestCode) == FILE_SELECTION_INTENT_REQUEST) {
            if (resultCode == RESULT_OK && data.getExtras() != null) {
//                int sourceEventId = data.getExtras().getInt(FileSelectActivity.INTENT_SOURCE_EVENT_ID);
                long actionTimeMillis = data.getExtras().getLong(FileSelectActivity.ACTION_TIME_MILLIS);
                ArrayList<File> filesForUpload = BundleUtils.getFileArrayList(data.getExtras(), FileSelectActivity.INTENT_SELECTED_FILES);
                FileSelectionCompleteEvent event = new FileSelectionCompleteEvent(requestCode, filesForUpload, actionTimeMillis);
                EventBus.getDefault().postSticky(event);
            }
        } else {
            super.onActivityResult(requestCode, resultCode, data);
        }
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
        BaseRecyclerViewAdapterPreferences prefs = new BaseRecyclerViewAdapterPreferences().selectable(event.isAllowMultiSelect(), event.isInitialSelectionLocked());
        if (!event.isAllowEditing()) {
            prefs.readonly();
        }
        UsernameSelectFragment fragment = UsernameSelectFragment.newInstance(prefs, event.getActionId(), event.getIndirectSelection(), event.getInitialSelection());
        showFragmentNow(fragment);
    }

    @Subscribe(threadMode = ThreadMode.MAIN_ORDERED)
    public void onEvent(ViewUserEvent event) {
        UserFragment fragment = UserFragment.newInstance(event.getUser());
        showFragmentNow(fragment);
    }

    private void showAlbumSelectionFragment(int actionId, AvailableAlbumsListAdapter.AvailableAlbumsListAdapterPreferences prefs, HashSet<Long> currentSelection) {
        AlbumSelectFragment fragment = AlbumSelectFragment.newInstance(prefs, actionId, currentSelection);
        showFragmentNow(fragment);
    }

    protected void showFragmentNow(Fragment f) {
        showFragmentNow(f, false);
    }

    private void showFragmentNow(Fragment f, boolean addDuplicatePreviousToBackstack) {

        Crashlytics.log(Log.DEBUG, TAG, String.format("showing fragment: %1$s (%2$s)", f.getTag(), f.getClass().getName()));
        checkLicenceIfNeeded();

        DisplayUtils.hideKeyboardFrom(getApplicationContext(), getWindow());

        Fragment lastFragment = getSupportFragmentManager().findFragmentById(R.id.main_view);
        String lastFragmentName = "";
        if (lastFragment != null) {
            lastFragmentName = lastFragment.getTag();
        }
        if (!addDuplicatePreviousToBackstack && f.getClass().getName().equals(lastFragmentName)) {
            getSupportFragmentManager().popBackStackImmediate();
        }
        //TODO I've added code that clears stack when showing root album... is this "good enough"?
        //TODO - trying to prevent adding duplicates here. not sure it works right.
//        TODO maybe should be using current fragment classname when adding to backstack rather than one being replaced... hmmmm
        FragmentTransaction tx = getSupportFragmentManager().beginTransaction();
        tx.addToBackStack(f.getClass().getName());
        tx.replace(R.id.main_view, f, f.getClass().getName()).commit();
        Crashlytics.log(Log.DEBUG, TAG, "replaced existing fragment with new: " + f.getClass().getName());
    }

    @Subscribe(threadMode = ThreadMode.MAIN_ORDERED)
    public void onEvent(ViewGroupEvent event) {
        GroupFragment fragment = GroupFragment.newInstance(event.getGroup());
        showFragmentNow(fragment);
    }

    @Subscribe(threadMode = ThreadMode.MAIN_ORDERED)
    public void onEvent(final GroupSelectionNeededEvent event) {
        BaseRecyclerViewAdapterPreferences prefs = new BaseRecyclerViewAdapterPreferences().selectable(event.isAllowMultiSelect(), event.isInitialSelectionLocked());
        if (!event.isAllowEditing()) {
            prefs.readonly();
        }
        GroupSelectFragment fragment = GroupSelectFragment.newInstance(prefs, event.getActionId(), event.getInitialSelection());
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
            showGallery(CategoryItem.ROOT_ALBUM);
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

    private boolean invokeStoredActionIfAvailableOnClass(Class c) {

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
                        Crashlytics.logException(e);
                        throw new RuntimeException("Error running post login action ", e);
                    } catch (InvocationTargetException e) {
                        Crashlytics.logException(e);
                        throw new RuntimeException("Error running post login action ", e);
                    }
                }
            }
        }
        return invoked;
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(ToolbarEvent event) {
        toolbar.setTitle(event.getTitle());
        if(event.isExpandToolbarView()) {
            appBar.setExpanded(true, event.getTitle()!= null);
        } else if(event.isContractToolbarView()) {
            appBar.setExpanded(false, event.getTitle()!= null);
        }
        appBar.setEnabled(event.getTitle()!= null);
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(ThemeAlteredEvent event) {
        CustomNavigationView navigationView = findViewById(R.id.nav_view);
        navigationView.updateTheme();
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(PiwigoLoginSuccessEvent event) {

        PiwigoSessionDetails sessionDetails = PiwigoSessionDetails.getInstance(ConnectionPreferences.getActiveProfile());
        Crashlytics.setString("ServerVersion", sessionDetails.getPiwigoVersion() /* string value */);


        CustomNavigationView navigationView = findViewById(R.id.nav_view);
        navigationView.setMenuVisibilityToMatchSessionState();
        if (event.isChangePage() && !invokeStoredActionIfAvailable()) {
            // If nothing specified, show the root gallery.
            showGallery(CategoryItem.ROOT_ALBUM);
        } else {
            //TODO notify all pages that need it that they need to be reloaded - i.e. flush them out of the fragment manager or send an event forcing reload.
        }
        AdsManager.getInstance().updateShowAdvertsSetting(getApplicationContext());
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
        getSupportFragmentManager().popBackStackImmediate();
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(AlbumCreatedEvent event) {
        getSupportFragmentManager().popBackStackImmediate();
    }

    @Subscribe(threadMode = ThreadMode.MAIN_ORDERED)
    public void onEvent(AlbumSelectionNeededEvent event) {
        AvailableAlbumsListAdapter.AvailableAlbumsListAdapterPreferences prefs = new AvailableAlbumsListAdapter.AvailableAlbumsListAdapterPreferences();
        prefs.selectable(event.isAllowMultiSelect(), event.isInitialSelectionLocked());
        prefs.withShowHierachy();
        if (!event.isAllowEditing()) {
            prefs.readonly();
        }
        showAlbumSelectionFragment(event.getActionId(), prefs, event.getInitialSelection());
    }

    @Subscribe(threadMode = ThreadMode.MAIN_ORDERED)
    public void onEvent(ExpandingAlbumSelectionNeededEvent event) {
//        ExpandableAlbumsListAdapter.ExpandableAlbumsListAdapterPreferences prefs = new ExpandableAlbumsListAdapter.ExpandableAlbumsListAdapterPreferences();
//        AlbumSelectExpandableFragment f = AlbumSelectExpandableFragment.newInstance(prefs, event.getActionId(), event.getInitialSelection());
        CategoryItemViewAdapterPreferences prefs = new CategoryItemViewAdapterPreferences();
        if(event.isAllowEditing()) {
            prefs.selectable(event.isAllowMultiSelect(), event.isInitialSelectionLocked());
        }
        if(event.getInitialRoot() != null) {
            prefs.withInitialRoot(new CategoryItemStub("???", event.getInitialRoot()));
        } else {
            prefs.withInitialRoot(CategoryItemStub.ROOT_GALLERY);
        }
        prefs.setAllowItemAddition(true);
        prefs.withInitialSelection(event.getInitialSelection());
        RecyclerViewCategoryItemSelectFragment f = RecyclerViewCategoryItemSelectFragment.newInstance(prefs, event.getActionId());
        showFragmentNow(f);
    }

}
