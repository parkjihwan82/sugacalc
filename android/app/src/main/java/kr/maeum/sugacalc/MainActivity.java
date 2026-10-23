package kr.maeum.sugacalc;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.widget.Toast;
import java.io.OutputStream;
import android.graphics.Color;
import android.os.Bundle;
import android.print.PrintAttributes;
import android.print.PrintManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

public class MainActivity extends Activity {
    private WebView web;
    private WebView printView; // 인쇄 중 참조 유지
    private String pendingText;  // 저장 대기 중인 내용
    private static final int REQ_SAVE = 7001;

    private static final String PRINT_HOOK =
        "(function(){" +
        "function snap(d){" +
        " d.querySelectorAll('input').forEach(function(i){" +
        "  if(i.type==='checkbox'||i.type==='radio'){if(i.checked)i.setAttribute('checked','');else i.removeAttribute('checked');}" +
        "  else i.setAttribute('value',i.value);});" +
        " d.querySelectorAll('select').forEach(function(s){Array.prototype.forEach.call(s.options,function(o,k){" +
        "  if(k===s.selectedIndex)o.setAttribute('selected','');else o.removeAttribute('selected');});});" +
        " return '<!doctype html>'+d.documentElement.outerHTML.replace(/<script[\\s\\S]*?<\\/script>/gi,'');}" +
        "function hook(w,top){try{if(!w||w.__printHooked)return;w.__printHooked=true;" +
        " w.print=function(){if(top)AndroidApp.printMain();else AndroidApp.printHtml(snap(w.document));};}catch(e){}}" +
        "hook(window,true);" +
        "document.querySelectorAll('iframe').forEach(function(f){hook(f.contentWindow,false);" +
        " f.addEventListener('load',function(){hook(f.contentWindow,false);});});" +
        "})();";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.parseColor("#173b5d"));

        web = new WebView(this);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setAllowFileAccess(true);
        s.setUseWideViewPort(true);
        s.setLoadWithOverviewMode(true);
        s.setTextZoom(100);           // 휴대폰 글꼴 크기 설정과 관계없이 일정한 크기
        s.setSupportZoom(true);
        s.setBuiltInZoomControls(true);
        s.setDisplayZoomControls(false);

        web.addJavascriptInterface(new Bridge(), "AndroidApp");
        web.setWebChromeClient(new WebChromeClient()); // 알림창(alert/confirm) 표시
        web.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                view.evaluateJavascript(PRINT_HOOK, null);
            }
        });

        setContentView(web);
        if (savedInstanceState != null) web.restoreState(savedInstanceState);
        else web.loadUrl("file:///android_asset/index.html");
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        web.saveState(outState);
    }

    @Override
    public void onBackPressed() {
        if (web.canGoBack()) web.goBack();
        else super.onBackPressed();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_SAVE) return;
        if (resultCode != RESULT_OK || data == null || data.getData() == null || pendingText == null) { pendingText = null; return; }
        Uri uri = data.getData();
        try {
            OutputStream os = getContentResolver().openOutputStream(uri);
            os.write(pendingText.getBytes("UTF-8"));
            os.close();
            Toast.makeText(this, "저장했습니다", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "저장하지 못했습니다", Toast.LENGTH_LONG).show();
        }
        pendingText = null;
    }

    private void printWebView(WebView v) {
        PrintManager pm = (PrintManager) getSystemService(PRINT_SERVICE);
        String job = getString(R.string.app_name);
        pm.print(job, v.createPrintDocumentAdapter(job), new PrintAttributes.Builder()
                .setMediaSize(PrintAttributes.MediaSize.ISO_A4).build());
    }

    private class Bridge {
        @JavascriptInterface
        public void printMain() {
            runOnUiThread(new Runnable() {
                public void run() { printWebView(web); }
            });
        }

        @JavascriptInterface
        public void saveText(final String fileName, final String text) {
            runOnUiThread(new Runnable() {
                public void run() {
                    pendingText = text;
                    Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                    i.addCategory(Intent.CATEGORY_OPENABLE);
                    i.setType("text/csv");
                    i.putExtra(Intent.EXTRA_TITLE, fileName);
                    startActivityForResult(i, REQ_SAVE);
                }
            });
        }

        @JavascriptInterface
        public void printHtml(final String html) {
            runOnUiThread(new Runnable() {
                public void run() {
                    printView = new WebView(MainActivity.this);
                    printView.setWebViewClient(new WebViewClient() {
                        @Override
                        public void onPageFinished(WebView view, String url) { printWebView(view); }
                    });
                    printView.loadDataWithBaseURL("file:///android_asset/", html, "text/html", "UTF-8", null);
                }
            });
        }
    }
}
