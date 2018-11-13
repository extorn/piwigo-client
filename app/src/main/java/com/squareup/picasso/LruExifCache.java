package com.squareup.picasso;

import android.content.Context;
import android.graphics.Bitmap;

import com.drew.metadata.Metadata;

import org.greenrobot.eventbus.EventBus;

import delit.piwigoclient.ui.events.ExifDataRetrievedEvent;

public class LruExifCache extends BaseLruExifCache<Metadata> {
    public LruExifCache(Context context) {
        super(context);
    }

    public LruExifCache(int maxSize) {
        super(maxSize);
    }

    @Override
    protected void onImageRetrieved(String uri, Bitmap mapValue, Metadata metadata) {
        EventBus.getDefault().post(new ExifDataRetrievedEvent(uri, metadata));
    }
}
