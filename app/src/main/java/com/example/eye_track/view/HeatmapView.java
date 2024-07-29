package com.example.eye_track.view;

import static androidx.camera.extensions.ExtensionsManager.init;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ImageFormat;
import android.graphics.Paint;
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

    private Map<String,Integer> coordinates=new HashMap<>();
    private Paint paint;
    private int maxFrequency = 1;


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

    private void init(){
        paint = new Paint();
        paint.setStyle(Paint.Style.FILL);
    }

    public void updateCoordinates(Map<String, Integer> newCoordinates){
        coordinates=newCoordinates;
        invalidate();
    }

    public HeatmapView(Context context, @Nullable AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    @Override
    protected void onDraw(Canvas canvas){
        super.onDraw(canvas);

        for (Map.Entry<String, Integer> entry : coordinates.entrySet()) {
            String[] coords = entry.getKey().split(",");
            float x = Float.parseFloat(coords[0]);
            float y = Float.parseFloat(coords[1]);
            int count = entry.getValue();
            paint.setColor(getColor(count));
            canvas.drawCircle(x, y, 30, paint);
        }
    }

    private int getColor(int count) {
        float ratio = (float) count / maxFrequency;
        int alpha = (int) (255 * ratio);
        int red = 255;
        int green = (int) (255 * (1 - ratio));
        return Color.argb(alpha, red, green, 0); //
    }


}
