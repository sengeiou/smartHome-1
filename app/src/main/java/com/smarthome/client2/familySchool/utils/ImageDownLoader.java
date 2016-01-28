package com.smarthome.client2.familySchool.utils;

import java.io.IOException;
import java.util.HashSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;

import com.smarthome.client2.SmartHomeApplication;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapFactory.Options;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v4.util.LruCache;
import android.util.Log;
import android.util.SparseArray;

/**
 * @author n003913 图片加载类
 */
public class ImageDownLoader
{
    /**
     * 缓存Image的类，当存储Image的大小大于LruCache设定的值，系统自动释放内存
     */
    private LruCache<String, Bitmap> mMemoryCache;

    /**
     * 操作文件相关类对象的引用
     */
    private FileUtil fileUtil;

    /**
     * 下载Image的线程池
     */
    private ExecutorService mImageThreadPool = null;

    private Handler handler;

    private SparseArray<onImageLoaderListener> sArray;

    /**
     * 暂存正在下载和下载完毕的url，避免重复地下载、写入文件和回调接口。
     * XXX 如果有同时的下载请求，且url相同，回调接口索引不同，有问题。
     */
    private HashSet<String> mSet;

    private static ImageDownLoader instance;

    private int mCacheSize;

    synchronized public static ImageDownLoader getInstance()
    {
        if (instance == null)
        {
            instance = new ImageDownLoader();
        }
        return instance;
    }

    public ImageDownLoader()
    {
        // 获取系统分配给每个应用程序的最大内存，每个应用系统分配32M
        int maxMemory = (int) Runtime.getRuntime().maxMemory();
        LogUtil.d("memory", maxMemory + "#");
        mCacheSize = maxMemory / 4;
        // 给LruCache分配1/8 4M
        mMemoryCache = new LruCache<String, Bitmap>(mCacheSize)
        {
            // 必须重写此方法，来测量Bitmap的大小
            @Override
            protected int sizeOf(String key, Bitmap value)
            {
                return value.getRowBytes() * value.getHeight();
            }
        };
        mSet = new HashSet<String>();
        fileUtil = new FileUtil(SmartHomeApplication.getInstance());
        sArray = new SparseArray<onImageLoaderListener>();
        handler = new Handler()
        {
            @Override
            public void handleMessage(Message msg)
            {
                onImageLoaderListener listener = sArray.get(msg.what);
                if (listener != null)
                {
                    listener.onImageLoader((Bitmap) msg.obj, msg.getData().getString("imgurl"));
                }
            }
        };
    }

    public void addListener(int key, onImageLoaderListener listener)
    {
        sArray.put(key, listener);
    }

    /**
     * 获取线程池的方法，因为涉及到并发的问题，我们加上同步锁
     * @return
     */
    public ExecutorService getThreadPool()
    {
        if (mImageThreadPool == null)
        {
            synchronized (ExecutorService.class)
            {
                if (mImageThreadPool == null)
                {
                    // 为了下载图片更加的流畅，我们用了2个线程来下载图片
                    mImageThreadPool = Executors.newFixedThreadPool(2);
                }
            }
        }
        return mImageThreadPool;
    }

    /**
     * 添加Bitmap到内存缓存
     * @param key
     * @param bitmap
     */
    public void addBitmapToMemoryCache(String key, Bitmap bitmap)
    {
        if (getBitmapFromMemCache(key) == null && bitmap != null)
        {
            mMemoryCache.put(key, bitmap);
        }
    }

    /**
     * 从内存缓存中获取一个Bitmap
     * @param key
     * @return
     */
    public Bitmap getBitmapFromMemCache(String key)
    {
        return mMemoryCache.get(key);
    }

