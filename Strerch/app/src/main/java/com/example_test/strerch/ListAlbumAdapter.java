package com.example_test.strerch;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.support.annotation.NonNull;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.List;

class ListAlbumAdapter extends ArrayAdapter<Album> {

    private LayoutInflater mInflater;
    private static Context Mcontext;  /** ListAlbumAdapter では必要 */

    /**
     * RootMenu.java ArtistSectionFragment 内にて
     * menu_tracks の R.id.list (= trackList) に SetAdapter する
     * param : MainActivity
     * param : albums
     */
    ListAlbumAdapter(Context context, List<Album> item) {
        super(context, 0, item);
        mInflater =  (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        Mcontext = context;
    }

    @NonNull
    @Override
    public View getView(int position, View convertView, @NonNull ViewGroup parent) {
        /**
         * item は param : List<Album> item に代入された albums を取り出したもの
         */
        Album item = getItem(position);
        ViewHolder holder;

        /**
         * item_album を捕まえて膨らませる (inflate させる)
         *   item_album を捕まえて convertView に代入
         *   convertView に holder を setTag
         *   holder に setText
         */
        if (convertView == null) {
            convertView = mInflater.inflate(R.layout.item_album, null);
            holder = new ViewHolder();
            holder.albumTextView = (TextView) convertView.findViewById(R.id.title);
            holder.artistTextView = (TextView) convertView.findViewById(R.id.artist);
            holder.tracksTextView = (TextView) convertView.findViewById(R.id.tracks);
            holder.artworkImageView = (ImageView) convertView.findViewById(R.id.albumart);
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }
        assert item != null;
        holder.albumTextView.setText(item.album);
        holder.artistTextView.setText(item.artist);
        holder.tracksTextView.setText(String.valueOf(item.tracks) + "tracks");

        /**
         * holder に setImage させるのだけ別処理
         * ImageGetTask で非同期で画像を読み込む
         */
        String path = item.albumArt;
        holder.artworkImageView.setImageResource(R.mipmap.ic_launcher);  /** ひとまず適当な画像を setImage */
        if (path == null) {
            /**
             * 一応ダミー画像もイメージキャッシュに登録する
             */
            path = String.valueOf(R.mipmap.ic_launcher);
            Bitmap bitmap = ImageCache.getImage(path);
            if (bitmap == null) {
                bitmap = BitmapFactory.decodeResource(Mcontext.getResources(), R.mipmap.ic_launcher);
                ImageCache.setImage(path, bitmap);
            }
        }
        holder.artworkImageView.setTag(path);  /** 表示したい ImageView にタグとしてアルバムアートの画像 path を保存 */
        ImageGetTask task = new ImageGetTask(holder.artworkImageView);  /** ImageGetTask を生成して今の ImageView を登録 */
        task.execute(path);  /** 表示したい画像の path を与えて小タスクを実行 */

        return convertView;
    }

    /**
     * item_album に実際に表示させたい項目を設定
     */
    private static class ViewHolder{
        TextView  albumTextView;
        TextView  artistTextView;
        TextView  tracksTextView;
        ImageView artworkImageView;
    }
}
