package com.wyywn.anicam.adapter;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.drawable.ColorDrawable;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;


public class PhotographScreenItemTouchHelperCallback extends ItemTouchHelper.Callback {

    // 用于绘制红色背景
    private final ColorDrawable background = new ColorDrawable(Color.RED);
    // 用于清除原始内容的Paint
    private Paint clearPaint;
    private final PhotographPicListAdapter mAdapter;

    public PhotographScreenItemTouchHelperCallback(PhotographPicListAdapter adapter) {
        mAdapter = adapter;

        clearPaint = new Paint();
        clearPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
    }

    @Override
    public int getMovementFlags(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder) {

        int dragFlags = ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT |
                ItemTouchHelper.UP | ItemTouchHelper.DOWN;

        int swipeFlags = ItemTouchHelper.UP;
        return makeMovementFlags(dragFlags, swipeFlags);
    }

    @Override
    public boolean onMove(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder,
                          RecyclerView.ViewHolder target) {
        // 获取拖拽和目标项的位置
        int fromPosition = viewHolder.getAdapterPosition();
        int toPosition = target.getAdapterPosition();

        // 在数据列表中交换位置
        //Collections.swap(mAdapter.getDataList(), fromPosition, toPosition);
        try {
            JSONObject fromOriginal = mAdapter.mData.getJSONObject(fromPosition);
            JSONObject toOriginal = mAdapter.mData.getJSONObject(toPosition);
            mAdapter.mData.put(toPosition, fromOriginal);
            mAdapter.mData.put(fromPosition, toOriginal);

            /*JSONArray temp = new JSONArray();
            for (int i = 0; i < mAdapter.mData.length(); i++) {
                if (i == fromPosition) {
                    temp.put(toOriginal);
                } else if (i == toPosition) {
                    temp.put(fromOriginal);
                } else {
                    temp.put(mAdapter.mData.get(i));
                }
            }
            mAdapter.mData = temp;*/
        } catch (JSONException e) {
            e.printStackTrace();
            return false;
        }

        // 通知 Adapter 列表项位置已改变
        mAdapter.notifyItemMoved(fromPosition, toPosition);

        if (mAdapter.orderChangedListener != null) {
            mAdapter.orderChangedListener.onOrderChanged(
                    fromPosition,
                    toPosition,
                    mAdapter.getCurrentOrder()
            );
        }

        return true;
    }

    @Override
    public void onSwiped(RecyclerView.ViewHolder viewHolder, int direction) {
        // 获取需要删除的项的位置
        int position = viewHolder.getAdapterPosition();

        // 检查方向是否是你想要处理的
        //if (direction == ItemTouchHelper.UP || direction == ItemTouchHelper.DOWN) {
            // 从数据源中移除该项
            if (mAdapter.mData != null && position < mAdapter.mData.length()) {
                JSONArray newData = new JSONArray();
                try {
                    for (int i = 0; i < mAdapter.mData.length(); i++) {
                        if (i != position) {
                            newData.put(mAdapter.mData.getJSONObject(i));
                        }
                    }
                    mAdapter.mData = newData;
                    mAdapter.notifyItemRemoved(position);

                    if (mAdapter.itemRemovedListener != null) {
                        mAdapter.itemRemovedListener.onItemRemoved(position);
                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        //}
    }

    @Override
    public void onChildDraw(@NonNull Canvas c, @NonNull RecyclerView recyclerView,
                            @NonNull RecyclerView.ViewHolder viewHolder, float dX, float dY,
                            int actionState, boolean isCurrentlyActive) {
        if (actionState == ItemTouchHelper.ACTION_STATE_SWIPE) {
            // 整个item背景变为红色
            View itemView = viewHolder.itemView;

            // 计算透明度（根据滑动距离）
            float alpha = Math.min(1.0f, Math.abs(dY) / (float) itemView.getHeight());
            background.setAlpha((int) (alpha * 255));

            // 设置背景为整个itemView
            background.setBounds(itemView.getLeft(), itemView.getTop(),
                    itemView.getRight(), itemView.getBottom());
            background.draw(c);
        }
        super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive);
    }

    @Override
    public boolean isLongPressDragEnabled() {
        // 启用长按拖拽
        return true;
    }

    @Override
    public boolean isItemViewSwipeEnabled() {
        // 启用滑动删除
        return true;
    }

    @Override
    public float getSwipeThreshold(RecyclerView.ViewHolder viewHolder) {
        // 设置滑动阈值（滑动超过item宽度的40%即触发删除）
        return 0.5f;
    }

    @Override
    public float getMoveThreshold(RecyclerView.ViewHolder viewHolder) {
        // 设置移动阈值
        return 0.5f;
    }
}
