package com.example.syhuang.iothubdemo;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;

import java.io.UnsupportedEncodingException;


public class MainActivity extends AppCompatActivity {

    static private final String deviceId  = "181818_18181818181818181";
    static private final String deviceKey = "qM0mOTloTazmh+l62CFAAcwB5HdR6Otau4G7D3SDaBw=";
    static               String TAG       = MainActivity.class.getSimpleName();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);


    }

    /**
     * 初始化
     * 在 IoT 中心标识注册表中创建名为 javadevice 的设备（如果还没有该设备）。
     * 随即显示稍后需要用到的设备 ID 和密钥：
     *
     * @param view
     */
    public void initIot(View view) {
        IotHubManager.getInstance().start(deviceId, deviceKey);
        IotHubManager.getInstance().setReceiveListener(new IotHubManager.ReceiveListener() {
            @Override
            public void onMethodReceive(String method, String data) {
                Log.i(TAG, "data-->" + method + "," + data);

            }

            @Override
            public void onDataReceive(String data) {

                Log.i(TAG, "data-->" + data);
            }
        });


    }

    /**
     * 发送数据
     *
     * @param view
     */

    public void sendMessage(View view) {
        try {
            String msgStr = "{\"messageId\":" + 2 + ",\"temperature\":" + 1 + ",\"humidity\":" + 66 + "}";
            msgStr = new String(msgStr.getBytes(), "utf-8");
            IotHubManager.getInstance().write(msgStr);
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
    }

    /**
     * 设备孪生
     *
     * @param view
     */

    public void deviceTwin(View view) {
        IotHubManager.getInstance().setProperty("hello", "hello");

    }


}
