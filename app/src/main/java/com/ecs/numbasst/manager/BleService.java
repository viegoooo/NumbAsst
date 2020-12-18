package com.ecs.numbasst.manager;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.ecs.numbasst.base.util.ByteUtils;
import com.ecs.numbasst.manager.callback.Callback;
import com.ecs.numbasst.manager.callback.DownloadCallback;
import com.ecs.numbasst.manager.callback.NumberCallback;
import com.ecs.numbasst.manager.callback.ConnectionCallback;
import com.ecs.numbasst.manager.callback.UpdateCallback;
import com.ecs.numbasst.manager.contants.BleConstants;
import com.ecs.numbasst.manager.contants.BleSppGattAttributes;

import java.util.List;
import java.util.UUID;

public class BleService extends Service implements SppInterface {

    private final static String TAG = "BLEService";

    private static final int STATE_DISCONNECTED = 0x1000;
    private static final int STATE_CONNECTING = 0x1001;
    private static final int STATE_CONNECTED = 0x1002;

    private static final int MSG_CONNECTED = 0x1011;
    private static final int MSG_DISCONNECTED = 0x1012;

    private static final int RETRY_TIMEOUT = 20 *1000;
    private static final int RETRY_TIMES = 3;

    private BluetoothManager mBluetoothManager;
    private BluetoothAdapter mBluetoothAdapter;
    private String mBluetoothDeviceAddress;
    private BluetoothGatt mBluetoothGatt;
    //ble characteristic
    private BluetoothGattCharacteristic mNotifyCharacteristic;
    private BluetoothGattCharacteristic mWriteCharacteristic;

    private int mConnectionState = STATE_DISCONNECTED;

    public String connectedDeviceAddress;

    private String intentAction;

    private final IBinder mBinder = new LocalBinder();

    private Callback currCallback;

    private static ConnectionCallback connectionCallBack;
    private static NumberCallback numberCallback;
    private static UpdateCallback updateCallback;
    private static DownloadCallback downloadCallBack;

    private Handler msgHandler;
    private CountDownTimer retryTimer;


    private ProtocolHelper protocolHelper;

    @Override
    public void onCreate() {
        super.onCreate();
        initialize();
        msgHandler = new MsgHandler();
        protocolHelper = new ProtocolHelper();
    }

    public static class MsgHandler extends Handler {
        @Override
        public void handleMessage(@NonNull Message msg) {
            switch (msg.what) {
                case MSG_CONNECTED:
                    if (connectionCallBack != null) {
                        connectionCallBack.onSucceed((String) msg.obj);
                    }
                    break;
                case MSG_DISCONNECTED:
                    if (connectionCallBack != null) {
                        connectionCallBack.onFailed((String) msg.obj);
                    }
                    break;
                case ProtocolHelper.TYPE_SET_NUMBER:
                    if (numberCallback != null) {
                        if (msg.arg1 == ProtocolHelper.STATE_SUCCEED) {
                            numberCallback.onSetSucceed();
                        } else {
                            numberCallback.onFailed("设置车号失败");
                        }
                    }
                    break;
                case ProtocolHelper.TYPE_GET_NUMBER:
                    if (numberCallback != null) {
                        if (msg.arg1 == ProtocolHelper.STATE_SUCCEED) {
                            numberCallback.onNumberGot((String) msg.obj);
                        } else {
                            numberCallback.onFailed("主机返回数据异常！");
                        }
                    }

                    break;

                case ProtocolHelper.TYPE_UNIT_UPDATE_REQUEST:
                    if (updateCallback != null) {
                        if (msg.arg1 == ProtocolHelper.STATE_SUCCEED) {
                            updateCallback.onRequestSucceed();
                        } else {
                            updateCallback.onFailed("更新单元请求失败！");
                        }
                    }
                    break;
                case ProtocolHelper.TYPE_UNIT_UPDATE_FILE_TRANSFER:
                    //传输文件 主机回复
                    break;
                case ProtocolHelper.TYPE_UNIT_UPDATE_COMPLETED:
                    if (updateCallback != null) {
                        updateCallback.onUpdateCompleted(msg.arg1, msg.arg2);
                    }
                    break;
                case ProtocolHelper.TYPE_DOWNLOAD_HEAD:
                    if (downloadCallBack != null) {
                        long size = (long) msg.obj;
                        downloadCallBack.onConfirmed(size);
                    }
                    break;
                case ProtocolHelper.TYPE_DOWNLOAD_TRANSFER:
                    if (downloadCallBack != null) {
                        byte[] data = (byte[]) msg.obj;
                        downloadCallBack.onTransferred(data);
                    }
                    break;
            }
        }
    }

