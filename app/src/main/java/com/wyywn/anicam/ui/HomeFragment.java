package com.wyywn.anicam.ui;

import android.app.Activity;
import androidx.appcompat.app.AlertDialog;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.DocumentsContract;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.color.MaterialColors;
import com.google.android.material.R.attr;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.wyywn.anicam.utils.Functions;
import com.wyywn.anicam.MainActivity;
import com.wyywn.anicam.R;
import com.wyywn.anicam.adapter.HomeLibManagerAdapter;
import com.wyywn.anicam.adapter.HomePicItemTouchHelperCallback;
import com.wyywn.anicam.adapter.HomePicManagerAdapter;
import com.wyywn.anicam.adapter.HomeLibItemTouchHelperCallback;
import com.wyywn.anicam.utils.ImageViewerUtil;
import com.wyywn.anicam.databinding.FragmentHomeBinding;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;
import java.util.Objects;

public class HomeFragment extends Fragment implements
        HomeLibManagerAdapter.OnOrderChangedListener, HomePicManagerAdapter.OnOrderChangedListener {

    private static final int SORTING_NULL = 0;
    private static final int SORTING_LIB = 1;
    private static final int SORTING_PIC = 2;
    private static final int MENU_EDIT = 1;
    private static final int MENU_DELETE = 2;
    private static final int MENU_ADDTO = 3;
    private static final int DIRECTORY_PICK_LIB = 1;
    private static final int DIRECTORY_PICK_PIC = 2;

    private JSONArray libJsonArr;
    private ItemTouchHelper libItemTouchHelper;
    private ItemTouchHelper picItemTouchHelper;

    private int sortingMode = SORTING_NULL;
    private int directoryPickerMode;
    private RecyclerView libRecyclerView;
    private RecyclerView picRecyclerView;
    private HomeLibManagerAdapter homeLibAdapter;
    private String exDataPath;
    private int selectedLibPosition = -1;
    private ActivityResultLauncher<Intent> directoryPickerLauncher;
    private ActivityResultLauncher<String[]> multipleFilePickerLauncher;
    private FragmentHomeBinding binding;

    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        assert getActivity() != null;
        exDataPath = Objects.requireNonNull(((MainActivity) getActivity()).getExternalFilesDir("")).getAbsolutePath();

        binding = FragmentHomeBinding.inflate(inflater, container, false);

        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        int selectedColor = MaterialColors.getColor(view, attr.colorOutline);
        int notSelectedColor = MaterialColors.getColor(view, attr.colorOnSecondary);

        binding.addLibButton.setOnClickListener(this::onAddLibButtonClick);
        binding.importLibFolderButton.setOnClickListener(this::onImportLibFolderButtonClick);
        binding.switchLibSortModeButton.setBackgroundColor(notSelectedColor);
        binding.switchLibSortModeButton.setOnClickListener(v -> {
            switch (sortingMode){
                case SORTING_NULL:
                    sortingMode = SORTING_LIB;
                    binding.switchLibSortModeButton.setBackgroundColor(selectedColor);
                    libItemTouchHelper.attachToRecyclerView(libRecyclerView);
                    break;
                case SORTING_LIB:
                    sortingMode = SORTING_NULL;
                    binding.switchLibSortModeButton.setBackgroundColor(notSelectedColor);
                    libItemTouchHelper.attachToRecyclerView(null);
                    break;
                case SORTING_PIC:
                    sortingMode = SORTING_LIB;
                    binding.switchLibSortModeButton.setBackgroundColor(selectedColor);
                    binding.switchPicSortModeButton.setBackgroundColor(notSelectedColor);
                    libItemTouchHelper.attachToRecyclerView(libRecyclerView);
                    picItemTouchHelper.attachToRecyclerView(null);
                    break;
            }
        });

        binding.addPicButton.setOnClickListener(this::onAddPicButtonClick);
        binding.addPicFromFolderButton.setOnClickListener(this::onAddPicFromFolderButtonClick);
        binding.switchPicSortModeButton.setBackgroundColor(notSelectedColor);
        binding.switchPicSortModeButton.setOnClickListener(v -> {
            if (selectedLibPosition == -1){
                return;
            }
            switch (sortingMode){
                case SORTING_NULL:
                    sortingMode = SORTING_PIC;
                    binding.switchPicSortModeButton.setBackgroundColor(selectedColor);
                    picItemTouchHelper.attachToRecyclerView(picRecyclerView);
                    break;
                case SORTING_LIB:
                    sortingMode = SORTING_PIC;
                    binding.switchLibSortModeButton.setBackgroundColor(notSelectedColor);
                    binding.switchPicSortModeButton.setBackgroundColor(selectedColor);
                    picItemTouchHelper.attachToRecyclerView(picRecyclerView);
                    libItemTouchHelper.attachToRecyclerView(null);
                    break;
                case SORTING_PIC:
                    sortingMode = SORTING_NULL;
                    binding.switchPicSortModeButton.setBackgroundColor(notSelectedColor);
                    picItemTouchHelper.attachToRecyclerView(null);
                    break;
            }
        });

        directoryPickerLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                        Uri treeUri = result.getData().getData();
                        handleSelectedDirectory(treeUri);
                    }
                });
        multipleFilePickerLauncher = registerForActivityResult(
                new ActivityResultContracts.OpenMultipleDocuments(), // <-- 修改成这个
                uris -> {
                    // 当用户选择多个文件后会回调这里
                    if (uris != null && !uris.isEmpty()) {
                        handleSelectedFiles(uris);
                    }
                }
        );

        libJsonArr = Functions.getLib(exDataPath);
        refreshLibDisplay();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            binding.addPicButton.setTooltipText(getString(R.string.tooltip_addPicButton));
            binding.addPicFromFolderButton.setTooltipText(getString(R.string.tooltip_addPicFromFolderButton));
            binding.switchPicSortModeButton.setTooltipText(getString(R.string.tooltip_switchPicSortModeButton));
            binding.addLibButton.setTooltipText(getString(R.string.tooltip_addLibButton));
            binding.importLibFolderButton.setTooltipText(getString(R.string.tooltip_importLibFolderButton));
            binding.switchLibSortModeButton.setTooltipText(getString(R.string.tooltip_switchLibSortModeButton));
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        //selectedLibPosition = -1;
        sortingMode = SORTING_NULL;
        if (libItemTouchHelper != null){
            libItemTouchHelper.attachToRecyclerView(null);
        }
        if (picItemTouchHelper != null){
            picItemTouchHelper.attachToRecyclerView(null);
        }
        binding = null;
    }


    private void onAddLibButtonClick(View view){
        requireActivity().runOnUiThread(() ->
                Toast.makeText(requireContext(), "click", Toast.LENGTH_SHORT).show());

        /*// 创建自定义对话框布局
        View dialogView = getLayoutInflater().inflate(R.layout.layout_input_dialog_oneline, null);

        // 创建AlertDialog（不使用MaterialAlertDialogBuilder以保持完全控制）
        AlertDialog dialog = new AlertDialog.Builder(getContext())
                .setView(dialogView)
                .create();

        Objects.requireNonNull(dialog.getWindow()).setBackgroundDrawableResource(android.R.color.transparent);

        EditText editName = dialogView.findViewById(R.id.inputEditText);*/

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(getContext(), R.style.M3AlertDialogTheme);
        View dialogView = getLayoutInflater().inflate(R.layout.layout_input_dialog_oneline, null);
        builder.setView(dialogView);

        builder.setTitle(getString(R.string.dialogTitle_addLib));

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

        /*TextView textView = dialogView.findViewById(R.id.headlineTextView);
        String title = "_Title";
        textView.setText(title);*/

        dialogView.findViewById(R.id.cancelButton).setOnClickListener(v -> dialog.dismiss());

        dialogView.findViewById(R.id.submitButton).setOnClickListener(v -> {
            String inputName = editName.getText().toString();

            JSONObject tempObj = new JSONObject();
            try {
                tempObj.put("id", System.currentTimeMillis());
                tempObj.put("name", inputName);
                tempObj.put("pic", new JSONArray("[]"));
            } catch (JSONException e) {
                throw new RuntimeException(e);
            }
            libJsonArr.put(tempObj);
            Functions.saveLib(exDataPath, libJsonArr);
            refreshLibDisplay();

            dialog.dismiss();
        });
        //dialog.show();
    }
    private void onImportLibFolderButtonClick(View v){
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION |
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION |
                Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
        );
        directoryPickerMode = DIRECTORY_PICK_LIB;
        directoryPickerLauncher.launch(intent);
    }
    private void refreshLibDisplay(){
        libRecyclerView = binding.libRecyclerView;
        LinearLayoutManager layoutManager = new LinearLayoutManager((requireContext()), LinearLayoutManager.VERTICAL, false);
        libRecyclerView.setLayoutManager(layoutManager);
        HomeLibManagerAdapter.OnItemClickListener itemClickListener = position -> {
            try {
                JSONObject selectedItem = libJsonArr.getJSONObject(position);
                requireActivity().runOnUiThread(() ->
                        Toast.makeText(requireContext(), selectedItem.toString(), Toast.LENGTH_SHORT).show());
                selectedLibPosition = position;
                setPicDisplay(selectedItem);
                homeLibAdapter.setSelectedPosition(selectedLibPosition);
            } catch (JSONException e) {
                throw new RuntimeException(e);
            }
        };
        HomeLibManagerAdapter.OnItemLongClickListener itemLongClickListener = (view, position) -> {
            if (sortingMode != SORTING_LIB){
                //showPopupMenu(view, position);
                /*requireActivity().runOnUiThread(() ->
                        Toast.makeText(requireContext(), "long click", Toast.LENGTH_SHORT).show());*/
                Context context = view.getContext();
                PopupMenu popupMenu = new PopupMenu(context, view, Gravity.END);

                // 从菜单资源文件加载
                //popupMenu.inflate(R.menu.item_options_menu);

                // 或者动态添加菜单项
                popupMenu.getMenu().add(0, MENU_EDIT, 0, getString(R.string.menu_edit));
                popupMenu.getMenu().add(0, MENU_DELETE, 0, getString(R.string.menu_delete));

                // 菜单项点击事件
                popupMenu.setOnMenuItemClickListener(item -> handleLibMenuAction(position, item.getItemId()));

                // 显示菜单
                popupMenu.show();
            }
        };

        homeLibAdapter = new HomeLibManagerAdapter(libJsonArr, itemClickListener, itemLongClickListener);
        libRecyclerView.setAdapter(homeLibAdapter);

        homeLibAdapter.setOnOrderChangedListener(this);

        ItemTouchHelper.Callback callback = new HomeLibItemTouchHelperCallback(homeLibAdapter);
        libItemTouchHelper = new ItemTouchHelper(callback);

        if (selectedLibPosition != -1){
            try {
                setPicDisplay(libJsonArr.getJSONObject(selectedLibPosition));
                homeLibAdapter.setSelectedPosition(selectedLibPosition);
            } catch (JSONException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private boolean handleLibMenuAction(int position, int id) {
        // 处理菜单项选择
        //Log.d("MenuAction", "Position: " + position + ", Action: " + action);
        requireActivity().runOnUiThread(() ->
                Toast.makeText(requireContext(), "MenuAction: Position: " + position + ", Action: " + id, Toast.LENGTH_SHORT).show());
        if (id == MENU_EDIT){
            /*// 创建自定义对话框布局
            View dialogView = getLayoutInflater().inflate(R.layout.layout_input_dialog_oneline, null);

            // 创建AlertDialog（不使用MaterialAlertDialogBuilder以保持完全控制）
            AlertDialog dialog = new AlertDialog.Builder(getContext())
                    .setView(dialogView)
                    .create();

            Objects.requireNonNull(dialog.getWindow()).setBackgroundDrawableResource(android.R.color.transparent);

            EditText editName = dialogView.findViewById(R.id.inputEditText);*/

            MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(getContext(), R.style.M3AlertDialogTheme);
            View dialogView = getLayoutInflater().inflate(R.layout.layout_input_dialog_oneline, null);
            builder.setView(dialogView);

            builder.setTitle(getString(R.string.dialogTitle_editLib));

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
                editName.setText(libJsonArr.getJSONObject(position).getString("name"));
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
                        inputName = libJsonArr.getJSONObject(position).getString("name");
                    }

                    JSONObject tempObj = new JSONObject();
                    tempObj.put("id", libJsonArr.getJSONObject(position).getInt("id"));
                    tempObj.put("name", inputName);
                    tempObj.put("pic", libJsonArr.getJSONObject(position).getJSONArray("pic"));

                    //libJsonArr.put(tempObj);
                    libJsonArr.remove(position);
                    libJsonArr = Functions.insertJsonObjToJsonArray(position, libJsonArr, tempObj);
                } catch (JSONException e) {
                    throw new RuntimeException(e);
                }
                Functions.saveLib(exDataPath, libJsonArr);
                refreshLibDisplay();

                dialog.dismiss();
            });
            //dialog.show();
            return true;
        } else if (id == MENU_DELETE) {
            new MaterialAlertDialogBuilder(requireContext())
                    .setTitle(getString(R.string.dialogTitle_delete))
                    .setMessage(getString(R.string.dialogMsg_deleteLib))
                    .setPositiveButton(getString(R.string.dialogButton_submit), (dialog, which) -> {
                        // 确定按钮点击事件
                        libJsonArr.remove(position);
                        Functions.saveLib(exDataPath, libJsonArr);
                        refreshLibDisplay();
                        /*requireActivity().runOnUiThread(() ->
                                Toast.makeText(requireContext(), "_confirmed", Toast.LENGTH_SHORT).show());*/
                    })
                    .setNegativeButton(getString(R.string.dialogButton_cancel), (dialog, which) -> {
                        // 取消按钮点击事件
                        dialog.dismiss();
                    })
                    .show();
            return true;
        }
        return false;
    }
    // 实现顺序改变回调
    @Override
    public void onOrderChanged(int fromPosition, int toPosition, JSONArray newOrder) {
        // 调用保存函数
        if (sortingMode == SORTING_LIB){
            Toast.makeText(requireContext(), String.valueOf(selectedLibPosition), Toast.LENGTH_SHORT).show();
            /*try {
                long currentId = libJsonArr.getJSONObject(selectedLibPosition).getLong("id");
                for (int i = 0; i < newOrder.length(); i++) {
                    if (newOrder.getJSONObject(i).getLong("id") == currentId){
                        selectedLibPosition = i;
                        break;
                    }
                }
            } catch (JSONException e) {
                throw new RuntimeException(e);
            }
            libJsonArr = newOrder;*/
            if (fromPosition == selectedLibPosition){
                selectedLibPosition = toPosition;
            } else if (toPosition == selectedLibPosition){
                if (fromPosition == toPosition - 1){
                    selectedLibPosition -= 1;
                } else if (fromPosition == toPosition + 1) {
                    selectedLibPosition += 1;
                }
            }
            Toast.makeText(requireContext(), String.valueOf(selectedLibPosition), Toast.LENGTH_SHORT).show();
        } else if (sortingMode == SORTING_PIC) {
            try {
                JSONObject localObj = libJsonArr.getJSONObject(selectedLibPosition);
                localObj.put("pic", newOrder);
                libJsonArr.put(selectedLibPosition, localObj);
            } catch (JSONException e) {
                throw new RuntimeException(e);
            }
        }
        Functions.saveLib(exDataPath, libJsonArr);
        //Toast.makeText(requireContext(), "顺序已保存", Toast.LENGTH_SHORT).show();
    }

    private void onAddPicButtonClick(View v){
        if (selectedLibPosition == -1){
            requireActivity().runOnUiThread(() ->
                    Toast.makeText(requireContext(), getString(R.string.info_errorNotSelectedLib), Toast.LENGTH_SHORT).show());
            return;
        }
        multipleFilePickerLauncher.launch(new String[]{"image/*"});
    }
    private void onAddPicFromFolderButtonClick(View v){
        if (selectedLibPosition == -1){
            requireActivity().runOnUiThread(() ->
                    Toast.makeText(requireContext(), getString(R.string.info_errorNotSelectedLib), Toast.LENGTH_SHORT).show());
            return;
        }
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION |
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION |
                Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
        );
        directoryPickerMode = DIRECTORY_PICK_PIC;
        directoryPickerLauncher.launch(intent);
    }

    View picItemView;
    private void setPicDisplay(JSONObject libObj){
        TextView pic_sub_textView = binding.picSubTextView;
        try {
            pic_sub_textView.setText(libObj.getString("name"));
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }

        picRecyclerView = binding.picRecyclerView;
        LinearLayoutManager layoutManager = new LinearLayoutManager((requireContext()), LinearLayoutManager.VERTICAL, false);
        picRecyclerView.setLayoutManager(layoutManager);
        HomePicManagerAdapter.OnItemClickListener itemClickListener = position -> {
            try {
                JSONObject selectedItem = libJsonArr.getJSONObject(selectedLibPosition).getJSONArray("pic").getJSONObject(position);
                requireActivity().runOnUiThread(() ->
                        Toast.makeText(requireContext(), selectedItem.toString(), Toast.LENGTH_SHORT).show());

                ImageViewerUtil.viewImageWithSystemViewer(getContext(), Uri.parse(selectedItem.getString("uri")));
            } catch (JSONException e) {
                throw new RuntimeException(e);
            }
        };
        HomePicManagerAdapter.OnItemLongClickListener itemLongClickListener = (view, position) -> {
            if (sortingMode != SORTING_PIC){
                //showPopupMenu(view, position);
                /*requireActivity().runOnUiThread(() ->
                        Toast.makeText(requireContext(), "long click", Toast.LENGTH_SHORT).show());*/
                picItemView = view;
                Context picItem_Context = view.getContext();
                PopupMenu popupMenu = new PopupMenu(picItem_Context, view, Gravity.END);

                // 从菜单资源文件加载
                //popupMenu.inflate(R.menu.item_options_menu);

                // 或者动态添加菜单项
                popupMenu.getMenu().add(0, MENU_EDIT, 0, getString(R.string.menu_edit));
                popupMenu.getMenu().add(0, MENU_DELETE, 0, getString(R.string.menu_delete));
                popupMenu.getMenu().add(0, MENU_ADDTO, 0, getString(R.string.menu_addTo));

                // 菜单项点击事件
                popupMenu.setOnMenuItemClickListener(item -> handlePicMenuAction(position, item.getItemId()));

                // 显示菜单
                popupMenu.show();
            }
        };

        HomePicManagerAdapter adapter;
        try {
            adapter = new HomePicManagerAdapter(libObj.getJSONArray("pic"), itemClickListener, itemLongClickListener);
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
        picRecyclerView.setAdapter(adapter);

        adapter.setOnOrderChangedListener(this);

        ItemTouchHelper.Callback callback = new HomePicItemTouchHelperCallback(adapter);
        picItemTouchHelper = new ItemTouchHelper(callback);
    }
    private void handleSelectedFiles(List<Uri> uris) {
        new Thread(() -> {
            handleFiles(uris, selectedLibPosition, true);
        }).start();
    }

    private void handleFiles(List<Uri> uris, int operationPosition, boolean isTakingPersistableUriPermissionNeeded) {
        try {
            JSONObject localLibObj = libJsonArr.getJSONObject(operationPosition);
            JSONArray localPicArr = localLibObj.getJSONArray("pic");
            for (Uri uri : uris) {
                if (isTakingPersistableUriPermissionNeeded) {
                    assert getActivity() != null;
                    try {
                        ContentResolver resolver = getActivity().getApplicationContext().getContentResolver();
                        final int takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                                | Intent.FLAG_GRANT_WRITE_URI_PERMISSION;
                        resolver.takePersistableUriPermission(uri, takeFlags);
                    } catch (SecurityException e) {
                        throw new RuntimeException(e);
                    }
                }

                boolean isRedundantPic = false;
                for (int i = 0; i < localPicArr.length(); i++) {
                    if (localPicArr.getJSONObject(i).getString("uri").equals(uri.toString())) {
                        isRedundantPic = true;
                        break;
                    }
                }

                String fileName = Functions.getFileName(getContext(), uri);
                if (isRedundantPic) {
                    requireActivity().runOnUiThread(() ->
                            Toast.makeText(requireContext(), "Selected file: " + fileName + " is redundant, pass", Toast.LENGTH_SHORT).show());
                    continue;
                }
                requireActivity().runOnUiThread(() ->
                        Toast.makeText(requireContext(), "Selected file: " + fileName, Toast.LENGTH_SHORT).show());
                JSONObject tempObj = new JSONObject();
                tempObj.put("id", System.currentTimeMillis());
                tempObj.put("name", fileName);
                tempObj.put("uri", uri.toString());
                localPicArr.put(tempObj);
            }

            requireActivity().runOnUiThread(() -> {
                Toast.makeText(requireContext(),
                        "已选择 " + uris.size() + " 个文件",
                        Toast.LENGTH_SHORT).show();
            });

            localLibObj.put("pic", localPicArr);
            libJsonArr.put(operationPosition, localLibObj);
            Functions.saveLib(exDataPath, libJsonArr);

            requireActivity().runOnUiThread(() -> setPicDisplay(localLibObj));
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }

    private void handleSelectedDirectory(Uri treeUri){
        final int takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_WRITE_URI_PERMISSION;
        requireContext().getContentResolver().takePersistableUriPermission(treeUri, takeFlags);

        if (directoryPickerMode == DIRECTORY_PICK_PIC){
            new Thread(() -> handleFiles(Functions.getFilesByType(requireContext(), treeUri, new String[]{"image/*"}), selectedLibPosition, false)).start();
        } else if (directoryPickerMode == DIRECTORY_PICK_LIB) {
            new Thread(() -> {
                try {
                    JSONArray subFolderTreeUris = Functions.getSubdirectoryTreeUris(requireContext(), treeUri);
                    for (int i = 0; i < subFolderTreeUris.length(); i++) {
                        JSONObject subTreeUriInfo = subFolderTreeUris.getJSONObject(i);
                        String folderName = subTreeUriInfo.getString("name");

                        // 不需要为子目录单独获取权限，使用原始 treeUri
                        // 构建子目录的完整文档路径
                        String baseDocumentId = DocumentsContract.getTreeDocumentId(treeUri);
                        String subFolderDocumentId = baseDocumentId + "/" + folderName;

                        // 获取子目录中的图片文件
                        List<Uri> localFolderImages = Functions.getFilesByTypeInSubfolder(requireContext(), treeUri, subFolderDocumentId, new String[]{"image/*"});

                        if (localFolderImages.isEmpty()) {
                            continue;
                        }

                        JSONObject tempJsonObj = new JSONObject();
                        tempJsonObj.put("id", System.currentTimeMillis());
                        tempJsonObj.put("name", folderName);
                        tempJsonObj.put("pic", new JSONArray("[]"));
                        libJsonArr.put(tempJsonObj);

                        handleFiles(localFolderImages, libJsonArr.length() - 1, false);
                    }
                    /*Functions.saveLib(exDataPath, libJsonArr);*/
                    assert getActivity() != null;
                    getActivity().runOnUiThread(this::refreshLibDisplay);

                } catch (JSONException e) {
                    throw new RuntimeException(e);
                }
            }).start();
        }

    }

    private boolean handlePicMenuAction(int position, int id) {
        // 处理菜单项选择
        //Log.d("MenuAction", "Position: " + position + ", Action: " + action);
        requireActivity().runOnUiThread(() ->
                Toast.makeText(requireContext(), "MenuAction: Position: " + position + ", Action: " + id, Toast.LENGTH_SHORT).show());

        switch (id){
            case MENU_EDIT:
                MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(getContext(), R.style.M3AlertDialogTheme);
                View dialogView = getLayoutInflater().inflate(R.layout.layout_input_dialog_oneline, null);
                builder.setView(dialogView);

                builder.setTitle(getString(R.string.dialogTitle_editPic));

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
                    JSONObject rawPicItemObj = libJsonArr.getJSONObject(selectedLibPosition).getJSONArray("pic").getJSONObject(position);
                    editName.setText(rawPicItemObj.getString("name"));
                } catch (JSONException e) {
                    throw new RuntimeException(e);
                }

                dialogView.findViewById(R.id.cancelButton).setOnClickListener(v -> dialog.dismiss());

                dialogView.findViewById(R.id.submitButton).setOnClickListener(v -> {
                    String inputName = editName.getText().toString();
                    try {
                        JSONObject localLibItem = libJsonArr.getJSONObject(selectedLibPosition);
                        JSONArray localPicArr = localLibItem.getJSONArray("pic");
                        JSONObject rawPicItemObj = localPicArr.getJSONObject(position);
                        if (inputName.isEmpty()){
                            inputName = rawPicItemObj.getString("name");
                        }

                        JSONObject tempObj = new JSONObject();
                        tempObj.put("id", rawPicItemObj.getInt("id"));
                        tempObj.put("name", inputName);
                        tempObj.put("uri", rawPicItemObj.getString("uri"));

                        //libJsonArr.put(tempObj);
                        localPicArr.remove(position);
                        localPicArr = Functions.insertJsonObjToJsonArray(position, localPicArr, tempObj);
                        localLibItem.put("pic", localPicArr);
                        libJsonArr.put(selectedLibPosition, localLibItem);
                    } catch (JSONException e) {
                        throw new RuntimeException(e);
                    }

                    Functions.saveLib(exDataPath, libJsonArr);
                    refreshLibDisplay();

                    dialog.dismiss();
                });
                //dialog.show();
                return true;
            case MENU_DELETE:
                new MaterialAlertDialogBuilder(requireContext())
                        .setTitle(getString(R.string.dialogTitle_delete))
                        .setMessage(getString(R.string.dialogMsg_deletePic))
                        .setPositiveButton(getString(R.string.dialogButton_submit), (dialog_delete, which) -> {
                            // 确定按钮点击事件
                            try {
                                JSONObject localLibItem = libJsonArr.getJSONObject(selectedLibPosition);
                                JSONArray localPicArr = localLibItem.getJSONArray("pic");
                                localPicArr.remove(position);
                                localLibItem.put("pic", localPicArr);
                                libJsonArr.put(selectedLibPosition, localLibItem);
                            } catch (JSONException e) {
                                throw new RuntimeException(e);
                            }
                            Functions.saveLib(exDataPath, libJsonArr);
                            refreshLibDisplay();
                            /*requireActivity().runOnUiThread(() ->
                                    Toast.makeText(requireContext(), "_confirmed", Toast.LENGTH_SHORT).show());*/
                        })
                        .setNegativeButton(getString(R.string.dialogButton_cancel), (dialog_delete, which) -> {
                            // 取消按钮点击事件
                            dialog_delete.dismiss();
                        })
                        .show();
                return true;
            case MENU_ADDTO:
                Context context = picItemView.getContext();
                PopupMenu popupMenu = new PopupMenu(context, picItemView, Gravity.END);

                // 或者动态添加菜单项
                for (int i = 0; i < libJsonArr.length(); i++) {
                    try {
                        popupMenu.getMenu().add(0, i, 0, libJsonArr.getJSONObject(i).getString("name"));
                    } catch (JSONException e) {
                        throw new RuntimeException(e);
                    }
                }

                // 菜单项点击事件
                popupMenu.setOnMenuItemClickListener(item -> {
                    try {
                        JSONObject currentPicObj = libJsonArr.getJSONObject(selectedLibPosition).getJSONArray("pic").getJSONObject(position);
                        JSONArray clickedLibPicArr = libJsonArr.getJSONObject(item.getItemId()).getJSONArray("pic");
                        for (int i = 0; i < clickedLibPicArr.length(); i++) {
                            if (clickedLibPicArr.getJSONObject(i).toString().equals(currentPicObj.toString())){
                                requireActivity().runOnUiThread(() ->
                                        Toast.makeText(requireContext(), getString(R.string.info_duplicatedAdd), Toast.LENGTH_SHORT).show());
                                return false;
                            }
                        }
                        clickedLibPicArr.put(new JSONObject(currentPicObj.toString()));
                        Functions.saveLib(exDataPath, libJsonArr);
                        refreshLibDisplay();
                        return true;
                    } catch (JSONException e) {
                        throw new RuntimeException(e);
                    }
                });

                // 显示菜单
                popupMenu.show();
        }
        return false;
    }
}
