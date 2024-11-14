package com.example.eyetracking.model;

public class CalibrationUser {
    private byte[] images;
    private int[] coordinates;

    public CalibrationUser(byte[] images,int[] coordinates){
        this.images=images;
        this.coordinates=coordinates;
    }
}
