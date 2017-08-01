package com.ghondar.vlcplayer;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.PixelFormat;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import org.videolan.libvlc.IVLCVout;
import org.videolan.libvlc.LibVLC;
import org.videolan.libvlc.Media;
import org.videolan.libvlc.MediaPlayer;
import org.videolan.libvlc.util.VLCUtil;

import java.util.ArrayList;
import com.vlcplayer.R;

public class PlayerActivity extends Activity implements IVLCVout.Callback {

    public final static String LOCATION = "srcVideo";
    public final static String CONSOLE = "console";

    private String mFilePath;
    private AudioManager audioManager;

    // display surface
    private LinearLayout layout;
    private FrameLayout vlcOverlay;
    private SurfaceView mSurface;
    private SurfaceHolder holder;
    private ImageView vlcButtonPlayPause;
    private ImageButton vlcButtonScale;
    private Handler handlerOverlay;
    private Runnable runnableOverlay;
    private ImageView ivBack;
    private ImageView ivVoiceStatue;
    private TextView tvPlayedTime;
    private TextView tvTotalTime;
    private SeekBar sbTime;
    private RelativeLayout rlConsole;

    private long totalTime;
    private int progress;

    // media player
    private LibVLC libvlc;
    private MediaPlayer mMediaPlayer = null;
    private int mVideoHeight;
    private int mVideoWidth;
    private int mVideoVisibleHeight;
    private int mVideoVisibleWidth;
    private int mSarNum;
    private int mSarDen;
    private MediaPlayer.EventListener eventListener;
    private ProgressDialog dialog;

    private int voice;

    private int counter = 0;

    private static final int SURFACE_BEST_FIT = 0;
    private static final int SURFACE_FIT_HORIZONTAL = 1;
    private static final int SURFACE_FIT_VERTICAL = 2;
    private static final int SURFACE_FILL = 3;
    private static final int SURFACE_16_9 = 4;
    private static final int SURFACE_4_3 = 5;
    private static final int SURFACE_ORIGINAL = 6;

    private int mCurrentSize = SURFACE_BEST_FIT;

    /*************
     * Activity
     *************/
    private int voiceImage = R.drawable.voice;
    private int muteImage = R.drawable.mute;
    private final long timeToDisappear = 3000;
    private boolean isConsole;
    private MyVolumeReceiver mVolumeReceiver;

     @Override     
     public void onCreate(Bundle savedInstanceState) {         
	super.onCreate(savedInstanceState);         
	setContentView(R.layout.player); 	
	Intent intent = getIntent();         
	mFilePath = intent.getExtras().getString(LOCATION);         
	isConsole = intent.getExtras().getBoolean(CONSOLE,false);         
	initView();         
	initListener();         
	playMovie();     
     }

    private void initView() {
        layout = (LinearLayout) findViewById(R.id.vlc_container);
        mSurface = (SurfaceView) findViewById(R.id.vlc_surface);
        vlcOverlay = (FrameLayout) findViewById(R.id.vlc_overlay);
        vlcButtonPlayPause = (ImageView) findViewById(R.id.vlc_button_play_pause);
        vlcButtonScale = (ImageButton) findViewById(R.id.vlc_button_scale);
        ivBack = (ImageView) findViewById(R.id.iv_back);
        ivVoiceStatue = (ImageView) findViewById(R.id.iv_voice_statue);
        tvPlayedTime = (TextView) findViewById(R.id.tv_played_time);
        tvTotalTime = (TextView) findViewById(R.id.tv_total_time);
        sbTime = (SeekBar) findViewById(R.id.sb_time);
        rlConsole = (RelativeLayout) findViewById(R.id.rl_console);
        audioManager = (AudioManager) this.getSystemService(Context.AUDIO_SERVICE);
        voice = audioManager.getStreamVolume(AudioManager.STREAM_SYSTEM);
        if (voice == 0) {
            ivVoiceStatue.setImageDrawable(getResources().getDrawable(muteImage));
        } else {
            ivVoiceStatue.setImageDrawable(getResources().getDrawable(voiceImage));
        }
        if(isConsole == false) {
            rlConsole.setVisibility(View.GONE);
        }
        dialog = new ProgressDialog(this);
        dialog.setTitle("视频缓存");
        dialog.setMessage("正在努力加载中 ...");
        dialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
        dialog.setCancelable(true);
        dialog.setCanceledOnTouchOutside(false);
        dialog.show();
    }

