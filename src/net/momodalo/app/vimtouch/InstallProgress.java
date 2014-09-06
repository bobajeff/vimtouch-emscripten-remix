package net.momodalo.app.vimtouch;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.ProgressBar;
import android.net.Uri;
import android.content.SharedPreferences;
import android.app.DownloadManager;
import android.app.DownloadManager.Query;
import android.app.DownloadManager.Request;
import android.content.BroadcastReceiver;
import android.content.IntentFilter;
import android.database.Cursor;
import android.widget.TextView;
import android.os.Handler;
import android.os.Message;

import java.util.ArrayList;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.BufferedOutputStream;
import java.io.InputStream;
import java.io.File;
import java.io.FileWriter;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import java.security.MessageDigest;
import java.security.DigestInputStream;
import java.lang.ref.WeakReference;
import java.math.BigInteger;

import net.momodalo.app.vimtouch.addons.RuntimeFactory;
import net.momodalo.app.vimtouch.addons.RuntimeAddOn;
import net.momodalo.app.vimtouch.addons.PluginFactory;
import net.momodalo.app.vimtouch.addons.PluginAddOn;

import com.spartacusrex.spartacuside.startup.setup.filemanager;

public class InstallProgress extends Activity {
    public static final String LOG_TAG = "VIM Installation";
    private Uri mUri;
    private ProgressBar mProgressBar;
    private TextView mProgressText;

    private void installDefaultRuntime() {
        ArrayList<RuntimeAddOn> runtimes = RuntimeFactory.getAllRuntimes(getApplicationContext());
        try{

        if(runtimes.size() == 0) {
            MessageDigest md = MessageDigest.getInstance("MD5");
            InputStream is = new DigestInputStream(getResources().openRawResource(R.raw.vim),md);
            installZip(is, null, "Default Runtime");


            // write md5 bytes
            File md5 = new File(getMD5Filename(this));
            FileWriter fout = new FileWriter(md5);

            BigInteger bi = new BigInteger(1, md.digest());
            String result = bi.toString(16);
            if (result.length() % 2 != 0) 
                result = "0"+result;
            Log.e(LOG_TAG, "compute md5 "+result);
            fout.write(result);
            fout.close();
        }else{
            Context context = getApplicationContext();
            for (RuntimeAddOn rt: runtimes){
                if(!rt.isInstalled(context)){
                    InputStream input = rt.getPackageContext().getAssets().openFd(rt.getAssetName()).createInputStream();
                    rt.initTypeDir(context);
                    FileWriter fw = new FileWriter(rt.getFileListName(context));
                    installZip(input,fw, rt.getDescription());
                    fw.close();
                    rt.setInstalled(context,true);
                }
            }
        }

        installZip(getResources().openRawResource(R.raw.terminfo),null, "Terminfo");

        installSysVimrc(this);
        installBusybox(this);

        } catch(Exception e) { 
            Log.e(LOG_TAG, "install vim runtime or compute md5 error", e); 
        }
    }

    private static String getVimrc(Activity activity) {
        return activity.getApplicationContext().getFilesDir()+"/vim/vimrc";
    }

    private static String getMD5Filename( Activity activity) {
        return activity.getApplicationContext().getFilesDir()+"/vim.md5";
    }

    private static boolean checkMD5(Activity activity){
        ArrayList<RuntimeAddOn> runtimes = RuntimeFactory.getAllRuntimes(activity.getApplicationContext());
        if(runtimes.size() > 0)  return true;

        File md5 = new File(getMD5Filename(activity));
        InputStream ris = activity.getResources().openRawResource(R.raw.vim);

        if(!md5.exists()) return false;

        // read md5 
        try{
            BufferedReader reader = new BufferedReader(new FileReader(md5));

            String saved = reader.readLine();
            if(saved.equals(activity.getResources().getString(R.string.vim_md5))) return true;
        }catch(Exception e){
        }

        return false;

    }

