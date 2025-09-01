package com.wyywn.anicam.adapter;

import android.text.Html;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import com.wyywn.anicam.R;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class PhotographLibListAdapter extends RecyclerView.Adapter<PhotographLibListAdapter.ViewHolder> {
    JSONArray mData;
    private OnItemClickListener mlistener;
    private OnItemLongClickListener mlistenerLong;
    private int selectedPosition = -1;

    public PhotographLibListAdapter(JSONArray data, OnItemClickListener listener, OnItemLongClickListener listenerLong) {
        mData = data;
        mlistener = listener;
        mlistenerLong = listenerLong;
    }

    // 设置选中位置
    public void setSelectedPosition(int position) {
        int previousSelected = selectedPosition;
        selectedPosition = position;

        // 只刷新有变化的item
        if (previousSelected != -1) {
            notifyItemChanged(previousSelected);
        }
        if (position != -1) {
            notifyItemChanged(position);
        }
    }

    // 获取当前选中位置
    public int getSelectedPosition() {
        return selectedPosition;
    }

    // 清除选中状态
    public void clearSelection() {
        int previousSelected = selectedPosition;
        selectedPosition = -1;
        if (previousSelected != -1) {
            notifyItemChanged(previousSelected);
        }
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.layout_photographlib_item, parent, false);
        return new ViewHolder(view, mlistener, mlistenerLong);
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        try {
            JSONObject item = mData.getJSONObject(position);
            //holder.textView.setText(item.getString("name"));
            String text = item.getString("name");
            holder.textView.setText(Html.fromHtml(text, Html.FROM_HTML_MODE_COMPACT));
            holder.itemView.setSelected(position == selectedPosition);
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public int getItemCount() {
        return mData.length();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener, View.OnLongClickListener {
        public TextView textView;

        public LinearLayout background;
        private OnItemClickListener listener;
        private OnItemLongClickListener listenerLong;

        public ViewHolder(View itemView, OnItemClickListener listener, OnItemLongClickListener listenerLong) {
            super(itemView);
            textView = itemView.findViewById(R.id.textView);
            background = itemView.findViewById(R.id.background);

            this.listener = listener;
            this.listenerLong = listenerLong;
            itemView.setOnClickListener(this);
            itemView.setOnLongClickListener(this);

            // 确保itemView可以接收选中状态
            itemView.setClickable(true);
            itemView.setFocusable(true);
        }

        @Override
        public void onClick(View v) {
            int position = getAdapterPosition();
            if (position != RecyclerView.NO_POSITION && listener != null) {
                listener.onItemClick(position);
            }
        }

        @Override
        public boolean onLongClick(View v) { // 实现这个方法，注意返回值是boolean
            int position = getAdapterPosition();
            if (position != RecyclerView.NO_POSITION && listenerLong != null) {
                listenerLong.onItemLongClick(v, position);

                //background.setBackgroundColor(Color.rgb(255, 0, 0));
            }
            return true; // 返回true表示已经处理了长按事件
        }
    }

    // 添加顺序改变监听接口
    public interface OnOrderChangedListener {
        void onOrderChanged(int fromPosition, int toPosition, JSONArray newOrder);
    }

    OnOrderChangedListener orderChangedListener;

    public void setOnOrderChangedListener(OnOrderChangedListener listener) {
        this.orderChangedListener = listener;
    }

    // 获取当前顺序的JSONArray
    public JSONArray getCurrentOrder() {
        return mData;
    }

    public interface OnItemClickListener {
        void onItemClick(int position);
    }
    public interface OnItemLongClickListener {
        void onItemLongClick(View view, int position);
    }
}
