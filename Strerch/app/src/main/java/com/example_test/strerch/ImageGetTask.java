package com.example_test.strerch;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.widget.ImageView;

/**
 * メインスレッドとは別スレッドで非同期処理が行える AsyncTask を継承
 */
class ImageGetTask extends AsyncTask<String, Void, Bitmap> {

    private ImageView image;
    private String tag;

    /**
     * image ... 表示したい ImageView (holder.artworkImageView)
     * tag   ... アルバムアート画像の path
     */
    ImageGetTask(ImageView _image){
        super();
        image = _image;
        tag = image.getTag().toString();
    }

    /**
     * アルバムタイトルからコンテントプロバイダ経由でアルバムアートを取得する
     */
    static String searchArtPath(Context context, String album) {

        return "hoge";
    }

    /**
     * cache から画像をとってくる
     * なければ decode して cache に setImage
     */
    @Override
    protected Bitmap doInBackground(String ... params) {
        Bitmap bitmap = ImageCache.getImage(params[0]);
        if (bitmap == null) {
            bitmap = decodeBitmap(params[0], 72, 72);
            ImageCache.setImage(params[0], bitmap);
        }
        return bitmap;
    }

    /**
     * 最終的に holder.artworkImageView に setImageBitmap するメソッド
     */
    @Override
    protected void onPostExecute(Bitmap result) {
        if (tag.equals(image.getTag())) image.setImageBitmap(result);
    }

    /**
     * bitmap をデコードするメソッド
     * @param path
     * @param width
     * @param height
     * @return path と options を指定して BitmapFactory.decodeFile でデコードした結果
     */
    private static Bitmap decodeBitmap(String path, int width, int height) {
        final BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(path, options);
        options.inSampleSize = calculateInSampleSize(options, width, height);
        options.inJustDecodeBounds = false;
        return BitmapFactory.decodeFile(path, options);
    }

    private static int calculateInSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight) {
        final int height = options.outHeight;
        final int width = options.outWidth;
        int inSampleSize = 1;

        if (height > reqHeight || width > reqWidth) {
            if (width > height) {
                inSampleSize = Math.round((float)height / (float)reqHeight);
            } else {
                inSampleSize = Math.round((float)width / (float)reqWidth);
            }
        }
        return inSampleSize;
    }

}