    public static boolean isInstalled(Activity activity){
        // check runtimes which not installed yet first
        ArrayList<RuntimeAddOn> runtimes = RuntimeFactory.getAllRuntimes(activity.getApplicationContext());
        for (RuntimeAddOn rt: runtimes){
            if(!rt.isInstalled(activity.getApplicationContext())){
                return false;
            }
        }
        

        File vimrc = new File(getVimrc(activity));
        if(vimrc.exists()){
            // Compare size to make sure the sys vimrc doesn't change
            try{
                if(vimrc.getTotalSpace() != activity.getResources().openRawResource(R.raw.vimrc).available()){
                    installSysVimrc(activity);
                }
            }catch(Exception e){
                installSysVimrc(activity);
            }
            return checkMD5(activity);
        }
        
        return false;
    }

    public static void installSysVimrc(Activity activity) {

        File vimrc = new File(activity.getApplicationContext().getFilesDir()+"/vim/vimrc");

        try{
            BufferedInputStream is = new BufferedInputStream(activity.getResources().openRawResource(R.raw.vimrc));
            FileWriter fout = new FileWriter(vimrc);
            while(is.available() > 0){
                fout.write(is.read());
            }
            fout.close();
        } catch(Exception e) { 
            Log.e(LOG_TAG, "install vimrc", e); 
        } 

        File tmp = new File(activity.getApplicationContext().getFilesDir()+"/tmp");
        tmp.mkdir();
    }
    
