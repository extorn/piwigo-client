package delit.piwigoclient.ui.subscription;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentManager;
import androidx.viewpager.widget.ViewPager;

import delit.libs.ui.view.CustomViewPager;
import delit.libs.ui.view.recycler.MyFragmentRecyclerPagerAdapter;
import delit.piwigoclient.R;
import delit.piwigoclient.ui.common.FragmentUIHelper;
import delit.piwigoclient.ui.common.fragment.MyFragment;

public class AppPurchasesFragment<F extends MyFragment<F,FUIH>, FUIH extends FragmentUIHelper<FUIH, F>> extends MyFragment<F, FUIH> {
    private ViewPager viewPager;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {

        super.onCreateView(inflater, container, savedInstanceState);

        View v = inflater.inflate(R.layout.fragment_app_purchases, container, false);
        viewPager = v.findViewById(R.id.viewpager);
        viewPager.setAdapter(new AppPurchasesPagerAdapter(requireContext(), getChildFragmentManager()));
        return v;
    }

    @Override
    public void onStart() {
        super.onStart();
        viewPager.setCurrentItem(0);
    }

    private static class AppPurchasesPagerAdapter<F extends MyFragment<F,?> & MyFragmentRecyclerPagerAdapter.PagerItemView> extends MyFragmentRecyclerPagerAdapter<F,CustomViewPager> {

        private final Context context;

        public AppPurchasesPagerAdapter(Context context, FragmentManager fm) {
            super(fm);
            this.context = context;
        }

        @Nullable
        @Override
        public CharSequence getPageTitle(int position) {
            switch(position) {
                case 0:
                    return context.getString(R.string.app_purchases_tab_shop);
                case 1:
                    return context.getString(R.string.app_purchases_tab_my_purchases);
                default:
                    throw new IllegalStateException();
            }
        }

        @Override
        protected F createNewItem(Class<? extends F> fragmentTypeNeeded, int position) {
            switch(position) {
                case 0:
                    return (F)new AppShopFrontFragment();
                case 1:
                    return (F)new MyPurchasesFragment();
                default:
                    throw new IllegalStateException();
            }
        }

        @Override
        public int getCount() {
            return 2;
        }
    }
}
