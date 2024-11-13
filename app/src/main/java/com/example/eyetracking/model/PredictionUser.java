package com.example.eyetracking.model;

public class PredictionUser {
    private String userId;
    private byte[] images;
    private int age;
    private int gender;
    private long relativeTime;
    private int videoIndex;

    public PredictionUser(String userId,byte[] images,int age,int gender,long relativeTime,int videoIndex){
        this.userId=userId;
        this.images=images;
        this.age=age;
        this.gender=gender;
        this.relativeTime=relativeTime;
        this.videoIndex=videoIndex;
    }

}
