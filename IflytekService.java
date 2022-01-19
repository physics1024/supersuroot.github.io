package com.android.server.iflytek;

import android.app.ActivityManager;
import android.app.AlarmManager;
import android.app.Application;
import android.app.PackageInstallObserver;
import android.app.admin.DevicePolicyManager;
import android.app.admin.IDevicePolicyManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.hardware.usb.UsbManager;
import android.iflytek.IIflytekManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.Binder;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.INetworkManagementService;
import android.os.IPowerManager;
import android.os.Message;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemProperties;
import android.os.UserManager;
import android.os.storage.StorageEventListener;
import android.os.storage.StorageManager;
import android.os.storage.VolumeInfo;
import android.provider.Settings;
import android.system.GaiException;
import android.system.StructAddrinfo;
import android.text.TextUtils;
import android.util.Slog;
import com.android.internal.util.Preconditions;
import com.android.internal.widget.LockPatternUtils;
import com.android.server.pm.PackageManagerService;
import com.qti.snapdragon.sdk.display.ColorManager;
import java.io.File;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.StringJoiner;
import libcore.io.Libcore;

public class IflytekService extends IIflytekManager.Stub {
  private static final int AF_INET = 2;
  
  private static final int AI_NUMERICHOST = 4;
  
  private static final long MAX_WAIT_TIME = 120000L;
  
  private static final int MSG_SET_IPV4_UID_RULE = 1;
  
  private static final int MSG_SET_IPV6_UID_RULE = 2;
  
  private static final int NETID_UNSET = 0;
  
  public static final String TAG = "IflytekService";
  
  private static final String VOL_ID = "public:179,65";
  
  private static final long WAIT_TIME_INCR = 20000L;
  
  private SensorEventListener mASensorEventListener = new SensorEventListener() {
      public void onAccuracyChanged(Sensor param1Sensor, int param1Int) {}
      
      public void onSensorChanged(SensorEvent param1SensorEvent) {
        try {
          if (param1SensorEvent.sensor.getType() == 1) {
            boolean bool;
            if (Settings.Global.getInt(IflytekService.-get1(IflytekService.this).getContentResolver(), "eyeguaided_reversal_enabled") == 1) {
              bool = true;
            } else {
              bool = false;
            } 
            if (bool) {
              float f1 = param1SensorEvent.values[0];
              float f2 = param1SensorEvent.values[1];
              float f3 = param1SensorEvent.values[2];
              Slog.d("IflytekService", "reversal x:" + f1 + ",y:" + f2 + ",z:" + f3);
              if (f1 < 8.0F && f3 < 0.0F) {
                if (!IflytekService.-get3(IflytekService.this)) {
                  IflytekService.-set2(IflytekService.this, true);
                  Intent intent = new Intent("com.huawei.eyesprotect.flip.changed");
                  intent.putExtra("fliped", true);
                  IflytekService.-get1(IflytekService.this).sendBroadcast(intent);
                  return;
                } 
              } else if (IflytekService.-get3(IflytekService.this)) {
                IflytekService.-set2(IflytekService.this, false);
                Intent intent = new Intent("com.huawei.eyesprotect.flip.changed");
                intent.putExtra("fliped", false);
                IflytekService.-get1(IflytekService.this).sendBroadcast(intent);
                return;
              } 
            } 
          } 
        } catch (Exception exception) {
          Slog.e("IflytekService", "mASensorEventListener onSensorChanged: " + exception.getMessage());
        } 
      }
    };
  
  private ColorManager mColorManager;
  
  private final Context mContext;
  
  final Handler mHandler;
  
  private final Handler.Callback mHandlerCallback = new Handler.Callback() {
      public boolean handleMessage(Message param1Message) {
        switch (param1Message.what) {
          default:
            return false;
          case 1:
            IflytekService.-wrap2(IflytekService.this, 0, param1Message.arg1, param1Message.arg2);
            return true;
          case 2:
            break;
        } 
        IflytekService.-wrap4(IflytekService.this, 0, param1Message.arg1, param1Message.arg2);
        return true;
      }
    };
  
  private boolean mIsEyesNear = false;
  
  private boolean mIsFliped = false;
  
  private boolean mIsLightInHealthyRange = true;
  
  private SensorEventListener mLSensorEventListener = new SensorEventListener() {
      public void onAccuracyChanged(Sensor param1Sensor, int param1Int) {}
      
      public void onSensorChanged(SensorEvent param1SensorEvent) {
        try {
          if (param1SensorEvent.sensor.getType() == 5) {
            boolean bool;
            if (Settings.Global.getInt(IflytekService.-get1(IflytekService.this).getContentResolver(), "eyeguaided_light_enabled") == 1) {
              bool = true;
            } else {
              bool = false;
            } 
            if (bool) {
              float f = param1SensorEvent.values[0];
              Slog.d("IflytekService", "light sensorEvent.values" + f);
              if ((f > 8000.0F || f < 10.0F) && IflytekService.-get4(IflytekService.this)) {
                IflytekService.-set3(IflytekService.this, false);
                Intent intent = new Intent("com.huawei.eyesprotect.ligth.changed");
                intent.putExtra("inHealthyRange", false);
                IflytekService.-get1(IflytekService.this).sendBroadcast(intent);
                return;
              } 
              if (f <= 8000.0F && f >= 10.0F && (IflytekService.-get4(IflytekService.this) ^ true) != 0) {
                IflytekService.-set3(IflytekService.this, true);
                Intent intent = new Intent("com.huawei.eyesprotect.ligth.changed");
                intent.putExtra("inHealthyRange", true);
                IflytekService.-get1(IflytekService.this).sendBroadcast(intent);
                return;
              } 
            } 
          } 
        } catch (Exception exception) {
          Slog.e("IflytekService", "mLSensorEventListener onSensorChanged: " + exception.getMessage());
        } 
      }
    };
  
  private INetworkManagementService mNetworkService;
  
  private SensorEventListener mPSensorEventListener = new SensorEventListener() {
      public void onAccuracyChanged(Sensor param1Sensor, int param1Int) {}
      
      public void onSensorChanged(SensorEvent param1SensorEvent) {
        try {
          if (param1SensorEvent.sensor.getType() == 8) {
            boolean bool;
            if (Settings.Global.getInt(IflytekService.-get1(IflytekService.this).getContentResolver(), "eyeguaided_proximity_enabled") == 1) {
              bool = true;
            } else {
              bool = false;
            } 
            if (bool) {
              float f = param1SensorEvent.values[0];
              Slog.d("IflytekService", "proximity sensorEvent.values" + f);
              if (!IflytekService.-get2(IflytekService.this) && (int)f == 0) {
                IflytekService.-set1(IflytekService.this, true);
                Intent intent = new Intent("com.huawei.eyesprotect.proximity.changed");
                intent.putExtra("isShorter", true);
                IflytekService.-get1(IflytekService.this).sendBroadcast(intent);
                return;
              } 
              if (IflytekService.-get2(IflytekService.this) && (int)f != 0) {
                IflytekService.-set1(IflytekService.this, false);
                Intent intent = new Intent("com.huawei.eyesprotect.proximity.changed");
                intent.putExtra("isShorter", false);
                IflytekService.-get1(IflytekService.this).sendBroadcast(intent);
                return;
              } 
            } 
          } 
        } catch (Exception exception) {
          Slog.e("IflytekService", "mPSensorEventListener onSensorChanged: " + exception.getMessage());
        } 
      }
    };
  
  private IPowerManager mPowerManager;
  
  private SensorManager mSensorManager;
  
  private WifiManager mWifiManager = null;
  
  private int uidLogservices = -1;
  
  private int uidOta = -1;
  
  public IflytekService(Context paramContext) {
    this.mContext = paramContext;
    this.mNetworkService = INetworkManagementService.Stub.asInterface(ServiceManager.getService("network_management"));
    this.mWifiManager = (WifiManager)paramContext.getSystemService("wifi");
    NetworkConnectChangedReceiver networkConnectChangedReceiver = new NetworkConnectChangedReceiver();
    IntentFilter intentFilter = new IntentFilter();
    intentFilter.addAction("android.net.conn.CONNECTIVITY_CHANGE");
    paramContext.registerReceiver(networkConnectChangedReceiver, intentFilter);
    HandlerThread handlerThread = new HandlerThread("IflytekService");
    handlerThread.start();
    this.mHandler = new Handler(handlerThread.getLooper(), this.mHandlerCallback);
    this.uidOta = getUidFromPackageName("com.iflytek.study.ota");
    this.uidLogservices = getUidFromPackageName("com.iflytek.cbg.aistudy.logservice");
    try {
      File file = new File(Environment.getDataDirectory() + "/system/" + "netwhitelist.txt");
      if (file.exists()) {
        Slog.d("IflytekService", "IflytekService: file exists");
        if (file.isFile() && !file.delete())
          Slog.e("IflytekService", "IflytekService: file delete fail"); 
      } 
    } catch (Exception exception1) {
      exception1.printStackTrace();
    } 
    if (this.mNetworkService != null) {
      try {
        boolean bool = this.mNetworkService.isFirewallAppEnabled();
        Slog.d("IflytekService", "IflytekService: isFirewallEnabled = " + bool);
        if (!bool)
          this.mNetworkService.setFirewallAppEnabled(true); 
        int i = this.mWifiManager.getConnectionInfo().getIpAddress();
        Slog.d("IflytekService", "IflytekService: ipAddress = " + i);
        if (i != 0) {
          String str = intToIp(i);
          Slog.d("IflytekService", "IflytekService: wlan0Ip = " + str);
          this.mNetworkService.enableUrl(str, true);
        } 
        if (this.uidOta >= 0)
          this.mHandler.obtainMessage(1, this.uidOta, 1).sendToTarget(); 
        if (this.uidLogservices >= 0)
          this.mHandler.obtainMessage(1, this.uidLogservices, 1).sendToTarget(); 
        if (this.uidOta >= 0 && this.mNetworkService.isIpv6FirewallAppEnabled())
          this.mHandler.obtainMessage(2, this.uidOta, 1).sendToTarget(); 
        if (this.uidLogservices >= 0 && this.mNetworkService.isIpv6FirewallAppEnabled())
          this.mHandler.obtainMessage(2, this.uidLogservices, 1).sendToTarget(); 
        try {
          if (ColorManager.connect(paramContext, new ColorManager.ColorManagerListener() {
                public void onConnected() {
                  Slog.d("IflytekService", "ColorManager onConnected");
                  if (IflytekService.-get0(IflytekService.this) == null)
                    IflytekService.-set0(IflytekService.this, ColorManager.getInstance((Application)IflytekService.-get1(IflytekService.this).getApplicationContext(), IflytekService.-get1(IflytekService.this), ColorManager.DCM_DISPLAY_TYPE.DISP_PRIMARY)); 
                }
              }) != 0)
            Slog.e("IflytekService", "Connection failed"); 
          this.mSensorManager = (SensorManager)paramContext.getSystemService("sensor");
          this.mPowerManager = IPowerManager.Stub.asInterface(ServiceManager.getService("power"));
          return;
        } catch (Exception null) {
          Slog.e("IflytekService", "setEyeGuaided exception: " + exception.getMessage());
        } 
      } catch (Exception exception1) {
        exception1.printStackTrace();
        try {
          if (ColorManager.connect((Context)exception, new ColorManager.ColorManagerListener() {
                public void onConnected() {
                  Slog.d("IflytekService", "ColorManager onConnected");
                  if (IflytekService.-get0(IflytekService.this) == null)
                    IflytekService.-set0(IflytekService.this, ColorManager.getInstance((Application)IflytekService.-get1(IflytekService.this).getApplicationContext(), IflytekService.-get1(IflytekService.this), ColorManager.DCM_DISPLAY_TYPE.DISP_PRIMARY)); 
                }
              }) != 0)
            Slog.e("IflytekService", "Connection failed"); 
          this.mSensorManager = (SensorManager)exception.getSystemService("sensor");
          this.mPowerManager = IPowerManager.Stub.asInterface(ServiceManager.getService("power"));
          return;
        } catch (Exception exception) {
          Slog.e("IflytekService", "setEyeGuaided exception: " + exception.getMessage());
        } 
      } 
      return;
    } 
    try {
      if (ColorManager.connect((Context)exception, new ColorManager.ColorManagerListener() {
            public void onConnected() {
              Slog.d("IflytekService", "ColorManager onConnected");
              if (IflytekService.-get0(IflytekService.this) == null)
                IflytekService.-set0(IflytekService.this, ColorManager.getInstance((Application)IflytekService.-get1(IflytekService.this).getApplicationContext(), IflytekService.-get1(IflytekService.this), ColorManager.DCM_DISPLAY_TYPE.DISP_PRIMARY)); 
            }
          }) != 0)
        Slog.e("IflytekService", "Connection failed"); 
      this.mSensorManager = (SensorManager)exception.getSystemService("sensor");
      this.mPowerManager = IPowerManager.Stub.asInterface(ServiceManager.getService("power"));
      return;
    } catch (Exception exception1) {
      Slog.e("IflytekService", "setEyeGuaided exception: " + exception1.getMessage());
    } 
  }
  
  private boolean addNetworkAccessAppWhiteListInternal(ComponentName paramComponentName, List<String> paramList, String paramString) {
    // Byte code:
    //   0: aload_1
    //   1: ifnonnull -> 15
    //   4: new java/lang/IllegalArgumentException
    //   7: dup
    //   8: ldc_w 'input component name is null'
    //   11: invokespecial <init> : (Ljava/lang/String;)V
    //   14: athrow
    //   15: aload_2
    //   16: ifnonnull -> 30
    //   19: new java/lang/IllegalArgumentException
    //   22: dup
    //   23: ldc_w 'input network access app white list is null'
    //   26: invokespecial <init> : (Ljava/lang/String;)V
    //   29: athrow
    //   30: aload_2
    //   31: invokeinterface isEmpty : ()Z
    //   36: ifeq -> 50
    //   39: new java/lang/IllegalArgumentException
    //   42: dup
    //   43: ldc_w 'input network access app white list is empty'
    //   46: invokespecial <init> : (Ljava/lang/String;)V
    //   49: athrow
    //   50: aload_0
    //   51: aload_1
    //   52: invokespecial checkActiveAndUserId : (Landroid/content/ComponentName;)V
    //   55: new java/util/StringJoiner
    //   58: dup
    //   59: ldc_w ','
    //   62: invokespecial <init> : (Ljava/lang/CharSequence;)V
    //   65: astore_1
    //   66: aload_0
    //   67: getfield mNetworkService : Landroid/os/INetworkManagementService;
    //   70: ifnull -> 136
    //   73: invokestatic clearCallingIdentity : ()J
    //   76: lstore #5
    //   78: aload_0
    //   79: getfield mNetworkService : Landroid/os/INetworkManagementService;
    //   82: invokeinterface isFirewallAppEnabled : ()Z
    //   87: istore #7
    //   89: ldc 'IflytekService'
    //   91: new java/lang/StringBuilder
    //   94: dup
    //   95: invokespecial <init> : ()V
    //   98: ldc_w 'addNetworkAccessAppWhiteListInternal: isFirewallEnabled = '
    //   101: invokevirtual append : (Ljava/lang/String;)Ljava/lang/StringBuilder;
    //   104: iload #7
    //   106: invokevirtual append : (Z)Ljava/lang/StringBuilder;
    //   109: invokevirtual toString : ()Ljava/lang/String;
    //   112: invokestatic d : (Ljava/lang/String;Ljava/lang/String;)I
    //   115: pop
    //   116: iload #7
    //   118: ifne -> 131
    //   121: aload_0
    //   122: getfield mNetworkService : Landroid/os/INetworkManagementService;
    //   125: iconst_1
    //   126: invokeinterface setFirewallAppEnabled : (Z)V
    //   131: lload #5
    //   133: invokestatic restoreCallingIdentity : (J)V
    //   136: aload_2
    //   137: invokeinterface iterator : ()Ljava/util/Iterator;
    //   142: astore #8
    //   144: aload #8
    //   146: invokeinterface hasNext : ()Z
    //   151: ifeq -> 330
    //   154: aload #8
    //   156: invokeinterface next : ()Ljava/lang/Object;
    //   161: checkcast java/lang/String
    //   164: astore #9
    //   166: ldc 'IflytekService'
    //   168: new java/lang/StringBuilder
    //   171: dup
    //   172: invokespecial <init> : ()V
    //   175: ldc_w 'addNetworkAccessAppWhiteListInternal: networkName = '
    //   178: invokevirtual append : (Ljava/lang/String;)Ljava/lang/StringBuilder;
    //   181: aload #9
    //   183: invokevirtual append : (Ljava/lang/String;)Ljava/lang/StringBuilder;
    //   186: invokevirtual toString : ()Ljava/lang/String;
    //   189: invokestatic d : (Ljava/lang/String;Ljava/lang/String;)I
    //   192: pop
    //   193: ldc_w ''
    //   196: aload #9
    //   198: invokevirtual equals : (Ljava/lang/Object;)Z
    //   201: ifne -> 255
    //   204: aload_0
    //   205: getfield mNetworkService : Landroid/os/INetworkManagementService;
    //   208: ifnull -> 255
    //   211: invokestatic clearCallingIdentity : ()J
    //   214: lstore #5
    //   216: aload_0
    //   217: aload #9
    //   219: invokespecial getUidFromPackageName : (Ljava/lang/String;)I
    //   222: istore #4
    //   224: iload #4
    //   226: iflt -> 250
    //   229: aload_0
    //   230: getfield mHandler : Landroid/os/Handler;
    //   233: iconst_1
    //   234: iload #4
    //   236: iconst_1
    //   237: invokevirtual obtainMessage : (III)Landroid/os/Message;
    //   240: invokevirtual sendToTarget : ()V
    //   243: aload_1
    //   244: aload #9
    //   246: invokevirtual add : (Ljava/lang/CharSequence;)Ljava/util/StringJoiner;
    //   249: pop
    //   250: lload #5
    //   252: invokestatic restoreCallingIdentity : (J)V
    //   255: ldc 'IflytekService'
    //   257: new java/lang/StringBuilder
    //   260: dup
    //   261: invokespecial <init> : ()V
    //   264: ldc_w 'addNetworkAccessAppWhiteListInternal: sj = '
    //   267: invokevirtual append : (Ljava/lang/String;)Ljava/lang/StringBuilder;
    //   270: aload_1
    //   271: invokevirtual append : (Ljava/lang/Object;)Ljava/lang/StringBuilder;
    //   274: invokevirtual toString : ()Ljava/lang/String;
    //   277: invokestatic d : (Ljava/lang/String;Ljava/lang/String;)I
    //   280: pop
    //   281: goto -> 144
    //   284: astore #8
    //   286: aload #8
    //   288: invokevirtual printStackTrace : ()V
    //   291: lload #5
    //   293: invokestatic restoreCallingIdentity : (J)V
    //   296: goto -> 136
    //   299: astore_1
    //   300: lload #5
    //   302: invokestatic restoreCallingIdentity : (J)V
    //   305: aload_1
    //   306: athrow
    //   307: astore #9
    //   309: aload #9
    //   311: invokevirtual printStackTrace : ()V
    //   314: lload #5
    //   316: invokestatic restoreCallingIdentity : (J)V
    //   319: goto -> 255
    //   322: astore_1
    //   323: lload #5
    //   325: invokestatic restoreCallingIdentity : (J)V
    //   328: aload_1
    //   329: athrow
    //   330: aload_1
    //   331: invokevirtual toString : ()Ljava/lang/String;
    //   334: astore_1
    //   335: ldc 'IflytekService'
    //   337: new java/lang/StringBuilder
    //   340: dup
    //   341: invokespecial <init> : ()V
    //   344: ldc_w 'addNetworkAccessAppWhiteListInternal: networkAccessListStr = '
    //   347: invokevirtual append : (Ljava/lang/String;)Ljava/lang/StringBuilder;
    //   350: aload_1
    //   351: invokevirtual append : (Ljava/lang/String;)Ljava/lang/StringBuilder;
    //   354: invokevirtual toString : ()Ljava/lang/String;
    //   357: invokestatic d : (Ljava/lang/String;Ljava/lang/String;)I
    //   360: pop
    //   361: invokestatic clearCallingIdentity : ()J
    //   364: lstore #5
    //   366: ldc 'IflytekService'
    //   368: ldc_w 'addNetworkAccessAppWhiteListInternal: begin write'
    //   371: invokestatic d : (Ljava/lang/String;Ljava/lang/String;)I
    //   374: pop
    //   375: aload_0
    //   376: getfield mContext : Landroid/content/Context;
    //   379: invokevirtual getContentResolver : ()Landroid/content/ContentResolver;
    //   382: aload_3
    //   383: aload_1
    //   384: invokestatic putString : (Landroid/content/ContentResolver;Ljava/lang/String;Ljava/lang/String;)Z
    //   387: pop
    //   388: ldc 'IflytekService'
    //   390: ldc_w 'addNetworkAccessAppWhiteListInternal: finish write'
    //   393: invokestatic d : (Ljava/lang/String;Ljava/lang/String;)I
    //   396: pop
    //   397: lload #5
    //   399: invokestatic restoreCallingIdentity : (J)V
    //   402: aload_2
    //   403: invokeinterface iterator : ()Ljava/util/Iterator;
    //   408: astore_1
    //   409: aload_1
    //   410: invokeinterface hasNext : ()Z
    //   415: ifeq -> 564
    //   418: aload_1
    //   419: invokeinterface next : ()Ljava/lang/Object;
    //   424: checkcast java/lang/String
    //   427: astore_2
    //   428: ldc 'IflytekService'
    //   430: new java/lang/StringBuilder
    //   433: dup
    //   434: invokespecial <init> : ()V
    //   437: ldc_w 'addNetworkAccessAppWhiteListInternal: networkName1 = '
    //   440: invokevirtual append : (Ljava/lang/String;)Ljava/lang/StringBuilder;
    //   443: aload_2
    //   444: invokevirtual append : (Ljava/lang/String;)Ljava/lang/StringBuilder;
    //   447: invokevirtual toString : ()Ljava/lang/String;
    //   450: invokestatic d : (Ljava/lang/String;Ljava/lang/String;)I
    //   453: pop
    //   454: ldc_w ''
    //   457: aload_2
    //   458: invokevirtual equals : (Ljava/lang/Object;)Z
    //   461: ifne -> 409
    //   464: aload_0
    //   465: getfield mNetworkService : Landroid/os/INetworkManagementService;
    //   468: ifnull -> 409
    //   471: invokestatic clearCallingIdentity : ()J
    //   474: lstore #5
    //   476: aload_0
    //   477: aload_2
    //   478: invokespecial getUidFromPackageName : (Ljava/lang/String;)I
    //   481: istore #4
    //   483: iload #4
    //   485: iflt -> 514
    //   488: aload_0
    //   489: getfield mNetworkService : Landroid/os/INetworkManagementService;
    //   492: invokeinterface isIpv6FirewallAppEnabled : ()Z
    //   497: ifeq -> 514
    //   500: aload_0
    //   501: getfield mHandler : Landroid/os/Handler;
    //   504: iconst_2
    //   505: iload #4
    //   507: iconst_1
    //   508: invokevirtual obtainMessage : (III)Landroid/os/Message;
    //   511: invokevirtual sendToTarget : ()V
    //   514: lload #5
    //   516: invokestatic restoreCallingIdentity : (J)V
    //   519: goto -> 409
    //   522: astore_1
    //   523: aload_1
    //   524: invokevirtual printStackTrace : ()V
    //   527: lload #5
    //   529: invokestatic restoreCallingIdentity : (J)V
    //   532: goto -> 402
    //   535: astore_1
    //   536: lload #5
    //   538: invokestatic restoreCallingIdentity : (J)V
    //   541: aload_1
    //   542: athrow
    //   543: astore_2
    //   544: aload_2
    //   545: invokevirtual printStackTrace : ()V
    //   548: lload #5
    //   550: invokestatic restoreCallingIdentity : (J)V
    //   553: goto -> 409
    //   556: astore_1
    //   557: lload #5
    //   559: invokestatic restoreCallingIdentity : (J)V
    //   562: aload_1
    //   563: athrow
    //   564: iconst_1
    //   565: ireturn
    // Exception table:
    //   from	to	target	type
    //   78	116	284	java/lang/Exception
    //   78	116	299	finally
    //   121	131	284	java/lang/Exception
    //   121	131	299	finally
    //   229	250	307	java/lang/Exception
    //   229	250	322	finally
    //   286	291	299	finally
    //   309	314	322	finally
    //   366	397	522	java/lang/Exception
    //   366	397	535	finally
    //   488	514	543	java/lang/Exception
    //   488	514	556	finally
    //   523	527	535	finally
    //   544	548	556	finally
  }
  
