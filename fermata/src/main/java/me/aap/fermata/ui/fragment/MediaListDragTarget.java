package me.aap.fermata.ui.fragment;

import androidx.annotation.Nullable;

/**
 * Where a car drag starts on a media list screen: an origin that is neither on the list nor on a
 * view with its own drag gestures (control panel, nav bar) goes to the list, so the video beside it
 * or the toolbar above it never takes the drag.
 * Pure Java, so it can be unit tested. Rects are {left, top, right, bottom} in car display pixels,
 * edges inclusive; null means not shown.
 */
final class MediaListDragTarget {

	private MediaListDragTarget() {
	}

	/**
	 * @param list     the visible part of the media list
	 * @param keep     views whose own drag gestures are kept (null entries are ignored)
	 * @param blockers views drawn over the list that must not be dragged by accident, such as the
	 *                 split handle or the floating button (null entries are ignored)
	 * @param menuOpen whether an overlay menu is open
	 * @return the center of {@code list}, or null to start the drag at the origin
	 */
	@Nullable
	static float[] resolve(float x, float y, @Nullable float[] list, float[][] keep,
												 float[][] blockers, boolean menuOpen) {
		if ((list == null) || (list[0] >= list[2]) || (list[1] >= list[3]) || menuOpen) return null;
		if (inAny(x, y, keep)) return null;
		if (contains(list, x, y) && !inAny(x, y, blockers)) return null;
		return new float[]{(list[0] + list[2]) / 2, (list[1] + list[3]) / 2};
	}

	private static boolean inAny(float x, float y, float[][] rects) {
		for (float[] r : rects) {
			if ((r != null) && contains(r, x, y)) return true;
		}
		return false;
	}

	private static boolean contains(float[] r, float x, float y) {
		return (x >= r[0]) && (x <= r[2]) && (y >= r[1]) && (y <= r[3]);
	}
}
