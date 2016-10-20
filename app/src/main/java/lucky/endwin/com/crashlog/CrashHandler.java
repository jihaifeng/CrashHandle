package lucky.endwin.com.crashlog;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Environment;
import android.os.Looper;
import android.os.Process;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 全局异常处理
 * <p/>
 * Created by jihf on 2016/6/23 0023.
 */
public class CrashHandler implements Thread.UncaughtExceptionHandler {
  private static final String TAG = CrashHandler.class.getSimpleName().trim();
  private static CrashHandler Instance;
  private Context mContext;
  //捕获到异常后，程序休眠3秒钟
  private static final long sleepTime = 3000;
  //退出APP
  public static final int EXIT_APP = 0;
  //重启APP
  public static final int RESTART_APP = 1;
  //只做异常信息收集，让系统处理异常
  public static final int DEFAULT = 2;
  //选择的异常处理类型，默认退出
  private int mHandleType = EXIT_APP;
  //表示异常处理类型的集合
  private static List<Integer> handleTypeList = new ArrayList<>();
  //系统默认的异常处理器
  private Thread.UncaughtExceptionHandler mDefaultHandler;
  //自动删除5天前的崩溃日志,即文件保存五天
  private int delectDay = 5;
  //存储设备信息和异常信息的Map
  private Map<String, String> infos = new HashMap<>();
  //崩溃日志文件的名字，前缀，全称 （mFileName + 当天日期），默认txt文件
  private static String mFileName = "CrashLog";
  //崩溃日志文件夹默认名字Crash
  private static String defaultFloadName = "Crash";
  //崩溃日志文件夹的名字，默认为Crash
  private static String mfloadName = defaultFloadName;
  //文件名之间的分隔符
  private static String SEPARATOR = "-";
  //程序异常后的提示语数组
  private String[] msg = { "对不起，程序出现异常，即将退出！", "对不起，程序出现异常，即将重启！", "" };
  //要显示的提示语
  private String strToast = "";

  /**
   * 单例模式
   */
  public static CrashHandler getInstance() {
    if (null == Instance) {
      Instance = new CrashHandler();
    }
    return Instance;
  }

  /**
   * 启用全局异常捕获
   *
   * @param type 异常处理类型，0=====>退出APP，1======>重启
   */
  public void init(Context context, int type) {
    mContext = context;
    // 设置异常处理类型
    setHandleType(type);
    //获取系统默认的异常处理启器
    mDefaultHandler = Thread.getDefaultUncaughtExceptionHandler();
    //设置该类（CrashHandler）作为默认的异常处理器
    Thread.setDefaultUncaughtExceptionHandler(Instance);
    //设置自动删除崩溃日志文件
    autoDelectFile(delectDay);
    mfloadName = TextUtils.isEmpty(getApplicationName(context)) ? defaultFloadName : getApplicationName(context) + "-Crash";
  }

  public String getApplicationName(Context context) {
    PackageManager packageManager = null;
    ApplicationInfo applicationInfo = null;
    try {
      packageManager = context.getApplicationContext().getPackageManager();
      applicationInfo = packageManager.getApplicationInfo(context.getPackageName(), 0);
    } catch (PackageManager.NameNotFoundException e) {
      applicationInfo = null;
    }
    String applicationName = (String) packageManager.getApplicationLabel(applicationInfo);
    return applicationName;
  }

  @Override public void uncaughtException(Thread thread, Throwable ex) {
    if (!handleException(ex) && null != mDefaultHandler) {
      //如果没做处理，则让系统处理器处理
      mDefaultHandler.uncaughtException(thread, ex);
    } else {
      //休眠5秒钟
      SystemClock.sleep(sleepTime);
      //退出应用程序
      Process.killProcess(Process.myPid());
      System.exit(1);
    }
  }

  /**
   * 设置异常处理类型
   */
  public void setHandleType(int handleType) {
    if (handleTypeList.size() != 0) {
      //先清空list
      handleTypeList.clear();
    }
    handleTypeList.add(0);
    handleTypeList.add(1);
    handleTypeList.add(2);
    //设置异常处理类型
    if (!handleTypeList.contains(handleType)) {
      mHandleType = DEFAULT;
    } else {
      mHandleType = handleType;
    }
    strToast = msg[handleType];
  }

  private void autoDelectFile(final int autoClearDay) {
    FileUtils.delete(getfloadPath(), new FilenameFilter() {

      @Override public boolean accept(File file, String filename) {
        String s = FileUtils.getFileNameWithoutExtension(filename);
        int day = autoClearDay < 0 ? autoClearDay : -1 * autoClearDay;
        String date = getmFileName() + SEPARATOR + DateTimeUtils.getOtherDay(day);
        return date.compareTo(s) >= 0;
      }
    });
  }

