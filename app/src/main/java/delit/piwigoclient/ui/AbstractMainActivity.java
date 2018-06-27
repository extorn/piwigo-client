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
import delit.piwigoclient.business.ConnectionPreferences;
import delit.piwigoclient.model.piwigo.Basket;
import delit.piwigoclient.model.piwigo.CategoryItem;
import delit.piwigoclient.model.piwigo.CategoryItemStub;
import delit.piwigoclient.model.piwigo.GalleryItem;
import delit.piwigoclient.model.piwigo.PictureResourceItem;
import delit.piwigoclient.model.piwigo.PiwigoSessionDetails;
import delit.piwigoclient.model.piwigo.ResourceContainer;
import delit.piwigoclient.model.piwigo.VersionCompatability;
import delit.piwigoclient.model.piwigo.VideoResourceItem;
import delit.piwigoclient.ui.album.AlbumSelectFragment;
import delit.piwigoclient.ui.album.AvailableAlbumsListAdapter;
import delit.piwigoclient.ui.album.create.CreateAlbumFragment;
import delit.piwigoclient.ui.album.view.ViewAlbumFragment;
import delit.piwigoclient.ui.common.MyActivity;
import delit.piwigoclient.ui.common.recyclerview.BaseRecyclerViewAdapterPreferences;
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

public abstract class AbstractMainActivity extends MyActivity implements ComponentCallbacks2 {

    private static final String STATE_CURRENT_ALBUM = "currentAlbum";
    private static final String STATE_BASKET = "basket";
    private static final String TAG = "mainActivity";
    private static final int FILE_SELECTION_INTENT_REQUEST = 10101;
    // these fields are persisted.
    private CategoryItem currentAlbum = CategoryItem.ROOT_ALBUM;
    private String onLoginActionMethodName = null;
    private ArrayList<Serializable> onLoginActionParams = new ArrayList<>();
    private Basket basket = new Basket();

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putSerializable(STATE_CURRENT_ALBUM, currentAlbum);
        outState.putSerializable(STATE_BASKET, basket);
        super.onSaveInstanceState(outState);
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (savedInstanceState != null) {
            currentAlbum = (CategoryItem) savedInstanceState.getSerializable(STATE_CURRENT_ALBUM);
            basket = (Basket) savedInstanceState.getSerializable(STATE_BASKET);
        }

        setContentView(R.layout.activity_main);
        Toolbar toolbar = findViewById(R.id.toolbar);
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

        DrawerLayout drawer = findViewById(R.id.drawer_layout);
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawer, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        drawer.addDrawerListener(toggle);
        toggle.syncState();