  private boolean addNetworkAccessWhiteListInternal(ComponentName paramComponentName, List<String> paramList, String paramString) {
    // Byte code:
    //   0: aload_1
    //   1: ifnonnull -> 15
    //   4: new java/lang/IllegalArgumentException
    //   7: dup
    //   8: ldc_w 'input component name is null'
    //   11: invokespecial <init> : (Ljava/lang/String;)V
    //   14: athrow
    //   15: aload_2
    //   16: ifnonnull -> 30
    //   19: new java/lang/IllegalArgumentException
    //   22: dup
    //   23: ldc_w 'input network access white list is null'
    //   26: invokespecial <init> : (Ljava/lang/String;)V
    //   29: athrow
    //   30: aload_2
    //   31: invokeinterface isEmpty : ()Z
    //   36: ifeq -> 50
    //   39: new java/lang/IllegalArgumentException
    //   42: dup
    //   43: ldc_w 'input network access white list is empty'
    //   46: invokespecial <init> : (Ljava/lang/String;)V
    //   49: athrow
    //   50: aload_2
    //   51: invokeinterface size : ()I
    //   56: sipush #1000
    //   59: if_icmple -> 73
    //   62: new java/lang/IllegalArgumentException
    //   65: dup
    //   66: ldc_w 'input network access white list num > 1000'
    //   69: invokespecial <init> : (Ljava/lang/String;)V
    //   72: athrow
    //   73: aload_0
    //   74: aload_1
    //   75: invokespecial checkActiveAndUserId : (Landroid/content/ComponentName;)V
    //   78: new java/util/StringJoiner
    //   81: dup
    //   82: ldc_w ','
    //   85: invokespecial <init> : (Ljava/lang/CharSequence;)V
    //   88: astore #10
    //   90: aconst_null
    //   91: astore_1
    //   92: aconst_null
    //   93: astore #9
    //   95: iconst_0
    //   96: invokestatic setUserRequired : (Z)V
    //   99: new java/lang/StringBuilder
    //   102: dup
    //   103: invokespecial <init> : ()V
    //   106: invokestatic getDataDirectory : ()Ljava/io/File;
    //   109: invokevirtual append : (Ljava/lang/Object;)Ljava/lang/StringBuilder;
    //   112: ldc '/system/'
    //   114: invokevirtual append : (Ljava/lang/String;)Ljava/lang/StringBuilder;
    //   117: ldc 'netwhitelist.txt'
    //   119: invokevirtual append : (Ljava/lang/String;)Ljava/lang/StringBuilder;
    //   122: invokevirtual toString : ()Ljava/lang/String;
    //   125: astore #8
    //   127: new java/io/File
    //   130: dup
    //   131: aload #8
    //   133: invokespecial <init> : (Ljava/lang/String;)V
    //   136: astore #11
    //   138: aload #11
    //   140: invokevirtual exists : ()Z
    //   143: ifne -> 551
    //   146: ldc 'IflytekService'
    //   148: ldc_w 'addNetworkAccessWhiteListInternal: file not exists'
    //   151: invokestatic d : (Ljava/lang/String;Ljava/lang/String;)I
    //   154: pop
    //   155: aload #11
    //   157: invokevirtual createNewFile : ()Z
    //   160: pop
    //   161: aload_0
    //   162: getfield mNetworkService : Landroid/os/INetworkManagementService;
    //   165: ifnull -> 322
    //   168: invokestatic clearCallingIdentity : ()J
    //   171: lstore #5
    //   173: aload_0
    //   174: getfield mNetworkService : Landroid/os/INetworkManagementService;
    //   177: invokeinterface isFirewallAppEnabled : ()Z
    //   182: istore #7
    //   184: ldc 'IflytekService'
    //   186: new java/lang/StringBuilder
    //   189: dup
    //   190: invokespecial <init> : ()V
    //   193: ldc_w 'addNetworkAccessWhiteListInternal: isFirewallEnabled = '
    //   196: invokevirtual append : (Ljava/lang/String;)Ljava/lang/StringBuilder;
    //   199: iload #7
    //   201: invokevirtual append : (Z)Ljava/lang/StringBuilder;
    //   204: invokevirtual toString : ()Ljava/lang/String;
    //   207: invokestatic d : (Ljava/lang/String;Ljava/lang/String;)I
    //   210: pop
    //   211: iload #7
    //   213: ifne -> 226
    //   216: aload_0
    //   217: getfield mNetworkService : Landroid/os/INetworkManagementService;
    //   220: iconst_1
    //   221: invokeinterface setFirewallAppEnabled : (Z)V
    //   226: aload_0
    //   227: getfield mWifiManager : Landroid/net/wifi/WifiManager;
    //   230: invokevirtual getConnectionInfo : ()Landroid/net/wifi/WifiInfo;
    //   233: invokevirtual getIpAddress : ()I
    //   236: istore #4
    //   238: ldc 'IflytekService'
    //   240: new java/lang/StringBuilder
    //   243: dup
    //   244: invokespecial <init> : ()V
    //   247: ldc_w 'addNetworkAccessWhiteListInternal: ipAddress = '
    //   250: invokevirtual append : (Ljava/lang/String;)Ljava/lang/StringBuilder;
    //   253: iload #4
    //   255: invokevirtual append : (I)Ljava/lang/StringBuilder;
    //   258: invokevirtual toString : ()Ljava/lang/String;
    //   261: invokestatic d : (Ljava/lang/String;Ljava/lang/String;)I
    //   264: pop
    //   265: iload #4
    //   267: ifeq -> 317
    //   270: aload_0
    //   271: iload #4
    //   273: invokespecial intToIp : (I)Ljava/lang/String;
    //   276: astore #11
    //   278: ldc 'IflytekService'
    //   280: new java/lang/StringBuilder
    //   283: dup
    //   284: invokespecial <init> : ()V
    //   287: ldc_w 'addNetworkAccessWhiteListInternal: wlan0Ip = '
    //   290: invokevirtual append : (Ljava/lang/String;)Ljava/lang/StringBuilder;
    //   293: aload #11
    //   295: invokevirtual append : (Ljava/lang/String;)Ljava/lang/StringBuilder;
    //   298: invokevirtual toString : ()Ljava/lang/String;
    //   301: invokestatic d : (Ljava/lang/String;Ljava/lang/String;)I
    //   304: pop
    //   305: aload_0
    //   306: getfield mNetworkService : Landroid/os/INetworkManagementService;
    //   309: aload #11
    //   311: iconst_1
    //   312: invokeinterface enableUrl : (Ljava/lang/String;Z)V
    //   317: lload #5
    //   319: invokestatic restoreCallingIdentity : (J)V
    //   322: new java/io/RandomAccessFile
    //   325: dup
    //   326: aload #8
    //   328: ldc_w 'rw'
    //   331: invokespecial <init> : (Ljava/lang/String;Ljava/lang/String;)V
    //   334: astore #8
    //   336: aload #8
    //   338: aload #8
    //   340: invokevirtual length : ()J
    //   343: invokevirtual seek : (J)V
    //   346: aload_2
    //   347: invokeinterface iterator : ()Ljava/util/Iterator;
    //   352: astore_1
    //   353: aload_1
    //   354: invokeinterface hasNext : ()Z
    //   359: ifeq -> 685
    //   362: aload_1
    //   363: invokeinterface next : ()Ljava/lang/Object;
    //   368: checkcast java/lang/String
    //   371: astore_2
    //   372: ldc 'IflytekService'
    //   374: new java/lang/StringBuilder
    //   377: dup
    //   378: invokespecial <init> : ()V
    //   381: ldc_w 'addNetworkAccessWhiteListInternal: networkName = '
    //   384: invokevirtual append : (Ljava/lang/String;)Ljava/lang/StringBuilder;
    //   387: aload_2
    //   388: invokevirtual append : (Ljava/lang/String;)Ljava/lang/StringBuilder;
    //   391: invokevirtual toString : ()Ljava/lang/String;
    //   394: invokestatic d : (Ljava/lang/String;Ljava/lang/String;)I
    //   397: pop
    //   398: ldc_w ''
    //   401: aload_2
    //   402: invokevirtual equals : (Ljava/lang/Object;)Z
    //   405: ifne -> 353
    //   408: aload #10
    //   410: aload_2
    //   411: invokevirtual add : (Ljava/lang/CharSequence;)Ljava/util/StringJoiner;
    //   414: pop
    //   415: aload_0
    //   416: aload_2
    //   417: invokespecial isIpAddress : (Ljava/lang/String;)Z
    //   420: ifeq -> 644
    //   423: aload_0
    //   424: getfield mNetworkService : Landroid/os/INetworkManagementService;
    //   427: ifnull -> 353
    //   430: aload_0
    //   431: getfield mNetworkService : Landroid/os/INetworkManagementService;
    //   434: aload_2
    //   435: iconst_1
    //   436: invokeinterface enableUrl : (Ljava/lang/String;Z)V
    //   441: goto -> 353
    //   444: astore_1
    //   445: aload #8
    //   447: astore_2
    //   448: aload_1
    //   449: astore #8
    //   451: aload_2
    //   452: astore_1
    //   453: aload #8
    //   455: invokevirtual printStackTrace : ()V
    //   458: aload_2
    //   459: ifnull -> 466
    //   462: aload_2
    //   463: invokevirtual close : ()V
    //   466: aload #10
    //   468: invokevirtual toString : ()Ljava/lang/String;
    //   471: astore_1
    //   472: ldc 'IflytekService'
    //   474: new java/lang/StringBuilder
    //   477: dup
    //   478: invokespecial <init> : ()V
    //   481: ldc_w 'addNetworkAccessWhiteListInternal: networkAccessListStr = '
    //   484: invokevirtual append : (Ljava/lang/String;)Ljava/lang/StringBuilder;
    //   487: aload_1
    //   488: invokevirtual append : (Ljava/lang/String;)Ljava/lang/StringBuilder;
    //   491: invokevirtual toString : ()Ljava/lang/String;
    //   494: invokestatic d : (Ljava/lang/String;Ljava/lang/String;)I
    //   497: pop
    //   498: invokestatic clearCallingIdentity : ()J
    //   501: lstore #5
    //   503: ldc 'IflytekService'
    //   505: ldc_w 'addNetworkAccessWhiteListInternal: begin write'
    //   508: invokestatic d : (Ljava/lang/String;Ljava/lang/String;)I
    //   511: pop
    //   512: aload_0
    //   513: getfield mContext : Landroid/content/Context;
    //   516: invokevirtual getContentResolver : ()Landroid/content/ContentResolver;
    //   519: aload_3
    //   520: aload_1
    //   521: invokestatic putString : (Landroid/content/ContentResolver;Ljava/lang/String;Ljava/lang/String;)Z
    //   524: pop
    //   525: ldc 'IflytekService'
    //   527: ldc_w 'addNetworkAccessWhiteListInternal: finish write'
    //   530: invokestatic d : (Ljava/lang/String;Ljava/lang/String;)I
    //   533: pop
    //   534: lload #5
    //   536: invokestatic restoreCallingIdentity : (J)V
    //   539: iconst_1
    //   540: ireturn
    //   541: astore #11
    //   543: aload #11
    //   545: invokevirtual printStackTrace : ()V
    //   548: goto -> 161
    //   551: ldc 'IflytekService'
    //   553: ldc_w 'addNetworkAccessWhiteListInternal: file exists'
    //   556: invokestatic d : (Ljava/lang/String;Ljava/lang/String;)I
    //   559: pop
    //   560: aload #11
    //   562: invokevirtual isFile : ()Z
    //   565: ifeq -> 585
    //   568: aload #11
    //   570: invokevirtual delete : ()Z
    //   573: ifne -> 585
    //   576: ldc 'IflytekService'
    //   578: ldc_w 'addNetworkAccessWhiteListInternal: file delete fail'
    //   581: invokestatic e : (Ljava/lang/String;Ljava/lang/String;)I
    //   584: pop
    //   585: aload #11
    //   587: invokevirtual exists : ()Z
    //   590: ifne -> 161
    //   593: ldc 'IflytekService'
    //   595: ldc_w 'addNetworkAccessWhiteListInternal: file not exists'
    //   598: invokestatic d : (Ljava/lang/String;Ljava/lang/String;)I
    //   601: pop
    //   602: aload #11
    //   604: invokevirtual createNewFile : ()Z
    //   607: pop
    //   608: goto -> 161
    //   611: astore #11
    //   613: aload #11
    //   615: invokevirtual printStackTrace : ()V
    //   618: goto -> 161
    //   621: astore #11
    //   623: aload #11
    //   625: invokevirtual printStackTrace : ()V
    //   628: lload #5
    //   630: invokestatic restoreCallingIdentity : (J)V
    //   633: goto -> 322
    //   636: astore_1
    //   637: lload #5
    //   639: invokestatic restoreCallingIdentity : (J)V
    //   642: aload_1
    //   643: athrow
    //   644: aload #8
    //   646: new java/lang/StringBuilder
    //   649: dup
    //   650: invokespecial <init> : ()V
    //   653: aload_2
    //   654: invokevirtual append : (Ljava/lang/String;)Ljava/lang/StringBuilder;
    //   657: ldc_w ','
    //   660: invokevirtual append : (Ljava/lang/String;)Ljava/lang/StringBuilder;
    //   663: invokevirtual toString : ()Ljava/lang/String;
    //   666: invokevirtual writeBytes : (Ljava/lang/String;)V
    //   669: goto -> 353
    //   672: astore_1
    //   673: aload #8
    //   675: ifnull -> 683
    //   678: aload #8
    //   680: invokevirtual close : ()V
    //   683: aload_1
    //   684: athrow
    //   685: ldc 'IflytekService'
    //   687: new java/lang/StringBuilder
    //   690: dup
    //   691: invokespecial <init> : ()V
    //   694: ldc_w 'addNetworkAccessWhiteListInternal: sj = '
    //   697: invokevirtual append : (Ljava/lang/String;)Ljava/lang/StringBuilder;
    //   700: aload #10
    //   702: invokevirtual append : (Ljava/lang/Object;)Ljava/lang/StringBuilder;
    //   705: invokevirtual toString : ()Ljava/lang/String;
    //   708: invokestatic d : (Ljava/lang/String;Ljava/lang/String;)I
    //   711: pop
    //   712: aload #8
    //   714: ifnull -> 722
    //   717: aload #8
    //   719: invokevirtual close : ()V
    //   722: goto -> 466
    //   725: astore_1
    //   726: aload_1
    //   727: invokevirtual printStackTrace : ()V
    //   730: goto -> 722
    //   733: astore_1
    //   734: aload_1
    //   735: invokevirtual printStackTrace : ()V
    //   738: goto -> 466
    //   741: astore_2
    //   742: aload_2
    //   743: invokevirtual printStackTrace : ()V
    //   746: goto -> 683
    //   749: astore_1
    //   750: aload_1
    //   751: invokevirtual printStackTrace : ()V
    //   754: lload #5
    //   756: invokestatic restoreCallingIdentity : (J)V
    //   759: goto -> 539
    //   762: astore_1
    //   763: lload #5
    //   765: invokestatic restoreCallingIdentity : (J)V
    //   768: aload_1
    //   769: athrow
    //   770: astore_2
    //   771: aload_1
    //   772: astore #8
    //   774: aload_2
    //   775: astore_1
    //   776: goto -> 673
    //   779: astore #8
    //   781: aload #9
    //   783: astore_2
    //   784: goto -> 451
    // Exception table:
    //   from	to	target	type
    //   155	161	541	java/lang/Exception
    //   173	211	621	java/lang/Exception
    //   173	211	636	finally
    //   216	226	621	java/lang/Exception
    //   216	226	636	finally
    //   226	265	621	java/lang/Exception
    //   226	265	636	finally
    //   270	317	621	java/lang/Exception
    //   270	317	636	finally
    //   322	336	779	java/lang/Exception
    //   322	336	770	finally
    //   336	353	444	java/lang/Exception
    //   336	353	672	finally
    //   353	441	444	java/lang/Exception
    //   353	441	672	finally
    //   453	458	770	finally
    //   462	466	733	java/lang/Exception
    //   503	534	749	java/lang/Exception
    //   503	534	762	finally
    //   602	608	611	java/lang/Exception
    //   623	628	636	finally
    //   644	669	444	java/lang/Exception
    //   644	669	672	finally
    //   678	683	741	java/lang/Exception
    //   685	712	444	java/lang/Exception
    //   685	712	672	finally
    //   717	722	725	java/lang/Exception
    //   750	754	762	finally
  }
  
