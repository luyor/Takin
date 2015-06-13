package com.takinltd.takin;

import android.content.res.Resources;
import android.content.res.XmlResourceParser;
import android.location.Criteria;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.SystemClock;
import android.support.v4.view.GestureDetectorCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.GestureDetector;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.Chronometer;

import com.baidu.location.BDLocation;
import com.baidu.location.BDLocationListener;
import com.baidu.location.LocationClient;
import com.baidu.mapapi.SDKInitializer;
import com.baidu.mapapi.map.BaiduMap;
import com.baidu.mapapi.map.BaiduMapOptions;
import com.baidu.mapapi.map.MapStatus;
import com.baidu.mapapi.map.MapStatusUpdate;
import com.baidu.mapapi.map.MapStatusUpdateFactory;
import com.baidu.mapapi.map.MapView;
import com.baidu.mapapi.map.MyLocationData;
import com.baidu.mapapi.map.OverlayOptions;
import com.baidu.mapapi.map.TextOptions;
import com.baidu.mapapi.map.UiSettings;
import com.baidu.mapapi.model.LatLng;
import com.baidu.navisdk.comapi.geolocate.ILocationChangeListener;
import com.baidu.nplatform.comapi.map.MapController;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.IOException;
import java.io.StringReader;
import java.util.EventListener;


public class MainActivity extends AppCompatActivity{
    private static final String TAG = "Main Activity";

    private GestureDetectorCompat mDetector;

    private MapView mMapView = null;
    private BaiduMap mBaiduMap = null;
    UiSettings ui = null;
    String status = "CHOOSING_MAP";
    private LocationManager locationManager;
    private Button button;
    private Chronometer timer;
    private int currentMap = 0;
    private int totMap = 1;

    private View gesture_space;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        //在使用SDK各组件之前初始化context信息，传入ApplicationContext
        //注意该方法要再setContentView方法之前实现
        SDKInitializer.initialize(getApplicationContext());
        setContentView(R.layout.activity_main);

        button = (Button) findViewById(R.id.button_start);
        timer = (Chronometer)this.findViewById(R.id.chronometer);
        //gesture_space = this.findViewById(R.id.gestureOverlayView);
        //gesture_space.setOnTouchListener();

        //获取地图控件引用
        mMapView = (MapView) findViewById(R.id.bmapView);
        mBaiduMap = mMapView.getMap();
        mBaiduMap.setOnMapTouchListener(new BaiduMap.OnMapTouchListener() {
            @Override
            public void onTouch(MotionEvent event) {
                mDetector.onTouchEvent(event);
            }
        });
        //设置缩放范围
        mBaiduMap.setMaxAndMinZoomLevel(20, 16);
        ui = mBaiduMap.getUiSettings();
        mMapView.showScaleControl(false);
        mMapView.showZoomControls(false);
        ui.setAllGesturesEnabled(false);

        // read xml
        Resources r = getResources();
        XmlResourceParser xrp = r.getXml(R.xml.map0);

        try {
            while (xrp.getEventType() != XmlResourceParser.END_DOCUMENT) {
                if (xrp.getEventType() == XmlResourceParser.START_TAG) {
                    String name = xrp.getName();
                    if(name.equals("zoomlevel")){
                        MapStatusUpdate msu = MapStatusUpdateFactory.zoomTo(Float.parseFloat(xrp.getAttributeValue(0)));
                        //Log.d(TAG, ""+xrp.getAttributeValue(0));
                        mBaiduMap.setMapStatus(msu);
                    }
                    else if (name.equals("centrepoint")) {
                        Log.d(TAG, Float.parseFloat(xrp.getAttributeValue(0))+" "+Float.parseFloat(xrp.getAttributeValue(1)));
                        LatLng ll = new LatLng(Float.parseFloat(xrp.getAttributeValue(1)), Float.parseFloat(xrp.getAttributeValue(0)));
                        MapStatusUpdate msu = MapStatusUpdateFactory.newLatLng(ll);
                        mBaiduMap.setMapStatus(msu);
                    }
                    else if (name.equals("controlpoint")){
                        int bgcolor = 0xAAFF0000;
                        if (Integer.parseInt(xrp.getIdAttribute())==0){
                            bgcolor = 0xAA00FF00;
                        }
                        LatLng point = new LatLng(Float.parseFloat(xrp.getAttributeValue(2)), Float.parseFloat(xrp.getAttributeValue(1)));
                        OverlayOptions textOption = new TextOptions()
                                .bgColor(bgcolor)
                                .fontSize(24)
                                .fontColor(0xFF000000)
                                .text(xrp.getIdAttribute())
                                .rotate(0)
                                .position(point);
                        mBaiduMap.addOverlay(textOption);
                    }
                } else if (xrp.getEventType() == XmlPullParser.END_TAG) {
                } else if (xrp.getEventType() == XmlPullParser.TEXT) {
                }
                //下一个标签
                xrp.next();
            }
        } catch (XmlPullParserException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        Criteria criteria = new Criteria();
        String provider = locationManager.getBestProvider(criteria, true);
        Location location = locationManager.getLastKnownLocation(provider);
        //locationManager.requestLocationUpdates(provider, 20000, 0, this);

        SetStatus();
        //change status and set timer when button clicked
        button.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                switch (status){
                    case "CHOOSING_MAP":
                        status = "RUNNING";
                        timer.setBase(SystemClock.elapsedRealtime());
                        timer.start();
                        break;
                    case "RUNNING":
                        status = "CHOOSING_MAP";
                        timer.stop();
                        timer.setBase(SystemClock.elapsedRealtime());
                        mMapView.bringToFront();
                        break;
                    case "FINISHED":
                        status = "CHOOSING_MAP";
                        break;
                    default:Log.wtf(TAG, "Status not supported");
                }
                SetStatus();
            }
        });
        mDetector = new GestureDetectorCompat(this, new MyGestureListener());
    }
    class MyGestureListener extends GestureDetector.SimpleOnGestureListener{
        public static final int MAJOR_MOVE = 0;

        @Override
        public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
            super.onFling(e1, e2, velocityX, velocityY);
            int dx = (int) (e2.getX() - e1.getX());
            if (Math.abs(dx) > MAJOR_MOVE && Math.abs(velocityX) > Math.abs(velocityY)) { //降噪处理，必须有较大的动作才识别
                if (velocityX > 0) {
                    ChangeMap((currentMap+1)%totMap);
                } else {
                    ChangeMap((currentMap-1)%totMap);
                }
            }
            return true;
        }
    }

    //change button/gesture when status changes
    private void SetStatus(){
        switch (status){
            case "CHOOSING_MAP":
                button.setText("Start");
                //gesture_space.bringToFront();
                break;
            case "RUNNING":
                button.setText("Quit");
                //mMapView.bringToFront();
                break;
            case "FINISHED":
                button.setText("Finish");
                break;
            default:Log.wtf(TAG, "Status not supported");
        }
    }

    private void ChangeMap(int index){
        Log.d(TAG, "change map");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        //在activity执行onDestroy时执行mMapView.onDestroy()，实现地图生命周期管理
        mMapView.onDestroy();
    }
    @Override
    protected void onResume() {
        super.onResume();
        //在activity执行onResume时执行mMapView. onResume ()，实现地图生命周期管理
        mMapView.onResume();
    }
    @Override
    protected void onPause() {
        super.onPause();
        //在activity执行onPause时执行mMapView. onPause ()，实现地图生命周期管理
        mMapView.onPause();
    }
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

}
