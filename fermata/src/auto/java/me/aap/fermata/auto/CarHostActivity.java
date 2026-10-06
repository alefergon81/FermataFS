package me.aap.fermata.auto;

import static android.Manifest.permission.RECORD_AUDIO;
import static android.content.pm.PackageManager.PERMISSION_GRANTED;
import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static me.aap.utils.async.Completed.completed;
import static me.aap.utils.async.Completed.failed;
import static me.aap.utils.ui.UiUtils.ID_NULL;

import android.app.Activity;
import android.app.Presentation;
import android.content.Context;
import android.content.Intent;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.OperationCanceledException;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.TextView.OnEditorActionListener;

import androidx.activity.OnBackPressedDispatcher;
import androidx.activity.result.ActivityResultRegistry;
import androidx.activity.result.contract.ActivityResultContract;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityOptionsCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentController;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LifecycleRegistry;
import androidx.lifecycle.ViewModelStore;
import androidx.lifecycle.ViewModelStoreOwner;
import androidx.lifecycle.ViewTreeLifecycleOwner;
import androidx.lifecycle.ViewTreeViewModelStoreOwner;
import androidx.savedstate.SavedStateRegistry;
import androidx.savedstate.SavedStateRegistryController;
import androidx.savedstate.SavedStateRegistryOwner;
import androidx.savedstate.ViewTreeSavedStateRegistryOwner;

import com.google.android.material.textfield.TextInputEditText;

import me.aap.fermata.BuildConfig;
import me.aap.fermata.R;
import me.aap.fermata.addon.AddonManager;
import me.aap.fermata.ui.activity.FermataActivity;
import me.aap.fermata.ui.activity.MainActivityDelegate;
import me.aap.fermata.ui.fragment.MainActivityFragment;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.async.Promise;
import me.aap.utils.function.Consumer;
import me.aap.utils.function.Supplier;
import me.aap.utils.log.Log;
import me.aap.utils.ui.activity.ActivityDelegate;
import me.aap.utils.ui.activity.ActivityListener;
import me.aap.utils.ui.fragment.ActivityFragment;

/**
 * The {@link FermataActivity} of the car display: the role {@code MainCarActivity} plays for the
 * official projection, built from public APIs over the {@link Presentation} context.
 * <p>
 * It owns the lifecycle, ViewModel, saved state, back dispatcher and (always failing) activity
 * result registries, and drives a {@link FragmentController}. Its effective state is the Session
 * state capped by readiness (the delegate exists): until then Session transitions are only
 * recorded, and on readiness the steps are replayed in order.
 */
