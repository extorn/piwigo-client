package delit.piwigoclient.ui;

import android.content.ComponentCallbacks2;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.io.File;
import java.io.Serializable;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;

import delit.piwigoclient.R;
import delit.piwigoclient.model.piwigo.Basket;
import delit.piwigoclient.model.piwigo.CategoryItem;
import delit.piwigoclient.model.piwigo.CategoryItemStub;
import delit.piwigoclient.model.piwigo.GalleryItem;
import delit.piwigoclient.model.piwigo.PictureResourceItem;
import delit.piwigoclient.model.piwigo.PiwigoAlbum;
import delit.piwigoclient.model.piwigo.PiwigoSessionDetails;
import delit.piwigoclient.model.piwigo.VersionCompatability;
import delit.piwigoclient.model.piwigo.VideoResourceItem;
import delit.piwigoclient.ui.album.AlbumSelectFragment;
import delit.piwigoclient.ui.album.create.CreateAlbumFragment;
import delit.piwigoclient.ui.album.view.ViewAlbumFragment;
import delit.piwigoclient.ui.common.MyActivity;
import delit.piwigoclient.ui.events.AlbumAlteredEvent;
import delit.piwigoclient.ui.events.AlbumDeletedEvent;
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
import delit.piwigoclient.ui.events.ThemeAlteredEvent;
import delit.piwigoclient.ui.events.ViewGroupEvent;
import delit.piwigoclient.ui.events.ViewUserEvent;
import delit.piwigoclient.ui.events.trackable.AlbumCreateNeededEvent;
import delit.piwigoclient.ui.events.trackable.AlbumCreatedEvent;
import delit.piwigoclient.ui.events.trackable.AlbumPermissionsSelectionNeededEvent;
import delit.piwigoclient.ui.events.trackable.AlbumSelectionNeededEvent;
import delit.piwigoclient.ui.events.trackable.FileListSelectionCompleteEvent;
import delit.piwigoclient.ui.events.trackable.FileListSelectionNeededEvent;
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
import delit.piwigoclient.ui.upload.UploadFragment;
import hotchemi.android.rate.MyAppRate;
import paul.arian.fileselector.FileSelectionActivity;

public class MainActivity extends MyActivity implements ComponentCallbacks2 {

    private static final String STATE_CURRENT_ALBUM = "currentAlbum";
    private static final String STATE_BASKET = "basket";
    private static final String ON_LOGIN_ACTION_METHOD_NAME = "onLoginActionMethodName";
    private static final String ON_LOGIN_ACTION_METHOD_PARAMS = "onLoginActionMethodParams";
    private static final String TAG = "mainActivity";
    private static final int FILE_SELECTION_INTENT_REQUEST = 10101;
    // these fields are persisted.
    private CategoryItem currentAlbum = PiwigoAlbum.ROOT_ALBUM;
    private String onLoginActionMethodName = null;
    private ArrayList<Serializable> onLoginActionParams = new ArrayList<Serializable>();
    private Basket basket = new Basket();

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putSerializable(STATE_CURRENT_ALBUM, currentAlbum);
        outState.putSerializable(STATE_BASKET, basket);
        outState.putString(ON_LOGIN_ACTION_METHOD_NAME, onLoginActionMethodName);
        outState.putSerializable(ON_LOGIN_ACTION_METHOD_PARAMS, onLoginActionParams);
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (savedInstanceState != null) {
            currentAlbum = (CategoryItem) savedInstanceState.getSerializable(STATE_CURRENT_ALBUM);
            onLoginActionMethodName = savedInstanceState.getString(ON_LOGIN_ACTION_METHOD_NAME);
            onLoginActionParams = (ArrayList<Serializable>) savedInstanceState.getSerializable(ON_LOGIN_ACTION_METHOD_PARAMS);
            basket = (Basket) savedInstanceState.getSerializable(STATE_BASKET);
        }

        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        toolbar.setVisibility(prefs.getBoolean(getString(R.string.preference_app_show_toolbar_key), true) ? View.VISIBLE : View.GONE);

