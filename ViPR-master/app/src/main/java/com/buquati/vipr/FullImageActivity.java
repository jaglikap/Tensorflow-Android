package com.buquati.vipr;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.TextView;

import com.buquati.vipr.adapters.MyAdapter;

public class FullImageActivity extends Activity {
    private TextView txtView ;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_full_image);
        // get intent data
        Intent i = getIntent();

        // Selected image id
        int position = i.getExtras().getInt("id");
        byte[] photo_bytes = i.getExtras().getByteArray("item");
        String name = i.getExtras().getString("image_name");
        Bitmap photo = BitmapFactory.decodeByteArray(photo_bytes, 0, photo_bytes.length);
        MyAdapter myAdapter = new MyAdapter(this);

        ImageView imageView = (ImageView) findViewById(R.id.full_image_view);
        txtView=(TextView)findViewById(R.id.image_name);
        imageView.setImageBitmap(photo);
        txtView.setText(name);
        //imageView.setImageResource(myAdapter. mThumbIds[position]);
    }
}
