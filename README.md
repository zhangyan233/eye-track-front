# eye-track-front
Development Tool: Android Studio
SDK Version: minSdk = 24,targetSdk = 34
Android Version: Android Studio 7-12
Device Requirements: Support camera, video, audio

Communication between a WebSocket client and a server
- Calibration: client sends CalibrationUser object with a symbol 'C' to tell server it's an object from calibration step
  Involve info: image(string),coordinates(int[])
## Example Code
```java
  String base64Image=Base64.encodeToString(image, Base64.DEFAULT);
  CalibrationUser user = new CalibrationUser(base64Image,coordinates);
  Gson gson = new Gson();
  String json = gson.toJson(user);
  
  String prefixedJson = "C" + json;
  byte[] jsonBytes = prefixedJson.getBytes(StandardCharsets.UTF_8);
```


- prediction: 
  - Require multiple video urls from server, send a String "RequestVideoURL" and the chosen amount
## Example Code
    ```java
    public void onOpen(ServerHandshake handshake) {
                  webSocketClient.send("RequestVideoURL:"+videoCount);
              }
    ```
  - Receive multiple video urls from server, response based on JSONObject
## Example Code
    ```java
    JSONObject jsonResponse = new JSONObject(message);
    JSONArray urlsArray = jsonResponse.getJSONArray("video_urls");
    videoUrls.clear();

    for (int i = 0; i < urlsArray.length(); i++) {
        videoUrls.add(urlsArray.getString(i).trim());
    }
    ```
    
  - Send PredictionUser object with a symbol 'P' to tell server it's an object from prediction step
  - Involve info: image(string),relativeTime(long),videoUrls(string[]),videoIndex(int)
## Example Code
    ```java
    String base64Image= Base64.encodeToString(image,Base64.DEFAULT);
    PredictionUser user = new PredictionUser(videoUrls,base64Image,relativeTime,videoIndex);
    Gson gson = new Gson();
    String json = gson.toJson(user);

    String prefixedJson = "P" + json;
    byte[] jsonBytes = prefixedJson.getBytes(StandardCharsets.UTF_8);
    ```