  private void checkActiveAndUserId(ComponentName paramComponentName) {
    DevicePolicyManager devicePolicyManager = (DevicePolicyManager)this.mContext.getSystemService("device_policy");
    if (devicePolicyManager == null)
      return; 
    if (!devicePolicyManager.isAdminActive(paramComponentName))
      throw new SecurityException("not active: " + paramComponentName.getPackageName()); 
  }
  
  private void cleanNetworkAccessAppWhiteListInternal(ComponentName paramComponentName, String paramString) {
    // Byte code:
    //   0: ldc 'IflytekService'
    //   2: new java/lang/StringBuilder
    //   5: dup
    //   6: invokespecial <init> : ()V
    //   9: ldc_w 'cleanNetworkAccessAppWhiteListInternal: admin = '
    //   12: invokevirtual append : (Ljava/lang/String;)Ljava/lang/StringBuilder;
    //   15: aload_1
    //   16: invokevirtual append : (Ljava/lang/Object;)Ljava/lang/StringBuilder;
    //   19: invokevirtual toString : ()Ljava/lang/String;
    //   22: invokestatic d : (Ljava/lang/String;Ljava/lang/String;)I
    //   25: pop
    //   26: aload_1
    //   27: ifnonnull -> 41
    //   30: new java/lang/IllegalArgumentException
    //   33: dup
    //   34: ldc_w 'input component name is null'
    //   37: invokespecial <init> : (Ljava/lang/String;)V
    //   40: athrow
    //   41: aload_0
    //   42: aload_1
    //   43: invokespecial checkActiveAndUserId : (Landroid/content/ComponentName;)V
    //   46: aload_0
    //   47: aload_1
    //   48: aload_2
    //   49: invokespecial getNetworkAccessAppWhiteListInternal : (Landroid/content/ComponentName;Ljava/lang/String;)Ljava/util/List;
    //   52: astore_1
    //   53: aload_1
    //   54: ifnull -> 165
    //   57: aload_0
    //   58: getfield mNetworkService : Landroid/os/INetworkManagementService;
    //   61: ifnull -> 165
    //   64: invokestatic clearCallingIdentity : ()J
    //   67: lstore #4
    //   69: aload_1
    //   70: invokeinterface iterator : ()Ljava/util/Iterator;
    //   75: astore #6
    //   77: aload #6
    //   79: invokeinterface hasNext : ()Z
    //   84: ifeq -> 332
    //   87: aload #6
    //   89: invokeinterface next : ()Ljava/lang/Object;
    //   94: checkcast java/lang/String
    //   97: astore #7
    //   99: ldc 'IflytekService'
    //   101: new java/lang/StringBuilder
    //   104: dup
    //   105: invokespecial <init> : ()V
    //   108: ldc_w 'cleanNetworkAccessAppWhiteListInternal: networkName = '
    //   111: invokevirtual append : (Ljava/lang/String;)Ljava/lang/StringBuilder;
    //   114: aload #7
    //   116: invokevirtual append : (Ljava/lang/String;)Ljava/lang/StringBuilder;
    //   119: invokevirtual toString : ()Ljava/lang/String;
    //   122: invokestatic d : (Ljava/lang/String;Ljava/lang/String;)I
    //   125: pop
    //   126: aload_0
    //   127: aload #7
    //   129: invokespecial getUidFromPackageName : (Ljava/lang/String;)I
    //   132: istore_3
    //   133: iload_3
    //   134: iflt -> 77
    //   137: aload_0
    //   138: getfield mHandler : Landroid/os/Handler;
    //   141: iconst_1
    //   142: iload_3
    //   143: iconst_0
    //   144: invokevirtual obtainMessage : (III)Landroid/os/Message;
    //   147: invokevirtual sendToTarget : ()V
    //   150: goto -> 77
    //   153: astore #6
    //   155: aload #6
    //   157: invokevirtual printStackTrace : ()V
    //   160: lload #4
    //   162: invokestatic restoreCallingIdentity : (J)V
    //   165: invokestatic clearCallingIdentity : ()J
    //   168: lstore #4
    //   170: ldc 'IflytekService'
    //   172: ldc_w 'cleanNetworkAccessAppWhiteListInternal: begin write'
    //   175: invokestatic d : (Ljava/lang/String;Ljava/lang/String;)I
    //   178: pop
    //   179: aload_0
    //   180: getfield mContext : Landroid/content/Context;
    //   183: invokevirtual getContentResolver : ()Landroid/content/ContentResolver;
    //   186: aload_2
    //   187: ldc_w ''
    //   190: invokestatic putString : (Landroid/content/ContentResolver;Ljava/lang/String;Ljava/lang/String;)Z
    //   193: pop
    //   194: ldc 'IflytekService'
    //   196: ldc_w 'cleanNetworkAccessAppWhiteListInternal: finish write'
    //   199: invokestatic d : (Ljava/lang/String;Ljava/lang/String;)I
    //   202: pop
    //   203: lload #4
    //   205: invokestatic restoreCallingIdentity : (J)V
    //   208: aload_1
    //   209: ifnull -> 331
    //   212: aload_0
    //   213: getfield mNetworkService : Landroid/os/INetworkManagementService;
    //   216: ifnull -> 331
    //   219: invokestatic clearCallingIdentity : ()J
    //   222: lstore #4
    //   224: aload_0
    //   225: getfield mNetworkService : Landroid/os/INetworkManagementService;
    //   228: invokeinterface isIpv6FirewallAppEnabled : ()Z
    //   233: ifeq -> 326
    //   236: aload_0
    //   237: getfield mNetworkService : Landroid/os/INetworkManagementService;
    //   240: iconst_0
    //   241: invokeinterface setIpv6FirewallAppEnabled : (Z)V
    //   246: aload_0
    //   247: getfield mNetworkService : Landroid/os/INetworkManagementService;
    //   250: iconst_1
    //   251: invokeinterface setIpv6FirewallAppEnabled : (Z)V
    //   256: aload_0
    //   257: getfield uidOta : I
    //   260: iflt -> 291
    //   263: aload_0
    //   264: getfield mNetworkService : Landroid/os/INetworkManagementService;
    //   267: invokeinterface isIpv6FirewallAppEnabled : ()Z
    //   272: ifeq -> 291
    //   275: aload_0
    //   276: getfield mHandler : Landroid/os/Handler;
    //   279: iconst_2
    //   280: aload_0
    //   281: getfield uidOta : I
    //   284: iconst_1
    //   285: invokevirtual obtainMessage : (III)Landroid/os/Message;
    //   288: invokevirtual sendToTarget : ()V
    //   291: aload_0
    //   292: getfield uidLogservices : I
    //   295: iflt -> 326
    //   298: aload_0
    //   299: getfield mNetworkService : Landroid/os/INetworkManagementService;
    //   302: invokeinterface isIpv6FirewallAppEnabled : ()Z
    //   307: ifeq -> 326
    //   310: aload_0
    //   311: getfield mHandler : Landroid/os/Handler;
    //   314: iconst_2
    //   315: aload_0
    //   316: getfield uidLogservices : I
    //   319: iconst_1
    //   320: invokevirtual obtainMessage : (III)Landroid/os/Message;
    //   323: invokevirtual sendToTarget : ()V
    //   326: lload #4
    //   328: invokestatic restoreCallingIdentity : (J)V
    //   331: return
    //   332: lload #4
    //   334: invokestatic restoreCallingIdentity : (J)V
    //   337: goto -> 165
    //   340: astore_1
    //   341: lload #4
    //   343: invokestatic restoreCallingIdentity : (J)V
    //   346: aload_1
    //   347: athrow
    //   348: astore_2
    //   349: aload_2
    //   350: invokevirtual printStackTrace : ()V
    //   353: lload #4
    //   355: invokestatic restoreCallingIdentity : (J)V
    //   358: goto -> 208
    //   361: astore_1
    //   362: lload #4
    //   364: invokestatic restoreCallingIdentity : (J)V
    //   367: aload_1
    //   368: athrow
    //   369: astore_1
    //   370: aload_1
    //   371: invokevirtual printStackTrace : ()V
    //   374: lload #4
    //   376: invokestatic restoreCallingIdentity : (J)V
    //   379: return
    //   380: astore_1
    //   381: lload #4
    //   383: invokestatic restoreCallingIdentity : (J)V
    //   386: aload_1
    //   387: athrow
    // Exception table:
    //   from	to	target	type
    //   69	77	153	java/lang/Exception
    //   69	77	340	finally
    //   77	133	153	java/lang/Exception
    //   77	133	340	finally
    //   137	150	153	java/lang/Exception
    //   137	150	340	finally
    //   155	160	340	finally
    //   170	203	348	java/lang/Exception
    //   170	203	361	finally
    //   224	291	369	java/lang/Exception
    //   224	291	380	finally
    //   291	326	369	java/lang/Exception
    //   291	326	380	finally
    //   349	353	361	finally
    //   370	374	380	finally
  }
  
