package delit.piwigoclient.ui.upload;

import android.content.Context;
import android.graphics.Color;
import android.support.annotation.NonNull;
import android.support.v7.widget.AppCompatImageView;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.squareup.picasso.Picasso;

import java.io.File;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import delit.piwigoclient.R;
import delit.piwigoclient.business.ResizingPicassoLoader;
import delit.piwigoclient.model.piwigo.GalleryItem;
import delit.piwigoclient.ui.PicassoFactory;

/**
 * {@link RecyclerView.Adapter} that can display a {@link GalleryItem}
 */
public class FilesToUploadRecyclerViewAdapter extends RecyclerView.Adapter<FilesToUploadRecyclerViewAdapter.ViewHolder> {

    private boolean useDarkMode;
    private final ArrayList<File> filesToUpload;
    private final HashMap<File, Integer> fileUploadProgress = new HashMap<>();
    private RemoveListener listener;
    public static final int VIEW_TYPE_LIST = 0;
    public static final int VIEW_TYPE_GRID = 1;
    private int viewType = VIEW_TYPE_LIST;
    public static final int SCALING_QUALITY_PERFECT = Integer.MAX_VALUE;
    public static final int SCALING_QUALITY_VHIGH = 960;
    public static final int SCALING_QUALITY_HIGH = 480;
    public static final int SCALING_QUALITY_MEDIUM = 240;
    public static final int SCALING_QUALITY_LOW = 120;
    public static final int SCALING_QUALITY_VLOW = 60;
    private int scalingQuality = SCALING_QUALITY_MEDIUM;

    public FilesToUploadRecyclerViewAdapter(ArrayList<File> filesToUpload, @NonNull Context context, RemoveListener listener) {
        this.listener = listener;
        Context context1 = context;
        this.filesToUpload = filesToUpload;
        this.setHasStableIds(true);
    }

    public void setViewType(int viewType) {
        if(viewType != VIEW_TYPE_GRID && viewType != VIEW_TYPE_LIST) {
            throw new IllegalArgumentException("illegal view type");
        }
        this.viewType = viewType;
    }

    public void setUseDarkMode(boolean useDarkMode) {
        this.useDarkMode = useDarkMode;
    }

