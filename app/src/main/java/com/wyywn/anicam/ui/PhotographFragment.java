package com.wyywn.anicam.ui;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Point;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AlertDialog;
import androidx.exifinterface.media.ExifInterface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Display;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.camera.core.AspectRatio;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraControl;
import androidx.camera.core.CameraInfo;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.FocusMeteringAction;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.MeteringPoint;
import androidx.camera.core.MeteringPointFactory;
import androidx.camera.core.Preview;
import androidx.camera.core.ZoomState;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.LiveData;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.slider.Slider;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.textfield.TextInputEditText;
import com.google.common.util.concurrent.ListenableFuture;
import com.wyywn.anicam.utils.FixedSizeQueue;
import com.wyywn.anicam.utils.FullScreenUtils;
import com.wyywn.anicam.utils.Functions;
import com.wyywn.anicam.MainActivity;
import com.wyywn.anicam.R;
import com.wyywn.anicam.adapter.PhotographLibListAdapter;
import com.wyywn.anicam.adapter.PhotographPicListAdapter;
import com.wyywn.anicam.adapter.PhotographScreenItemTouchHelperCallback;
import com.wyywn.anicam.databinding.FragmentPhotographBinding;
import com.wyywn.anicam.utils.TextViewHint;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public class PhotographFragment extends Fragment implements
        PhotographPicListAdapter.OnOrderChangedListener,  PhotographPicListAdapter.OnItemRemovedListener, SensorEventListener {

    private static final int ON_LIB_TAB = 0;
    private static final int ON_SCREEN_TAB = 1;
    private static final int MENU_COPY = 0;
    private static final int MENU_EDIT = 1;
    private static final int MENU_DELETE = 2;
    private static final int ASPECT_RATIO_FULL = 0;
    private static final int ASPECT_RATIO_16_9 = 1;
    private static final int ASPECT_RATIO_4_3 = 2;
    private static final int ASPECT_RATIO_1_1 = 3;
    private static final int FACING_BACK = 1;
    private static final int FACING_FRONT = 0;

    private static final int[] ASPECT_RATIO_LIST = {ASPECT_RATIO_1_1, ASPECT_RATIO_4_3, ASPECT_RATIO_16_9, ASPECT_RATIO_FULL};

    private Camera camera;
    private Executor executor = Executors.newSingleThreadExecutor();
    private ListenableFuture<ProcessCameraProvider> cameraProviderFuture;
    ProcessCameraProvider cameraProvider;
    //private ExecutorService cameraExecutor;
    private ImageCapture imageCapture;
    private PreviewView cameraPreviewView;
    private CameraControl cameraControl;
    private CameraInfo cameraInfo;
    private SensorManager sensorManager;
    private Sensor accelerometer;
    private long lastAccelerometerUpdateTime = 0;
    private FixedSizeQueue angleQueue;
    private boolean isFlashOn = false;
    private int screenWidth;
    private int screenHeight;

    private JSONArray libJsonArr;
    private JSONObject envJsonObj;
    private PhotographLibListAdapter photographLibListAdapter;
    private PhotographLibListAdapter photographPresetListAdapter;
    private PhotographPicListAdapter screenPicListAdapter;
    private ItemTouchHelper screenItemTouchHelper;
    private String exDataPath;
    private FragmentPhotographBinding binding;
    SharedPreferences prefs_setting;
    SharedPreferences prefs_photograph;

    private final List<ImageView> imageViewList = new ArrayList<>();

    private JSONArray selectedPresetPicsArr;
    private int selectedPicPosition = -1;

    private ExecutorService cameraExecutor = Executors.newSingleThreadExecutor();
    private Handler handler = new Handler(Looper.getMainLooper());
    private Runnable updateUIRunnable;

    public interface MainActivityListener {
        void startLocationUpdates(@NonNull MainActivity.LocationRequestCallback callback);
        boolean getLocationServiceAvailability();
    }
    private MainActivityListener mListener;

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        if (context instanceof MainActivityListener) {
            mListener = (MainActivityListener) context;
        }
        else {
            throw new RuntimeException(context.toString().concat(" must implement OnListener"));
        }
    }

    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {

        binding = FragmentPhotographBinding.inflate(inflater, container, false);

        prefs_setting = PreferenceManager.getDefaultSharedPreferences(requireContext());
        assert getActivity() != null;
        prefs_photograph = getActivity().getSharedPreferences("photograph", Context.MODE_PRIVATE);
        exDataPath = Objects.requireNonNull(((MainActivity) getActivity()).getExternalFilesDir("")).getAbsolutePath();

        View rootView = binding.getRoot();
        if (!prefs_setting.getString("volumeButtonCapture", "up").equals("off")){
            setupVolumeKeyListeners(rootView);
        }
        return rootView;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        view.setFocusableInTouchMode(true);
        view.requestFocus();

        // 获得屏幕宽度和高度，以便计算UI到照片的scale值
        assert getActivity() != null;
        WindowManager windowManager = (WindowManager) ((MainActivity) getActivity()).getSystemService(Context.WINDOW_SERVICE);
        Display defaultDisplay = windowManager.getDefaultDisplay();
        Point outPoint = new Point();
        defaultDisplay.getRealSize(outPoint);
        screenWidth = outPoint.x; 
        screenHeight = outPoint.y;

        libJsonArr = Functions.getLib(exDataPath);
        envJsonObj = Functions.getEnv(exDataPath);

        initializePresetDefault();
        refreshLibRecycler();
        refreshPresetListRecycler(true);

        setLibSelection();
        setPresetsSelection();

        //loadPicsToScreen();
        setupUpdateImageViewRunnable();
        initializeDisplay();

        view.post(() -> {
            if (getActivity() != null) {
                FullScreenUtils.enableFullScreen(getActivity());
            }
        });

        cameraPreviewView = binding.cameraPreviewView;
        cameraExecutor = Executors.newSingleThreadExecutor();
        initializeCamera();

        changeAspectRatio(prefs_photograph.getInt("aspectRatioMode", ASPECT_RATIO_4_3));

        NavController navController = Navigation.findNavController(view);
        // 添加目的地变更监听器
        navController.addOnDestinationChangedListener((controller, destination, arguments) -> {
            // 当导航发生时，destination 就是即将显示的目标
            int nextFragmentId = destination.getId();
            // 根据目标ID或类名处理逻辑
            if (nextFragmentId == R.id.navigation_home || nextFragmentId == R.id.navigation_settings) {
                if (getActivity() != null) {
                    FullScreenUtils.disableFullScreen(getActivity());
                }
            }
        });

        requireActivity().getOnBackPressedDispatcher().addCallback(
                getViewLifecycleOwner(), // 关键：绑定到Fragment的生命周期
                new OnBackPressedCallback(true) {
                    @Override
                    public void handleOnBackPressed() {
                        if (prefs_photograph.getBoolean("collapsedNavigation", false)){
                            Functions.showContentWithAnimation(getActivity().findViewById(R.id.nav_view));
                            SharedPreferences.Editor editor = prefs_photograph.edit();
                            editor.putBoolean("collapsedNavigation", false);
                            editor.apply();
                            binding.toggleNavigationButton.setIconResource(R.drawable.keyboard_arrow_down_24px);

                            if (getResources().getConfiguration().orientation != Configuration.ORIENTATION_PORTRAIT){
                                ConstraintLayout.LayoutParams params = (ConstraintLayout.LayoutParams) binding.right.getLayoutParams();
                                params.width = (int) (320 * getResources().getDisplayMetrics().density);
                                binding.right.setLayoutParams(params);
                            }
                        } else {
                            if (getActivity() != null) {
                                FullScreenUtils.disableFullScreen(getActivity());
                            }
                            navController.navigate(R.id.navigation_home);
                        }
                    }
                }
        );
    }

    @Override
    public void onResume() {
        super.onResume();
        setLibSelection();
        setPresetsSelection();
        TextViewHint.init(requireActivity().findViewById(R.id.info_textView));
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        handler.removeCallbacksAndMessages(null);

        TextViewHint.init(null);
        TextViewHint.resetState();
        Functions.hideContentWithAnimation(requireActivity().findViewById(R.id.info_textView));
        //executor.shutdownNow();

        /*SharedPreferences.Editor editor = prefs_photograph.edit();
        editor.putBoolean("autoRotate", false);
        editor.apply();*/
        disableAutoRotate();
        binding = null;
    }

    @Override
    public void onDetach() {
        super.onDetach();
        mListener = null;
    }

    private void setupVolumeKeyListeners(View rootView) {
        rootView.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                String mode = prefs_setting.getString("volumeButtonCapture", "up");
                if ((mode.equals("down") && keyCode == KeyEvent.KEYCODE_VOLUME_DOWN)
                        || (mode.equals("up") && keyCode == KeyEvent.KEYCODE_VOLUME_UP) ){
                    onCapture();
                    return true;
                }
            }
            return false;
        });
    }

    private void setupZoomControl() {
        LiveData<ZoomState> zoomStateLiveData = cameraInfo.getZoomState();

        // 在 LiveData 外部设置监听器，避免重复添加
        setupZoomSliderListener();

        zoomStateLiveData.observe(getViewLifecycleOwner(), zoomState -> {
            if (zoomState != null) {
                float minZoom = zoomState.getMinZoomRatio();
                float maxZoom = zoomState.getMaxZoomRatio();

                if (maxZoom > minZoom) {
                    binding.toggleZoomSliderButton.setVisibility(View.VISIBLE);

                    // 只需要在 onChanged 中更新 Slider 的值和范围
                    // 并且只在首次设置时才更新，或者在 min/max 变动时
                    if (binding.zoomSlider.getValueTo() != 100) { // 简单判断是否已初始化
                        binding.zoomSlider.setValueFrom(0);
                        binding.zoomSlider.setValueTo(100);
                    }

                    // 计算当前缩放比例在Slider上的位置并更新
                    float currentZoom = zoomState.getZoomRatio();
                    float progress = (float) (100 * Math.sqrt((currentZoom - minZoom) / (maxZoom - minZoom)));
                    // 仅当值与当前值有较大差异时才设置，避免无限循环
                    if (Math.abs(binding.zoomSlider.getValue() - progress) > 0.001) {
                        binding.zoomSlider.setValue(progress);
                    }
                } else {
                    binding.toggleZoomSliderButton.setVisibility(View.GONE);
                }
            }
        });
    }

    @SuppressLint("DefaultLocale")
    private void setupZoomSliderListener() {
        binding.zoomSlider.addOnChangeListener((slider, value, fromUser) -> {
            // 在这里获取最新的 minZoom 和 maxZoom
            ZoomState zoomState = cameraInfo.getZoomState().getValue();
            if (zoomState == null) return;
            float minZoom = zoomState.getMinZoomRatio();
            float maxZoom = zoomState.getMaxZoomRatio();

            if (fromUser) {
                float zoomRatio = (float) ((maxZoom - minZoom) * Math.pow(value, 2) / 10000 + minZoom);

                ListenableFuture<Void> future = cameraControl.setZoomRatio(zoomRatio);
                TextViewHint.showText(getString(R.string.hint_zoom).concat(String.format("%.1f", zoomRatio)));
                future.addListener(() -> {
                    // 缩放成功
                }, ContextCompat.getMainExecutor(requireContext()));
            }
        });
    }

    private void initializeCamera() {
        cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext());
        cameraProviderFuture.addListener(() -> {
            try {
                cameraProvider = cameraProviderFuture.get();
                bindPreview(cameraProvider, prefs_photograph.getInt("facing", FACING_BACK));
                //adjustPreviewSize();
            } catch (ExecutionException | InterruptedException e) {
                Log.e("CameraXApp", "Error binding camera", e);
            }
        }, ContextCompat.getMainExecutor((requireContext())));
    }
    private void bindPreview(ProcessCameraProvider cameraProvider, int facingLocal) {
        if (prefs_setting.getBoolean("playgroundMode", false)) {
            Functions.showContentWithAnimation(cameraPreviewView);
            return;
        }

        if (facingLocal == FACING_FRONT){
            Functions.hideContentWithAnimation(binding.switchFlashButton);
            //binding.switchFlashButton.setVisibility(View.INVISIBLE);
        } else {
            if (binding.switchFlashButton.getVisibility() == View.GONE){
                Functions.showContentWithAnimation(binding.switchFlashButton);
            }
            //binding.switchFlashButton.setVisibility(View.VISIBLE);
        }

        cameraProvider.unbindAll();

        int aspectRatioMode = prefs_photograph.getInt("aspectRatioMode", ASPECT_RATIO_4_3);

        Preview.Builder previewBuilder = new Preview.Builder();
        ImageCapture.Builder imageCaptureBuilder = new ImageCapture.Builder();

        int targetAspectRatio = calculateTargetAspectRatio(aspectRatioMode);
        if (targetAspectRatio != -1) {
            previewBuilder.setTargetAspectRatio(targetAspectRatio);
            imageCaptureBuilder.setTargetAspectRatio(targetAspectRatio);
        }

        Preview cameraPreview = previewBuilder.build();
        imageCapture = imageCaptureBuilder.build();

        CameraSelector cameraSelector = new CameraSelector.Builder()
                .requireLensFacing(facingLocal)
                .build();

        try {
            camera = cameraProvider.bindToLifecycle(this, cameraSelector, cameraPreview, imageCapture);
            cameraPreview.setSurfaceProvider(cameraPreviewView.getSurfaceProvider());

            cameraControl = camera.getCameraControl();
            cameraInfo = camera.getCameraInfo();

            setupZoomControl();
            Functions.showContentWithAnimation(cameraPreviewView);
        } catch (Exception e) {
            Log.e("CameraXApp", "Use case binding failed", e);
        }
    }
    private int calculateTargetAspectRatio(int aspectRatioMode) {
        if (aspectRatioMode == ASPECT_RATIO_16_9) {
            return AspectRatio.RATIO_16_9;
        }
        return AspectRatio.RATIO_4_3;
    }
    private void performTapToFocus(float x, float y) {
        if (camera == null) return;

        CameraControl cameraControl = camera.getCameraControl();

        // 1. 创建计量点工厂（基于预览视图）
        MeteringPointFactory factory = cameraPreviewView.getMeteringPointFactory();

        // 2. 创建计量点（将屏幕坐标转换为相机坐标系）
        MeteringPoint point = factory.createPoint(x, y);

        // 3. 创建对焦动作（带超时时间）
        FocusMeteringAction action = new FocusMeteringAction.Builder(point, FocusMeteringAction.FLAG_AF)
                .setAutoCancelDuration(2, TimeUnit.SECONDS)
                .build();

        // 4. 触发对焦
        cameraControl.startFocusAndMetering(action)
                .addListener(() -> {
                    // 对焦成功
                }, cameraExecutor);
    }

    private void initializeDisplay(){
        if (prefs_photograph.getInt("tab", ON_LIB_TAB) == ON_SCREEN_TAB){
            Functions.slideLibToScreen(binding.tabLibLinearLayout, binding.tabScreenLinearLayout);
            TabLayout.Tab tab = binding.switchScreenTabLayout.getTabAt(ON_SCREEN_TAB);
            assert tab != null;
            tab.select();
        }
        autoSelect();
        loadPicsToScreen();
    }

    public boolean autoSelect(){
        try {
            int presetPosition = 0;
            if (envJsonObj.has("currentPhotographSelectedPresetId")){
                presetPosition = Functions.matchedPositionOfId(envJsonObj.getJSONArray("screenPresets"), envJsonObj.getLong("currentPhotographSelectedPresetId"));
            }
            if (selectedPresetPicsArr == null && envJsonObj.getJSONArray("screenPresets").length() > 0){
                selectedPresetPicsArr = envJsonObj.getJSONArray("screenPresets").getJSONObject(presetPosition).getJSONArray("pics");
            }
            if (selectedPicPosition == -1 && selectedPresetPicsArr.length() > 0){
                selectedPicPosition = 0;
            }
            if (screenPicListAdapter.getSelectedPosition() != selectedPicPosition){
                screenPicListAdapter.setSelectedPosition(selectedPicPosition);
            }
            if (selectedPresetPicsArr == null || selectedPicPosition == -1){
                //Toast.makeText(getContext(), "_not selected, return by autoSelect", Toast.LENGTH_SHORT).show();
                return false;
            } else {
                return true;
            }
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }

    }

    /**
     * 设置一系列UI按钮的监听器
     */
    private void setupButtonListeners(){
        binding.switchScreenTabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                int position = tab.getPosition();
                if (position == ON_LIB_TAB){
                    Functions.slideScreenToLib(binding.tabLibLinearLayout, binding.tabScreenLinearLayout);
                } else if (position == ON_SCREEN_TAB) {
                    refreshPresetListRecycler(false);
                    //setPresetsSelection();
                    Functions.slideLibToScreen(binding.tabLibLinearLayout, binding.tabScreenLinearLayout);
                }
                SharedPreferences.Editor editor = prefs_photograph.edit();
                editor.putInt("tab", position);
                editor.apply();
            }
            @Override
            public void onTabUnselected(TabLayout.Tab tab) {
            }
            @Override
            public void onTabReselected(TabLayout.Tab tab) {
            }
        });

        binding.switchAspectRatioButton.setOnClickListener(v -> {
            int nextAspectRatioMode = prefs_photograph.getInt("aspectRatioMode", 2) - 1;
            if (nextAspectRatioMode == -1){
                nextAspectRatioMode = ASPECT_RATIO_LIST.length - 1;
            }
            int finalNextAspectRatioMode = nextAspectRatioMode;
            Functions.hideContentWithAnimation(cameraPreviewView, () -> {
                changeAspectRatio(finalNextAspectRatioMode);
            });
            switch (nextAspectRatioMode){
                case ASPECT_RATIO_FULL:
                    TextViewHint.showText(getString(R.string.hint_aspectRatio).concat(getString(R.string.hint_aspectRatio_value_fullScreen)));
                    break;
                case ASPECT_RATIO_16_9:
                    TextViewHint.showText(getString(R.string.hint_aspectRatio).concat("16:9"));
                    break;
                case ASPECT_RATIO_4_3:
                    TextViewHint.showText(getString(R.string.hint_aspectRatio).concat("4:3"));
                    break;
                case ASPECT_RATIO_1_1:
                    TextViewHint.showText(getString(R.string.hint_aspectRatio).concat(getString(R.string.hint_aspectRatio_value_1_1)));
                    break;
            }
        });

        binding.switchFacingButton.setOnClickListener(v -> {
            int newFacing = FACING_BACK;
            if (prefs_photograph.getInt("facing", FACING_BACK) == FACING_BACK){
                newFacing = FACING_FRONT;
                TextViewHint.showText(getString(R.string.hint_facing).concat(getString(R.string.hint_facing_value_front)));
            } else {
                TextViewHint.showText(getString(R.string.hint_facing).concat(getString(R.string.hint_facing_value_back)));
            }
            SharedPreferences.Editor editor = prefs_photograph.edit();
            editor.putInt("facing", newFacing);
            editor.apply();
            bindPreview(cameraProvider, newFacing);
        });
        binding.switchFlashButton.setOnClickListener(v -> {
            if (camera == null) return;

            CameraControl cameraControl = camera.getCameraControl();
            isFlashOn = !isFlashOn;

            // 启用/禁用闪光灯
            cameraControl.enableTorch(isFlashOn);

            if (isFlashOn) {
                TextViewHint.showText(getString(R.string.hint_flashLight).concat(getString(R.string.hint_flashLight_value_on)));
                binding.switchFlashButton.setIconResource(R.drawable.flash_on_24px); // 闪光灯开启图标
            } else {
                TextViewHint.showText(getString(R.string.hint_flashLight).concat(getString(R.string.hint_flashLight_value_off)));
                binding.switchFlashButton.setIconResource(R.drawable.flash_off_24px); // 闪光灯关闭图标
            }
        });

        if (envJsonObj.has("latestPhotoUri")){
            try {
                updatePreviewButton(Uri.parse(envJsonObj.getString("latestPhotoUri")));
            } catch (JSONException e) {
                throw new RuntimeException(e);
            }

        }
        binding.previewImageCardView.setOnClickListener(v -> {
            try {
                if (!envJsonObj.has("latestPhotoUri")){
                    return;
                }
                Uri photoUri = Uri.parse(envJsonObj.getString("latestPhotoUri"));

                if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                    // 使用 FileProvider 生成安全的 URI
                    photoUri = FileProvider.getUriForFile(
                            requireContext(),
                            requireContext().getPackageName() + ".fileprovider",
                            new File(photoUri.getPath())
                    );
                }

                Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.setDataAndType(photoUri, "image/*");
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                startActivity(intent);
            } catch (JSONException e) {
                throw new RuntimeException(e);
            }
        });

        SharedPreferences.Editor editor = prefs_photograph.edit();
        editor.putBoolean("collapsedSelection", false);
        editor.putBoolean("collapsedNavigation", false);
        editor.apply();
        binding.toggleSelectionButton.setOnClickListener(v -> {
            boolean isPortrait = getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT;
            boolean isCollapsed = prefs_photograph.getBoolean("collapsedSelection", false);

            if (isPortrait){
                if (isCollapsed){
                    Functions.showContentWithAnimation(binding.selectionLinear);

                    assert binding.centralConstraint != null;
                    ConstraintLayout.LayoutParams params = (ConstraintLayout.LayoutParams) binding.centralConstraint.getLayoutParams();
                    params.bottomToTop = binding.selectionLinear.getId();
                    binding.centralConstraint.setLayoutParams(params);

                    binding.toggleSelectionButton.setIconResource(R.drawable.keyboard_arrow_down_24px);
                } else {
                    Functions.hideContentWithAnimation(binding.selectionLinear);

                    assert binding.centralConstraint != null;
                    ConstraintLayout.LayoutParams params = (ConstraintLayout.LayoutParams) binding.centralConstraint.getLayoutParams();
                    assert binding.bottomButtonBlank != null;
                    params.bottomToTop = binding.bottomButtonBlank.getId();
                    binding.centralConstraint.setLayoutParams(params);

                    binding.toggleSelectionButton.setIconResource(R.drawable.keyboard_arrow_up_24px);
                }
            } else {
                if (isCollapsed){
                    binding.toggleSelectionButton.setIconResource(R.drawable.keyboard_arrow_right_24px);
                    binding.bottomButtonLinear.setOrientation(LinearLayout.HORIZONTAL);

                    assert binding.right != null;
                    binding.right.setBackgroundResource(R.color.transparent_40);

                    ConstraintLayout.LayoutParams params = (ConstraintLayout.LayoutParams) binding.right.getLayoutParams();
                    params.width = (int) (320 * getResources().getDisplayMetrics().density);
                    binding.right.setLayoutParams(params);

                    Functions.showContentWithAnimation(binding.selectionLinear);

                    binding.bottomBlank.setVisibility(View.VISIBLE);
                } else {
                    Functions.hideContentWithAnimation(binding.selectionLinear, () -> {
                        binding.toggleSelectionButton.setIconResource(R.drawable.keyboard_arrow_left_24px);
                        binding.bottomButtonLinear.setOrientation(LinearLayout.VERTICAL);

                        assert binding.right != null;
                        binding.right.setBackgroundResource(R.color.transparent);

                        if (prefs_photograph.getBoolean("collapsedNavigation", false)){
                            ConstraintLayout.LayoutParams params = (ConstraintLayout.LayoutParams) binding.right.getLayoutParams();
                            params.width = ConstraintLayout.LayoutParams.WRAP_CONTENT;
                            binding.right.setLayoutParams(params);

                            binding.bottomBlank.setVisibility(View.GONE);
                        }
                    });
                }
            }
            editor.putBoolean("collapsedSelection", !isCollapsed);
            editor.apply();
        });
        binding.toggleNavigationButton.setOnClickListener(v -> {
            assert getActivity() != null;
            BottomNavigationView nav = getActivity().findViewById(R.id.nav_view);
            boolean isCollapsed = prefs_photograph.getBoolean("collapsedNavigation", false);
            boolean isPortrait = getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT;
            if (isCollapsed){
                Functions.showContentWithAnimation(nav);
                binding.toggleNavigationButton.setIconResource(R.drawable.keyboard_arrow_down_24px);

                if (!isPortrait){
                    ConstraintLayout.LayoutParams params = (ConstraintLayout.LayoutParams) binding.right.getLayoutParams();
                    params.width = (int) (320 * getResources().getDisplayMetrics().density);
                    binding.right.setLayoutParams(params);

                    binding.bottomBlank.setVisibility(View.VISIBLE);
                }
            } else {
                Functions.hideContentWithAnimation(nav);
                binding.toggleNavigationButton.setIconResource(R.drawable.keyboard_arrow_up_24px);

                if (!isPortrait){
                    if (prefs_photograph.getBoolean("collapsedSelection", false)){
                        assert binding.right != null;
                        ConstraintLayout.LayoutParams params = (ConstraintLayout.LayoutParams) binding.right.getLayoutParams();
                        params.width = ConstraintLayout.LayoutParams.WRAP_CONTENT;
                        binding.right.setLayoutParams(params);

                        binding.bottomBlank.setVisibility(View.GONE);
                    }
                }
            }
            editor.putBoolean("collapsedNavigation", !isCollapsed);
            editor.apply();
        });

        binding.toggleZoomSliderButton.setOnClickListener(v -> {
            if (binding.zoomSlider.getVisibility() == View.VISIBLE){
                Functions.hideContentWithAnimation(binding.zoomSlider);
                //binding.zoomSlider.setVisibility(View.GONE);
            } else {
                Functions.showContentWithAnimation(binding.zoomSlider);
                //binding.zoomSlider.setVisibility(View.VISIBLE);
            }
        });

        if (prefs_photograph.getBoolean("autoRotate", false)){
            enableAutoRotate();
        } else {
            disableAutoRotate();
        }
        assert getView() != null;
        /*int notSelectedColor = MaterialColors.getColor(getView(), com.google.android.material.R.attr.colorOnSecondary);
        binding.autoRotateButton.setBackgroundColor(notSelectedColor);*/
        binding.autoRotateButton.setOnClickListener(v -> {
            if (prefs_photograph.getBoolean("autoRotate", false)){
                disableAutoRotate();
                editor.putBoolean("autoRotate", false);
            } else {
                enableAutoRotate();
                editor.putBoolean("autoRotate", true);
            }
            editor.apply();
        });
    }
    private void enableAutoRotate(){
        assert getActivity() != null;
        sensorManager = (SensorManager) getActivity().getSystemService(Context.SENSOR_SERVICE);
        if (sensorManager == null) return;
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        if (accelerometer != null) {
            TextViewHint.showText(R.string.info_sensorInit);
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_GAME);

            onInitRotatePic();
            angleQueue = new FixedSizeQueue<>(3);

            assert getView() != null;
            int selectedColor = MaterialColors.getColor(getView(), com.google.android.material.R.attr.colorOutline);
            binding.autoRotateButton.setBackgroundColor(selectedColor);

            if (binding.resetRotationButton.getVisibility() == View.VISIBLE){
                Functions.hideContentWithAnimation(binding.resetRotationButton);
            }
            //binding.rotationSlider.setActivated(false);
        } else {
            TextViewHint.showText(R.string.info_sensorInitFailed);
        }
    }
    private void disableAutoRotate(){
        onStopRotatePic();

        assert getView() != null;
        int notSelectedColor = MaterialColors.getColor(getView(), com.google.android.material.R.attr.colorOnSecondary);
        binding.autoRotateButton.setBackgroundColor(notSelectedColor);
        //binding.rotationSlider.set(true);

        if (rotationSlider_lastRotation != 0){
            if (binding.resetRotationButton.getVisibility() == View.GONE){
                Functions.showContentWithAnimation(binding.resetRotationButton);
            }
            addResetRotationListener();
        }

        if (sensorManager != null) {
            sensorManager.unregisterListener(this);
        }
    }
    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // 传感器精度变化时的回调，通常不需要特殊处理
    }
    @Override
    public void onSensorChanged(SensorEvent event) {
        // 确保是加速度传感器数据
        if (event.sensor.getType() != Sensor.TYPE_ACCELEROMETER) {
            return;
        }

        float x = event.values[0];
        float y = event.values[1];
        float z = event.values[2];

        float currentAngle = 0;
        //int orientation = getResources().getConfiguration().orientation;
        WindowManager windowManager = (WindowManager) requireActivity().getSystemService(Context.WINDOW_SERVICE);
        Display display = windowManager.getDefaultDisplay();
        int rotation = display.getRotation();
        if (rotation == Surface.ROTATION_0){
            currentAngle = (float) Math.toDegrees(Math.atan2(y, x)) - 90;
        } else if (rotation == Surface.ROTATION_90) {
            currentAngle = (float) Math.toDegrees(Math.atan2(y, x));
        } else if (rotation == Surface.ROTATION_270) {
            currentAngle = (float) Math.toDegrees(Math.atan2(y, x)) - 180;
        }
        double angle2Z = Math.toDegrees(Math.atan2(Math.sqrt(x * x + y * y), z));
        if (angle2Z < 10) return;

        float angle;
        if (angleQueue.size() == 3){
            float delta = Math.abs((Float) angleQueue.get(2) - currentAngle);
            if (delta < 60) {
                angleQueue.add(currentAngle);
            }
            if (delta < 0.4){
                return;
            } else if (delta < 5) {
                angle = ((Float) angleQueue.get(0) + (Float) angleQueue.get(1) + (Float) angleQueue.get(2)) / 3;
            } else if (delta < 30) {
                angle = (float) (0.2 * (Float) angleQueue.get(0) + 0.3 * (Float) angleQueue.get(1) + 0.5 * (Float) angleQueue.get(2));
            } else {
                angle = currentAngle;
            }
        } else {
            angle = currentAngle;
            angleQueue.add(currentAngle);
        }

        // 限制UI更新频率，避免卡顿
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastAccelerometerUpdateTime >= 7) {
            lastAccelerometerUpdateTime = currentTime;
            // 在主线程更新UI
            if (angle < -180){
                angle += 360;
            }
            float finalAngle = angle;
            getActivity().runOnUiThread(() -> {
                onRotatePic(finalAngle);
                binding.rotationSlider.setValue(finalAngle);
            });
        }
    }

    private void updatePreviewButton(Uri uri) {
        // 使用 Glide 直接加载到 ImageView，并应用 centerCrop 变换
        requireActivity().runOnUiThread(() ->
                Glide.with(requireContext())
                        .load(uri)
                        .apply(new RequestOptions().centerCrop()) // 确保中心裁剪
                        .into(binding.previewImageImageView)
        );

    }
    @SuppressLint("SwitchIntDef")
    private void changeAspectRatio(int mode){
        // 保存新的比例模式
        SharedPreferences.Editor editor = prefs_photograph.edit();
        editor.putInt("aspectRatioMode", mode);
        editor.apply();

        // 设置预览尺寸
        int orientation = getResources().getConfiguration().orientation;
        switch (mode){
            case ASPECT_RATIO_FULL:
                setPreviewSize(screenWidth, screenHeight);
                break;
            case ASPECT_RATIO_16_9:
                switch (orientation){
                    case Configuration.ORIENTATION_UNDEFINED:
                    case Configuration.ORIENTATION_PORTRAIT:
                        setPreviewSize(screenWidth, screenWidth * 16 / 9);
                        break;
                    case Configuration.ORIENTATION_LANDSCAPE:
                        setPreviewSize(screenHeight * 16 / 9, screenHeight);
                        break;
                }
                break;
            case ASPECT_RATIO_4_3:
                switch (orientation){
                    case Configuration.ORIENTATION_UNDEFINED:
                    case Configuration.ORIENTATION_PORTRAIT:
                        setPreviewSize(screenWidth, screenWidth * 4 / 3);
                        break;
                    case Configuration.ORIENTATION_LANDSCAPE:
                        setPreviewSize(screenHeight * 4 / 3, screenHeight);
                        break;
                }
                break;
            case ASPECT_RATIO_1_1:
                switch (orientation){
                    case Configuration.ORIENTATION_UNDEFINED:
                    case Configuration.ORIENTATION_PORTRAIT:
                        setPreviewSize(screenWidth, screenWidth);
                        break;
                    case Configuration.ORIENTATION_LANDSCAPE:
                        setPreviewSize(screenHeight, screenHeight);
                        break;
                }
                break;
        }
        binding.previewContainerFrameLayout.requestLayout();
        cameraPreviewView.requestLayout();

        // 重新绑定相机使用新的宽高比
        if (cameraProvider != null) {
            bindPreview(cameraProvider, prefs_photograph.getInt("facing", FACING_BACK));
        }
    }
    private void setPreviewSize(int width, int height){
        //cameraPreviewView.setVisibility(View.GONE);
        View previewContainer = binding.previewContainerFrameLayout;
        previewContainer.getLayoutParams().width = width;
        previewContainer.getLayoutParams().height = height;
        cameraPreviewView.getLayoutParams().width = width;
        cameraPreviewView.getLayoutParams().height = height;
        //Functions.showContentWithAnimation(cameraPreviewView);
    }
    private void resetRotationSlider(){
        try {
            if (selectedPresetPicsArr != null && selectedPicPosition != -1) {
                if (selectedPresetPicsArr.length() > 0) {
                    //onColorValueChange(128, 128, 128);

                    AtomicReference<JSONObject> picObj = new AtomicReference<>(selectedPresetPicsArr.getJSONObject(selectedPicPosition));

                    AtomicReference<Matrix> matrix = new AtomicReference<>(Functions.stringToMatrix(picObj.get().getString("matrix")));
                    float[] values = new float[9];
                    matrix.get().getValues(values);
                    float initialRotation = (float) Math.toDegrees(Math.atan2(values[Matrix.MSKEW_X], values[Matrix.MSCALE_X]));
                    //int progress = (int) initialRotation;
                    binding.rotationSlider.setValue(initialRotation);

                    if ((int)initialRotation != 0 && !prefs_photograph.getBoolean("autoRotate", false)){
                        if (binding.resetRotationButton.getVisibility() == View.GONE){
                            Functions.showContentWithAnimation(binding.resetRotationButton);
                        }
                        //binding.resetRotationButton.setVisibility(View.VISIBLE);
                        addResetRotationListener();
                    } else {
                        Functions.hideContentWithAnimation(binding.resetRotationButton);
                        //binding.resetRotationButton.setVisibility(View.INVISIBLE);
                    }
                }
            }
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }
    private void addResetRotationListener(){
        AtomicReference<JSONObject> picObj;
        AtomicReference<Matrix> matrix;
        try {
            picObj = new AtomicReference<>(selectedPresetPicsArr.getJSONObject(selectedPicPosition));
            matrix = new AtomicReference<>(Functions.stringToMatrix(picObj.get().getString("matrix")));
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }

        binding.resetRotationButton.setOnClickListener(v -> {
            if (!autoSelect()) {
                binding.rotationSlider.setValue(0);
                //binding.resetRotationButton.setVisibility(View.INVISIBLE);
                Functions.hideContentWithAnimation(binding.resetRotationButton);
                return;
            }
            if (prefs_photograph.getBoolean("autoRotate", false)){
                Functions.hideContentWithAnimation(binding.resetRotationButton);
                return;
            }
            try {
                picObj.set(selectedPresetPicsArr.getJSONObject(selectedPicPosition));
                matrix.set(Functions.stringToMatrix(picObj.get().getString("matrix")));
                float[] mValues = new float[9];
                matrix.get().getValues(mValues);
                float rotation = (float) Math.toDegrees(Math.atan2(mValues[Matrix.MSKEW_X], mValues[Matrix.MSCALE_X]));
                float[] midPoints = Functions.getTransformedCenter(matrix.get(), picObj.get().getInt("width"), picObj.get().getInt("height"));
                matrix.get().postRotate(rotation, midPoints[0], midPoints[1]);
                imageViewList.get(selectedPicPosition).setImageMatrix(matrix.get());
                picObj.get().put("matrix", Functions.matrixToString(matrix.get()));
                Functions.saveEnv(exDataPath, envJsonObj);
            } catch (JSONException e) {
                throw new RuntimeException(e);
            }

            binding.rotationSlider.setValue(0);
            //binding.resetRotationButton.setVisibility(View.INVISIBLE);
            Functions.hideContentWithAnimation(binding.resetRotationButton);
        });
    }

    JSONObject rotationSlider_picObj;
    ImageView rotationSlider_imageView;
    Matrix rotationSlider_matrix;
    float[] rotationSlider_midPoints;
    float rotationSlider_initialRotation; // 记录初始旋转角度
    float rotationSlider_lastRotation;

    @SuppressLint("DefaultLocale")
    private void setupRotationSliderListener(){
        binding.rotationSlider.addOnSliderTouchListener(new Slider.OnSliderTouchListener() {
            @Override
            public void onStartTrackingTouch(@NonNull Slider slider) {
                if (prefs_photograph.getBoolean("autoRotate", false)) return;
                onInitRotatePic();
            }

            @Override
            public void onStopTrackingTouch(@NonNull Slider slider) {
                if (prefs_photograph.getBoolean("autoRotate", false)) return;

                onStopRotatePic();

                if (rotationSlider_lastRotation != 0){
                    if (binding.resetRotationButton.getVisibility() == View.GONE){
                        Functions.showContentWithAnimation(binding.resetRotationButton);
                    }
                    //binding.resetRotationButton.setVisibility(View.VISIBLE);
                    addResetRotationListener();
                } else {
                    Functions.hideContentWithAnimation(binding.resetRotationButton);
                    //binding.resetRotationButton.setVisibility(View.INVISIBLE);
                }
            }
        });

        binding.rotationSlider.addOnChangeListener((slider, value, fromUser) -> {
            if (!fromUser) return;
            if (prefs_photograph.getBoolean("autoRotate", false)) return;
            onRotatePic(value);
        });
    }
    private void onInitRotatePic(){
        if (!autoSelect()) return;
        try {
            // onStartTrackingTouch() 的逻辑
            rotationSlider_picObj = selectedPresetPicsArr.getJSONObject(selectedPicPosition);
            rotationSlider_imageView = imageViewList.get(selectedPicPosition);
            rotationSlider_matrix = Functions.stringToMatrix(rotationSlider_picObj.getString("matrix"));
            rotationSlider_midPoints = Functions.getTransformedCenter(rotationSlider_matrix, rotationSlider_picObj.getInt("width"), rotationSlider_picObj.getInt("height"));

            float[] values = new float[9];
            rotationSlider_matrix.getValues(values);
            rotationSlider_initialRotation = (float) Math.toDegrees(Math.atan2(values[Matrix.MSKEW_X], values[Matrix.MSCALE_X]));

            /*if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                binding.rotationSlider.setTooltipText(Float.toString(rotationSlider_initialRotation));
            }*/
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }
    private void onStopRotatePic(){
        if (!autoSelect()) return;
        try {
            if (rotationSlider_picObj == null){
                return;
            }
            rotationSlider_picObj.put("matrix", Functions.matrixToString(rotationSlider_matrix));
            Functions.saveEnv(exDataPath, envJsonObj);
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }
    @SuppressLint("DefaultLocale")
    private void onRotatePic(float value){
        if (!autoSelect()) {
            // 注意：Slider没有setProgress方法，需要用setValue
            binding.rotationSlider.setValue(0);
            return;
        }
        try {
            // onProgressChanged() 的逻辑
            rotationSlider_matrix = Functions.stringToMatrix(rotationSlider_picObj.getString("matrix"));
            // Slider 的值是 float 类型，所以直接使用 value
            rotationSlider_matrix.postRotate(rotationSlider_initialRotation - value, rotationSlider_midPoints[0], rotationSlider_midPoints[1]);
            rotationSlider_imageView.setImageMatrix(rotationSlider_matrix);

            /*if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                binding.rotationSlider.setTooltipText(Float.toString(value));
            }*/
            rotationSlider_lastRotation = value;
            TextViewHint.showText(getString(R.string.hint_rotation).concat(String.format("%.1f", value)).concat(getString(R.string.hint_rotation_suffix)));
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private void setupPreviewEvents(){
        final boolean[] isScaling = {false};
        float[] startPoint = {0,0};
        //double overflowExtent = 0.5;
        float overflowExtent = Float.parseFloat(prefs_setting.getString("overflowExtent", "0.5f"));
        final Matrix[] startMatrix = {new Matrix()};
        final float[] startDistance = {0f};
        final float[] midPoint = {0f, 0f};

        AtomicReference<Float> scale = new AtomicReference<>((float) 1);
        AtomicReference<Float> dx = new AtomicReference<>((float) 0);
        AtomicReference<Float> dy = new AtomicReference<>((float) 0);

        binding.previewContainerFrameLayout.setOnTouchListener((v, event) -> {
            /*if (selectedPicPosition < 0 || selectedPicPosition >= imageViewList.size()) {
                return false;
            }*/
            if (!autoSelect()) return false;

            Matrix matrix;
            try {
                matrix = Functions.getPhysicsMatrix(selectedPresetPicsArr.getJSONObject(selectedPicPosition));
            } catch (JSONException e) {
                throw new RuntimeException(e);
            }
            ImageView imageView = imageViewList.get(selectedPicPosition);
            imageView.setScaleType(ImageView.ScaleType.MATRIX);

            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    startPoint[0] = event.getX();
                    startPoint[1] = event.getY();
                    startMatrix[0].set(imageView.getImageMatrix());

                    if (prefs_photograph.getBoolean("autoRotate", false)){
                        disableAutoRotate();
                    }

                    break;

                case MotionEvent.ACTION_POINTER_DOWN:
                    if (event.getPointerCount() == 2) {
                        isScaling[0] = true;
                        startDistance[0] = getDistance(event);
                        midPoint[0] = (event.getX(0) + event.getX(1)) / 2;
                        midPoint[1] = (event.getY(0) + event.getY(1)) / 2;
                        startMatrix[0].set(imageView.getImageMatrix());
                    }
                    break;

                case MotionEvent.ACTION_MOVE:
                    if (isScaling[0] && event.getPointerCount() == 2) {
                        // 缩放操作
                        float newDistance = getDistance(event);
                        if (newDistance > 10f) {
                            matrix.set(startMatrix[0]);
                            scale.set(newDistance / startDistance[0]);
                            matrix.postScale(scale.get(), scale.get(), midPoint[0], midPoint[1]);
                            applyBoundaryConstraints(matrix, imageView, overflowExtent);
                            imageView.setImageMatrix(matrix);

                            //TextViewHint.showText(getString(R.string.hint_scale).concat(scale.toString()));
                        }
                    } else if (!isScaling[0] && event.getPointerCount() == 1) {
                        // 移动操作
                        matrix.set(startMatrix[0]);
                        dx.set(event.getX() - startPoint[0]);
                        dy.set(event.getY() - startPoint[1]);
                        matrix.postTranslate(dx.get(), dy.get());
                        applyBoundaryConstraints(matrix, imageView, overflowExtent);
                        imageView.setImageMatrix(matrix);
                    }
                    break;


                case MotionEvent.ACTION_UP:
                    // 核心修改:
                    // 在任何一根手指松开后，保存当前矩阵作为新的起始矩阵
                    // 并更新单指移动的起始点
                    if (event.getPointerCount() > 1) {
                        // 只有当还有手指在屏幕上时才更新 startPoint
                        // 这是为了确保剩下的手指能够正确移动
                        int upPointerIndex = event.getActionIndex();
                        int newPointerIndex = 0;
                        if (upPointerIndex == 0) {
                            newPointerIndex = 1;
                        }

                        // 更新起始点为剩下的那个手指的位置
                        startPoint[0] = event.getX(newPointerIndex);
                        startPoint[1] = event.getY(newPointerIndex);
                        startMatrix[0].set(imageView.getImageMatrix()); // 更新起始矩阵
                    }

                    // 如果只剩下一根手指，则退出缩放模式
                    if (event.getPointerCount() == 1) {
                        isScaling[0] = false;
                    }

                    // 保存最终的变换矩阵
                    /*try {
                        JSONObject localPicObj = selectedPresetPicsArr.getJSONObject(selectedPicPosition);
                        localPicObj.put("position_x", localPicObj.getInt("position_x") + dx.get());
                        localPicObj.put("position_y", localPicObj.getInt("position_y") + dy.get());
                        localPicObj.put("scale", localPicObj.getDouble("scale") * scale.get());
                        localPicObj.put("scalePoint_x", midPoint[0]);
                        localPicObj.put("scalePoint_y", midPoint[1]);
                        Functions.saveEnv(exDataPath, envJsonObj);
                    } catch (JSONException e) {
                        throw new RuntimeException(e);
                    }*/
                    try {
                        JSONObject localPicObj = selectedPresetPicsArr.getJSONObject(selectedPicPosition);
                        Matrix currentMatrix = imageView.getImageMatrix();
                        localPicObj.put("matrix", Functions.matrixToString(currentMatrix));
                        Functions.saveEnv(exDataPath, envJsonObj);
                    } catch (JSONException e) {
                        throw new RuntimeException(e);
                    }

                    // 检查是否是点击事件
                    if (event.getAction() == MotionEvent.ACTION_UP) {
                        float moveX = Math.abs(event.getX() - startPoint[0]);
                        float moveY = Math.abs(event.getY() - startPoint[1]);
                        int touchSlop = ViewConfiguration.get(v.getContext()).getScaledTouchSlop();
                        if (moveX < touchSlop && moveY < touchSlop) {
                            performTapToFocus(event.getX(), event.getY());
                        }
                    }

                    if (prefs_photograph.getBoolean("autoRotate", false)){
                        enableAutoRotate();
                    }
                    break;
            }
            return true;
        });
    }
    private float getDistance(MotionEvent event) {
        float x = event.getX(0) - event.getX(1);
        float y = event.getY(0) - event.getY(1);
        return (float) Math.sqrt(x * x + y * y);
    }
    private void applyBoundaryConstraints(Matrix matrix, ImageView imageView, float overflowExtent) {
        if (imageView.getDrawable() == null) return;

        // 获取图片的原始尺寸
        int drawableWidth = imageView.getDrawable().getIntrinsicWidth();
        int drawableHeight = imageView.getDrawable().getIntrinsicHeight();

        // 获取当前变换后的图片边界
        float[] values = new float[9];
        matrix.getValues(values);
        float scale = values[Matrix.MSCALE_X];
        float transX = values[Matrix.MTRANS_X];
        float transY = values[Matrix.MTRANS_Y];

        // 计算变换后的图片尺寸
        float scaledWidth = drawableWidth * scale;
        float scaledHeight = drawableHeight * scale;

        // 获取父容器尺寸
        int parentWidth = binding.previewContainerFrameLayout.getWidth();
        int parentHeight = binding.previewContainerFrameLayout.getHeight();

        // 计算允许的最大和最小偏移
        float minTransX = -scaledWidth * overflowExtent;
        float minTransY = -scaledHeight * overflowExtent;
        float maxTransX = parentWidth - scaledWidth * (1 - overflowExtent);
        float maxTransY = parentHeight - scaledHeight * (1 - overflowExtent);

        // 应用边界约束
        if (transX < minTransX) transX = minTransX;
        if (transY < minTransY) transY = minTransY;
        if (transX > maxTransX) transX = maxTransX;
        if (transY > maxTransY) transY = maxTransY;

        // 更新矩阵
        values[Matrix.MTRANS_X] = transX;
        values[Matrix.MTRANS_Y] = transY;
        matrix.setValues(values);
    }

    private void loadPicsToScreen() {
        handler.removeCallbacks(updateUIRunnable);
        executor.execute(() -> {
            try {
                JSONArray localScreenPresetsArr = envJsonObj.getJSONArray("screenPresets");
                /*long currentPresetId = envJsonObj.getLong("currentPhotographSelectedPresetId");

                for (int i = 0; i < localScreenPresetsArr.length(); i++) {
                    JSONObject preset = localScreenPresetsArr.getJSONObject(i);
                    if (currentPresetId == preset.getLong("id")) {
                        selectedPresetPicsArr = preset.getJSONArray("pics");
                        break;
                    }
                }*/
                int pos = Functions.matchedPositionOfId(localScreenPresetsArr, envJsonObj.getLong("currentPhotographSelectedPresetId"));
                JSONObject presetObj = localScreenPresetsArr.getJSONObject(pos);
                selectedPresetPicsArr = presetObj.getJSONArray("pics");
            } catch (JSONException e) {
                // 在后台线程中捕获异常，并处理
                e.printStackTrace();
                handler.post(() -> {
                    // 如果需要，可以在主线程上显示错误提示
                    // Toast.makeText(getContext(), "JSON解析失败", Toast.LENGTH_SHORT).show();
                });
                return; // 结束线程任务
            }
            handler.post(updateUIRunnable);
        });
    }

    /**
     * 设置“将selectedPresetPicsArr的每一项生成imageView并添加到picsFrameLayout， 完成后设置一系列监听器”的runnable
     */
    private void setupUpdateImageViewRunnable(){
        updateUIRunnable = new Runnable() {
            @Override
            public void run() {
                synchronized (imageViewList) {
                    binding.picsFrameLayout.removeAllViews();
                    imageViewList.clear();
                    try {
                        for (int i = 0; i < selectedPresetPicsArr.length(); i++) {
                            JSONObject localPicObj = selectedPresetPicsArr.getJSONObject(i);

                            Uri uri = Uri.parse(localPicObj.getString("uri"));
                            if (!Functions.isUriFileExists(getContext(), uri)){
                                Toast.makeText(getContext(), R.string.info_picUriNotExists, Toast.LENGTH_SHORT).show();
                                continue;
                            }

                            ImageView tempImageView = new ImageView(getContext());
                            tempImageView.setScaleType(ImageView.ScaleType.MATRIX);
                            tempImageView.setImageURI(uri);
                            tempImageView.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
                                @Override
                                public boolean onPreDraw() {
                                    tempImageView.getViewTreeObserver().removeOnPreDrawListener(this);
                                    tempImageView.setImageMatrix(Functions.getPhysicsMatrix(localPicObj));
                                    tempImageView.setColorFilter(Functions.getColorFilter(localPicObj));
                                    /*try {
                                        tempImageView.setColorFilter(Functions.getColorMatrixFilter(localPicObj.getDouble("brightness")));
                                    } catch (JSONException e) {
                                        throw new RuntimeException(e);
                                    }*/
                                    return true;
                                }
                            });
                            binding.picsFrameLayout.addView(tempImageView);
                            imageViewList.add(tempImageView);
                        }
                    } catch (JSONException e) {
                        throw new RuntimeException(e);
                    }
                    //Collections.reverse(imageViewList);
                    resetRotationSlider();

                    setupButtonListeners();
                    binding.captureButton.setOnClickListener(v -> onCapture());
                    //setupBrightnessSeekBarListener();
                    setupRotationSliderListener();

                    setupColorOptionDialogListener();

                    setupPreviewEvents();
                }
            }
        };
    }

    private void onCapture(){
        if (selectedPresetPicsArr == null || selectedPicPosition == -1){
            //Toast.makeText(requireContext(), "_not selected, returning.", Toast.LENGTH_SHORT).show();
            return;
        }

        if (prefs_photograph.getBoolean("autoRotate", false)){
            onStopRotatePic();
            onInitRotatePic();
        }

        String filename = Functions.formatTimeInString(prefs_setting.getString("photoNameFormat", "anicam_{yyyyMMdd_HHmmss}"));
        //File tempFile = new File(Functions.getAppDCIMDirectory(getContext(), "_Anicam"), "temp_".concat(filename).concat(".jpg"));
        File tempFile = new File(requireContext().getCacheDir(), "temp_".concat(filename).concat(".jpg"));

        if (prefs_setting.getBoolean("playgroundMode", false)) {
            Bitmap bitmap;
            boolean isPortrait = getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT;
            switch (prefs_photograph.getInt("aspectRatioMode", ASPECT_RATIO_4_3)) {
                case ASPECT_RATIO_4_3:
                    if (isPortrait){
                        bitmap = Bitmap.createBitmap(3072, 4096, Bitmap.Config.ARGB_8888);
                    } else {
                        bitmap = Bitmap.createBitmap(4096, 3072, Bitmap.Config.ARGB_8888);
                    }
                    break;
                case ASPECT_RATIO_16_9:
                    if (isPortrait){
                        bitmap = Bitmap.createBitmap(2304, 4096, Bitmap.Config.ARGB_8888);
                    } else {
                        bitmap = Bitmap.createBitmap(4096, 2304, Bitmap.Config.ARGB_8888);
                    }
                    break;
                default:
                    bitmap = Bitmap.createBitmap(4096, 4096, Bitmap.Config.ARGB_8888);
            }

            Canvas canvas = new Canvas(bitmap);
            canvas.drawColor(Color.BLACK);
            try {
                FileOutputStream outputStream = new FileOutputStream(tempFile);
                bitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream);
                outputStream.flush();
                outputStream.close();

                mainPhotoProcessing(tempFile, filename);
            } catch (IOException e) {
                e.printStackTrace();
            }
            return;
        }

        ImageCapture.OutputFileOptions outputFileOptions = new ImageCapture.OutputFileOptions.Builder(tempFile).build();
        imageCapture.takePicture(outputFileOptions, cameraExecutor, new ImageCapture.OnImageSavedCallback() {
            @Override
            public void onImageSaved(@NonNull ImageCapture.OutputFileResults outputFileResults) {
                // 图片保存成功
                /*requireActivity().runOnUiThread(() ->
                        Toast.makeText(requireContext(), "Success", Toast.LENGTH_SHORT).show());*/
                mainPhotoProcessing(tempFile, filename);
            }

            @Override
            public void onError(@NonNull ImageCaptureException exception) {
                // 图片保存失败
                requireActivity().runOnUiThread(() ->
                        Toast.makeText(requireContext(), getString(R.string.info_captureFailed), Toast.LENGTH_SHORT).show());
            }
        });
    }
    private void mainPhotoProcessing(File tempFile, String filename){
        if (prefs_setting.getBoolean("saveOrigin", false)){
            Functions.copyInteriorToStorage(getContext(), tempFile, getString(R.string.saveFolderName), filename.concat("_origin.jpg"), Environment.DIRECTORY_DCIM);
        }

        try {
            int screenOrientation = getResources().getConfiguration().orientation;
            int exifOrientation = Integer.parseInt(Functions.getExifData(getContext(), Uri.fromFile(tempFile), new String[]{ExifInterface.TAG_ORIENTATION})[0]);
            Bitmap orgBitmap = BitmapFactory.decodeStream(new FileInputStream(tempFile));
            Bitmap clipedBitmap = clipBitmap(orgBitmap, exifOrientation, screenOrientation);
            Bitmap mergedBitmap = mergeWithBackground(clipedBitmap, selectedPresetPicsArr, screenOrientation);
            Bitmap outputBitmap = mergedBitmap;
            if (prefs_setting.getBoolean("timeWatermark", false)){
                outputBitmap = addWatermark(mergedBitmap);
            }
            File outputFile = Functions.saveBitmapToInteriorPathWithFile(getContext(), outputBitmap, requireContext().getCacheDir(), filename.concat(".jpg"));

            if (prefs_setting.getBoolean("addEXIF", true) && !prefs_setting.getBoolean("playgroundMode", false)){
                String[] exifData = Functions.getExifData(getContext(), Uri.fromFile(tempFile), Functions.allExifKeys);
                if (prefs_setting.getBoolean("addLocation", true) && mListener.getLocationServiceAvailability()){
                    mListener.startLocationUpdates(new MainActivity.LocationRequestCallback() {
                        @Override
                        public void onLocationReceived(Location location) {
                            assert outputFile != null;
                            Functions.addExifData(getContext(), outputFile, Functions.allExifKeys, exifData, location);
                            //Functions.copyInteriorToDCIM(getContext(), outputFile, "_Anicam", filename);
                            TextViewHint.showText(location.toString());
                            onProcessedPhoto(outputFile, filename.concat(".jpg"), new String[]{"EXIF", getString(R.string.content_location)});
                            /*requireActivity().runOnUiThread(() ->
                                    Toast.makeText(requireContext(), "finished processing", Toast.LENGTH_SHORT).show());*/
                        }
                        @Override
                        public void onLocationTimeout() {
                            assert outputFile != null;
                            Functions.addExifData(getContext(), outputFile, Functions.allExifKeys, exifData, null);
                            //Functions.copyInteriorToDCIM(getContext(), outputFile, "_Anicam", filename);
                            onProcessedPhoto(outputFile, filename.concat(".jpg"), new String[]{"EXIF"});
                            //Toast.makeText(requireContext(), getString(R.string.info_locationTimeout), Toast.LENGTH_SHORT).show();
                            TextViewHint.showText(R.string.info_locationTimeout);
                        }
                    });
                } else {
                    assert outputFile != null;
                    Functions.addExifData(getContext(), outputFile, Functions.allExifKeys, exifData, null);
                    onProcessedPhoto(outputFile, filename.concat(".jpg"), new String[]{"EXIF"});
                }
            } else {
                onProcessedPhoto(outputFile, filename.concat(".jpg"), new String[]{""});
            }
            tempFile.delete();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void onProcessedPhoto(File outputFile, String filename, String[] options) {
        Uri targetUri = Functions.copyInteriorToStorage(getContext(), outputFile, getString(R.string.saveFolderName), filename, Environment.DIRECTORY_DCIM);

        // 确保临时文件被删除
        if (outputFile != null && outputFile.exists()) {
            boolean deleted = outputFile.delete();
            if (!deleted) {
                Log.e("FileDelete", "Failed to delete temporary file: " + outputFile.getAbsolutePath());
                // 尝试强制删除
                outputFile.deleteOnExit();
            }
        }

        // 对于 Android 7.0+，需要使用 FileProvider 提供安全的 URI
        Uri shareUri = targetUri;
        try {
            // 只有对于 file:// URI 才需要使用 FileProvider
            if ("file".equals(targetUri.getScheme())) {
                shareUri = FileProvider.getUriForFile(
                        requireContext(),
                        requireContext().getPackageName() + ".fileprovider",
                        new File(Objects.requireNonNull(targetUri.getPath()))
                );
            }
        } catch (Exception e) {
            Log.e("FileProvider", "Error creating FileProvider URI", e);
            // 即使 FileProvider 失败，也继续使用原始 URI
            shareUri = targetUri;
        }

        if (shareUri == null) {
            TextViewHint.showText(R.string.info_fileSaveFailed);
            return;
        }

        // 发送广播时授予读取权限
        try {
            Intent intent = new Intent(android.hardware.Camera.ACTION_NEW_PICTURE, shareUri);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            requireContext().sendBroadcast(intent);
        } catch (Exception e) {
            Log.e("Broadcast", "Failed to send broadcast", e);
        }

        String textTip = "";
        for (String t : options) {
            textTip = textTip.concat(t).concat(", ");
        }
        if (!textTip.isEmpty()) {
            textTip = textTip.substring(0, textTip.length() - 2);
        }
        String finalTextTip = textTip;
        requireActivity().runOnUiThread(() -> {
            if (finalTextTip.isEmpty()){
                TextViewHint.showText(R.string.info_fileSaved);
            } else {
                TextViewHint.showText(getString(R.string.info_fileSavedWithExif).concat(finalTextTip));
            }
        });
        try {
            envJsonObj.put("latestPhotoUri", targetUri.toString());
            Functions.saveEnv(exDataPath, envJsonObj);
        } catch (JSONException e) {
            Log.e("JSON", "Failed to save latest photo URI", e);
        }

        updatePreviewButton(targetUri);
    }
    private Bitmap addWatermark(Bitmap bitmap){
        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint();
        paint.setColor(Color.parseColor(prefs_setting.getString("timeWatermark_color", "yellow")));
        paint.setTextSize(prefs_setting.getInt("timeWatermark_size", 40));
        String text = Functions.formatTimeInString(prefs_setting.getString("timeWatermark_format", "{yyyy/MM/dd HH:mm:ss}, Created By Anicam"));
        canvas.drawText(text, 20, bitmap.getHeight() - 40, paint);
        return bitmap;
    }
    private Bitmap clipBitmap(Bitmap orgBitmap, int exifOrientation, int screenOrientation){
        try {
            Bitmap clipedBitmap;
            /*boolean isPortrait = true;
            if (orgBitmap.getHeight() < orgBitmap.getWidth()){
                isPortrait = false;
            }*/

            boolean isScreenPortrait = screenOrientation == Configuration.ORIENTATION_PORTRAIT;
            boolean isImagePortrait = orgBitmap.getWidth() < orgBitmap.getHeight();
            if (isScreenPortrait != isImagePortrait){
                //orgBitmap
                int degree = 0;
                switch (exifOrientation){
                    case ExifInterface.ORIENTATION_ROTATE_180:
                        degree = 180;
                        break;
                    case ExifInterface.ORIENTATION_ROTATE_270:
                        degree = 270;
                        break;
                    case ExifInterface.ORIENTATION_ROTATE_90:
                        degree = 90;
                        break;
                }
                orgBitmap = adjustPhotoRotation(orgBitmap, degree);
            }

            if (prefs_photograph.getInt("facing", FACING_BACK) == FACING_FRONT){
                Matrix matrix = new Matrix();
                matrix.postScale(-1f, 1f); // 水平镜像翻转
                orgBitmap = Bitmap.createBitmap(orgBitmap, 0, 0, orgBitmap.getWidth(), orgBitmap.getHeight(), matrix, true);
            }

            switch (prefs_photograph.getInt("aspectRatioMode", ASPECT_RATIO_4_3)){
                case ASPECT_RATIO_1_1:
                    if (isScreenPortrait){
                        clipedBitmap = Bitmap.createBitmap(orgBitmap,
                                0,
                                (orgBitmap.getHeight() - orgBitmap.getWidth()) / 2,
                                orgBitmap.getWidth(),
                                orgBitmap.getWidth()
                        );
                    } else {
                        clipedBitmap = Bitmap.createBitmap(orgBitmap,
                                (orgBitmap.getWidth() - orgBitmap.getHeight()) / 2,
                                0,
                                orgBitmap.getHeight(),
                                orgBitmap.getHeight()
                        );
                    }
                    break;
                case ASPECT_RATIO_FULL:
                    float aspectRatio = (float)screenWidth / (float)screenHeight;
                    if (isScreenPortrait){
                        int newWidth = (int) (orgBitmap.getHeight() * aspectRatio);
                        clipedBitmap = Bitmap.createBitmap(orgBitmap,
                                ((orgBitmap.getWidth() - newWidth) / 2),
                                0,
                                newWidth,
                                orgBitmap.getHeight()
                        );
                    } else {
                        int newHeight = (int) (orgBitmap.getWidth() / aspectRatio);
                        clipedBitmap = Bitmap.createBitmap(orgBitmap,
                                0,
                                (orgBitmap.getHeight() - newHeight) / 2,
                                orgBitmap.getWidth(),
                                newHeight
                        );
                    }
                    break;
                default:
                    clipedBitmap = orgBitmap;
            }
            return clipedBitmap;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public Bitmap adjustPhotoRotation(Bitmap bitmap, final int orientationDegree) {
        Matrix matrix = new Matrix();
        matrix.setRotate(orientationDegree, (float) bitmap.getWidth() / 2, (float) bitmap.getHeight() / 2);
        try {
            return Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
        } catch (OutOfMemoryError ex) {
            Log.e("adjustPhotoRotation", "OutOfMemoryError", ex);
        }
        return null;
    }
    private Bitmap mergeWithBackground(Bitmap orgBitmap, JSONArray picArr, int screenOrientation) {
        try {
            float scale;
            if (screenOrientation == Configuration.ORIENTATION_PORTRAIT){
                scale = (float) orgBitmap.getWidth() / screenWidth;
            } else {
                scale = (float) orgBitmap.getHeight() / screenHeight;
            }

            Bitmap mutableBitmap = orgBitmap.copy(Bitmap.Config.ARGB_8888, true);
            for (int i = 0; i < picArr.length(); i++) {
                JSONObject currentPicObj = picArr.getJSONObject(i);
                Uri uri = Uri.parse(currentPicObj.getString("uri"));
                Bitmap picBitmap = BitmapFactory.decodeStream(requireContext().getContentResolver().openInputStream(uri));
                Matrix picMatrix = Functions.stringToMatrix(currentPicObj.getString("matrix"));

                Log.d("PhotoDebug", "User Matrix: " + picMatrix.toShortString());

                picMatrix.postScale(scale, scale, 0 ,0);

                Log.d("PhotoDebug", "Final Matrix for drawing: " + picMatrix.toShortString());

                Canvas canvas = new Canvas(mutableBitmap);
                Paint paint = new Paint();
                paint.setColorFilter(Functions.getColorFilter(currentPicObj));
                canvas.drawBitmap(picBitmap, picMatrix, paint);
            }
            return mutableBitmap;
        } catch (JSONException | FileNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    private void setupColorOptionDialogListener(){
        binding.setColorOptionsButton.setOnClickListener(v -> {
            if (!autoSelect()) return;
            BottomSheetDialog dialog = new BottomSheetDialog(requireContext());
            View dialogView = getLayoutInflater().inflate(R.layout.dialog_switch_colormatrix, null);
            dialog.setContentView(dialogView);

            if (dialog.getWindow() != null) {
                WindowManager.LayoutParams params = dialog.getWindow().getAttributes();
                params.dimAmount = 0.0f;
                dialog.getWindow().setAttributes(params);
            }

            int hueProgress = 128;
            int saturationProgress = 128;
            int luminanceProgress = 128;
            try {
                JSONObject colorObj = selectedPresetPicsArr.getJSONObject(selectedPicPosition).getJSONObject("colors");
                hueProgress = colorObj.getInt("hue");
                saturationProgress = colorObj.getInt("saturation");
                luminanceProgress = colorObj.getInt("luminance");
            } catch (JSONException e) {
                //throw new RuntimeException(e);
            }
            Slider hueSlider = dialogView.findViewById(R.id.hue_slider);
            EditText hueEditText = dialogView.findViewById(R.id.hueValue_textView);
            hueEditText.setText(String.valueOf(hueProgress));
            hueSlider.setValue(hueProgress);
            hueSlider.addOnChangeListener((slider, value, fromUser) -> {
                // 将进度转换为缩放比例 (0.1 到 2.0)
                /*float scale = 0.1f + (progress / 100f) * 1.9f;
                updateTransformations(getCurrentAngle(), scale);*/
                hueEditText.setText(String.valueOf(value));
                onColorValueChange(value, -1, -1);
            });
            hueEditText.setOnEditorActionListener((TextView textView, int actionId, KeyEvent keyEvent) -> {
                float value = Float.parseFloat(textView.getText().toString());
                hueSlider.setValue(value);
                onColorValueChange(value, -1, -1);
                return false;
            });

            Slider saturationSlider = dialogView.findViewById(R.id.saturation_slider);
            EditText saturationEditText = dialogView.findViewById(R.id.saturationValue_textView);
            saturationEditText.setText(String.valueOf(saturationProgress));
            saturationSlider.setValue(saturationProgress);
            saturationSlider.addOnChangeListener((slider, value, fromUser) -> {
                // 将进度转换为缩放比例 (0.1 到 2.0)
                /*float scale = 0.1f + (progress / 100f) * 1.9f;
                updateTransformations(getCurrentAngle(), scale);*/
                saturationEditText.setText(String.valueOf(value));
                onColorValueChange(-1, value, -1);
            });
            saturationEditText.setOnEditorActionListener((TextView textView, int actionId, KeyEvent keyEvent) -> {
                float value = Float.parseFloat(textView.getText().toString());
                saturationSlider.setValue(value);
                onColorValueChange(-1, value, -1);
                return false;
            });

            Slider luminanceSlider = dialogView.findViewById(R.id.luminance_slider);
            EditText luminanceEditText = dialogView.findViewById(R.id.luminanceValue_textView);
            luminanceEditText.setText(String.valueOf(luminanceProgress));
            luminanceSlider.setValue(luminanceProgress);
            luminanceSlider.addOnChangeListener((slider, value, fromUser) -> {
                // 将进度转换为缩放比例 (0.1 到 2.0)
                /*float scale = 0.1f + (progress / 100f) * 1.9f;
                updateTransformations(getCurrentAngle(), scale);*/
                luminanceEditText.setText(String.valueOf(value));
                onColorValueChange(-1, -1, value);
            });
            luminanceEditText.setOnEditorActionListener((TextView textView, int actionId, KeyEvent keyEvent) -> {
                float value = Float.parseFloat(textView.getText().toString());
                luminanceSlider.setValue(value);
                onColorValueChange(-1, -1, value);
                return false;
            });

            dialogView.findViewById(R.id.reset_button).setOnClickListener(view -> {
                hueEditText.setText(String.valueOf(128));
                hueSlider.setValue(128);
                saturationEditText.setText(String.valueOf(128));
                saturationSlider.setValue(128);
                luminanceEditText.setText(String.valueOf(128));
                luminanceSlider.setValue(128);
                onColorValueChange(128, 128, 128);
            });

            dialog.show();
        });
    }
    private void onColorValueChange(float mHueProgress, float mSaturationProgress, float mLuminanceProgress){
        try {
            JSONObject colorObj = selectedPresetPicsArr.getJSONObject(selectedPicPosition).getJSONObject("colors");
            float hueProgress = (float) colorObj.getDouble("hue");
            float saturationProgress = (float) colorObj.getDouble("saturation");
            float luminanceProgress = (float) colorObj.getDouble("luminance");
            if (mHueProgress != -1){
                hueProgress = mHueProgress;
                colorObj.put("hue", hueProgress);
            } else if (mSaturationProgress != -1) {
                saturationProgress = mSaturationProgress;
                colorObj.put("saturation", saturationProgress);
            } else if (mLuminanceProgress != -1) {
                luminanceProgress = mLuminanceProgress;
                colorObj.put("luminance", luminanceProgress);
            }
            ImageView imageView = imageViewList.get(selectedPicPosition);
            imageView.setColorFilter(Functions.getColorFilterByOptions(hueProgress, saturationProgress, luminanceProgress));

            Functions.saveEnv(exDataPath, envJsonObj);
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }

    }

    /**
     * 将libJsonArr添加到libSwitchRecyclerView中，并设置单击回调：保存currentPhotographSelectedLibId、env并调用setPicDisplay
     */
    private void refreshLibRecycler(){
        RecyclerView libSwitchRecyclerView = binding.libSwitchRecyclerView;
        LinearLayoutManager layoutManager = new LinearLayoutManager((requireContext()), LinearLayoutManager.VERTICAL, false);
        libSwitchRecyclerView.setLayoutManager(layoutManager);

        PhotographLibListAdapter.OnItemClickListener itemClickListener = position -> {
            try {
                JSONObject localJsonLibObj = libJsonArr.getJSONObject(position);
                if (envJsonObj.has("currentPhotographSelectedLibId")){
                    if (envJsonObj.getLong("currentPhotographSelectedLibId") == localJsonLibObj.getLong("id")){
                        return;
                    }
                }
                envJsonObj.put("currentPhotographSelectedLibId", localJsonLibObj.getLong("id"));
                Functions.saveEnv(exDataPath, envJsonObj);
                setPicDisplay(localJsonLibObj);
                if (photographLibListAdapter != null) {
                    photographLibListAdapter.setSelectedPosition(position);
                }
            } catch (JSONException e) {
                throw new RuntimeException(e);
            }
        };

        photographLibListAdapter = new PhotographLibListAdapter(libJsonArr, itemClickListener, null);
        libSwitchRecyclerView.setAdapter(photographLibListAdapter);
    }

    /**
     * 当env中没有screenPresets项时，主动添加并添加默认预设
     */
    private void initializePresetDefault(){
        try {
            if (!envJsonObj.has("screenPresets")){
                envJsonObj.put("screenPresets", new JSONArray());
            }
            JSONArray localScreenPresetsArr = envJsonObj.getJSONArray("screenPresets");
            if (localScreenPresetsArr.length() == 0){
                JSONObject defaultPicsObj = new JSONObject();
                defaultPicsObj.put("id", 0);
                defaultPicsObj.put("name", getString(R.string.content_defaultPresetName));
                defaultPicsObj.put("pics", new JSONArray());
                localScreenPresetsArr.put(defaultPicsObj);
            } else {
                for (int i = 0; i < localScreenPresetsArr.length(); i++) {
                    JSONArray presetPicsArr = envJsonObj.getJSONArray("screenPresets").getJSONObject(i).getJSONArray("pics");
                    int length = presetPicsArr.length();
                    /*for (int j = 0; j < length; j++) {
                        Uri currentPicUri = Uri.parse(presetPicsArr.getJSONObject(j).getString("uri"));
                        if (!Functions.isUriFileExists(getContext(), currentPicUri)){
                            localScreenPresetsArr.remove(j);
                            j--;
                            length--;
                        }
                    }*/
                    int j = 0;
                    while (j < length){
                        Uri currentPicUri = Uri.parse(presetPicsArr.getJSONObject(j).getString("uri"));
                        if (!Functions.isUriFileExists(getContext(), currentPicUri)){
                            TextViewHint.showText(getString(R.string.info_picAutoDeleted_prefix)
                                    .concat(presetPicsArr.getJSONObject(j).getString("name"))
                                    .concat(getString(R.string.info_picAutoDeleted_suffix))
                            );
                            presetPicsArr.remove(j);
                            length--;
                        } else {
                            j++;
                        }
                    }
                }
            }
            //envJsonObj.put("screenPresets", localScreenPresetsArr);
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 自动高亮lib选择项并使用setPicDisplay显示该项
     */
    private void setLibSelection(){
        try {
            if (!envJsonObj.has("currentPhotographSelectedLibId") || photographLibListAdapter == null || libJsonArr == null)
                return;
            int pos = Functions.matchedPositionOfId(libJsonArr, envJsonObj.getLong("currentPhotographSelectedLibId"));
            if (pos == -1) return;
            setPicDisplay(libJsonArr.getJSONObject(pos));
            photographLibListAdapter.setSelectedPosition(pos);
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 自动高亮preset选择项并使用setScreenPicsDisplay显示该项
     */
    public void setPresetsSelection(){
        try {
            JSONArray localScreenPresetsArr = envJsonObj.getJSONArray("screenPresets");
            if (!envJsonObj.has("currentPhotographSelectedPresetId")){
                envJsonObj.put("currentPhotographSelectedPresetId", 0);
            }
            int pos = Functions.matchedPositionOfId(localScreenPresetsArr, envJsonObj.getLong("currentPhotographSelectedPresetId"));
            setScreenPicsDisplay(localScreenPresetsArr.getJSONObject(pos).getJSONArray("pics"));
            photographPresetListAdapter.setSelectedPosition(pos);
            /*setScreenPicsDisplay(localScreenPresetsArr.getJSONObject(0).getJSONArray("pics"));
            photographPresetListAdapter.setSelectedPosition(0);*/

            if (selectedPicPosition != -1){
                screenPicListAdapter.setSelectedPosition(selectedPicPosition);
            }
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }

    private void setPicDisplay(JSONObject libObj){
        RecyclerView picSwitchRecyclerView = binding.picSwitchRecyclerView;
        LinearLayoutManager layoutManager = new LinearLayoutManager((requireContext()), LinearLayoutManager.HORIZONTAL, false);
        picSwitchRecyclerView.setLayoutManager(layoutManager);
        PhotographPicListAdapter.OnItemClickListener itemClickListener = position -> {
            try {
                JSONArray localPicsJsonArr = envJsonObj.getJSONArray("screenPresets").getJSONObject(0).getJSONArray("pics");
                if (localPicsJsonArr.length() == 0){
                    addPicToScreenTab(libObj.getJSONArray("pic").getJSONObject(position));
                } else {
                    JSONObject tempPicObj = new JSONObject(libObj.getJSONArray("pic").getJSONObject(position).toString());

                    if (!Functions.isUriFileExists(getContext(), Uri.parse(tempPicObj.getString("uri")))){
                        Toast.makeText(getContext(), R.string.info_picUriNotExists, Toast.LENGTH_SHORT).show();
                        return;
                    }

                    Functions.setDefaultMatrixValues(tempPicObj, null, requireContext().getContentResolver());
                    if (selectedPicPosition == -1){
                        selectedPicPosition = 0;
                    }
                    JSONArray localScreenPresetsArr = envJsonObj.getJSONArray("screenPresets");
                    /*for (int i = 0; i < localScreenPresetsArr.length(); i++) {
                        if (envJsonObj.getLong("currentPhotographSelectedPresetId") == localScreenPresetsArr.getJSONObject(i).getLong("id")){
                            //tempPicObj.put("matrix", localScreenPresetsArr.getJSONObject(i).getJSONArray("pics").getJSONObject(selectedPicPosition).getString("matrix"));
                            Functions.setDefaultMatrixValues(tempPicObj,
                                    localScreenPresetsArr.getJSONObject(i).getJSONArray("pics").getJSONObject(selectedPicPosition),
                                    getContext().getContentResolver());
                            break;
                        }
                    }*/

                    Functions.setDefaultMatrixValues(tempPicObj,
                            localScreenPresetsArr.getJSONObject(0).getJSONArray("pics").getJSONObject(selectedPicPosition),
                            requireContext().getContentResolver());

                    int pos = Functions.matchedPositionOfId(localScreenPresetsArr, envJsonObj.getLong("currentPhotographSelectedPresetId"));
                    if (pos != 0){
                        onPresetItemClick(0, false);
                    }

                    localPicsJsonArr.put(selectedPicPosition, tempPicObj);
                }
                loadPicsToScreen();
                Functions.saveEnv(exDataPath, envJsonObj);
                //refreshPresetListRecycler();
            } catch (JSONException e) {
                throw new RuntimeException(e);
            }
        };
        PhotographPicListAdapter.OnItemLongClickListener itemLongClickListener = (view, position) -> {
            try {
                addPicToScreenTab(libObj.getJSONArray("pic").getJSONObject(position));
                //refreshPresetListRecycler();
                Functions.saveEnv(exDataPath, envJsonObj);
                loadPicsToScreen();
            } catch (JSONException e) {
                throw new RuntimeException(e);
            }
        };

        try {
            PhotographPicListAdapter adapter = new PhotographPicListAdapter(libObj.getJSONArray("pic"), itemClickListener, itemLongClickListener, false);
            picSwitchRecyclerView.setAdapter(adapter);
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }

    }

    public void addPicToScreenTab(JSONObject picObj){
        Uri picUri;
        long picId;
        String picName;
        try {
            picUri = Uri.parse(picObj.getString("uri"));
            picId = picObj.getLong("id");
            picName = picObj.getString("name");
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
        if (picUri == null || !Functions.isUriFileExists(getContext(), picUri)){
            Toast.makeText(getContext(), getString(R.string.info_picUriNotExists), Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            JSONObject tempScreenPicObj = new JSONObject();
            tempScreenPicObj.put("id", picId);
            tempScreenPicObj.put("name", picName);
            tempScreenPicObj.put("uri", picUri);
            Functions.setDefaultMatrixValues(tempScreenPicObj, null, requireContext().getContentResolver());

            envJsonObj.getJSONArray("screenPresets").getJSONObject(0).getJSONArray("pics").put(tempScreenPicObj);
            refreshPresetListRecycler(false);
            Functions.saveEnv(exDataPath, envJsonObj);
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 读取env的screenPresets向screenPresetRecyclerView中添加，设置单击与长按回调，当env中有currentPhotographSelectedPresetId时自动调用onPresetItemClick进行选择
     * @param isResetPositionNeeded 是否重置选中的图片到0，为自动调用onPresetItemClick时传递给该函数的选项
     */
    private void refreshPresetListRecycler(boolean isResetPositionNeeded){
        RecyclerView screenPresetRecyclerView = binding.screenPresetRecyclerView;
        LinearLayoutManager layoutManager = new LinearLayoutManager((requireContext()), LinearLayoutManager.VERTICAL, false);
        screenPresetRecyclerView.setLayoutManager(layoutManager);

        PhotographLibListAdapter.OnItemClickListener itemClickListener = position -> onPresetItemClick(position, true);

        PhotographLibListAdapter.OnItemLongClickListener itemLongClickListener = (view, position) -> {
            PopupMenu popupMenu = new PopupMenu(view.getContext(), view);
            popupMenu.getMenu().add(0, MENU_COPY, 0, getString(R.string.menu_copy));
            if (position != 0){
                popupMenu.getMenu().add(0, MENU_EDIT, 0, getString(R.string.menu_edit));
                popupMenu.getMenu().add(0, MENU_DELETE, 0, getString(R.string.menu_delete));
            }
            popupMenu.setOnMenuItemClickListener(item -> handleLibMenuAction(position, item.getItemId()));
            popupMenu.show();
        };

        try {
            photographPresetListAdapter = new PhotographLibListAdapter(envJsonObj.getJSONArray("screenPresets"), itemClickListener, itemLongClickListener);
            screenPresetRecyclerView.setAdapter(photographPresetListAdapter);

            /*if (envJsonObj.getJSONArray("screenPresets").getJSONObject(0).getJSONArray("pics").length() > 0){
                selectedPicPosition = 0;
                screenPicListAdapter.setSelectedPosition(0);
            }*/
            if (envJsonObj.has("currentPhotographSelectedPresetId")){
                JSONArray localScreenPresetsArr = envJsonObj.getJSONArray("screenPresets");
                int pos = Functions.matchedPositionOfId(localScreenPresetsArr, envJsonObj.getLong("currentPhotographSelectedPresetId"));
                onPresetItemClick(pos, isResetPositionNeeded);
                photographPresetListAdapter.setSelectedPosition(pos);
            } else {
                //photographPresetListAdapter.setSelectedPosition(0);
                onPresetItemClick(0, isResetPositionNeeded);
                selectedPicPosition = 0;
                screenPicListAdapter.setSelectedPosition(0);
            }
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }

    }
    private void onPresetItemClick(int position, boolean isResetPositionNeeded){
        try {
            JSONObject localJsonPresetObj = envJsonObj.getJSONArray("screenPresets").getJSONObject(position);
            /*if (envJsonObj.has("currentPhotographSelectedPresetId")){
                if (envJsonObj.getLong("currentPhotographSelectedPresetId") == localJsonPresetObj.getLong("id")){
                    return;
                }
            }*/
            envJsonObj.put("currentPhotographSelectedPresetId", localJsonPresetObj.getLong("id"));
            Functions.saveEnv(exDataPath, envJsonObj);
            //setPicDisplay(localJsonPresetObj);
            setScreenPicsDisplay(localJsonPresetObj.getJSONArray("pics"));
            if (photographPresetListAdapter != null) {
                photographPresetListAdapter.setSelectedPosition(position);
            }
            if (localJsonPresetObj.getJSONArray("pics").length() > 0 && isResetPositionNeeded){
                selectedPicPosition = 0;
                screenPicListAdapter.setSelectedPosition(0);
            }
            loadPicsToScreen();
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }
    private boolean handleLibMenuAction(int position, int id) {
        switch (id){
            case MENU_COPY:
                try {
                    JSONArray localScreenPresetsArr = envJsonObj.getJSONArray("screenPresets");
                    JSONObject localScreenPresetObj = new JSONObject(envJsonObj.getJSONArray("screenPresets").getJSONObject(position).toString()) ;
                    localScreenPresetObj.put("id", System.currentTimeMillis());
                    if (position == 0){
                        localScreenPresetObj.put("name", getString(R.string.content_defaultNewPresetName));
                    }
                    localScreenPresetsArr.put(localScreenPresetObj);
                    envJsonObj.put("screenPresets", localScreenPresetsArr);
                    Functions.saveEnv(exDataPath, envJsonObj);
                    //photographPresetListAdapter.setSelectedPosition(0);
                    //JSONArray localScreenPresetsArr = envJsonObj.getJSONArray("screenPresets");
                    /*for (int i = 0; i < localScreenPresetsArr.length(); i++) {
                        if (envJsonObj.getLong("currentPhotographSelectedPresetId") == localScreenPresetsArr.getJSONObject(i).getLong("id")){
                            if (position == i){
                                //envJsonObj.put("currentPhotographSelectedPresetId", 0);
                                onPresetItemClick(0, true);
                            }
                            break;
                        }
                    }*/
                    refreshPresetListRecycler(false);
                    onPresetItemClick(localScreenPresetsArr.length() - 1, true);
                } catch (JSONException e) {
                    throw new RuntimeException(e);
                }
                break;
            case MENU_EDIT:
                editPresetDialog(position);
                break;
            case MENU_DELETE:
                new MaterialAlertDialogBuilder(requireContext())
                        .setTitle(getString(R.string.dialogTitle_delete))
                        .setMessage(getString(R.string.dialogMsg_deletePreset))
                        .setPositiveButton(getString(R.string.dialogButton_submit), (dialog, which) -> {
                            // 确定按钮点击事件
                            try {
                                JSONArray localScreenPresetsArr = envJsonObj.getJSONArray("screenPresets");
                                int pos = Functions.matchedPositionOfId(localScreenPresetsArr, envJsonObj.getLong("currentPhotographSelectedPresetId"));
                                if (position == pos){
                                    envJsonObj.put("currentPhotographSelectedPresetId", 0);
                                    //onPresetItemClick(0, true);
                                }
                                envJsonObj.getJSONArray("screenPresets").remove(position);
                                Functions.saveEnv(exDataPath, envJsonObj);
                                //refreshPresetListRecycler(false);
                                refreshPresetListRecycler(false);
                            } catch (JSONException e) {
                                throw new RuntimeException(e);
                            }
                            /*requireActivity().runOnUiThread(() ->
                                    Toast.makeText(requireContext(), "_confirmed", Toast.LENGTH_SHORT).show());*/
                        })
                        .setNegativeButton(getString(R.string.dialogButton_cancel), (dialog, which) -> {
                            // 取消按钮点击事件
                            dialog.dismiss();
                        })
                        .show();
                break;
        }
        return false;
    }
    private void editPresetDialog(int position){
        /*View dialogView = getLayoutInflater().inflate(R.layout.layout_input_dialog_oneline, null);

        AlertDialog dialog = new AlertDialog.Builder(getContext())
                .setView(dialogView)
                .create();

        Objects.requireNonNull(dialog.getWindow()).setBackgroundDrawableResource(android.R.color.transparent);

        EditText editName = dialogView.findViewById(R.id.inputEditText);*/

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(getContext(), R.style.M3AlertDialogTheme);
        View dialogView = getLayoutInflater().inflate(R.layout.layout_input_dialog_oneline, null);
        builder.setView(dialogView);

        builder.setTitle(getString(R.string.dialogTitle_editPreset));

        AlertDialog dialog = builder.create();
        // 在对话框显示后自动聚焦到输入框并弹出键盘
        dialog.setOnShowListener(dialogInterface -> {
            // 1. 获取输入框并请求焦点
            EditText editName = dialogView.findViewById(R.id.inputEditText);
            editName.requestFocus();

            // 2. 延迟执行确保窗口焦点就绪
            editName.postDelayed(() -> {
                // 3. 显示软键盘
                InputMethodManager imm = (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) {
                    imm.showSoftInput(editName, InputMethodManager.SHOW_IMPLICIT);
                }
            }, 100); // 100ms 延迟
        });
        dialog.show(); // 注意：要先 show() 再获取视图

        TextInputEditText editName = dialogView.findViewById(R.id.inputEditText);

        try {
            JSONObject presetItemObj = envJsonObj.getJSONArray("screenPresets").getJSONObject(position);
            editName.setText(presetItemObj.getString("name"));
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }

        /*TextView textView = dialogView.findViewById(R.id.headlineTextView);
        String title = "_Title";
        textView.setText(title);*/

        dialogView.findViewById(R.id.cancelButton).setOnClickListener(v -> dialog.dismiss());

        dialogView.findViewById(R.id.submitButton).setOnClickListener(v -> {
            String inputName = editName.getText().toString();
            try {
                if (inputName.isEmpty()){
                    return;
                }
                envJsonObj.getJSONArray("screenPresets").getJSONObject(position).put("name", inputName);
                Functions.saveEnv(exDataPath, envJsonObj);
                refreshPresetListRecycler(false);

            } catch (JSONException e) {
                throw new RuntimeException(e);
            }

            Functions.saveLib(exDataPath, libJsonArr);
            refreshLibRecycler();

            dialog.dismiss();
        });
        //dialog.show();
    }

    private void setScreenPicsDisplay(JSONArray screenPicsArr){
        try {
            RecyclerView screenRecyclerView = binding.screenPicRecyclerView;
            LinearLayoutManager layoutManager = new LinearLayoutManager((requireContext()), LinearLayoutManager.HORIZONTAL, false);
            screenRecyclerView.setLayoutManager(layoutManager);
            PhotographPicListAdapter.OnItemClickListener itemClickListener = position -> {
                /*try {
                    addPicToScreenTab(libObj.getJSONArray("pic").getJSONObject(position));
                } catch (JSONException e) {
                    throw new RuntimeException(e);
                }*/
                selectedPicPosition = position;
                screenPicListAdapter.setSelectedPosition(position);

                resetRotationSlider();
            };

            screenPicListAdapter = new PhotographPicListAdapter(screenPicsArr, itemClickListener, null, true);
            screenRecyclerView.setAdapter(screenPicListAdapter);

            screenPicListAdapter.setOnOrderChangedListener(this);
            screenPicListAdapter.setOnItemRemovedListener(this);

            if (screenItemTouchHelper != null) {
                screenItemTouchHelper.attachToRecyclerView(null);
            }

            ItemTouchHelper.Callback callback = new PhotographScreenItemTouchHelperCallback(screenPicListAdapter);
            screenItemTouchHelper = new ItemTouchHelper(callback);
            screenItemTouchHelper.attachToRecyclerView(screenRecyclerView);

            /*if (envJsonObj.getJSONArray("screenPresets").getJSONObject(0).getJSONArray("pics").length() > 0){
                selectedPicPosition = 0;
                screenPicListAdapter.setSelectedPosition(0);
            }*/

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
    @Override
    public void onOrderChanged(int froImageInfo, int toPosition, JSONArray newOrder) {
        //Toast.makeText(getContext(), newOrder.toString(), Toast.LENGTH_SHORT).show();
        try {
            JSONArray localScreenPresetsArr = envJsonObj.getJSONArray("screenPresets");
            int pos = Functions.matchedPositionOfId(localScreenPresetsArr, envJsonObj.getLong("currentPhotographSelectedPresetId"));
            envJsonObj.getJSONArray("screenPresets").getJSONObject(pos).put("pics", newOrder);
            Functions.saveEnv(exDataPath, envJsonObj);
            if (froImageInfo == selectedPicPosition){
                selectedPicPosition = toPosition;
            } else if (toPosition == selectedPicPosition) {
                if (toPosition == froImageInfo - 1){
                    selectedPicPosition++;
                } else if (toPosition == froImageInfo + 1) {
                    selectedPicPosition--;
                }
            }
            loadPicsToScreen();
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }
    @Override
    public void onItemRemoved(int position) {
        try {
            if (position == selectedPicPosition){
                selectedPicPosition = -1;
            }
            JSONArray localScreenPresetsArr = envJsonObj.getJSONArray("screenPresets");
            int pos = Functions.matchedPositionOfId(localScreenPresetsArr, envJsonObj.getLong("currentPhotographSelectedPresetId"));
            envJsonObj.getJSONArray("screenPresets").getJSONObject(pos).getJSONArray("pics").remove(position);
            Functions.saveEnv(exDataPath, envJsonObj);
            //envJsonObj.put("currentPhotographSelectedPresetId", 0);
            //refreshPresetListRecycler(false);
            loadPicsToScreen();
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }
}
