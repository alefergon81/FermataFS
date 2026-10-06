package me.aap.fermata.auto;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static me.aap.fermata.auto.CarGesturePlan.DOWN;
import static me.aap.fermata.auto.CarGesturePlan.DRAG_IDLE_MS;
import static me.aap.fermata.auto.CarGesturePlan.MOVE;
import static me.aap.fermata.auto.CarGesturePlan.POINTER_DOWN;
import static me.aap.fermata.auto.CarGesturePlan.POINTER_UP;
import static me.aap.fermata.auto.CarGesturePlan.UP;

import org.junit.Before;
import org.junit.Test;

import java.util.List;

import me.aap.fermata.auto.CarGesturePlan.Event;

public class CarGesturePlanTest {
	private static final float D = 0.001f;
	private CarGesturePlan plan;

	@Before
	public void setUp() {
		plan = new CarGesturePlan();
		plan.setCenter(500, 300);
	}

	@Test
	public void clickIsDownUpAtPoint() {
		List<Event> l = plan.click(10, 20, 1000, 0);
		assertEquals(2, l.size());
		assertEvent(l.get(0), DOWN, 1000, 1000, 10, 20);
		assertEvent(l.get(1), UP, 1000, 1000 + CarGesturePlan.TAP_MS, 10, 20);
		assertFalse(l.get(1).deferred());
	}

	@Test
	public void longClickHoldsAndIsDeferred() {
		List<Event> l = plan.click(10, 20, 1000, 600);
		assertEvent(l.get(1), UP, 1000, 1600, 10, 20);
		assertTrue(l.get(1).deferred());
	}

	@Test
	public void dragStartsAtCenterWithoutTap() {
		List<Event> l = plan.scroll(0, 50, 1000);
		assertEquals(2, l.size());
		assertEvent(l.get(0), DOWN, 1000, 1000, 500, 300);
		// Distances are old minus new, so the finger moves up.
		assertEvent(l.get(1), MOVE, 1000, 1000, 500, 250);
		assertTrue(plan.isDragOpen());
	}

	@Test
	public void dragStartsAtLastTap() {
		plan.click(100, 400, 1000, 0);
		List<Event> l = plan.scroll(-10, 0, 2000);
		assertEvent(l.get(0), DOWN, 2000, 2000, 100, 400);
		assertEvent(l.get(1), MOVE, 2000, 2000, 110, 400);
	}

	@Test
	public void dragStartsAtLastPinchFocus() {
		plan.click(100, 400, 1000, 0);
		plan.scale(700, 200, 2f, 2000);
		List<Event> l = plan.scroll(0, 10, 3000);
		assertEvent(l.get(0), DOWN, 3000, 3000, 700, 200);
	}

	@Test
	public void scrollContinuesOpenDrag() {
		plan.scroll(0, 10, 1000);
		List<Event> l = plan.scroll(0, 10, 1100);
		assertEquals(1, l.size());
		assertEvent(l.get(0), MOVE, 1000, 1100, 500, 280);
	}

	@Test
	public void idleClosesDragOnlyAfterTimeout() {
		plan.scroll(0, 10, 1000);
		plan.scroll(0, 10, 1200);
		assertEquals(1200 + DRAG_IDLE_MS, plan.getIdleDeadline());
		assertTrue(plan.idle(1200 + DRAG_IDLE_MS - 1).isEmpty());
		List<Event> l = plan.idle(1200 + DRAG_IDLE_MS);
		assertEquals(1, l.size());
		assertEvent(l.get(0), UP, 1000, 1200 + DRAG_IDLE_MS, 500, 280);
		assertFalse(plan.isDragOpen());
		assertTrue(plan.idle(5000).isEmpty());
	}

	@Test
	public void clickClosesOpenDragFirst() {
		plan.scroll(0, 10, 1000);
		List<Event> l = plan.click(50, 60, 1100, 0);
		assertEquals(3, l.size());
		assertEvent(l.get(0), UP, 1000, 1100, 500, 290);
		assertEquals(DOWN, l.get(1).action());
		assertFalse(plan.isDragOpen());
	}

	@Test
	public void flingContinuesOpenDrag() {
		plan.scroll(0, 10, 1000);
		List<Event> l = plan.fling(0, -1000, 1050);
		// No new DOWN: the fling merges into the open drag.
		assertEquals(CarGesturePlan.FLING_STEPS + 1, l.size());
		float y = 290;
		long t = 1050;
		for (int i = 0; i < CarGesturePlan.FLING_STEPS; i++) {
			t += CarGesturePlan.FLING_STEP_MS;
			y -= 10;
			assertEvent(l.get(i), MOVE, 1000, t, 500, y);
		}
		assertEvent(l.get(l.size() - 1), UP, 1000, t, 500, y);
		assertFalse(plan.isDragOpen());
	}

	@Test
	public void flingWithoutDragStartsShortDragAtOrigin() {
		plan.click(100, 200, 1000, 0);
		List<Event> l = plan.fling(2000, 0, 2000);
		assertEquals(CarGesturePlan.FLING_STEPS + 2, l.size());
		assertEvent(l.get(0), DOWN, 2000, 2000, 100, 200);
		assertEvent(l.get(1), MOVE, 2000, 2010, 120, 200);
		assertEquals(UP, l.get(l.size() - 1).action());
		assertFalse(plan.isDragOpen());
	}

	@Test
	public void pinchUsesTwoPointersAroundFocus() {
		List<Event> l = plan.scale(400, 300, 2f, 1000);
		assertEquals(CarGesturePlan.PINCH_STEPS + 4, l.size());
		assertEquals(DOWN, l.get(0).action());
		assertEquals(1, l.get(0).pointerCount());
		assertEquals(POINTER_DOWN, l.get(1).action());
		assertEquals(2, l.get(1).pointerCount());
		Event last = l.get(l.size() - 3);
		assertEquals(MOVE, last.action());
		float half = CarGesturePlan.PINCH_SPAN; // span doubled: half span = PINCH_SPAN / 2 * 2
		assertEquals(400 - half, last.xs()[0], D);
		assertEquals(400 + half, last.xs()[1], D);
		assertEquals(POINTER_UP, l.get(l.size() - 2).action());
		assertEquals(UP, l.get(l.size() - 1).action());
		for (Event e : l) assertEquals(1000, e.downTime());
	}

	@Test
	public void pinchClosesOpenDrag() {
		plan.scroll(0, 10, 1000);
		List<Event> l = plan.scale(400, 300, 0.5f, 1100);
		assertEquals(UP, l.get(0).action());
		assertEquals(DOWN, l.get(1).action());
	}

	private static void assertEvent(Event e, int action, long down, long time, float x, float y) {
		assertEquals("action", action, e.action());
		assertEquals("downTime", down, e.downTime());
		assertEquals("eventTime", time, e.eventTime());
		assertEquals("x", x, e.x(), D);
		assertEquals("y", y, e.y(), D);
	}
}
