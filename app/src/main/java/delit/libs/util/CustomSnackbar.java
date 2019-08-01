//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by Fernflower decompiler)
//

package delit.libs.util;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.core.view.ViewCompat;

import com.google.android.material.snackbar.BaseTransientBottomBar;

import delit.piwigoclient.R;

public class CustomSnackbar extends BaseTransientBottomBar<CustomSnackbar> {

    /**
     * Constructor for the transient bottom bar.
     *
     * @param parent The parent for this transient bottom bar.
     * @param content The content view for this transient bottom bar.
     * @param callback The content view callback for this transient bottom bar.
     */
    private CustomSnackbar(ViewGroup parent, View content, ContentViewCallback callback) {
        super(parent, content, callback);
    }

    public static CustomSnackbar make(@NonNull View parent, @Duration int duration) {
        final LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        final View content = inflater.inflate(R.layout.snackbar, findSuitableParent(parent), false);
        final ContentViewCallback viewCallback = new ContentViewCallback(content);
        final CustomSnackbar customSnackbar = new CustomSnackbar(findSuitableParent(parent), content, viewCallback);

        customSnackbar.getView().setPadding(0, 0, 0, 0);
        customSnackbar.setDuration(duration);
        return customSnackbar;
    }

    public CustomSnackbar setIcon(@DrawableRes int drawableId) {
        ImageView imageView = getView().findViewById(R.id.snackbar_icon);
        imageView.setVisibility(View.VISIBLE);
        imageView.setImageDrawable(AppCompatResources.getDrawable(imageView.getContext(), drawableId));
        return this;
    }

    public void show() {
        super.show();
    }

    public void dismiss() {
        super.dismiss();
    }

    public boolean isShown() {
        return super.isShown();
    }

    public CustomSnackbar setTitle(CharSequence text) {
        TextView textView = getView().findViewById(R.id.snackbar_title);
        if(text != null) {
            textView.setText(text);
        } else {
            textView.setVisibility(View.GONE);
        }
        return this;
    }
    
    
    public CustomSnackbar setText(CharSequence text) {
        TextView textView = getView().findViewById(R.id.snackbar_text);
        textView.setText(text);
        return this;
    }

    public CustomSnackbar setAction(CharSequence text, final View.OnClickListener listener) {
        Button actionView = getView().findViewById(R.id.snackbar_action);
        actionView.setText(text);
        actionView.setVisibility(View.VISIBLE);
        actionView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                listener.onClick(view);
                // Now dismiss the Snackbar
                dismiss();
            }
        });
        return this;
    }

    private static ViewGroup findSuitableParent(View view) {
        ViewGroup fallback = null;
        int resId = 16908290; //TODO what is this magic number?
        do {
            if (view instanceof CoordinatorLayout) {
                return (ViewGroup)view;
            }

            if (view instanceof FrameLayout) {
                if (view.getId() == resId) {
                    return (ViewGroup)view;
                }

                fallback = (ViewGroup)view;
            }

            if (view != null) {
                ViewParent parent = view.getParent();
                view = parent instanceof View ? (View)parent : null;
            }
        } while(view != null);

        return fallback;
    }

    public static class BaseCallback extends BaseTransientBottomBar.BaseCallback<CustomSnackbar> implements View.OnLongClickListener {
        @Override
        public boolean onLongClick(View v) {
            return false;
        }
    }

    @NonNull
    public CustomSnackbar addCallback(@NonNull BaseCallback callback) {
        getView().setOnLongClickListener(callback);
        return super.addCallback(callback);
    }

    private static class ContentViewCallback implements BaseTransientBottomBar.ContentViewCallback {

        private View content;

        public ContentViewCallback(View content) {
            this.content = content;
        }

        @Override
        public void animateContentIn(int delay, int duration) {
            ViewCompat.setScaleY(content, 0f);
            ViewCompat.animate(content).scaleY(1f).setDuration(duration).setStartDelay(delay);
        }

        @Override
        public void animateContentOut(int delay, int duration) {
            ViewCompat.setScaleY(content, 1f);
            ViewCompat.animate(content).scaleY(0f).setDuration(duration).setStartDelay(delay);
        }
    }
}