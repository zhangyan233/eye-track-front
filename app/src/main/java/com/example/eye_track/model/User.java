package com.example.eye_track.model;

public class User {
    private String userId;
    private byte[] images;
    private int age;
    private int gender;

    public User(String userId,byte[] images,int age,int gender){
        this.userId=userId;
        this.images=images;
        this.age=age;
        this.gender=gender;
    }
}
