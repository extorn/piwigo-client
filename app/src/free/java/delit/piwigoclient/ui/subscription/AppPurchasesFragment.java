package delit.piwigoclient.ui.subscription;

import android.content.Context;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.FragmentManager;
import androidx.viewpager.widget.ViewPager;

import delit.libs.ui.view.CustomViewPager;
import delit.libs.ui.view.recycler.MyFragmentRecyclerPagerAdapter;
import delit.piwigoclient.R;
import delit.piwigoclient.business.AppPreferences;
import delit.piwigoclient.ui.common.FragmentUIHelper;
import delit.piwigoclient.ui.common.dialogmessage.QuestionResultAdapter;
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
        Button b = v.findViewById(R.id.not_interested_button);
        b.setOnClickListener(this::onClickNotInterestedInPaying);
        return v;
    }

    private void onClickNotInterestedInPaying(View view) {
        getUiHelper().showOrQueueDialogQuestion(R.string.alert_question_title, getString(R.string.alert_not_interested_in_paying), R.string.button_cancel, R.string.button_remove, new NotInterestedListener<>(getUiHelper()));
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

    public static class NotInterestedListener<F extends MyFragment<F,FUIH>, FUIH extends FragmentUIHelper<FUIH, F>> extends QuestionResultAdapter<FUIH,F> implements Parcelable {

        public NotInterestedListener(FUIH uiHelper) {
            super(uiHelper);
        }

        protected NotInterestedListener(Parcel in) {
            super(in);
        }

        @Override
        protected void onPositiveResult(AlertDialog dialog) {
            super.onPositiveResult(dialog);
            AppPreferences.setHidePaymentOptionForever(getUiHelper().getPrefs(), getContext());
            getUiHelper().showOrQueueDialogMessage(R.string.alert_information, getContext().getString(R.string.alert_purchases_option_removed_from_menu));
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            super.writeToParcel(dest, flags);
        }

        @Override
        public int describeContents() {
            return 0;
        }

        public static final Creator<NotInterestedListener<?,?>> CREATOR = new Creator<NotInterestedListener<?,?>>() {
            @Override
            public NotInterestedListener<?,?> createFromParcel(Parcel in) {
                return new NotInterestedListener<>(in);
            }

            @Override
            public NotInterestedListener<?,?>[] newArray(int size) {
                return new NotInterestedListener[size];
            }
        };
    }
}
