package me.aap.fermata.ui.fragment;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class MediaListDragTargetTest {
	private static final float D = 0.001f;
	// A wide car display in split mode: toolbar on top, list on the left, video on the right,
	// control panel below.
	private static final float[] LIST = {0, 60, 530, 400};
	private static final float[] LIST_CENTER = {265, 230};
	private static final float[] PANEL = {0, 400, 1768, 486};
	private static final float[] HANDLE = {510, 210, 550, 250};
	private static final float[] FAB = {450, 320, 510, 380};
	private static final float[][] KEEP = {PANEL};
	private static final float[][] BLOCKERS = {HANDLE, FAB};

	private static float[] resolve(float x, float y) {
		return MediaListDragTarget.resolve(x, y, LIST, KEEP, BLOCKERS, false);
	}

	@Test
	public void noList() {
		assertNull(MediaListDragTarget.resolve(1000, 200, null, KEEP, BLOCKERS, false));
	}

	@Test
	public void emptyList() {
		assertNull(MediaListDragTarget.resolve(1000, 200, new float[]{0, 60, 0, 400}, KEEP,
				BLOCKERS, false));
		assertNull(MediaListDragTarget.resolve(1000, 200, new float[]{0, 60, 530, 60}, KEEP,
				BLOCKERS, false));
	}

	@Test
	public void menuOpen() {
		assertNull(MediaListDragTarget.resolve(1000, 200, LIST, KEEP, BLOCKERS, true));
	}

	@Test
	public void originInList() {
		assertNull(resolve(100, 150));
	}

	@Test
	public void originOnListEdges() {
		assertNull(resolve(0, 60));
		assertNull(resolve(530, 400 - 1));
		assertNull(resolve(200, 400 - 1));
	}

	@Test
	public void originOnControlPanel() {
		assertNull(resolve(1000, 450));
		assertNull(resolve(100, 450));
	}

	@Test
	public void originOnHandleOverList() {
		assertArrayEquals(LIST_CENTER, resolve(520, 230), D);
	}

	@Test
	public void originOnFloatingButton() {
		assertArrayEquals(LIST_CENTER, resolve(480, 350), D);
	}

	@Test
	public void originOnVideo() {
		assertArrayEquals(LIST_CENTER, resolve(1200, 300), D);
	}

	@Test
	public void originOnToolbar() {
		assertArrayEquals(LIST_CENTER, resolve(200, 30), D);
	}

	@Test
	public void noTapCenterOverVideo() {
		assertArrayEquals(LIST_CENTER, resolve(884, 243), D);
	}

	@Test
	public void nullKeepAndBlockerEntries() {
		float[][] none = {null};
		assertArrayEquals(LIST_CENTER,
				MediaListDragTarget.resolve(1000, 450, LIST, none, none, false), D);
		assertNull(MediaListDragTarget.resolve(520, 230, LIST, none, none, false));
	}
}
