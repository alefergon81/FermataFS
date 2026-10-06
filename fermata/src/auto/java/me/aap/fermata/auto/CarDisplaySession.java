package me.aap.fermata.auto;

import static me.aap.utils.ui.UiUtils.showAlert;

import android.app.Presentation;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Rect;
import android.view.Surface;
import android.view.View;
import android.view.Window;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.car.app.Screen;
import androidx.car.app.Session;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleEventObserver;
import androidx.lifecycle.LifecycleOwner;

import java.io.File;
import java.nio.file.Files;

import me.aap.fermata.BuildConfig;
import me.aap.fermata.FermataApplication;
import me.aap.fermata.media.engine.MediaEngine;
import me.aap.fermata.media.pref.MediaPrefs;
import me.aap.fermata.media.service.FermataMediaServiceConnection;
import me.aap.fermata.media.service.MediaSessionCallback;
import me.aap.fermata.ui.activity.MainActivityDelegate;
import me.aap.utils.log.Log;

/**
 * A car display session. The Session lifecycle drives the host (design D3/D4); the surface only
 * attaches to and detaches from the display, so switching to another Android Auto app keeps the
 * UI, its state and its media priority. Everything is released when the Session is destroyed.
 */
final class CarDisplaySession extends Session implements LifecycleEventObserver {
	/** Debug builds: {@code adb shell am broadcast -a <this> --ei w 1280 --ei h 600 --ei dpi 160} */
	static final String ACTION_DEBUG_RESIZE = "me.aap.fermata.auto.CAR_DISPLAY_RESIZE";
	/** Debug builds: a file in the app files dir holding a delay (ms) for the service connection. */
	static final String DEBUG_DELAY_FILE = "car_display_delay";
	private static FermataMediaServiceConnection service;
	private final Context ctx;
	private final Rect stableArea = new Rect();
	private CarDisplayScreen screen;
	private CarDisplay display;
	private CarHostActivity host;
	private FermataMediaServiceConnection connection;
	private MainActivityDelegate delegate;
	private BroadcastReceiver debugReceiver;
	private int dpi;

	CarDisplaySession(Context ctx) {
		this.ctx = ctx;
		getLifecycle().addObserver(this);
	}

	@NonNull
	@Override
	public Screen onCreateScreen(@NonNull Intent intent) {
		return screen = new CarDisplayScreen(getCarContext(), this);
	}

	@Override
	public void onStateChanged(@NonNull LifecycleOwner source, @NonNull Lifecycle.Event event) {
		Log.i("Car display session: ", event);
		if (event == Lifecycle.Event.ON_CREATE) onCreate();
		if (host != null) host.setSessionState(event.getTargetState());
		if (event == Lifecycle.Event.ON_DESTROY) onDestroy();
	}

	private void onCreate() {
		long delay = BuildConfig.D ? debugDelay() : 0;
		if (delay > 0) {
			Log.i("Debug: delaying the service connection by ", delay, "ms");
			FermataApplication.get().getHandler().postDelayed(this::connect, delay);
		} else {
			connect();
		}
		if (BuildConfig.D) registerDebugReceiver();
	}

	private void onDestroy() {
		if (debugReceiver != null) {
			ctx.unregisterReceiver(debugReceiver);
			debugReceiver = null;
		}
		if (delegate != null) {
			CarHostActivity.clearContextFallback(delegate);
			delegate = null;
		}
		if (display != null) {
			display.release();
			display = null;
		}
		host = null;
		screen = null;
	}

	private void connect() {
		if (getLifecycle().getCurrentState() == Lifecycle.State.DESTROYED) return;
		FermataMediaServiceConnection s = service;

		if ((s != null) && s.isConnected()) {
			onConnected(s);
		} else {
			FermataMediaServiceConnection.connect(null).main().onSuccess(c -> {
				service = c;
				onConnected(c);
			}).onFailure(err -> {
				Log.e(err, "Failed to connect to the media service");
				if (host != null) showAlert(host, String.valueOf(err));
			});
		}
	}