  private void cleanNetworkAccessWhiteListInternal(ComponentName paramComponentName, String paramString) {
    // Byte code:
    //   0: ldc 'IflytekService'
    //   2: new java/lang/StringBuilder
    //   5: dup
    //   6: invokespecial <init> : ()V
    //   9: ldc_w 'cleanNetworkAccessWhiteListInternal: admin = '
    //   12: invokevirtual append : (Ljava/lang/String;)Ljava/lang/StringBuilder;
    //   15: aload_1
    //   16: invokevirtual append : (Ljava/lang/Object;)Ljava/lang/StringBuilder;
    //   19: ldc_w ', networkAccessList = '
    //   22: invokevirtual append : (Ljava/lang/String;)Ljava/lang/StringBuilder;
    //   25: aload_2
    //   26: invokevirtual append : (Ljava/lang/String;)Ljava/lang/StringBuilder;
    //   29: invokevirtual toString : ()Ljava/lang/String;
    //   32: invokestatic d : (Ljava/lang/String;Ljava/lang/String;)I
    //   35: pop
    //   36: aload_1
    //   37: ifnonnull -> 51
    //   40: new java/lang/IllegalArgumentException
    //   43: dup
    //   44: ldc_w 'input component name is null'
    //   47: invokespecial <init> : (Ljava/lang/String;)V
    //   50: athrow
    //   51: aload_0
    //   52: aload_1
    //   53: invokespecial checkActiveAndUserId : (Landroid/content/ComponentName;)V
    //   56: new java/io/File
    //   59: dup
    //   60: new java/lang/StringBuilder
    //   63: dup
    //   64: invokespecial <init> : ()V
    //   67: invokestatic getDataDirectory : ()Ljava/io/File;
    //   70: invokevirtual append : (Ljava/lang/Object;)Ljava/lang/StringBuilder;
    //   73: ldc '/system/'
    //   75: invokevirtual append : (Ljava/lang/String;)Ljava/lang/StringBuilder;
    //   78: ldc 'netwhitelist.txt'
    //   80: invokevirtual append : (Ljava/lang/String;)Ljava/lang/StringBuilder;
    //   83: invokevirtual toString : ()Ljava/lang/String;
    //   86: invokespecial <init> : (Ljava/lang/String;)V
    //   89: astore #7
    //   91: aload #7
    //   93: invokevirtual exists : ()Z
    //   96: ifeq -> 133
    //   99: ldc 'IflytekService'
    //   101: ldc_w 'cleanNetworkAccessWhiteListInternal: file exists'
    //   104: invokestatic d : (Ljava/lang/String;Ljava/lang/String;)I
    //   107: pop
    //   108: aload #7
    //   110: invokevirtual isFile : ()Z
    //   113: ifeq -> 133
    //   116: aload #7
    //   118: invokevirtual delete : ()Z
    //   121: ifne -> 133
    //   124: ldc 'IflytekService'
    //   126: ldc_w 'cleanNetworkAccessWhiteListInternal: file delete fail'
    //   129: invokestatic e : (Ljava/lang/String;Ljava/lang/String;)I
    //   132: pop
    //   133: aload_0
    //   134: getfield mNetworkService : Landroid/os/INetworkManagementService;
    //   137: ifnull -> 303
    //   140: invokestatic clearCallingIdentity : ()J
    //   143: lstore #4
    //   145: aload_0
    //   146: getfield uidOta : I
    //   149: iflt -> 168
    //   152: aload_0
    //   153: getfield mHandler : Landroid/os/Handler;
    //   156: iconst_1
    //   157: aload_0
    //   158: getfield uidOta : I
    //   161: iconst_0
    //   162: invokevirtual obtainMessage : (III)Landroid/os/Message;
    //   165: invokevirtual sendToTarget : ()V
    //   168: aload_0
    //   169: getfield uidLogservices : I
    //   172: iflt -> 191
    //   175: aload_0
    //   176: getfield mHandler : Landroid/os/Handler;
    //   179: iconst_1
    //   180: aload_0
    //   181: getfield uidLogservices : I
    //   184: iconst_0
    //   185: invokevirtual obtainMessage : (III)Landroid/os/Message;
    //   188: invokevirtual sendToTarget : ()V
    //   191: aload_0
    //   192: aload_1
    //   193: ldc_w 'network_access_app_whitelist'
    //   196: invokespecial getNetworkAccessAppWhiteListInternal : (Landroid/content/ComponentName;Ljava/lang/String;)Ljava/util/List;
    //   199: astore #7
    //   201: aload #7
    //   203: ifnull -> 596
    //   206: aload #7
    //   208: invokeinterface iterator : ()Ljava/util/Iterator;
    //   213: astore #8
    //   215: aload #8
    //   217: invokeinterface hasNext : ()Z
    //   222: ifeq -> 596
    //   225: aload #8
    //   227: invokeinterface next : ()Ljava/lang/Object;
    //   232: checkcast java/lang/String
    //   235: astore #9
    //   237: ldc 'IflytekService'
    //   239: new java/lang/StringBuilder
    //   242: dup
    //   243: invokespecial <init> : ()V
    //   246: ldc_w 'cleanNetworkAccessWhiteListInternal: networkName = '
    //   249: invokevirtual append : (Ljava/lang/String;)Ljava/lang/StringBuilder;
    //   252: aload #9
    //   254: invokevirtual append : (Ljava/lang/String;)Ljava/lang/StringBuilder;
    //   257: invokevirtual toString : ()Ljava/lang/String;
    //   260: invokestatic d : (Ljava/lang/String;Ljava/lang/String;)I
    //   263: pop
    //   264: aload_0
    //   265: aload #9
    //   267: invokespecial getUidFromPackageName : (Ljava/lang/String;)I
    //   270: istore_3
    //   271: iload_3
    //   272: iflt -> 215
    //   275: aload_0
    //   276: getfield mHandler : Landroid/os/Handler;
    //   279: iconst_1
    //   280: iload_3
    //   281: iconst_0
    //   282: invokevirtual obtainMessage : (III)Landroid/os/Message;
    //   285: invokevirtual sendToTarget : ()V
    //   288: goto -> 215
    //   291: astore #7
    //   293: aload #7
    //   295: invokevirtual printStackTrace : ()V
    //   298: lload #4
    //   300: invokestatic restoreCallingIdentity : (J)V
    //   303: invokestatic clearCallingIdentity : ()J
    //   306: lstore #4
    //   308: ldc 'IflytekService'
    //   310: ldc_w 'cleanNetworkAccessWhiteListInternal: begin write'
    //   313: invokestatic d : (Ljava/lang/String;Ljava/lang/String;)I
    //   316: pop
    //   317: aload_0
    //   318: getfield mContext : Landroid/content/Context;
    //   321: invokevirtual getContentResolver : ()Landroid/content/ContentResolver;
    //   324: aload_2
    //   325: invokestatic getString : (Landroid/content/ContentResolver;Ljava/lang/String;)Ljava/lang/String;
    //   328: pop
    //   329: aload_0
    //   330: getfield mContext : Landroid/content/Context;
    //   333: invokevirtual getContentResolver : ()Landroid/content/ContentResolver;
    //   336: aload_2
    //   337: ldc_w ''
    //   340: invokestatic putString : (Landroid/content/ContentResolver;Ljava/lang/String;Ljava/lang/String;)Z
    //   343: pop
    //   344: ldc 'IflytekService'
    //   346: ldc_w 'cleanNetworkAccessWhiteListInternal: finish write'
    //   349: invokestatic d : (Ljava/lang/String;Ljava/lang/String;)I
    //   352: pop
    //   353: lload #4
    //   355: invokestatic restoreCallingIdentity : (J)V
    //   358: aload_0
    //   359: getfield mNetworkService : Landroid/os/INetworkManagementService;
    //   362: ifnull -> 585
    //   365: invokestatic clearCallingIdentity : ()J
    //   368: lstore #4
    //   370: aload_0
    //   371: getfield mNetworkService : Landroid/os/INetworkManagementService;
    //   374: invokeinterface isIpv6FirewallAppEnabled : ()Z
    //   379: ifeq -> 402
    //   382: aload_0
    //   383: getfield mNetworkService : Landroid/os/INetworkManagementService;
    //   386: iconst_0
    //   387: invokeinterface setIpv6FirewallAppEnabled : (Z)V
    //   392: aload_0
    //   393: getfield mNetworkService : Landroid/os/INetworkManagementService;
    //   396: iconst_1
    //   397: invokeinterface setIpv6FirewallAppEnabled : (Z)V
    //   402: aload_0
    //   403: getfield uidOta : I
    //   406: iflt -> 437
    //   409: aload_0
    //   410: getfield mNetworkService : Landroid/os/INetworkManagementService;
    //   413: invokeinterface isIpv6FirewallAppEnabled : ()Z
    //   418: ifeq -> 437
    //   421: aload_0
    //   422: getfield mHandler : Landroid/os/Handler;
    //   425: iconst_2
    //   426: aload_0
    //   427: getfield uidOta : I
    //   430: iconst_1
    //   431: invokevirtual obtainMessage : (III)Landroid/os/Message;
    //   434: invokevirtual sendToTarget : ()V
    //   437: aload_0
    //   438: getfield uidLogservices : I
    //   441: iflt -> 472
    //   444: aload_0
    //   445: getfield mNetworkService : Landroid/os/INetworkManagementService;
    //   448: invokeinterface isIpv6FirewallAppEnabled : ()Z
    //   453: ifeq -> 472
    //   456: aload_0
    //   457: getfield mHandler : Landroid/os/Handler;
    //   460: iconst_2
    //   461: aload_0
    //   462: getfield uidLogservices : I
    //   465: iconst_1
    //   466: invokevirtual obtainMessage : (III)Landroid/os/Message;
    //   469: invokevirtual sendToTarget : ()V
    //   472: aload_0
    //   473: aload_1
    //   474: ldc_w 'network_access_app_whitelist'
    //   477: invokespecial getNetworkAccessAppWhiteListInternal : (Landroid/content/ComponentName;Ljava/lang/String;)Ljava/util/List;
    //   480: astore_1
    //   481: aload_1
    //   482: ifnull -> 932
    //   485: aload_1
    //   486: invokeinterface iterator : ()Ljava/util/Iterator;
    //   491: astore_1
    //   492: aload_1
    //   493: invokeinterface hasNext : ()Z
    //   498: ifeq -> 932
    //   501: aload_1
    //   502: invokeinterface next : ()Ljava/lang/Object;
    //   507: checkcast java/lang/String
    //   510: astore_2
    //   511: ldc 'IflytekService'
    //   513: new java/lang/StringBuilder
    //   516: dup
    //   517: invokespecial <init> : ()V
    //   520: ldc_w 'cleanNetworkAccessWhiteListInternal: networkName = '
    //   523: invokevirtual append : (Ljava/lang/String;)Ljava/lang/StringBuilder;
    //   526: aload_2
    //   527: invokevirtual append : (Ljava/lang/String;)Ljava/lang/StringBuilder;
    //   530: invokevirtual toString : ()Ljava/lang/String;
    //   533: invokestatic d : (Ljava/lang/String;Ljava/lang/String;)I
    //   536: pop
    //   537: aload_0
    //   538: aload_2
    //   539: invokespecial getUidFromPackageName : (Ljava/lang/String;)I
    //   542: istore_3
    //   543: iload_3
    //   544: iflt -> 492
    //   547: aload_0
    //   548: getfield mNetworkService : Landroid/os/INetworkManagementService;
    //   551: invokeinterface isIpv6FirewallAppEnabled : ()Z
    //   556: ifeq -> 492
    //   559: aload_0
    //   560: getfield mHandler : Landroid/os/Handler;
    //   563: iconst_2
    //   564: iload_3
    //   565: iconst_1
    //   566: invokevirtual obtainMessage : (III)Landroid/os/Message;
    //   569: invokevirtual sendToTarget : ()V
    //   572: goto -> 492
    //   575: astore_1
    //   576: aload_1
    //   577: invokevirtual printStackTrace : ()V
    //   580: lload #4
    //   582: invokestatic restoreCallingIdentity : (J)V
    //   585: return
    //   586: astore #7
    //   588: aload #7
    //   590: invokevirtual printStackTrace : ()V
    //   593: goto -> 133
    //   596: aload_0
    //   597: getfield mNetworkService : Landroid/os/INetworkManagementService;
    //   600: invokeinterface isFirewallAppEnabled : ()Z
    //   605: istore #6
    //   607: ldc 'IflytekService'
    //   609: new java/lang/StringBuilder
    //   612: dup
    //   613: invokespecial <init> : ()V
    //   616: ldc_w 'cleanNetworkAccessWhiteListInternal: isFirewallEnabled = '
    //   619: invokevirtual append : (Ljava/lang/String;)Ljava/lang/StringBuilder;
    //   622: iload #6
    //   624: invokevirtual append : (Z)Ljava/lang/StringBuilder;
    //   627: invokevirtual toString : ()Ljava/lang/String;
    //   630: invokestatic d : (Ljava/lang/String;Ljava/lang/String;)I
    //   633: pop
    //   634: iload #6
    //   636: ifne -> 880
    //   639: aload_0
    //   640: getfield mNetworkService : Landroid/os/INetworkManagementService;
    //   643: iconst_1
    //   644: invokeinterface setFirewallAppEnabled : (Z)V
    //   649: aload_0
    //   650: getfield mWifiManager : Landroid/net/wifi/WifiManager;
    //   653: invokevirtual getConnectionInfo : ()Landroid/net/wifi/WifiInfo;
    //   656: invokevirtual getIpAddress : ()I
    //   659: istore_3
    //   660: ldc 'IflytekService'
    //   662: new java/lang/StringBuilder
    //   665: dup
    //   666: invokespecial <init> : ()V
    //   669: ldc_w 'cleanNetworkAccessWhiteListInternal: ipAddress = '
    //   672: invokevirtual append : (Ljava/lang/String;)Ljava/lang/StringBuilder;
    //   675: iload_3
    //   676: invokevirtual append : (I)Ljava/lang/StringBuilder;
    //   679: invokevirtual toString : ()Ljava/lang/String;
    //   682: invokestatic d : (Ljava/lang/String;Ljava/lang/String;)I
    //   685: pop
    //   686: iload_3
    //   687: ifeq -> 736
    //   690: aload_0
    //   691: iload_3
    //   692: invokespecial intToIp : (I)Ljava/lang/String;
    //   695: astore #8
    //   697: ldc 'IflytekService'
    //   699: new java/lang/StringBuilder
    //   702: dup
    //   703: invokespecial <init> : ()V
    //   706: ldc_w 'cleanNetworkAccessWhiteListInternal: wlan0Ip = '
    //   709: invokevirtual append : (Ljava/lang/String;)Ljava/lang/StringBuilder;
    //   712: aload #8
    //   714: invokevirtual append : (Ljava/lang/String;)Ljava/lang/StringBuilder;
    //   717: invokevirtual toString : ()Ljava/lang/String;
    //   720: invokestatic d : (Ljava/lang/String;Ljava/lang/String;)I
    //   723: pop
    //   724: aload_0
    //   725: getfield mNetworkService : Landroid/os/INetworkManagementService;
    //   728: aload #8
    //   730: iconst_1
    //   731: invokeinterface enableUrl : (Ljava/lang/String;Z)V
    //   736: aload_0
    //   737: getfield uidOta : I
    //   740: iflt -> 759
    //   743: aload_0
    //   744: getfield mHandler : Landroid/os/Handler;
    //   747: iconst_1
    //   748: aload_0
    //   749: getfield uidOta : I
    //   752: iconst_1
    //   753: invokevirtual obtainMessage : (III)Landroid/os/Message;
    //   756: invokevirtual sendToTarget : ()V
    //   759: aload_0
    //   760: getfield uidLogservices : I
    //   763: iflt -> 782
    //   766: aload_0
    //   767: getfield mHandler : Landroid/os/Handler;
    //   770: iconst_1
    //   771: aload_0
    //   772: getfield uidLogservices : I
    //   775: iconst_1
    //   776: invokevirtual obtainMessage : (III)Landroid/os/Message;
    //   779: invokevirtual sendToTarget : ()V
    //   782: aload #7
    //   784: ifnull -> 903
    //   787: aload #7
    //   789: invokeinterface iterator : ()Ljava/util/Iterator;
    //   794: astore #7
    //   796: aload #7
    //   798: invokeinterface hasNext : ()Z
    //   803: ifeq -> 903
    //   806: aload #7
    //   808: invokeinterface next : ()Ljava/lang/Object;
    //   813: checkcast java/lang/String
    //   816: astore #8
    //   818: ldc 'IflytekService'
    //   820: new java/lang/StringBuilder
    //   823: dup
    //   824: invokespecial <init> : ()V
    //   827: ldc_w 'cleanNetworkAccessWhiteListInternal: networkName = '
    //   830: invokevirtual append : (Ljava/lang/String;)Ljava/lang/StringBuilder;
    //   833: aload #8
    //   835: invokevirtual append : (Ljava/lang/String;)Ljava/lang/StringBuilder;
    //   838: invokevirtual toString : ()Ljava/lang/String;
    //   841: invokestatic d : (Ljava/lang/String;Ljava/lang/String;)I
    //   844: pop
    //   845: aload_0
    //   846: aload #8
    //   848: invokespecial getUidFromPackageName : (Ljava/lang/String;)I
    //   851: istore_3
    //   852: iload_3
    //   853: iflt -> 796
    //   856: aload_0
    //   857: getfield mHandler : Landroid/os/Handler;
    //   860: iconst_1
    //   861: iload_3
    //   862: iconst_1
    //   863: invokevirtual obtainMessage : (III)Landroid/os/Message;
    //   866: invokevirtual sendToTarget : ()V
    //   869: goto -> 796
    //   872: astore_1
    //   873: lload #4
    //   875: invokestatic restoreCallingIdentity : (J)V
    //   878: aload_1
    //   879: athrow
    //   880: aload_0
    //   881: getfield mNetworkService : Landroid/os/INetworkManagementService;
    //   884: iconst_0
    //   885: invokeinterface setFirewallAppEnabled : (Z)V
    //   890: aload_0
    //   891: getfield mNetworkService : Landroid/os/INetworkManagementService;
    //   894: iconst_1
    //   895: invokeinterface setFirewallAppEnabled : (Z)V
    //   900: goto -> 649
    //   903: lload #4
    //   905: invokestatic restoreCallingIdentity : (J)V
    //   908: goto -> 303
    //   911: astore_2
    //   912: aload_2
    //   913: invokevirtual printStackTrace : ()V
    //   916: lload #4
    //   918: invokestatic restoreCallingIdentity : (J)V
    //   921: goto -> 358
    //   924: astore_1
    //   925: lload #4
    //   927: invokestatic restoreCallingIdentity : (J)V
    //   930: aload_1
    //   931: athrow
    //   932: lload #4
    //   934: invokestatic restoreCallingIdentity : (J)V
    //   937: return
    //   938: astore_1
    //   939: lload #4
    //   941: invokestatic restoreCallingIdentity : (J)V
    //   944: aload_1
    //   945: athrow
    // Exception table:
    //   from	to	target	type
    //   56	133	586	java/lang/Exception
    //   145	168	291	java/lang/Exception
    //   145	168	872	finally
    //   168	191	291	java/lang/Exception
    //   168	191	872	finally
    //   191	201	291	java/lang/Exception
    //   191	201	872	finally
    //   206	215	291	java/lang/Exception
    //   206	215	872	finally
    //   215	271	291	java/lang/Exception
    //   215	271	872	finally
    //   275	288	291	java/lang/Exception
    //   275	288	872	finally
    //   293	298	872	finally
    //   308	353	911	java/lang/Exception
    //   308	353	924	finally
    //   370	402	575	java/lang/Exception
    //   370	402	938	finally
    //   402	437	575	java/lang/Exception
    //   402	437	938	finally
    //   437	472	575	java/lang/Exception
    //   437	472	938	finally
    //   472	481	575	java/lang/Exception
    //   472	481	938	finally
    //   485	492	575	java/lang/Exception
    //   485	492	938	finally
    //   492	543	575	java/lang/Exception
    //   492	543	938	finally
    //   547	572	575	java/lang/Exception
    //   547	572	938	finally
    //   576	580	938	finally
    //   596	634	291	java/lang/Exception
    //   596	634	872	finally
    //   639	649	291	java/lang/Exception
    //   639	649	872	finally
    //   649	686	291	java/lang/Exception
    //   649	686	872	finally
    //   690	736	291	java/lang/Exception
    //   690	736	872	finally
    //   736	759	291	java/lang/Exception
    //   736	759	872	finally
    //   759	782	291	java/lang/Exception
    //   759	782	872	finally
    //   787	796	291	java/lang/Exception
    //   787	796	872	finally
    //   796	852	291	java/lang/Exception
    //   796	852	872	finally
    //   856	869	291	java/lang/Exception
    //   856	869	872	finally
    //   880	900	291	java/lang/Exception
    //   880	900	872	finally
    //   912	916	924	finally
  }
  
  private InetAddress disallowDeprecatedFormats(String paramString, InetAddress paramInetAddress) {
    boolean bool = paramInetAddress instanceof java.net.Inet4Address;
    int i = paramString.indexOf(':');
    return (!bool || i != -1) ? paramInetAddress : Libcore.os.inet_pton(2, paramString);
  }
  
  private List<String> getNetworkAccessAppWhiteListInternal(ComponentName paramComponentName, String paramString) {
    Slog.d("IflytekService", "getNetworkAccessAppWhiteListInternal: networkAccessListName = " + paramString);
    if (paramString == null)
      return null; 
    String str2 = "";
    String str1 = str2;
    try {
      Slog.d("IflytekService", "getNetworkAccessAppWhiteListInternal: begin read");
      str1 = str2;
      paramString = Settings.Global.getString(this.mContext.getContentResolver(), paramString);
      str1 = paramString;
      Slog.d("IflytekService", "getNetworkAccessAppWhiteListInternal: end read");
      str1 = paramString;
    } catch (Exception exception) {
      exception.printStackTrace();
    } 
    Slog.d("IflytekService", "getNetworkAccessAppWhiteListInternal: networkAccessListStr = " + str1);
    List<String> list = Arrays.asList(str1.split(","));
    Slog.d("IflytekService", "getNetworkAccessAppWhiteListInternal: networkAccessList = " + list);
    return list;
  }
  
  private List<String> getNetworkAccessWhiteListInternal(ComponentName paramComponentName, String paramString) {
    Slog.d("IflytekService", "getNetworkAccessWhiteListInternal: networkAccessListName = " + paramString);
    if (paramString == null)
      return null; 
    String str2 = "";
    String str1 = str2;
    try {
      Slog.d("IflytekService", "getNetworkAccessWhiteListInternal: begin read");
      str1 = str2;
      paramString = Settings.Global.getString(this.mContext.getContentResolver(), paramString);
      str1 = paramString;
      Slog.d("IflytekService", "getNetworkAccessWhiteListInternal: end read");
      str1 = paramString;
    } catch (Exception exception) {
      exception.printStackTrace();
    } 
    Slog.d("IflytekService", "getNetworkAccessWhiteListInternal: networkAccessListStr = " + str1);
    List<String> list = Arrays.asList(str1.split(","));
    Slog.d("IflytekService", "getNetworkAccessWhiteListInternal: networkAccessList = " + list);
    return list;
  }
  
  private int getUidFromPackageName(String paramString) {
    Slog.d("IflytekService", "getUidFromPackageName: packageName = " + paramString);
    try {
      ApplicationInfo applicationInfo = this.mContext.getPackageManager().getApplicationInfo(paramString, 1);
      Slog.d("IflytekService", "getUidFromPackageName: ai.uid = " + applicationInfo.uid);
      return applicationInfo.uid;
    } catch (Exception exception) {
      exception.printStackTrace();
      return -1;
    } 
  }
  
  private boolean hasUserRestriction(ComponentName paramComponentName, String paramString) {
    boolean bool = false;
    UserManager userManager = UserManager.get(this.mContext);
    if (userManager != null)
      bool = userManager.hasUserRestriction(paramString); 
    Slog.d("IflytekService", "has user restriction: " + bool);
    return bool;
  }
  
  private String intToIp(int paramInt) {
    return (paramInt & 0xFF) + "." + (paramInt >> 8 & 0xFF) + "." + (paramInt >> 16 & 0xFF) + "." + (paramInt >> 24 & 0xFF);
  }
  
  private boolean isIpAddress(String paramString) {
    boolean bool;
    Slog.d("IflytekService", "isIpAddress: address = " + paramString);
    InetAddress inetAddress = parseNumericAddressNoThrow(paramString);
    Slog.d("IflytekService", "isIpAddress: inetAddress = " + inetAddress);
    boolean bool1 = inetAddress instanceof java.net.Inet4Address;
    Slog.d("IflytekService", "isIpAddress: resultIpv4 = " + bool1);
    int i = paramString.indexOf(':');
    Slog.d("IflytekService", "isIpAddress: resultIpv6 = " + i);
    if (paramString.contains("/")) {
      bool = true;
    } else {
      bool = false;
    } 
    Slog.d("IflytekService", "isIpAddress: resultIpSector = " + bool);
    if (!bool1 && i == -1 && bool != true) {
      bool1 = false;
    } else {
      bool1 = true;
    } 
    Slog.d("IflytekService", "isIpAddress: isIpAddress = " + bool1);
    return bool1;
  }
  
  private boolean isMediaMounted() throws Exception {
    StorageManager storageManager = (StorageManager)this.mContext.getSystemService("storage");
    VolumeInfo volumeInfo = storageManager.findVolumeById("public:179,65");
    if (volumeInfo == null)
      return false; 
    File file = volumeInfo.getPath();
    return (file == null) ? false : "mounted".equals(storageManager.getVolumeState(file.getAbsolutePath()));
  }
  
  private void mountMedia() throws Exception {
    Slog.i("IflytekService", "mountMedia");
    if (isMediaMounted()) {
      Slog.d("IflytekService", "mounted");
      return;
    } 
    ((StorageManager)this.mContext.getSystemService("storage")).mount("public:179,65");
  }
  
  private InetAddress parseNumericAddressNoThrow(String paramString) {
    String str = paramString;
    if (paramString.startsWith("[")) {
      str = paramString;
      if (paramString.endsWith("]")) {
        str = paramString;
        if (paramString.indexOf(':') != -1)
          str = paramString.substring(1, paramString.length() - 1); 
      } 
    } 
    StructAddrinfo structAddrinfo = new StructAddrinfo();
    structAddrinfo.ai_flags = 4;
    paramString = null;
    try {
      InetAddress[] arrayOfInetAddress = Libcore.os.android_getaddrinfo(str, structAddrinfo, 0);
      null = arrayOfInetAddress;
      if (null != null)
        return null[0]; 
    } catch (GaiException gaiException) {
      gaiException.printStackTrace();
      if (null != null)
        return null[0]; 
    } finally {}
    return null;
  }
  
  private void setIpv4FirewallUidRule(int paramInt1, int paramInt2, int paramInt3) {
    try {
      if (this.mNetworkService != null)
        this.mNetworkService.setFirewallAppUidRule(paramInt1, paramInt2, paramInt3); 
      return;
    } catch (Exception exception) {
      exception.printStackTrace();
      return;
    } 
  }
  
