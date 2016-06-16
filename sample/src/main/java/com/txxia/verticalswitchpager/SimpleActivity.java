package com.txxia.verticalswitchpager;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.webkit.WebView;

public class SimpleActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_simple);

        WebView webView = (WebView) findViewById(R.id.webView);
        webView.loadUrl("http://m.360.com/");
    }
}