        if(!hasAgreedToEula()) {
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
    public void onBackPressed() {

        int backstackCount = getSupportFragmentManager().getBackStackEntryCount();

        if (!hasAgreedToEula()) {
            // exit immediately.
            finish();
            return;
        }

        boolean preferencesShowing;
        Fragment myFragment = getSupportFragmentManager().findFragmentByTag(PreferencesFragment.class.getName());
        preferencesShowing = myFragment != null && myFragment.isVisible();

        DrawerLayout drawer = findViewById(R.id.drawer_layout);
        if (drawer.isDrawerOpen(GravityCompat.START)) {
            drawer.closeDrawer(GravityCompat.START);
        } else if (preferencesShowing) {

            Toolbar toolbar = findViewById(R.id.toolbar);
            toolbar.setVisibility(prefs.getBoolean(getString(R.string.preference_app_show_toolbar_key), true) ? View.VISIBLE : View.GONE);

            ConnectionPreferences.ProfilePreferences connectionPrefs = ConnectionPreferences.getActiveProfile();

            if(!"".equals(connectionPrefs.getTrimmedNonNullPiwigoServerAddress(prefs, getApplicationContext()))) {
                // Can and need to login to the server, so lets do that.
                boolean haveBeenLoggedIn = null != getSupportFragmentManager().findFragmentByTag(LoginFragment.class.getName());

                if(haveBeenLoggedIn && !PiwigoSessionDetails.isFullyLoggedIn(connectionPrefs)) {

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
                    showGallery(CategoryItem.ROOT_ALBUM);
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
        getMenuInflater().inflate(R.menu.piwigo_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
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
        PreferencesFragment fragment = new PreferencesFragment();
        showFragmentNow(fragment);
    }

    private void showEula() {
        showFragmentNow(EulaFragment.newInstance());
    }

    private void showGallery(final CategoryItem gallery) {
        if(CategoryItem.ROOT_ALBUM.equals(gallery)) {
            // check if we've shown any albums before. If so, pop everything off the stack.
            removeFragmentsFromHistory(ViewAlbumFragment.class, true);
        }
        showFragmentNow(ViewAlbumFragment.newInstance(gallery), true);
        AdsManager.getInstance().showAlbumBrowsingAdvertIfAppropriate();
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

        DrawerLayout drawer = findViewById(R.id.drawer_layout);
        drawer.closeDrawer(GravityCompat.START);
    }

    protected void showLoginFragment() {
        LoginFragment fragment = LoginFragment.newInstance();
        showFragmentNow(fragment);
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
        Intent intent = new Intent(Constants.ACTION_MANUAL_UPLOAD);
        intent.putExtra("galleryId", currentAlbum.getId());
        startActivity(intent);
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
        if(!allowEdit) {
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
    public void onEvent(AlbumDeletedEvent event) {
        for(Long itemParent : event.getItem().getParentageChain()) {
            EventBus.getDefault().post(new AlbumAlteredEvent(itemParent));
        }
        getSupportFragmentManager().popBackStackImmediate();
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(AlbumItemSelectedEvent event) {

        Fragment newFragment = null;

        ResourceContainer albumOpen = event.getResourceContainer();
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
                    newFragment.setArguments(SlideshowFragment.buildArgs(albumOpen, selectedItem));
                } else if (allowVideoPlayback) {
                    newFragment = new AlbumVideoItemFragment();
                    newFragment.setArguments(AlbumVideoItemFragment.buildArgs((VideoResourceItem) selectedItem, 1, 1, 1, true));
                }
            } else if (selectedItem instanceof PictureResourceItem) {
                newFragment = new SlideshowFragment();
                newFragment.setArguments(SlideshowFragment.buildArgs(albumOpen, selectedItem));
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
    public void onEvent(FileSelectionNeededEvent event) {
        Intent intent = new Intent(getBaseContext(), FileSelectActivity.class);
        intent.putExtra(FileSelectActivity.INTENT_DATA, event);
        setTrackedIntent(event.getActionId(), FILE_SELECTION_INTENT_REQUEST);
        startActivityForResult(intent, event.getActionId());
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {

        if (getTrackedIntentType(requestCode) == FILE_SELECTION_INTENT_REQUEST) {
            if (resultCode == RESULT_OK && data.getExtras() != null) {
                int sourceEventId = data.getExtras().getInt(FileSelectActivity.INTENT_SOURCE_EVENT_ID);
                ArrayList<File> filesForUpload = (ArrayList<File>) data.getExtras().get(FileSelectActivity.INTENT_SELECTED_FILES);
                FileSelectionCompleteEvent event = new FileSelectionCompleteEvent(requestCode, filesForUpload);
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
        BaseRecyclerViewAdapterPreferences prefs = new BaseRecyclerViewAdapterPreferences().selectable(event.isAllowMultiSelect(), event.isInitialSelectionLocked());
        if(!event.isAllowEditing()) {
            prefs.readonly();
        }
        UsernameSelectFragment fragment = UsernameSelectFragment.newInstance(prefs, event.getActionId(), event.getIndirectSelection(), event.getInitialSelection());
        showFragmentNow(fragment);
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
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

        checkLicenceIfNeeded();

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
        BaseRecyclerViewAdapterPreferences prefs = new BaseRecyclerViewAdapterPreferences().selectable(event.isAllowMultiSelect(), event.isInitialSelectionLocked());
        if(!event.isAllowEditing()) {
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

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(AlbumPermissionsSelectionNeededEvent event) {
        showAlbumPermissions(event.getAvailableAlbums(), event.getDirectAlbumPermissions(), event.getIndirectAlbumPermissions(), event.isAllowEdit(), event.getActionId());
    }

    private boolean invokeStoredActionIfAvailable() {

        boolean invoked = invokeStoredActionIfAvailableOnClass(AbstractMainActivity.class);
        if(!invoked) {
            invoked = invokeStoredActionIfAvailableOnClass(MainActivity.class);
        }
        return invoked;
    }

    private boolean invokeStoredActionIfAvailableOnClass(Class c) {

        boolean invoked = false;
        boolean actionAvailable = onLoginActionMethodName != null;
        if (actionAvailable) {
            Method[] methods = c.getDeclaredMethods();
            for(Method m : methods) {
                if(m.getName().equals(onLoginActionMethodName)) {
                    onLoginActionMethodName = null;
                    try {
                        invoked = true;
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
        return invoked;
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(ThemeAlteredEvent event) {
        CustomNavigationView navigationView = findViewById(R.id.nav_view);
        navigationView.updateTheme();
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(PiwigoLoginSuccessEvent event) {
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
        AvailableAlbumsListAdapter.AvailableAlbumsListAdapterPreferences prefs = new AvailableAlbumsListAdapter.AvailableAlbumsListAdapterPreferences();
        prefs.selectable(event.isAllowMultiSelect(), event.isInitialSelectionLocked());
        prefs.withShowHierachy();
        if(!event.isAllowEditing()) {
            prefs.readonly();
        }
        showAlbumSelectionFragment(event.getActionId(), prefs, event.getInitialSelection());
    }

}
