package com.example.syhuang.iothubdemo;

import android.util.Log;

import com.microsoft.azure.sdk.iot.device.DeviceClient;
import com.microsoft.azure.sdk.iot.device.DeviceTwin.Device;
import com.microsoft.azure.sdk.iot.device.DeviceTwin.DeviceMethodData;
import com.microsoft.azure.sdk.iot.device.DeviceTwin.Property;
import com.microsoft.azure.sdk.iot.device.IotHubClientProtocol;
import com.microsoft.azure.sdk.iot.device.IotHubConnectionStatusChangeCallback;
import com.microsoft.azure.sdk.iot.device.IotHubConnectionStatusChangeReason;
import com.microsoft.azure.sdk.iot.device.IotHubEventCallback;
import com.microsoft.azure.sdk.iot.device.IotHubMessageResult;
import com.microsoft.azure.sdk.iot.device.IotHubStatusCode;
import com.microsoft.azure.sdk.iot.device.Message;
import com.microsoft.azure.sdk.iot.device.transport.IotHubConnectionStatus;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.Charset;

public class IotHubManager {
    private static final String  TAG   = "IotHubManager";
    public static final  Charset UTF_8 = Charset.forName("UTF-8");
    String connString;
    IotHubClientProtocol protocol = IotHubClientProtocol.MQTT;

    private static final int METHOD_SUCCESS     = 200;
    private static final int METHOD_HUNG        = 300;
    private static final int METHOD_NOT_FOUND   = 404;
    private static final int METHOD_NOT_DEFINED = 404;

    private DeviceClient    mClient;
    private ReceiveListener mReceiveListener;

    private static IotHubManager sInstance = null;
    Device device = null;
    //设备孪生

    public interface ReceiveListener {
        /**
         * 接收直接方法
         *
         * @param method
         * @param data
         */
        public void onMethodReceive(String method, String data);

        /**
         * 接收一般数据
         *
         * @param data
         */

        public void onDataReceive(String data);
    }

    private IotHubManager() {
    }

    public synchronized static IotHubManager getInstance() {
        if (sInstance == null) {
            sInstance = new IotHubManager();
        }
        return sInstance;
    }

    public void setReceiveListener(ReceiveListener listener) {
        mReceiveListener = listener;
    }

