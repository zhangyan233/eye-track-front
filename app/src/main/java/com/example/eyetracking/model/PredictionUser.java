package com.example.eyetracking.model;

public class PredictionUser {
    private String userId;
    private byte[] images;
    private int age;
    private int gender;

    public PredictionUser(String userId,byte[] images,int age,int gender){
        this.userId=userId;
        this.images=images;
        this.age=age;
        this.gender=gender;
    }

}
