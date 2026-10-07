package me.aap.fermata.media.lib;

import org.junit.Assert;
import org.junit.Test;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

public class M3uParserTest extends Assert {

	@Test
	public void testParse() throws Exception {
		String m3u = "#EXTM3U url-tvg=\"http://epg/x.xml\" url-logo=\"http://logos\" catchup=\"shift\" " +
				"catchup-days=\"3\" catchup-source=\"?utc={utc}\"\r\n" +
				"#PLAYLIST:My list\r\n" +
				"#EXTALB:Header album\r\n" +
				"#EXTART:Header artist\r\n" +
				"#EXTGENRE:Header genre\r\n" +
				"#EXTIMG:cover.png\r\n" +
				"\r\n" +
				"#EXTINF:-1 tvg-id=\"ch1.id\" tvg-name=\"Ch One\" tvg-logo=\"one.png\" group-title=\"News, World\",Channel 1\r\n" +
				"http://h/live/1.ts\r\n" +
				"   \r\n" +
				"#EXTINF:120,Song\r\n" +
				"#EXTGRP:Music\r\n" +
				"#EXTALB:Album\r\n" +
				"#EXTART:Artist\r\n" +
				"#EXTGENRE:Rock\r\n" +
				"#EXTIMG:http://img/song.png\r\n" +
				"#EXT-X-MEDIA:TYPE=AUDIO\r\n" +
				"song.mp3\r\n" +
				"#EXTINF:0 catchup=\"flussonic\" catchup-days=\"7\" tvg-logo=\"http://abs/l.png\",Ungrouped\n" +
				"http://h/2.ts\n" +
				"#EXTINF:-1,After\n" +
				"\n" +
				"http://h/3.ts\n" +
				"http://h/no-extinf.ts\n";
		List<String> entries = new ArrayList<>();
		List<String> tvg = new ArrayList<>();
		M3uParser p = new M3uParser(M3uParser.TYPE_VIDEO);
		p.parse(new StringReader(m3u), new M3uParser.Handler() {
			@Override
			public void onEntry(M3uParser.Entry e) {
				entries.add(e.location + '|' + e.name + '|' + e.group + '|' + e.album + '|' + e.artist + '|' +
						e.genre + '|' + e.logo + '|' + e.tvgId + '|' + e.tvgName + '|' + e.catchup + '|' +
						e.catchupDays + '|' + e.catchupSource + '|' + e.duration + '|' + e.type);
			}

			@Override
			public void onTvgUrl(String url) {
				tvg.add(url);
			}
		});

		assertEquals(List.of("http://epg/x.xml"), tvg);
		assertEquals("http://epg/x.xml", p.getTvgUrl());
		assertEquals("My list", p.getName());
		assertEquals("cover.png", p.getCover());
		assertEquals("http://logos", p.getLogoUrlBase());
		assertEquals("shift", p.getCatchup());
		assertEquals("3", p.getCatchupDays());
		assertEquals("?utc={utc}", p.getCatchupSource());
		assertEquals(List.of(
				"http://h/live/1.ts|Channel 1|News, World|Header album|Header artist|Header genre|" +
						"http://logos/one.png|ch1.id|Ch One|shift|3|?utc={utc}|-1|2",
				"song.mp3|Song|Music|Album|Artist|Rock|http://img/song.png|null|null|shift|3|" +
						"?utc={utc}|120|1",
				"http://h/2.ts|Ungrouped|null|Header album|Header artist|Header genre|http://abs/l.png|" +
						"null|null|flussonic|7|?utc={utc}|0|2",
				"http://h/3.ts|After|null|Header album|Header artist|Header genre|cover.png|null|null|" +
						"shift|3|?utc={utc}|-1|2"
		), entries);
	}

	@Test
	public void testUnquotedHeaderAndNoName() throws Exception {
		String m3u = "#EXTM3U x-tvg-url=http://epg/a.xml.gz catchup=append\n" +
				"#EXTINF:-1 group-title=\"G\"\n" +
				"http://h/skipped.ts\n" +
				"#EXTINF:-1,Named\n" +
				"http://h/1.ts\n";
		List<String> entries = new ArrayList<>();
		M3uParser p = new M3uParser(M3uParser.TYPE_UNKNOWN);
		p.parse(new StringReader(m3u), e -> entries.add(e.location + '|' + e.group + '|' + e.catchup +
				'|' + e.type));
		assertEquals("http://epg/a.xml.gz", p.getTvgUrl());
		// An #EXTINF without a name does not produce an entry, and its group carries over
		assertEquals(List.of("http://h/1.ts|G|append|0"), entries);
	}
}