    /**
     * 先从内存缓存中获取Bitmap,如果没有就从SD卡或者手机缓存中获取，SD卡或者手机缓存 没有就去下载
     * @param url
     * @param flag 对应SparseArray中的key，获取对应的onImageLoaderListener来处理,flag==-1时阻塞不新开线程
     * @return
     */
    public Bitmap downloadImage(final String url, final int flag)
    {
        // 替换Url中非字母和非数字的字符，这里比较重要，因为我们用Url作为文件名，比如我们的Url
        // 是Http://xiaanming/abc.jpg;用这个作为图片名称，系统会认为xiaanming为一个目录，
        // 我们没有创建此目录保存文件就会报错
        if (url == null || url.isEmpty())
        {
            return null;
        }
        // final String subUrl = url.replaceAll("[/]", "_");
        final String subUrl = url.replaceAll("[^a-zA-Z0-9]", "_")
                .replaceAll("\\s+", "_");
        Bitmap bitmap = showCacheBitmap(subUrl);
        if (bitmap != null)
        {
        	sendMsgToCallBack(url, bitmap, flag,handler);
            return bitmap;
        }
        else
        {
            if (mSet.contains(url))
            {
                LogUtil.e("widget", "url-contains");
                return null;
            }
            mSet.add(url);
            if (flag == -1)
            {
                bitmap = getBitmapFormUrl(url);
                // 下载失败
                if (bitmap == null)
                {
                    mSet.remove(url);
                    return null;
                }
                try
                {
                    // 保存在SD卡或者手机目录
                    fileUtil.savaBitmap(subUrl, bitmap);
                }
                catch (IOException e)
                {
                    e.printStackTrace();
                }
                // 将Bitmap加入内存缓存
                addBitmapToMemoryCache(subUrl, bitmap);
                sendMsgToCallBack(url, bitmap, flag, handler);
                return bitmap;
            }
            else
            {
                getThreadPool().execute(new Runnable()
                {
                    @Override
                    public void run()
                    {
                        Bitmap bitmap = getBitmapFormUrl(url);
                        // 下载失败
                        if (bitmap == null)
                        {
                            mSet.remove(url);
                            return;
                        }
                        try
                        {
                            // 保存在SD卡或者手机目录
                            fileUtil.savaBitmap(subUrl, bitmap);
                        }
                        catch (IOException e)
                        {
                            e.printStackTrace();
                        }
                        // 将Bitmap加入内存缓存
                        addBitmapToMemoryCache(subUrl, bitmap);
                        // 通知界面刷新
                        sendMsgToCallBack(url, bitmap, flag, handler);

                    }
                });
            }
        }
        return null;
    }
    
    public Bitmap downloadImage(final String url, final int flag, final Handler imgHandle)
    {
        // 替换Url中非字母和非数字的字符，这里比较重要，因为我们用Url作为文件名，比如我们的Url
        // 是Http://xiaanming/abc.jpg;用这个作为图片名称，系统会认为xiaanming为一个目录，
        // 我们没有创建此目录保存文件就会报错
        if (url == null || url.isEmpty())
        {
            return null;
        }
        // final String subUrl = url.replaceAll("[/]", "_");
        final String subUrl = url.replaceAll("[^a-zA-Z0-9]", "_")
                .replaceAll("\\s+", "_");
        Bitmap bitmap = showCacheBitmap(subUrl);
        if (bitmap != null)
        {
        	sendMsgToCallBack(url, bitmap, flag, imgHandle);
            return bitmap;
        }
        else
        {
            if (mSet.contains(url))
            {
                LogUtil.e("widget", "url-contains");
                return null;
            }
            mSet.add(url);
            if (flag == -1)
            {
                bitmap = getBitmapFormUrl(url);
                // 下载失败
                if (bitmap == null)
                {
                    mSet.remove(url);
                    return null;
                }
                try
                {
                    // 保存在SD卡或者手机目录
                    fileUtil.savaBitmap(subUrl, bitmap);
                }
                catch (IOException e)
                {
                    e.printStackTrace();
                }
                // 将Bitmap加入内存缓存
                addBitmapToMemoryCache(subUrl, bitmap);
                sendMsgToCallBack(url, bitmap, flag, imgHandle);
                return bitmap;
            }
            else
            {
                getThreadPool().execute(new Runnable()
                {
                    @Override
                    public void run()
                    {
                        Bitmap bitmap = getBitmapFormUrl(url);
                        // 下载失败
                        if (bitmap == null)
                        {
                            mSet.remove(url);
                            return;
                        }
                        try
                        {
                            // 保存在SD卡或者手机目录
                            fileUtil.savaBitmap(subUrl, bitmap);
                        }
                        catch (IOException e)
                        {
                            e.printStackTrace();
                        }
                        // 将Bitmap加入内存缓存
                        addBitmapToMemoryCache(subUrl, bitmap);
                        // 通知界面刷新
                        sendMsgToCallBack(url, bitmap, flag,imgHandle);

                    }
                });
            }
        }
        return null;
    }
    
    private void sendMsgToCallBack(String url, Bitmap bmp, int flag, Handler imgHandler ){
    	Message msg = handler.obtainMessage();
        Bundle urlData = new Bundle();
        urlData.putString("imgurl", url);
        msg.what = flag;
        msg.obj = bmp;
        msg.setData(urlData);
        imgHandler.sendMessage(msg);
    }
    
    private void sendMsgToCallBack(String url, Bitmap bmp, int flag, Handler imgHandler, String tag ){
    	Message msg = handler.obtainMessage();
        Bundle urlData = new Bundle();
        urlData.putString("imgurl", url);
        urlData.putString("tag", tag);
        msg.what = flag;
        msg.obj = bmp;
        msg.setData(urlData);
        imgHandler.sendMessage(msg);
    }
    