  private void setIpv6FirewallAppEnabled(final boolean enable) {
    if (enable)
      try {
        if (this.mNetworkService.isIpv6FirewallAppEnabled())
          this.mNetworkService.setIpv6FirewallAppEnabled(false); 
        this.mHandler.postDelayed(new Runnable() {
              public void run() {
                try {
                  Slog.d("IflytekService", "setIpv6FirewallAppEnabled: enable = " + enable);
                  IflytekService.-get5(IflytekService.this).setIpv6FirewallAppEnabled(enable);
                  if (enable) {
                    if (IflytekService.-get8(IflytekService.this) >= 0 && IflytekService.-get5(IflytekService.this).isIpv6FirewallAppEnabled())
                      IflytekService.-get5(IflytekService.this).setIpv6FirewallAppUidRule(0, IflytekService.-get8(IflytekService.this), 1); 
                    if (IflytekService.-get7(IflytekService.this) >= 0 && IflytekService.-get5(IflytekService.this).isIpv6FirewallAppEnabled())
                      IflytekService.-get5(IflytekService.this).setIpv6FirewallAppUidRule(0, IflytekService.-get7(IflytekService.this), 1); 
                    String str = Settings.Global.getString(IflytekService.-get1(IflytekService.this).getContentResolver(), "network_access_app_whitelist");
                    Slog.d("IflytekService", "setIpv6FirewallAppEnabled: networkAccessListStr = " + str);
                    List<String> list = Arrays.asList(str.split(","));
                    Slog.d("IflytekService", "setIpv6FirewallAppEnabled: networkAccessList = " + list);
                    if (list != null)
                      for (String str1 : list) {
                        Slog.d("IflytekService", "setIpv6FirewallAppEnabled: networkName = " + str1);
                        int i = IflytekService.-wrap0(IflytekService.this, str1);
                        if (i >= 0 && IflytekService.-get5(IflytekService.this).isIpv6FirewallAppEnabled())
                          IflytekService.-get5(IflytekService.this).setIpv6FirewallAppUidRule(0, i, 1); 
                      }  
                  } 
                } catch (Exception exception) {
                  exception.printStackTrace();
                } 
              }
            }5000L);
        return;
      } catch (Exception exception) {
        exception.printStackTrace();
        return;
      }  
    this.mHandler.postDelayed(new Runnable() {
          public void run() {
            try {
              Slog.d("IflytekService", "setIpv6FirewallAppEnabled: enable = " + enable);
              IflytekService.-get5(IflytekService.this).setIpv6FirewallAppEnabled(enable);
              if (enable) {
                if (IflytekService.-get8(IflytekService.this) >= 0 && IflytekService.-get5(IflytekService.this).isIpv6FirewallAppEnabled())
                  IflytekService.-get5(IflytekService.this).setIpv6FirewallAppUidRule(0, IflytekService.-get8(IflytekService.this), 1); 
                if (IflytekService.-get7(IflytekService.this) >= 0 && IflytekService.-get5(IflytekService.this).isIpv6FirewallAppEnabled())
                  IflytekService.-get5(IflytekService.this).setIpv6FirewallAppUidRule(0, IflytekService.-get7(IflytekService.this), 1); 
                String str = Settings.Global.getString(IflytekService.-get1(IflytekService.this).getContentResolver(), "network_access_app_whitelist");
                Slog.d("IflytekService", "setIpv6FirewallAppEnabled: networkAccessListStr = " + str);
                List<String> list = Arrays.asList(str.split(","));
                Slog.d("IflytekService", "setIpv6FirewallAppEnabled: networkAccessList = " + list);
                if (list != null)
                  for (String str1 : list) {
                    Slog.d("IflytekService", "setIpv6FirewallAppEnabled: networkName = " + str1);
                    int i = IflytekService.-wrap0(IflytekService.this, str1);
                    if (i >= 0 && IflytekService.-get5(IflytekService.this).isIpv6FirewallAppEnabled())
                      IflytekService.-get5(IflytekService.this).setIpv6FirewallAppUidRule(0, i, 1); 
                  }  
              } 
            } catch (Exception exception) {
              exception.printStackTrace();
            } 
          }
        }5000L);
  }
  
  private void setIpv6FirewallUidRule(int paramInt1, int paramInt2, int paramInt3) {
    try {
      if (this.mNetworkService != null)
        this.mNetworkService.setIpv6FirewallAppUidRule(paramInt1, paramInt2, paramInt3); 
      return;
    } catch (Exception exception) {
      exception.printStackTrace();
      return;
    } 
  }
  
  private void setUserRestriction(ComponentName paramComponentName, String paramString, boolean paramBoolean) {
    if (paramComponentName == null)
      throw new IllegalArgumentException("input component name is null"); 
    checkActiveAndUserId(paramComponentName);
    DevicePolicyManager devicePolicyManager = (DevicePolicyManager)this.mContext.getSystemService("device_policy");
    if (devicePolicyManager == null)
      return; 
    ComponentName componentName = paramComponentName;
    if (!devicePolicyManager.isProfileOwnerApp(paramComponentName.getPackageName()))
      componentName = devicePolicyManager.getProfileOwner(); 
    if (paramBoolean) {
      devicePolicyManager.addUserRestriction(componentName, paramString);
      return;
    } 
    devicePolicyManager.clearUserRestriction(componentName, paramString);
  }
  
  private void unmountMedia() throws Exception {
    // Byte code:
    //   0: ldc 'IflytekService'
    //   2: ldc_w 'unmountMedia'
    //   5: invokestatic i : (Ljava/lang/String;Ljava/lang/String;)I
    //   8: pop
    //   9: aload_0
    //   10: invokespecial isMediaMounted : ()Z
    //   13: ifne -> 26
    //   16: ldc 'IflytekService'
    //   18: ldc_w 'not mount'
    //   21: invokestatic d : (Ljava/lang/String;Ljava/lang/String;)I
    //   24: pop
    //   25: return
    //   26: new com/android/server/iflytek/IflytekService$StorageListener
    //   29: dup
    //   30: aload_0
    //   31: invokespecial <init> : (Lcom/android/server/iflytek/IflytekService;)V
    //   34: astore_3
    //   35: aload_0
    //   36: getfield mContext : Landroid/content/Context;
    //   39: ldc_w 'storage'
    //   42: invokevirtual getSystemService : (Ljava/lang/String;)Ljava/lang/Object;
    //   45: checkcast android/os/storage/StorageManager
    //   48: astore #4
    //   50: aload #4
    //   52: aload_3
    //   53: invokevirtual registerListener : (Landroid/os/storage/StorageEventListener;)V
    //   56: aload_3
    //   57: monitorenter
    //   58: aload #4
    //   60: ldc 'public:179,65'
    //   62: invokevirtual unmount : (Ljava/lang/String;)V
    //   65: lconst_0
    //   66: lstore_1
    //   67: aload_3
    //   68: invokevirtual isDone : ()Z
    //   71: ifne -> 98
    //   74: lload_1
    //   75: ldc2_w 120000
    //   78: lcmp
    //   79: ifge -> 98
    //   82: aload_3
    //   83: ldc2_w 20000
    //   86: invokevirtual wait : (J)V
    //   89: lload_1
    //   90: ldc2_w 20000
    //   93: ladd
    //   94: lstore_1
    //   95: goto -> 67
    //   98: aload_3
    //   99: invokevirtual isDone : ()Z
    //   102: ifne -> 114
    //   105: ldc 'IflytekService'
    //   107: ldc_w 'Timed out waiting for packageInstalled callback'
    //   110: invokestatic e : (Ljava/lang/String;Ljava/lang/String;)I
    //   113: pop
    //   114: aload_3
    //   115: monitorexit
    //   116: aload #4
    //   118: aload_3
    //   119: invokevirtual unregisterListener : (Landroid/os/storage/StorageEventListener;)V
    //   122: return
    //   123: astore #5
    //   125: aload_3
    //   126: monitorexit
    //   127: aload #5
    //   129: athrow
    //   130: astore #5
    //   132: aload #4
    //   134: aload_3
    //   135: invokevirtual unregisterListener : (Landroid/os/storage/StorageEventListener;)V
    //   138: aload #5
    //   140: athrow
    // Exception table:
    //   from	to	target	type
    //   56	58	130	finally
    //   58	65	123	finally
    //   67	74	123	finally
    //   82	89	123	finally
    //   98	114	123	finally
    //   114	116	130	finally
    //   125	130	130	finally
  }
  
  public void addInstallPackageWhiteList(ComponentName paramComponentName, List<String> paramList) {
    if (paramList == null || paramList.isEmpty() || paramList.size() > 200)
      throw new IllegalArgumentException("Whitelist is illegal"); 
    if (paramComponentName == null)
      throw new IllegalArgumentException("input component name is null"); 
    checkActiveAndUserId(paramComponentName);
    long l = Binder.clearCallingIdentity();
    try {
      StringBuilder stringBuilder = new StringBuilder();
      stringBuilder.append(";");
      for (int i = 0; i < paramList.size(); i++)
        stringBuilder.append(paramList.get(i)).append(";"); 
      Settings.Global.putString(this.mContext.getContentResolver(), "install_whitelist", stringBuilder.toString());
      return;
    } catch (Exception exception) {
      Slog.e("IflytekService", "Exception:" + exception);
      return;
    } finally {
      Binder.restoreCallingIdentity(l);
    } 
  }
  
  public boolean addNetworkAccessAppWhiteList(ComponentName paramComponentName, List<String> paramList) throws RemoteException {
    Slog.d("IflytekService", "addNetworkAccessAppWhiteList: admin = " + paramComponentName + ", networkNames = " + paramList);
    return addNetworkAccessAppWhiteListInternal(paramComponentName, paramList, "network_access_app_whitelist");
  }
  
  public boolean addNetworkAccessWhiteList(ComponentName paramComponentName, List<String> paramList) throws RemoteException {
    Slog.d("IflytekService", "addNetworkAccessWhiteList: admin = " + paramComponentName + ", networkNames = " + paramList);
    return addNetworkAccessWhiteListInternal(paramComponentName, paramList, "network_access_whitelist");
  }
  
  public void addPersistentApp(ComponentName paramComponentName, List<String> paramList) {
    if (paramComponentName == null)
      throw new IllegalArgumentException("admin is null"); 
    checkActiveAndUserId(paramComponentName);
    if (paramList != null && paramList.size() != 0 && paramList.size() <= 200) {
      StringJoiner stringJoiner = new StringJoiner(",");
      Iterator<String> iterator = paramList.iterator();
      while (iterator.hasNext())
        stringJoiner.add(iterator.next()); 
      null = stringJoiner.toString();
      long l = Binder.clearCallingIdentity();
      try {
        Settings.Global.putString(this.mContext.getContentResolver(), "persistent_app_white_list", null);
        return;
      } finally {
        Binder.restoreCallingIdentity(l);
      } 
    } 
    throw new IllegalArgumentException("packageNames is illegal");
  }
  
  public void cleanNetworkAccessAppWhiteList(ComponentName paramComponentName) throws RemoteException {
    Slog.d("IflytekService", "cleanNetworkAccessAppWhiteList: admin = " + paramComponentName);
    if (paramComponentName == null)
      return; 
    cleanNetworkAccessAppWhiteListInternal(paramComponentName, "network_access_app_whitelist");
  }
  
  public void cleanNetworkAccessWhiteList(ComponentName paramComponentName) throws RemoteException {
    Slog.d("IflytekService", "cleanNetworkAccessWhiteList: admin = " + paramComponentName);
    if (paramComponentName == null)
      return; 
    cleanNetworkAccessWhiteListInternal(paramComponentName, "network_access_whitelist");
  }
  
  public void disableInstallSource(ComponentName paramComponentName, List<String> paramList) {
    if (paramComponentName == null)
      throw new IllegalArgumentException("admin is null"); 
    checkActiveAndUserId(paramComponentName);
    if (paramList != null && paramList.size() != 0 && paramList.size() <= 200) {
      StringJoiner stringJoiner = new StringJoiner(",");
      Iterator<String> iterator = paramList.iterator();
      while (iterator.hasNext())
        stringJoiner.add(iterator.next()); 
      null = stringJoiner.toString();
      long l = Binder.clearCallingIdentity();
      try {
        Settings.Global.putString(this.mContext.getContentResolver(), "install_source_white_list", null);
        Settings.Global.putInt(this.mContext.getContentResolver(), "install_source_disabled", 1);
        return;
      } finally {
        Binder.restoreCallingIdentity(l);
      } 
    } 
    throw new IllegalArgumentException("Whitelist is illegal");
  }
  
  public void enableInstallPackage(ComponentName paramComponentName) {
    if (paramComponentName == null)
      throw new IllegalArgumentException("admin is null"); 
    checkActiveAndUserId(paramComponentName);
    long l = Binder.clearCallingIdentity();
    try {
      Settings.Global.putInt(this.mContext.getContentResolver(), "install_source_disabled", 0);
      return;
    } finally {
      Binder.restoreCallingIdentity(l);
    } 
  }
  
  public List<String> getApplicationProcess(ComponentName paramComponentName) {
    null = new ArrayList();
    ActivityManager activityManager = (ActivityManager)this.mContext.getSystemService("activity");
    long l = Binder.clearCallingIdentity();
    try {
      for (ActivityManager.RunningAppProcessInfo runningAppProcessInfo : activityManager.getRunningAppProcesses()) {
        for (int i = 0; i < runningAppProcessInfo.pkgList.length; i++)
          null.add(runningAppProcessInfo.pkgList[i]); 
      } 
      return null;
    } catch (Exception exception) {
      Slog.e("IflytekService", "getApplicationProcess:" + exception.getMessage());
      return null;
    } finally {
      Binder.restoreCallingIdentity(l);
    } 
  }
  
  public boolean getAutoBrightnessEnabled(ComponentName paramComponentName) {
    try {
      int i = Settings.System.getInt(this.mContext.getContentResolver(), "screen_brightness_mode");
      return (i == 1);
    } catch (Exception exception) {
      Slog.e("IflytekService", "getEyeGuaidedReversalEnabled exception: " + exception.getMessage());
      return false;
    } 
  }
  
  public boolean getAvailableUpdated(ComponentName paramComponentName) throws RemoteException {
    if (paramComponentName == null)
      throw new IllegalArgumentException("input component name is null"); 
    checkActiveAndUserId(paramComponentName);
    null = this.mContext.getContentResolver();
    long l = Binder.clearCallingIdentity();
    try {
      Slog.d("IflytekService", "getAvailableUpdated");
      String str = Settings.System.getString(null, "toycloud_has_update");
      if (!TextUtils.isEmpty(str) && (str.equals("null") ^ true) != 0) {
        boolean bool = str.equals("1");
        if (bool)
          return true; 
        return false;
      } 
      return false;
    } catch (Exception exception) {
      Slog.e("IflytekService", "getAvailableUpdated:", exception);
      return false;
    } finally {
      Binder.restoreCallingIdentity(l);
    } 
  }
  
  public int getBrightnessLevel(ComponentName paramComponentName) {
    try {
      return Settings.System.getInt(this.mContext.getContentResolver(), "screen_brightness");
    } catch (Exception exception) {
      Slog.e("IflytekService", "getBrightnessLevel exception: " + exception.getMessage());
      return 10;
    } 
  }
  
  public boolean getEyeGuaidedBWModeEnabled(ComponentName paramComponentName) {
    try {
      boolean bool;
      if (Settings.Secure.getInt(this.mContext.getContentResolver(), "accessibility_display_daltonizer_enabled") == 0) {
        bool = true;
      } else {
        bool = false;
      } 
      int i = Settings.Secure.getInt(this.mContext.getContentResolver(), "accessibility_display_daltonizer");
      Slog.d("IflytekService", "getEyeGuaidedBWModeEnabled admin:" + paramComponentName + ",disabled:" + bool + ",daltonizer:" + i);
      return bool ? false : ((i == 0));
    } catch (Exception exception) {
      Slog.e("IflytekService", "getEyeGuaidedBWModeEnabled exception: " + exception.getMessage());
      return false;
    } 
  }
  
  public boolean getEyeGuaidedBlueLightFilterEnabled(ComponentName paramComponentName) {
    try {
      if (Settings.Global.getInt(this.mContext.getContentResolver(), "blue_light_filter_enabled", 0) == 1) {
        boolean bool1 = true;
        Slog.d("IflytekService", "getEyeGuaidedBlueLightFilterEnabled amin:" + paramComponentName + ",enabled:" + bool1);
        return bool1;
      } 
      boolean bool = false;
      Slog.d("IflytekService", "getEyeGuaidedBlueLightFilterEnabled amin:" + paramComponentName + ",enabled:" + bool);
      return bool;
    } catch (Exception exception) {
      Slog.e("IflytekService", "getEyeGuaidedBlueLightFilterEnabled exception: " + exception.getMessage());
      return false;
    } 
  }
  
  public int getEyeGuaidedBlueLightFilterLevel(ComponentName paramComponentName) {
    try {
      int i = Settings.Global.getInt(this.mContext.getContentResolver(), "blue_light_filter_value");
      Slog.d("IflytekService", "getEyeGuaidedBlueLightFilterLevel amin:" + paramComponentName + ",level:" + i);
      return i;
    } catch (Exception exception) {
      Slog.e("IflytekService", "getEyeGuaidedBlueLightFilterLevel exception: " + exception.getMessage());
      return 0;
    } 
  }
  
  public boolean getEyeGuaidedLightEnabled(ComponentName paramComponentName) {
    try {
      int i = Settings.Global.getInt(this.mContext.getContentResolver(), "eyeguaided_light_enabled");
      return (i == 1);
    } catch (Exception exception) {
      Slog.e("IflytekService", "getEyeGuaidedLightEnabled exception: " + exception.getMessage());
      return false;
    } 
  }
  
  public boolean getEyeGuaidedProximityEnabled(ComponentName paramComponentName) {
    try {
      int i = Settings.Global.getInt(this.mContext.getContentResolver(), "eyeguaided_proximity_enabled");
      return (i == 1);
    } catch (Exception exception) {
      Slog.e("IflytekService", "getEyeGuaidedProximityEnabled exception: " + exception.getMessage());
      return false;
    } 
  }
  
  public boolean getEyeGuaidedReversalEnabled(ComponentName paramComponentName) {
    try {
      int i = Settings.Global.getInt(this.mContext.getContentResolver(), "eyeguaided_reversal_enabled");
      return (i == 1);
    } catch (Exception exception) {
      Slog.e("IflytekService", "getEyeGuaidedReversalEnabled exception: " + exception.getMessage());
      return false;
    } 
  }
  
  public List<String> getInstallPackageSourceWhiteList(ComponentName paramComponentName) {
    String str = "";
    try {
      String str1 = Settings.Global.getString(this.mContext.getContentResolver(), "install_source_white_list");
      str = str1;
      if (str != null)
        return Arrays.asList(str.split(",")); 
    } catch (Exception exception) {
      Slog.e("IflytekService", "getInstallPackageSourceWhiteList Exception:" + exception);
      if (str != null)
        return Arrays.asList(str.split(",")); 
    } 
    return null;
  }
  
  public List<String> getInstallPackageWhiteList(ComponentName paramComponentName) {
    String str2;
    String str1 = "";
    try {
      String str = Settings.Global.getString(this.mContext.getContentResolver(), "install_whitelist");
      str2 = str;
      if (str != null) {
        str2 = str;
        str1 = str;
        if ((str.isEmpty() ^ true) != 0) {
          str1 = str;
          str2 = str.replaceFirst(";", "");
        } 
      } 
    } catch (Exception exception) {
      Slog.e("IflytekService", "Exception:" + exception);
      str2 = str1;
    } 
    return Arrays.asList(str2.split(";"));
  }
  
  public List<String> getNetworkAccessAppWhiteList(ComponentName paramComponentName) throws RemoteException {
    Slog.d("IflytekService", "getNetworkAccessAppWhiteList: admin = " + paramComponentName);
    return (paramComponentName == null) ? null : getNetworkAccessAppWhiteListInternal(paramComponentName, "network_access_app_whitelist");
  }
  
  public List<String> getNetworkAccessWhiteList(ComponentName paramComponentName) throws RemoteException {
    Slog.d("IflytekService", "getNetworkAccessWhiteList: admin = " + paramComponentName);
    return (paramComponentName == null) ? null : getNetworkAccessWhiteListInternal(paramComponentName, "network_access_whitelist");
  }
  
