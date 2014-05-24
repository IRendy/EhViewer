/*
 * Copyright (C) 2014 Hippo Seven
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hippo.ehviewer.ui;

import java.io.File;
import java.io.IOException;
import java.util.List;

import org.apache.commons.lang3.StringEscapeUtils;

import com.hippo.ehviewer.AppContext;
import com.hippo.ehviewer.ImageGeterManager;
import com.hippo.ehviewer.R;
import com.hippo.ehviewer.service.DownloadService;
import com.hippo.ehviewer.service.DownloadServiceConnection;
import com.hippo.ehviewer.util.Cache;
import com.hippo.ehviewer.util.Config;
import com.hippo.ehviewer.util.Download;
import com.hippo.ehviewer.util.Ui;
import com.hippo.ehviewer.util.Util;
import com.hippo.ehviewer.widget.AlertButton;
import com.hippo.ehviewer.widget.DialogBuilder;
import com.hippo.ehviewer.widget.DownloadItemLayout;
import com.hippo.ehviewer.widget.LoadImageView;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.os.Bundle;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView.OnItemLongClickListener;

public class DownloadActivity extends AbstractActivity {
    
    private static final String TAG = "DownloadActivity";
    
    private DownloadServiceConnection mServiceConn = new DownloadServiceConnection();
    
    private AppContext mAppContext;
    private ImageGeterManager mImageGeterManager;
    
    private List<DownloadInfo> mDownloadInfos;
    private DlAdapter mDlAdapter;
    private int longClickItemIndex;
    
    private AlertDialog longClickDialog;
    
    private AlertDialog setLongClickDialog() {
        return new DialogBuilder(this).setTitle(R.string.what_to_do)
                .setItems(R.array.download_item_long_click, new OnItemClickListener() {
                    @Override
                    public void onItemClick(AdapterView<?> arg0, View arg1,
                            int position, long arg3) {
                        switch (position) {
                        case 0: // Remove info item
                            mServiceConn.getService().cancel(Download.getKey(longClickItemIndex));
                            Download.remove(longClickItemIndex);
                            mDlAdapter.notifyDataSetChanged();
                            break;
                        case 1: // Remove info item
                            mServiceConn.getService().cancel(Download.getKey(longClickItemIndex));
                            mDlAdapter.notifyDataSetChanged();
                            // TODO 有人反馈 Download.get(longClickItemIndex).title 为空，看来很有必要在正则抓取时检查是否为空
                            File dir = new File(Config.getDownloadPath(), StringEscapeUtils.escapeHtml4(Download.get(longClickItemIndex).title));
                            try {
                                Util.deleteContents(dir);
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                            dir.delete();
                            Download.remove(longClickItemIndex);
                            break;
                        case 2:
                            DownloadInfo di= mDownloadInfos.get(longClickItemIndex);
                            Intent intent = new Intent(DownloadActivity.this,
                                    MangaDownloadActivity.class);
                            intent.putExtra(MangaDownloadActivity.KEY_TITLE, di.title);
                            intent.putExtra(MangaDownloadActivity.KEY_SIZE, di.pageSum);
                            intent.putExtra(MangaDownloadActivity.KEY_GID, Integer.parseInt(di.gid));
                            intent.putExtra(MangaDownloadActivity.KEY_END_INDEX, di.lastStartIndex);
                            startActivity(intent);
                        default:
                            break;
                        }
                        longClickDialog.cancel();
                    }
                }).setNegativeButton(android.R.string.cancel, new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        ((AlertButton)v).dialog.dismiss();
                    }
                }).create();
    }
    
    private class DlAdapter extends BaseAdapter {
        private LayoutInflater mInflater;
        
        public DlAdapter() {
            mInflater = LayoutInflater.from(DownloadActivity.this);
        }
        
        @Override
        public int getCount() {
            return mDownloadInfos.size();
        }

        @Override
        public Object getItem(int arg0) {
            return mDownloadInfos.get(arg0);
        }

        @Override
        public long getItemId(int position) {
            return 0;
        }
        
        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            DownloadInfo di= mDownloadInfos.get(position);
            if (convertView != null) {
                DownloadItemLayout view = (DownloadItemLayout)convertView;
                if (!view.gid.equals(di.gid)) {
                    view.gid = di.gid;
                    view.status = di.status;
                    view.type = di.type;
                    view.lastStartIndex = di.lastStartIndex;
                    view.downloadSize = di.downloadSize;
                    view.isWait = false;
                    
                    LoadImageView thumb = (LoadImageView)view
                            .findViewById(R.id.thumb);
                    Bitmap bmp = null;
                    if (Cache.memoryCache != null &&
                            (bmp = Cache.memoryCache.get(di.gid)) != null) {
                        thumb.setLoadInfo(di.thumb, di.gid);
                        thumb.setImageBitmap(bmp);
                        thumb.setState(LoadImageView.LOADED);
                    } else {
                        thumb.setImageDrawable(null);
                        thumb.setLoadInfo(di.thumb, di.gid);
                        thumb.setState(LoadImageView.NONE);
                        mImageGeterManager.add(di.thumb, di.gid,
                                ImageGeterManager.DISK_CACHE | ImageGeterManager.DOWNLOAD,
                                new LoadImageView.SimpleImageGetListener(thumb));
                    }
                    
                    TextView title = (TextView)view.findViewById(R.id.title);
                    title.setText(di.title);
                    
                    setDownloadInfo(view, di);
                } else if (view.status != di.status || view.isWait) {
                    view.status = di.status;
                    view.type = di.type;
                    view.lastStartIndex = di.lastStartIndex;
                    view.downloadSize = di.downloadSize;
                    view.isWait = false;
                    
                    setDownloadInfo(view, di);
                } else if (view.status == DownloadInfo.DOWNLOADING &&
                        (view.type != di.type
                        || view.lastStartIndex != di.lastStartIndex
                        || view.downloadSize != di.downloadSize)) {
                    view.type = di.type;
                    view.lastStartIndex = di.lastStartIndex;
                    view.downloadSize = di.downloadSize;
                    
                    ProgressBar pb = (ProgressBar)view.findViewById(R.id.progressBar);
                    TextView info = (TextView)view.findViewById(R.id.info);
                    
                    if (di.type == DownloadInfo.DETAIL_URL) {
                        pb.setIndeterminate(true);
                        info.setText(R.string.downloading);
                    } else {
                        pb.setIndeterminate(false);
                        pb.setMax(di.pageSum);
                        pb.setProgress(di.lastStartIndex);
                        StringBuilder sb = new StringBuilder(getString(R.string.downloading));
                        sb.append("  ").append(di.lastStartIndex).append(" / ").append(di.pageSum).append("\n");
                        if (di.totalSize != 0)
                            sb.append(String.format("%.2f", di.downloadSize))
                                    .append(" / ").append(String.format("%.2f", di.totalSize)).append(" KB");
                        info.setText(sb.toString());
                    }
                }
                return view;
            }
            
            DownloadItemLayout view = (DownloadItemLayout)mInflater.inflate(R.layout.download_item, null);
            view.gid = di.gid;
            view.status = di.status;
            view.type = di.type;
            view.lastStartIndex = di.lastStartIndex;
            view.downloadSize = di.downloadSize;
            view.isWait = false;
            
            LoadImageView thumb = (LoadImageView)view
                    .findViewById(R.id.thumb);
            Bitmap bmp = null;
            if (Cache.memoryCache != null &&
                    (bmp = Cache.memoryCache.get(di.gid)) != null) {
                thumb.setLoadInfo(di.thumb, di.gid);
                thumb.setImageBitmap(bmp);
                thumb.setState(LoadImageView.LOADED);
            } else {
                thumb.setImageDrawable(null);
                thumb.setLoadInfo(di.thumb, di.gid);
                thumb.setState(LoadImageView.NONE);
                mImageGeterManager.add(di.thumb, di.gid,
                        ImageGeterManager.DISK_CACHE | ImageGeterManager.DOWNLOAD,
                        new LoadImageView.SimpleImageGetListener(thumb));
            }
            
            TextView title = (TextView)view.findViewById(R.id.title);
            title.setText(di.title);
            
            setDownloadInfo(view, di);
            
            return view;
        }
        
        /**
         * For clickable button, when click change message to "Please wait", then when action over change message
         * 
         * @param view
         * @param di
         * @param position
         */
        private void setDownloadInfo(final DownloadItemLayout view, final DownloadInfo di) {
            final ProgressBar pb = (ProgressBar)view.findViewById(R.id.progressBar);
            final TextView info = (TextView)view.findViewById(R.id.info);
            final ImageView action = (ImageView)view.findViewById(R.id.action);
            
            if (di.status == DownloadInfo.STOP) {
                pb.setVisibility(View.GONE);
                String meg = getString(R.string.not_started) + (di.type == DownloadInfo.DETAIL_URL ? ""
                        : ("  " + (di.lastStartIndex) + " / " + di.pageSum));
                info.setText(meg);
                action.setImageResource(R.drawable.ic_action_start);
                action.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        pb.setVisibility(View.GONE);
                        info.setText(R.string.wait);
                        action.setClickable(false);
                        view.isWait = true;
                        
                        Intent it = new Intent(DownloadActivity.this, DownloadService.class);
                        startService(it);
                        mServiceConn.getService().add(di);
                    }
                });
            } else if (di.status == DownloadInfo.DOWNLOADING) {
                pb.setVisibility(View.VISIBLE);
                if (di.type == DownloadInfo.DETAIL_URL) {
                    pb.setIndeterminate(true);
                    info.setText(R.string.downloading);
                } else {
                    pb.setIndeterminate(false);
                    pb.setMax(di.pageSum);
                    pb.setProgress(di.lastStartIndex);
                    StringBuilder sb = new StringBuilder(getString(R.string.downloading));
                    sb.append("  ").append(di.lastStartIndex).append(" / ").append(di.pageSum).append("\n");
                    if (di.totalSize != 0)
                        sb.append(String.format("%.2f", di.downloadSize))
                                .append(" / ").append(String.format("%.2f", di.totalSize)).append(" KB");
                    info.setText(sb.toString());
                }
                action.setImageResource(R.drawable.ic_action_pause);
                action.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        pb.setVisibility(View.GONE);
                        info.setText(R.string.wait);
                        action.setClickable(false);
                        view.isWait = true;
                        
                        mServiceConn.getService().cancel(di.gid);
                    }
                });
            } else if (di.status == DownloadInfo.WAITING) {
                pb.setVisibility(View.GONE);
                info.setText(R.string.waiting);
                action.setImageResource(R.drawable.ic_action_pause);
                action.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        pb.setVisibility(View.GONE);
                        info.setText(R.string.wait);
                        action.setClickable(false);
                        view.isWait = true;
                        
                        mServiceConn.getService().cancel(di.gid);
                    }
                });
            } else if (di.status == DownloadInfo.COMPLETED) {
                pb.setVisibility(View.GONE);
                info.setText(R.string.download_successfully);
                action.setImageResource(R.drawable.ic_action_completed);
                action.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        Intent intent = new Intent(DownloadActivity.this,
                                MangaDownloadActivity.class);
                        intent.putExtra(MangaDownloadActivity.KEY_TITLE, di.title);
                        intent.putExtra(MangaDownloadActivity.KEY_SIZE, di.pageSum);
                        intent.putExtra(MangaDownloadActivity.KEY_GID, Integer.parseInt(di.gid));
                        intent.putExtra(MangaDownloadActivity.KEY_END_INDEX, di.lastStartIndex);
                        startActivity(intent);
                    }
                });
            } else if (di.status == DownloadInfo.FAILED) {
                pb.setVisibility(View.GONE);
                String meg = getString(R.string.download_unsuccessfully) + (di.type == DownloadInfo.DETAIL_URL ? ""
                        : ("  " + (di.lastStartIndex) + " / " + di.pageSum));
                info.setText(meg);
                action.setImageResource(R.drawable.ic_action_start);
                action.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        pb.setVisibility(View.GONE);
                        info.setText(R.string.wait);
                        action.setClickable(false);
                        view.isWait = true;
                        
                        Intent it = new Intent(DownloadActivity.this, DownloadService.class);
                        startService(it);
                        mServiceConn.getService().add(di);
                    }
                });
            }
        }
    }
    
    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(mReceiver);
        unbindService(mServiceConn);
    }
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.download);
        
        mAppContext = (AppContext)getApplication();
        mImageGeterManager = mAppContext.getImageGeterManager();
        
        Ui.translucent(this);
        
        // Download service
        Intent it = new Intent(this, DownloadService.class);
        bindService(it, mServiceConn, BIND_AUTO_CREATE);
        
        IntentFilter filter = new IntentFilter();
        filter.addAction(DownloadService.ACTION_UPDATE);
        registerReceiver(mReceiver, filter);
        
        longClickDialog = setLongClickDialog();
        
        mDownloadInfos = Download.getDownloadInfoList();
        
        ListView listView = (ListView)findViewById(R.id.download);
        mDlAdapter = new DlAdapter();
        listView.setAdapter(mDlAdapter);
        listView.setOnItemLongClickListener(new OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> arg0, View arg1,
                    int position, long arg3) {
                longClickItemIndex = position;
                longClickDialog.show();
                return true;
            }
        });
    }
    
    private BroadcastReceiver mReceiver = new BroadcastReceiver(){
        @Override
        public void onReceive(Context context, Intent intent) {
            if(intent.getAction().equals(DownloadService.ACTION_UPDATE))
                mDlAdapter.notifyDataSetChanged();
        }
    };
}
