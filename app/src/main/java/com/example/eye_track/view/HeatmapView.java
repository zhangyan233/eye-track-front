package com.example.eye_track.view;

import static androidx.camera.extensions.ExtensionsManager.init;

import android.content.Context;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ImageFormat;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class HeatmapView extends View{
    private Map<String, Integer> coordinates = new HashMap<>();

    private Paint paint;
    private int maxFrequency = 1;
    private static final int RADIUS = 50;
    private static final int WIDTH = 1365;  // 假设宽度为1365
    private static final int HEIGHT = 720;  // 假设高度为720
    private int[][] frequencyMap;

    public HeatmapView(Context context) {
        super(context);
        init();
    }

    public HeatmapView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public HeatmapView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    public HeatmapView(Context context, @Nullable AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init();
    }

    private void init() {
        paint = new Paint();
        paint.setStyle(Paint.Style.FILL);
        paint.setAntiAlias(true);
        paint.setMaskFilter(new BlurMaskFilter(15, BlurMaskFilter.Blur.NORMAL));
        frequencyMap = new int[WIDTH][HEIGHT];
    }

    public void updateCoordinates(Map<String, Integer> newCoordinates) {
        for (Map.Entry<String, Integer> entry : newCoordinates.entrySet()) {
            String[] coords = entry.getKey().split(",");
            float x = Float.parseFloat(coords[0]);
            float y = Float.parseFloat(coords[1]);
            int count = entry.getValue();
            updateFrequencyMap(x, y, count);
        }
        invalidate();
    }

    private void updateFrequencyMap(float x, float y, int count) {
        for (int i = (int) Math.max(0, x - RADIUS); i < Math.min(WIDTH, x + RADIUS); i++) {
            for (int j = (int) Math.max(0, y - RADIUS); j < Math.min(HEIGHT, y + RADIUS); j++) {
                if (Math.hypot(i - x, j - y) <= RADIUS) {
                    frequencyMap[i][j] += count;
                    if (frequencyMap[i][j] > maxFrequency) {
                        maxFrequency = frequencyMap[i][j];
                    }
                }
            }
        }
    }

    @Override
    protected void onDraw(Canvas canvas){
        super.onDraw(canvas);

        for (int i = 0; i < WIDTH; i++) {
            for (int j = 0; j < HEIGHT; j++) {
                int count = frequencyMap[i][j];
                if (count > 0) {
//                    paint.setColor(getColor(count));
//                    canvas.drawCircle(i, j, RADIUS / 10, paint);
                    paint.setShader(getGradientShader(i, j, count));
                    canvas.drawCircle(i, j, 1, paint);
                }
            }
        }

        paint.setShader(null);
    }
    private Shader getGradientShader(float x, float y, int count) {
        float ratio = (float) count / maxFrequency;
        int[] colors;
        float[] positions = new float[47];
        positions[0]=0f;
        positions[1]=0.1f;
        for (int i = 2; i < 47; i++) {
            positions[i]=0.1f+(i-1)*0.02f;
        }

        if (ratio > 0.5) {
            int len=positions.length;
            // High frequency: Red
            int[] red=new int[len];
            red[0]=Color.RED;
            for (int i = 1; i <len-1; i++) {
                red[i]=Color.argb(255-255/len*i,255,255/len*i,0);
            }
            red[len-1]=Color.YELLOW;
            colors=red;
        } else if (ratio > 0.2) {
            // Medium frequency: Yellow
            int len=positions.length;
            // High frequency: Red
            int[] yellow=new int[len];
            yellow[0]=Color.YELLOW;
            for (int i = 1; i <len-1; i++) {
                yellow[i]=Color.argb(255-255/len*i,255-255/len*i,255,0);
            }
            yellow[len-1]=Color.GREEN;
            colors=yellow;

        } else {
            // Low frequency: Green
            int len=positions.length;
            // High frequency: Red
            int[] green=new int[len];
            green[0]=Color.GREEN;
            for (int i = 1; i <len-1; i++) {
                green[i]=Color.argb(255-255/len*i,0,255-255/len*i,0);
            }
            green[len-1]=Color.TRANSPARENT;
            colors=green;
        }

        return new RadialGradient(x, y, 1, colors, positions, Shader.TileMode.CLAMP);

    }
//    private int getColor(int count) {
//        float ratio = (float) count / maxFrequency;
//        int red, green, blue;
//
//        if (ratio > 0.66) {
//            // High frequency: Red
//            red = 255;
//            green = 0;
//            blue = 0;
//        } else if (ratio > 0.33) {
//            // Medium frequency: Yellow
//            red = 255;
//            green = 255;
//            blue = 0;
//        } else {
//            // Low frequency: Green
//            red = 0;
//            green = 255;
//            blue = 0;
//        }
//
//        return Color.rgb(red, green, blue);
//    }
}
