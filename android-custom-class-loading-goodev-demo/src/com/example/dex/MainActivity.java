/*
 * Copyright 2011 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.dex;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import dalvik.system.DexClassLoader;

/**
 * change from http://code.google.com/p/android-custom-class-loading-sample/
 * move the secondary_dex.jar file to Internet
 *
 */
public class MainActivity extends Activity {
    private static final String SECONDARY_DEX_NAME = "secondary_dex.jar";
    private static final String SECONDARY_DEX_URL = "http://goodev.sinaapp.com/and/secondary_dex.jar";
    
    // Buffer size for file copying.  While 8kb is used in this sample, you
    // may want to tweak it based on actual size of the secondary dex file involved.
    private static final int BUF_SIZE = 8 * 1024;
    
    private Button mToastButton = null;
    private ProgressDialog mProgressDialog = null;
    
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        mToastButton = (Button) findViewById(R.id.toast_button);
        
        // Before the secondary dex file can be processed by the DexClassLoader,
        // it has to be first copied from asset resource to a storage location.
        final File dexInternalStoragePath = new File(getDir("dex", Context.MODE_PRIVATE),SECONDARY_DEX_NAME);
        if (!dexInternalStoragePath.exists()) {
            mProgressDialog = ProgressDialog.show(this,
                    getResources().getString(R.string.diag_title), 
                    getResources().getString(R.string.diag_message), true, false);
            // Perform the file copying in an AsyncTask.
            // 从网络下载需要的dex文件
            (new PrepareDexTask()).execute(dexInternalStoragePath);
        } else {
            mToastButton.setEnabled(true);
        }
        
        mToastButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
                // Internal storage where the DexClassLoader writes the optimized dex file to.
                final File optimizedDexOutputPath = getDir("outdex", Context.MODE_PRIVATE);
                
                // Initialize the class loader with the secondary dex file.
                DexClassLoader cl = new DexClassLoader(dexInternalStoragePath.getAbsolutePath(),
                        optimizedDexOutputPath.getAbsolutePath(),
                        null,
                        getClassLoader());
                Class libProviderClazz = null;
                
                try {
                    // Load the library class from the class loader.
                    // 载入从网络上下载的类
                    libProviderClazz =
                            cl.loadClass("com.example.dex.lib.LibraryProvider");
                    
                    // Cast the return object to the library interface so that the
                    // caller can directly invoke methods in the interface.
                    // Alternatively, the caller can invoke methods through reflection,
                    // which is more verbose and slow.
                    LibraryInterface lib = (LibraryInterface) libProviderClazz.newInstance();
                    
                    // Display the toast!
                    lib.showAwesomeToast(view.getContext(), "hello 世界!");
                } catch (Exception exception) {
                    // Handle exception gracefully here.
                    exception.printStackTrace();
                }
            }
        });
    }
    
    /**
     * 从网络下载自定义类的dex文件
     * @return
     */
    private InputStream downloadFile() {
        try {
            HttpClient client = new DefaultHttpClient();
            HttpHost targetHost = new HttpHost("goodev.sinaapp.com", 80, "http");  
            HttpGet httpGet = new HttpGet(SECONDARY_DEX_URL);  
            HttpResponse response = client.execute(targetHost, httpGet);  
            int statusCode = response.getStatusLine().getStatusCode();  
            if(statusCode == HttpStatus.SC_OK) {
                InputStream is = response.getEntity().getContent();
                return is;
            }
        } catch (ClientProtocolException e) {
            e.printStackTrace();
        } catch (IllegalStateException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }
    // File I/O code to copy the secondary dex file from asset resource to internal storage.
    private boolean prepareDex(File dexInternalStoragePath) {
        InputStream bis = null;
        OutputStream dexWriter = null;

        try {
            bis = downloadFile();
            if(bis == null) {
                return false;
            }
//            bis = new BufferedInputStream(getAssets().open(SECONDARY_DEX_NAME));
            dexWriter = new BufferedOutputStream(new FileOutputStream(dexInternalStoragePath));
            byte[] buf = new byte[BUF_SIZE];
            int len;
            while((len = bis.read(buf, 0, BUF_SIZE)) > 0) {
                dexWriter.write(buf, 0, len);
            }
            dexWriter.close();
            bis.close();
            return true;
        } catch (IOException e) {
            if (dexWriter != null) {
                try {
                    dexWriter.close();
                } catch (IOException ioe) {
                    ioe.printStackTrace();
                }
            }
            if (bis != null) {
                try {
                    bis.close();
                } catch (IOException ioe) {
                    ioe.printStackTrace();
                }
            }
            return false;
        }
    }
    
    private class PrepareDexTask extends AsyncTask<File, Void, Boolean> {

        @Override
        protected void onCancelled() {
            super.onCancelled();
            if (mProgressDialog != null) mProgressDialog.cancel();
        }

        @Override
        protected void onPostExecute(Boolean result) {
            super.onPostExecute(result);
            if (mProgressDialog != null) mProgressDialog.cancel();
        }

        @Override
        protected Boolean doInBackground(File... dexInternalStoragePaths) {
            prepareDex(dexInternalStoragePaths[0]);
            return null;
        }
    }
}
