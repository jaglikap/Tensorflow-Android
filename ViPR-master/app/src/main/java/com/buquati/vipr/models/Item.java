package com.buquati.vipr.models;

import android.graphics.Bitmap;

public class Item {
    public final String name;
    //public final int drawableId;
    public Bitmap photo;
    public Item(String name, Bitmap photo) {
        this.name = name;
        this.photo = photo;
        //this.drawableId = drawableId;
    }

    Item(String name, Bitmap photo, int drawableId) {
        this.name = name;
        this.photo = photo;
        // this.drawableId = drawableId;
    }
}
