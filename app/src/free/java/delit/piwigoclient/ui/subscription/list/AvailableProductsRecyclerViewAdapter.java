package delit.piwigoclient.ui.subscription.list;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.android.billingclient.api.SkuDetails;

import delit.libs.ui.view.recycler.BaseRecyclerViewAdapterPreferences;
import delit.libs.ui.view.recycler.CustomClickListener;
import delit.libs.ui.view.recycler.SimpleRecyclerViewAdapter;
import delit.piwigoclient.R;
import delit.piwigoclient.model.piwigo.GalleryItem;
import delit.piwigoclient.subscription.api.AvailableProducts;

/**
 * {@link RecyclerView.Adapter} that can display a {@link GalleryItem}
 */
public class AvailableProductsRecyclerViewAdapter<LVA extends AvailableProductsRecyclerViewAdapter<LVA, MSA,VH>, MSA extends AvailableProductsMultiSelectStatusAdapter<MSA, LVA,VH>, VH extends AvailableProductItemViewHolder<VH, LVA,MSA>> extends SimpleRecyclerViewAdapter<LVA, SkuDetails, AvailableProductsRecyclerViewAdapter.AvailableProductAdapterPrefs, VH, MSA> {

    private static final String TAG = "ProductsAdapter";
    public static final int VIEW_TYPE_LIST = 0;
    private final ProductManagementListener productManagementListener;
    private int viewType = VIEW_TYPE_LIST;

    public AvailableProductsRecyclerViewAdapter(AvailableProducts availableProductsModel, ProductManagementListener listener) {
        super((MSA) new AvailableProductsMultiSelectStatusAdapter(), new AvailableProductAdapterPrefs());
        this.productManagementListener = listener;
        this.setHasStableIds(true);
        setItems(availableProductsModel.getAvailableProducts());
    }

    @NonNull
    @Override
    public VH buildViewHolder(View view, int viewType) {
        if (viewType == VIEW_TYPE_LIST) {
            return (VH) new AvailableProductItemViewHolder<>(view);
        } else {
            throw new IllegalArgumentException("Unsupported view type : " + viewType);
        }
    }

    @NonNull
    @Override
    protected View inflateView(@NonNull ViewGroup parent, int viewType) {
        if(viewType == VIEW_TYPE_LIST){
            return LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.layout_list_item_available_product, parent, false);
        } else {
            throw new IllegalStateException("viewType not supported" + viewType);
        }
    }

    public void setViewType(int viewType) {
        if (viewType != VIEW_TYPE_LIST) {
            throw new IllegalArgumentException("illegal view type");
        }
        this.viewType = viewType;
    }

    @Override
    public int getItemViewType(int position) {
        // only storing items of one type.
        return viewType;
    }

    @Override
    protected CustomClickListener<MSA,LVA, AvailableProductAdapterPrefs, SkuDetails, VH> buildCustomClickListener(VH viewHolder) {
        return new ProductItemCustomClickListener(viewHolder, (LVA)this);
    }

    public static class AvailableProductAdapterPrefs extends BaseRecyclerViewAdapterPreferences<AvailableProductAdapterPrefs> { }

    public class ProductItemCustomClickListener extends CustomClickListener<MSA, LVA, AvailableProductAdapterPrefs, SkuDetails, VH> {
        public ProductItemCustomClickListener(VH viewHolder, LVA lva) {
            super(viewHolder, lva);
        }

        public void onManagePayment(SkuDetails itemDetail) {
            productManagementListener.manageProductPurchase(itemDetail);
        }
    }
}
