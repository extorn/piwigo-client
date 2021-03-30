package delit.piwigoclient.ui.subscription.list;

import com.android.billingclient.api.SkuDetails;

import delit.libs.ui.view.recycler.BaseRecyclerViewAdapter;

public class AvailableProductsMultiSelectStatusAdapter<MSA extends AvailableProductsMultiSelectStatusAdapter<MSA,LVA,VH>, LVA extends AvailableProductsRecyclerViewAdapter<LVA,MSA,VH>, VH extends AvailableProductItemViewHolder<VH,LVA,MSA>> extends BaseRecyclerViewAdapter.MultiSelectStatusAdapter<MSA,LVA, AvailableProductsRecyclerViewAdapter.AvailableProductAdapterPrefs, SkuDetails,VH> {

}
