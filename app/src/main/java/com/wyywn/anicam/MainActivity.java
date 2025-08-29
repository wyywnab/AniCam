package com.wyywn.anicam;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.MenuItem;
import android.view.Window;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.navigation.ui.NavigationUI;
import androidx.preference.PreferenceManager;

import com.google.android.material.color.DynamicColors;
import com.google.android.material.navigation.NavigationBarView;
import com.wyywn.anicam.databinding.ActivityMainBinding;
import com.wyywn.anicam.ui.PhotographFragment;
import com.wyywn.anicam.utils.Functions;
import com.wyywn.anicam.utils.TextViewHint;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class MainActivity extends AppCompatActivity
        implements LocationListener, PhotographFragment.MainActivityListener {

    private static final int REQUEST_STORAGE_PERMISSION = 100;
    private String[] getRequiredPermissions() {
        List<String> permissionsList = new ArrayList<>();
        permissionsList.add(Manifest.permission.CAMERA);
        permissionsList.add(Manifest.permission.ACCESS_COARSE_LOCATION);
        permissionsList.add(Manifest.permission.ACCESS_FINE_LOCATION);
        permissionsList.add(Manifest.permission.INTERNET);

        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            permissionsList.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
            permissionsList.add(Manifest.permission.READ_EXTERNAL_STORAGE);
        }

        return permissionsList.toArray(new String[0]);
    }

    private ActivityMainBinding binding;

    //Context context;
    private SharedPreferences prefs_setting;
    private String exDataPath;
    private LocationManager locationManager;
    //private Location latestLocation;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    // 使用 volatile 确保跨线程的可见性
    private volatile LocationRequestCallback locationRequestCallback;
    private final AtomicBoolean isWaitingForLocation = new AtomicBoolean(false);

    public interface LocationRequestCallback {
        void onLocationReceived(Location location);
        void onLocationTimeout();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        requestWindowFeature(Window.FEATURE_NO_TITLE);

        DynamicColors.applyToActivityIfAvailable(this);

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        prefs_setting = PreferenceManager.getDefaultSharedPreferences(this);

        if (!isAllPermissionsGranted()){
            ActivityCompat.requestPermissions(this, getRequiredPermissions(), 1001);
        }

        // 设置状态栏背景颜色
        //getWindow().setStatusBarColor(Color.WHITE); // 设置状态栏颜色为白色
        // 设置状态栏文字和图标为深色（仅API23以上）
        /*View decorView = getWindow().getDecorView();
        int flags = decorView.getSystemUiVisibility();
        flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR; // 添加浅色状态栏标志
        decorView.setSystemUiVisibility(flags);*/

        /*BottomNavigationView navView = findViewById(R.id.nav_view);
        // Passing each menu ID as a set of Ids because each
        // menu should be considered as top level destinations.
        AppBarConfiguration appBarConfiguration = new AppBarConfiguration.Builder(
                R.id.navigation_home, R.id.navigation_photograph, R.id.navigation_table, R.id.navigation_settings)
                .build();

        NavigationUI.setupActionBarWithNavController(this, navController, appBarConfiguration);*/
        NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment_activity_main);
        NavigationUI.setupWithNavController(binding.navView, navController);
        /*binding.navView.setOnItemSelectedListener(new NavigationBarView.OnItemSelectedListener() {
            @Override
            public boolean onNavigationItemSelected(@NonNull MenuItem item) {
                int id = item.getItemId();
                if (id == R.id.navigation_home) {
                    navController.navigate(R.id.navigation_home);
                    return true;
                } else if (id == R.id.navigation_photograph) {
                    navController.navigate(R.id.navigation_photograph);
                    return true;
                } else if (id == R.id.navigation_table) {
                    navController.navigate(R.id.navigation_table);
                    return true;
                } else if (id == R.id.navigation_settings) {
                    navController.navigate(R.id.navigation_settings);
                    return true;
                }
                //navController.navigate(id);
                return true;
            }
        });*/

        TextViewHint.init(findViewById(R.id.info_textView));

        //context = this;
        exDataPath = Objects.requireNonNull(getExternalFilesDir("")).getAbsolutePath();

        testFile();

        if (prefs_setting.getBoolean("addLocation", true)){
            initLocationService();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        TextViewHint.resetState(); // 确保全局重置
    }

    private boolean isAllPermissionsGranted() {
        boolean allPermissionsGranted = true;
        for (String permission : getRequiredPermissions()) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                allPermissionsGranted = false;
                break;
            }
        }
        return allPermissionsGranted;
    }

    private void initLocationService() {
        try {
            locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
            if (!locationManager.getAllProviders().contains(LocationManager.NETWORK_PROVIDER)) {
                // Fallback to GPS or handle the error
                Log.e("Location", "Network provider unavailable");
                return;
            }
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, getRequiredPermissions(), 1001);
                //return;
            }
            /*locationManager.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER,
                    0L,
                    0f,
                    this
            );*/

        } catch (Exception e) {
            Log.e("MainActivity", "初始化服务失败", e);
        }
    }
    public boolean getLocationServiceAvailability(){
        return (locationManager.getAllProviders().contains(LocationManager.NETWORK_PROVIDER) && locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER));
    }
    // 拍照前调用此方法，启动位置更新
    @SuppressLint("MissingPermission")
    public void startLocationUpdates(@NonNull LocationRequestCallback callback) {
        if (!isWaitingForLocation.getAndSet(true)) {
            Log.d("LocationService", "Starting location updates");
            this.locationRequestCallback = callback;
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                // 在主线程上请求位置更新，避免Handler错误
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (locationManager.hasProvider(LocationManager.NETWORK_PROVIDER)) {
                        locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 0, 0, this, Looper.getMainLooper());
                    }
                    if (locationManager.hasProvider(LocationManager.GPS_PROVIDER)) {
                        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, this, Looper.getMainLooper());
                    }
                } else {
                    locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, this, Looper.getMainLooper());
                    locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 0, 0, this, Looper.getMainLooper());
                }
                // 设置一个超时，如果5秒内未获取到位置，则停止等待
                scheduler.schedule(() -> {
                    setLocationRequestTimeout("Location request timed out");
                }, 5, TimeUnit.SECONDS);

            } else {
                callback.onLocationTimeout();
            }
        }
    }

    private void setLocationRequestTimeout(String msg){
        if (isWaitingForLocation.getAndSet(false)) {
            new Handler(Looper.getMainLooper()).post(() -> {
                Log.d("LocationService", msg);
                stopLocationUpdates();
                if (locationRequestCallback != null) {
                    locationRequestCallback.onLocationTimeout();
                }
            });
        }
    }

    // 拍照后调用此方法，停止位置更新
    public void stopLocationUpdates() {
        /*Log.d("LocationService", "Stopping location updates");
        locationManager.removeUpdates(this);
        isWaitingForLocation.set(false);
        this.locationRequestCallback = null;*/
        if (isWaitingForLocation.getAndSet(false)) {
            Log.d("LocationService", "Stopping location updates");
            locationManager.removeUpdates(this);
            // 这里不直接将 locationRequestCallback 置空，而是在 onLocationChanged 中处理
            // locationRequestCallback = null;
        }
    }

    @Override
    public void onLocationChanged(@NonNull Location location) {
        //latestLocation = location;
        Log.d("LocationService", "Location received: " + location.toString());
        // 收到位置后立即停止更新并通知回调
        /*stopLocationUpdates();
        if (locationRequestCallback != null) {
            locationRequestCallback.onLocationReceived(location);
        }*/
        LocationRequestCallback callbackToExecute = locationRequestCallback;
        if (callbackToExecute != null) {
            callbackToExecute.onLocationReceived(location);
            // 立即停止位置更新，并清空回调，防止重复调用
            stopLocationUpdates();
        } else {
            // 如果回调已为空，说明已经处理过位置或者超时，直接停止更新
            stopLocationUpdates();
            Log.d("LocationService", "Location received after timeout or previous successful capture.");
        }
    }

    /*@Override
    public Location getLatestLocation() {
        return latestLocation;
    }*/
    /*// 当位置改变时执行，除了移动设置距离为 0时
    @Override
    public void onLocationChanged(@NonNull Location location) {
        latestLocation = location;
        TextViewHint.showText(latestLocation.toString());
        //Toast.makeText(context, Double.toString(latitude), Toast.LENGTH_SHORT).show();
        // 移除位置管理器
        // 需要一直获取位置信息可以去掉这个
        //locationManager.removeUpdates(this);
    }*/
    // 当前定位提供者状态
    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {
        Log.e("onStatusChanged", provider);
    }
    // 任意定位提高者启动执行
    @Override
    public void onProviderEnabled(@NonNull String provider) {
        Log.e("onProviderEnabled", provider);
    }
    // 任意定位提高者关闭执行
    @Override
    public void onProviderDisabled(@NonNull String provider) {
        Log.e("onProviderDisabled", provider);
    }

    private void testFile(){
        try {
            if (Functions.getLib(exDataPath).length() == 0) {
                Functions.saveLib(exDataPath, new JSONArray("[]"));
            }
            if (Functions.getEnv(exDataPath).length() == 0) {
                Functions.saveEnv(exDataPath, new JSONObject("{}"));
            }
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }

}