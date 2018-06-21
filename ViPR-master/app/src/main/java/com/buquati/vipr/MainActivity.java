package com.buquati.vipr;

import android.Manifest;
import android.app.Activity;
import android.app.ProgressDialog;
import android.content.ActivityNotFoundException;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.FileProvider;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.buquati.vipr.adapters.MyAdapter;
import com.buquati.vipr.models.Item;
import com.buquati.vipr.utils.Utility;

import org.json.JSONException;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;


public class MainActivity extends AppCompatActivity {
    public File file;
    public Uri uri;
    private int REQUEST_CAMERA = 0, SELECT_FILE = 1;
    private Button btnSearch;
    private ImageView ivImage,imageDrawingPane;
    public float proba=0;
    private String userChoosenTask;
    Canvas canvasMaster;
    Canvas canvasDrawingPane;
    public Bitmap bitmapMaster;
    public Bitmap tempBitmapMaster;
    public Bitmap bitmapDrawingPane;
    public Bitmap globalBitmap;
    public String imagePath;
    private TextView infoText;
    private TextView infoResults;
    private FloatingActionButton fab;
    ProgressDialog dialog;
    public String mCurrentPhotoPath;
    public Uri photoURI;

    /*TensorFlow Android Part*/
    private static AssetManager assetManager;
    private static final String MODEL_FILE ="file:///android_asset/bqt_mugs_224_mobilenetV2_graph.pb"; //"file:///android_asset/frozen_inference_graph.pb";
    private static final String LABEL_FILE = "bqt_mugs_224_tflite_product_map.json";//"bqt_mugs_product_map.json";
    private static final String INPUT_NODE = "Placeholder:0";
    private static final String RESULTS = "final_result";
    private Classifier classifier;
   public ArrayList<Item> imageItems = new ArrayList<>();

   public int x=0;
   public int y=0;
   public int w=0;
   public int h=0;

    static final int REQUEST_TAKE_PHOTO = 1;

