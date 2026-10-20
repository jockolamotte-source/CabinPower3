package com.cabinpower.solar3;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import org.json.JSONObject;

/** Evaluates user-owned read-only telemetry and sends locally generated alerts. */
final class AlertEngine {
    private static final String CHANNEL="cabin_solar3_alerts";
    private final Context app;
    private final CabinSettings settings;
    private final HistoryStore db;
    AlertEngine(Context c,HistoryStore d){app=c.getApplicationContext();settings=new CabinSettings(app);db=d;}
    static void channel(Context c){
        if(Build.VERSION.SDK_INT>=26){
            NotificationManager nm=(NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE);
            NotificationChannel ch=new NotificationChannel(CHANNEL,"Cabin inverter alerts",NotificationManager.IMPORTANCE_HIGH);
            ch.setDescription("Battery, temperature and inverter availability alerts");nm.createNotificationChannel(ch);
        }
    }
    void evaluate(JSONObject data){
        JSONObject v=data.optJSONObject("values");if(v==null)return;
        boolean online="online".equalsIgnoreCase(data.optString("state"));
        Double soc=HistoryStore.n(v,"bSoc"),tmp=HistoryStore.n(v,"rTmpIn");
        trigger("battery",settings.socEnabled()&&soc!=null&&soc<=settings.socLimit(),"Low battery",soc==null?"SOC unavailable":"Battery is at "+soc.intValue()+"% (threshold "+settings.socLimit()+"%).");
        trigger("temperature",settings.tempEnabled()&&tmp!=null&&tmp>=settings.tempLimit(),"Inverter temperature",tmp==null?"Temperature unavailable":"Inverter reports "+tmp+"°C (threshold "+settings.tempLimit()+"°C).");
        trigger("offline",settings.offlineEnabled()&&!online,"Inverter reported offline","Enerwise reports the cabin inverter is not online.");
    }
    void networkFailure(){
        // Connection failure may be the phone's Internet connection. Do not label it an inverter fault.
    }
    private void trigger(String kind,boolean condition,String title,String message){
        if(!settings.notifications()){settings.shouldNotify(kind,false);return;}
        if(!settings.shouldNotify(kind,condition))return;
        db.addEvent(kind,message);
        if(Build.VERSION.SDK_INT>=33&&app.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)return;
        channel(app);
        Intent intent=new Intent(app,MainActivity.class);intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP|Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent tap=PendingIntent.getActivity(app,0,intent,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Notification n=new Notification.Builder(app,CHANNEL)
           .setSmallIcon(android.R.drawable.ic_dialog_alert).setContentTitle(title)
           .setContentText(message).setStyle(new Notification.BigTextStyle().bigText(message))
           .setAutoCancel(true).setContentIntent(tap).build();
        NotificationManager mgr=(NotificationManager)app.getSystemService(Context.NOTIFICATION_SERVICE);
        mgr.notify(kind.hashCode(),n);
    }
}