	private void onConnected(FermataMediaServiceConnection c) {
		if (getLifecycle().getCurrentState() == Lifecycle.State.DESTROYED) return;
		connection = c;
		checkReady();
	}

	// The host is ready when both the Presentation (first surface) and the service exist.
	private void checkReady() {
		if ((host == null) || (connection == null) || (delegate != null)) return;
		MainActivityDelegate d = delegate = new MainActivityDelegate(host, connection.createBinder());
		CarHostActivity.setContextFallback(d);
		pauseOtherYoutube(connection.getMediaSessionCallback());
		Log.i("Car display ready");
		host.setDelegate(d);
	}

	// Car takes over: a YouTube page playing elsewhere (the phone) is paused, so the car page
	// becomes the only active YouTube player (design D11a).
	private static void pauseOtherYoutube(MediaSessionCallback cb) {
		MediaEngine eng = cb.getEngine();
		if ((eng != null) && (eng.getId() == MediaPrefs.MEDIA_ENG_YT) && cb.isPlaying()) {
			Log.i("Pausing the YouTube player of another page");
			cb.onPause();
		}
	}

	// ---- surface ----

	void onSurfaceAvailable(Surface surface, int width, int height, int dpi) {
		if (getLifecycle().getCurrentState() == Lifecycle.State.DESTROYED) return;
		if (display == null) display = new CarDisplay(ctx);
		this.dpi = dpi;

		if (display.setSurface(surface, width, height, dpi)) {
			Presentation p = display.getPresentation();
			if (p == null) return;
			host = new CarHostActivity(p, screen);
			host.setStableArea(stableArea);
			host.setSessionState(getLifecycle().getCurrentState());
			checkReady();
		}

		if (host != null) host.setDisplaySize(width, height);
	}

	void onSurfaceDestroyed() {
		if (display != null) display.detachSurface();
	}

	void setStableArea(Rect r) {
		stableArea.set(r);
		if (host != null) host.setStableArea(r);
	}

	@Nullable
	View getTouchTarget() {
		Presentation p = (display == null) ? null : display.getPresentation();
		Window w = (p == null) ? null : p.getWindow();
		return (w == null) ? null : w.getDecorView();
	}

	@Nullable
	float[] getDragOrigin(float x, float y, long originTime) {
		return (host == null) ? null : host.getDragOrigin(x, y, originTime);
	}

	void onBack() {
		if (host != null) host.onBack();
	}

	void onHome() {
		if (host != null) host.onHome();
	}

	boolean onFullScreen() {
		return (host != null) && host.onFullScreen();
	}

	// ---- debug hooks ----

	private long debugDelay() {
		try {
			File f = new File(ctx.getFilesDir(), DEBUG_DELAY_FILE);
			if (!f.isFile()) return 0;
			return Long.parseLong(new String(Files.readAllBytes(f.toPath())).trim());
		} catch (Exception err) {
			Log.e(err, "Failed to read ", DEBUG_DELAY_FILE);
			return 0;
		}
	}

	// Re-runs the surface-change path with other dimensions: the DHU cannot resize the surface.
	private void registerDebugReceiver() {
		debugReceiver = new BroadcastReceiver() {
			@Override
			public void onReceive(Context context, Intent i) {
				if (display == null) return;
				int w = i.getIntExtra("w", display.getWidth() * 4 / 5);
				int h = i.getIntExtra("h", display.getHeight() * 4 / 5);
				int d = i.getIntExtra("dpi", dpi);
				Log.i("Debug: resizing the car display to ", w, 'x', h, " dpi=", d);
				display.resize(w, h, d);
				if (host != null) host.setDisplaySize(w, h);
			}
		};
		ContextCompat.registerReceiver(ctx, debugReceiver, new IntentFilter(ACTION_DEBUG_RESIZE),
				ContextCompat.RECEIVER_EXPORTED);
	}
}
