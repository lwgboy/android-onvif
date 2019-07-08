package com.wp.android_onvif.onvif;

import android.content.Context;

import com.wp.android_onvif.onvifBean.Device;
import com.wp.android_onvif.onvifBean.Digest;
import com.wp.android_onvif.onvifBean.MediaProfile;
import com.wp.android_onvif.util.Gsoap;
import com.wp.android_onvif.util.HttpUtil;
import com.wp.android_onvif.util.XmlDecodeUtil;

import java.io.IOException;
import java.io.InputStream;

/**
 * 获取设备信息
 */

public class GetDeviceInfoThread extends Thread {
    private static String tag = "OnvifSdk";
    private Device device;
    private Context context;
    private GetDeviceInfoCallBack callBack;

//    private WriteFileUtil util;

    public GetDeviceInfoThread(Device device, Context context, GetDeviceInfoCallBack callBack) {
        this.device = device;
        this.context = context;
        this.callBack = callBack;
//        util = new WriteFileUtil("onvif.txt");
    }

    @Override
    public void run() {
        super.run();
        try {
            //getCapabilities，不需要鉴权
            String postString = OnvifUtils.getPostString("getCapabilities.xml", context, device, false);
            String caps = HttpUtil.postRequest(device.getServiceUrl(), postString);
            //解析返回的xml数据获取存在的url
            XmlDecodeUtil.getCapabilitiesUrl(caps, device);
            // getDeviceInformation 获取设备信息

            String deviceInformation = OnvifUtils.getPostString("getDeviceInformation.xml", context, device, true);
            String deviceInformationReturn = HttpUtil.postRequest(device.getServiceUrl(), deviceInformation);
            XmlDecodeUtil.getDeviceInformation(deviceInformationReturn, device);


            //getProfiles，需要鉴权
            postString = OnvifUtils.getPostString("getProfiles.xml", context, device, true);
            String profilesString = HttpUtil.postRequest(device.getMediaUrl(), postString);
            //解析获取MediaProfile 集合
            device.addProfiles(XmlDecodeUtil.getMediaProfiles(profilesString));
            //通过token获取RTSP url
            for (MediaProfile profile : device.getProfiles()) {
                postString = OnvifUtils.getPostString("getStreamUri.xml", context, device, true, profile.getToken());
                String profileString = HttpUtil.postRequest(device.getMediaUrl(), postString);
                //解析获取mediaUrl
                profile.setRtspUrl(XmlDecodeUtil.getStreamUri(profileString));
            }
            callBack.getDeviceInfoResult(true, device, "NO_ERROR");

//            postString = getPostString("getConfigOptions.xml", true);
//            caps = HttpUtil.postRequest(device.getPtzUrl(), postString);
//            util.writeData(caps.getBytes());

//            util.finishWrite();
        } catch (Exception e) {
            e.printStackTrace();
            callBack.getDeviceInfoResult(false,device, e.toString());
        }
    }

    /**
     * 通过用户名/密码/assets 文件获取对应需要发送的String
     *
     * @param fileName   assets文件名
     * @param needDigest 是否需要鉴权
     * @return 需要发送的 string
     */
    private String getPostString(String fileName, boolean needDigest, String... params) throws IOException {
        //读取文件内容
        String postString = "";
        InputStream is = context.getAssets().open(fileName);
        byte[] postData = new byte[is.available()];
        if (is.read(postData) > 0) {
            postString = new String(postData, "utf-8");
        }
        //获取digest
        Digest digest = Gsoap.getDigest(device.getUserName(), device.getPsw());
        //需要digest
        if (needDigest && digest != null) {
            if (params.length > 0) {
                postString = String.format(postString, digest.getUserName(),
                        digest.getEncodePsw(), digest.getNonce(), digest.getCreatedTime(), params[0]);
            } else {
                postString = String.format(postString, digest.getUserName(),
                        digest.getEncodePsw(), digest.getNonce(), digest.getCreatedTime());
            }

        }
        return postString;
    }

    /**
     * Author ： BlackHao
     * Time : 2018/1/11 14:24
     * Description : 获取 device 信息回调
     */
    public interface GetDeviceInfoCallBack {
        void getDeviceInfoResult(boolean isSuccess, Device device, String errorMsg);
    }
}
