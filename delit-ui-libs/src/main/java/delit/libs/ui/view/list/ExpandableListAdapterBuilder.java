package delit.libs.ui.view.list;

import android.content.Context;
import android.widget.SimpleExpandableListAdapter;

import androidx.annotation.IdRes;
import androidx.annotation.LayoutRes;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import delit.libs.util.ArrayUtils;

/**
     * A structured iteration over a data blob to build the necessary for this adapter
     * @param <D> The data
     * @param <P> A group object within the data 
     * @param <C> A child object within the data (there could be many of these for a single group)
     */
    public abstract class ExpandableListAdapterBuilder<D,P,C> {
        
        public SimpleExpandableListAdapter build(Context context, D data, ViewBinding<P> groupBinder, ViewBinding<C> childBinder) {

            // a list of group field to view mappings for each group 
            List<Map<String, ?>> groupData = new ArrayList<>();
            // a list of child field to view mappings for each group
            List<List<Map<String, ?>>> childData = new ArrayList<>();

            P currentParent = null;
            while(hasNextParent(data, currentParent)) {

                List<Map<String, ?>> children = new ArrayList<>();
                
                currentParent = getNextParent(data, currentParent);
                C currentChild = null;
                while(hasNextChild(data, currentParent, currentChild)) {
                    currentChild = getNextChild(data, currentParent, currentChild);
                    children.add(childBinder.buildDataMap(currentChild));
                }
                
                groupData.add(groupBinder.buildDataMap(currentParent));
                childData.add(children);
            }
            return new SimpleExpandableListAdapter(context, groupData, groupBinder.getLayout(), groupBinder.getFromDataFields(), groupBinder.getToViewFields(), childData, childBinder.getLayout(), childBinder.getFromDataFields(), childBinder.getToViewFields());
        }
        
        public abstract boolean hasNextParent(D data, P currentParent);
        public abstract boolean hasNextChild(D data, P parent, C currentChild);
        public abstract P getNextParent(D data, P currentParent);
        public abstract C getNextChild(D data, P parent, C currentChild);

        public abstract static class ViewBinding<T> {
            private @LayoutRes int layout;
            private LinkedHashMap<Integer, String> viewIdToFieldBinding = new LinkedHashMap<>();

            public ViewBinding(@LayoutRes int layout) {
                this.layout = layout;
            }

            public int getLayout() {
                return layout;
            }

            public void addViewIdToFieldBinding(@IdRes int viewId, String fieldName) {
                if(viewIdToFieldBinding.containsValue(fieldName)) {
                    throw new IllegalArgumentException("Field has already been added to the binding : "+fieldName);
                }
                viewIdToFieldBinding.put(viewId, fieldName);
            }

            public String[] getFromDataFields() {
                return viewIdToFieldBinding.values().toArray(new String[0]);
            }

            public int[] getToViewFields() {
                return ArrayUtils.unwrapInts(viewIdToFieldBinding.keySet());
            }

            abstract Map<String, ?> buildDataMap(T item);
        }
    }