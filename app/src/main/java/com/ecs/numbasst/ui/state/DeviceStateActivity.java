package com.ecs.numbasst.ui.state;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;

import com.ecs.numbasst.R;
import com.ecs.numbasst.base.BaseActivity;
import com.ecs.numbasst.manager.BleServiceManager;
import com.ecs.numbasst.manager.callback.QueryStateCallback;

public class DeviceStateActivity extends BaseActivity {


    private TextView tvTitle;
    private ImageButton btnBack;
    private Spinner spinnerStateType;
    private Button btnGetState;
    private ProgressBar progressBarStatus;
    private TextView deviceStatus;

    BleServiceManager manager;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    protected int initLayout() {
        return R.layout.activity_device_state;
    }

    @Override
    protected void initView() {
        tvTitle = findViewById(R.id.action_bar_title);
        btnBack= findViewById(R.id.ib_action_back);
        spinnerStateType =findViewById(R.id.spinner_select_state);
        btnGetState =findViewById(R.id.btn_get_device_state);
        progressBarStatus = findViewById(R.id.progress_bar_get_state_status);
        deviceStatus = findViewById(R.id.tv_get_device_state_status);

    }

    @Override
    protected void initData() {
        tvTitle.setText(getTitle());

        manager = BleServiceManager.getInstance();
    }

    private final QueryStateCallback callback = new QueryStateCallback() {

        @Override
        public void onRetryFailed() {
            updateDeviceStatus("多次尝试与主机通讯失败！");
        }

        @Override
        public void onGetState(int type, int params) {
            updateDeviceStatus("状态类型：" +type +  "   状态参数 ："+params);
        }

        @Override
        public void onFailed(String reason) {
            updateDeviceStatus("更新单元请求失败！");
        }
    };


    @Override
    protected void initEvent() {
        btnBack.setOnClickListener(this);
        btnGetState.setOnClickListener(this);
    }

    @Override
    public void onClick(View v) {
        int id = v.getId();
        if(id == R.id.ib_action_back){
            finish();
        }else if(id == R.id.btn_get_device_state){
            prepareGetDeviceState();
        }
    }

    private void prepareGetDeviceState(){

        if (manager.getConnectedDeviceMac()==null){
            showToast("请先连接设备");
            return;
        }
        if (progressBarStatus.getVisibility()==View.VISIBLE ){
            showToast("获取设备状态中请勿重复点击");
            return;
        }

        int stateType = spinnerStateType.getSelectedItemPosition() +1;
        //File file = new File("");
        //long fileSize = file.length();
        progressBarStatus.setVisibility(View.VISIBLE);
        deviceStatus.setText("查询 " + spinnerStateType.getSelectedItem().toString() + "  中..." );
        BleServiceManager.getInstance().getDeviceState(stateType,callback);
    }

    private void updateDeviceStatus(String msg){
        progressBarStatus.setVisibility(View.GONE);
        deviceStatus.setText(msg);
    }

}