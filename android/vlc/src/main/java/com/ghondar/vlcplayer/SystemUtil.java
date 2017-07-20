package com.ghondar.vlcplayer;

/**
 * Created by chenwenyu on 17-7-19.
 */

public class SystemUtil {

    public static String getMediaTime(int ms) {
        int hour, mintue, second;
        hour = ms / 3600000;
        mintue = (ms - hour * 3600000) / 60000;
        second = (ms - hour * 3600000 - mintue * 60000) / 1000;
        String sHour, sMintue, sSecond;
        if (hour < 10) {
            sHour = "0" + String.valueOf(hour);
        } else {
            sHour = String.valueOf(hour);
        }
        if (mintue < 10) {
            sMintue = "0" + String.valueOf(mintue);
        } else {
            sMintue = String.valueOf(mintue);
        }
        if (second < 10) {
            sSecond = "0" + String.valueOf(second);
        } else {
            sSecond = String.valueOf(second);
        }
        if (sHour == "00")
            return sMintue + ":" + sSecond;
        else
            return sMintue + ":" + sSecond;
    }
}