    @Override
    public long getItemId(int position) {
        // theoretically this should always be identical for one and only one file.
        return new BigInteger(filesToUpload.get(position).getAbsolutePath().getBytes()).longValue();
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view;
        if (viewType == VIEW_TYPE_LIST) {
            view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.list_files_for_upload, parent, false);

        } else if (viewType == VIEW_TYPE_GRID) {
            view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.grid_files_for_upload, parent, false);
        } else {
            throw new IllegalStateException("viewType not supported" + viewType);
        }

        if(useDarkMode) {
            view.setBackgroundColor(Color.WHITE);
        }

        final ViewHolder viewHolder = new ViewHolder(view);

        viewHolder.fileForUploadImageView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(!viewHolder.imageLoader.isImageLoaded()) {
                    viewHolder.imageLoader.load();
                }
            }
        });

        viewHolder.deleteButton.setOnClickListener(new Button.OnClickListener() {
            @Override
            public void onClick(View v) {
                onDeleteButtonClicked(viewHolder, false);
            }
        });
        viewHolder.deleteButton.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                onDeleteButtonClicked(viewHolder, true);
                return true;
            }
        });

        final ViewTreeObserver.OnPreDrawListener predrawListener = new ViewTreeObserver.OnPreDrawListener() {
            @Override
            public boolean onPreDraw() {
                try {
                    if(!viewHolder.imageLoader.isImageLoaded()  && !viewHolder.imageLoader.isImageLoading()) {
                        int imgSize = scalingQuality;
                        if (imgSize == Integer.MAX_VALUE) {
                            imgSize = viewHolder.fileForUploadImageView.getMeasuredWidth();
                        } else {
                            // need that math.max to ensure that the image size remains positive
                            //FIXME How can this ever be called before the ImageView object has a size?
                            imgSize = Math.max(SCALING_QUALITY_VLOW,Math.min(scalingQuality, viewHolder.fileForUploadImageView.getMeasuredWidth()));
                        }
                        viewHolder.imageLoader.setResizeTo(imgSize, imgSize);
                        viewHolder.imageLoader.load();
                    }
                } catch(IllegalStateException e) {
                    // image loader not configured yet...
                }
                return true;
            }
        };
        viewHolder.fileForUploadImageView.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override
            public void onViewAttachedToWindow(View v) {
                viewHolder.fileForUploadImageView.getViewTreeObserver().addOnPreDrawListener(predrawListener);
            }

            @Override
            public void onViewDetachedFromWindow(View v) {
                viewHolder.fileForUploadImageView.getViewTreeObserver().removeOnPreDrawListener(predrawListener);
            }
        });

        return viewHolder;
    }

    public void remove(File item) {
        filesToUpload.remove(item);
        fileUploadProgress.remove(item);
        notifyDataSetChanged();
    }

    protected void onDeleteButtonClicked(ViewHolder viewHolder, boolean longClick) {
        listener.onRemove(this, viewHolder.mItem, longClick);
    }

    @Override
    public int getItemViewType(int position) {
        // only storing items of one type.
        return viewType;
    }

    @Override
    public void onBindViewHolder(final ViewHolder holder, final int position) {
        final File itemToView = filesToUpload.get(position);

        View itemView = holder.mView;

        // Configure the progress bar
        Integer progress = fileUploadProgress.get(itemToView);

        if (progress != null) {
            holder.progressBar.setVisibility(View.VISIBLE);
            holder.progressBar.setIndeterminate(false);
            holder.progressBar.setProgress(progress);
        } else {
            holder.progressBar.setVisibility(View.GONE);
            holder.progressBar.setIndeterminate(false);
            holder.progressBar.setProgress(0);
        }

        // Now we've updated the progress bar, we can return, no need to reload the remainder of the fields as they won't have altered.
        if (holder.getOldPosition() < 0 && holder.mItem != null && holder.mItem.equals(itemToView)) {
            return;
        }

        // store the item in this recyclable holder.
        holder.mItem = itemToView;

        // configure the filename field
        holder.fileNameField.setText(itemToView.getName());

        // configure the image loader
        holder.imageLoader.setFileToLoad(itemToView);

    }

    @Override
    public int getItemCount() {
        return filesToUpload.size();
    }

    public void selectAllItemIds() {

        notifyItemRangeChanged(0, getItemCount());
    }

    public void updateProgressBar(File fileBeingUploaded, int percentageComplete) {
        fileUploadProgress.put(fileBeingUploaded, percentageComplete);
        notifyDataSetChanged();
    }

    public ArrayList<File> getFiles() {
        ArrayList<File> currentContent = new ArrayList<>(filesToUpload.size());
        currentContent.addAll(filesToUpload);
        return currentContent;
    }

    public void addAll(List<File> filesForUpload) {
        filesToUpload.addAll(filesForUpload);
        notifyDataSetChanged();
    }

    public void clear() {
        filesToUpload.clear();
        fileUploadProgress.clear();
        notifyDataSetChanged();
    }

    public class ViewHolder extends RecyclerView.ViewHolder {
        public final View mView;
        private final ProgressBar progressBar;
        private final TextView fileNameField;
        private final ImageButton deleteButton;
        private final AppCompatImageView fileForUploadImageView;
        private final ResizingPicassoLoader imageLoader;
        public File mItem;


        public ViewHolder(View view) {
            super(view);
            mView = view;
            progressBar = itemView.findViewById(R.id.file_for_upload_progress);
            fileNameField = itemView.findViewById(R.id.file_for_upload_txt);
            deleteButton = itemView.findViewById(R.id.file_for_upload_delete_button);
            PicassoFactory.getInstance().getPicassoSingleton().load(R.drawable.ic_delete_black_24px).into(deleteButton);
            fileForUploadImageView = itemView.findViewById(R.id.file_for_upload_img);

            imageLoader = new ResizingPicassoLoader(fileForUploadImageView, 0, 0);
        }

        @Override
        public String toString() {
            return super.toString() + " '" + mItem.getName() + "'";
        }
    }

    public interface RemoveListener {
        void onRemove(FilesToUploadRecyclerViewAdapter adapter, File itemToRemove, boolean longClick);
    }
}