    private void initListener() {
        ivBack.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                finish();
            }
        });
        ivVoiceStatue.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (voice == 0) {
                    ivVoiceStatue.setImageDrawable(getResources().getDrawable(voiceImage));
                } else {
                    ivVoiceStatue.setImageDrawable(getResources().getDrawable(muteImage));
                }
            }
        });
        registerReceiver();
        sbTime.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean b) {
                if ((mMediaPlayer != null && !mMediaPlayer.isSeekable()) || totalTime == 0) {
                    return;
                }
                if (progress > totalTime) {
                    progress = (int) totalTime;
                }
                tvPlayedTime.setText(SystemUtil.getMediaTime(progress));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                progress = seekBar.getProgress();
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                if ((mMediaPlayer != null && !mMediaPlayer.isSeekable()) || totalTime == 0) {
                    sbTime.setProgress(progress);
                    tvPlayedTime.setText(SystemUtil.getMediaTime(progress));
                    return;
                }
                int progress = seekBar.getProgress();
                if (progress > totalTime) {
                    progress = (int) totalTime;
                }
                mMediaPlayer.setTime((long) progress);
            }
        });
        eventListener = new MediaPlayer.EventListener() {
            @Override
            public void onEvent(MediaPlayer.Event event) {
                try {
                    if (event.getTimeChanged() == 0 || totalTime == 0) {
                        return;
                    }
                    sbTime.setProgress((int) event.getTimeChanged());
                    tvPlayedTime.setText(SystemUtil.getMediaTime((int) event.getTimeChanged()));
                    if (mMediaPlayer.getPlayerState() == Media.State.Ended) {
                        sbTime.setProgress(0);
                        mMediaPlayer.setTime(0);
                        tvTotalTime.setText(SystemUtil.getMediaTime((int) totalTime));
                        mMediaPlayer.stop();
                        vlcButtonPlayPause.setImageDrawable(getResources().getDrawable(R.drawable.ic_action_pause_over_video));
                        vlcOverlay.setVisibility(View.VISIBLE);
                    }
                    if (mMediaPlayer.getPlayerState() == Media.State.Playing) {
                        vlcButtonPlayPause.setImageDrawable(getResources().getDrawable(R.drawable.ic_action_play_over_video));
                        dialog.dismiss();
                    }
                } catch (Exception e) {

                }
            }
        };
        vlcButtonPlayPause.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(mMediaPlayer == null) {
                    return;
                }
                if (mMediaPlayer.isPlaying()) {
                    mMediaPlayer.pause();
                    vlcButtonPlayPause.setImageDrawable(getResources().getDrawable(R.drawable.ic_action_pause_over_video));
                } else {
                    mMediaPlayer.play();
                    vlcButtonPlayPause.setImageDrawable(getResources().getDrawable(R.drawable.ic_action_play_over_video));
                }
            }
        });
        vlcButtonScale.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (mCurrentSize < SURFACE_ORIGINAL) {
                    mCurrentSize++;
                } else {
                    mCurrentSize = 0;
                }
                changeSurfaceSize(true);
            }
        });
        // OVERLAY
        handlerOverlay = new Handler();
        runnableOverlay = new Runnable() {
            @Override
            public void run() {
                vlcOverlay.setVisibility(View.GONE);
                toggleFullscreen(true);
            }
        };

        handlerOverlay.postDelayed(runnableOverlay, timeToDisappear);
        layout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                vlcOverlay.setVisibility(View.VISIBLE);
                handlerOverlay.removeCallbacks(runnableOverlay);
                handlerOverlay.postDelayed(runnableOverlay, timeToDisappear);
            }
        });
    }

    private void registerReceiver() {
        mVolumeReceiver = new MyVolumeReceiver();
        IntentFilter filter = new IntentFilter();
        filter.addAction("android.media.VOLUME_CHANGED_ACTION");
        registerReceiver(mVolumeReceiver, filter);
    }

    private class MyVolumeReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals("android.media.VOLUME_CHANGED_ACTION")) {
                int currVolume = audioManager.getStreamVolume(AudioManager.STREAM_SYSTEM);
                if (currVolume == voice) {
                    return;
                }
                voice = currVolume;
                if (currVolume == 0) {
                    ivVoiceStatue.setImageDrawable(getResources().getDrawable(R.drawable.mute));
                } else {
                    ivVoiceStatue.setImageDrawable(getResources().getDrawable(R.drawable.voice));
                }
            }
        }
    }

    public void playMovie() {
        if (mMediaPlayer != null && mMediaPlayer.isPlaying())
            return;
        holder = mSurface.getHolder();
        createPlayer(mFilePath);
    }

    private void toggleFullscreen(boolean fullscreen) {
        WindowManager.LayoutParams attrs = getWindow().getAttributes();
        if (fullscreen) {
            attrs.flags |= WindowManager.LayoutParams.FLAG_FULLSCREEN;
            layout.setSystemUiVisibility(View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_FULLSCREEN);
        } else {
            attrs.flags &= ~WindowManager.LayoutParams.FLAG_FULLSCREEN;
        }
        getWindow().setAttributes(attrs);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        changeSurfaceLayout();
    }

    @Override
    protected void onResume() {
        resumePlay();
        super.onResume();
    }

    @Override
    protected void onPause() {
        pausePlay();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        releasePlayer();
        super.onDestroy();
    }

    /*************
     * Surface
     *************/
    @SuppressWarnings("SuspiciousNameCombination")
    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
    private void changeSurfaceSize(boolean message) {
        int screenWidth = getWindow().getDecorView().getWidth();
        int screenHeight = getWindow().getDecorView().getHeight();

        if (mMediaPlayer != null) {
            final IVLCVout vlcVout = mMediaPlayer.getVLCVout();
            vlcVout.setWindowSize(screenWidth, screenHeight);
        }

        double displayWidth = screenWidth, displayHeight = screenHeight;

        if (screenWidth < screenHeight) {
            displayWidth = screenHeight;
            displayHeight = screenWidth;
        }

        // sanity check
        if (displayWidth * displayHeight <= 1 || mVideoWidth * mVideoHeight <= 1) {
            return;
        }

        // compute the aspect ratio
        double aspectRatio, visibleWidth;
        if (mSarDen == mSarNum) {
            /* No indication about the density, assuming 1:1 */
            visibleWidth = mVideoVisibleWidth;
            aspectRatio = (double) mVideoVisibleWidth / (double) mVideoVisibleHeight;
        } else {
            /* Use the specified aspect ratio */
            visibleWidth = mVideoVisibleWidth * (double) mSarNum / mSarDen;
            aspectRatio = visibleWidth / mVideoVisibleHeight;
        }

        // compute the display aspect ratio
        double displayAspectRatio = displayWidth / displayHeight;

        counter++;
        switch (mCurrentSize) {
            case SURFACE_BEST_FIT:
                if (counter > 2) {

                }
                if (displayAspectRatio < aspectRatio)
                    displayHeight = displayWidth / aspectRatio;
                else
                    displayWidth = displayHeight * aspectRatio;
                break;
            case SURFACE_FIT_HORIZONTAL:
                displayHeight = displayWidth / aspectRatio;
                break;
            case SURFACE_FIT_VERTICAL:
                displayWidth = displayHeight * aspectRatio;
                break;
            case SURFACE_FILL:
                break;
            case SURFACE_16_9:
                aspectRatio = 16.0 / 9.0;
                if (displayAspectRatio < aspectRatio)
                    displayHeight = displayWidth / aspectRatio;
                else
                    displayWidth = displayHeight * aspectRatio;
                break;
            case SURFACE_4_3:
                aspectRatio = 4.0 / 3.0;
                if (displayAspectRatio < aspectRatio)
                    displayHeight = displayWidth / aspectRatio;
                else
                    displayWidth = displayHeight * aspectRatio;
                break;
            case SURFACE_ORIGINAL:
                displayHeight = mVideoVisibleHeight;
                displayWidth = visibleWidth;
                break;
        }

        // set display size
        int finalWidth = (int) Math.ceil(displayWidth * mVideoWidth / mVideoVisibleWidth);
        int finalHeight = (int) Math.ceil(displayHeight * mVideoHeight / mVideoVisibleHeight);

        SurfaceHolder holder = mSurface.getHolder();
        holder.setFixedSize(finalWidth, finalHeight);

        ViewGroup.LayoutParams lp = mSurface.getLayoutParams();
        lp.width = finalWidth;
        lp.height = finalHeight;
        mSurface.setLayoutParams(lp);
        mSurface.invalidate();
    }

    private void changeSurfaceLayout() {
        changeSurfaceSize(false);
    }

    /*************
     * Player
     *************/

    private void createPlayer(String media) {
        releasePlayer();
        try {
            final SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(this.getApplicationContext());
            // Create LibVLC
            // TODO: make this more robust, and sync with audio demo
            ArrayList<String> options = new ArrayList<String>(50);
            int deblocking = getDeblocking(-1);

            int networkCaching = pref.getInt("network_caching_value", 0);
            if (networkCaching > 60000)
                networkCaching = 60000;
            else if (networkCaching < 0)
                networkCaching = 0;
            //options.add("--subsdec-encoding <encoding>");
              /* CPU intensive plugin, setting for slow devices */
            options.add("--audio-time-stretch");
            options.add("--avcodec-skiploopfilter");
            options.add("" + deblocking);
            options.add("--avcodec-skip-frame");
            options.add("0");
            options.add("--avcodec-skip-idct");
            options.add("0");
            options.add("--subsdec-encoding");
//            options.add(subtitlesEncoding);
            options.add("--stats");
        /* XXX: why can't the default be fine ? #7792 */
            if (networkCaching > 0)
                options.add("--network-caching=" + networkCaching);
            options.add("--androidwindow-chroma");
            options.add("RV32");

            options.add("-vv");

            libvlc = new LibVLC(options);

            holder.setKeepScreenOn(true);

            // Create media player
            mMediaPlayer = new MediaPlayer(libvlc);
            holder.setFormat(PixelFormat.RGBX_8888);
            holder.setKeepScreenOn(true);
            mMediaPlayer.setEventListener(eventListener);

            // Set up video output
            final IVLCVout vout = mMediaPlayer.getVLCVout();
            if (!vout.areViewsAttached()) {
                vout.setVideoView(mSurface);
                vout.addCallback(this);
                vout.attachViews();
            }
            //vout.setSubtitlesView(mSurfaceSubtitles);
            Uri uri = Uri.parse(media);
            Media m = new Media(libvlc, uri);
            mMediaPlayer.setMedia(m);
            mMediaPlayer.play();
        } catch (Exception e) {
        }
    }

    private static int getDeblocking(int deblocking) {
        int ret = deblocking;
        if (deblocking < 0) {
            /**
             * Set some reasonable sDeblocking defaults:
             *
             * Skip all (4) for armv6 and MIPS by default
             * Skip non-ref (1) for all armv7 more than 1.2 Ghz and more than 2 cores
             * Skip non-key (3) for all devices that don't meet anything above
             */
            VLCUtil.MachineSpecs m = VLCUtil.getMachineSpecs();
            if (m == null)
                return ret;
            if ((m.hasArmV6 && !(m.hasArmV7)) || m.hasMips)
                ret = 4;
            else if (m.frequency >= 1200 && m.processors > 2)
                ret = 1;
            else if (m.bogoMIPS >= 1200 && m.processors > 2) {
                ret = 1;
            } else
                ret = 3;
        } else if (deblocking > 4) { // sanity check
            ret = 3;
        }
        return ret;
    }

    private void resumePlay() {
        if(mMediaPlayer == null) {
            return;
        }
        final IVLCVout vout = mMediaPlayer.getVLCVout();
        if (!vout.areViewsAttached()) {
            vout.setVideoView(mSurface);
            vout.addCallback(this);
            vout.attachViews();
        }
        mMediaPlayer.setEventListener(eventListener);
        vlcButtonPlayPause.setImageDrawable(getResources().getDrawable(R.drawable.ic_action_play_over_video));
        mMediaPlayer.play();
    }
    private void pausePlay() {
        if (mMediaPlayer.isPlaying()) {
            mMediaPlayer.pause();
            vlcButtonPlayPause.setImageDrawable(getResources().getDrawable(R.drawable.ic_action_pause_over_video));
        }
        final IVLCVout vout = mMediaPlayer.getVLCVout();
        vout.detachViews();
        vout.removeCallback(this);
        mMediaPlayer.setEventListener(null);
    }


    // TODO: handle this cleaner
    private void releasePlayer() {
        if (libvlc == null)
            return;
        mMediaPlayer.stop();
        final IVLCVout vout = mMediaPlayer.getVLCVout();
        vout.removeCallback(this);
        vout.detachViews();
        holder = null;
        libvlc.release();
        libvlc = null;

        mVideoWidth = 0;
        mVideoHeight = 0;
        if(mVolumeReceiver != null) {
            unregisterReceiver(mVolumeReceiver);
        }
    }


    @Override
    public void onNewLayout(IVLCVout vout, int width, int height, int visibleWidth, int visibleHeight, int sarNum, int sarDen) {
        if (width * height == 0)
            return;

        // store video size
        mVideoWidth = width;
        mVideoHeight = height;
        mVideoVisibleWidth = visibleWidth;
        mVideoVisibleHeight = visibleHeight;
        mSarNum = sarNum;
        mSarDen = sarDen;
        totalTime = mMediaPlayer.getLength();
        sbTime.setMax((int) totalTime);
        tvTotalTime.setText(SystemUtil.getMediaTime((int) totalTime));
        changeSurfaceLayout();
    }

    @Override
    public void onSurfacesCreated(IVLCVout vout) {

    }

    @Override
    public void onSurfacesDestroyed(IVLCVout vout) {

    }

    @Override
    public void onHardwareAccelerationError(IVLCVout vout) {
        // Handle errors with hardware acceleration
        this.releasePlayer();
    }
}