    public synchronized void start(String deviceId, String deviceKey) {
        if (mClient == null) {
            Log.i(TAG, "start-->deviceId:" + deviceId + " ,deviceKey:" + deviceKey);
            try {
                connString = "HostName=AzureIoTHub4Beta.azure-devices.cn;DeviceId=" + deviceId + ";SharedAccessKey=" + deviceKey;
                IotHubClientProtocol protocol = IotHubClientProtocol.MQTT;
                mClient = new DeviceClient(connString, protocol);
                Log.i(TAG, "create--> created an IoT Hub client.");
                /****************************数据通信**************************************/
                MessageCallback callback = new MessageCallback();
                Log.i(TAG, "create-->iot hub receive message ");

                mClient.setMessageCallback(callback, 1);
                mClient.open();
                Log.i(TAG, "open-->Opened connection to IoT Hub.");
                /*****************************直接方法监听***********************/
                mClient.subscribeToDeviceMethod(new DeviceMethodCallback(), null, new DeviceMethodStatusCallBack(), null);
                mClient.registerConnectionStatusChangeCallback(new MessageConnectionStatusCallback(), null);
            } catch (IOException e1) {
                Log.e(TAG, "IOException while opening IoTHub connection");
                e1.printStackTrace();
                try {
                    mClient.closeNow();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                mClient = null;

            } catch (URISyntaxException e2) {
                Log.e(TAG, "URISyntaxException while opening IoTHub connection");
                e2.printStackTrace();
                mClient = null;
            }
        }

    }


    /**
     * 设置修改属性
     *
     * @param key
     * @param value
     */
    public void setProperty(String key, String value) {
        try {
            // Create a Device object to store the device twin properties
            if (mClient != null) {
                if (device == null) {
                    device = new Device() {
                        @Override
                        public void PropertyCall(String propertyKey, Object propertyValue, Object context) {
                            Log.i(TAG, "result-->" + propertyKey + "," + propertyValue + "," + context);
                        }
                    };
                    mClient.startDeviceTwin(new DeviceTwinStatusCallBack(), 1, device, null);
                }

                device.setReportedProp(new Property(key, value));
                Log.i(TAG, "getReportedProp-->" + device.getReportedProp().toString());
                mClient.sendReportedProperties(device.getReportedProp());
            } else {
                Log.i(TAG, "mClient is null");

            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * device
     */
    class DeviceTwinStatusCallBack implements IotHubEventCallback {

        @Override
        public void execute(IotHubStatusCode responseStatus, Object context) {
            Integer i = (Integer) context;
            Log.i(TAG, "DeviceTwinStatusCallBack---->" + "IoT Hub responded to message " + i.toString()
                    + " with status " + responseStatus.name() + "," + context);
        }
    }

    public synchronized void stop() {
        try {
            if (mClient != null) {
                Log.i(TAG, "Close IoT Hub Client");
                mClient.closeNow();
            }
        } catch (IOException e1) {
            Log.e(TAG, "IOException while closing IoTHub connection: " + e1.toString());
        } finally {
            mClient = null;
        }
    }

    public void write(String message) {
        try {
            if (mClient != null) {
                Log.i(TAG, "write message:" + message);
                Message msg = new Message(message);
                msg.setExpiryTime(5000);
                Object lockobj = new Object();
                WriteEventCallback callback = new WriteEventCallback();
                mClient.sendEventAsync(msg, callback, lockobj);
                synchronized (lockobj) {
                    //lockobj.wait();
                }
                Log.i(TAG, "write message end ， context:" + lockobj);
            } else {
                Log.i(TAG, "write message client is null");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 接收直接方法数据
     *
     */
    private class DeviceMethodCallback implements com.microsoft.azure.sdk.iot.device.DeviceTwin.DeviceMethodCallback {
        @Override
        public DeviceMethodData call(String methodName, Object methodData, Object context) {

            String data = "";
            if (methodData instanceof byte[]) {
                data = new String((byte[]) methodData, UTF_8);
            }
            if (mReceiveListener != null) {
                mReceiveListener.onMethodReceive(methodName, data);
            }
            Log.i(TAG, "receive method-->" + methodName + " ,data:" + data);

            DeviceMethodData deviceMethodData = new DeviceMethodData(METHOD_SUCCESS, "ok");
            return deviceMethodData;

        }
    }

    private class DeviceMethodStatusCallBack implements IotHubEventCallback {
        @Override
        public void execute(IotHubStatusCode status, Object context) {
            Integer i = (Integer) context;
            Log.i(TAG, "receive  message--> " + i.toString() + " with status " + status.name());
        }
    }

    /**
     * 发送数据
     */

    private class WriteEventCallback implements IotHubEventCallback {
        @Override
        public void execute(IotHubStatusCode status, Object context) {
            Log.i(TAG, "write-->receive IoT Hub responded to message with status " + status.name() + " | context:" + context);
            if (context != null) {
                synchronized (context) {
                    //context.notifyAll();
                }
            }
        }

    }

    /***
     *接收消息
     */

    class MessageCallback implements com.microsoft.azure.sdk.iot.device.MessageCallback {
        @Override
        public IotHubMessageResult execute(Message msg, Object context) {

            String data = new String(msg.getBytes(), UTF_8);
            Log.i(TAG, "receive message--> " + data);
            if (mReceiveListener != null) {
                mReceiveListener.onDataReceive(data);
            }
            return IotHubMessageResult.COMPLETE;
        }
    }

    protected static class MessageConnectionStatusCallback implements IotHubConnectionStatusChangeCallback {

        @Override
        public void execute(IotHubConnectionStatus status, IotHubConnectionStatusChangeReason statusChangeReason, Throwable throwable, Object callbackContext) {
            Log.i(TAG, "connect state:--> " + status + " context: " + callbackContext.toString());

        }
    }
}

