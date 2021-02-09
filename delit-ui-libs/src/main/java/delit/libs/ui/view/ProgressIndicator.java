package delit.libs.ui.view;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.AttrRes;
import androidx.annotation.IntRange;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.core.view.ViewCompat;

import java.util.Objects;

import delit.libs.R;

public class ProgressIndicator extends FrameLayout {
    private static final String TAG = "ProgressInd";
    private Button actionButton;
    private TextView descriptionField;
    private ProgressBar progressBar;

    public ProgressIndicator(Context context) {
        super(context);
        init(context, null, 0);
    }

    public ProgressIndicator(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs, 0);
    }

    public ProgressIndicator(Context context, AttributeSet attrs, @AttrRes int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context, attrs, defStyleAttr);
    }

    private void init(Context context, AttributeSet attrs, int defStyleAttr) {
        TypedArray a = context.obtainStyledAttributes(
                attrs, R.styleable.ProgressIndicator, defStyleAttr, 0);
        ViewCompat.setBackgroundTintList(this, a.getColorStateList(R.styleable.ProgressIndicator_backgroundTint));
        a.recycle();

        View content = inflate(context, R.layout.layout_progress_indicator, null);
        addView(content);
        actionButton = findViewById(R.id.progressAction);
        descriptionField = findViewById(R.id.progressText);
        progressBar = findViewById(R.id.progressBar);
    }
    
    public void showProgressIndicator(@StringRes int progressTextRes, @IntRange(from=-1,to=100) int progress) {
        progressBar.setIndeterminate(progress < 0);
        showProgressIndicatorInternal(getContext().getString(progressTextRes), progress, 0, null);
    }

    public void showProgressIndicator(@IntRange(from=-1,to=100) int progress) {
        progressBar.setIndeterminate(progress < 0);
        showProgressIndicatorInternal(null, progress, 0, null);
    }

    public void showMultiProgressIndicator(@IntRange(from=-1,to=100) int progress, @IntRange(from=-1,to=100) int secondaryProgress) {
        showMultiProgressIndicator(null, progress, secondaryProgress);
    }

    public void hideProgressIndicator() {
        setVisibility(GONE);
    }

    public void showMultiProgressIndicator(@StringRes int progressTextRes, @IntRange(from=-1,to=100) int progress, @IntRange(from=-1,to=100) int secondaryProgress, View.OnClickListener actionListener) {
        progressBar.setIndeterminate(progress < 0 && secondaryProgress < 0);
        showProgressIndicatorInternal(getContext().getString(progressTextRes), progress, secondaryProgress, actionListener);
    }

    public void showMultiProgressIndicator(@StringRes int progressTextRes, @IntRange(from=-1,to=100) int progress, @IntRange(from=-1,to=100) int secondaryProgress) {
        progressBar.setIndeterminate(progress < 0 && secondaryProgress < 0);
        showProgressIndicatorInternal(getContext().getString(progressTextRes), progress, secondaryProgress, null);
    }

    public void showMultiProgressIndicator(@Nullable String progressText, @IntRange(from=-1,to=100) int progress, @IntRange(from=-1,to=100) int secondaryProgress) {
        progressBar.setIndeterminate(progress < 0 && secondaryProgress < 0);
        showProgressIndicatorInternal(progressText, progress, secondaryProgress, null);
    }

    public void showProgressIndicator(@Nullable String progressText, @IntRange(from=-1,to=100) int progress) {
        progressBar.setIndeterminate(progress < 0);
        showProgressIndicatorInternal(progressText, progress, 0,null);
    }

    public void showProgressIndicator(@StringRes int textResId, @IntRange(from=-1,to=100) int progress, View.OnClickListener actionListener) {
        showProgressIndicator(getContext().getString(textResId), progress, actionListener);
    }

    public void showProgressIndicator(@Nullable String progressText, @IntRange(from=-1,to=100) int progress, View.OnClickListener actionListener) {
        progressBar.setIndeterminate(progress < 0);
        showProgressIndicatorInternal(progressText, progress, 0, actionListener);
    }


    private void showProgressIndicatorInternal(@Nullable String progressText, @IntRange(from=-1,to=100) int progress, @IntRange(from=-1,to=100) int secondaryProgress, View.OnClickListener actionListener) {

        setVisibility(VISIBLE);

        actionButton.setVisibility(actionListener != null ? VISIBLE : GONE);
        actionButton.setOnClickListener(actionListener);

        if(progressText != null) {
            if(!Objects.equals(descriptionField.getText(), progressText)) {
                descriptionField.setText(progressText);
            }
            if(descriptionField.getVisibility() != VISIBLE) {
                descriptionField.setVisibility(VISIBLE);
            }
        } else {
            descriptionField.setVisibility(GONE);
        }

        if(progressBar.getSecondaryProgress() != secondaryProgress) {
            progressBar.setSecondaryProgress(secondaryProgress);
        }
        if(progressBar.getProgress() != progress) {
            progressBar.setProgress(progress);
        }
    }

    public void updateProgressIndicator(@IntRange(from=0,to=100) int progress) {

        progressBar.setIndeterminate(progress < 0);
        progressBar.setProgress(progress);
        progressBar.requestLayout();
    }

    public boolean isVisible() {
        return getVisibility() == VISIBLE;
    }
}
