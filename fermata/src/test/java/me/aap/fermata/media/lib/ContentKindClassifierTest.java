package me.aap.fermata.media.lib;

import static me.aap.fermata.media.lib.ContentKindClassifier.KIND_LIVE;
import static me.aap.fermata.media.lib.ContentKindClassifier.KIND_MOVIE;
import static me.aap.fermata.media.lib.ContentKindClassifier.KIND_SERIES;
import static me.aap.fermata.media.lib.ContentKindClassifier.classify;

import org.junit.Assert;
import org.junit.Test;

public class ContentKindClassifierTest extends Assert {

	@Test
	public void testXtreamPaths() {
		assertEquals(KIND_MOVIE, classify("http://h/movie/u/p/1.mkv", "Film", -1));
		assertEquals(KIND_SERIES, classify("http://h/series/u/p/2.mp4", "Show", -1));
		assertEquals(KIND_LIVE, classify("http://h/u/p/3.ts", "Channel", -1));
		assertEquals(KIND_LIVE, classify("http://h/live/u/p/4.mp4", "Channel S01E02", 100));
		assertEquals(KIND_MOVIE, classify("HTTP://H/MOVIE/U/P/5.MKV", null, 0));
		// Only the path counts, not the host or the query
		assertEquals(KIND_LIVE, classify("http://movie.h/u/p/6?x=/series/", "Channel", -1));
	}

	@Test
	public void testVodExtension() {
		assertEquals(KIND_SERIES, classify("http://h/files/show.mkv", "Show S02E05", -1));
		assertEquals(KIND_SERIES, classify("http://h/files/show.mp4?token=1", "Show 1x02", -1));
		assertEquals(KIND_MOVIE, classify("http://h/files/film.avi", "Film (2020)", -1));
		assertEquals(KIND_MOVIE, classify("film.webm", "Film", 0));
	}

	@Test
	public void testEpisodeMarker() {
		assertEquals(KIND_SERIES, classify("http://h/stream/abc", "Show S1 E2", -1));
		assertEquals(KIND_SERIES, classify("http://h/stream/abc", "Show 1x02", -1));
		assertEquals(KIND_SERIES, classify("http://h/stream/abc", "Show s01e02", 0));
		assertEquals(KIND_LIVE, classify("http://h/stream/abc", "Channel 1080x", -1));
	}

	@Test
	public void testDuration() {
		assertEquals(KIND_MOVIE, classify("http://h/stream/abc", "Film", 5400));
		assertEquals(KIND_LIVE, classify("http://h/stream/abc", "Channel", 0));
	}

	@Test
	public void testUnknownIsLive() {
		assertEquals(KIND_LIVE, classify("http://h/stream/abc", "Channel", -1));
		assertEquals(KIND_LIVE, classify("http://h/stream/abc.ts", null, -1));
		assertEquals(KIND_LIVE, classify(null, null, 0));
	}
}
