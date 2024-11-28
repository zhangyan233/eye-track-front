package com.example.eyetracking.model;

import com.google.common.primitives.Bytes;

import java.util.List;

public class PredictionUser {
    //private String userId;
    private byte[] images;
    //private int age;
    //private int gender;
    private List<String> videos;
    private long relativeTime;
    private int videoIndex;

    public PredictionUser(List<String> videos,byte[] images,long relativeTime,int videoIndex){
        this.images=images;
        this.videos=videos;
        this.relativeTime=relativeTime;
        this.videoIndex=videoIndex;
    }

}
