package com.example.eye_track.utils;

public class FFmpegJNI {
    static {
        System.loadLibrary("avdevice");
    }

    public native int runFFmpegCommand(String[] command);
}
