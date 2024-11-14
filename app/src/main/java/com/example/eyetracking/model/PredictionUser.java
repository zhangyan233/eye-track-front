package com.example.eyetracking.model;

public class PredictionUser {
    //private String userId;
    private byte[] images;
    //private int age;
    //private int gender;
    private String[] videos;
    private long relativeTime;
    private int videoIndex;

    public PredictionUser(String[] videos,byte[] images,long relativeTime,int videoIndex){
        this.images=images;
        this.videos=videos;
        this.relativeTime=relativeTime;
        this.videoIndex=videoIndex;
    }

}
