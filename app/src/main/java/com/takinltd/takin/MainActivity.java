package com.takinltd.takin;

import android.content.Context;
import android.content.res.Resources;
import android.content.res.XmlResourceParser;
import android.graphics.Matrix;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Criteria;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.SystemClock;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.Chronometer;
import android.widget.ImageView;

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


public class MainActivity extends AppCompatActivity {
    private static final String TAG = "Main Activity";

    private MapView mMapView = null;
    private BaiduMap mBaiduMap = null;
    UiSettings ui = null;
    String status = "CHOOSING_MAP";
    private LocationManager locationManager;
    private Button button;
    private Chronometer timer;

    // for direction info
    private SensorManager sm=null;
    private Sensor aSensor=null;
    private Sensor mSensor=null;
    float[] accelerometerValues = new float[3];
    float[] magneticFieldValues = new float[3];
    float[] values = new float[3];
    float[] rotationmatrix = new float[9];
    ImageView iv;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        //在使用SDK各组件之前初始化context信息，传入ApplicationContext
        //注意该方法要再setContentView方法之前实现
        SDKInitializer.initialize(getApplicationContext());
        setContentView(R.layout.activity_main);

        iv = (ImageView)findViewById(R.id.compass);
        sm=(SensorManager)getSystemService(Context.SENSOR_SERVICE);
        aSensor=sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        mSensor=sm.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        sm.registerListener(myListener, aSensor, SensorManager.SENSOR_DELAY_GAME);
        sm.registerListener(myListener, mSensor, SensorManager.SENSOR_DELAY_GAME);

        button = (Button) findViewById(R.id.button_start);
        timer = (Chronometer)this.findViewById(R.id.chronometer);

        //获取地图控件引用
        mMapView = (MapView) findViewById(R.id.bmapView);
        mBaiduMap = mMapView.getMap();
        //设置缩放范围
        mBaiduMap.setMaxAndMinZoomLevel(20,16);
        ui = mBaiduMap.getUiSettings();
        mMapView.showScaleControl(false);
        mMapView.showZoomControls(false);
       // ui.setAllGesturesEnabled(false);

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

        SetButton();
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
                        break;
                    case "FINISHED":
                        status = "CHOOSING_MAP";
                        break;
                    default:Log.wtf(TAG, "Status not supported");
                }
                SetButton();
            }
        });
    }
    //change button text
    void SetButton(){
        switch (status){
            case "CHOOSING_MAP":
                button.setText("Start");
                break;
            case "RUNNING":
                button.setText("Quit");
                break;
            case "FINISHED":
                button.setText("Finish");
                break;
            default:Log.wtf(TAG, "Status not supported");
        }
    }


    final SensorEventListener myListener = new SensorEventListener() {
        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {
            // TODO Auto-generated method stub

        }

        @Override
        public void onSensorChanged(SensorEvent event) {
            // TODO Auto-generated method stub
            if(event.sensor.getType()==Sensor.TYPE_ACCELEROMETER){
                //Log.d(TAG, "Ac");
                accelerometerValues=event.values;
            }
            if(event.sensor.getType()==Sensor.TYPE_MAGNETIC_FIELD){
                //Log.d(TAG, "Mg");
                magneticFieldValues=event.values;
            }
            //调用getRotaionMatrix获得变换矩阵R[]
            SensorManager.getRotationMatrix(rotationmatrix, null, accelerometerValues, magneticFieldValues);
            SensorManager.getOrientation(rotationmatrix, values);
            //经过SensorManager.getOrientation(R, values);得到的values值为弧度
            //转换为角度
            values[0]=(float)Math.toDegrees(values[0]);

            // rotate imageview compass
            Matrix matrix = new Matrix();
            iv.setScaleType(ImageView.ScaleType.MATRIX);   //required
            matrix.postRotate(-values[0], iv.getDrawable().getBounds().width()/2, iv.getDrawable().getBounds().height()/2);
            iv.setImageMatrix(matrix);
        }
    };

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
