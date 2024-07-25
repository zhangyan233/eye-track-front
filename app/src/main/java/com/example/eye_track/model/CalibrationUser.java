package com.example.eye_track.model;

public class CalibrationUser {
    private String userId;
    private byte[] images;
    private int age;
    private int gender;
    private int[] coordinates;

    public CalibrationUser(String userId,byte[] images,int age,int gender,int[] coordinates){
        this.userId=userId;
        this.images=images;
        this.age=age;
        this.gender=gender;
        this.coordinates=coordinates;
    }
}