    public void installBusybox(Activity activity) {

        try {

        	
            //Create a working Directory
            File tmp = new File(activity.getApplicationContext().getFilesDir()+"/tmp");
            if (!tmp.exists()) {
                tmp.mkdirs();
            }
            File busyboxbin = new File(activity.getApplicationContext().getFilesDir()+"/busybox/bin");
            if (!busyboxbin.exists()) {
            	busyboxbin.mkdirs();
            }
            File filesdir = new File(activity.getApplicationContext().getFilesDir()+"/");
            
            //Vim directory
            File vimdir = new File(activity.getApplicationContext().getFilesDir()+"/vim");

            //Working directory
            File worker = new File(tmp,"WORK_"+System.currentTimeMillis());
            if(!worker.exists()){
                worker.mkdirs();
            }

            //Extract the assets..

            File busytar = new File(busyboxbin, "busybox");
            if(busytar.exists()){
               busytar.delete();
            }

            ContextWrapper context;
			//Extract BusyBox, need it just for ln and cp
            filemanager.extractAsset(activity.getApplicationContext(), "busybox.mp3", busytar);

            //Set up a simple environment
            String[] env = new String[2];
            env[0] = "PATH=/sbin:/vendor/bin:/system/sbin:/system/bin:/system/xbin";
            env[1] = "LD_LIBRARY_PATH=/vendor/lib:/system/lib";

            //Set executable - This *needs* chmod on the phone..
//          Process pp = Runtime.getRuntime().exec("chmod 770 "+busytar.getPath());
            Process pp = Runtime.getRuntime().exec("chmod 770 "+busytar.getPath(),env,busyboxbin);
            pp.waitFor();

            //extract neocomplete
        	//give out message
	        mProgressBar.setProgress(0);
	        mProgressBar.setMax(100);
	        mHandler.sendMessage(mHandler.obtainMessage(MSG_SET_TEXT, "Untarring Neocomplete..."));
	           	//get asset
	        File neocomplete = new File(worker, "neocomplete.tar.gz");
	        filemanager.extractAsset(activity.getApplicationContext(), "neocomplete.tar.gz.mp3", neocomplete);
	        	//run untar command
	        pp = Runtime.getRuntime().exec(busytar.getPath()+" tar -C "+vimdir.getPath()+" -xzf "+neocomplete.getPath(),env,vimdir);
	        pp.waitFor();
	        
            //extract syntastic
        	//give out message
	        mProgressBar.setProgress(2);
	        mProgressBar.setMax(100);
	        mHandler.sendMessage(mHandler.obtainMessage(MSG_SET_TEXT, "Untarring Syntastic..."));
	           	//get asset
	        File syntastic = new File(worker, "syntastic.tar.gz");
	        filemanager.extractAsset(activity.getApplicationContext(), "syntastic.tar.gz.mp3", syntastic);
	        	//run untar command
	        pp = Runtime.getRuntime().exec(busytar.getPath()+" tar -C "+vimdir.getPath()+" -xzf "+syntastic.getPath(),env,vimdir);
	        pp.waitFor();
            
            //extract pythondata
            	//give out message
            mProgressBar.setProgress(4);
            mHandler.sendMessage(mHandler.obtainMessage(MSG_SET_TEXT, "Untarring Python..."));
               	//get asset
            File pythondata = new File(worker, "python.data.tar.gz");
            filemanager.extractAsset(activity.getApplicationContext(), "python.data.tar.gz.mp3", pythondata);
            	//run untar command
            pp = Runtime.getRuntime().exec(busytar.getPath()+" tar -C "+filesdir.getPath()+" -xzf "+pythondata.getPath(),env,filesdir);
            pp.waitFor();
            
            //extract pythonsdcard
        	//give out message
	        mProgressBar.setProgress(7);
            File pythonextradir = new File("/sdcard/com.android.python27/extras");
            if (!pythonextradir.exists()) {
    	        mHandler.sendMessage(mHandler.obtainMessage(MSG_SET_TEXT, "Untarring Python Extras..."));
            	pythonextradir.mkdirs();
	           	//get asset
		        File pythonsdcard = new File(worker, "python.sdcard.tar.gz");
		        filemanager.extractAsset(activity.getApplicationContext(), "python.sdcard.tar.gz.mp3", pythonsdcard);
		        	//run untar command
		        pp = Runtime.getRuntime().exec(busytar.getPath()+" tar -C "+pythonextradir+" -xzf "+pythonsdcard.getPath(),env,filesdir);
		        pp.waitFor();
            }
    
            //extract node.js
        	//give out message
	        mProgressBar.setProgress(13);
	        mHandler.sendMessage(mHandler.obtainMessage(MSG_SET_TEXT, "Untarring Node.js..."));
	           	//get asset
	        File nodedata = new File(worker, "node.data.tar.gz");
	        filemanager.extractAsset(activity.getApplicationContext(), "node.data.tar.gz.mp3", nodedata);
	        	//run untar command
	        pp = Runtime.getRuntime().exec(busytar.getPath()+" tar -C "+filesdir.getPath()+" -xzf "+nodedata.getPath(),env,filesdir);
	        pp.waitFor();
	        
            //extract fastcomp
        	//give out message
	        mProgressBar.setProgress(20);
	        mHandler.sendMessage(mHandler.obtainMessage(MSG_SET_TEXT, "Untarring Fastcomp backend..."));
	           	//get asset
	        File fastcompdata = new File(worker, "fastcomp.data.tar.gz");
	        filemanager.extractAsset(activity.getApplicationContext(), "fastcomp.data.tar.gz.mp3", fastcompdata);
	        	//run untar command
	        pp = Runtime.getRuntime().exec(busytar.getPath()+" tar -C "+filesdir.getPath()+" -xzf "+fastcompdata.getPath(),env,filesdir);
	        pp.waitFor();
	        
            //extract emscripten
        	//give out message
	        mProgressBar.setProgress(75);
	        mHandler.sendMessage(mHandler.obtainMessage(MSG_SET_TEXT, "Untarring Emscripten..."));
	           	//get asset
	        File emscriptendata = new File(worker, "emscripten.data.tar.gz");
	        filemanager.extractAsset(activity.getApplicationContext(), "emscripten.data.tar.gz.mp3", emscriptendata);
	        	//run untar command
	        pp = Runtime.getRuntime().exec(busytar.getPath()+" tar -C "+filesdir.getPath()+" -xzf "+emscriptendata.getPath(),env,filesdir);
	        pp.waitFor();
	        
	        mProgressBar.setProgress(100);

            
//            pp = Runtime.getRuntime().exec(busybox.getPath()+" --install -s "+bbindir.getPath());
            pp = Runtime.getRuntime().exec(busytar.getPath()+" --install -s "+busyboxbin.getPath(),env,busyboxbin);
            pp.waitFor();

            //Now delete the SU link.. too much confusion..
            File su = new File(busyboxbin.getPath(),"su");
            su.delete();


            filemanager.deleteFolder(worker);

            
        } catch (Exception iOException) {
            Log.v("SpartacusRex", "INSTALL SYSTEM EXCEPTION : "+iOException);
        }

        //Its done..

        Log.v("SpartacusRex", "Finished Binary Install");
    }

