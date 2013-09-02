package com.nicholasquirk.comicviewer;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.PointF;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Vibrator;
import android.util.FloatMath;
import android.view.Display;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;
import android.view.animation.Animation;
import android.view.animation.Animation.AnimationListener;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;
import android.widget.ImageView.ScaleType;
import android.widget.ScrollView;
import android.widget.Toast;

/**
 *
 * @author Nicholas Quirk
 *
 */
public class ImageViewer extends Activity implements OnTouchListener, AnimationListener {

    //private static final String TAG = "ImageViewer";
    private String imagesPath = null;
    private String currImageName = null;
    private ImageView imageView = null;
    private ArrayList<String> fileNamesList = null;
    private Integer currImageIndex = null;
    private int screenWidth = 0;
    private boolean hasReachedEnd = false;
    //Global action references.
    static final int NONE = 0;
    static final int DRAG = 1;
    static final int ZOOM = 2;
    static final int LONG = 3;
    //References to the current and last action.
    private int mode = NONE;
    private int prevMode = NONE;
    //Matrix transformation values.
    private PointF mid = new PointF();
    private float oldDist = 1f;
    //Matrices for pinch zooming.
    private Matrix matrix = new Matrix();
    private Matrix savedMatrix = new Matrix();

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.image_view);

        Display display = getWindowManager().getDefaultDisplay();
        this.screenWidth = display.getWidth();

        Bundle extras;

        if (savedInstanceState != null) {
            this.fileNamesList = savedInstanceState.getStringArrayList("fileNamesList");
            this.imagesPath = savedInstanceState.getString("imagesPath");
            this.currImageName = savedInstanceState.getString("currImageName");
            this.currImageIndex = savedInstanceState.getInt("currImageIndex");
        } else {
            extras = getIntent().getExtras();
            if (extras != null) {
                if (extras.getBoolean("loadLastPage")) {
                    this.imagesPath = extras.getString("imagesPath");
                    this.currImageName = extras.getString("currImageName");
                    this.currImageIndex = extras.getInt("currImageIndex");
                } else {
                    this.imagesPath = extras.getString("imagesPath");
                }
            }
        }

        if (!hasFileName(this.imagesPath, this.currImageName)) {
            if (this.currImageIndex == null) {
                File dir = new File(this.imagesPath);
                String[] fileNames = dir.list(new ImageExt());
                this.fileNamesList = new ArrayList<String>(Arrays.asList(fileNames));
                Collections.sort(this.fileNamesList);
                this.currImageIndex = 0;
                this.currImageName = this.fileNamesList.get(this.currImageIndex);
            } else {
                this.currImageName = this.fileNamesList.get(this.currImageIndex);
            }
        } else {
            File dir = new File(this.imagesPath);
            String[] fileNames = dir.list();
            this.fileNamesList = new ArrayList<String>(Arrays.asList(fileNames));
            Collections.sort(this.fileNamesList);
        }

        lockScreenBrightness();
        loadImage();
    }

    private void lockScreenBrightness() {
        android.provider.Settings.System.putInt(
                getContentResolver(),
                android.provider.Settings.System.SCREEN_BRIGHTNESS,
                255);
    }

    @Override
    public void onPause() {
        super.onPause();
        FileOutputStream fos;
        PrintStream ps;
        try {
            fos = openFileOutput(Main.lastPageFile, Context.MODE_PRIVATE);
            ps = new PrintStream(fos);
            ps.println(this.imagesPath);
            ps.println(this.currImageName);
            ps.println(this.currImageIndex);
            ps.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
    }

    public void onResume() {
        super.onResume();
        FileInputStream fis;
        BufferedReader br = null;

        try {
            fis = openFileInput(Main.lastPageFile);
            br = new BufferedReader(new InputStreamReader(fis));
            this.imagesPath = br.readLine();
            this.currImageName = br.readLine();
            this.currImageIndex = Integer.parseInt(br.readLine());
            File dir = new File(this.imagesPath);
            String[] fileNames = dir.list(new ImageExt());
            this.fileNamesList = new ArrayList<String>(Arrays.asList(fileNames));
            Collections.sort(this.fileNamesList);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        int ea = event.getAction();

        //dumpEvent(event);

        switch (ea & MotionEvent.ACTION_MASK) {
            case MotionEvent.ACTION_DOWN:
                break;
            case MotionEvent.ACTION_MOVE:
                if (this.mode == ZOOM) {
                    float newDist = spacing(event);
                    if (newDist > 10f) {
                        this.matrix.set(savedMatrix);
                        float scale = newDist / oldDist;
                        this.matrix.postScale(scale, scale, this.mid.x, this.mid.y);
                        this.imageView.setImageMatrix(this.matrix);
                    }
                }
                break;
            case MotionEvent.ACTION_UP:
                if (this.mode == ZOOM) {
                    resetZoomedImage();
                } else if (this.mode == NONE && this.prevMode == NONE) {
                    if ((this.screenWidth * .75) < event.getX()) {
                        viewNextPage();
                        vibrateOnPageTurn();
                    } else if ((this.screenWidth * .25) > event.getX()) {
                        viewPrevPage();
                        vibrateOnPageTurn();
                    } else if ((this.screenWidth * .25) < event.getX() && (this.screenWidth * .75) > event.getX()) {
                        Toast.makeText(getApplicationContext(), String.format("page %d of %d", this.currImageIndex + 1, this.fileNamesList.size()), Toast.LENGTH_SHORT).show();
                    }
                }
                this.mode = NONE;
                this.prevMode = NONE;
                break;
            case MotionEvent.ACTION_POINTER_DOWN:
                this.oldDist = spacing(event);
                if (this.oldDist > 10f) {
                    this.savedMatrix.set(this.matrix);
                    midPoint(this.mid, event);
                    this.mode = ZOOM;
                    this.imageView.setScaleType(ScaleType.MATRIX);
                }
                break;
            case MotionEvent.ACTION_POINTER_UP:
                resetZoomedImage();
                break;
        }

        this.imageView.invalidate();
        return true;
    }

    private void resetZoomedImage() {
        //Update action modes.
        this.mode = NONE;
        this.prevMode = ZOOM;

        //Reset these values to enable the elastic zooming.
        this.mid = new PointF();
        this.oldDist = 1f;
        this.matrix = new Matrix();
        this.savedMatrix = new Matrix();

        loadImage();
    }

    private void loadImage() {

        File f = new File(this.imagesPath, this.currImageName);
        Drawable drawable = null;

        if (this.imageView != null) {
            drawable = this.imageView.getDrawable();
        }

        if (drawable != null) {
            BitmapDrawable bitmapDrawable = (BitmapDrawable) drawable;
            if (bitmapDrawable != null) {
                if (bitmapDrawable.getBitmap() != null) {
                    bitmapDrawable.getBitmap().recycle();
                }
            }
        }

        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inPreferredConfig = Bitmap.Config.RGB_565;
        options.inTempStorage = new byte[32 * 1024];

        Bitmap bitmapTemp = BitmapFactory.decodeFile(f.getAbsolutePath());
        if (bitmapTemp.getWidth() > bitmapTemp.getHeight()) {
            options.inSampleSize = 2;
        }
        bitmapTemp = null;

        Bitmap bitmap = BitmapFactory.decodeFile(f.getAbsolutePath(), options);
        Bitmap mutableBitmap = bitmap.copy(Bitmap.Config.RGB_565, true);

        this.imageView = (ImageView) findViewById(R.id.imageview);
        this.imageView.setOnTouchListener(this);
        this.imageView.setAdjustViewBounds(true);
        this.imageView.setImageBitmap(mutableBitmap);
        this.imageView.setDrawingCacheEnabled(true);
        this.imageView.setLongClickable(true);

        if (this.imageView.getParent() instanceof ScrollView) {
            ScrollView sv = (ScrollView) this.imageView.getParent();
            sv.scrollTo(0, 0);
        }

        this.imageView.setAlpha(255);
    }

    private void viewNextPage() {
        if (this.currImageIndex < (this.fileNamesList.size() - 1)) {
            this.currImageIndex++;
            this.currImageName = this.fileNamesList.get(this.currImageIndex);

            Animation a = AnimationUtils.loadAnimation(getApplicationContext(), R.anim.fade_out);
            a.setAnimationListener(this);
            this.imageView.startAnimation(a);
        } else if (this.currImageIndex == (this.fileNamesList.size() - 1)) {
            if (this.hasReachedEnd == false) {
                Toast.makeText(getApplicationContext(), "fin", Toast.LENGTH_SHORT).show();
                this.hasReachedEnd = true;
            }
        }
    }

    private void viewPrevPage() {
        if (this.currImageIndex > 0) {
            this.currImageIndex--;
            this.currImageName = this.fileNamesList.get(this.currImageIndex);
            this.hasReachedEnd = false;
            this.currImageName = this.fileNamesList.get(this.currImageIndex);

            Animation a = AnimationUtils.loadAnimation(getApplicationContext(), R.anim.fade_out);
            a.setAnimationListener(this);
            this.imageView.startAnimation(a);
        }
    }

    private void selectPage() {
        File f = new File(this.imagesPath);
        int itemsNum = f.list(new ImageExt()).length;
        final CharSequence[] items = new CharSequence[itemsNum];

        for (int i = 0; i < items.length; i++) {
            int j = i + 1;
            items[i] = j + "";
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Page Number");
        builder.setItems(items, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int item) {
                Integer pageIndex = Integer.valueOf(items[item].toString());
                ImageViewer.this.currImageIndex = pageIndex - 1;
                ImageViewer.this.currImageName = ImageViewer.this.fileNamesList.get(ImageViewer.this.currImageIndex);
                loadImage();
            }
        });
        AlertDialog alert = builder.create();
        alert.show();
    }

    public void onSaveInstanceState(Bundle savedInstanceState) {
        savedInstanceState.putStringArrayList("fileNamesList", this.fileNamesList);
        savedInstanceState.putString("imagesPath", this.imagesPath);
        savedInstanceState.putString("currImageName", this.currImageName);
        savedInstanceState.putInt("currImageIndex", this.currImageIndex);
        super.onSaveInstanceState(savedInstanceState);
    }

    public void vibrateOnPageTurn() {
        Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        v.vibrate(25);
    }

    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.image_view, menu);
        return true;
    }

    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.go_to_first_page:
                this.currImageIndex = 0;
                this.currImageName = this.fileNamesList.get(this.currImageIndex);
                loadImage();
                break;
            case R.id.go_to_last_page:
                this.currImageIndex = this.fileNamesList.size() - 1;
                this.currImageName = this.fileNamesList.get(this.currImageIndex);
                loadImage();
                break;
            case R.id.go_to:
                selectPage();
                break;
            case R.id.close:
                terminate();
                break;
        }
        return false;
    }

    public void terminate() {
        super.onDestroy();
        this.finish();
    }

    private boolean hasFileName(String dir, String filename) {
        File f = new File(dir);
        if (f != null && f.isDirectory()) {
            String[] filesList = f.list();
            if (filesList != null && filesList.length > 0) {
                for (String s : filesList) {
                    if (s.equals(filename)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Determine the space between the first two fingers.
     *
     * @param event
     * @return
     */
    private float spacing(MotionEvent event) {
        float x = event.getX(0) - event.getX(1);
        float y = event.getY(0) - event.getY(1);
        return FloatMath.sqrt(x * x + y * y);
    }

    /**
     * Calculate the mid point of the first two fingers.
     *
     * @param point
     * @param event
     */
    private void midPoint(PointF point, MotionEvent event) {
        float x = event.getX(0) + event.getX(1);
        float y = event.getY(0) + event.getY(1);
        point.set(x / 2, y / 2);
    }

    @Override
    public void onAnimationEnd(Animation animation) {
        loadImage();
    }

    @Override
    public void onAnimationRepeat(Animation animation) {
        // TODO Auto-generated method stub
    }

    @Override
    public void onAnimationStart(Animation animation) {
        // TODO Auto-generated method stub
    }

    class ImageExt implements FilenameFilter {

        String ext;

        public ImageExt() {
        }

        public ImageExt(String ext) {
            this.ext = ("." + ext).toLowerCase();
        }

        public boolean accept(File dir, String name) {
            return (name.toLowerCase().endsWith("jpeg") || name.toLowerCase().endsWith("jpg") || name.toLowerCase().endsWith("gif") || name.toLowerCase().endsWith("png"));
        }
    }
}
