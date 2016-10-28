/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.example.easyexoplayerdemo.exoplayer;

import java.net.CookieHandler;
import java.net.CookieManager;
import java.net.CookiePolicy;

import com.example.easyexoplayerdemo.R;
import com.example.easyexoplayerdemo.exoplayer.DemoPlayer.RendererBuilder;
import com.google.android.exoplayer.AspectRatioFrameLayout;
import com.google.android.exoplayer.ExoPlayer;
import com.google.android.exoplayer.audio.AudioCapabilities;
import com.google.android.exoplayer.audio.AudioCapabilitiesReceiver;
import com.google.android.exoplayer.drm.UnsupportedDrmException;
import com.google.android.exoplayer.text.CaptionStyleCompat;
import com.google.android.exoplayer.text.SubtitleLayout;
import com.google.android.exoplayer.util.Util;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnKeyListener;
import android.view.View.OnTouchListener;
import android.view.accessibility.CaptioningManager;
import android.widget.Button;
import android.widget.MediaController;
import android.widget.TextView;
import android.widget.Toast;

/**
 * An activity that plays media using {@link DemoPlayer}.
 */
public class ExtractorPlayerActivity extends Activity
		implements SurfaceHolder.Callback, OnClickListener, DemoPlayer.Listener, AudioCapabilitiesReceiver.Listener {


	private static final CookieManager defaultCookieManager;

	static {
		defaultCookieManager = new CookieManager();
		defaultCookieManager.setCookiePolicy(CookiePolicy.ACCEPT_ORIGINAL_SERVER);
	}

	private MediaController mediaController;
	private View debugRootView;
	private View shutterView;
	private AspectRatioFrameLayout videoFrame;
	private SurfaceView surfaceView;
	private TextView playerStateTextView;
	private SubtitleLayout subtitleLayout;
	private Button retryButton;

	private DemoPlayer player;
	private boolean playerNeedsPrepare;

	private long playerPosition;
	private boolean enableBackgroundAudio;

	private Uri contentUri;

	private AudioCapabilitiesReceiver audioCapabilitiesReceiver;

	// Activity lifecycle
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		setContentView(R.layout.player_activity);
		View root = findViewById(R.id.root);
		root.setOnTouchListener(new OnTouchListener() {
			@Override
			public boolean onTouch(View view, MotionEvent motionEvent) {
				if (motionEvent.getAction() == MotionEvent.ACTION_DOWN) {
					toggleControlsVisibility();
				} else if (motionEvent.getAction() == MotionEvent.ACTION_UP) {
					view.performClick();
				}
				return true;
			}
		});
		root.setOnKeyListener(new OnKeyListener() {
			@Override
			public boolean onKey(View v, int keyCode, KeyEvent event) {
				if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_ESCAPE
						|| keyCode == KeyEvent.KEYCODE_MENU) {
					return false;
				}
				return mediaController.dispatchKeyEvent(event);
			}
		});

		shutterView = findViewById(R.id.shutter);
		debugRootView = findViewById(R.id.controls_root);

		videoFrame = (AspectRatioFrameLayout) findViewById(R.id.video_frame);
		surfaceView = (SurfaceView) findViewById(R.id.surface_view);
		surfaceView.getHolder().addCallback(this);

		playerStateTextView = (TextView) findViewById(R.id.player_state_view);
		subtitleLayout = (SubtitleLayout) findViewById(R.id.subtitles);

		mediaController = new MediaController(this);
		mediaController.setAnchorView(root);
		retryButton = (Button) findViewById(R.id.retry_button);
		retryButton.setOnClickListener(this);

		CookieHandler currentHandler = CookieHandler.getDefault();
		if (currentHandler != defaultCookieManager) {
			CookieHandler.setDefault(defaultCookieManager);
		}

		audioCapabilitiesReceiver = new AudioCapabilitiesReceiver(this, this);
		audioCapabilitiesReceiver.register();
		
	}

	@Override
	public void onNewIntent(Intent intent) {
		releasePlayer();
		playerPosition = 0;
		setIntent(intent);
	}

	@Override
	public void onResume() {
		super.onResume();
		Intent intent = getIntent();
		contentUri = intent.getData();
		configureSubtitleView();
		if (player == null) {
			preparePlayer(true);
		} else {
			player.setBackgrounded(false);
		}
	}

	@Override
	public void onPause() {
		super.onPause();
		if (!enableBackgroundAudio) {
			releasePlayer();
		} else {
			player.setBackgrounded(true);
		}
		shutterView.setVisibility(View.VISIBLE);
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		audioCapabilitiesReceiver.unregister();
		releasePlayer();
	}

	// OnClickListener methods

	@Override
	public void onClick(View view) {
		if (view == retryButton) {
			preparePlayer(true);
		}
	}

	// AudioCapabilitiesReceiver.Listener methods

	@Override
	public void onAudioCapabilitiesChanged(AudioCapabilities audioCapabilities) {
		if (player == null) {
			return;
		}
		boolean backgrounded = player.getBackgrounded();
		boolean playWhenReady = player.getPlayWhenReady();
		releasePlayer();
		preparePlayer(playWhenReady);
		player.setBackgrounded(backgrounded);
	}

	// Internal methods

	private RendererBuilder getRendererBuilder() {
		String userAgent = Util.getUserAgent(this, "ExoPlayerDemo");
		ExtractorRendererBuilder builder=new ExtractorRendererBuilder(this, userAgent, contentUri);
		return builder;
	}

	private void preparePlayer(boolean playWhenReady) {
		if (player == null) {
			player = new DemoPlayer(getRendererBuilder());
			player.addListener(this);
			player.seekTo(playerPosition);
			playerNeedsPrepare = true;
			mediaController.setMediaPlayer(player.getPlayerControl());
			mediaController.setEnabled(true);
		}
		if (playerNeedsPrepare) {
			player.prepare();
			playerNeedsPrepare = false;
			updateButtonVisibilities();
		}
		player.setSurface(surfaceView.getHolder().getSurface());
		player.setPlayWhenReady(playWhenReady);
	}

	private void releasePlayer() {
		if (player != null) {
			playerPosition = player.getCurrentPosition();
			player.release();
			player = null;
		}
	}

	// DemoPlayer.Listener implementation

	@Override
	public void onStateChanged(boolean playWhenReady, int playbackState) {
		if (playbackState == ExoPlayer.STATE_ENDED) {
			showControls();
		}
		String text = "playWhenReady=" + playWhenReady + ", playbackState=";
		switch (playbackState) {
		case ExoPlayer.STATE_BUFFERING:
			text += "buffering";
			break;
		case ExoPlayer.STATE_ENDED:
			text += "ended";
			break;
		case ExoPlayer.STATE_IDLE:
			text += "idle";
			break;
		case ExoPlayer.STATE_PREPARING:
			text += "preparing";
			break;
		case ExoPlayer.STATE_READY:
			text += "ready";
			break;
		default:
			text += "unknown";
			break;
		}
		playerStateTextView.setText(text);
		updateButtonVisibilities();
	}

	@Override
	public void onError(Exception e) {
		if (e instanceof UnsupportedDrmException) {
			// Special case DRM failures.
			Toast.makeText(getApplicationContext(), "DRM error", Toast.LENGTH_LONG).show();
		}
		playerNeedsPrepare = true;
		updateButtonVisibilities();
		showControls();
	}

	@Override
	public void onVideoSizeChanged(int width, int height, int unappliedRotationDegrees, float pixelWidthAspectRatio) {
		shutterView.setVisibility(View.GONE);
		videoFrame.setAspectRatio(height == 0 ? 1 : (width * pixelWidthAspectRatio) / height);
	}

	// User controls

	private void updateButtonVisibilities() {
//		retryButton.setVisibility(playerNeedsPrepare ? View.VISIBLE : View.GONE);
//		videoButton.setVisibility(haveTracks(DemoPlayer.TYPE_VIDEO) ? View.VISIBLE : View.GONE);
//		audioButton.setVisibility(haveTracks(DemoPlayer.TYPE_AUDIO) ? View.VISIBLE : View.GONE);
//		textButton.setVisibility(haveTracks(DemoPlayer.TYPE_TEXT) ? View.VISIBLE : View.GONE);
	
		if(haveTracks(DemoPlayer.TYPE_VIDEO)){
			showToast("视频加载完毕");
		}
		if(haveTracks(DemoPlayer.TYPE_AUDIO)){
			showToast("音频加载完毕");
		}
		if(haveTracks(DemoPlayer.TYPE_TEXT)){
			showToast("字幕加载完毕");
		}
	}
	
	private void showToast(String text){
		Toast.makeText(this, text, Toast.LENGTH_SHORT).show();
	}
	
	private boolean haveTracks(int type) {
		return player != null && player.getTrackCount(type) > 0;
	}


	private void toggleControlsVisibility() {
		if (mediaController.isShowing()) {
			mediaController.hide();
			debugRootView.setVisibility(View.GONE);
		} else {
			showControls();
		}
	}

	private void showControls() {
		mediaController.show(0);
		debugRootView.setVisibility(View.VISIBLE);
	}


	// SurfaceHolder.Callback implementation

	@Override
	public void surfaceCreated(SurfaceHolder holder) {
		if (player != null) {
			player.setSurface(holder.getSurface());
		}
	}

	@Override
	public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
		// Do nothing.
	}

	@Override
	public void surfaceDestroyed(SurfaceHolder holder) {
		if (player != null) {
			player.blockingClearSurface();
		}
	}

	private void configureSubtitleView() {
		CaptionStyleCompat style;
		float fontScale;
		if (Util.SDK_INT >= 19) {
			style = getUserCaptionStyleV19();
			fontScale = getUserCaptionFontScaleV19();
		} else {
			style = CaptionStyleCompat.DEFAULT;
			fontScale = 1.0f;
		}
		subtitleLayout.setStyle(style);
		subtitleLayout.setFractionalTextSize(SubtitleLayout.DEFAULT_TEXT_SIZE_FRACTION * fontScale);
	}

	@TargetApi(19)
	private float getUserCaptionFontScaleV19() {
		CaptioningManager captioningManager = (CaptioningManager) getSystemService(Context.CAPTIONING_SERVICE);
		return captioningManager.getFontScale();
	}

	@TargetApi(19)
	private CaptionStyleCompat getUserCaptionStyleV19() {
		CaptioningManager captioningManager = (CaptioningManager) getSystemService(Context.CAPTIONING_SERVICE);
		return CaptionStyleCompat.createFromCaptionStyle(captioningManager.getUserStyle());
	}

}
