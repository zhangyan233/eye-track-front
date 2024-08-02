package com.example.eye_track.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

public class OverlayView extends View {
    private float x=-1;
    private float y=-1;
    private Paint paint;

    public OverlayView(Context context) {
        super(context);
    }

    public OverlayView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        paint=new Paint();
        paint.setColor(Color.RED);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(5);
    }

    public OverlayView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public OverlayView(Context context, @Nullable AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    public void updatePoint(float x,float y){
        this.x=x;
        this.y=y;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (x >= 0 && y >= 0) {
            canvas.drawCircle(x, y, 50, paint); // 在 (x, y) 位置绘制一个半径为 10 的圆
        }
    }
}
