package delit.piwigoclient.ui.slideshow.item;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.button.MaterialButton;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.Locale;

import delit.libs.core.util.Logging;
import delit.piwigoclient.R;
import delit.piwigoclient.model.piwigo.ResourceItem;
import delit.piwigoclient.ui.common.FragmentUIHelper;
import delit.piwigoclient.ui.common.fragment.MyFragment;
import delit.piwigoclient.ui.events.SlideshowItemRefreshRequestEvent;
import delit.piwigoclient.ui.events.SlideshowSizeUpdateEvent;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;

public class AlbumItemNotLoadedFragment<F extends AlbumItemNotLoadedFragment<F,FUIH>, FUIH extends FragmentUIHelper<FUIH,F>> extends MyFragment<F,FUIH> implements SlideshowItemView<ResourceItem> {

    private static final String ARG_AND_STATE_SLIDESHOW_PAGE_IDX = "slideshowPageIdx";
    private static final String ARG_AND_STATE_ALBUM_LOADED_RESOURCE_ITEM_COUNT = "albumLoadedResourceItemCount";
    private static final String ARG_AND_STATE_ALBUM_TOTAL_RESOURCE_ITEM_COUNT = "albumTotalResourceItemCount";
    private static final String STATE_IS_PRIMARY_SLIDESHOW_ITEM = "isPrimarySlideshowItem";

    private TextView itemPositionTextView;
    private int slideshowPageIdx;
    private int albumLoadedItemCount;
    private long albumTotalItemCount;
    private boolean isPrimarySlideshowItem;

    public AlbumItemNotLoadedFragment(){
        //for the slideshow pager adapter to instantiate it
        super(R.layout.item_not_loaded);
    }

    public static Bundle buildArgs(int slideshowPageIdx, int albumResourceItemCount, long totalResourceItemCount) {
        Bundle b = new Bundle();
        b.putInt(ARG_AND_STATE_SLIDESHOW_PAGE_IDX, slideshowPageIdx);
        b.putInt(ARG_AND_STATE_ALBUM_LOADED_RESOURCE_ITEM_COUNT, albumResourceItemCount);
        b.putLong(ARG_AND_STATE_ALBUM_TOTAL_RESOURCE_ITEM_COUNT, totalResourceItemCount);
        return b;
    }

    private void restoreSavedInstanceState(Bundle b) {
        if (b == null) {
            return;
        }
        loadArgsFromBundle(b);
        isPrimarySlideshowItem = b.getBoolean(STATE_IS_PRIMARY_SLIDESHOW_ITEM);
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(ARG_AND_STATE_SLIDESHOW_PAGE_IDX, slideshowPageIdx);
        outState.putInt(ARG_AND_STATE_ALBUM_LOADED_RESOURCE_ITEM_COUNT, albumLoadedItemCount);
        outState.putLong(ARG_AND_STATE_ALBUM_TOTAL_RESOURCE_ITEM_COUNT, albumTotalItemCount);
        outState.putBoolean(STATE_IS_PRIMARY_SLIDESHOW_ITEM, isPrimarySlideshowItem);
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        slideshowPageIdx = getArguments().getInt(ARG_AND_STATE_SLIDESHOW_PAGE_IDX);
        super.onCreate(savedInstanceState);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View v = super.onCreateView(inflater, container, savedInstanceState);
        itemPositionTextView = v.findViewById(R.id.slideshow_resource_item_x_of_y_text);
        return v;
    }

    private void updateItemPositionText() {
        if (albumLoadedItemCount == 1 && slideshowPageIdx == albumLoadedItemCount && albumTotalItemCount == albumLoadedItemCount) {
            itemPositionTextView.setVisibility(GONE);
        } else {
            itemPositionTextView.setVisibility(VISIBLE);
            if(albumLoadedItemCount < albumTotalItemCount) {
                itemPositionTextView.setText(String.format(Locale.getDefault(), "%1$d/%2$d[%3$d]", slideshowPageIdx + 1, albumLoadedItemCount, albumTotalItemCount));
            } else {
                itemPositionTextView.setText(String.format(Locale.getDefault(), "%1$d/%2$d", slideshowPageIdx + 1, albumTotalItemCount));
            }
        }
    }

    private void loadArgsFromBundle(Bundle b) {
        slideshowPageIdx = b.getInt(ARG_AND_STATE_SLIDESHOW_PAGE_IDX);
        albumLoadedItemCount = b.getInt(ARG_AND_STATE_ALBUM_LOADED_RESOURCE_ITEM_COUNT);
        albumTotalItemCount = b.getLong(ARG_AND_STATE_ALBUM_TOTAL_RESOURCE_ITEM_COUNT);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        MaterialButton button = view.findViewById(R.id.slideshow_item_not_loaded_refresh_button);
        button.setOnClickListener((v)-> onUserActionClickRefreshContent());

        slideshowPageIdx = -1;
        albumLoadedItemCount = -1;
        albumTotalItemCount = -1;

        Bundle args = getArguments();
        if (args != null) {
            loadArgsFromBundle(args);
        }
        // override page default values with any saved state
        restoreSavedInstanceState(savedInstanceState);

        updateItemPositionText();
    }

    private void onUserActionClickRefreshContent() {
        EventBus.getDefault().post(new SlideshowItemRefreshRequestEvent(slideshowPageIdx));
    }

    @Override
    public ResourceItem getModel() {
        return null;
    }

    @Override
    public void onPageSelected() {
        Logging.logAnalyticEventIfPossible("AlbumItemNotLoadedFragShown");
        onUserActionClickRefreshContent();
    }

    @Override
    public void onPageDeselected() {

    }

    @Override
    public int getPagerIndex() {
        return slideshowPageIdx;
    }

    @Override
    public void onPagerIndexChangedTo(int newPagerIndex) {
        slideshowPageIdx = newPagerIndex;
        updateItemPositionText();
    }

    @Subscribe(threadMode = ThreadMode.MAIN_ORDERED)
    public void onEvent(SlideshowSizeUpdateEvent event) {
        this.albumLoadedItemCount = event.getLoadedResources();
        this.albumTotalItemCount = event.getTotalResources();
        updateItemPositionText();
    }
}
