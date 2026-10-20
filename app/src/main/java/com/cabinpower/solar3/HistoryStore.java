package com.cabinpower.solar3;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.content.ContentValues;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.Writer;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Durable, local-only SQLite history. Time axis is phone capture time, not unreliable device time. */
final class HistoryStore extends SQLiteOpenHelper {
    private static final String[] METRICS = {"pv","load","soc","voltage","current","temp","battemp","inputv","legb","legc"};
    HistoryStore(Context c) { super(c, "cabin_history_v3.db", null, 1); }
    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE measurements (t INTEGER PRIMARY KEY, sample TEXT UNIQUE, pv REAL, load REAL, soc REAL, voltage REAL, current REAL, temp REAL, battemp REAL, inputv REAL, legb REAL, legc REAL)");
        db.execSQL("CREATE TABLE alert_events (t INTEGER PRIMARY KEY, kind TEXT NOT NULL, message TEXT NOT NULL)");
    }
    @Override public void onUpgrade(SQLiteDatabase db,int oldVersion,int newVersion) { }

    static Double n(JSONObject o, String k) {
        if (!o.has(k) || o.isNull(k)) return null;
        try { String value=o.get(k).toString(); double x=Double.parseDouble(value); return Double.isFinite(x)?x:null; }
        catch (Exception ignored) { return null; }
    }
    static void value(ContentValues c, String col, Double value) {
        if (value == null) c.putNull(col); else c.put(col,value);
    }
    synchronized boolean add(JSONObject payload) {
        JSONObject v=payload.optJSONObject("values"); if(v==null)return false;
        String sample=payload.optString("sampleTime", "");
        long now=payload.optLong("receivedAtMs",System.currentTimeMillis());
        ContentValues row=new ContentValues();
        row.put("t",now);
        if(!sample.trim().isEmpty())row.put("sample",sample);
        value(row,"pv",n(v,"sPP1"));
        Double b=n(v,"ldPB"),c=n(v,"ldPC"); value(row,"load",(b==null&&c==null)?null:(b==null?0:b)+(c==null?0:c));
        value(row,"soc",n(v,"bSoc"));value(row,"voltage",n(v,"bV"));value(row,"current",n(v,"B_TA"));
        value(row,"temp",n(v,"rTmpIn"));value(row,"battemp",n(v,"rTmpBat"));
        value(row,"inputv",n(v,"InAV"));value(row,"legb",n(v,"loadBV"));value(row,"legc",n(v,"loadCV"));
        SQLiteDatabase db=getWritableDatabase();
        long result=db.insertWithOnConflict("measurements",null,row,SQLiteDatabase.CONFLICT_IGNORE);
        // Retain 120 days. Existing sample time is deliberately NOT duplicated.
        if (result!=-1) db.delete("measurements","t < ?",new String[]{String.valueOf(now-120L*86400000L)});
        return result!=-1;
    }
    synchronized JSONObject history(int days) throws Exception {
        days=(days==1||days==7||days==30||days==90)?days:1;
        long now=System.currentTimeMillis();
        long earliest=now-days*86400000L;
        long bucket = days==1?300000L:days==7?1800000L:days==30?7200000L:21600000L;
        JSONArray arr=new JSONArray();
        String sql="SELECT MIN(t),AVG(pv),AVG(load),AVG(soc),AVG(voltage),AVG(current),AVG(temp),AVG(battemp),AVG(inputv),AVG(legb),AVG(legc),COUNT(*) FROM measurements WHERE t >= ? GROUP BY (t / "+bucket+") ORDER BY 1 ASC";
        long count=0;double sumPV=0, sumLoad=0;
        try(Cursor cur=getReadableDatabase().rawQuery(sql,new String[]{String.valueOf(earliest)})) {
            while(cur.moveToNext()) {
                JSONObject p=new JSONObject();p.put("t",cur.getLong(0));
                for(int i=0;i<METRICS.length;i++) p.put(METRICS[i],cur.isNull(i+1)?JSONObject.NULL:cur.getDouble(i+1));
                arr.put(p);count+=cur.getLong(11);
            }
        }
        JSONObject out=new JSONObject();out.put("period",days);out.put("points",arr);out.put("samples",count);
        out.put("retentionDays",120);out.put("backgroundMinimumMinutes",15);
        return out;
    }
    synchronized JSONObject events() throws Exception {
        JSONArray arr=new JSONArray();
        try(Cursor c=getReadableDatabase().rawQuery("SELECT t,kind,message FROM alert_events ORDER BY t DESC LIMIT 60",null)){
            while(c.moveToNext()){JSONObject o=new JSONObject();o.put("t",c.getLong(0));o.put("kind",c.getString(1));o.put("message",c.getString(2));arr.put(o);}
        }
        JSONObject out=new JSONObject();out.put("events",arr);return out;
    }
    synchronized void addEvent(String kind,String message){
        ContentValues cv=new ContentValues();cv.put("t",System.currentTimeMillis());cv.put("kind",kind);cv.put("message",message);
        getWritableDatabase().insert("alert_events",null,cv);
        getWritableDatabase().execSQL("DELETE FROM alert_events WHERE t NOT IN (SELECT t FROM alert_events ORDER BY t DESC LIMIT 500)");
    }
    synchronized void clearAll(){getWritableDatabase().delete("measurements",null,null);getWritableDatabase().delete("alert_events",null,null);}
    synchronized void exportCsv(Writer out) throws IOException {
        out.write("phone_time_iso,device_sample_time,pv_w,load_w,battery_soc_pct,battery_v,battery_current_a,inverter_temp_c,battery_temp_c,input_v,leg_b_v,leg_c_v\n");
        SimpleDateFormat date=new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ",Locale.US);
        try(Cursor c=getReadableDatabase().rawQuery("SELECT t,sample,pv,load,soc,voltage,current,temp,battemp,inputv,legb,legc FROM measurements ORDER BY t ASC",null)){
            while(c.moveToNext()){
                StringBuilder sb=new StringBuilder();sb.append(date.format(new Date(c.getLong(0))));
                for(int i=1;i<12;i++){
                    sb.append(','); if(!c.isNull(i)){
                        String s=c.getString(i);sb.append('"').append(s.replace("\"","\"\"")).append('"');
                    }
                } out.write(sb.append('\n').toString());
            }
        }
    }
}
