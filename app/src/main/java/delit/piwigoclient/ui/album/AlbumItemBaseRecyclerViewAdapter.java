package delit.piwigoclient.ui.album;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import delit.libs.ui.view.recycler.BaseRecyclerViewAdapter;
import delit.libs.ui.view.recycler.CustomViewHolder;
import delit.piwigoclient.model.piwigo.CategoryItem;
import delit.piwigoclient.model.piwigo.GalleryItem;
import delit.piwigoclient.model.piwigo.ResourceContainer;
import delit.piwigoclient.ui.album.view.AlbumItemRecyclerViewAdapterPreferences;
import delit.piwigoclient.ui.common.recyclerview.IdentifiableListViewAdapter;
import delit.piwigoclient.ui.model.ViewModelContainer;

/**
 * {@link RecyclerView.Adapter} that can display a {@link GalleryItem}
 */
public abstract class AlbumItemBaseRecyclerViewAdapter<LVA extends AlbumItemBaseRecyclerViewAdapter<LVA,T,MSL,VH, RC>, T extends GalleryItem, MSL extends BaseRecyclerViewAdapter.MultiSelectStatusAdapter<MSL,LVA,AlbumItemRecyclerViewAdapterPreferences,T,VH>, VH extends CustomViewHolder<VH, LVA, AlbumItemRecyclerViewAdapterPreferences, T,MSL>, RC extends ResourceContainer<?, T>> extends IdentifiableListViewAdapter<LVA, AlbumItemRecyclerViewAdapterPreferences, T, RC, VH, MSL> {

    public AlbumItemBaseRecyclerViewAdapter(@NonNull final Context context, final Class<? extends ViewModelContainer> modelType, final RC gallery, MSL multiSelectStatusListener, AlbumItemRecyclerViewAdapterPreferences prefs) {
        super(context, modelType, gallery, multiSelectStatusListener, prefs);
    }

    @Override
    public int getItemViewType(int position) {
        return getItemByPosition(position).getType();
    }

    @NonNull
    protected View inflateView(@NonNull ViewGroup parent, int viewType) {
        View view;
        switch(viewType) {
            case GalleryItem.CATEGORY_TYPE:
                view = inflateChildAlbumView(parent);
                break;
            case GalleryItem.PICTURE_RESOURCE_TYPE:
                view = inflatePictureResourceView(parent);
                break;
            case GalleryItem.VIDEO_RESOURCE_TYPE:
                view = inflateVideoResourceView(parent);
                break;
            case GalleryItem.ALBUM_HEADING_TYPE:
                view = inflateChildAlbumsHeader(parent);
                break;
            case GalleryItem.PICTURE_HEADING_TYPE:
                view = inflateChildResourcesHeader(parent);
                break;
            default:
                throw new RuntimeException("viewType not found ("+viewType+")");
        }
        return view;
    }

    @NonNull
    @Override
    public VH buildViewHolder(View view, int viewType) {

        switch (viewType) {
            case GalleryItem.CATEGORY_TYPE:
                return buildViewHolderChildAlbum(view, viewType);
            case GalleryItem.VIDEO_RESOURCE_TYPE:
                return buildViewHolderChildVideoResources(view, viewType);
            case GalleryItem.PICTURE_RESOURCE_TYPE:
                return buildViewHolderChildPictureResource(view, this, viewType);
            case GalleryItem.ALBUM_HEADING_TYPE:
                return buildViewHolderChildAlbumsHeader(view, viewType);
            case GalleryItem.PICTURE_HEADING_TYPE:
                return buildViewHolderChildResourcesHeader(view, viewType);
            default:
                throw new IllegalStateException("No matching viewholder could be found");
        }
    }

    protected VH buildViewHolderChildResourcesHeader(View view, int viewType) {
        throw new UnsupportedOperationException("unimplemented");
    }

    protected VH buildViewHolderChildAlbumsHeader(View view, int viewType) {
        throw new UnsupportedOperationException("unimplemented");
    }

    protected VH buildViewHolderChildPictureResource(View view, AlbumItemBaseRecyclerViewAdapter<LVA,T,MSL,VH,RC> lvatmslvhrcAlbumItemBaseRecyclerViewAdapter, int viewType) {
        throw new UnsupportedOperationException("unimplemented");
    }

    protected VH buildViewHolderChildVideoResources(View view, int viewType) {
        throw new UnsupportedOperationException("unimplemented");
    }

    protected VH buildViewHolderChildAlbum(View view, int viewType) {
        throw new UnsupportedOperationException("unimplemented");
    }

    protected View inflateChildResourcesHeader(ViewGroup parent) {
        throw new UnsupportedOperationException("Resource Heading type is not supported");
    }

    protected View inflateChildAlbumsHeader(ViewGroup parent) {
        throw new UnsupportedOperationException("Album Heading type is not supported");
    }

    protected View inflatePictureResourceView(ViewGroup parent) {
        throw new UnsupportedOperationException("Resource type is not supported");
    }

    protected View inflateVideoResourceView(ViewGroup parent) {
        throw new UnsupportedOperationException("Resource type is not supported");
    }

    protected View inflateChildAlbumView(ViewGroup parent) {
        throw new UnsupportedOperationException("Category type is not supported");
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        T newItem = getItemByPosition(position);
        if (!isHolderOutOfSync(holder, newItem)) {
            // rendering the same item
            onRebindViewHolderWithSameData(holder, position, newItem);
        } else {
            super.onBindViewHolder(holder, position);
        }
    }

    protected abstract void onRebindViewHolderWithSameData(@NonNull VH holder, int position, T newItem);

    @Override
    protected boolean isDirtyItemViewHolder(VH holder, T newItem) {
        if(!super.isDirtyItemViewHolder(holder, newItem)) {
            boolean catDirty = (holder.getItem() instanceof CategoryItem
            && newItem instanceof CategoryItem
            && ((CategoryItem) holder.getItem()).isAdminCopy() != ((CategoryItem) newItem).isAdminCopy());
            return catDirty;
        }
        return true;
    }
}
