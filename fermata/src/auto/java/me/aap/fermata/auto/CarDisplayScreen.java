package me.aap.fermata.auto;

import static androidx.core.graphics.drawable.IconCompat.createWithResource;

import android.graphics.Rect;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.car.app.AppManager;
import androidx.car.app.CarContext;
import androidx.car.app.CarToast;
import androidx.car.app.Screen;
import androidx.car.app.ScreenManager;
import androidx.car.app.SurfaceCallback;
import androidx.car.app.SurfaceContainer;
import androidx.car.app.model.Action;
import androidx.car.app.model.ActionStrip;
import androidx.car.app.model.CarColor;
import androidx.car.app.model.CarIcon;
import androidx.car.app.model.SearchTemplate;
import androidx.car.app.model.Template;
import androidx.car.app.navigation.model.NavigationTemplate;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;

import me.aap.fermata.R;
import me.aap.utils.function.Consumer;
import me.aap.utils.log.Log;

/**
 * The car screen of {@link CarDisplayService}: a {@link NavigationTemplate} in pan mode over the
 * car display. Back, home and the long-press toggle stay in the regular action strip, which the
 * host keeps visible in pan mode (spike result, design D6).
 */
final class CarDisplayScreen extends Screen implements SurfaceCallback, CarHostActivity.Ui {
	private final CarDisplaySession session;
	private final CarTouchDispatcher touch;
	private SearchScreen search;

	CarDisplayScreen(@NonNull CarContext ctx, CarDisplaySession session) {
		super(ctx);
		this.session = session;
		touch = new CarTouchDispatcher(session::getTouchTarget);
		ctx.getCarService(AppManager.class).setSurfaceCallback(this);
		getLifecycle().addObserver(new DefaultLifecycleObserver() {
			@Override
			public void onDestroy(@NonNull LifecycleOwner owner) {
				touch.release();
			}
		});
		Log.i("Car display screen created. Car API level: ", ctx.getCarAppApiLevel());
	}

	@Override
	public void onSurfaceAvailable(@NonNull SurfaceContainer sc) {
		if (sc.getSurface() == null) return;
		Log.i("Car surface available: ", sc.getWidth(), 'x', sc.getHeight(), " dpi=", sc.getDpi());
		session.onSurfaceAvailable(sc.getSurface(), sc.getWidth(), sc.getHeight(), sc.getDpi());
	}

	@Override
	public void onSurfaceDestroyed(@NonNull SurfaceContainer sc) {
		Log.i("Car surface destroyed");
		session.onSurfaceDestroyed();
	}

	@Override
	public void onVisibleAreaChanged(@NonNull Rect r) {
		touch.setCenter(r.exactCenterX(), r.exactCenterY());
	}

	@Override
	public void onStableAreaChanged(@NonNull Rect r) {
		Log.i("Car stable area: ", r);
		session.setStableArea(r);
	}

	@Override
	public void onClick(float x, float y) {
		boolean wasArmed = touch.isLongPressArmed();
		touch.click(x, y);
		if (wasArmed) invalidate();
	}

	@Override
	public void onScroll(float distanceX, float distanceY) {
		touch.scroll(distanceX, distanceY);
	}

	@Override
	public void onFling(float velocityX, float velocityY) {
		touch.fling(velocityX, velocityY);
	}

	@Override
	public void onScale(float focusX, float focusY, float scaleFactor) {
		touch.scale(focusX, focusY, scaleFactor);
	}

	@NonNull
	@Override
	public Template onGetTemplate() {
		Action back = action(R.drawable.back, null, session::onBack);
		Action home = action(R.drawable.home, null, session::onHome);
		Action fullScreen = action(R.drawable.car_fullscreen, null, () -> {
			if (!session.onFullScreen()) toast(R.string.car_display_no_video);
		});
		boolean armed = touch.isLongPressArmed();
		Action longPress = action(R.drawable.long_press, armed ? CarColor.YELLOW : null, () -> {
			boolean arm = !touch.isLongPressArmed();
			touch.setLongPressArmed(arm);
			if (arm) toast(R.string.car_display_long_press_armed);
			invalidate();
		});
		// The host allows up to 4 actions in the strip.
		return new NavigationTemplate.Builder()
				.setActionStrip(new ActionStrip.Builder().addAction(back).addAction(home)
						.addAction(fullScreen).addAction(longPress).build())
				.setMapActionStrip(new ActionStrip.Builder().addAction(Action.PAN).build()).build();
	}

	private void toast(@StringRes int msg) {
		CarToast.makeText(getCarContext(), msg, CarToast.LENGTH_SHORT).show();
	}

	private Action action(@DrawableRes int icon, CarColor tint, Runnable onClick) {
		CarIcon.Builder b = new CarIcon.Builder(createWithResource(getCarContext(), icon));
		if (tint != null) b.setTint(tint);
		return new Action.Builder().setIcon(b.build()).setOnClickListener(onClick::run).build();
	}

	// ---- CarHostActivity.Ui ----

	@Override
	public void showSearch(String text, Consumer<String> onSubmit, Runnable onCancel) {
		hideSearch();
		search = new SearchScreen(getCarContext(), text, onSubmit, onCancel);
		getCarContext().getCarService(ScreenManager.class).push(search);
	}

	@Override
	public void hideSearch() {
		SearchScreen s = search;
		if (s == null) return;
		search = null;
		s.done = true;
		ScreenManager sm = getCarContext().getCarService(ScreenManager.class);
		if (sm.getTop() == s) sm.pop();
		else sm.remove(s);
	}

	/**
	 * The head unit's text entry. Submitting delivers the text; leaving it any other way (back,
	 * or typing blocked while driving and the user backs out) cancels, so nothing waits forever.
	 */
	private static final class SearchScreen extends Screen {
		private final String text;
		private final SearchTemplate.SearchCallback callback;
		boolean done;

		SearchScreen(CarContext ctx, String text, Consumer<String> onSubmit, Runnable onCancel) {
			super(ctx);
			this.text = text;
			getLifecycle().addObserver(new DefaultLifecycleObserver() {
				@Override
				public void onDestroy(@NonNull LifecycleOwner owner) {
					if (done) return;
					done = true;
					onCancel.run();
				}
			});
			callback = new SearchTemplate.SearchCallback() {
				@Override
				public void onSearchSubmitted(@NonNull String searchText) {
					if (done) return;
					done = true;
					onSubmit.accept(searchText);
				}
			};
		}

		@NonNull
		@Override
		public Template onGetTemplate() {
			return new SearchTemplate.Builder(callback).setInitialSearchText(text)
					.setHeaderAction(Action.BACK).setShowKeyboardByDefault(true).build();
		}
	}
}