  public List<String> getPersistentApp(ComponentName paramComponentName) {
    String str = "";
    try {
      String str1 = Settings.Global.getString(this.mContext.getContentResolver(), "persistent_app_white_list");
      str = str1;
      if (str != null)
        return Arrays.asList(str.split(",")); 
    } catch (Exception exception) {
      Slog.e("IflytekService", "getPersistentApp Exception:" + exception);
      if (str != null)
        return Arrays.asList(str.split(",")); 
    } 
    return null;
  }
  
  public int getScreenTimeouted(ComponentName paramComponentName) {
    try {
      return Settings.System.getInt(this.mContext.getContentResolver(), "screen_off_timeout");
    } catch (Exception exception) {
      return 0;
    } 
  }
  
  public List<String> getSuperWhiteListForHwSystemManger(ComponentName paramComponentName) {
    String str = "";
    try {
      String str1 = Settings.Global.getString(this.mContext.getContentResolver(), "super_white_list");
      str = str1;
      if (str != null)
        return Arrays.asList(str.split(",")); 
    } catch (Exception exception) {
      Slog.e("IflytekService", "getSuperWhiteListForHwSystemManger Exception:" + exception);
      if (str != null)
        return Arrays.asList(str.split(",")); 
    } 
    return null;
  }
  
  public void installPackage(ComponentName paramComponentName, String paramString) {
    if (paramComponentName == null || paramString == null)
      throw new IllegalArgumentException("admin or packagePath is null"); 
    checkActiveAndUserId(paramComponentName);
    Uri uri = Uri.fromFile((new File(paramString)).getAbsoluteFile());
    PackageManager packageManager = this.mContext.getPackageManager();
    PackageInstallObserver packageInstallObserver = new PackageInstallObserver();
    long l = Binder.clearCallingIdentity();
    try {
      packageManager.installPackage(uri, packageInstallObserver, 66, paramComponentName.getPackageName());
      return;
    } catch (Exception exception) {
      Slog.e("IflytekService", "installPackage:" + exception.getMessage());
      return;
    } finally {
      Binder.restoreCallingIdentity(l);
    } 
  }
  
  public boolean isAdbDisabled(ComponentName paramComponentName) {
    try {
      int i = Settings.Secure.getInt(this.mContext.getContentResolver(), "adb_enabled");
      return (i == 0);
    } catch (Exception exception) {
      return false;
    } 
  }
  
  public boolean isAdminActive(ComponentName paramComponentName) {
    if (paramComponentName == null)
      throw new IllegalArgumentException("admin is null"); 
    IDevicePolicyManager iDevicePolicyManager = IDevicePolicyManager.Stub.asInterface(ServiceManager.getService("device_policy"));
    if (iDevicePolicyManager == null)
      throw new IllegalArgumentException("Error: Could not access the Device Policy Manager. Is the system running?"); 
    long l = Binder.clearCallingIdentity();
    try {
      return iDevicePolicyManager.isAdminActive(paramComponentName, this.mContext.getUserId());
    } catch (Exception exception) {
      Slog.e("IflytekService", "isAdminActive Exception:" + exception);
      return false;
    } finally {
      Binder.restoreCallingIdentity(l);
    } 
  }
  
  public boolean isBackButtonDisabled(ComponentName paramComponentName) throws RemoteException {
    try {
      int i = Settings.Global.getInt(this.mContext.getContentResolver(), "back_key_disabled");
      return (i == 1);
    } catch (Exception exception) {
      return false;
    } 
  }
  
  public boolean isBluetoothDisabled(ComponentName paramComponentName) {
    boolean bool = hasUserRestriction(paramComponentName, "no_bluetooth");
    Slog.d("IflytekService", "is bluetooth disabled: " + bool);
    return bool;
  }
  
  public boolean isCameraDisabled(ComponentName paramComponentName) {
    try {
      boolean bool = SystemProperties.get("config.disable_cameraservice", "none").equals("true");
      return bool;
    } catch (Exception exception) {
      return false;
    } 
  }
  
  public boolean isDataConnectivityDisabled(ComponentName paramComponentName) {
    boolean bool = hasUserRestriction(paramComponentName, "no_data_roaming");
    Slog.d("IflytekService", "is data connectivity disabled: " + bool);
    return bool;
  }
  
  public boolean isEnterAppDetailDisabled(ComponentName paramComponentName) throws RemoteException {
    try {
      int i = Settings.Global.getInt(this.mContext.getContentResolver(), "enter_app_detail_disabled");
      return (i == 1);
    } catch (android.provider.Settings.SettingNotFoundException settingNotFoundException) {
      settingNotFoundException.printStackTrace();
      return false;
    } 
  }
  
  public boolean isExternalStorageDisabled(ComponentName paramComponentName) {
    boolean bool = hasUserRestriction(paramComponentName, "no_physical_media");
    Slog.d("IflytekService", "is external storage disabled: " + bool);
    return bool;
  }
  
  public boolean isEyeGuaided(ComponentName paramComponentName) {
    try {
      int i = Settings.System.getInt(this.mContext.getContentResolver(), "toycloud_eyeguaid");
      return (i == 1);
    } catch (Exception exception) {
      return false;
    } 
  }
  
  public boolean isGPSTurnOn(ComponentName paramComponentName) {
    if (Settings.Secure.getInt(this.mContext.getContentResolver(), "location_mode", 0) != 0) {
      boolean bool1 = true;
      Slog.d("IflytekService", "is gps turn on: " + bool1);
      return bool1;
    } 
    boolean bool = false;
    Slog.d("IflytekService", "is gps turn on: " + bool);
    return bool;
  }
  
  public boolean isHomeButtonDisabled(ComponentName paramComponentName) throws RemoteException {
    try {
      int i = Settings.Global.getInt(this.mContext.getContentResolver(), "home_key_disabled");
      return (i == 1);
    } catch (Exception exception) {
      return false;
    } 
  }
  
  public boolean isInstallSourceDisabled(ComponentName paramComponentName) {
    try {
      int i = Settings.Global.getInt(this.mContext.getContentResolver(), "install_source_disabled");
      return (i == 1);
    } catch (android.provider.Settings.SettingNotFoundException settingNotFoundException) {
      settingNotFoundException.printStackTrace();
      return false;
    } 
  }
  
  public boolean isMmsReceiveDisabled(ComponentName paramComponentName) {
    return false;
  }
  
  public boolean isMmsSendDisabled(ComponentName paramComponentName) {
    boolean bool = hasUserRestriction(paramComponentName, "no_sms");
    Slog.d("IflytekService", "is mms send disabled: " + bool);
    return bool;
  }
  
  public boolean isNFCDisabled(ComponentName paramComponentName) {
    boolean bool = hasUserRestriction(paramComponentName, "no_outgoing_beam");
    Slog.d("IflytekService", "is nfc disabled: " + bool);
    return bool;
  }
  
  public boolean isPowerDisabled(ComponentName paramComponentName) {
    try {
      int i = Settings.Global.getInt(this.mContext.getContentResolver(), "long_power_key_disabled");
      return (i == 1);
    } catch (Exception exception) {
      return false;
    } 
  }
  
  public boolean isSendNotificationDisabled(ComponentName paramComponentName) throws RemoteException {
    try {
      int i = Settings.Global.getInt(this.mContext.getContentResolver(), "send_notification_disabled");
      return (i == 1);
    } catch (android.provider.Settings.SettingNotFoundException settingNotFoundException) {
      settingNotFoundException.printStackTrace();
      return false;
    } 
  }
  
  public boolean isSleepDeviceDisabled(ComponentName paramComponentName) {
    try {
      int i = Settings.Global.getInt(this.mContext.getContentResolver(), "short_power_key_disabled");
      return (i == 1);
    } catch (Exception exception) {
      return false;
    } 
  }
  
  public boolean isStatusBarExpandPanelDisabled(ComponentName paramComponentName) throws RemoteException {
    try {
      int i = Settings.Global.getInt(this.mContext.getContentResolver(), "status_bar_expand_panel_disabled");
      return (i == 1);
    } catch (android.provider.Settings.SettingNotFoundException settingNotFoundException) {
      settingNotFoundException.printStackTrace();
      return false;
    } 
  }
  
  public boolean isSystemApplicationFreezed(ComponentName paramComponentName, String paramString) throws RemoteException {
    int i = ((PackageManagerService)ServiceManager.getService("package")).getApplicationEnabledSetting(paramString, this.mContext.getUserId());
    return !(i != 3 && i != 2 && i != 4);
  }
  
  public boolean isTaskButtonDisabled(ComponentName paramComponentName) throws RemoteException {
    try {
      int i = Settings.Global.getInt(this.mContext.getContentResolver(), "task_key_disabled");
      return (i == 1);
    } catch (Exception exception) {
      Slog.e("IflytekService", "Exception:" + exception);
      return false;
    } 
  }
  
  public boolean isUSBDataDisabled(ComponentName paramComponentName) {
    long l = Binder.clearCallingIdentity();
    null = (UsbManager)this.mContext.getSystemService(UsbManager.class);
    try {
      boolean bool = null.isFunctionEnabled("iflyMtp");
      Slog.d("IflytekService", "isUSBDataDisabled:" + (null.isFunctionEnabled("iflyMtp") ^ true));
      return bool ^ true;
    } catch (Exception exception) {
      return false;
    } finally {
      Binder.restoreCallingIdentity(l);
    } 
  }
  
  public boolean isUSBOtgDisabled(ComponentName paramComponentName) {
    return false;
  }
  
  public boolean isVoiceImcomingDisabled(ComponentName paramComponentName) {
    return false;
  }
  
  public boolean isVoiceOutgoingDisabled(ComponentName paramComponentName) {
    boolean bool = hasUserRestriction(paramComponentName, "no_outgoing_calls");
    Slog.d("IflytekService", "is voice outgoing disabled: " + bool);
    return bool;
  }
  
  public boolean isVolumeAdjustDisabled(ComponentName paramComponentName) {
    try {
      int i = Settings.Global.getInt(this.mContext.getContentResolver(), "volume_key_disabled");
      return (i == 1);
    } catch (Exception exception) {
      return false;
    } 
  }
  
  public boolean isWIFIeditDisabled(ComponentName paramComponentName) {
    boolean bool = hasUserRestriction(paramComponentName, "no_config_wifi");
    Slog.d("IflytekService", "is wifi edit disabled: " + bool);
    return bool;
  }
  
  public boolean isWifiAdvancedOptionsDisabled(ComponentName paramComponentName) {
    try {
      int i = Settings.Global.getInt(this.mContext.getContentResolver(), "wifi_advanced_options_disabled");
      return (i == 1);
    } catch (android.provider.Settings.SettingNotFoundException settingNotFoundException) {
      settingNotFoundException.printStackTrace();
      return false;
    } 
  }
  
  public void killApplicationProcess(ComponentName paramComponentName, String paramString) {
    if (paramComponentName == null)
      throw new IllegalArgumentException("admin is null"); 
    checkActiveAndUserId(paramComponentName);
    null = (ActivityManager)this.mContext.getSystemService("activity");
    long l = Binder.clearCallingIdentity();
    try {
      List list = null.getRecentTasks(ActivityManager.getMaxAppRecentsLimitStatic(), 2);
      if (list != null && list.size() > 0)
        for (ActivityManager.RecentTaskInfo recentTaskInfo : list) {
          if (recentTaskInfo != null && recentTaskInfo.baseIntent != null && recentTaskInfo.baseIntent.getComponent() != null && recentTaskInfo.baseIntent.getComponent().getPackageName() != null && (recentTaskInfo.baseIntent.getComponent().getPackageName().equals(paramString) ^ true) == 0) {
            null.removeTask(recentTaskInfo.persistentId);
            break;
          } 
        }  
      null.forceStopPackage(paramString);
      return;
    } catch (Exception exception) {
      Slog.e("IflytekService", "killApplicationProcess:" + exception.getMessage());
      return;
    } finally {
      Binder.restoreCallingIdentity(l);
    } 
  }
  
  public void rebootDevice(ComponentName paramComponentName) {
    if (paramComponentName == null)
      throw new IllegalArgumentException("admin is null"); 
    checkActiveAndUserId(paramComponentName);
    long l = Binder.clearCallingIdentity();
    try {
      Intent intent = new Intent("android.intent.action.REBOOT");
      intent.putExtra("nowait", 1);
      intent.putExtra("interval", 1);
      intent.putExtra("window", 0);
      this.mContext.sendBroadcast(intent);
      return;
    } catch (Exception exception) {
      Slog.e("IflytekService", "rebootDevice:" + exception.getMessage());
      return;
    } finally {
      Binder.restoreCallingIdentity(l);
    } 
  }
  
  public void removeInstallPackageWhiteList(ComponentName paramComponentName, List<String> paramList) {
    if (paramList == null || paramList.isEmpty() || paramList.size() > 200)
      throw new IllegalArgumentException("Whitelist is illegal"); 
    if (paramComponentName == null)
      throw new IllegalArgumentException("input component name is null"); 
    checkActiveAndUserId(paramComponentName);
    long l = Binder.clearCallingIdentity();
    try {
      String str = Settings.Global.getString(this.mContext.getContentResolver(), "install_whitelist");
      int i = 0;
      while (true) {
        String str1;
        if (i < paramList.size()) {
          str1 = str;
          if (str != null) {
            str1 = str;
            if (str.contains(";" + (String)paramList.get(i) + ";"))
              str1 = str.replaceAll((String)paramList.get(i) + ";", ""); 
          } 
        } else {
          Settings.Global.putString(this.mContext.getContentResolver(), "install_whitelist", str);
          return;
        } 
        i++;
        str = str1;
      } 
    } catch (Exception exception) {
      Slog.e("IflytekService", "Exception:" + exception);
      return;
    } finally {
      Binder.restoreCallingIdentity(l);
    } 
  }
  
  public void removePersistentApp(ComponentName paramComponentName, List<String> paramList) {
    if (paramComponentName == null)
      throw new IllegalArgumentException("admin is null"); 
    checkActiveAndUserId(paramComponentName);
    if (paramList == null || paramList.size() == 0)
      throw new IllegalArgumentException("PackageNames is illegal"); 
    ArrayList<String> arrayList = new ArrayList<String>(getPersistentApp(paramComponentName));
    for (String str : paramList) {
      if (arrayList != null && arrayList.contains(str))
        arrayList.remove(str); 
    } 
    StringJoiner stringJoiner = new StringJoiner(",");
    Iterator<String> iterator = arrayList.iterator();
    while (iterator.hasNext())
      stringJoiner.add(iterator.next()); 
    null = stringJoiner.toString();
    long l = Binder.clearCallingIdentity();
    try {
      Settings.Global.putString(this.mContext.getContentResolver(), "persistent_app_white_list", null);
      return;
    } finally {
      Binder.restoreCallingIdentity(l);
    } 
  }
  
  public void removeSuperWhiteListForHwSystemManger(ComponentName paramComponentName, List<String> paramList) {
    StringJoiner stringJoiner;
    if (paramComponentName == null)
      throw new IllegalArgumentException("admin is null"); 
    checkActiveAndUserId(paramComponentName);
    if (paramList == null || paramList.size() == 0)
      throw new IllegalArgumentException("PackageNames is illegal"); 
    null = new ArrayList<String>(getSuperWhiteListForHwSystemManger(paramComponentName));
    PackageManager packageManager = this.mContext.getPackageManager();
    long l = Binder.clearCallingIdentity();
    try {
      label37: for (String str1 : paramList) {
        if (null != null && null.contains(str1)) {
          null.remove(str1);
          String[] arrayOfString = (packageManager.getPackageInfo(str1, 4096)).requestedPermissions;
          for (int i = 0;; i++) {
            int j = arrayOfString.length;
            if (i < j) {
              try {
                packageManager.revokeRuntimePermission(str1, arrayOfString[i], Binder.getCallingUserHandle());
              } catch (Exception exception) {}
            } else {
              continue label37;
            } 
          } 
        } 
      } 
      stringJoiner = new StringJoiner(",");
      Iterator<String> iterator = null.iterator();
    } catch (Exception exception) {
      Slog.e("IflytekService", "removeSuperWhiteListForHwSystemManger:" + exception);
      return;
    } finally {
      Binder.restoreCallingIdentity(l);
    } 
    String str = stringJoiner.toString();
    Settings.Global.putString(this.mContext.getContentResolver(), "super_white_list", str);
    Binder.restoreCallingIdentity(l);
  }
  
  public void setAdbDisabled(ComponentName paramComponentName, boolean paramBoolean) throws RemoteException {
    if (paramComponentName == null)
      throw new IllegalArgumentException("input component name is null"); 
    checkActiveAndUserId(paramComponentName);
    ContentResolver contentResolver = this.mContext.getContentResolver();
    long l = Binder.clearCallingIdentity();
    try {
      Slog.d("IflytekService", "setAdbDisabled:" + String.valueOf(paramBoolean));
      if (paramBoolean) {
        boolean bool;
        if (Settings.Secure.getInt(contentResolver, "adb_enabled", 0) > 0) {
          bool = true;
        } else {
          bool = false;
        } 
        if (bool) {
          paramBoolean = isUSBDataDisabled(paramComponentName);
          Settings.Secure.putInt(contentResolver, "adb_enabled", 0);
          if ((paramBoolean ^ true) != 0)
            ((UsbManager)this.mContext.getSystemService(UsbManager.class)).setCurrentFunction("mtp", true); 
        } 
      } else {
        boolean bool;
        if (Settings.Secure.getInt(contentResolver, "adb_enabled", 0) > 0) {
          bool = true;
        } else {
          bool = false;
        } 
        if (!bool)
          Settings.Secure.putInt(contentResolver, "adb_enabled", 1); 
      } 
      return;
    } catch (Exception exception) {
      Slog.e("IflytekService", "setAdbDisabled:", exception);
      return;
    } finally {
      Binder.restoreCallingIdentity(l);
    } 
  }
  
  public void setAutoBrightnessEnabled(ComponentName paramComponentName, boolean paramBoolean) {
    boolean bool;
    long l = Binder.clearCallingIdentity();
    if (paramComponentName == null)
      throw new IllegalArgumentException("input component name is null"); 
    checkActiveAndUserId(paramComponentName);
    if (paramBoolean) {
      bool = true;
    } else {
      bool = false;
    } 
    try {
      Settings.System.putInt(this.mContext.getContentResolver(), "screen_brightness_mode", bool);
      return;
    } catch (Exception exception) {
      Slog.e("IflytekService", "setAutoBrightnessEnable exception: " + exception.getMessage());
      return;
    } finally {
      Binder.restoreCallingIdentity(l);
    } 
  }
  
  public void setBackButtonDisabled(ComponentName paramComponentName, boolean paramBoolean) throws RemoteException {
    long l = Binder.clearCallingIdentity();
    if (paramComponentName == null)
      throw new IllegalArgumentException("input component name is null"); 
    checkActiveAndUserId(paramComponentName);
    try {
      boolean bool;
      ContentResolver contentResolver = this.mContext.getContentResolver();
      if (paramBoolean) {
        bool = true;
      } else {
        bool = false;
      } 
      Settings.Global.putInt(contentResolver, "back_key_disabled", bool);
      return;
    } catch (Exception exception) {
      Slog.e("IflytekService", "Exception:" + exception);
      return;
    } finally {
      Binder.restoreCallingIdentity(l);
    } 
  }
  
  public void setBluetoothDisabled(ComponentName paramComponentName, boolean paramBoolean) {
    Slog.d("IflytekService", "set bluetooth disabled: " + paramBoolean);
    setUserRestriction(paramComponentName, "no_bluetooth", paramBoolean);
    if (!paramBoolean)
      setUserRestriction(paramComponentName, "no_bluetooth_sharing", paramBoolean); 
  }
  