    private void dispatchTakePictureIntent() {
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        // Ensure that there's a camera activity to handle the intent
        if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
            // Create the File where the photo should go
            File photoFile = null;
            try {
                photoFile = createImageFile();
            } catch (IOException ex) {
                Toast.makeText(getApplicationContext(),"Errir while creating File",Toast.LENGTH_LONG).show();
                // Error occurred while creating the File
           // ...
            }
            // Continue only if the File was successfully created
            if (photoFile != null) {
                photoURI = FileProvider.getUriForFile(this,"com.buquati.vipr.fileprovider",photoFile);
                //Uri photoURI = FileProvider.getUriForFile(this,"com.buquati.vipr.fileprovider",photoFile);
                takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI);
                //startActivityForResult(takePictureIntent, REQUEST_TAKE_PHOTO);
                startActivityForResult(takePictureIntent, REQUEST_CAMERA);
            }
        }
    }
    private void galleryAddPic() {
        Intent mediaScanIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
        File f = new File(mCurrentPhotoPath);
        Uri contentUri = Uri.fromFile(f);
        mediaScanIntent.setData(contentUri);
        this.sendBroadcast(mediaScanIntent);
    }


    private File createImageFile() throws IOException {
        // Create an image file name
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String imageFileName = "JPEG_" + timeStamp + "_";
        File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        File image = File.createTempFile(
                imageFileName,  /* prefix */
                ".jpg",         /* suffix */
                storageDir      /* directory */
        );

        // Save a file: path for use with ACTION_VIEW intents
        imagePath=image.getAbsolutePath();
        mCurrentPhotoPath = image.getAbsolutePath();
        return image;
    }
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        imagePath=null;
        imageItems = new ArrayList<>();
        dialog = new ProgressDialog(MainActivity.this);
        dialog.setTitle("Please be patient");
        dialog.setMessage("Searching the matching products");

        GridView gridView = (GridView)findViewById(R.id.gridview);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        this.assetManager = this.getApplicationContext().getAssets();
        infoResults = (TextView)findViewById(R.id.infoResults);
        infoResults.setVisibility(View.INVISIBLE);
        fab = (FloatingActionButton) findViewById(R.id.fab);
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            fab.setEnabled(false);//new
            ActivityCompat.requestPermissions(this, new String[] { Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE }, 0);
        }
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                selectImage();
            }
        });

        infoText = (TextView)findViewById(R.id.infoText);

        btnSearch = (Button)findViewById(R.id.search_button);

        btnSearch.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {


                byte[] byteArray;
                ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();

                if(ivImage == null)
                    return;
                else if (globalBitmap == null)
                    return;
                else {

                    Bitmap bm = BitmapFactory.decodeFile(imagePath);
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    bm.compress(Bitmap.CompressFormat.JPEG, 100, baos);
                    byte[] byteArrayImage = baos.toByteArray();
                    String encodedImage = Base64.encodeToString(byteArrayImage, Base64.DEFAULT);

                    tempBitmapMaster.compress(Bitmap.CompressFormat.PNG, 100, byteArrayOutputStream);
                    try {
                       // classifier = TensorFlowImageClassifier.create(assetManager,MODEL_FILE,LABEL_FILE,bm.getWidth(),INPUT_NODE,RESULTS);
                        classifier = TensorFlowImageClassifier.create(assetManager,MODEL_FILE,LABEL_FILE,globalBitmap.getWidth(),INPUT_NODE,RESULTS);
                    } catch (IOException e) {
                        e.printStackTrace();
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                    //final List<Classifier.Recognition> results = classifier.recognizeImage(bm,assetManager);
                    final List<Classifier.Recognition> results = classifier.recognizeImage(globalBitmap,assetManager);
                    for(int l=0;l<results.size();l++){
                        imageItems.add(results.get(l).getItemRecognt());
                    }
                    Log.i("Size ",String.valueOf(imageItems.size()));

                    Log.i("DONE","Getting the results done ...");
                    Toast.makeText(getApplicationContext(),"Getting the results..",Toast.LENGTH_LONG).show();
                    btnSearch.setVisibility(View.INVISIBLE);
                    infoResults.setVisibility(View.VISIBLE);
                    gridView.setAdapter(new MyAdapter(getApplicationContext(),imageItems));

                }
            }
        });

        gridView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
               // Toast.makeText(getApplicationContext(),"Position:"+position,Toast.LENGTH_LONG).show();
                // Sending image id to FullScreenActivity
                Intent i = new Intent(getApplicationContext(), FullImageActivity.class);
                // passing array index
                i.putExtra("id", position);
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                Bitmap bm = imageItems.get(position).photo;
                bm.compress(Bitmap.CompressFormat.PNG, 100, baos);
                byte[] byteArrayImage = baos.toByteArray();
                i.putExtra("item",byteArrayImage);
                i.putExtra("image_name",imageItems.get(position).name);
                startActivity(i);

            }
        });


        imageDrawingPane = (ImageView)findViewById(R.id.drawingpane);

        ivImage = (ImageView) findViewById(R.id.iv);

    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == 0){
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED && grantResults[1] == PackageManager.PERMISSION_GRANTED) {
                    fab.setEnabled(true);//new
                } else {
                    //code for deny
                }

        }
    }
