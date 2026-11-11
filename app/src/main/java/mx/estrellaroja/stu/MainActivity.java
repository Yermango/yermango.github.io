package mx.estrellaroja.stu;

import android.Manifest;
import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Base64;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;

/**
 * ER Operadores: abre https://yermango.github.io a pantalla completa, sin barra de navegador.
 * La app vive en GitHub; este APK casi nunca hay que volver a instalarlo.
 */
public class MainActivity extends Activity {

    private static final int PEDIR_ARCHIVO = 10;
    private static final int PEDIR_PERMISO = 11;

    private WebView web;
    private String inicio;
    private String host;
    private ValueCallback<Uri[]> alElegir;

    @Override
    protected void onCreate(Bundle guardado) {
        super.onCreate(guardado);
        inicio = getString(R.string.url_app);
        host = Uri.parse(inicio).getHost();
        getWindow().setStatusBarColor(Color.parseColor("#0B5ED7"));

        web = new WebView(this);
        web.setBackgroundColor(Color.WHITE);
        setContentView(web);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);          // la sesión se guarda en el teléfono
        s.setDatabaseEnabled(true);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        s.setTextZoom(100);                    // aunque el teléfono tenga letra grande, no se desacomoda
        s.setSupportZoom(false);
        s.setBuiltInZoomControls(false);
        s.setDisplayZoomControls(false);
        s.setUseWideViewPort(true);
        s.setLoadWithOverviewMode(false);
        s.setAllowFileAccess(false);
        s.setUserAgentString(s.getUserAgentString() + " ERApp/1");
        CookieManager.getInstance().setAcceptCookie(true);

        web.addJavascriptInterface(new Puente(), "Android");
        web.setWebViewClient(new Cliente());
        web.setWebChromeClient(new Ventanas());

        if (guardado != null) web.restoreState(guardado);
        else web.loadUrl(inicio);
    }

    /** Links de la app se quedan adentro; cualquier otro se abre en el navegador. */
    private boolean abrirFuera(String url) {
        Uri u = Uri.parse(url);
        if (url.startsWith("file:") || (u.getHost() != null && u.getHost().equals(host))) return false;
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, u));
        } catch (Exception ignorar) { }
        return true;
    }

    private class Cliente extends WebViewClient {
        @Override
        public boolean shouldOverrideUrlLoading(WebView v, String url) {
            return abrirFuera(url);
        }

        @Override
        public boolean shouldOverrideUrlLoading(WebView v, android.webkit.WebResourceRequest r) {
            return abrirFuera(r.getUrl().toString());
        }

        @Override
        public void onPageStarted(WebView v, String url, Bitmap icono) {
            super.onPageStarted(v, url, icono);
        }

        /** Solo se llama cuando falla la página principal (sin internet y sin copia guardada). */
        @Override
        @SuppressWarnings("deprecation")
        public void onReceivedError(WebView v, int codigo, String descripcion, String url) {
            v.loadUrl("file:///android_asset/sin_internet.html");
        }
    }

    /** Para "Subir archivos" (Excel del horario, DATOS OP, GPS, tags). */
    private class Ventanas extends WebChromeClient {
        @Override
        public boolean onShowFileChooser(WebView v, ValueCallback<Uri[]> cb, FileChooserParams p) {
            if (alElegir != null) alElegir.onReceiveValue(null);
            alElegir = cb;
            Intent i = new Intent(Intent.ACTION_GET_CONTENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);
            i.setType("*/*");
            try {
                startActivityForResult(Intent.createChooser(i, "Elige el archivo"), PEDIR_ARCHIVO);
            } catch (Exception e) {
                alElegir = null;
                return false;
            }
            return true;
        }
    }

    @Override
    protected void onActivityResult(int pedido, int resultado, Intent datos) {
        if (pedido == PEDIR_ARCHIVO && alElegir != null) {
            alElegir.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(resultado, datos));
            alElegir = null;
            return;
        }
        super.onActivityResult(pedido, resultado, datos);
    }

    /** Lo que la página puede pedirle al teléfono. */
    private class Puente {
        /** Guarda un Excel en la carpeta Descargas. Responde "ok", "permiso" o "error: ...". */
        @JavascriptInterface
        public String guardar(String base64, String nombre, String tipo) {
            try {
                byte[] datos = Base64.decode(base64, Base64.DEFAULT);
                if (Build.VERSION.SDK_INT >= 29) {
                    ContentValues cv = new ContentValues();
                    cv.put(MediaStore.MediaColumns.DISPLAY_NAME, nombre);
                    cv.put(MediaStore.MediaColumns.MIME_TYPE, tipo);
                    cv.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
                    final Uri uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv);
                    if (uri == null) return "error: no se pudo crear el archivo";
                    OutputStream o = getContentResolver().openOutputStream(uri);
                    if (o == null) return "error: no se pudo escribir";
                    o.write(datos);
                    o.close();
                    avisarYAbrir(nombre, uri, tipo);
                } else {
                    if (Build.VERSION.SDK_INT >= 23
                            && checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                        runOnUiThread(new Runnable() {
                            public void run() {
                                requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, PEDIR_PERMISO);
                            }
                        });
                        return "permiso";
                    }
                    File carpeta = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                    if (!carpeta.exists()) carpeta.mkdirs();
                    FileOutputStream o = new FileOutputStream(new File(carpeta, nombre));
                    o.write(datos);
                    o.close();
                    avisarYAbrir(nombre, null, tipo);
                }
                return "ok";
            } catch (Exception e) {
                return "error: " + e.getMessage();
            }
        }

        @JavascriptInterface
        public void reintentar() {
            runOnUiThread(new Runnable() {
                public void run() { web.loadUrl(inicio); }
            });
        }
    }

    private void avisarYAbrir(final String nombre, final Uri uri, final String tipo) {
        runOnUiThread(new Runnable() {
            public void run() {
                Toast.makeText(MainActivity.this, "Guardado en Descargas: " + nombre, Toast.LENGTH_LONG).show();
                if (uri == null) return;
                try {
                    Intent ver = new Intent(Intent.ACTION_VIEW);
                    ver.setDataAndType(uri, tipo);
                    ver.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(ver);
                } catch (Exception sinApp) { /* no hay app de Excel: queda en Descargas */ }
            }
        });
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        if (web != null && web.canGoBack()) web.goBack();
        else super.onBackPressed();
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        web.saveState(out);
    }

    @Override
    protected void onPause() {
        super.onPause();
        CookieManager.getInstance().flush();
        web.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        web.onResume();
    }
}
