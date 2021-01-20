package com.ecs.numbasst.ui.state;

import android.os.Bundle;
import android.view.View;

import androidx.recyclerview.widget.RecyclerView;

import com.ecs.numbasst.R;
import com.ecs.numbasst.base.BaseActivity;
import com.ecs.numbasst.manager.BleServiceManager;
import com.ecs.numbasst.manager.ProtocolHelper;
import com.ecs.numbasst.manager.msg.StateMsg;
import com.ecs.numbasst.ui.state.entity.ErrorInfo;
import com.ecs.numbasst.ui.state.entity.StateInfo;
import com.ecs.numbasst.view.TopActionBar;

import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.ArrayList;
import java.util.List;

public class ErrorDetailsActivity extends BaseActivity {

    BleServiceManager manager;
    TopActionBar actionBar;
    RecyclerView listViewError;
    ErrorListAdapter adapter;
    List<String> errorList;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    protected int initLayout() {
        return R.layout.activity_error_details;
    }

    @Override
    protected void initView() {
        actionBar = findViewById(R.id.action_bar_error_details);
        listViewError = findViewById(R.id.rv_error_detail);
    }



    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onGetState(StateMsg msg) {
        StateInfo info = msg.getStateInfo();
        if (info == null) {
            showToast("获取状态为空!");
        } else {
            if (info instanceof ErrorInfo){
                //显示错误
                ErrorInfo errorInfo = (ErrorInfo)info;
                adapter.addAll(errorInfo.getErrorInfoList());
            }
        }
    }

    @Override
    protected void initData() {
        manager = BleServiceManager.getInstance();
        actionBar.setTitle(getTitle());
        errorList = new ArrayList<>();
        adapter = new ErrorListAdapter(errorList);
    }

    @Override
    protected void initEvent() {
        actionBar.setOnClickBackListener(this);
        actionBar.setOnClickRefreshListener(this);
    }

    @Override
    public void onClick(View v) {
        int id = v.getId();
        if (id ==actionBar.getViewBackID()){
            finish();
        }else if (id == actionBar.getViewRefreshID()){
            refreshAll();
        }
    }

    private void refreshAll() {
        manager.getDeviceState(ProtocolHelper.DEVICE_STATUS_FAULT_DIAGNOSIS);
        showToast("查询故障中..");
    }
}