  public void setBrightnessLevel(ComponentName paramComponentName, int paramInt) {
    long l = Binder.clearCallingIdentity();
    if (paramInt < 0 || paramInt > 255)
      throw new IllegalArgumentException("the level you set is outside the range (0, 255)!"); 
    if (paramComponentName == null)
      throw new IllegalArgumentException("input component name is null"); 
    checkActiveAndUserId(paramComponentName);
    try {
      this.mPowerManager.setTemporaryScreenBrightnessSettingOverride(paramInt);
      Settings.System.putInt(this.mContext.getContentResolver(), "screen_brightness", paramInt);
      return;
    } catch (Exception exception) {
      Slog.e("IflytekService", "setBrightnessLevel exception: " + exception.getMessage());
      return;
    } finally {
      Binder.restoreCallingIdentity(l);
    } 
  }
  
  public void setCameraDisabled(ComponentName paramComponentName, boolean paramBoolean) throws RemoteException {
    if (paramComponentName == null)
      throw new IllegalArgumentException("input component name is null"); 
    checkActiveAndUserId(paramComponentName);
    try {
      Slog.d("IflytekService", "setCameraDisabled:" + String.valueOf(paramBoolean));
      if (paramBoolean) {
        SystemProperties.set("config.disable_cameraservice", "true");
        return;
      } 
      SystemProperties.set("config.disable_cameraservice", "false");
      return;
    } catch (Exception exception) {
      Slog.e("IflytekService", "setCameraDisabled:", exception);
      return;
    } 
  }
  
  public void setDataConnectivityDisabled(ComponentName paramComponentName, boolean paramBoolean) {
    Slog.d("IflytekService", "set data connectivity disabled: " + paramBoolean);
    setUserRestriction(paramComponentName, "no_data_roaming", paramBoolean);
  }
  
  public boolean setEnterAppDetailDisabled(ComponentName paramComponentName, boolean paramBoolean) throws RemoteException {
    if (paramComponentName == null)
      throw new IllegalArgumentException("admin is null"); 
    checkActiveAndUserId(paramComponentName);
    long l = Binder.clearCallingIdentity();
    try {
      ContentResolver contentResolver = this.mContext.getContentResolver();
      if (paramBoolean) {
        boolean bool1 = true;
        paramBoolean = Settings.Global.putInt(contentResolver, "enter_app_detail_disabled", bool1);
        return paramBoolean;
      } 
      boolean bool = false;
      paramBoolean = Settings.Global.putInt(contentResolver, "enter_app_detail_disabled", bool);
      return paramBoolean;
    } finally {
      Binder.restoreCallingIdentity(l);
    } 
  }
  
  public void setExternalStorageDisabled(ComponentName paramComponentName, boolean paramBoolean) {
    Slog.d("IflytekService", "set external storage disabled: " + paramBoolean);
    setUserRestriction(paramComponentName, "no_physical_media", false);
    long l = Binder.clearCallingIdentity();
    if (paramBoolean)
      try {
        unmountMedia();
        return;
      } catch (Exception exception) {
        Slog.e("IflytekService", "Exception:" + exception);
        return;
      } finally {
        Binder.restoreCallingIdentity(l);
        setUserRestriction(paramComponentName, "no_physical_media", paramBoolean);
      }  
    mountMedia();
    Binder.restoreCallingIdentity(l);
    setUserRestriction(paramComponentName, "no_physical_media", paramBoolean);
  }
  
  public void setEyeGuaided(ComponentName paramComponentName, boolean paramBoolean) throws RemoteException {
    if (paramComponentName == null)
      throw new IllegalArgumentException("input component name is null"); 
    checkActiveAndUserId(paramComponentName);
    null = this.mContext.getContentResolver();
    long l = Binder.clearCallingIdentity();
    try {
      Slog.d("IflytekService", "setEyeGuaided:" + String.valueOf(paramBoolean));
      String str = Settings.System.getString(null, "toycloud_eyeguaid");
      if (!TextUtils.isEmpty(str) && (str.equals("null") ^ true) != 0)
        if (paramBoolean) {
          Settings.System.putString(null, "toycloud_eyeguaid", "1");
        } else {
          Settings.System.putString(null, "toycloud_eyeguaid", "0");
        }  
      return;
    } catch (Exception exception) {
      Slog.e("IflytekService", "setEyeGuaided:", exception);
      return;
    } finally {
      Binder.restoreCallingIdentity(l);
    } 
  }
  
  public void setEyeGuaidedBWModeEnabled(ComponentName paramComponentName, boolean paramBoolean) {
    long l = Binder.clearCallingIdentity();
    Slog.d("IflytekService", "setEyeGuaidedBWModeEnabled admin:" + paramComponentName + ",enabled:" + paramBoolean);
    if (paramComponentName == null)
      throw new IllegalArgumentException("input component name is null"); 
    checkActiveAndUserId(paramComponentName);
    if (paramBoolean)
      try {
        Settings.Secure.putInt(this.mContext.getContentResolver(), "accessibility_display_daltonizer_enabled", 1);
        Settings.Secure.putInt(this.mContext.getContentResolver(), "accessibility_display_daltonizer", 0);
        return;
      } catch (Exception exception) {
        Slog.e("IflytekService", "setEyeGuaidedBWModeEnabled exception: " + exception.getMessage());
        return;
      } finally {
        Binder.restoreCallingIdentity(l);
      }  
    Settings.Secure.putInt(this.mContext.getContentResolver(), "accessibility_display_daltonizer_enabled", 0);
    Binder.restoreCallingIdentity(l);
  }
  
  public void setEyeGuaidedBlueLightFilterEnabled(ComponentName paramComponentName, boolean paramBoolean) {
    Binder.clearCallingIdentity();
    Slog.d("IflytekService", "setEyeGuaidedBlueLightFilterEnabled amin:" + paramComponentName + ",enabled:" + paramBoolean);
    if (paramComponentName == null)
      throw new IllegalArgumentException("input component name is null"); 
    checkActiveAndUserId(paramComponentName);
    try {
      if (this.mColorManager != null) {
        int i;
        if (Settings.Global.getInt(this.mContext.getContentResolver(), "blue_light_filter_enabled", 0) == 1) {
          i = 1;
        } else {
          i = 0;
        } 
        if (paramBoolean) {
          final int value = Settings.Global.getInt(this.mContext.getContentResolver(), "blue_light_filter_value", -30);
          i = -1;
          try {
            int k = this.mColorManager.setColorBalance(j);
            i = k;
            if (i == 0) {
              Settings.Global.putInt(this.mContext.getContentResolver(), "blue_light_filter_enabled", 1);
              return;
            } 
          } catch (Exception exception) {
            Slog.e("IflytekService", "setEyeGuaidedBlueLightFilterEnabled exception setColorBalance: " + exception.getMessage());
            if (i == 0) {
              Settings.Global.putInt(this.mContext.getContentResolver(), "blue_light_filter_enabled", 1);
              return;
            } 
          } 
          ColorManager.ColorManagerListener colorManagerListener = new ColorManager.ColorManagerListener() {
              public void onConnected() {
                Slog.d("IflytekService", "ColorManager onConnected");
                IflytekService.-set0(IflytekService.this, ColorManager.getInstance((Application)IflytekService.-get1(IflytekService.this).getApplicationContext(), IflytekService.-get1(IflytekService.this), ColorManager.DCM_DISPLAY_TYPE.DISP_PRIMARY));
                if (IflytekService.-get0(IflytekService.this).setColorBalance(value) == 0)
                  Settings.Global.putInt(IflytekService.-get1(IflytekService.this).getContentResolver(), "blue_light_filter_enabled", 1); 
              }
            };
          if (ColorManager.connect(this.mContext, colorManagerListener) != 0) {
            Slog.e("IflytekService", "Connection failed");
            return;
          } 
        } else if (i != 0) {
          if (this.mColorManager.setColorBalance(0) == 0) {
            Settings.Global.putInt(this.mContext.getContentResolver(), "blue_light_filter_enabled", 0);
            return;
          } 
          ColorManager.ColorManagerListener colorManagerListener = new ColorManager.ColorManagerListener() {
              public void onConnected() {
                Slog.d("IflytekService", "ColorManager onConnected");
                IflytekService.-set0(IflytekService.this, ColorManager.getInstance((Application)IflytekService.-get1(IflytekService.this).getApplicationContext(), IflytekService.-get1(IflytekService.this), ColorManager.DCM_DISPLAY_TYPE.DISP_PRIMARY));
                if (IflytekService.-get0(IflytekService.this).setColorBalance(0) == 0)
                  Settings.Global.putInt(IflytekService.-get1(IflytekService.this).getContentResolver(), "blue_light_filter_enabled", 0); 
              }
            };
          if (ColorManager.connect(this.mContext, colorManagerListener) != 0)
            Slog.e("IflytekService", "Connection failed"); 
        } 
      } 
    } catch (Exception exception) {
      Slog.e("IflytekService", "setEyeGuaidedBlueLightFilterEnabled exception: " + exception.getMessage(), exception.fillInStackTrace());
      return;
    } 
  }
  
  public void setEyeGuaidedBlueLightFilterLevel(ComponentName paramComponentName, int paramInt) {
    // Byte code:
    //   0: invokestatic clearCallingIdentity : ()J
    //   3: lstore #5
    //   5: ldc 'IflytekService'
    //   7: new java/lang/StringBuilder
    //   10: dup
    //   11: invokespecial <init> : ()V
    //   14: ldc_w 'setEyeGuaidedBlueLightFilterLevel amin:'
    //   17: invokevirtual append : (Ljava/lang/String;)Ljava/lang/StringBuilder;
    //   20: aload_1
    //   21: invokevirtual append : (Ljava/lang/Object;)Ljava/lang/StringBuilder;
    //   24: ldc_w ',level:'
    //   27: invokevirtual append : (Ljava/lang/String;)Ljava/lang/StringBuilder;
    //   30: iload_2
    //   31: invokevirtual append : (I)Ljava/lang/StringBuilder;
    //   34: invokevirtual toString : ()Ljava/lang/String;
    //   37: invokestatic d : (Ljava/lang/String;Ljava/lang/String;)I
    //   40: pop
    //   41: aload_1
    //   42: ifnonnull -> 56
    //   45: new java/lang/IllegalArgumentException
    //   48: dup
    //   49: ldc_w 'input component name is null'
    //   52: invokespecial <init> : (Ljava/lang/String;)V
    //   55: athrow
    //   56: aload_0
    //   57: aload_1
    //   58: invokespecial checkActiveAndUserId : (Landroid/content/ComponentName;)V
    //   61: aload_0
    //   62: getfield mColorManager : Lcom/qti/snapdragon/sdk/display/ColorManager;
    //   65: astore_1
    //   66: aload_1
    //   67: ifnull -> 116
    //   70: iload_2
    //   71: bipush #-100
    //   73: if_icmplt -> 232
    //   76: iload_2
    //   77: bipush #100
    //   79: if_icmpgt -> 232
    //   82: iconst_m1
    //   83: istore_3
    //   84: aload_0
    //   85: getfield mColorManager : Lcom/qti/snapdragon/sdk/display/ColorManager;
    //   88: iload_2
    //   89: invokevirtual setColorBalance : (I)I
    //   92: istore #4
    //   94: iload #4
    //   96: istore_3
    //   97: iload_3
    //   98: ifne -> 191
    //   101: aload_0
    //   102: getfield mContext : Landroid/content/Context;
    //   105: invokevirtual getContentResolver : ()Landroid/content/ContentResolver;
    //   108: ldc_w 'blue_light_filter_value'
    //   111: iload_2
    //   112: invokestatic putInt : (Landroid/content/ContentResolver;Ljava/lang/String;I)Z
    //   115: pop
    //   116: lload #5
    //   118: invokestatic restoreCallingIdentity : (J)V
    //   121: return
    //   122: astore_1
    //   123: ldc 'IflytekService'
    //   125: new java/lang/StringBuilder
    //   128: dup
    //   129: invokespecial <init> : ()V
    //   132: ldc_w 'setEyeGuaidedBlueLightFilterDisabled exception setColorBalance: '
    //   135: invokevirtual append : (Ljava/lang/String;)Ljava/lang/StringBuilder;
    //   138: aload_1
    //   139: invokevirtual getMessage : ()Ljava/lang/String;
    //   142: invokevirtual append : (Ljava/lang/String;)Ljava/lang/StringBuilder;
    //   145: invokevirtual toString : ()Ljava/lang/String;
    //   148: invokestatic e : (Ljava/lang/String;Ljava/lang/String;)I
    //   151: pop
    //   152: goto -> 97
    //   155: astore_1
    //   156: ldc 'IflytekService'
    //   158: new java/lang/StringBuilder
    //   161: dup
    //   162: invokespecial <init> : ()V
    //   165: ldc_w 'setEyeGuaidedBlueLightFilterDisabled exception: '
    //   168: invokevirtual append : (Ljava/lang/String;)Ljava/lang/StringBuilder;
    //   171: aload_1
    //   172: invokevirtual getMessage : ()Ljava/lang/String;
    //   175: invokevirtual append : (Ljava/lang/String;)Ljava/lang/StringBuilder;
    //   178: invokevirtual toString : ()Ljava/lang/String;
    //   181: invokestatic e : (Ljava/lang/String;Ljava/lang/String;)I
    //   184: pop
    //   185: lload #5
    //   187: invokestatic restoreCallingIdentity : (J)V
    //   190: return
    //   191: new com/android/server/iflytek/IflytekService$9
    //   194: dup
    //   195: aload_0
    //   196: iload_2
    //   197: invokespecial <init> : (Lcom/android/server/iflytek/IflytekService;I)V
    //   200: astore_1
    //   201: aload_0
    //   202: getfield mContext : Landroid/content/Context;
    //   205: aload_1
    //   206: invokestatic connect : (Landroid/content/Context;Lcom/qti/snapdragon/sdk/display/ColorManager$ColorManagerListener;)I
    //   209: ifeq -> 116
    //   212: ldc 'IflytekService'
    //   214: ldc_w 'Connection failed'
    //   217: invokestatic e : (Ljava/lang/String;Ljava/lang/String;)I
    //   220: pop
    //   221: goto -> 116
    //   224: astore_1
    //   225: lload #5
    //   227: invokestatic restoreCallingIdentity : (J)V
    //   230: aload_1
    //   231: athrow
    //   232: new java/lang/IllegalArgumentException
    //   235: dup
    //   236: ldc_w 'the level you set is outside the range (-100, 100)!'
    //   239: invokespecial <init> : (Ljava/lang/String;)V
    //   242: athrow
    // Exception table:
    //   from	to	target	type
    //   61	66	155	java/lang/Exception
    //   61	66	224	finally
    //   84	94	122	java/lang/Exception
    //   84	94	224	finally
    //   101	116	155	java/lang/Exception
    //   101	116	224	finally
    //   123	152	155	java/lang/Exception
    //   123	152	224	finally
    //   156	185	224	finally
    //   191	221	155	java/lang/Exception
    //   191	221	224	finally
    //   232	243	155	java/lang/Exception
    //   232	243	224	finally
  }
  
  public void setEyeGuaidedLightEnabled(ComponentName paramComponentName, boolean paramBoolean) {
    boolean bool = true;
    long l = Binder.clearCallingIdentity();
    if (paramComponentName == null)
      throw new IllegalArgumentException("input component name is null"); 
    checkActiveAndUserId(paramComponentName);
    try {
      this.mIsLightInHealthyRange = true;
      ContentResolver contentResolver = this.mContext.getContentResolver();
      if (!paramBoolean)
        bool = false; 
      Settings.Global.putInt(contentResolver, "eyeguaided_light_enabled", bool);
      if (paramBoolean) {
        this.mSensorManager.registerListener(this.mLSensorEventListener, this.mSensorManager.getDefaultSensor(5), 3);
      } else {
        this.mSensorManager.unregisterListener(this.mLSensorEventListener);
      } 
      return;
    } catch (Exception exception) {
      Slog.e("IflytekService", "setEyeGuaidedLightEnabled exception: " + exception.getMessage());
      return;
    } finally {
      Binder.restoreCallingIdentity(l);
    } 
  }
  
  public void setEyeGuaidedProximityEnabled(ComponentName paramComponentName, boolean paramBoolean) {
    boolean bool = false;
    long l = Binder.clearCallingIdentity();
    if (paramComponentName == null)
      throw new IllegalArgumentException("input component name is null"); 
    checkActiveAndUserId(paramComponentName);
    try {
      this.mIsEyesNear = false;
      ContentResolver contentResolver = this.mContext.getContentResolver();
      if (paramBoolean)
        bool = true; 
      Settings.Global.putInt(contentResolver, "eyeguaided_proximity_enabled", bool);
      if (paramBoolean) {
        this.mSensorManager.registerListener(this.mPSensorEventListener, this.mSensorManager.getDefaultSensor(8), 3);
      } else {
        this.mSensorManager.unregisterListener(this.mPSensorEventListener);
      } 
      return;
    } catch (Exception exception) {
      Slog.e("IflytekService", "setEyeGuaidedProximityEnabled exception: " + exception.getMessage());
      return;
    } finally {
      Binder.restoreCallingIdentity(l);
    } 
  }
  
  public void setEyeGuaidedReversalEnabled(ComponentName paramComponentName, boolean paramBoolean) {
    boolean bool = true;
    long l = Binder.clearCallingIdentity();
    if (paramComponentName == null)
      throw new IllegalArgumentException("input component name is null"); 
    checkActiveAndUserId(paramComponentName);
    try {
      this.mIsFliped = false;
      ContentResolver contentResolver = this.mContext.getContentResolver();
      if (!paramBoolean)
        bool = false; 
      Settings.Global.putInt(contentResolver, "eyeguaided_reversal_enabled", bool);
      if (paramBoolean) {
        this.mSensorManager.registerListener(this.mASensorEventListener, this.mSensorManager.getDefaultSensor(1), 3);
      } else {
        this.mSensorManager.unregisterListener(this.mASensorEventListener);
      } 
      return;
    } catch (Exception exception) {
      Slog.e("IflytekService", "setEyeGuaidedReversalEnabled exception: " + exception.getMessage());
      return;
    } finally {
      Binder.restoreCallingIdentity(l);
    } 
  }
  
  public void setFactoryReseted(ComponentName paramComponentName) throws RemoteException {
    if (paramComponentName == null)
      throw new IllegalArgumentException("input component name is null"); 
    checkActiveAndUserId(paramComponentName);
    long l = Binder.clearCallingIdentity();
    try {
      Slog.d("IflytekService", "setFactoryReseted");
      Intent intent = new Intent("android.intent.action.MASTER_CLEAR");
      intent.setPackage("android");
      intent.addFlags(268435456);
      intent.putExtra("android.intent.extra.REASON", "MasterClearConfirm");
      this.mContext.sendBroadcast(intent);
      return;
    } catch (Exception exception) {
      Slog.e("IflytekService", "setFactoryReseted:", exception);
      return;
    } finally {
      Binder.restoreCallingIdentity(l);
    } 
  }
  
  public void setHomeButtonDisabled(ComponentName paramComponentName, boolean paramBoolean) throws RemoteException {
    long l = Binder.clearCallingIdentity();
    if (paramComponentName == null)
      throw new IllegalArgumentException("input component name is null"); 
    checkActiveAndUserId(paramComponentName);
    try {
      boolean bool;
      ContentResolver contentResolver = this.mContext.getContentResolver();
      if (paramBoolean) {
        bool = true;
      } else {
        bool = false;
      } 
      Settings.Global.putInt(contentResolver, "home_key_disabled", bool);
      return;
    } catch (Exception exception) {
      Slog.e("IflytekService", "Exception:" + exception);
      return;
    } finally {
      Binder.restoreCallingIdentity(l);
    } 
  }
  
