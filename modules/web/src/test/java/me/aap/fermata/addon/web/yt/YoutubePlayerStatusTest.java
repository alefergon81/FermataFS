package me.aap.fermata.addon.web.yt;

import static org.junit.Assert.assertEquals;

import org.junit.Before;
import org.junit.Test;

import me.aap.fermata.addon.web.yt.YoutubePlayerStatus.Status;

public class YoutubePlayerStatusTest {
	private YoutubePlayerStatus s;

	@Before
	public void setUp() {
		s = new YoutubePlayerStatus();
		s.setSkipAd(true);
	}

	@Test
	public void adWithSkip() {
		s.adState(true);
		assertEquals(Status.SKIPPING_AD, s.get());
	}

	@Test
	public void adWithSkipOff() {
		s.setSkipAd(false);
		s.adState(true);
		assertEquals(Status.NONE, s.get());
	}

	@Test
	public void adWithSkipPaused() {
		s.adState(true);
		s.paused();
		assertEquals(Status.NONE, s.get());
	}

	@Test
	public void adEndedBeforeContentPlays() {
		s.adState(true);
		s.adState(false);
		assertEquals(Status.SKIPPING_AD, s.get());
	}

	@Test
	public void contentPlaying() {
		s.adState(true);
		s.adState(false);
		s.playing();
		assertEquals(Status.NONE, s.get());
	}

	@Test
	public void waiting() {
		s.waiting();
		assertEquals(Status.LOADING, s.get());
	}

	@Test
	public void waitingPaused() {
		s.waiting();
		s.paused();
		assertEquals(Status.NONE, s.get());
	}

	@Test
	public void progressClearsWaiting() {
		s.waiting();
		s.progress();
		assertEquals(Status.NONE, s.get());
	}
}