    private void installLocalFile() {
        try {
            File file = new File(mUri.getPath());
            if(file.exists()){
                installZip(new FileInputStream(file), null, mUri.getPath());
            }
        }catch (Exception e){
            Log.e(LOG_TAG, "install " + mUri + " error " + e);
        }
    }

    static final int MSG_SET_TEXT = 1;
    static class ProgressHandler extends Handler {
        private final WeakReference<InstallProgress> mActivity;

        ProgressHandler(InstallProgress activity) {
            mActivity = new WeakReference<InstallProgress>(activity);
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
            case MSG_SET_TEXT:
                String res = (String)msg.obj;
                InstallProgress activity = mActivity.get();

                if (activity != null)
                    activity.mProgressText.setText(res);
                    activity.setTitle(res);

                break;
            }
        }
    }
    private ProgressHandler mHandler = new ProgressHandler(this);

    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        try {
            mUri = getIntent().getData();
        }catch (Exception e){
            mUri = null;
        }

        setContentView(R.layout.installprogress);
        mProgressBar = (ProgressBar) findViewById(R.id.progress_bar);
        mProgressText = (TextView) findViewById(R.id.progress_text);

        // Start lengthy operation in a background thread
        new Thread(new Runnable() {
            public void run() {
            Log.e(LOG_TAG, "install " + mUri );
                Context context = getApplicationContext();
                if(mUri == null){
                    installDefaultRuntime();
                /*
                }else if (mUri.getScheme().equals("http") || 
                          mUri.getScheme().equals("https") ||
                          mUri.getScheme().equals("ftp")) {
                    downloadRuntime(mUri);
                */
                }else if (mUri.getScheme().equals("backup")){
                    File output = new File(mUri.getPath());
                    backupAll(output);
                    showNotification(R.string.backup_finish);
                }else if (mUri.getScheme().equals("file")) {
                    installLocalFile();
                    showNotification(R.string.install_finish);
                }else if (mUri.getScheme().equals("plugin")){
                    PluginAddOn plugin = PluginFactory.getPluginById( mUri.getAuthority(), context);
                    try{
                        InputStream input = plugin.getPackageContext().getAssets().openFd(plugin.getAssetName()).createInputStream();
                        plugin.initTypeDir(context);
                        FileWriter fw = new FileWriter(plugin.getFileListName(context));
                        installZip(input,fw, plugin.getDescription());
                        fw.close();
                        plugin.setInstalled(context,true);
                        showNotification(R.string.install_finish);
                    }catch(Exception e){
                    }
                }else if (mUri.getScheme().equals("runtime")){
                    RuntimeAddOn runtime = RuntimeFactory.getRuntimeById( mUri.getAuthority(), context);
                    try{
                        InputStream input = runtime.getPackageContext().getAssets().openFd(runtime.getAssetName()).createInputStream();
                        runtime.initTypeDir(context);
                        FileWriter fw = new FileWriter(runtime.getFileListName(context));
                        installZip(input,fw, runtime.getDescription());
                        fw.close();
                        runtime.setInstalled(context,true);
                        showNotification(R.string.install_finish);
                    }catch(Exception e){
                    }
                }else if (mUri.getScheme().equals("content")){
                    try{
                        InputStream attachment = getContentResolver().openInputStream(mUri);
                        installZip(attachment, null, " from other application");
                        showNotification(R.string.install_finish);
                    }catch(Exception e){
                    }
                }

                // check plugins which not installed yet first
                ArrayList<PluginAddOn> plugins = PluginFactory.getAllPlugins(getApplicationContext());
                for (PluginAddOn plugin: plugins){
                    if(!plugin.isInstalled(getApplicationContext())){
                        try{
                            InputStream input = plugin.getPackageContext().getAssets().openFd(plugin.getAssetName()).createInputStream();
                            plugin.initTypeDir(context);
                            FileWriter fw = new FileWriter(plugin.getFileListName(context));
                            installZip(input,fw, plugin.getDescription());
                            fw.close();
                            plugin.setInstalled(context,true);
                            showNotification(R.string.install_finish);
                        }catch(Exception e){
                        }
                    }
                }
        
                finish();

            }
        }).start();
    }

    void showNotification(int desc) {
        String svc = NOTIFICATION_SERVICE;
        NotificationManager nm = (NotificationManager)getSystemService(svc);

        CharSequence from = "VimTouch";
        CharSequence message = getString(desc);

        Notification notif = new Notification(R.drawable.notification, message,
                                              System.currentTimeMillis());

        // The PendingIntent to launch our activity if the user selects this
        // notification
        Intent intent = new Intent(this, VimTouch.class);
        PendingIntent contentIntent = PendingIntent.getActivity(this, 0,
                                                                intent, 0);

        notif.setLatestEventInfo(this, from, message, contentIntent);
        notif.defaults = Notification.DEFAULT_SOUND
                         | Notification.DEFAULT_LIGHTS;
        notif.flags |= Notification.FLAG_AUTO_CANCEL;

        nm.notify(0, notif);
    }

    private void installZip(InputStream is, FileWriter fw, String desc) {
        String dirname = getApplicationContext().getFilesDir().getPath();
        int progress = 0;
        mProgressBar.setProgress(0);
        String msgText = getString(R.string.installing);
        mHandler.sendMessage(mHandler.obtainMessage(MSG_SET_TEXT, msgText+" "+desc));
        ZipInputStream zin = new ZipInputStream(new BufferedInputStream(is));
        ZipEntry ze = null;
        int size;
        byte[] buffer = new byte[8192];

        try  {
            int total = is.available();
            mProgressBar.setMax(total);
            while ((ze = zin.getNextEntry()) != null) {
                Log.i(LOG_TAG, "Unzipping " + ze.getName());

                if(ze.isDirectory()) {
                    File file = new File(dirname+"/"+ze.getName());
                    if(!file.isDirectory())
                        file.mkdirs();
                    if(ze.getName().startsWith("bin/")) {
                        file.setExecutable(true, false);
                        file.setReadable(true, false);
                    }
                } else {
                    File file = new File(dirname+"/"+ze.getName());
                    FileOutputStream fout = new FileOutputStream(file);
                    BufferedOutputStream bufferOut = new BufferedOutputStream(fout, buffer.length);
                    while((size = zin.read(buffer, 0, buffer.length)) != -1) {
                        bufferOut.write(buffer, 0, size);
                    }

                    bufferOut.flush();
                    bufferOut.close();
                    if(ze.getName().startsWith("bin/")) {
                        file.setExecutable(true, false);
                        file.setReadable(true, false);
                    }
                    if(fw != null) fw.write(ze.getName()+"\n");
                }
                mProgressBar.setProgress(total-is.available());
            }

            byte[] buf = new byte[2048];
            while(is.available() > 0){
                is.read(buf);
                mProgressBar.setProgress(total-is.available());
            }
            buf = null;

            zin.close();
        } catch(Exception e) {
            Log.e(LOG_TAG, "unzip", e);
        }
    }
    

    private void backupAll(File dest){
        String src = getApplicationContext().getFilesDir().getPath()+"/vim";

        mProgressBar.setProgress(0);
        String msgText = getString(R.string.backup);
        mHandler.sendMessage(mHandler.obtainMessage(MSG_SET_TEXT, msgText));

        try {
            ZipOutputStream zip = null;
            FileOutputStream fileWriter = null;

            fileWriter = new FileOutputStream(dest);
            zip = new ZipOutputStream(fileWriter);
            mProgressBar.setProgress(0);
            addFolderToZip("", src, zip);
            zip.flush();
            zip.close();
        }catch(Exception e){
        }
    }

    private void addFileToZip(String path, String srcFile, ZipOutputStream zip) throws Exception {
        File folder = new File(srcFile);
        if (folder.isDirectory()) {
            addFolderToZip(path, srcFile, zip);
        } else {
            byte[] buf = new byte[1024];
            int len;
            FileInputStream in = new FileInputStream(srcFile);
            zip.putNextEntry(new ZipEntry(path + "/" + folder.getName()));
            while ((len = in.read(buf)) > 0) {
                zip.write(buf, 0, len);
            }
        }
    }

    private void addFolderToZip(String path, String srcFolder, ZipOutputStream zip) throws Exception {
        File folder = new File(srcFolder);
        int total = folder.list().length;
        int done = 0;
        if (path.equals("")) {
            mProgressBar.setMax(total);
        }

        for (String fileName : folder.list()) {
            if (path.equals("")) {
                addFileToZip(folder.getName(), srcFolder + "/" + fileName, zip);
                mProgressBar.setProgress(++done);
            } else {
                addFileToZip(path + "/" + folder.getName(), srcFolder + "/" + fileName, zip);
            }
        }
    }

    private DownloadManager mDM;
    private long mEnqueue = -1;
    private BroadcastReceiver mReceiver = null;

    private void downloadRuntime(Uri uri) {
        if(mReceiver == null) {
            mReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                context.unregisterReceiver(this);
                mReceiver = null;

                String action = intent.getAction();
                if (DownloadManager.ACTION_DOWNLOAD_COMPLETE.equals(action)) {
                    long downloadId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, 0);
                    Query query = new Query();
                    query.setFilterById(mEnqueue);
                    Cursor c = mDM.query(query);
                    if (c.moveToFirst()) {
                        int columnIndex = c.getColumnIndex(DownloadManager.COLUMN_STATUS);
                        if (DownloadManager.STATUS_SUCCESSFUL == c.getInt(columnIndex)) {
                            mUri = Uri.parse(c.getString(c.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI)));
                            new Thread(new Runnable() {
                                public void run() {
                                    try {
                                        InputStream attachment = getContentResolver().openInputStream(mUri);
                                        installZip(attachment, null, "downloads");
                                        showNotification(R.string.install_finish);
                                    }catch(Exception e){}
                                    finish();
                                }
                            }).start();
                        }
                    }
                }
            }
            };
         
            registerReceiver(mReceiver, new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE));
        }
     
        mDM = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
        Request request = new Request(uri);
        mEnqueue = mDM.enqueue(request);
        
        mProgressBar.setProgress(0);
        mHandler.sendMessage(mHandler.obtainMessage(MSG_SET_TEXT, getString(R.string.downloading)));

        while(mReceiver != null){
            Query query = new Query();
            query.setFilterById(mEnqueue);
            Cursor c = mDM.query(query);
            if (c.moveToFirst()) {
                int columnIndex = c.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES);
                int total = c.getInt(columnIndex);
                columnIndex = c.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR);
                int bytes = c.getInt(columnIndex);
                if(total != mProgressBar.getMax()) mProgressBar.setMax(total);
                mProgressBar.setProgress(bytes);
                try{
                    Thread.sleep(500);
                }catch(Exception e){
                }
            }
            c.close();
        }
    }

    public void onDestroy() {
        if(mReceiver != null)
            unregisterReceiver(mReceiver);
        super.onDestroy();
    }
}