        /*
        Floating action button (all screens!) - if wanted

        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(pkg View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
                        .setAction("Action", null).show();
            }
        });*/

        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawer, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        drawer.setDrawerListener(toggle);
        toggle.syncState();

        if(!hasAgreedToEula()) {
            drawer.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED);
        } else {
            drawer.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED);
        }

        if (savedInstanceState == null) {
            if (!hasAgreedToEula()) {
                showEula();
            } else if (prefs.getString(getApplicationContext().getString(R.string.preference_piwigo_server_address_key), "").isEmpty()) {
                showPreferences();
            } else {
                showGallery(currentAlbum);
            }
        }

        configureAndShowRateAppReminderIfNeeded();
    }

    private void configureAndShowRateAppReminderIfNeeded() {
        MyAppRate.with(this)
                .setInstallDays(10) // default 10, 0 means install day.
                .setLaunchTimes(10) // default 10
                .setRemindInterval(30) // default 1
                .setShowLaterButton(true) // default true
                .setDebug(false) // default false
                .monitor();

        // Show a dialog if meets conditions
        MyAppRate.showRateDialogIfMeetsConditions(this);
    }

    public Basket getBasket() {
        return basket;
    }

    @Override
    public void onBackPressed() {

        int backstackCount = getSupportFragmentManager().getBackStackEntryCount();

        if (!hasAgreedToEula()) {
            // exit immediately.
            finish();
            return;
        }

        boolean preferencesShowing = false;
        Fragment myFragment = (Fragment)getSupportFragmentManager().findFragmentByTag(PreferencesFragment.class.getName());
        preferencesShowing = myFragment != null && myFragment.isVisible();

        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        if (drawer.isDrawerOpen(GravityCompat.START)) {
            drawer.closeDrawer(GravityCompat.START);
        } else if (preferencesShowing) {

            Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
            toolbar.setVisibility(prefs.getBoolean(getString(R.string.preference_app_show_toolbar_key), true) ? View.VISIBLE : View.GONE);

            if(!"".equals(prefs.getString(getString(R.string.preference_piwigo_server_address_key), "").trim())) {
                // Can and need to login to the server, so lets do that.
                boolean haveBeenLoggedIn = null != getSupportFragmentManager().findFragmentByTag(LoginFragment.class.getName());

                if(haveBeenLoggedIn && !PiwigoSessionDetails.isFullyLoggedIn()) {

                    // clear the backstack - its for an old session (clear stack back to first session login).
                    for(int i = 0; i < getSupportFragmentManager().getBackStackEntryCount(); i++) {
                        FragmentManager.BackStackEntry entry = getSupportFragmentManager().getBackStackEntryAt(i);
                        if(LoginFragment.class.getName().equals(entry.getName())) {
                            int popToFragmentId = entry.getId();
                            getSupportFragmentManager().popBackStack(popToFragmentId, FragmentManager.POP_BACK_STACK_INCLUSIVE);
                            break;
                        }
                    }
                    // we have been logged in before, and are now not logged in - need a new session.
                    showGallery(PiwigoAlbum.ROOT_ALBUM);
                } else {
                    // pop the current fragment off, close app if it is the last one
                    doDefaultBackOperation();
                }
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
        if(getSupportFragmentManager().getBackStackEntryCount() > 0) {
            // pop the current fragment off
            getSupportFragmentManager().popBackStack();
            // get the next fragment
            int i = getSupportFragmentManager().getBackStackEntryCount() - 2;
            if(i >= 0) {
                FragmentManager.BackStackEntry entry = getSupportFragmentManager().getBackStackEntryAt(i);
                // while we have another fragment in history and the next fragment is a Login fragment, pop it off and go to the previous one.
                while (LoginFragment.class.getName().equals(entry.getName()) && i >= 0) {
                    getSupportFragmentManager().popBackStack();
                    i--;
                    if (i >= 0) {
                        entry = getSupportFragmentManager().getBackStackEntryAt(i);
                    }
                }
            }
            // if there are no fragments left, do default back operation (i.e. close app)
            if(i < 0) {
                super.onBackPressed();
            }
        } else {
            super.onBackPressed();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        if (id == R.id.action_settings) {
            if (showPreferences()) {
                return true;
            }
        }

        return super.onOptionsItemSelected(item);
    }

    private boolean showPreferences() {
        PreferencesFragment fragment = new PreferencesFragment();
        showFragmentNow(fragment);
        return true;
    }

    private void showEula() {
        showFragmentNow(EulaFragment.newInstance(this));
    }

    public boolean showGallery(final CategoryItem gallery) {
        if (!PiwigoSessionDetails.isFullyLoggedIn()) {
            onLoginActionMethodName = "showGallery";
            onLoginActionParams.clear();
            onLoginActionParams.add(gallery);
            showLoginFragment();
        } else {
            if(PiwigoAlbum.ROOT_ALBUM.equals(gallery)) {
                // check if we've shown any albums before. If so, pop everything off the stack.
                boolean found = false;
                int i = 0;
                while(!found && getSupportFragmentManager().getBackStackEntryCount() > i) {
                    FragmentManager.BackStackEntry entry = getSupportFragmentManager().getBackStackEntryAt(i);
                    if(ViewAlbumFragment.class.getName().equals(entry.getName())) {
                        found = true;
                        if(i > 0) {
                            // if the previous item was a login action - force that off the stack too.
                            entry = getSupportFragmentManager().getBackStackEntryAt(i - 1);
                            if(LoginFragment.class.getName().equals(entry.getName())) {
                                i--;
                            }
                        }
                    } else {
                        i++;
                    }
                }
                if(found) {
                    getSupportFragmentManager().popBackStack(i, FragmentManager.POP_BACK_STACK_INCLUSIVE);
                }
            }
            showFragmentNow(ViewAlbumFragment.newInstance(gallery), true);
        }
        AdsManager.getInstance().showAlbumBrowsingAdvertIfAppropriate();
        return true;
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onNavigationItemSelected(NavigationItemSelectEvent event) {
        // Handle navigation view item clicks here.
        int id = event.navigationitemSelected;

        switch (id) {
            case R.id.nav_upload:
                showUpload();
                break;
            case R.id.nav_groups:
                showGroups();
                break;
            case R.id.nav_users:
                showUsers();
                break;
            case R.id.nav_top_tips:
                showTopTips();
                break;
            case R.id.nav_gallery:
                showGallery(PiwigoAlbum.ROOT_ALBUM);
                break;
            case R.id.nav_about:
                showAboutFragment();
                break;
            case R.id.nav_licences:
                showLicencesFragment();
                break;
            case R.id.nav_settings:
                showPreferences();
                break;
            case R.id.nav_eula:
                showEula();
                break;
            default:
        }

        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        drawer.closeDrawer(GravityCompat.START);
    }

    private void showLoginFragment() {
        LoginFragment fragment = LoginFragment.newInstance();
        showFragmentNow(fragment);
    }

    private void showTopTips() {
        TopTipsFragment fragment = TopTipsFragment.newInstance(this);
        showFragmentNow(fragment);
    }

    private boolean showAboutFragment() {
        AboutFragment fragment = AboutFragment.newInstance(this);
        showFragmentNow(fragment);
        return true;
    }

    private boolean showLicencesFragment() {
        LicencesFragment fragment = LicencesFragment.newInstance(this);
        showFragmentNow(fragment);
        return true;
    }

    private boolean showUpload() {
        if (!PiwigoSessionDetails.isFullyLoggedIn()) {
            onLoginActionMethodName = "showUpload";
            showLoginFragment();
        } else {
            UploadFragment fragment = UploadFragment.newInstance(currentAlbum);
            showFragmentNow(fragment);
        }
        return true;
    }

    private boolean showGroups() {
        if (!PiwigoSessionDetails.isFullyLoggedIn()) {
            onLoginActionMethodName = "showGroups";
            showLoginFragment();
        } else {
            GroupsListFragment fragment = GroupsListFragment.newInstance();
            showFragmentNow(fragment);
        }
        return true;
    }


    private boolean showAlbumPermissions(final ArrayList<CategoryItemStub> availableAlbums, final HashSet<Long> directAlbumPermissions, final HashSet<Long> indirectAlbumPermissions, boolean allowEdit, int actionId) {
        if (!PiwigoSessionDetails.isFullyLoggedIn()) {
            onLoginActionMethodName = "showAlbumPermissions";
            onLoginActionParams.clear();
            onLoginActionParams.add(availableAlbums);
            onLoginActionParams.add(directAlbumPermissions);
            onLoginActionParams.add(indirectAlbumPermissions);
            onLoginActionParams.add(allowEdit);
            onLoginActionParams.add(actionId);
            showLoginFragment();
        } else {
            delit.piwigoclient.ui.permissions.AlbumSelectFragment fragment = delit.piwigoclient.ui.permissions.AlbumSelectFragment.newInstance(availableAlbums, true, allowEdit, actionId, indirectAlbumPermissions, directAlbumPermissions);
            showFragmentNow(fragment);
        }
        return true;
    }

    private boolean showUsers() {
        if (!PiwigoSessionDetails.isFullyLoggedIn()) {
            onLoginActionMethodName = "showUsers";
            showLoginFragment();
        } else {
            showFragmentNow(UsersListFragment.newInstance());
        }
        return true;
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(AlbumSelectedEvent event) {
        currentAlbum = event.getAlbum();
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(AlbumDeletedEvent event) {
        for(Long itemParent : event.getItem().getParentageChain()) {
            EventBus.getDefault().post(new AlbumAlteredEvent(itemParent));
        }
        getSupportFragmentManager().popBackStackImmediate();
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(AlbumItemSelectedEvent event) {

        Fragment newFragment = null;

        PiwigoAlbum albumOpen = event.getAlbum();
        GalleryItem selectedItem = event.getSelectedItem();

        if (selectedItem instanceof CategoryItem) {
            currentAlbum = (CategoryItem) selectedItem;
            showGallery(currentAlbum);
        } else {
            boolean showVideosInSlideshow = prefs.getBoolean(getString(R.string.preference_gallery_include_videos_in_slideshow_key), getResources().getBoolean(R.bool.preference_gallery_include_videos_in_slideshow_default));
            boolean allowVideoPlayback = prefs.getBoolean(getString(R.string.preference_gallery_enable_video_playback_key), getResources().getBoolean(R.bool.preference_gallery_enable_video_playback_default));
            if (selectedItem instanceof VideoResourceItem) {
                if (showVideosInSlideshow) {
                    newFragment = SlideshowFragment.newInstance(albumOpen, selectedItem);
                } else if (allowVideoPlayback) {
                    boolean startOnResume = true;
                    newFragment = AlbumVideoItemFragment.newInstance((VideoResourceItem) selectedItem, startOnResume);
                }
            }
            if (selectedItem instanceof PictureResourceItem) {
                newFragment = SlideshowFragment.newInstance(albumOpen, selectedItem);
            }
        }

        if (newFragment != null) {
            showFragmentNow(newFragment);
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(final AlbumCreateNeededEvent event) {

        CreateAlbumFragment fragment = CreateAlbumFragment.newInstance(event.getActionId(), event.getParentAlbum());
        showFragmentNow(fragment);
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(FileListSelectionNeededEvent event) {
        Intent intent = new Intent(getBaseContext(), FileSelectionActivity.class);
        intent.putStringArrayListExtra(FileSelectionActivity.ARG_ALLOWED_FILE_TYPES, event.getAllowedFileTypes());
        intent.putExtra(FileSelectionActivity.ARG_SORT_A_TO_Z, event.isUseAlphabeticalSortOrder());
        setTrackedIntent(event.getActionId(), FILE_SELECTION_INTENT_REQUEST);
        startActivityForResult(intent, event.getActionId());
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {

        if (getTrackedIntentType(requestCode) == FILE_SELECTION_INTENT_REQUEST) {
            if (resultCode == RESULT_OK) {
                ArrayList<File> filesForUpload = (ArrayList<File>) data.getExtras().get(FileSelectionActivity.SELECTED_FILES);
                FileListSelectionCompleteEvent event = new FileListSelectionCompleteEvent(requestCode, filesForUpload);
                EventBus.getDefault().post(event);
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

                PicassoFactory.getInstance().clearPicassoCache(getApplicationContext());
                EventBus.getDefault().post(new MemoryTrimmedRunningAppEvent());

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

                PicassoFactory.getInstance().clearPicassoCache(getApplicationContext());
                EventBus.getDefault().post(new MemoryTrimmedEvent());
                /*
                   Release as much memory as the process can.

                   The app is on the LRU list and the system is running low on memory.
                   The event raised indicates where the app sits within the LRU list.
                   If the event is TRIM_MEMORY_COMPLETE, the process will be one of
                   the first to be terminated.
                */

                break;

            default:

                PicassoFactory.getInstance().clearPicassoCache(getApplicationContext());
                EventBus.getDefault().post(new GenericLowMemoryEvent());

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
        showLowMemoryWarningMessage(R.string.alert_warning_lowMemory_message);
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(MemoryTrimmedRunningAppEvent event) {
        showLowMemoryWarningMessage(R.string.alert_warning_lowMemory_message);
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(MemoryTrimmedEvent event) {
        showLowMemoryWarningMessage(R.string.alert_warning_lowMemory_message);
    }

    private void showLowMemoryWarningMessage(int messageId) {
        getUiHelper().showOrQueueDialogMessage(R.string.alert_warning_lowMemory, getString(messageId), R.string.button_close);
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(final UsernameSelectionNeededEvent event) {
        UsernameSelectFragment fragment = UsernameSelectFragment.newInstance(event.isAllowMultiSelect(), event.isAllowEditing(), event.getActionId(), event.getIndirectSelection(), event.getCurrentSelection());
        showFragmentNow(fragment);
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(ViewUserEvent event) {
        UserFragment fragment = UserFragment.newInstance(event.getUser());
        showFragmentNow(fragment);
    }

    private void showAlbumSelectionFragment(int actionId, boolean allowMultiSelect, boolean allowEditing, HashSet<Long> currentSelection) {
        AlbumSelectFragment fragment = AlbumSelectFragment.newInstance(allowMultiSelect, allowEditing, actionId, currentSelection);
        showFragmentNow(fragment);
    }

    private void showFragmentNow(Fragment f) {
        showFragmentNow(f, false);
    }

    private void showFragmentNow(Fragment f, boolean addDuplicatePreviousToBackstack) {

        Fragment lastFragment = getSupportFragmentManager().findFragmentById(R.id.main_view);
        String lastFragmentName = "";
        if(lastFragment != null) {
            lastFragmentName = lastFragment.getTag();
        }
        if(!addDuplicatePreviousToBackstack && f.getClass().getName().equals(lastFragmentName)) {
            getSupportFragmentManager().popBackStackImmediate();
        }
        //TODO I've added code that clears stack when showing root album... is this "good enough"?
        //TODO - trying to prevent adding duplicates here. not sure it works right.
//        TODO maybe should be using current fragment classname when adding to backstack rather than one being replaced... hmmmm
        FragmentTransaction tx = getSupportFragmentManager().beginTransaction();
        tx.addToBackStack(f.getClass().getName());
        tx.replace(R.id.main_view, f, f.getClass().getName()).commit();
    }

    public static void performNoBackStackTransaction(final FragmentManager fragmentManager, String tag, Fragment fragment) {
        final int newBackStackLength = fragmentManager.getBackStackEntryCount() +1;

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

                    if ( newBackStackLength > nowCount ) { // user pressed back
                        fragmentManager.popBackStackImmediate();
                    }
                }
            }
        });
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(ViewGroupEvent event) {
        GroupFragment fragment = GroupFragment.newInstance(event.getGroup());
        showFragmentNow(fragment);
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(final GroupSelectionNeededEvent event) {
        GroupSelectFragment fragment = GroupSelectFragment.newInstance(event.isAllowMultiSelect(), event.isAllowEditing(), event.getActionId(), event.getCurrentSelection());
        showFragmentNow(fragment);
    }

    @Override
    public void onStart() {
        super.onStart();
        EventBus.getDefault().register(this);
    }

    @Override
    public void onStop() {
        EventBus.getDefault().unregister(this);
        super.onStop();
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(EulaNotAgreedEvent event) {
        // exit the app now.
        finishAffinity();
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(EulaAgreedEvent event) {
        // unlock the drawer.
        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        drawer.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED);

        if (prefs.getString(getApplicationContext().getString(R.string.preference_piwigo_server_address_key), "").trim().isEmpty()) {
            showPreferences();
        } else {
            showGallery(PiwigoAlbum.ROOT_ALBUM);
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(AlbumPermissionsSelectionNeededEvent event) {
        showAlbumPermissions(event.getAvailableAlbums(), event.getDirectAlbumPermissions(), event.getIndirectAlbumPermissions(), event.isAllowEdit(), event.getActionId());
    }

    private boolean invokeStoredActionIfAvailable() {

        boolean actionAvailable = onLoginActionMethodName != null;
        if (actionAvailable) {
            Method[] methods = MainActivity.class.getDeclaredMethods();
            for(Method m : methods) {
                if(m.getName().equals(onLoginActionMethodName)) {
                    onLoginActionMethodName = null;
                    try {
                        Object[] params = onLoginActionParams.toArray();
                        onLoginActionParams.clear();
                        m.invoke(this, params);
                        break;
                    } catch (IllegalAccessException e) {
                        throw new RuntimeException("Error running post login action ",e );
                    } catch (InvocationTargetException e) {
                        throw new RuntimeException("Error running post login action ",e );
                    }
                }
            }
        }
        return actionAvailable;
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(ThemeAlteredEvent event) {
        CustomNavigationView navigationView = (CustomNavigationView) findViewById(R.id.nav_view);
        navigationView.updateTheme();
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(PiwigoLoginSuccessEvent event) {
        CustomNavigationView navigationView = (CustomNavigationView) findViewById(R.id.nav_view);
        navigationView.setMenuVisibilityToMatchSessionState();
        if (event.isChangePage() && !invokeStoredActionIfAvailable()) {
            // If nothing specified, show the root gallery.
            showGallery(PiwigoAlbum.ROOT_ALBUM);
            AdsManager.getInstance().updateShowAdvertsSetting();
        }
        VersionCompatability.INSTANCE.runTests();
        if (!VersionCompatability.INSTANCE.isSupportedVersion()) {
            String serverVersion = VersionCompatability.INSTANCE.getServerVersionString();
            String minimumVersion = VersionCompatability.INSTANCE.getMinimumTestedVersionString();
            getUiHelper().showOrQueueDialogMessage(R.string.alert_warning, String.format(getString(R.string.alert_error_unsupported_piwigo_version_pattern), serverVersion, minimumVersion));
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

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(AlbumSelectionNeededEvent event) {
        showAlbumSelectionFragment(event.getActionId(), event.isAllowMultiSelect(), event.isAllowEditing(), event.getCurrentSelection());
    }
}