final class CarHostActivity extends android.view.ContextThemeWrapper
		implements FermataActivity, LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {
	private static final int DESTROYED = -1;
	private static final int INITIAL = 0;
	private static final int CREATED = 1;
	private static final int STARTED = 2;
	private static final int RESUMED = 3;
	private final Handler handler = new Handler(Looper.getMainLooper());
	private final Presentation presentation;
	private final Ui ui;
	private final LifecycleRegistry lifecycle = new LifecycleRegistry(this);
	private final ViewModelStore viewModelStore = new ViewModelStore();
	private final SavedStateRegistryController savedState = SavedStateRegistryController.create(this);
	private final OnBackPressedDispatcher backDispatcher = new OnBackPressedDispatcher(this::delegateBack);
	private final ActivityResultRegistry resultRegistry = new FailingResultRegistry();
	private final FragmentController fragments;
	private final FrameLayout contentRoot;
	private final Promise<MainActivityDelegate> delegatePromise = new Promise<>();
	private final Rect stableArea = new Rect();
	private final ActivityListener videoModeListener = (a, e) -> applyPadding();
	private MainActivityDelegate delegate;
	private Lifecycle.State sessionState = Lifecycle.State.INITIALIZED;
	private int state = INITIAL;
	private int displayWidth;
	private int displayHeight;
	private EditText inputText;
	private TextWatcher inputWatcher;

	/** What the host needs from the car screen. */
	interface Ui {
		void showSearch(String text, Consumer<String> onSubmit, Runnable onCancel);

		void hideSearch();
	}

	CarHostActivity(Presentation presentation, Ui ui) {
		super(MainActivityDelegate.attachBaseContext(presentation.getContext()), 0);
		this.presentation = presentation;
		this.ui = ui;
		MainActivityDelegate.setTheme(this, true);
		fragments = FragmentController.createController(new CarFragmentHost(this));
		contentRoot = new FrameLayout(this);
		ViewTreeLifecycleOwner.set(contentRoot, this);
		ViewTreeViewModelStoreOwner.set(contentRoot, this);
		ViewTreeSavedStateRegistryOwner.set(contentRoot, this);
		presentation.setContentView(contentRoot,
				new FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT));
		setWindowBackground();
	}

	// The theme's background, so that the padding around the content is not the Presentation's
	// default (white) window background.
	private void setWindowBackground() {
		Window w = presentation.getWindow();
		if (w == null) return;
		TypedValue tv = new TypedValue();
		if (!getTheme().resolveAttribute(android.R.attr.colorBackground, tv, true)) return;
		if ((tv.type >= TypedValue.TYPE_FIRST_COLOR_INT) && (tv.type <= TypedValue.TYPE_LAST_COLOR_INT)) {
			w.setBackgroundDrawable(new ColorDrawable(tv.data));
		}
	}

	// ---- lifecycle ----

	void setSessionState(Lifecycle.State s) {
		sessionState = s;
		sync();
	}

	/** Marks the host ready and replays the steps up to the Session state. */
	void setDelegate(MainActivityDelegate d) {
		if (state == DESTROYED) return;
		delegate = d;
		delegatePromise.complete(d);
		sync();
	}

	boolean isDestroyed() {
		return state == DESTROYED;
	}

	private void sync() {
		if (state == DESTROYED) return;

		if (sessionState == Lifecycle.State.DESTROYED) {
			while (state > CREATED) down();
			if (state == CREATED) destroy();
			state = DESTROYED;
			delegatePromise.cancel();
			return;
		}

		int target = (delegate == null) ? INITIAL : switch (sessionState) {
			case RESUMED -> RESUMED;
			case STARTED -> STARTED;
			case CREATED -> CREATED;
			default -> INITIAL;
		};
		while (state < target) up();
		while (state > Math.max(target, CREATED)) down();
	}

	private void up() {
		switch (state) {
			case INITIAL -> {
				Log.i("Car host: create");
				savedState.performAttach();
				savedState.performRestore(null);
				lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_CREATE);
				fragments.attachHost(null);
				if (BuildConfig.D) logFragments();
				fragments.dispatchCreate();
				delegate.addBroadcastListener(videoModeListener, ActivityListener.FRAGMENT_CONTENT_CHANGED);
				delegate.onActivityCreate(null);
				fragments.dispatchActivityCreated();
				state = CREATED;
			}
			case CREATED -> {
				Log.i("Car host: start");
				lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_START);
				fragments.dispatchStart();
				state = STARTED;
			}
			case STARTED -> {
				Log.i("Car host: resume");
				lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_RESUME);
				fragments.dispatchResume();
				delegate.onActivityResume();
				state = RESUMED;
			}
		}
	}

	private void down() {
		switch (state) {
			case RESUMED -> {
				Log.i("Car host: pause");
				delegate.onActivityPause();
				fragments.dispatchPause();
				lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE);
				state = STARTED;
			}
			case STARTED -> {
				Log.i("Car host: stop");
				fragments.dispatchStop();
				lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_STOP);
				state = CREATED;
			}
		}
	}

	private void destroy() {
		Log.i("Car host: destroy");
		stopInput();
		delegate.removeBroadcastListener(videoModeListener);
		delegate.onActivityDestroy();
		fragments.dispatchDestroy();
		lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY);
		viewModelStore.clear();
	}

	private void logFragments() {
		fragments.getSupportFragmentManager().registerFragmentLifecycleCallbacks(
				new FragmentManager.FragmentLifecycleCallbacks() {
					@Override
					public void onFragmentCreated(@NonNull FragmentManager fm, @NonNull Fragment f,
																				@Nullable Bundle s) {
						Log.i("Car fragment created: ", f);
					}

					@Override
					public void onFragmentStarted(@NonNull FragmentManager fm, @NonNull Fragment f) {
						Log.i("Car fragment started: ", f);
					}

					@Override
					public void onFragmentResumed(@NonNull FragmentManager fm, @NonNull Fragment f) {
						Log.i("Car fragment resumed: ", f);
					}

					@Override
					public void onFragmentPaused(@NonNull FragmentManager fm, @NonNull Fragment f) {
						Log.i("Car fragment paused: ", f);
					}

					@Override
					public void onFragmentStopped(@NonNull FragmentManager fm, @NonNull Fragment f) {
						Log.i("Car fragment stopped: ", f);
					}

					@Override
					public void onFragmentDestroyed(@NonNull FragmentManager fm, @NonNull Fragment f) {
						Log.i("Car fragment destroyed: ", f);
					}
				}, true);
	}

	@NonNull
	@Override
	public Lifecycle getLifecycle() {
		return lifecycle;
	}

	@NonNull
	@Override
	public ViewModelStore getViewModelStore() {
		return viewModelStore;
	}

	@NonNull
	@Override
	public SavedStateRegistry getSavedStateRegistry() {
		return savedState.getSavedStateRegistry();
	}

	OnBackPressedDispatcher getOnBackPressedDispatcher() {
		return backDispatcher;
	}

	ActivityResultRegistry getActivityResultRegistry() {
		return resultRegistry;
	}

	// ---- content and layout ----

	FrameLayout getContentRoot() {
		return contentRoot;
	}

	LayoutInflater getLayoutInflater() {
		return LayoutInflater.from(this);
	}

	@Override
	public Object getSystemService(String name) {
		// Dialogs and popups must bind to the Presentation window.
		if (WINDOW_SERVICE.equals(name)) return presentation.getContext().getSystemService(name);
		return super.getSystemService(name);
	}

	/** The area the template never covers, in display pixels. */
	void setStableArea(Rect r) {
		stableArea.set(r);
		applyPadding();
	}

	// Content stays inside the stable area; full-screen video ignores it.
	private void applyPadding() {
		Rect r = stableArea;
		MainActivityDelegate d = delegate;
		if (r.isEmpty() || ((d != null) && d.isVideoMode())) {
			contentRoot.setPadding(0, 0, 0, 0);
		} else {
			int right = Math.max(0, displayWidth - r.right);
			int bottom = Math.max(0, displayHeight - r.bottom);
			contentRoot.setPadding(r.left, r.top, right, bottom);
		}
	}

	void setDisplaySize(int width, int height) {
		displayWidth = width;
		displayHeight = height;
		applyPadding();
	}

	// ---- navigation ----

	/** Back: a fragment's callback if any, else the delegate (which stays put at the start screen). */
	void onBack() {
		if (isInputActive()) {
			stopInput();
			return;
		}
		backDispatcher.onBackPressed();
	}

	private void delegateBack() {
		MainActivityDelegate d = delegate;
		if (d != null) d.onBackPressed();
	}

	/** Home: the start screen; already there, the screen's own start (YouTube's home page). */
	void onHome() {
		MainActivityDelegate d = delegate;
		if (d == null) return;
		stopInput();
		d.hideActiveMenu();
		int id = getStartFragmentId();
		if (id == ID_NULL) id = R.id.folders_fragment;
		ActivityFragment f = d.getActiveFragment();
		if ((f != null) && (f.getFragmentId() == id)) {
			if (f instanceof MainActivityFragment mf) mf.goHome();
		} else {
			d.showFragment(id);
		}
	}

	/** The active screen's start point for a new drag; see MainActivityFragment.getCarDragOrigin. */
	@Nullable
	float[] getDragOrigin(float x, float y, long originTime) {
		MainActivityDelegate d = delegate;
		if (d == null) return null;
		return (d.getActiveFragment() instanceof MainActivityFragment f)
				? f.getCarDragOrigin(x, y, originTime) : null;
	}

	/** Toggles the full-screen video of the active screen; false if it has none. */
	boolean onFullScreen() {
		MainActivityDelegate d = delegate;
		if (d == null) return false;
		return (d.getActiveFragment() instanceof MainActivityFragment f) && f.toggleFullScreen();
	}

	// ---- FermataActivity ----

	@NonNull
	@Override
	public FutureSupplier<MainActivityDelegate> getActivityDelegate() {
		return delegatePromise;
	}

	@Override
	public boolean isCarActivity() {
		return true;
	}

	@Override
	public int getStartFragmentId() {
		return AddonManager.get().hasAddon(R.id.youtube_fragment) ? R.id.youtube_fragment : ID_NULL;
	}

	@Override
	public void setRequestedOrientation(int requestedOrientation) {
	}

	@Override
	public Window getWindow() {
		return presentation.getWindow();
	}

	@Override
	public View getCurrentFocus() {
		return presentation.getCurrentFocus();
	}

	@NonNull
	@Override
	public FragmentManager getSupportFragmentManager() {
		return fragments.getSupportFragmentManager();
	}

	@Override
	public void setContentView(int layoutResID) {
		contentRoot.removeAllViews();
		getLayoutInflater().inflate(layoutResID, contentRoot, true);
		applyPadding();
	}

	@Override
	public <T extends View> T findViewById(int id) {
		return contentRoot.findViewById(id);
	}

	@Override
	public void recreate() {
	}

	@Override
	public void finish() {
		MainActivityDelegate d = delegate;
		if (d != null) d.onActivityFinish();
	}

	@Override
	public void startActivity(Intent intent, @Nullable Bundle options) {
		// Not an Activity: anything started from here opens on the phone in a new task.
		try {
			intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
			super.startActivity(intent, options);
		} catch (Exception err) {
			Log.e(err, "Failed to start activity from the car display: ", intent);
		}
	}

	@Override
	public FutureSupplier<Intent> startActivityForResult(Supplier<Intent> intent) {
		return failed(new UnsupportedOperationException());
	}

	/** The current grant state, without prompting: the car display cannot show a prompt. */
	@Override
	public FutureSupplier<int[]> checkPermissions(String... perms) {
		int[] result = new int[perms.length];
		for (int i = 0; i < perms.length; i++) {
			result[i] = ContextCompat.checkSelfPermission(this, perms[i]);
		}
		return completed(result);
	}

	@Override
	public Intent getIntent() {
		return new Intent(Intent.ACTION_MAIN);
	}

	// ---- text input ----

	/**
	 * Voice when enabled and RECORD_AUDIO is already granted, else the head unit's text entry. On
	 * submit, the text is set, the watcher notified, then IME_ACTION_SEARCH fired, which is what
	 * makes web pages submit their form.
	 */
	@Override
	public EditText startInput(TextWatcher w) {
		if (inputText == null) inputText = new TextInputEditText(this);
		inputWatcher = w;
		MainActivityDelegate d = delegate;

		if ((d != null) && d.getPrefs().getVoiceControlEnabledPref() &&
				(ContextCompat.checkSelfPermission(this, RECORD_AUDIO) == PERMISSION_GRANTED)) {
			Log.i("Car input: voice");
			d.startSpeechRecognizer(null, true).onCompletion((q, err) -> {
				if (inputWatcher != w) return;
				if ((q != null) && !q.isEmpty()) submitInput(q.get(0));
				else if (err instanceof OperationCanceledException) showKeyboard(w);
				else stopInput();
			});
		} else {
			Log.i("Car input: keyboard");
			// Posted, so that the caller can set the initial text on the returned field first.
			handler.post(() -> showKeyboard(w));
		}

		return inputText;
	}

	private void showKeyboard(TextWatcher w) {
		if (inputWatcher != w) return;
		ui.showSearch(inputText.getText().toString(), this::submitInput, () -> {
			if (inputWatcher == w) stopInput();
		});
	}

	private void submitInput(String text) {
		TextWatcher w = inputWatcher;
		if (w == null) return;
		Log.i("Car input: submit");
		inputText.setText(text);
		Editable e = inputText.getText();
		w.afterTextChanged(e);
		if (w instanceof OnEditorActionListener l) {
			l.onEditorAction(inputText, EditorInfo.IME_ACTION_SEARCH, null);
		}
		stopInput();
	}

	@Override
	public void stopInput() {
		if (inputWatcher == null) return;
		Log.i("Car input: stop");
		inputWatcher = null;
		ui.hideSearch();
	}

	@Override
	public boolean isInputActive() {
		return inputWatcher != null;
	}

	@Override
	public boolean setTextInput(String text) {
		TextWatcher w = inputWatcher;
		if (w == null) return false;
		inputText.setText(text);
		w.afterTextChanged(inputText.getText());
		stopInput();
		return true;
	}

	/** A non-focusable field that opens the car text input on click and receives its text. */
	@Override
	public EditText createEditText(Context ctx) {
		EditText et = new TextInputEditText(ctx, null, androidx.appcompat.R.attr.editTextStyle);
		et.setFocusable(false);
		et.setOnClickListener(v -> {
			if (isInputActive()) return;
			EditText input = startInput(new TextWatcher() {
				@Override
				public void beforeTextChanged(CharSequence s, int start, int count, int after) {
				}

				@Override
				public void onTextChanged(CharSequence s, int start, int before, int count) {
				}

				@Override
				public void afterTextChanged(Editable s) {
					if (!s.toString().contentEquals(et.getText())) et.setText(s);
				}
			});
			input.setText(et.getText());
		});
		return et;
	}

	private final class FailingResultRegistry extends ActivityResultRegistry {
		@Override
		public <I, O> void onLaunch(int requestCode, @NonNull ActivityResultContract<I, O> contract,
																I input, @Nullable ActivityOptionsCompat options) {
			Log.w("Activity results are not supported on the car display: ", contract);
			handler.post(() -> dispatchResult(requestCode, Activity.RESULT_CANCELED, null));
		}
	}

	static void setContextFallback(MainActivityDelegate d) {
		if (ActivityDelegate.getContextToDelegate() == null) {
			ActivityDelegate.setContextToDelegate(new ContextFallback(d));
		}
	}

	static void clearContextFallback(MainActivityDelegate d) {
		if ((ActivityDelegate.getContextToDelegate() instanceof ContextFallback f) && (f.delegate == d)) {
			ActivityDelegate.setContextToDelegate(null);
		}
	}

	/** The car delegate as the context fallback, cleared only if it still points to that delegate. */
	private record ContextFallback(MainActivityDelegate delegate)
			implements me.aap.utils.function.Function<Context, ActivityDelegate> {
		@Override
		public ActivityDelegate apply(Context ctx) {
			return delegate;
		}
	}
}