    public Bitmap downloadImage(final String url, final int flag, final Handler imgHandle, final String tag)
    {
        // 替换Url中非字母和非数字的字符，这里比较重要，因为我们用Url作为文件名，比如我们的Url
        // 是Http://xiaanming/abc.jpg;用这个作为图片名称，系统会认为xiaanming为一个目录，
        // 我们没有创建此目录保存文件就会报错
        if (url == null || url.isEmpty())
        {
            return null;
        }
        // final String subUrl = url.replaceAll("[/]", "_");
        final String subUrl = url.replaceAll("[^a-zA-Z0-9]", "_")
                .replaceAll("\\s+", "_");
        Bitmap bitmap = showCacheBitmap(subUrl);
        if (bitmap != null)
        {
        	sendMsgToCallBack(url, bitmap, flag, imgHandle, tag);
            return bitmap;
        }
        else
        {
            if (mSet.contains(url))
            {
                LogUtil.e("widget", "url-contains");
                return null;
            }
            mSet.add(url);
            if (flag == -1)
            {
                bitmap = getBitmapFormUrl(url);
                // 下载失败
                if (bitmap == null)
                {
                    mSet.remove(url);
                    return null;
                }
                try
                {
                    // 保存在SD卡或者手机目录
                    fileUtil.savaBitmap(subUrl, bitmap);
                }
                catch (IOException e)
                {
                    e.printStackTrace();
                }
                // 将Bitmap加入内存缓存
                addBitmapToMemoryCache(subUrl, bitmap);
                sendMsgToCallBack(url, bitmap, flag, imgHandle, tag);
                return bitmap;
            }
            else
            {
                getThreadPool().execute(new Runnable()
                {
                    @Override
                    public void run()
                    {
                        Bitmap bitmap = getBitmapFormUrl(url);
                        // 下载失败
                        if (bitmap == null)
                        {
                            mSet.remove(url);
                            return;
                        }
                        try
                        {
                            // 保存在SD卡或者手机目录
                            fileUtil.savaBitmap(subUrl, bitmap);
                        }
                        catch (IOException e)
                        {
                            e.printStackTrace();
                        }
                        // 将Bitmap加入内存缓存
                        addBitmapToMemoryCache(subUrl, bitmap);
                        // 通知界面刷新
                        sendMsgToCallBack(url, bitmap, flag,imgHandle, tag);

                    }
                });
            }
        }
        return null;
    }

    /**
     * 获取Bitmap, 内存中没有就去手机或者sd卡中获取，这一步在getView中会调用，比较关键的一步
     * @param url
     * @return
     */
    public Bitmap showCacheBitmap(String url)
    {
        if (getBitmapFromMemCache(url) != null)
        {
            return getBitmapFromMemCache(url);
        }
        else if (fileUtil.isFileExists(url) && fileUtil.getFileSize(url) != 0)
        {
            // 从SD卡获取手机里面获取Bitmap
            Bitmap bitmap = fileUtil.getBitmap(url);
            // 将Bitmap 加入内存缓存
            addBitmapToMemoryCache(url, bitmap);
            return bitmap;
        }
        return null;
    }

    /**
     * 从Url中获取Bitmap
     * @param url
     * @return
     */
    private Bitmap getBitmapFormUrl(String url)
    {
        Bitmap bitmap = null;
        HttpParams httpParameters = new BasicHttpParams();
        HttpConnectionParams.setConnectionTimeout(httpParameters, 5000);
        HttpConnectionParams.setSoTimeout(httpParameters, 5000);
        DefaultHttpClient client = new DefaultHttpClient(httpParameters);
        HttpGet get = new HttpGet(url);
        HttpResponse response;
        try
        {
            response = client.execute(get);
            if (response.getStatusLine().getStatusCode() == 200)
            {
                HttpEntity entity = response.getEntity();
                // bitmap = BitmapFactory.decodeStream(entity.getContent());
                long size = entity.getContentLength();
                // TODO 压缩比例
                if (size >= (mCacheSize / 50))
                {
                    int scale = (int) (size / (mCacheSize / 50));
                    BitmapFactory.Options options = new Options();
                    options.inSampleSize = scale;
                    options.inJustDecodeBounds = false;
                    bitmap = BitmapFactory.decodeStream(entity.getContent(),
                            null,
                            options);
                }
                else
                {
                    bitmap = BitmapFactory.decodeStream(entity.getContent());
                }
            }
        }
        catch (ClientProtocolException e)
        {
            e.printStackTrace();
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
        return bitmap;
    }

    /**
     * 取消正在下载的任务
     */
    public synchronized void cancelTask()
    {
        if (mImageThreadPool != null)
        {
            mImageThreadPool.shutdownNow();
            mImageThreadPool = null;
        }
    }

    /**
     * 异步下载图片的回调接口
     */
    public interface onImageLoaderListener
    {
        void onImageLoader(Bitmap bitmap, String url);
    }
}
