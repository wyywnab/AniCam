package com.wyywn.anicam.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import com.wyywn.anicam.R;
import com.wyywn.anicam.databinding.FragmentSettingsBinding;

public class SettingsFragment extends Fragment {
    private FragmentSettingsBinding binding;

    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {

        binding = FragmentSettingsBinding.inflate(inflater, container, false);

        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // 检查FragmentManager中是否已经有SettingsFragment的实例
        // 这样可以避免在配置变更时（如屏幕旋转）重复添加
        if (savedInstanceState == null) {
            // 获取子Fragment管理器
            FragmentManager fragmentManager = getChildFragmentManager();

            // 启动一个Fragment事务
            fragmentManager.beginTransaction()
                    // 替换布局文件中 ID 为 R.id.settings_container 的容器
                    .replace(R.id.settings_container, new SettingsPreferenceFragment())
                    .commit();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
