package com.example.gpsloc;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.TextView;
import android.widget.Toast;

import com.alibaba.fastjson.JSON;
import com.aliyun.alink.dm.api.DeviceInfo;
import com.aliyun.alink.linkkit.api.ILinkKitConnectListener;
import com.aliyun.alink.linkkit.api.IoTMqttClientConfig;
import com.aliyun.alink.linkkit.api.LinkKit;
import com.aliyun.alink.linkkit.api.LinkKitInitParams;
import com.aliyun.alink.linksdk.channel.core.persistent.mqtt.MqttConfigure;
import com.aliyun.alink.linksdk.cmp.core.base.AMessage;
import com.aliyun.alink.linksdk.cmp.core.base.ConnectState;
import com.aliyun.alink.linksdk.cmp.core.listener.IConnectNotifyListener;
import com.aliyun.alink.linksdk.tmp.device.payload.ValueWrapper;
import com.aliyun.alink.linksdk.tmp.listener.IPublishResourceListener;
import com.aliyun.alink.linksdk.tools.AError;
import com.aliyun.alink.linksdk.tools.ALog;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class MainActivity extends Activity {
    private TextView textTitle;
    private ImageView imgTitleLeft;
    private ImageView imgTitleRight;
    private Button btn_device_control;//Отчетность по данным
    private EditText edit_device_lon;//долгота
    private EditText edit_device_lat;//широта
    private String productkey="a1drKTRimGy";//productKey
    private String devicename="ioslinkkit1";//deviceName
    private String devicesecret="fe73e21163e43309a3b65adfb9ac68e0";//deviceSecret
    private long mkeyTime;//Интервал времени между двумя выходами по нажатию кнопки возврата телефона
    private boolean isConnect=false;
    private IConnectNotifyListener notifyListener;
    private LocationManager locationManager;
    private LocationListener listener;
    private ListView merYunDataLv;
    private ArrayList<HashMap<String, Object>> lstDraftItem;
    private SimpleAdapter adapter;
    Handler handler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            switch (msg.what) {
                case 0:
                    imgTitleRight.setImageResource(R.mipmap.ic_offline);
                    isConnect=false;
                    break;
                case 1:
                    imgTitleRight.setImageResource(R.mipmap.ic_online);
                    isConnect=true;
                    break;
                default:
                    break;
            }
        }
    };
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        initView();
        connectDevice();
        if (ActivityCompat.checkSelfPermission(MainActivity.this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.INTERNET}, 10);
            }
            return;
        }
        lstDraftItem = new ArrayList<HashMap<String, Object>>();

        adapter = new SimpleAdapter(MainActivity.this, lstDraftItem, R.layout.history_item,
                new String[]{"cal", "lon","lat"},
                new int[]{R.id.history_cal, R.id.history_data1,R.id.history_data2});
        merYunDataLv.setAdapter(adapter);
        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        listener = new LocationListener() {
            @Override
            public void onLocationChanged(Location location) {
                edit_device_lon.setText(getConvertNumber(""+location.getLongitude()));
                edit_device_lat.setText(getConvertNumber(""+location.getLatitude()));
            }

            @Override
            public void onStatusChanged(String provider, int status, Bundle extras) {
            }

            @Override
            public void onProviderEnabled(String provider) {
            }

            @Override
            public void onProviderDisabled(String provider) {
            }
        };
        locationManager.requestLocationUpdates("gps", 2000, 0, listener);
    }
    private void showToast(String str, Context context){
        Toast.makeText(context, str, Toast.LENGTH_SHORT).show();
    }
    private String getConvertNumber(String currentString){
        DecimalFormat df=new  DecimalFormat("#.000000");
        return df.format(Double.parseDouble(currentString));
    }
    private void initView() {
        textTitle=findViewById(R.id.title_center_text);
        textTitle.setText(R.string.app_name);
        merYunDataLv=this.findViewById(R.id.lv_yundata_list);
        imgTitleLeft=findViewById(R.id.title_left_iv);
        imgTitleLeft.setImageResource(R.mipmap.tranparent);//为使文字居中不偏移
        imgTitleRight=findViewById(R.id.title_right_iv);
        imgTitleRight.setImageResource(R.mipmap.ic_offline);//为使文字居中不偏移
        edit_device_lon=findViewById(R.id.edit_device_lon);
        edit_device_lat=findViewById(R.id.edit_device_lat);
        btn_device_control=findViewById(R.id.btn_device_control);
        btn_device_control.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (checkDataConnectComplete()){
                    Map<String, ValueWrapper> props = new HashMap<String, ValueWrapper>();
                    props.put("Longitude",new ValueWrapper.DoubleValueWrapper(Double.parseDouble(edit_device_lon.getText().toString().trim())));
                    props.put("Latitude",new ValueWrapper.DoubleValueWrapper(Double.parseDouble(edit_device_lat.getText().toString().trim())));
                    props.put("Altitude",new ValueWrapper.DoubleValueWrapper(0.0));
                    props.put("CoordinateSystem",new ValueWrapper.IntValueWrapper(1));
                    reportProperty("GeoLocation",new ValueWrapper.StructValueWrapper(props));
                    HashMap<String, Object> map = new HashMap<String, Object>();
                    map.put("lon",edit_device_lon.getText().toString().trim());
                    map.put("lat",edit_device_lat.getText().toString().trim());
                    map.put("cal",getCurrentTime());
                    lstDraftItem.add(map);
                    adapter.notifyDataSetChanged();
                }
            }
        });
    }
    private  String getCurrentTime(){
        SimpleDateFormat sdf= new SimpleDateFormat("yyyy-MM-dd HH:mm");
        return sdf.format(new Date());
    }
    private boolean checkDataConnectComplete() {
        if (TextUtils.isEmpty(edit_device_lon.getText().toString().trim())){
            showToast("Долгота еще не заполнена!",MainActivity.this);
            edit_device_lon.setFocusable(true);
            edit_device_lon.requestFocus();
            return false;
        }else if (TextUtils.isEmpty(edit_device_lat.getText().toString().trim())){
            showToast("Широта еще не указана!",MainActivity.this);
            edit_device_lat.setFocusable(true);
            edit_device_lat.requestFocus();
            return false;
        }
        return true;
    }
    private void reportProperty(String identifier, ValueWrapper value){
        try {
            if (TextUtils.isEmpty(identifier) || value == null) {
                return;
            }
            Map<String, ValueWrapper> reportData = new HashMap<>();
            reportData.put(identifier, value);
            LinkKit.getInstance().getDeviceThing().thingPropertyPost(reportData, new IPublishResourceListener() {

                public void onSuccess(String s, Object o) {
                    showToast("Сообщение успешно отправлено!",MainActivity.this);
                    ALog.d("MainActivity", "успешно отправлено onSuccess() called with: s = [" + s + "], o = [" + o + "]");
                }

                public void onError(String s, AError aError) {
                    if (!isConnect){
                        showToast("Пожалуйста, подключите устройство!",MainActivity.this);
                    }
                    ALog.d("MainActivity", "Не удалось сообщитьonError() called with: s = [" + s + "], aError = [" + JSON.toJSONString(aError) + "]");
                }
            });

        }catch (Exception e){
            e.printStackTrace();
        }
    }
    private void connectDevice() {
        DeviceInfo deviceInfo = new DeviceInfo();
        deviceInfo.productKey = productkey;// Тип продукта
        deviceInfo.deviceName = devicename;// Название оборудования
        deviceInfo.deviceSecret = devicesecret;// Ключ устройства
        Map<String, ValueWrapper> propertyValues = new HashMap<>();
        IoTMqttClientConfig clientConfig = new IoTMqttClientConfig(deviceInfo.productKey,deviceInfo.deviceName,deviceInfo.deviceSecret );
        LinkKitInitParams params = new LinkKitInitParams();
        params.deviceInfo = deviceInfo;
        params.propertyValues = null;
        params.mqttClientConfig = clientConfig;
        LinkKit.getInstance().init(this, params, new ILinkKitConnectListener() {
            @Override
            public void onError(AError error) {
                handler.sendEmptyMessage(0);

            }
            @Override
            public void onInitDone(Object data) {
                handler.sendEmptyMessage(1);
            }
        });

        MqttConfigure.setKeepAliveInterval(3600);
        ALog.setLevel(ALog.LEVEL_DEBUG);
    }
    //点击两次退出软件程序
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if(keyCode == KeyEvent.KEYCODE_BACK){
            if((System.currentTimeMillis() - mkeyTime) > 2000){
                mkeyTime = System.currentTimeMillis();
                Toast.makeText(this, "再按一次退出程序", Toast.LENGTH_LONG).show();
            }else{
                finish();
            }
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }
}