package me.aap.fermata.addon.web.yt;

import androidx.annotation.Nullable;

/**
 * Where a car drag starts on the YouTube watch page: drags with no origin on the current page go
 * to the scroll container beside the player (the recommendations).
 * Pure Java, so it can be unit tested. Rects are {left, top, right, bottom} in car display pixels.
 */
final class CarDragTarget {
	private static final float SCALE_EPSILON = 0.01f;

	/**
	 * The page layout reported by the injected script.
	 *
	 * @param watch  whether the page is a watch page
	 * @param scale  the visual viewport scale (pinch zoom)
	 * @param target the scroll container beside the player, or null
	 */
	record Snapshot(boolean watch, float scale, @Nullable float[] target) {}

	private CarDragTarget() {
	}

	/**
	 * @param pageOrigin whether a tap or pinch set the origin after the page's URL last changed
	 * @param visible    the web view's visible rect
	 * @return {x, y} where the drag starts instead, or null to start at the origin
	 */
	@Nullable
	static float[] resolve(boolean pageOrigin, @Nullable Snapshot s, float[] visible,
												 boolean fullScreen) {
		if ((s == null) || !s.watch() || (s.target() == null) || fullScreen) return null;
		if (Math.abs(s.scale() - 1f) > SCALE_EPSILON) return null;
		if (pageOrigin) return null;

		float[] t = s.target();
		float left = Math.max(t[0], visible[0]);
		float top = Math.max(t[1], visible[1]);
		float right = Math.min(t[2], visible[2]);
		float bottom = Math.min(t[3], visible[3]);
		if ((left >= right) || (top >= bottom)) return null;
		return new float[]{(left + right) / 2, (top + bottom) / 2};
	}
}
