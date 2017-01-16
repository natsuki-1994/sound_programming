package com.example_test.strerch;

import android.graphics.Typeface;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.view.PagerTabStrip;
import android.support.v4.view.ViewPager;
import android.support.v7.widget.SwitchCompat;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ListView;

import java.util.List;

/**
 * RootMenu は MainActivity 直下の Fragment (activity_main.xml の R.id.root)
 */
public class RootMenu extends Fragment {

    /**
     * Pager で Track 一覧・Artist 一覧・Album 一覧などの Fragment をスワイプで切り替える
     * MainActivity class
     *   --- RootMenu class
     *         --- menu_home    (HomeSectionFragment class)
     *         --- menu_tracks  (TrackSectionFragment class)
     *         --- menu_artists (ArtistSectionFragment class)
     *         --- menu_albums  (AlbumSectionFragment class)
     *   --- AlbumMenu class
     *   --- ArtistMenu class
     */
    SectionsPagerAdapter mSectionsPagerAdapter;
    ViewPager mViewPager;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        /**
         * menu layout を捕まえて膨らませる (inflate させる)
         * sectionPagerAdapter を mViewPager (R.id.pager) に setAdapter
         */
        View rootView = inflater.inflate(R.layout.menu, container, false);
        mSectionsPagerAdapter = new SectionsPagerAdapter(getChildFragmentManager());
        mViewPager = (ViewPager) rootView.findViewById(R.id.pager);
        mViewPager.setAdapter(mSectionsPagerAdapter);
        mViewPager.setOffscreenPageLimit(3);

        /**
        /* PagerTab のカスタマイズ
         */
        PagerTabStrip strip = (PagerTabStrip) rootView.findViewById(R.id.pager_title_strip);
        strip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        strip.setTextSpacing(50);
        strip.setNonPrimaryAlpha(0.3f);

        return rootView;
    }

    /**
     * 上記の onCreateView にて使用
     */
    public class SectionsPagerAdapter extends FragmentPagerAdapter {

        SectionsPagerAdapter(FragmentManager fm) { super(fm); }

        /**
         * ページをスワイプするごとに R.id.pager に表示する Fragment を変更
         */
        @Override
        public Fragment getItem(int position) {
            Fragment fragment = null;
            switch (position) {
                case 0: fragment = new HomeSectionFragment(); break;
                case 1: fragment = new TrackSectionFragment(); break;
                case 2: fragment = new AlbumSectionFragment(); break;
                case 3: fragment = new ArtistSectionFragment(); break;
            }
            return fragment;
        }

        @Override
        public int getCount() { return 4; }

        /**
         * ページをスワイプするごとに R.id.pager_title_strip に表示する ページ名 を変更
         */
        @Override
        public CharSequence getPageTitle(int position) {
            switch (position) {
                case 0: return getString(R.string.title_section1);
                case 1: return getString(R.string.title_section2);
                case 2: return getString(R.string.title_section3);
                case 3: return getString(R.string.title_section4);
            }
            return null;
        }

    }

    public static class HomeSectionFragment extends Fragment {

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

            MainActivity activity = (MainActivity) getActivity();

            /**
             * menu_home を捕まえて膨らませる (inflate する)
             * button クリックしたときの動作を指定（メソッドは MainActivity で指定）
             */
            View v = inflater.inflate(R.layout.menu_home, container, false);
            SwitchCompat toggleOutside = (SwitchCompat) v.findViewById(R.id.toggleOutside);
            SwitchCompat toggleSlow = (SwitchCompat) v.findViewById(R.id.toggleSlow);
            Button buttonPlayPause = (Button) v.findViewById(R.id.c_btn);
            Button buttonNext = (Button) v.findViewById(R.id.c_btn_next);
            Button buttonBack = (Button) v.findViewById(R.id.c_btn_back);

            Typeface font = Typeface.createFromAsset(activity.getAssets(), "fontawesome-webfont.ttf");
            buttonPlayPause.setTypeface(font);
            buttonNext.setTypeface(font);
            buttonBack.setTypeface(font);

            activity.focusedFragment = 1;
            activity.changeSeekBar();

            /**
             * toggleButton（toggleOutside）をクリックしたときの動作
             * メソッドは MainActivity で定義
             */
            toggleOutside.setOnCheckedChangeListener(activity.toggleOutsideClickListener);
            toggleSlow.setOnCheckedChangeListener(activity.toggleSlowClickListener);

            /**
             * Play / Pause ボタン押したとき
             */
            buttonPlayPause.setOnClickListener(activity.buttonPlayPauseClickListener);

            return v;
        }
    }

    public static class TrackSectionFragment extends Fragment {

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

            MainActivity activity = (MainActivity) getActivity();
            List<Track> tracks = Track.getItems(activity);

            /**
             * menu_tracks を捕まえて膨らませる (inflate する)
             */
            View v = inflater.inflate(R.layout.menu_tracks, container,false);
            ListView trackList = (ListView) v.findViewById(R.id.list);
            ListTrackAdapter adapter = new ListTrackAdapter(activity, tracks);
            trackList.setAdapter(adapter);

            /**
             * album をクリックしたときに RootMenu の Fragment から AlbumMenu の Fragment に変更
             * メソッドは MainActivity で定義
             */
            trackList.setOnItemClickListener(activity.TrackLongClickListener);

            activity.changeInformation();
            return v;
        }

    }

    public static class AlbumSectionFragment extends Fragment {

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

            MainActivity activity = (MainActivity) getActivity();
            List<Album> albums = Album.getItems(activity);

            /**
             * menu_albums を捕まえて膨らませる (inflate する)
             */
            View v = inflater.inflate(R.layout.menu_albums, container, false);
            ListView albumList = (ListView) v.findViewById(R.id.list);
            ListAlbumAdapter adapter = new ListAlbumAdapter(activity, albums);
            albumList.setAdapter(adapter);

            /**
             * album をクリックしたときに RootMenu の Fragment から AlbumMenu の Fragment に変更
             * メソッドは MainActivity で定義
             */
            albumList.setOnItemClickListener(activity.AlbumClickListener);
            albumList.setOnItemLongClickListener(activity.AlbumLongClickListener);

            return v;
        }
    }

    public static class ArtistSectionFragment extends Fragment {

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

            final MainActivity activity = (MainActivity) getActivity();
            List<Artist> artists = Artist.getItems(activity);

            /**
             * menu_artists を捕まえて膨らませる (inflate する)
             */
            View v = inflater.inflate(R.layout.menu_artists,container,false);
            ListView artistList = (ListView) v.findViewById(R.id.list);
            ListArtistAdapter adapter = new ListArtistAdapter(activity, artists);
            artistList.setAdapter(adapter);

            /**
             * artist をクリックしたときに RootMenu の Fragment から ArtistMenu の Fragment に変更
             * メソッドは MainActivity で定義
             */
            artistList.setOnItemClickListener(activity.ArtistClickListener);
            artistList.setOnItemLongClickListener(activity.ArtistLongClickListener);

            return v;
        }
    }
}