  /**
   * 全局异常处理
   */
  private boolean handleException(Throwable ex) {
    if (null == ex) {
      //异常为空，返回false
      return false;
    }
    try {
      new Thread(new Runnable() {
        @Override public void run() {
          Looper.prepare();
          if (!TextUtils.isEmpty(strToast)) {
            Toast.makeText(mContext, strToast, Toast.LENGTH_LONG).show();
          }
          Looper.loop();
        }
      }).start();
      // 收集设备参数信息
      collectDeviceInfo(mContext);
      // 保存日志文件
      saveCrashInfoFile(ex);
      if (DEFAULT == mHandleType) {
        return false;
      } else {
        //休眠3秒钟
        SystemClock.sleep(sleepTime);
        if (RESTART_APP == mHandleType) {
          //重启应用程序
          Intent intent = mContext.getPackageManager().getLaunchIntentForPackage(mContext.getPackageName());
          intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
          mContext.startActivity(intent);
        } else if (EXIT_APP == mHandleType) {
          //退出应用程序
          Process.killProcess(Process.myPid());
          System.exit(1);
        }
        return true;
      }
    } catch (Exception e) {
      e.printStackTrace();
      return false;
    }
  }

  /**
   * 收集设备参数信息
   */
  private void collectDeviceInfo(Context mContext) {

    try {
      PackageManager packageManager = mContext.getPackageManager();
      PackageInfo packageInfo = packageManager.getPackageInfo(mContext.getPackageName(), PackageManager.GET_ACTIVITIES);
      if (null != packageInfo) {
        String versionName = packageInfo.versionName;
        String versionCode = String.valueOf(packageInfo.versionCode);
        infos.put("versionName", versionName);
        infos.put("versionCode", versionCode);
      }
    } catch (PackageManager.NameNotFoundException e) {
      e.printStackTrace();
    }
    try {
      Field[] fields = Build.class.getDeclaredFields();
      for (Field field : fields) {
        field.setAccessible(true);
        infos.put(field.getName(), field.getName() + ": " + field.get(null).toString());
      }
    } catch (IllegalAccessException e) {
      e.printStackTrace();
    }
  }

  /**
   * 保存日志文件
   */
  private String saveCrashInfoFile(Throwable ex) {
    StringBuilder sb = new StringBuilder();
    String dateTimeNow = DateTimeUtils.getCurrentDateTimeFormat();
    try {
      sb.append("\r\n" + dateTimeNow + "\n");
      for (Map.Entry<String, String> entry : infos.entrySet()) {
        String key = entry.getKey();
        String value = entry.getValue();
        sb.append(key + " = " + value + "\n");
      }
      Writer writer = new StringWriter();
      PrintWriter printWriter = new PrintWriter(writer);
      ex.printStackTrace(printWriter);
      Throwable throwable = ex.getCause();
      if (null != throwable) {
        throwable.printStackTrace(printWriter);
        throwable = throwable.getCause();
      }
      printWriter.flush();
      printWriter.close();
      String errorMsg = writer.toString();
      sb.append(errorMsg);
      sb.append("\n==========================" + "华丽的分割线" + "==========================\n");
      Log.e(TAG, sb.toString() + "");
      String fileName = writeErrorToFile(sb.toString());
      return fileName;
    } catch (Exception e) {
      e.printStackTrace();
      sb.append("an error occured while writing file...\r\n");
      writeErrorToFile(sb.toString());
    }
    return null;
  }

  /**
   * 崩溃日志写入文件
   *
   * @param strError 崩溃日志信息
   * @return 崩溃日志的文件名
   */
  private String writeErrorToFile(String strError) {

    String fileName = getFileFullName();
    try {
      if (FileUtils.hasSdcard()) {
        String path = getfloadPath();
        File dir = new File(path);
        if (!dir.exists()) {
          dir.mkdir();
        }
        FileOutputStream fos = new FileOutputStream(path + fileName, true);
        fos.write(strError.getBytes());
        fos.flush();
        fos.close();
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
    return fileName;
  }

  /**
   * 自定义崩溃日志的前缀，全称（fileName + 当天日期），默认txt文件
   * <p/>
   * 如  CrashLog-20160624.txt
   */
  public void setFileName(String fileName) {
    if (null != fileName && !TextUtils.isEmpty(fileName) && !"".equals(fileName)) {
      mFileName = fileName;
    }
  }

  public static String getmFileName() {
    return mFileName;
  }

  /**
   * 获取崩溃日志的文件名(全称)
   *
   * @return 如 CrashLog-20160624.txt
   */
  public String getFileFullName() {
    String dateToday = DateTimeUtils.getCustomDateFormat();
    String fileName = getmFileName() + SEPARATOR + dateToday + ".txt";
    return fileName;
  }

  /**
   * 设置崩溃日志保存的文件夹名称，默认为Crash
   */
  public void setfloadName(String floadName) {
    if (null != floadName && !TextUtils.isEmpty(floadName) && !"".equals(floadName)) {
      mfloadName = floadName;
    }
  }

  /**
   * 获取崩溃日志保存的文件夹
   */
  public String getfloadPath() {
    return Environment.getExternalStorageDirectory().getAbsolutePath() + File.separator + mfloadName + File.separator;
  }
}
