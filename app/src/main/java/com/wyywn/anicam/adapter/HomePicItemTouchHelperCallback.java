package com.wyywn.anicam.adapter;

import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

import org.json.JSONException;
import org.json.JSONObject;


public class HomePicItemTouchHelperCallback extends ItemTouchHelper.Callback {

    private final HomePicManagerAdapter mAdapter;

    public HomePicItemTouchHelperCallback(HomePicManagerAdapter adapter) {
        mAdapter = adapter;
    }

    @Override
    public int getMovementFlags(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder) {
        // 允许上下拖拽
        int dragFlags = ItemTouchHelper.UP | ItemTouchHelper.DOWN;
        // 不允许滑动
        int swipeFlags = 0;
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
        // 在这里处理滑动删除，如果 getMovementFlags 中不设置 swipeFlags，这个方法不会被调用
    }
}
