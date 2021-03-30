package delit.piwigoclient.ui.subscription.list;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;

import delit.libs.ui.view.recycler.BaseRecyclerViewAdapterPreferences;
import delit.libs.ui.view.recycler.CustomClickListener;
import delit.libs.ui.view.recycler.SimpleRecyclerViewAdapter;
import delit.piwigoclient.R;
import delit.piwigoclient.model.piwigo.GalleryItem;
import delit.piwigoclient.subscription.api.ExistingPurchase;

/**
 * {@link RecyclerView.Adapter} that can display a {@link GalleryItem}
 */
public class PurchasedProductsRecyclerViewAdapter<LVA extends PurchasedProductsRecyclerViewAdapter<LVA, MSA,VH>, MSA extends PurchasedProductsMultiSelectStatusAdapter<MSA, LVA,VH>, VH extends PurchasedProductItemViewHolder<VH, LVA,MSA>> extends SimpleRecyclerViewAdapter<LVA, ExistingPurchase, PurchasedProductsRecyclerViewAdapter.PurchasedProductAdapterPrefs, VH, MSA> {

    private static final String TAG = "ProductsAdapter";
    public static final int VIEW_TYPE_LIST = 0;
//    private final ProductManagementListener productManagementListener;
    private int viewType = VIEW_TYPE_LIST;

    public PurchasedProductsRecyclerViewAdapter() {
        super((MSA) new PurchasedProductsMultiSelectStatusAdapter(), new PurchasedProductAdapterPrefs());
        setItems(new ArrayList<>());
//        this.productManagementListener = listener;
        this.setHasStableIds(true);
    }

    @NonNull
    @Override
    public VH buildViewHolder(View view, int viewType) {
        if (viewType == VIEW_TYPE_LIST) {
            return (VH) new PurchasedProductItemViewHolder<>(view);
        } else {
            throw new IllegalArgumentException("Unsupported view type : " + viewType);
        }
    }

    @NonNull
    @Override
    protected View inflateView(@NonNull ViewGroup parent, int viewType) {
        if(viewType == VIEW_TYPE_LIST){
            return LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.layout_list_item_purchased_product, parent, false);
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
    protected CustomClickListener<MSA,LVA, PurchasedProductAdapterPrefs, ExistingPurchase, VH> buildCustomClickListener(VH viewHolder) {
        return new ProductItemCustomClickListener(viewHolder, (LVA)this);
    }

    public static class PurchasedProductAdapterPrefs extends BaseRecyclerViewAdapterPreferences<PurchasedProductAdapterPrefs> { }

    public class ProductItemCustomClickListener extends CustomClickListener<MSA, LVA, PurchasedProductAdapterPrefs, ExistingPurchase, VH> {
        public ProductItemCustomClickListener(VH viewHolder, LVA lva) {
            super(viewHolder, lva);
        }

        public void onManagePayment(ExistingPurchase itemDetail) {
//            productManagementListener.manageProductPurchase(itemDetail);
        }
    }
}