    public class LocalBinder extends Binder {
        BleService getService() {
            return BleService.this;
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        close();
        msgHandler.removeCallbacksAndMessages(null);
        return super.onUnbind(intent);
    }


    private final BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                connectedDeviceAddress = mBluetoothDeviceAddress;
                mConnectionState = STATE_CONNECTED;
                if (connectionCallBack != null) {
                    //创建所需的消息对象
                    Message msg = Message.obtain();
                    msg.what = MSG_CONNECTED;
                    msg.obj = connectedDeviceAddress;
                    msgHandler.sendMessage(msg);
                }
                //Attempts to discover services after successful connection,start service discovery
                Log.i(TAG, "Connected to GATT server.Attempting to start service discovery:" +
                        mBluetoothGatt.discoverServices());
//                intentAction = BleConstants.ACTION_GATT_CONNECTED;
//                broadcastUpdate(intentAction);
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                mConnectionState = STATE_DISCONNECTED;
                connectedDeviceAddress = null;
                if (connectionCallBack != null) {
                    //创建所需的消息对象
                    Message msg = Message.obtain();
                    msg.what = MSG_DISCONNECTED;
                    msg.obj = "断开连接";
                    msgHandler.sendMessage(msg);
                }
                Log.i(TAG, "Disconnected from GATT server. status=" + status);
//                intentAction = BleConstants.ACTION_GATT_DISCONNECTED;
//                broadcastUpdate(intentAction);
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                // 默认先使用 B-0006/TL8266 服务发现
                BluetoothGattService service = gatt.getService(BleSppGattAttributes.UUID_BLE_SPP_SERVICE);
                if (service != null) {
                    //找到服务，继续查找特征值
                    mNotifyCharacteristic = service.getCharacteristic(BleSppGattAttributes.UUID_BLE_SPP_NOTIFY);
                    mWriteCharacteristic = service.getCharacteristic(BleSppGattAttributes.UUID_BLE_SPP_WRITE);
                }

                if (mNotifyCharacteristic != null) {
                    broadcastUpdate(BleConstants.ACTION_GATT_SERVICES_DISCOVERED);
                    //使能Notify
                    setCharacteristicNotification(mNotifyCharacteristic, true);
                }

