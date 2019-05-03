package org.eclipse.leshan.client.demo;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
//import java.util.Random;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.eclipse.leshan.client.request.ServerIdentity;
import org.eclipse.leshan.client.resource.BaseInstanceEnabler;
import org.eclipse.leshan.core.model.ObjectModel;
import org.eclipse.leshan.core.response.ExecuteResponse;
import org.eclipse.leshan.core.response.ReadResponse;
import org.eclipse.leshan.util.NamedThreadFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MyLocation extends BaseInstanceEnabler {

  private static final Logger LOG = LoggerFactory.getLogger(MyLocation.class);

  private static final List<Integer> supportedResources = Arrays.asList(0, 1, 5);
//  private static final Random RANDOM = new Random();

  private final int SENSOR_VALUE_LATITUDE = 0;
  private final int SENSOR_VALUE_LONGITUDE = 1;
  private final int TIMESTAMP = 5;
  private float latitude;
  private float longitude;
  private float scaleFactor;
  private Date timestamp;
  private final ScheduledExecutorService scheduler;

  public MyLocation() {
    this.latitude = getLatitude();
    this.longitude = getLongitude();
    this.scaleFactor = 1.0f;
    this.timestamp = new Date();

    this.scheduler = Executors
        .newSingleThreadScheduledExecutor(new NamedThreadFactory("Temperature Sensor"));
    scheduler.scheduleAtFixedRate(new Runnable() {

      @Override
      public void run() {
        adjustLocation();
      }
    }, 2, 2, TimeUnit.SECONDS);
  }

  @Override
  public ReadResponse read(ServerIdentity identity, int resourceid) {
    LOG.info("Read on Location Resource " + resourceid);
    switch (resourceid) {
      case 0:
        return ReadResponse.success(resourceid, getLatitude());
      case 1:
        return ReadResponse.success(resourceid, getLongitude());
      case 5:
        return ReadResponse.success(resourceid, getTimestamp());
      default:
        return super.read(identity, resourceid);
    }
  }

  private synchronized void adjustLocation() {
    setLatitude();
    setLongitude();
  }

//  public void moveLocation(String nextMove) {
//    switch (nextMove.charAt(0)) {
//      case 'w':
//        moveLatitude(1.0f);
//        break;
//      case 'a':
//        moveLongitude(-1.0f);
//        break;
//      case 's':
//        moveLatitude(-1.0f);
//        break;
//      case 'd':
//        moveLongitude(1.0f);
//        break;
//    }
//  }

//  private void moveLatitude(float delta) {
//    latitude = latitude + delta * scaleFactor;
//    timestamp = new Date();
//    fireResourcesChange(0, 5);
//  }

//  private void moveLongitude(float delta) {
//    longitude = longitude + delta * scaleFactor;
//    timestamp = new Date();
//    fireResourcesChange(1, 5);
//  }

  private static float myHttpRequest(String dataType) {
    try {
      URL urlForGetRequest = new URL("http://127.0.0.1:5000/get-location");
      String readLine = null;
      HttpURLConnection conection = (HttpURLConnection) urlForGetRequest.openConnection();
      conection.setRequestMethod("GET");
      int responseCode = conection.getResponseCode();
      if (responseCode == HttpURLConnection.HTTP_OK) {
        BufferedReader in = new BufferedReader(
            new InputStreamReader(conection.getInputStream()));
        StringBuilder response = new StringBuilder();
        while ((readLine = in.readLine()) != null) {
          response.append(readLine);
        }
        in.close();
        JsonElement root = new JsonParser().parse(response.toString());

        return root.getAsJsonObject().get(dataType).getAsFloat();
      } else {
        System.out.println("GET NOT WORKED");
      }
    } catch (Exception e) {
      System.out.println(e.getMessage());
    }
    return 0f;
  }

  public float getLatitude() {
    return latitude;
  }

  public void setLatitude() {
    float tempLatitude = myHttpRequest("latitude");

    if (tempLatitude != this.latitude) {
      this.latitude = tempLatitude;
      this.timestamp = new Date();
      fireResourcesChange(0, 5);
    }
  }

  public float getLongitude() {
    return longitude;
  }

  public void setLongitude() {
    float tempLongitude = myHttpRequest("longitude");

    if (tempLongitude != this.latitude) {
      this.longitude = tempLongitude;
      this.timestamp = new Date();
      fireResourcesChange(1, 5);
    }
  }

  public Date getTimestamp() {
    return this.timestamp;
  }

  @Override
  public List<Integer> getAvailableResourceIds(ObjectModel model) {
    return supportedResources;
  }
}