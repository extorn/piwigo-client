package delit.piwigoclient.ui;

import android.os.Bundle;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.HashSet;

import delit.piwigoclient.ui.events.ViewTagEvent;
import delit.piwigoclient.ui.events.trackable.TagSelectionNeededEvent;
import delit.piwigoclient.ui.tags.TagSelectFragment;
import delit.piwigoclient.ui.tags.TagsListFragment;
import delit.piwigoclient.ui.tags.ViewTagFragment;

/**
 * Created by gareth on 07/04/18.
 */

public class MainActivity extends AbstractMainActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EventBus.getDefault().register(this);
    }

    @Override
    public void onStart() {
        super.onStart();
    }

    @Override
    protected void onRestart() {
        super.onRestart();
        EventBus.getDefault().register(this);
    }

    @Override
    public void onStop() {
        EventBus.getDefault().unregister(this);
        super.onStop();
    }

    protected void showTags() {
        TagsListFragment fragment = TagsListFragment.newInstance();
        showFragmentNow(fragment);
    }

    private void showTagSelectionFragment(int actionId, boolean allowMultiSelect, boolean allowEditing, boolean allowAddition, boolean initialSelectionLocked, HashSet<Long> initialSelection) {
        TagSelectFragment fragment = TagSelectFragment.newInstance(allowMultiSelect, allowEditing, allowAddition, initialSelectionLocked, actionId, initialSelection);
        showFragmentNow(fragment);
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(TagSelectionNeededEvent event) {
        showTagSelectionFragment(event.getActionId(), event.isAllowMultiSelect(), event.isAllowEditing(), true, event.isInitialSelectionLocked(), event.getInitialSelection());
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(ViewTagEvent event) {
        ViewTagFragment fragment = ViewTagFragment.newInstance(event.getTag());
        showFragmentNow(fragment);
    }
}
