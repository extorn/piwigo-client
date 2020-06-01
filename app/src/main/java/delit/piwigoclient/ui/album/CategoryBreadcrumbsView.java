package delit.piwigoclient.ui.album;

import android.content.Context;
import android.os.Build;
import android.util.AttributeSet;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

import java.util.ArrayList;
import java.util.List;

import delit.libs.ui.view.AbstractBreadcrumbsView;
import delit.piwigoclient.model.piwigo.CategoryItem;

public class CategoryBreadcrumbsView extends AbstractBreadcrumbsView<CategoryItem> {
    private CategoryPathNavigator navigator;

    public CategoryBreadcrumbsView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public CategoryBreadcrumbsView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public CategoryBreadcrumbsView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    @Override
    protected PathNavigator<CategoryItem> buildPathNavigator() {
        navigator = new CategoryPathNavigator();
        return navigator;
    }

    public void setRoot(CategoryItem rootAlbum) {
        navigator.setRootAlbum(rootAlbum);
    }

    private class CategoryPathNavigator implements PathNavigator<CategoryItem> {
        private CategoryItem rootAlbum;
        private List<CategoryItem> itemChain = new ArrayList<>();

        @Override
        public CategoryItem getParent(@NonNull CategoryItem item) {
            if(!itemChain.contains(item)) {
                if(rootAlbum == null) {
                    return null;
                }
                itemChain = rootAlbum.getFullPath(item);
            }
            int idx = itemChain.indexOf(item);
            if(idx == 0) {
                return null;
            } else if(idx < 0) {
                throw new IllegalStateException("Item not found in breadcrumb list : " + item);
            }
            return itemChain.get(idx -1);
        }

        @Override
        public String getItemName(@NonNull CategoryItem item) {
            if(!itemChain.contains(item)) {
                itemChain.add(item);
            }
            return  item.getName();
        }

        public void setRootAlbum(CategoryItem rootAlbum) {
            this.rootAlbum = rootAlbum;
        }
    }
}
