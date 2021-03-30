package delit.piwigoclient.ui.subscription.list;

import delit.libs.ui.view.recycler.BaseRecyclerViewAdapter;
import delit.piwigoclient.subscription.api.ExistingPurchase;

public class PurchasedProductsMultiSelectStatusAdapter<MSA extends PurchasedProductsMultiSelectStatusAdapter<MSA,LVA,VH>, LVA extends PurchasedProductsRecyclerViewAdapter<LVA,MSA,VH>, VH extends PurchasedProductItemViewHolder<VH,LVA,MSA>> extends BaseRecyclerViewAdapter.MultiSelectStatusAdapter<MSA,LVA, PurchasedProductsRecyclerViewAdapter.PurchasedProductAdapterPrefs, ExistingPurchase,VH> {

}
