package com.cabinpower.solar3;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONObject;

final class CabinSettings {
    private final SharedPreferences p;
    CabinSettings(Context c){ p=c.getSharedPreferences("cabin_settings_v3",Context.MODE_PRIVATE); }
    boolean background(){return p.getBoolean("background",true);}
    boolean notifications(){return p.getBoolean("notifications",true);}
    boolean socEnabled(){return p.getBoolean("lowSocEnabled",true);}
    boolean tempEnabled(){return p.getBoolean("highTempEnabled",true);}
    boolean offlineEnabled(){return p.getBoolean("offlineEnabled",true);}
    int socLimit(){return p.getInt("lowSoc",20);}
    int tempLimit(){return p.getInt("highTemp",60);}
    JSONObject json() throws Exception {
        JSONObject j=new JSONObject();j.put("background",background());j.put("notifications",notifications());
        j.put("lowSocEnabled",socEnabled());j.put("highTempEnabled",tempEnabled());j.put("offlineEnabled",offlineEnabled());
        j.put("lowSoc",socLimit());j.put("highTemp",tempLimit());return j;
    }
    void set(JSONObject j){
        p.edit().putBoolean("background",j.optBoolean("background",background()))
          .putBoolean("notifications",j.optBoolean("notifications",notifications()))
          .putBoolean("lowSocEnabled",j.optBoolean("lowSocEnabled",socEnabled()))
          .putBoolean("highTempEnabled",j.optBoolean("highTempEnabled",tempEnabled()))
          .putBoolean("offlineEnabled",j.optBoolean("offlineEnabled",offlineEnabled()))
          .putInt("lowSoc",Math.max(5,Math.min(80,j.optInt("lowSoc",socLimit()))))
          .putInt("highTemp",Math.max(35,Math.min(95,j.optInt("highTemp",tempLimit()))))
          .apply();
    }
    boolean shouldNotify(String kind,boolean isBad){
        String key="active_"+kind;
        boolean wasBad=p.getBoolean(key,false);
        if(!isBad){ if(wasBad)p.edit().putBoolean(key,false).apply(); return false; }
        long now=System.currentTimeMillis();long last=p.getLong("notified_"+kind,0);
        boolean notify=!wasBad || now-last>4*3600000L;
        p.edit().putBoolean(key,true).apply();
        if(notify)p.edit().putLong("notified_"+kind,now).apply();
        return notify;
    }
}
