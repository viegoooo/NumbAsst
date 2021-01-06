package com.ecs.numbasst.ui.state.entity;

import androidx.annotation.NonNull;

/**
 * @author zw
 * @time 2020/12/28
 * @description
 */
public class TCUInfo extends StateInfo {

    private byte communicationStatus;
    private byte tcuWorkStatus_1;
    private byte tcuSignalStrength_1;
    private byte tcuWorkStatus_2;
    private byte tcuSignalStrength_2;

    /**
     * public TCUInfo(byte communicationStatus, byte tcuWorkStatus_1, byte tcuSignalStrength_1, byte tcuWorkStatus_2,byte tcuSignalStrength_2) {
     * this.communicationStatus = communicationStatus;
     * this.tcuWorkStatus_1 = tcuWorkStatus_1;
     * this.tcuSignalStrength_1 = tcuSignalStrength_1;
     * this.tcuWorkStatus_2 = tcuWorkStatus_2;
     * this.tcuSignalStrength_2 =tcuSignalStrength_2;
     * }
     */
    public TCUInfo(byte[] data) {
        super(data);
        if (data != null && data.length == 6)
            this.communicationStatus = data[1];
        this.tcuWorkStatus_1 = data[2];
        this.tcuSignalStrength_1 = data[3];
        this.tcuWorkStatus_2 = data[4];
        this.tcuSignalStrength_2 = data[5];
    }


    /**
     * TCU1---Bit0：未注册；bit1：已注册；bit2：已注销
     * TCU2---Bit4：未注册；bit5：已注册；bit6：已注销
     * @return
     */
    public String getCommunicationStatus() {
        String state;
        switch (communicationStatus){
            case  0:
                state = "TCU1未注册";
                break;
            case  1:
                state = "TCU1已注册";
                break;
            case  2:
                state = "TCU1已注销";
                break;
            case  4:
                state = "TCU2未注册";
                break;
            case  5:
                state = "TCU2已注册";
                break;
            case  6:
                state = "TCU2已注销";
                break;
            default:
                state = "未知";
                break;
        }

        return state;
    }

    public byte getTcuWorkStatus_1() {
        return tcuWorkStatus_1;
    }

    public byte getTcuSignalStrength_1() {
        return tcuSignalStrength_1;
    }

    public byte getTcuWorkStatus_2() {
        return tcuWorkStatus_2;
    }

    public byte getTcuSignalStrength_2() {
        return tcuSignalStrength_2;
    }

    @NonNull
    @Override
    public String toString() {
        return "TCU通讯状态:"+ communicationStatus + " TCU1工作状态："+ tcuWorkStatus_1 + " TCU1信号强度："+ tcuSignalStrength_1
                +" TCU2工作状态："+ tcuWorkStatus_2 + " TCU2信号强度："+ tcuSignalStrength_2;
    }
}
