package com.example_test.strerch;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.widget.Toast;

public class TestPlayerService extends Service {

    public enum ACTION {
        TEST0 { @Override public String getCmd() { return "link.canter.natsuki.stretch.ACTION_TEST0"; }},
        TEST1 { @Override public String getCmd() { return "link.canter.natsuki.stretch.ACTION_TEST1"; }},;
        public String getCmd() { return name(); }
    }

    private final IBinder mBinder = (IBinder) new TestPlayerServiceLocalBinder();

    public class TestPlayerServiceLocalBinder extends Binder {
        TestPlayerService getService() {
            return TestPlayerService.this;
        }
    }

    @Override public void onCreate() {
        super.onCreate();
    }

    @Override public void onDestroy() {
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        Toast.makeText(this, "TestPlayerService#onBind"+":"+ intent, Toast.LENGTH_SHORT).show();
        return mBinder;
    }

    @Override
    public void onRebind(Intent intent) {
        Toast.makeText(this, "TestPlayerService#onRebind"+":"+ intent, Toast.LENGTH_SHORT).show();
    }

    @Override
    public boolean onUnbind(Intent intent) {
        Toast.makeText(this, "TestPlayerService#onUnbind"+":"+ intent, Toast.LENGTH_SHORT).show();
        return true;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent.getAction();
        if (action.equals(ACTION.TEST0.getCmd())) {
            Toast.makeText(this, "TestPlayerService#TEST0" + ":" + intent.getStringExtra("TEST_MSG"), Toast.LENGTH_SHORT).show();
        } else if (action.equals(ACTION.TEST1.getCmd())) {
            Toast.makeText(this, "TestPlayerService#TEST1" + ":" + Integer.toString(intent.getIntExtra("TEST_ID",0)), Toast.LENGTH_SHORT).show();
        }

        return START_STICKY;
    }
}
