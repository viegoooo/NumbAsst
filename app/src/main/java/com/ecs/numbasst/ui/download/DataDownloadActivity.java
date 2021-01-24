package com.ecs.numbasst.ui.download;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.ecs.numbasst.R;
import com.ecs.numbasst.base.BaseActionBarActivity;
import com.ecs.numbasst.base.util.Log;
import com.ecs.numbasst.manager.BleServiceManager;
import com.ecs.numbasst.view.DialogDatePicker;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;


public class DataDownloadActivity extends BaseActionBarActivity {

    private final static int START_TIME = 1;
    private final static int END_TIME = 2;

    private TextView tvStartTime;
    private TextView tvEndTime;
    private Button btnDownload;

    private ProgressBar progressBarDownload;
    private TextView tvProgressPercent;
    private TextView tvStatus;

    DialogDatePicker datePickerSelect;

    private long dataTotalSize;
    private long currentSize;

    private boolean isDownloading;
    private SimpleDateFormat dateFormat;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    protected int initLayout() {
        return R.layout.activity_data_download;
    }

    @Override
    protected void initView() {
        tvStartTime = findViewById(R.id.btn_download_start_time);
        tvEndTime = findViewById(R.id.btn_download_end_time);
        btnDownload = findViewById(R.id.btn_download_data);
        progressBarDownload = findViewById(R.id.progress_bar_data_download_percent);
        tvProgressPercent = findViewById(R.id.tv_progress_percent);
        tvStatus =  findViewById(R.id.tv_data_download_status);

    }

    @Override
    protected void initData() {

        dateFormat = new SimpleDateFormat("yyy-MM-dd", Locale.getDefault());
        String date =dateFormat.format(new Date());
        tvStartTime.setText(date);
        tvEndTime.setText(date);
       // Log.d("zwcc"," Unix :" + new Date().getTime());

    }



    @Override
    protected void initEvent() {

        tvStartTime.setOnClickListener(this);
        tvEndTime.setOnClickListener(this);
        btnDownload.setOnClickListener(this);
        datePickerSelect = new DialogDatePicker(this, new DialogDatePicker.OnDateSelectCallBack() {
            @Override
            public void onDateSelected(int flag, int year, int month, int day, long time, String dateString) {
                switch (flag) {
                    case START_TIME:
                        tvStartTime.setText(dateString);
                        break;
                    case END_TIME:
                        tvEndTime.setText(dateString);
                        break;
                }
            }

        });
    }

    @Override
    public void onClick(View v) {
        super.onClick(v);
        int id = v.getId();
        if (id ==R.id.btn_download_start_time){
            datePickerSelect.showDatePickView("开始时间", START_TIME);
        }else if (id == R.id.btn_download_end_time){
            datePickerSelect.showDatePickView("结束时间", END_TIME);
        }else if( id == R.id.ib_action_back){
            finish();
        }else if( id == R.id.btn_download_data){
            //prepareDownloadData();

            testUdp();

        }

    }

    @Override
    public void onRefreshAll() {

    }


    private void  testUdp(){
        Date date = new Date();
        manager.downloadDataRequest(date,date);
        manager.downloadOneDayData(0,date);
    }



    private void prepareDownloadData() {

        if (!manager.isConnected()){
            showToast(getString(R.string.check_device_connection));
            return;
        }
        if (inProgressing() ||isDownloading){
            showToast("下载操作中请勿重复点击下载");
            return;
        }

//        String startTime = tvStartTime.getText().toString().replace("-","");
//        String endTime = tvEndTime.getText().toString().replace("-","");

        try {
            Date dateStart = dateFormat.parse(tvStartTime.getText().toString());
            Date dateEnd = dateFormat.parse(tvEndTime.getText().toString());
            dataTotalSize = 0;
            BleServiceManager.getInstance().downloadDataRequest(dateStart,dateEnd);
        } catch (ParseException e) {
            e.printStackTrace();
        }
    }


    private void showDownloadConfirmDialog(String size){
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle("数据下载");
        builder.setMessage("是否要下载数据？数据大小为"+size);
        builder.setPositiveButton("是", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                showProgressBar();
                isDownloading =true;
                BleServiceManager.getInstance().replyDownloadConfirm(true);
            }
        });
        builder.setNegativeButton("否", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                BleServiceManager.getInstance().replyDownloadConfirm(false);
                hideProgressBar();
                isDownloading = false;
                dialog.dismiss();
            }
        });

        builder.setCancelable(false);
        AlertDialog dialog = builder.create();
        dialog.show();
    }

}