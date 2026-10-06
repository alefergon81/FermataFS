package me.aap.fermata.auto;

import static android.os.SystemClock.uptimeMillis;

import android.os.Handler;
import android.os.Looper;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.MotionEvent.PointerCoords;
import android.view.MotionEvent.PointerProperties;
import android.view.View;
import android.view.ViewConfiguration;

import androidx.annotation.Nullable;

import java.util.List;

import me.aap.fermata.auto.CarGesturePlan.Event;
import me.aap.utils.function.Supplier;

/**
 * Dispatches {@link CarGesturePlan} events as {@link MotionEvent}s directly to the view tree of
 * the car display. Same process, so no injection permission is needed.
 */
final class CarTouchDispatcher {
	private final Handler handler = new Handler(Looper.getMainLooper());
	private final CarGesturePlan plan = new CarGesturePlan();
	private final Supplier<View> target;
	private final Runnable idleCheck = this::checkIdle;
	private Runnable deferred;
	private boolean longPressArmed;

	CarTouchDispatcher(Supplier<View> target) {
		this.target = target;
	}

	void setCenter(float x, float y) {
		plan.setCenter(x, y);
	}

	boolean isLongPressArmed() {
		return longPressArmed;
	}

	/** Arms or disarms the long press for the next tap only. */
	void setLongPressArmed(boolean armed) {
		longPressArmed = armed;
	}

	void click(float x, float y) {
		long hold = 0;
		if (longPressArmed) {
			longPressArmed = false;
			hold = ViewConfiguration.getLongPressTimeout() + 200L;
		}
		run(plan.click(x, y, uptimeMillis(), hold));
	}

	void scroll(float dx, float dy) {
		run(plan.scroll(dx, dy, uptimeMillis()));
		handler.removeCallbacks(idleCheck);
		handler.postAtTime(idleCheck, plan.getIdleDeadline());
	}

	void fling(float vx, float vy) {
		handler.removeCallbacks(idleCheck);
		run(plan.fling(vx, vy, uptimeMillis()));
	}

	void scale(float fx, float fy, float factor) {
		handler.removeCallbacks(idleCheck);
		run(plan.scale(fx, fy, factor, uptimeMillis()));
	}

	void release() {
		handler.removeCallbacksAndMessages(null);
		deferred = null;
	}

	private void checkIdle() {
		long now = uptimeMillis();
		if (now < plan.getIdleDeadline()) handler.postAtTime(idleCheck, plan.getIdleDeadline());
		else run(plan.idle(now));
	}

	private void run(List<Event> events) {
		// A held tap still waiting for its UP is finished first, to keep the event order.
		if (deferred != null) {
			handler.removeCallbacks(deferred);
			Runnable r = deferred;
			deferred = null;
			r.run();
		}

		for (Event e : events) {
			if (e.deferred()) {
				Runnable r = () -> {
					deferred = null;
					dispatch(e);
				};
				deferred = r;
				handler.postAtTime(r, e.eventTime());
			} else {
				dispatch(e);
			}
		}
	}

	private void dispatch(Event e) {
		View v = target.get();
		if (v == null) return;
		MotionEvent m = toMotionEvent(e);
		if (m == null) return;
		v.dispatchTouchEvent(m);
		m.recycle();
	}

	@Nullable
	private static MotionEvent toMotionEvent(Event e) {
		int n = e.pointerCount();
		int action = switch (e.action()) {
			case CarGesturePlan.DOWN -> MotionEvent.ACTION_DOWN;
			case CarGesturePlan.UP -> MotionEvent.ACTION_UP;
			case CarGesturePlan.MOVE -> MotionEvent.ACTION_MOVE;
			case CarGesturePlan.POINTER_DOWN ->
					MotionEvent.ACTION_POINTER_DOWN | (1 << MotionEvent.ACTION_POINTER_INDEX_SHIFT);
			case CarGesturePlan.POINTER_UP ->
					MotionEvent.ACTION_POINTER_UP | (1 << MotionEvent.ACTION_POINTER_INDEX_SHIFT);
			default -> -1;
		};
		if (action < 0) return null;
		PointerProperties[] props = new PointerProperties[n];
		PointerCoords[] coords = new PointerCoords[n];

		for (int i = 0; i < n; i++) {
			PointerProperties p = props[i] = new PointerProperties();
			p.id = i;
			p.toolType = MotionEvent.TOOL_TYPE_FINGER;
			PointerCoords c = coords[i] = new PointerCoords();
			c.x = e.xs()[i];
			c.y = e.ys()[i];
			c.pressure = 1f;
			c.size = 1f;
		}

		return MotionEvent.obtain(e.downTime(), e.eventTime(), action, n, props, coords, 0, 0, 1f, 1f,
				0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0);
	}
}
