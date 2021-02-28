package delit.piwigoclient.ui.slideshow.item.action;

import android.widget.ExpandableListView;

import androidx.viewpager.widget.ViewPager;

import com.drew.metadata.Metadata;

import java.lang.ref.WeakReference;

import delit.libs.ui.OwnedSafeAsyncTask;
import delit.piwigoclient.R;
import delit.piwigoclient.model.piwigo.PictureResourceItem;
import delit.piwigoclient.ui.common.FragmentUIHelper;
import delit.piwigoclient.ui.slideshow.item.AlbumPictureItemFragment;
import delit.piwigoclient.ui.slideshow.item.ExifDataListAdapter;

public class ExifDataLoadTask<F extends AlbumPictureItemFragment<F,FUIH,T>, FUIH extends FragmentUIHelper<FUIH, F>, T extends PictureResourceItem> extends OwnedSafeAsyncTask<F, Void,Void, ExifDataListAdapter> {
    private final WeakReference<ViewPager> viewPagerRef;
    private final Metadata metadata;

    public ExifDataLoadTask(F owner, ViewPager viewPager, Metadata metadata) {
        super(owner);
        this.viewPagerRef = new WeakReference<>(viewPager);
        this.metadata = metadata;
    }

    @Override
    protected ExifDataListAdapter doInBackgroundSafely(Void... voids) {
//            BaseRecyclerViewAdapterPreferences prefs = new BaseRecyclerViewAdapterPreferences();
//            prefs.readonly();
        ViewPager viewPager = viewPagerRef.get();
        if(viewPager != null) {
            return ExifDataListAdapter.newAdapter(viewPager.getContext(), metadata);
        }
        return null;
    }

    @Override
    protected void onPostExecuteSafely(ExifDataListAdapter exifDataListAdapter) {
        super.onPostExecuteSafely(exifDataListAdapter);
        ViewPager viewPager = viewPagerRef.get();
        if(viewPager != null) {
            ExpandableListView exifDataList = viewPager.findViewById(R.id.exifDataList);
            exifDataList.setAdapter(exifDataListAdapter);
        }
    }
}
