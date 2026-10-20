package com.cabinpower.solar3;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;
import android.util.Log;
import org.json.JSONObject;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Android-managed periodic task: minimum 15 minutes, may be delayed by Doze. */
public final class BackgroundMonitorJob extends JobService {
    static final int JOB_ID=31829;
    private ExecutorService work;
    static void configure(Context ctx){
        try {
            JobScheduler jobs=(JobScheduler)ctx.getSystemService(Context.JOB_SCHEDULER_SERVICE);
            if(jobs==null)return;
            CabinSettings s=new CabinSettings(ctx);
            boolean hasLogin=new SecureStore(ctx).load()!=null;
            if(!s.background()||!hasLogin){jobs.cancel(JOB_ID);return;}
            if(jobs.getPendingJob(JOB_ID)!=null)return;
            JobInfo info=new JobInfo.Builder(JOB_ID,new ComponentName(ctx,BackgroundMonitorJob.class))
                  .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                  .setPeriodic(15*60*1000L)
                  .setPersisted(true)
                  .build();
            jobs.schedule(info);
        } catch (RuntimeException e) {
            // Background scheduling must not prevent foreground login/telemetry
            // if a device policy or vendor scheduler rejects the job.
            Log.w("CabinPower", "Background scheduling permission denied", e);
        }
    }
    @Override public boolean onStartJob(JobParameters params){
        work=Executors.newSingleThreadExecutor();
        work.execute(()->{
            boolean retry=false;
            try{
                String[] login=new SecureStore(this).load();
                if(login!=null&&new CabinSettings(this).background()){
                    JSONObject data=new EnerwiseClient().fetch(login[0],login[1]);
                    HistoryStore db=new HistoryStore(this);
                    try{db.add(data);new AlertEngine(this,db).evaluate(data);}finally{db.close();}
                }
            }catch(Exception e){ retry=false; } // next periodic run handles outages; do not loop or spam alerts
            finally {jobFinished(params,retry);}
        });return true;
    }
    @Override public boolean onStopJob(JobParameters params){if(work!=null)work.shutdownNow();return true;}
}