  public void setMmsReceiveDisabled(ComponentName paramComponentName, boolean paramBoolean) {}
  
  public void setMmsSendDisabled(ComponentName paramComponentName, boolean paramBoolean) {
    Slog.d("IflytekService", "set mms send disabled: " + paramBoolean);
    setUserRestriction(paramComponentName, "no_sms", paramBoolean);
  }
  
  public void setNFCDisabled(ComponentName paramComponentName, boolean paramBoolean) {
    Slog.d("IflytekService", "set nfc disabled: " + paramBoolean);
    setUserRestriction(paramComponentName, "no_outgoing_beam", paramBoolean);
  }
  
  public void setNetworkReseted(ComponentName paramComponentName) throws RemoteException {
    if (paramComponentName == null)
      throw new IllegalArgumentException("input component name is null"); 
    checkActiveAndUserId(paramComponentName);
    long l = Binder.clearCallingIdentity();
    try {
      Slog.d("IflytekService", "setNetworkReseted");
      WifiManager wifiManager = (WifiManager)this.mContext.getSystemService("wifi");
      if (wifiManager != null)
        wifiManager.factoryReset(); 
      return;
    } catch (Exception exception) {
      Slog.e("IflytekService", "setNetworkReseted:", exception);
      return;
    } finally {
      Binder.restoreCallingIdentity(l);
    } 
  }
  
  public void setPowerDisabled(ComponentName paramComponentName, boolean paramBoolean) throws RemoteException {
    long l = Binder.clearCallingIdentity();
    if (paramComponentName == null)
      throw new IllegalArgumentException("input component name is null"); 
    checkActiveAndUserId(paramComponentName);
    try {
      boolean bool;
      ContentResolver contentResolver = this.mContext.getContentResolver();
      if (paramBoolean) {
        bool = true;
      } else {
        bool = false;
      } 
      Settings.Global.putInt(contentResolver, "long_power_key_disabled", bool);
      return;
    } catch (Exception exception) {
      Slog.e("IflytekService", "Exception:" + exception);
      return;
    } finally {
      Binder.restoreCallingIdentity(l);
    } 
  }
  
  public void setScreenLockRemoved(ComponentName paramComponentName) throws RemoteException {
    if (paramComponentName == null)
      throw new IllegalArgumentException("input component name is null"); 
    checkActiveAndUserId(paramComponentName);
    long l = Binder.clearCallingIdentity();
    try {
      Slog.d("IflytekService", "setScreenLockRemoved");
      LockPatternUtils lockPatternUtils = new LockPatternUtils(this.mContext);
      lockPatternUtils.clearLock(null, 0);
      lockPatternUtils.setLockScreenDisabled(false, 0);
      return;
    } catch (Exception exception) {
      Slog.e("IflytekService", "setScreenLockRemoved:", exception);
      return;
    } finally {
      Binder.restoreCallingIdentity(l);
    } 
  }
  
  public boolean setScreenTimeouted(ComponentName paramComponentName, int paramInt) throws RemoteException {
    if (paramComponentName == null)
      throw new IllegalArgumentException("input component name is null"); 
    checkActiveAndUserId(paramComponentName);
    long l = Binder.clearCallingIdentity();
    if (paramInt > 0)
      try {
        Slog.d("IflytekService", "setScreenTimeouted:" + String.valueOf(paramInt));
        Settings.System.putInt(this.mContext.getContentResolver(), "screen_off_timeout", paramInt);
        Uri uri = Settings.System.getUriFor("screen_off_timeout");
        this.mContext.getContentResolver().notifyChange(uri, null);
        return true;
      } catch (Exception exception) {
        Slog.e("IflytekService", "setScreenTimeouted:", exception);
        return false;
      } finally {
        Binder.restoreCallingIdentity(l);
      }  
    Binder.restoreCallingIdentity(l);
    return false;
  }
  
  public boolean setSendNotificationDisabled(ComponentName paramComponentName, boolean paramBoolean) throws RemoteException {
    if (paramComponentName == null)
      throw new IllegalArgumentException("admin is null"); 
    checkActiveAndUserId(paramComponentName);
    long l = Binder.clearCallingIdentity();
    try {
      ContentResolver contentResolver = this.mContext.getContentResolver();
      if (paramBoolean) {
        boolean bool1 = true;
        paramBoolean = Settings.Global.putInt(contentResolver, "send_notification_disabled", bool1);
        return paramBoolean;
      } 
      boolean bool = false;
      paramBoolean = Settings.Global.putInt(contentResolver, "send_notification_disabled", bool);
      return paramBoolean;
    } finally {
      Binder.restoreCallingIdentity(l);
    } 
  }
  
  public void setSilentActiveAdmin(ComponentName paramComponentName) {
    if (paramComponentName == null)
      throw new IllegalArgumentException("admin is null"); 
    IDevicePolicyManager iDevicePolicyManager = IDevicePolicyManager.Stub.asInterface(ServiceManager.getService("device_policy"));
    if (iDevicePolicyManager == null)
      throw new IllegalArgumentException("Error: Could not access the Device Policy Manager. Is the system running?"); 
    long l = Binder.clearCallingIdentity();
    try {
      iDevicePolicyManager.setActiveAdmin(paramComponentName, true, this.mContext.getUserId());
      Slog.d("IflytekService", "Success: set Active admin to package " + paramComponentName);
      DevicePolicyManager devicePolicyManager = (DevicePolicyManager)this.mContext.getSystemService("device_policy");
      if (devicePolicyManager != null) {
        ComponentName componentName = devicePolicyManager.getProfileOwner();
        if (componentName == null) {
          try {
            if (!iDevicePolicyManager.setProfileOwner(paramComponentName, "Iflytek", this.mContext.getUserId()))
              throw new RuntimeException("Can't set package " + paramComponentName + " as Profile owner."); 
          } catch (Exception exception) {
            throw exception;
          } 
          iDevicePolicyManager.setUserProvisioningState(3, this.mContext.getUserId());
          Slog.d("IflytekService", "Success: Active admin and Profile owner set to package " + exception);
        } 
      } 
      return;
    } catch (Exception exception) {
      Slog.e("IflytekService", "setSilentActiveAdmin Exception:" + exception);
      return;
    } finally {
      Binder.restoreCallingIdentity(l);
    } 
  }
  
  public void setSleepDeviceDisabled(ComponentName paramComponentName, boolean paramBoolean) throws RemoteException {
    long l = Binder.clearCallingIdentity();
    if (paramComponentName == null)
      throw new IllegalArgumentException("input component name is null"); 
    checkActiveAndUserId(paramComponentName);
    try {
      boolean bool;
      ContentResolver contentResolver = this.mContext.getContentResolver();
      if (paramBoolean) {
        bool = true;
      } else {
        bool = false;
      } 
      Settings.Global.putInt(contentResolver, "short_power_key_disabled", bool);
      return;
    } catch (Exception exception) {
      Slog.e("IflytekService", "Exception:" + exception);
      return;
    } finally {
      Binder.restoreCallingIdentity(l);
    } 
  }
  
  public void setStatusBarExpandPanelDisabled(ComponentName paramComponentName, boolean paramBoolean) throws RemoteException {
    if (paramComponentName == null)
      throw new IllegalArgumentException("admin is null"); 
    checkActiveAndUserId(paramComponentName);
    long l = Binder.clearCallingIdentity();
    try {
      boolean bool;
      ContentResolver contentResolver = this.mContext.getContentResolver();
      if (paramBoolean) {
        bool = true;
      } else {
        bool = false;
      } 
      Settings.Global.putInt(contentResolver, "status_bar_expand_panel_disabled", bool);
      return;
    } finally {
      Binder.restoreCallingIdentity(l);
    } 
  }
  
  public void setSuperWhiteListForHwSystemManger(ComponentName paramComponentName, List<String> paramList) {
    if (paramComponentName == null)
      throw new IllegalArgumentException("admin is null"); 
    checkActiveAndUserId(paramComponentName);
    if (paramList != null && paramList.size() != 0 && paramList.size() <= 200) {
      StringJoiner stringJoiner = new StringJoiner(",");
      Iterator<String> iterator = paramList.iterator();
      while (iterator.hasNext())
        stringJoiner.add(iterator.next()); 
      null = stringJoiner.toString();
      long l = Binder.clearCallingIdentity();
      try {
        Settings.Global.putString(this.mContext.getContentResolver(), "super_white_list", null);
        return;
      } finally {
        Binder.restoreCallingIdentity(l);
      } 
    } 
    throw new IllegalArgumentException("packageNames is illegal");
  }
  
  public void setSysTime(ComponentName paramComponentName, long paramLong) throws RemoteException {
    if (paramComponentName == null)
      throw new IllegalArgumentException("input component name is null"); 
    checkActiveAndUserId(paramComponentName);
    long l = Binder.clearCallingIdentity();
    ContentResolver contentResolver = this.mContext.getContentResolver();
    try {
      Slog.d("IflytekService", "setSysTime:" + String.valueOf(paramLong));
      Settings.Global.putInt(contentResolver, "auto_time", 0);
      if (paramLong < 2145887940000L && paramLong > 0L)
        ((AlarmManager)this.mContext.getSystemService("alarm")).setTime(paramLong); 
      return;
    } catch (Exception exception) {
      Slog.e("IflytekService", "setSysTime:", exception);
      return;
    } finally {
      Settings.Global.putInt(contentResolver, "auto_time", 1);
      Binder.restoreCallingIdentity(l);
    } 
  }
  
  public void setSystemApplicationFreezed(ComponentName paramComponentName, String paramString, boolean paramBoolean) throws RemoteException {
    byte b = 1;
    if (paramComponentName == null)
      throw new IllegalArgumentException("admin is null"); 
    checkActiveAndUserId(paramComponentName);
    null = (PackageManagerService)ServiceManager.getService("package");
    long l = Binder.clearCallingIdentity();
    if (paramBoolean)
      b = 3; 
    try {
      null.setApplicationEnabledSetting(paramString, b, 1, this.mContext.getUserId(), this.mContext.getOpPackageName());
      return;
    } finally {
      Binder.restoreCallingIdentity(l);
    } 
  }
  
  public void setTaskButtonDisabled(ComponentName paramComponentName, boolean paramBoolean) throws RemoteException {
    long l = Binder.clearCallingIdentity();
    if (paramComponentName == null)
      throw new IllegalArgumentException("input component name is null"); 
    checkActiveAndUserId(paramComponentName);
    try {
      boolean bool;
      ContentResolver contentResolver = this.mContext.getContentResolver();
      if (paramBoolean) {
        bool = true;
      } else {
        bool = false;
      } 
      Settings.Global.putInt(contentResolver, "task_key_disabled", bool);
      return;
    } catch (Exception exception) {
      Slog.e("IflytekService", "Exception:" + exception);
      return;
    } finally {
      Binder.restoreCallingIdentity(l);
    } 
  }
  
  public void setUSBDataDisabled(ComponentName paramComponentName, boolean paramBoolean) throws RemoteException {
    if (paramComponentName == null)
      throw new IllegalArgumentException("input component name is null"); 
    checkActiveAndUserId(paramComponentName);
    this.mContext.getContentResolver();
    long l = Binder.clearCallingIdentity();
    null = (UsbManager)this.mContext.getSystemService(UsbManager.class);
    try {
      Slog.d("IflytekService", "setUSBDataDisabled:" + String.valueOf(paramBoolean));
      if (paramBoolean) {
        null.setCurrentFunction("mtp", false);
      } else {
        null.setCurrentFunction("mtp", true);
      } 
      return;
    } catch (Exception exception) {
      Slog.e("IflytekService", "setUSBDataDisabled:", exception);
      return;
    } finally {
      Binder.restoreCallingIdentity(l);
    } 
  }
  
  public void setUSBOtgDisabled(ComponentName paramComponentName, boolean paramBoolean) throws RemoteException {
    if (paramComponentName == null)
      throw new IllegalArgumentException("input component name is null"); 
    checkActiveAndUserId(paramComponentName);
  }
  
  public void setVoiceIncomingDisabled(ComponentName paramComponentName, boolean paramBoolean) {}
  
  public void setVoiceOutgoingDisable(ComponentName paramComponentName, boolean paramBoolean) {
    Slog.d("IflytekService", "set voice outgoing disabled: " + paramBoolean);
    setUserRestriction(paramComponentName, "no_outgoing_calls", paramBoolean);
  }
  
  public void setVolumeAdjustDisabled(ComponentName paramComponentName, boolean paramBoolean) throws RemoteException {
    long l = Binder.clearCallingIdentity();
    if (paramComponentName == null)
      throw new IllegalArgumentException("input component name is null"); 
    checkActiveAndUserId(paramComponentName);
    try {
      boolean bool;
      ContentResolver contentResolver = this.mContext.getContentResolver();
      if (paramBoolean) {
        bool = true;
      } else {
        bool = false;
      } 
      Settings.Global.putInt(contentResolver, "volume_key_disabled", bool);
      return;
    } catch (Exception exception) {
      Slog.e("IflytekService", "Exception:" + exception);
      return;
    } finally {
      Binder.restoreCallingIdentity(l);
    } 
  }
  
  public void setWIFIeditDisabled(ComponentName paramComponentName, boolean paramBoolean) {
    Slog.d("IflytekService", "set wifi edit disabled: " + paramBoolean);
    setUserRestriction(paramComponentName, "no_config_wifi", paramBoolean);
  }
  
  public void setWifiAdvancedOptionsDisabled(ComponentName paramComponentName, boolean paramBoolean) {
    if (paramComponentName == null)
      throw new IllegalArgumentException("admin is null"); 
    checkActiveAndUserId(paramComponentName);
    long l = Binder.clearCallingIdentity();
    try {
      boolean bool;
      ContentResolver contentResolver = this.mContext.getContentResolver();
      if (paramBoolean) {
        bool = true;
      } else {
        bool = false;
      } 
      Settings.Global.putInt(contentResolver, "wifi_advanced_options_disabled", bool);
      return;
    } finally {
      Binder.restoreCallingIdentity(l);
    } 
  }
  
  public void shutdownDevice(ComponentName paramComponentName) {
    if (paramComponentName == null)
      throw new IllegalArgumentException("admin is null"); 
    checkActiveAndUserId(paramComponentName);
    long l = Binder.clearCallingIdentity();
    try {
      Intent intent = new Intent("com.android.internal.intent.action.REQUEST_SHUTDOWN");
      intent.putExtra("android.intent.extra.KEY_CONFIRM", false);
      intent.setFlags(268435456);
      this.mContext.startActivity(intent);
      return;
    } catch (Exception exception) {
      Slog.e("IflytekService", "shutdownDevice:" + exception.getMessage());
      return;
    } finally {
      Binder.restoreCallingIdentity(l);
    } 
  }
  
  public void test() throws RemoteException {
    Slog.d("IflytekService", "enter test function");
  }
  
  public void turnOnGPS(ComponentName paramComponentName, boolean paramBoolean) {
    Slog.d("IflytekService", "turn on gps: " + paramBoolean);
    long l = Binder.clearCallingIdentity();
    Preconditions.checkNotNull(paramComponentName, "ComponentName is null");
    checkActiveAndUserId(paramComponentName);
    if (paramBoolean)
      try {
        Settings.Secure.putInt(this.mContext.getContentResolver(), "location_mode", 2);
        return;
      } catch (Exception exception) {
        Slog.e("IflytekService", "Exception:" + exception);
        return;
      } finally {
        Binder.restoreCallingIdentity(l);
      }  
    Settings.Secure.putInt(this.mContext.getContentResolver(), "location_mode", 0);
    Binder.restoreCallingIdentity(l);
  }
  
  public void uninstallPackage(ComponentName paramComponentName, String paramString, boolean paramBoolean) {
    if (paramComponentName == null)
      throw new IllegalArgumentException("admin is null"); 
    checkActiveAndUserId(paramComponentName);
    null = this.mContext.getPackageManager();
    long l = Binder.clearCallingIdentity();
    try {
      null.deletePackage(paramString, null, 2);
      if (!paramBoolean)
        null.clearApplicationUserData(paramString, null); 
      return;
    } catch (Exception exception) {
      Slog.e("IflytekService", "uninstallPackage:" + exception.getMessage());
      return;
    } finally {
      Binder.restoreCallingIdentity(l);
    } 
  }
  
  public class NetworkConnectChangedReceiver extends BroadcastReceiver {
    public void onReceive(Context param1Context, Intent param1Intent) {
      Slog.d("IflytekService", "NetworkConnectChangedReceiver: action = " + param1Intent.getAction());
      NetworkInfo[] arrayOfNetworkInfo = ((ConnectivityManager)param1Context.getSystemService("connectivity")).getAllNetworkInfo();
      boolean bool2 = false;
      boolean bool1 = false;
      if (arrayOfNetworkInfo != null) {
        int i = 0;
        while (true) {
          bool2 = bool1;
          if (i < arrayOfNetworkInfo.length) {
            bool2 = bool1;
            if (arrayOfNetworkInfo[i].getState() == NetworkInfo.State.CONNECTED) {
              int j = IflytekService.-get6(IflytekService.this).getConnectionInfo().getIpAddress();
              Slog.d("IflytekService", "NetworkConnectChangedReceiver: ipAddress = " + j);
              bool2 = bool1;
              if (j != 0) {
                bool2 = true;
                bool1 = true;
                String str = IflytekService.-wrap1(IflytekService.this, j);
                Slog.d("IflytekService", "NetworkConnectChangedReceiver: wlan0Ip = " + str);
                if (IflytekService.-get5(IflytekService.this) != null) {
                  try {
                    IflytekService.-get5(IflytekService.this).enableUrl(str, true);
                    bool2 = bool1;
                  } catch (Exception exception) {
                    exception.printStackTrace();
                    bool2 = bool1;
                  } 
                } else {
                  continue;
                } 
              } else {
                continue;
              } 
            } else {
              continue;
            } 
          } 
          IflytekService.-wrap3(IflytekService.this, bool2);
          return;
          i++;
          bool1 = bool2;
        } 
      } 
      IflytekService.-wrap3(IflytekService.this, bool2);
    }
  }
  
  class StorageListener extends StorageEventListener {
    private boolean doneFlag = false;
    
    String newState;
    
    String oldState;
    
    String path;
    
    public void action() {
      // Byte code:
      //   0: aload_0
      //   1: monitorenter
      //   2: aload_0
      //   3: iconst_1
      //   4: putfield doneFlag : Z
      //   7: aload_0
      //   8: invokevirtual notifyAll : ()V
      //   11: aload_0
      //   12: monitorexit
      //   13: return
      //   14: astore_1
      //   15: aload_0
      //   16: monitorexit
      //   17: aload_1
      //   18: athrow
      // Exception table:
      //   from	to	target	type
      //   2	11	14	finally
    }
    
    public boolean isDone() {
      return this.doneFlag;
    }
    
    public void onStorageStateChanged(String param1String1, String param1String2, String param1String3) {
      Slog.i("IflytekService", "Storage state changed from " + param1String2 + " to " + param1String3);
      this.oldState = param1String2;
      this.newState = param1String3;
      this.path = param1String1;
      action();
    }
  }
}


/* Location:              C:\Users\lenovo\Desktop\dex2jar-2.0\classes-dex2jar.jar!\com\android\server\iflytek\IflytekService.class
 * Java compiler version: 6 (50.0)
 * JD-Core Version:       1.1.3
 */