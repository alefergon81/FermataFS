package me.aap.fermata.auto;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Turns Car App Library surface gestures into a plan of touch events. Pure Java: timestamps are
 * passed in and nothing is dispatched here, so the rules can be unit tested.
 * <p>
 * Drags start at the origin: the last tap or pinch focus, else the visible area center. The host
 * reports no drag start point, so tapping a list first "aims" the next drag at it. An
 * {@link OriginResolver} may move the start point of a new drag without changing the origin.
 */
public final class CarGesturePlan {
	public static final int DOWN = 0;
	public static final int UP = 1;
	public static final int MOVE = 2;
	public static final int POINTER_DOWN = 5;
	public static final int POINTER_UP = 6;
	/** A drag with no scroll for this long is closed with UP. */
	public static final long DRAG_IDLE_MS = 500;
	static final long TAP_MS = 50;
	static final int FLING_STEPS = 5;
	static final long FLING_STEP_MS = 10;
	static final int PINCH_STEPS = 4;
	static final long PINCH_STEP_MS = 10;
	static final float PINCH_SPAN = 100;
	private float centerX;
	private float centerY;
	private float originX;
	private float originY;
	private boolean hasOrigin;
	private long originTime = -1;
	@Nullable
	private OriginResolver originResolver;
	private boolean dragOpen;
	private long dragDown;
	private long lastMove;
	private float dragX;
	private float dragY;

	/**
	 * @param action    one of {@link #DOWN}, {@link #UP}, {@link #MOVE}, {@link #POINTER_DOWN},
	 *                  {@link #POINTER_UP}; the pointer index for the pointer actions is always 1
	 * @param xs        pointer x coordinates (1 or 2 pointers)
	 * @param ys        pointer y coordinates
	 * @param deferred  dispatch at {@code eventTime} rather than immediately (a held tap)
	 */
	public record Event(int action, long downTime, long eventTime, float[] xs, float[] ys,
											boolean deferred) {
		Event(int action, long downTime, long eventTime, float x, float y) {
			this(action, downTime, eventTime, new float[]{x}, new float[]{y}, false);
		}

		public int pointerCount() {
			return xs.length;
		}

		public float x() {
			return xs[0];
		}

		public float y() {
			return ys[0];
		}
	}

	/** Redirects the start point of a new drag, for example to the list the page wants to scroll. */
	public interface OriginResolver {
		/**
		 * @param originTime when a tap or pinch set the origin, or -1 if the origin is the center
		 * @return {x, y} where the drag starts instead, or null to start at the origin
		 */
		@Nullable
		float[] resolve(float x, float y, long originTime);
	}

	public void setOriginResolver(@Nullable OriginResolver resolver) {
		originResolver = resolver;
	}

	/** The fallback drag origin, normally the visible area center. */
	public void setCenter(float x, float y) {
		centerX = x;
		centerY = y;
	}

	public boolean isDragOpen() {
		return dragOpen;
	}

	/** When an open drag becomes idle and {@link #idle(long)} closes it. */
	public long getIdleDeadline() {
		return lastMove + DRAG_IDLE_MS;
	}

	public float getOriginX() {
		return hasOrigin ? originX : centerX;
	}

	public float getOriginY() {
		return hasOrigin ? originY : centerY;
	}

	/**
	 * A tap, or a long press when {@code holdMs} exceeds the long press timeout. Closes an open drag
	 * first and moves the origin to the tap point.
	 */
	public List<Event> click(float x, float y, long now, long holdMs) {
		List<Event> l = new ArrayList<>(3);
		closeDrag(now, l);
		setOrigin(x, y, now);
		l.add(new Event(DOWN, now, now, x, y));
		long upTime = now + Math.max(holdMs, TAP_MS);
		l.add(new Event(UP, now, upTime, new float[]{x}, new float[]{y}, holdMs > TAP_MS));
		return l;
	}

	/**
	 * A scroll step. {@code dx}/{@code dy} are distances as reported by the host (old minus new
	 * position), so the finger moves by their negation.
	 */
	public List<Event> scroll(float dx, float dy, long now) {
		List<Event> l = new ArrayList<>(2);
		if (!dragOpen) openDrag(now, l);
		dragX -= dx;
		dragY -= dy;
		lastMove = now;
		l.add(new Event(MOVE, dragDown, now, dragX, dragY));
		return l;
	}

	/** Closes the drag with UP if it has been idle for {@link #DRAG_IDLE_MS}. */
	public List<Event> idle(long now) {
		if (!dragOpen || (now < getIdleDeadline())) return Collections.emptyList();
		List<Event> l = new ArrayList<>(1);
		closeDrag(now, l);
		return l;
	}

	/**
	 * A fling with velocities in pixels per second. Continues an open drag, or starts a short drag
	 * at the origin, with fast moves along the velocity, then ends it with UP.
	 */
	public List<Event> fling(float vx, float vy, long now) {
		List<Event> l = new ArrayList<>(FLING_STEPS + 2);
		long t = now;
		if (!dragOpen) openDrag(t, l);
		else t = Math.max(t, lastMove);
		float stepX = vx * FLING_STEP_MS / 1000f;
		float stepY = vy * FLING_STEP_MS / 1000f;

		for (int i = 0; i < FLING_STEPS; i++) {
			t += FLING_STEP_MS;
			dragX += stepX;
			dragY += stepY;
			l.add(new Event(MOVE, dragDown, t, dragX, dragY));
		}

		lastMove = t;
		closeDrag(t, l);
		return l;
	}

	/** A pinch around the focus, scaling the span between two pointers by {@code factor}. */
	public List<Event> scale(float fx, float fy, float factor, long now) {
		List<Event> l = new ArrayList<>(PINCH_STEPS + 4);
		closeDrag(now, l);
		setOrigin(fx, fy, now);
		float from = PINCH_SPAN / 2;
		float to = from * factor;
		long t = now;
		l.add(new Event(DOWN, now, t, fx - from, fy));
		l.add(pinch(POINTER_DOWN, now, t, fx, fy, from));

		for (int i = 1; i <= PINCH_STEPS; i++) {
			t += PINCH_STEP_MS;
			l.add(pinch(MOVE, now, t, fx, fy, from + (to - from) * i / PINCH_STEPS));
		}

		l.add(pinch(POINTER_UP, now, t, fx, fy, to));
		l.add(new Event(UP, now, t, fx - to, fy));
		return l;
	}

	private static Event pinch(int action, long down, long time, float fx, float fy, float half) {
		return new Event(action, down, time, new float[]{fx - half, fx + half},
				new float[]{fy, fy}, false);
	}

	private void setOrigin(float x, float y, long time) {
		originX = x;
		originY = y;
		originTime = time;
		hasOrigin = true;
	}

	private void openDrag(long now, List<Event> l) {
		dragOpen = true;
		dragDown = now;
		lastMove = now;
		dragX = getOriginX();
		dragY = getOriginY();
		float[] p = (originResolver == null) ? null
				: originResolver.resolve(dragX, dragY, hasOrigin ? originTime : -1);

		if (p != null) {
			dragX = p[0];
			dragY = p[1];
		}

		l.add(new Event(DOWN, now, now, dragX, dragY));
	}

	private void closeDrag(long now, List<Event> l) {
		if (!dragOpen) return;
		dragOpen = false;
		l.add(new Event(UP, dragDown, Math.max(now, lastMove), dragX, dragY));
	}
}
