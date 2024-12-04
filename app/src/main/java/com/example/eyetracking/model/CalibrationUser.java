package com.example.eyetracking.model;

public class CalibrationUser {
    private String images;
    private int[] coordinates;

    public CalibrationUser(String images,int[] coordinates){
        this.images=images;
        this.coordinates=coordinates;
    }
}
