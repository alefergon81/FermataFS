package me.aap.fermata.addon.web.yt;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

import me.aap.fermata.addon.web.yt.CarDragTarget.Snapshot;

public class CarDragTargetTest {
	private static final float D = 0.001f;
	// The DHU layout: recommendations beside the player, on the right.
	private static final float[] RELATED = {1268, 48, 1768, 486};
	private static final float[] VISIBLE = {0, 0, 1768, 486};
	private static final Snapshot WATCH = new Snapshot(true, 1f, RELATED);
	private static final float[] RELATED_CENTER = {1518, 267};

	@Test
	public void noSnapshot() {
		assertNull(CarDragTarget.resolve(false, null, VISIBLE, false));
	}

	@Test
	public void notWatchPage() {
		assertNull(CarDragTarget.resolve(false, new Snapshot(false, 1f, RELATED), VISIBLE, false));
	}

	@Test
	public void noTarget() {
		assertNull(CarDragTarget.resolve(false, new Snapshot(true, 1f, null), VISIBLE, false));
	}

	@Test
	public void fullScreen() {
		assertNull(CarDragTarget.resolve(false, WATCH, VISIBLE, true));
	}

	@Test
	public void zoomed() {
		assertNull(CarDragTarget.resolve(false, new Snapshot(true, 1.5f, RELATED), VISIBLE, false));
	}

	@Test
	public void noPageOriginRedirects() {
		assertArrayEquals(RELATED_CENTER, CarDragTarget.resolve(false, WATCH, VISIBLE, false), D);
	}

	@Test
	public void pageOriginKeepsOrigin() {
		// A tap on this page, the player included, aims the drag.
		assertNull(CarDragTarget.resolve(true, WATCH, VISIBLE, false));
	}

	@Test
	public void targetIsClippedToVisibleBounds() {
		Snapshot s = new Snapshot(true, 1f, new float[]{1268, 48, 1768, 1648});
		assertArrayEquals(RELATED_CENTER, CarDragTarget.resolve(false, s, VISIBLE, false), D);
	}

	@Test
	public void targetOutsideVisibleBounds() {
		Snapshot s = new Snapshot(true, 1f, new float[]{1268, 600, 1768, 900});
		assertNull(CarDragTarget.resolve(false, s, VISIBLE, false));
	}
}
