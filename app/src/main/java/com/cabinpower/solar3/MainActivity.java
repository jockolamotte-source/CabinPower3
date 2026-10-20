package com.cabinpower.solar3;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.view.View;
import org.json.JSONObject;
import java.io.ByteArrayInputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public final class MainActivity extends Activity {
    private static final int EXPORT_DOCUMENT=811;
    private final ExecutorService executor=Executors.newSingleThreadExecutor();
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final AtomicBoolean busy=new AtomicBoolean(false);
    private SecureStore credentials;
    private HistoryStore history;
    private EnerwiseClient client=new EnerwiseClient();
    private WebView web;

    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        getWindow().setStatusBarColor(android.graphics.Color.rgb(255,243,215));
        getWindow().setNavigationBarColor(android.graphics.Color.rgb(255,243,215));
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        credentials=new SecureStore(this);
        history=new HistoryStore(this);
        AlertEngine.channel(this);
        BackgroundMonitorJob.configure(this);
        web=new WebView(this);
        web.setBackgroundColor(android.graphics.Color.rgb(255,248,236));
        WebView.setWebContentsDebuggingEnabled(false);
        WebSettings settings=web.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(false);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(false);
        settings.setAllowFileAccessFromFileURLs(false);
        settings.setAllowUniversalAccessFromFileURLs(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        web.setWebChromeClient(new WebChromeClient());
        web.setWebViewClient(new WebViewClient(){
            boolean allowed(String url){return url!=null&&url.startsWith("file:///android_asset/");}
            @Override public boolean shouldOverrideUrlLoading(WebView v,WebResourceRequest r){return !allowed(r.getUrl().toString());}
            @Override public boolean shouldOverrideUrlLoading(WebView v,String url){return !allowed(url);}
            @Override public WebResourceResponse shouldInterceptRequest(WebView v,WebResourceRequest r){
                if(allowed(r.getUrl().toString()))return super.shouldInterceptRequest(v,r);
                return new WebResourceResponse("text/plain","utf-8",new ByteArrayInputStream(new byte[0]));
            }
            @Override public void onPageFinished(WebView v,String url){if(allowed(url))v.evaluateJavascript("window.cabinBoot&&window.cabinBoot()",null);}
        });
        web.addJavascriptInterface(new Bridge(),"CabinBridge");
        setContentView(web);
        web.loadUrl("file:///android_asset/dashboard.html");
    }
    private void emit(String method,JSONObject payload){
        String script="window."+method+"("+JSONObject.quote(payload.toString())+")";
        handler.post(()->{if(web!=null)web.evaluateJavascript(script,null);});
    }
    private JSONObject message(String text){JSONObject v=new JSONObject();try{v.put("message",text);}catch(Exception ignored){}return v;}
    private void status(String s){emit("nativeStatus",message(s));}
    private void error(String s){emit("nativeError",message(s));}
    private void fetch(String user,String pass,boolean newLogin){
        if(!busy.compareAndSet(false,true))return;
        status(newLogin?"Signing in to Enerwise…":"Fetching cabin telemetry…");
        executor.execute(()->{
            try{
                if(newLogin){client=new EnerwiseClient();client.login(user,pass);credentials.save(user,pass);BackgroundMonitorJob.configure(this);}
                JSONObject result=client.fetch(user,pass);
                history.add(result);
                new AlertEngine(this,history).evaluate(result);
                emit("nativeReadings",result);
            }catch(Exception e){error(EnerwiseClient.safeError(e));}
            finally{busy.set(false);}
        });
    }

    final class Bridge {
        @JavascriptInterface public void bootstrap(){
            executor.execute(()->{
                String[] c=credentials.load();
                if(c==null)emit("nativeSetup",new JSONObject());
                else{emit("nativeSavedLogin",new JSONObject());fetch(c[0],c[1],false);}
                sendPreferences();sendEvents();
            });
        }
        @JavascriptInterface public void login(String user,String pass){
            if(user==null||pass==null||user.trim().isEmpty()||pass.isEmpty()){error("Enter an Enerwise username and password.");return;}
            fetch(user.trim(),pass,true);
        }
        @JavascriptInterface public void refresh(){
            executor.execute(()->{String[] c=credentials.load();if(c==null)emit("nativeSetup",new JSONObject());else fetch(c[0],c[1],false);});
        }
        @JavascriptInterface public void history(int days){
            executor.execute(()->{try{emit("nativeHistory",history.history(days));}catch(Exception e){error("Could not read saved graphs.");}});
        }
        @JavascriptInterface public void events(){sendEvents();}
        @JavascriptInterface public void preferences(){sendPreferences();}
        @JavascriptInterface public void savePreferences(String json){
            executor.execute(()->{
                try{new CabinSettings(MainActivity.this).set(new JSONObject(json));BackgroundMonitorJob.configure(MainActivity.this);sendPreferences();status("Monitoring preferences saved.");}
                catch(Exception e){error("Unable to save preferences.");}
            });
        }
        @JavascriptInterface public void requestNotificationPermission(){
            if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED){
                handler.post(()->requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},79));
            }else{status("Notification permission already available.");}
        }
        @JavascriptInterface public void exportHistory(){
            handler.post(()->{
                Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("text/csv");
                i.putExtra(Intent.EXTRA_TITLE,"CabinPower-History-"+new SimpleDateFormat("yyyyMMdd",Locale.US).format(new Date())+".csv");
                startActivityForResult(i,EXPORT_DOCUMENT);
            });
        }
        @JavascriptInterface public void clearHistory(){executor.execute(()->{history.clearAll();sendEvents();try{emit("nativeHistory",history.history(1));}catch(Exception ignored){}status("Saved history cleared.");});}
        @JavascriptInterface public void logout(){executor.execute(()->{credentials.clear();client=new EnerwiseClient();BackgroundMonitorJob.configure(MainActivity.this);emit("nativeSetup",new JSONObject());});}
    }
    private void sendPreferences(){
        executor.execute(()->{try{emit("nativePreferences",new CabinSettings(this).json());}catch(Exception e){error("Could not load settings.");}});
    }
    private void sendEvents(){executor.execute(()->{try{emit("nativeEvents",history.events());}catch(Exception e){error("Could not load alert history.");}});}
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);
        if(request!=EXPORT_DOCUMENT||result!=RESULT_OK||data==null||data.getData()==null)return;
        Uri destination=data.getData();
        executor.execute(()->{
            try(Writer out=new OutputStreamWriter(getContentResolver().openOutputStream(destination),StandardCharsets.UTF_8)){
                history.exportCsv(out);
                status("CSV saved to the location you selected.");
            }catch(Exception e){error("Unable to export CSV: "+e.getClass().getSimpleName());}
        });
    }
    @Override protected void onResume(){super.onResume();if(web!=null){web.onResume();web.evaluateJavascript("window.cabinResume&&window.cabinResume()",null);}}
    @Override protected void onPause(){if(web!=null){web.evaluateJavascript("window.cabinPause&&window.cabinPause()",null);web.onPause();}super.onPause();}
    @Override protected void onDestroy(){if(web!=null){web.removeJavascriptInterface("CabinBridge");web.destroy();web=null;}executor.shutdownNow();history.close();super.onDestroy();}
}