/**
 *  This method shows up a dialog offering 3 options to the user: Take a photo, Choose from Gallery and Cancel the dialog
 * **/
    private void selectImage() {
        if(ivImage!=null)
            ivImage.setImageBitmap(null);
      /*  if(imageDrawingPane!=null)
            imageDrawingPane.setImageBitmap(null);
        if(canvasMaster!=null)
            canvasMaster.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
        if(canvasDrawingPane!=null)
            canvasDrawingPane.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);*/
        final CharSequence[] items = { "Choose from Gallery","Take a Photo",
                "Cancel"};

        AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
        builder.setTitle("Add Photo");
        builder.setItems(items, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int item) {
                boolean result = Utility.checkPermission(MainActivity.this);
                //if the selected option is Take a Photo then we fire up the intent which will launch the phone's camera
                if (items[item].equals("Take a Photo")) {
                    userChoosenTask = "Take a Photo";
                    if (result){
                        //cameraIntent();
                        dispatchTakePictureIntent();
                    }

                    // else if the selected option is Choose from Gallery then we fire up the intent which will launch the phone's gallery
                } else if (items[item].equals("Choose from Gallery")) {
                    userChoosenTask = "Choose from Gallery";
                    if (result)
                        galleryIntent();
                    //else if the selected option is Cancel then the dialog is dismissed
                } else if (items[item].equals("Cancel")) {
                    dialog.dismiss();
                }
            }
        });
        builder.show();
    }

    private void galleryIntent() {
        Intent galleryIntent = new Intent(Intent.ACTION_PICK,
                android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        startActivityForResult(galleryIntent, SELECT_FILE);
    }

    private void cameraIntent() {
        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        startActivityForResult(intent, REQUEST_CAMERA);

        /*
        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        file = new File(Environment.getExternalStorageDirectory(),
                "file" + String.valueOf(System.currentTimeMillis()) + ".jpg");
        uri = Uri.fromFile(file);
        intent.putExtra(android.provider.MediaStore.EXTRA_OUTPUT, uri);
        intent.putExtra("return-data", true);
        startActivityForResult(intent, REQUEST_CAMERA);
        */
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode == Activity.RESULT_OK) {

            if (requestCode == SELECT_FILE) {
                try {
                    onSelectFromGalleryResult(data);
                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                }
            }
            else if (requestCode == REQUEST_CAMERA) {
                try {
                    onCaptureImageResult(data);
                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                }
            }
        }
        /*
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == Activity.RESULT_OK) {
            if (requestCode == SELECT_FILE) {
                if (data != null) {
                    uri = data.getData();
                    ImageCropFunction();
                }
            }
            else if (requestCode == REQUEST_CAMERA) {
                try {
                    ImageCropFunction();
                    onCaptureImageResult(data);
                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                }
            }
            else if(requestCode==3){
                if (data != null) {
                    Bundle bundle = data.getExtras();
                    Bitmap bitmap = bundle.getParcelable("data");
                }
            }
        }*/
    }

    private void onCaptureImageResult(Intent data) throws FileNotFoundException {
        /*Bitmap tempBitmap;
        Bitmap thumbnail = (Bitmap) data.getExtras().get("data");
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        thumbnail.compress(Bitmap.CompressFormat.PNG, 100, bytes);*/
        //prepareCanvasBitmapForDraw(thumbnail);
        infoText.setText(null);
        btnSearch.setVisibility(View.VISIBLE);

        // Get the dimensions of the View
        int targetW = ivImage.getWidth();
        int targetH = ivImage.getHeight();
       // Bitmap bm = BitmapFactory.decodeFile(mCurrentPhotoPath);

        // Get the dimensions of the bitmap
        BitmapFactory.Options bmOptions = new BitmapFactory.Options();
        bmOptions.inJustDecodeBounds = true;
        Bitmap firstBtm = BitmapFactory.decodeFile(mCurrentPhotoPath, bmOptions);
        int photoW = bmOptions.outWidth;
        int photoH = bmOptions.outHeight;

        // Determine how much to scale down the image
        int scaleFactor = Math.min(photoW/targetW, photoH/targetH);

        // Decode the image file into a Bitmap sized to fill the View
        bmOptions.inJustDecodeBounds = false;
        bmOptions.inSampleSize = scaleFactor;
        bmOptions.inPurgeable = true;
        Bitmap mybitmap =BitmapFactory.decodeFile(mCurrentPhotoPath);

        Bitmap bitmap = BitmapFactory.decodeFile(mCurrentPhotoPath, bmOptions);
         globalBitmap = resize(bitmap,224,224);
        Log.i("width bitmap",String.valueOf(bitmap.getWidth()));
        ExifInterface exif = null;
        try {
            File pictureFile = new File(mCurrentPhotoPath);
            exif = new ExifInterface(pictureFile.getAbsolutePath());
        } catch (IOException e) {
            e.printStackTrace();
        }
        int orientation = ExifInterface.ORIENTATION_NORMAL;

        if (exif != null)
            orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);

        switch (orientation) {
            case ExifInterface.ORIENTATION_ROTATE_90:
                mybitmap = rotateBitmap(mybitmap, 90);
                break;
            case ExifInterface.ORIENTATION_ROTATE_180:
                mybitmap = rotateBitmap(mybitmap, 180);
                break;

            case ExifInterface.ORIENTATION_ROTATE_270:
                mybitmap = rotateBitmap(mybitmap, 270);
                break;
        }
        prepareCanvasBitmapForDraw(mybitmap);
       // prepareCanvasBitmapForDraw(bitmap);
        //mImageView.setImageBitmap(bitmap);

    }

    @SuppressWarnings("deprecation")
    private void onSelectFromGalleryResult(Intent data) throws FileNotFoundException {
        Bitmap tempBitmap;
        Bitmap loadedBitmap;
        if (data != null) {
            Uri pickedImage = data.getData();
            tempBitmap = BitmapFactory.decodeStream(getContentResolver().openInputStream(pickedImage));
            String[] filePath = {MediaStore.Images.Media.DATA};
            Cursor cursor = getContentResolver().query(pickedImage, filePath, null, null, null);
            cursor.moveToFirst();
            imagePath = cursor.getString(cursor.getColumnIndex(filePath[0]));
            ExifInterface exif = null;
            try {
                File pictureFile = new File(imagePath);
                exif = new ExifInterface(pictureFile.getAbsolutePath());
            } catch (IOException e) {
                e.printStackTrace();
            }
            int orientation = ExifInterface.ORIENTATION_NORMAL;

            if (exif != null)
                orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);

            switch (orientation) {
                case ExifInterface.ORIENTATION_ROTATE_90:
                    tempBitmap = rotateBitmap(tempBitmap, 90);
                    break;
                case ExifInterface.ORIENTATION_ROTATE_180:
                    tempBitmap = rotateBitmap(tempBitmap, 180);
                    break;

                case ExifInterface.ORIENTATION_ROTATE_270:
                    tempBitmap = rotateBitmap(tempBitmap, 270);
                    break;
            }
            prepareCanvasBitmapForDraw(tempBitmap);
            globalBitmap = tempBitmap;
            infoText.setText(null);
            btnSearch.setVisibility(View.VISIBLE);
        }
    }


    public void prepareCanvasBitmapForDraw(Bitmap tempBitmap){
       // Bitmap myScaledBitmap = resize(tempBitmap,224,224);
        Log.i("width",String.valueOf(tempBitmap.getWidth()));
        Log.i("height",String.valueOf(tempBitmap.getHeight()));
        Bitmap.Config config;

        if(tempBitmap.getConfig() != null){
            config = tempBitmap.getConfig();
        }else{
            config = Bitmap.Config.ARGB_8888;
        }

        bitmapMaster = Bitmap.createBitmap(
                tempBitmap.getWidth(),
                tempBitmap.getHeight(),
                config);
        tempBitmapMaster=  Bitmap.createBitmap(
                tempBitmap.getWidth(),
                tempBitmap.getHeight(),
                config);
        canvasMaster = new Canvas(bitmapMaster);
        //canvasMaster.drawBitmap(tempBitmap, 0, 0, null);//commented
        canvasMaster.drawBitmap(tempBitmap, 0, 0, null);
       // ivImage.setScaleType(ImageView.ScaleType.FIT_CENTER);
        ivImage.setImageBitmap(bitmapMaster);

        bitmapDrawingPane = Bitmap.createBitmap(
                tempBitmap.getWidth(),
                tempBitmap.getHeight(),
                config);
        canvasDrawingPane = new Canvas(bitmapDrawingPane);
        imageDrawingPane.setImageBitmap(bitmapDrawingPane);

    }

    public Bitmap getImageFromUri(String imgpath) {
       /* String[] filePath = {MediaStore.Images.Media.DATA};
        Cursor cursor = getContentResolver().query(uri, filePath, null, null, null);
        cursor.moveToFirst();
        String imagePath = cursor.getString(cursor.getColumnIndex(filePath[0]));
        cursor.close();*/
        Bitmap loadedBitmap = BitmapFactory.decodeFile(imgpath);
        ExifInterface exif = null;
        try {
            File pictureFile = new File(imgpath);
            exif = new ExifInterface(pictureFile.getAbsolutePath());
        } catch (IOException e) {
            e.printStackTrace();
        }
        int orientation = ExifInterface.ORIENTATION_NORMAL;

        if (exif != null)
            orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);

        switch (orientation) {
            case ExifInterface.ORIENTATION_ROTATE_90:
                loadedBitmap = rotateBitmap(loadedBitmap, 90);
                break;
            case ExifInterface.ORIENTATION_ROTATE_180:
                loadedBitmap = rotateBitmap(loadedBitmap, 180);
                break;

            case ExifInterface.ORIENTATION_ROTATE_270:
                loadedBitmap = rotateBitmap(loadedBitmap, 270);
                break;
        }

        return loadedBitmap;
    }

    public static Bitmap rotateBitmap(Bitmap bitmap, int degrees) {
        Matrix matrix = new Matrix();
        matrix.postRotate(degrees);
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
    }

    public static Bitmap scaleBitmapAndKeepRation(Bitmap TargetBmp,int reqHeightInPixels,int reqWidthInPixels)
    {
        Matrix m = new Matrix();
        m.setRectToRect(new RectF(0, 0, TargetBmp.getWidth(), TargetBmp.getHeight()), new RectF(0, 0, reqWidthInPixels, reqHeightInPixels), Matrix.ScaleToFit.CENTER);
        Bitmap scaledBitmap = Bitmap.createBitmap(TargetBmp, 0, 0, TargetBmp.getWidth(), TargetBmp.getHeight(), m, true);
        return scaledBitmap;
    }
    private static Bitmap resize(Bitmap image, int maxWidth, int maxHeight) {
        if (maxHeight > 0 && maxWidth > 0) {
            int width = image.getWidth();
            int height = image.getHeight();
            float ratioBitmap = (float) width / (float) height;
            float ratioMax = (float) maxWidth / (float) maxHeight;

            int finalWidth = maxWidth;
            int finalHeight = maxHeight;
            if (ratioMax > 1) {
                finalWidth = (int) ((float)maxHeight * ratioBitmap);
            } else {
                finalHeight = (int) ((float)maxWidth / ratioBitmap);
            }/**/
            image = Bitmap.createScaledBitmap(image, finalWidth, finalHeight, true);
            return image;
        } else {
            return image;
        }
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        switch (item.getItemId()){
            case R.id.action_refresh:
                Intent refresh = new Intent(getApplicationContext(), MainActivity.class);
                refresh.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK );
                startActivity(refresh);//Start the same Activity
                overridePendingTransition(0, 0);
                finish();
                overridePendingTransition(0, 0);
        }


        return super.onOptionsItemSelected(item);
    }
    public void ImageCropFunction() {
        Intent CropIntent;
        // Image Crop Code
        try {
            CropIntent = new Intent("com.android.camera.action.CROP");
            CropIntent.setDataAndType(uri, "image/*");
            CropIntent.putExtra("crop", "true");
            CropIntent.putExtra("outputX", 224);
            CropIntent.putExtra("outputY", 224);
            CropIntent.putExtra("aspectX", 1);
            CropIntent.putExtra("aspectY", 1);
            CropIntent.putExtra("scaleUpIfNeeded", true);
            CropIntent.putExtra("return-data", true);

            startActivityForResult(CropIntent, 3);

        } catch (ActivityNotFoundException e) {

        }
    }



}