                if (mWriteCharacteristic == null) //适配没有FEE2的B-0002/04
                {
                    if (service != null) {
                        mWriteCharacteristic = service.getCharacteristic(UUID.fromString(BleSppGattAttributes.BLE_SPP_Notify_Characteristic));
                    } else {
                        Log.v("log", "service is null");
                        broadcastUpdate(BleConstants.ACTION_GATT_SERVICES_NO_DISCOVERED);
                    }
                }

//                if (service == null) {
//                    Log.v("log", "service is null");
//                    broadcastUpdate(ACTION_GATT_SERVICES_NO_DISCOVERED);
//                    // mBluetoothGatt.discoverServices();
//                }

            } else {
                Log.w(TAG, "onServicesDiscovered received: " + status);
            }
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt,
                                         BluetoothGattCharacteristic characteristic,
                                         int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                broadcastUpdate(BleConstants.ACTION_DATA_AVAILABLE, characteristic);
            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt,
                                            BluetoothGattCharacteristic characteristic) {
            broadcastUpdate(BleConstants.ACTION_DATA_AVAILABLE, characteristic);



            final byte[] data = characteristic.getValue();
            //主机返回消息,取消当前 重试计时器
            if (retryTimer!=null){
                retryTimer.cancel();
            }
            Log.d(TAG, "onCharacteristicChanged = " + ByteUtils.bytesToString(data));
            handleMsgFromBleDevice(data);
        }

        //Will call this when write successful
        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                broadcastUpdate(BleConstants.ACTION_WRITE_SUCCESSFUL);
                Log.v("log", "Write OK");
            }
        }
    };

    private void handleMsgFromBleDevice(byte[] data) {
        byte dataType = protocolHelper.getDataType(data);
        Log.d(TAG, " handleMsgFromBleDevice  type = " + ByteUtils.numToHex8(dataType));
        switch (dataType) {
            default:
            case ProtocolHelper.TYPE_UNKNOWN:
                Log.d(TAG, " handleMsgFromBleDevice  unknownType data = " + ByteUtils.bytesToString(data));
                break;

            //主机回复 设置车号 的返回状态
            case ProtocolHelper.TYPE_SET_NUMBER:
                byte statusSetNum = protocolHelper.formatOrderStatus(data);
                if (numberCallback != null) {
                    Message msg = Message.obtain();
                    msg.what = ProtocolHelper.TYPE_SET_NUMBER;
                    msg.arg1 = statusSetNum;
                    msgHandler.sendMessage(msg);
                }
                break;
            //主机回复 获取车号 的信息
            case ProtocolHelper.TYPE_GET_NUMBER:
                String number = protocolHelper.formatGetCarNumber(data);
                Log.d(TAG, " GET NUMBER = " + number);
                if (numberCallback != null) {
                    Message msg = Message.obtain();
                    msg.what = ProtocolHelper.TYPE_GET_NUMBER;
                    if (number == null) {
                        msg.arg1 = ProtocolHelper.STATE_FAILED;
                    } else {
                        msg.arg1 = ProtocolHelper.STATE_SUCCEED;
                        msg.obj = number;
                    }
                    msgHandler.sendMessage(msg);
                }
                break;
            //主机回复 单元升级请求 的返回状态
            case ProtocolHelper.TYPE_UNIT_UPDATE_REQUEST:
                if (updateCallback != null) {
                    byte statusUpdateReq = protocolHelper.formatOrderStatus(data);
                    Message msg = Message.obtain();
                    msg.what = ProtocolHelper.TYPE_UNIT_UPDATE_REQUEST;
                    msg.arg1 = statusUpdateReq;
                    msgHandler.sendMessage(msg);
                }
                break;

            case ProtocolHelper.TYPE_UNIT_UPDATE_FILE_TRANSFER:
                break;

            case ProtocolHelper.TYPE_UNIT_UPDATE_COMPLETED:
                if (updateCallback != null) {
                    byte[] completeStatus = protocolHelper.formatUpdateCompleteStatus(data);
                    Message msg = Message.obtain();
                    msg.what = ProtocolHelper.TYPE_UNIT_UPDATE_COMPLETED;
                    msg.arg1 = completeStatus[0];
                    msg.arg2 = completeStatus[1];
                    msgHandler.sendMessage(msg);
                }
                break;

            case ProtocolHelper.TYPE_DOWNLOAD_HEAD:
                if (downloadCallBack != null) {
                    long dataSize = protocolHelper.formatDownloadSize(data);
                    Message msg = Message.obtain();
                    msg.what = ProtocolHelper.TYPE_DOWNLOAD_HEAD;
                    msg.obj = dataSize;
                    msgHandler.sendMessage(msg);
                }
                break;
            case ProtocolHelper.TYPE_DOWNLOAD_TRANSFER:
                if (downloadCallBack != null) {
                    byte[] dataDownload = protocolHelper.formatDownloadData(data);
                    Message msg = Message.obtain();
                    msg.what = ProtocolHelper.TYPE_DOWNLOAD_TRANSFER;
                    msg.obj = dataDownload;
                    msgHandler.sendMessage(msg);
                }
                break;
        }
    }


    /**
     * Initializes a reference to the local Bluetooth adapter.
     *
     * @return Return true if the initialization is successful.
     */
    public boolean initialize() {
        // For API level 18 and above, get a reference to BluetoothAdapter through
        // BluetoothManager.
        if (mBluetoothManager == null) {
            mBluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
            if (mBluetoothManager == null) {
                Log.e(TAG, "Unable to initialize BluetoothManager.");
                return false;
            }
        }

        mBluetoothAdapter = mBluetoothManager.getAdapter();
        if (mBluetoothAdapter == null) {
            Log.e(TAG, "Unable to obtain a BluetoothAdapter.");
            return false;
        }
        return true;
    }


    /**
     * Connects to the GATT server hosted on the Bluetooth LE device.
     *
     * @param address  The device address of the destination device.
     * @param callback connection status callback.
     *                 {@code BluetoothGattCallback#onConnectionStateChange(android.bluetooth.BluetoothGatt, int, int)}
     *                 callback.
     */
    @Override
    public void connect(final String address, ConnectionCallback callback) {
        if (mBluetoothAdapter == null || address == null) {
            if (!initialize()) {
                Log.w(TAG, "BluetoothAdapter not initialized or unspecified address.");
                callback.onFailed("BluetoothAdapter not initialized or unspecified address.");
                return;
            }
        }
        connectionCallBack = callback;
        currCallback =connectionCallBack;
        // Previously connected device.  Try to reconnect.
        if (address != null && address.equals(mBluetoothDeviceAddress)
                && mBluetoothGatt != null) {
            Log.d(TAG, "Trying to use an existing mBluetoothGatt for connection.");
            if (mBluetoothGatt.connect()) {
                mConnectionState = STATE_CONNECTING;
            } else {
                callback.onFailed("RemoteException :the connection attempt was initiated failed");
            }
            return;
        }

        final BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
        if (device == null) {
            Log.e(TAG, "Device not found.  Unable to connect.");
            callback.onFailed("没有找到设备,无法连接！");
            return;
        }
        // We want to directly connect to the device, so we are setting the autoConnect
        // parameter to false.
        mBluetoothGatt = device.connectGatt(this, false, mGattCallback);
        Log.d(TAG, "Trying to create a new connection.");
        mBluetoothDeviceAddress = address;
        mConnectionState = STATE_CONNECTING;
    }

    /**
     * Disconnects an existing connection or cancel a pending connection. The disconnection result
     * is reported asynchronously through the
     * {@code BluetoothGattCallback#onConnectionStateChange(android.bluetooth.BluetoothGatt, int, int)}
     * callback.
     */
    public void disconnect() {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }
        mBluetoothGatt.disconnect();
    }

    /**
     * After using a given BLE device, the app must call this method to ensure resources are
     * released properly.
     */
    public void close() {
        if (mBluetoothGatt == null) {
            return;
        }
        mBluetoothGatt.close();
        mBluetoothGatt = null;
    }


    @Override
    public void setCarNumber(String number, NumberCallback callback) {
        byte[] order = protocolHelper.createOrderSetCarNumber(number);
        numberCallback = callback;
        currCallback =callback;
        writeDataWithRetry(order,callback);
    }

    @Override
    public void getCarNumber(NumberCallback callback) {
        byte[] order = protocolHelper.createOrderGetCarNumber();
        numberCallback = callback;
        currCallback =callback;
        writeDataWithRetry(order,callback);
    }

    @Override
    public void updateUnitRequest(int unitType, long fileSize, UpdateCallback callback) {
        byte[] order = protocolHelper.createOrderUpdateUnitRequest(unitType, fileSize);
        updateCallback = callback;
        currCallback =callback;
        writeDataWithRetry(order,callback);
    }

    @Override
    public void updateUnitTransfer(String filePath) {

    }

    @Override
    public void downloadDataRequest(String startTime, String endTime, DownloadCallback callback) {
        byte[] order = protocolHelper.createOrderDownloadRequest(startTime, endTime);
        downloadCallBack = callback;
        currCallback =callback;
        writeDataWithRetry(order,callback);
    }

    @Override
    public void replyDownloadConfirm(boolean download) {
        byte[] order = protocolHelper.createOrderReplyDownloadConfirm(download);
        writeDataWithRetry(order,downloadCallBack);
    }


    public String getConnectedDeviceAddress() {
        Log.d(TAG,"connectedDeviceAddress =" +connectedDeviceAddress);
        return connectedDeviceAddress;
    }


    private void broadcastUpdate(final String action) {
        final Intent intent = new Intent(action);
        sendBroadcast(intent);
    }

    private void broadcastUpdate(final String action,
                                 final BluetoothGattCharacteristic characteristic) {
        final Intent intent = new Intent(action);

        if (BleSppGattAttributes.UUID_BLE_SPP_NOTIFY.equals(characteristic.getUuid())) {
            // For all other profiles, writes the data formatted in HEX.
            final byte[] data = characteristic.getValue();
            if (data != null && data.length > 0) {
                intent.putExtra(BleConstants.EXTRA_DATA, data);
            }
        }

        sendBroadcast(intent);
    }


    /**
     * Request a read on a given {@code BluetoothGattCharacteristic}. The read result is reported
     * asynchronously through the {@code BluetoothGattCallback#onCharacteristicRead(android.bluetooth.BluetoothGatt, android.bluetooth.BluetoothGattCharacteristic, int)}
     * callback.
     *
     * @param characteristic The characteristic to read from.
     */
    public void readCharacteristic(BluetoothGattCharacteristic characteristic) {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }
        mBluetoothGatt.readCharacteristic(characteristic);
    }

    /**
     * Enables or disables notification on a give characteristic.
     *
     * @param characteristic Characteristic to act on.
     * @param enabled        If true, enable notification.  False otherwise.
     */
    public void setCharacteristicNotification(BluetoothGattCharacteristic characteristic,
                                              boolean enabled) {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }
        mBluetoothGatt.setCharacteristicNotification(characteristic, enabled);

        // This is specific to BLE SPP Notify.
        if (BleSppGattAttributes.UUID_BLE_SPP_NOTIFY.equals(characteristic.getUuid())) {
            BluetoothGattDescriptor descriptor = characteristic.getDescriptor(
                    UUID.fromString(BleSppGattAttributes.CLIENT_CHARACTERISTIC_CONFIG));
            descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
            mBluetoothGatt.writeDescriptor(descriptor);
        }
    }


    private void writeDataWithRetry(byte[] data,Callback callback){
        if (retryTimer!=null){
            retryTimer.cancel();
        }
        retryTimer = new CountDownTimer(RETRY_TIMEOUT * RETRY_TIMES,RETRY_TIMEOUT) {
            @Override
            public void onTick(long millisUntilFinished) {
                Log.d(TAG,"重新尝试 通讯");
                writeData(data);
            }
            @Override
            public void onFinish() {
                //3次重试失败
                if (callback!=null){
                    callback.onRetryFailed();
                }
            }
        }.start();
    }


    private void writeData(byte[] data) {
        if (mWriteCharacteristic != null &&
                data != null) {
            mWriteCharacteristic.setValue(data);
            //mBluetoothLeService.writeC
            mBluetoothGatt.writeCharacteristic(mWriteCharacteristic);
        }
    }

    /**
     * Retrieves a list of supported GATT services on the connected device. This should be
     * invoked only after {@code BluetoothGatt#discoverServices()} completes successfully.
     *
     * @return A {@code List} of supported services.
     */
    public List<BluetoothGattService> getSupportedGattServices() {
        if (mBluetoothGatt == null) return null;

        return mBluetoothGatt.getServices();
    }


}