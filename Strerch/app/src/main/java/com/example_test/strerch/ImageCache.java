package com.example_test.strerch;

import android.graphics.Bitmap;

import java.util.HashMap;

/**
 * 画像をキャッシュするクラス
 */
class ImageCache {

    private static HashMap<String, Bitmap> cache = new HashMap<>();

    /**
     * cache が key を含んでいたら image を、含んでいなかったら null を返す
     */
    static Bitmap getImage(String key) {
        if (cache.containsKey(key)) {
            return cache.get(key);
        }
        return null;
    }

    static void setImage(String key, Bitmap image) {
        cache.put(key, image);
    }

}
