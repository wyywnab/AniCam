package com.wyywn.anicam.adapter;

import android.content.Context;
import android.graphics.Color;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.android.material.color.MaterialColors;
import com.wyywn.anicam.utils.Functions;
import com.wyywn.anicam.R;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class PhotographPicListAdapter extends RecyclerView.Adapter<PhotographPicListAdapter.ViewHolder> {
    JSONArray mData;
    private OnItemClickListener mlistener;
    private OnItemLongClickListener mlistenerLong;
    private int selectedPosition = -1;
    private boolean mIsSelectionEnabled;

    public PhotographPicListAdapter(JSONArray data, OnItemClickListener listener, OnItemLongClickListener listenerLong, boolean isSelectionEnabled) {
        mData = data;
        mlistener = listener;
        mlistenerLong = listenerLong;
        mIsSelectionEnabled = isSelectionEnabled;
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
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.layout_photographpic_item, parent, false);
        return new ViewHolder(view, mlistener, mlistenerLong);
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        try {
            JSONObject item = mData.getJSONObject(position);
            /*ContentResolver resolver = holder.context.getContentResolver();

            Bitmap bmp = Functions.getBitmapFromContentUri(resolver, Uri.parse(item.getString("uri")));
            Bitmap croppedBitmap = bmp;
            if (bmp.getHeight() > bmp.getWidth() * 1.4){
                croppedBitmap = Bitmap.createBitmap(bmp, 0, 0, bmp.getWidth(), (int) (bmp.getWidth() * 1.2));
            }

            //holder.imageView.setImageBitmap(croppedBitmap);

            //File file = new File(path);
            Glide.with(holder.imageView.getContext())
                    .load(croppedBitmap)
                    .into(holder.imageView);*/

            // 使用 Glide 直接加载和裁剪图片
            Uri imageUri = Uri.parse(item.getString("uri"));
            if (!Functions.isUriFileExists(holder.context, imageUri)){
                holder.textView.setText(item.getString("name"));
                int errorColor = MaterialColors.getColor(holder.textView.getRootView(), com.google.android.material.R.attr.colorErrorContainer);
                holder.background.setBackgroundColor(errorColor);
            } else {
                holder.textView.setText(item.getString("name"));
            }
            // 使用 Glide 的变换功能进行裁剪
            Glide.with(holder.imageView.getContext())
                    .load(imageUri)
                    .override(200, 240) // 设置缩略图尺寸，减少内存占用
                    .transform(new PicCropTransformation())
                    .into(holder.imageView);

            if (mIsSelectionEnabled){
                holder.itemView.setSelected(position == selectedPosition);
            }
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
        public ImageView imageView;
        public FrameLayout background;
        private OnItemClickListener listener;
        private OnItemLongClickListener listenerLong;
        private Context context;

        public ViewHolder(View itemView, OnItemClickListener listener, OnItemLongClickListener listenerLong) {
            super(itemView);
            textView = itemView.findViewById(R.id.textView);
            imageView = itemView.findViewById(R.id.imageView);
            background = itemView.findViewById(R.id.background);

            this.listener = listener;
            this.listenerLong = listenerLong;
            itemView.setOnClickListener(this);
            itemView.setOnLongClickListener(this);

            this.context = background.getContext();

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
                return true;
                //background.setBackgroundColor(Color.rgb(255, 0, 0));
            }
            return false;
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

    public interface OnItemRemovedListener {
        void onItemRemoved(int position);
    }

    public void setOnItemRemovedListener(OnItemRemovedListener listener) {
        this.itemRemovedListener = listener;
    }

    OnItemRemovedListener itemRemovedListener;
}