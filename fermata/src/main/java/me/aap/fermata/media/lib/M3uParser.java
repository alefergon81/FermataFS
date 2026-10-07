package me.aap.fermata.media.lib;

import static me.aap.utils.text.TextUtils.indexOfChar;

import androidx.annotation.Nullable;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;

import me.aap.utils.text.TextUtils;

/**
 * Streaming M3U line grammar shared by {@link M3uItem} and the TV playlist importer.
 * The handler is called once per playlist entry; the entry object is reused between calls.
 */
public final class M3uParser {
	public static final byte TYPE_UNKNOWN = M3uTrackItem.TYPE_UNKNOWN;
	public static final byte TYPE_AUDIO = M3uTrackItem.TYPE_AUDIO;
	public static final byte TYPE_VIDEO = M3uTrackItem.TYPE_VIDEO;
	private final Entry entry = new Entry();
	private String name;
	private String album;
	private String artist;
	private String genre;
	private String logoUrlBase;
	private String cover;
	private String catchup;
	private String catchupDays;
	private String catchupSource;
	private String tvgUrl;
	private byte type;

	public M3uParser(byte defaultType) {
		type = defaultType;
	}

	public void parse(Reader reader, Handler handler) throws Exception {
		Entry e = entry;
		boolean first = true;
		e.reset(null, null, null);

		try (BufferedReader r = (reader instanceof BufferedReader) ? (BufferedReader) reader :
				new BufferedReader(reader)) {
			read:
			for (String l = r.readLine(); l != null; l = r.readLine()) {
				l = l.trim();

				if (l.isEmpty()) {
					continue;
				} else if (l.startsWith("#EXTM3U")) {
					for (int off = 7, len = l.length(); off < len; ) {
						int i = l.indexOf('=', off);
						if (i == -1) continue read;
						String key = l.substring(off, i).trim();

						if ((++i != len) && (l.charAt(i) == '"')) {
							off = i + 1;
							i = l.indexOf('"', off);
							if (i == -1) i = len;
						} else {
							off = i;
							i = indexOfChar(l, " \t", off, len);
							if (i == -1) i = len;
						}

						switch (key) {
							case "logo-url":
							case "url-logo":
								logoUrlBase = trim(l.substring(off, i));
								break;
							case "url-epg":
							case "url-tvg":
							case "tvg-url":
							case "x-tvg-url":
								String url = trim(l.substring(off, i));
								if (url != null) {
									tvgUrl = url;
									handler.onTvgUrl(url);
								}
								break;
							case "catchup":
								catchup = e.catchup = trim(l.substring(off, i));
								break;
							case "catchup-days":
								catchupDays = e.catchupDays = trim(l.substring(off, i));
								break;
							case "catchup-source":
								catchupSource = e.catchupSource = trim(l.substring(off, i));
								break;
						}

						off = i + 1;
					}
				} else if (l.startsWith("#EXTINF:")) {
					first = false;
					int len = l.length();
					int i = indexOfChar(l, " \t,", 8, len);
					if (i == -1) continue;

					e.duration = TextUtils.toLong(l, 8, i, 0);

					if (l.charAt(i) == ',') {
						e.name = l.substring(i + 1);
						continue;
					}

					for (; ; ) {
						int start = i + 1;
						i = indexOfChar(l, "=,", start, len);
						if (i == -1) continue read;
						if (l.charAt(i) != '=') {
							e.name = l.substring(i + 1);
							continue read;
						}

						String key = l.substring(start, i).trim();
						i = l.indexOf('\"', i + 1);
						if (i == -1) continue read;

						start = i + 1;
						i = l.indexOf('\"', start);
						if (i == -1) continue read;

						switch (key) {
							case "logo":
							case "tvg-logo":
								e.logo = trim(l.substring(start, i));
								break;
							case "tvg-id":
								e.tvgId = trim(l.substring(start, i));
								break;
							case "tvg-name":
								e.tvgName = trim(l.substring(start, i));
								break;
							case "group-title":
								e.group = trim(l.substring(start, i));
								break;
							case "catchup":
								e.catchup = trim(l.substring(start, i));
								break;
							case "tvg-rec":
							case "catchup-days":
								e.catchupDays = trim(l.substring(start, i));
								break;
							case "catchup-source":
								e.catchupSource = trim(l.substring(start, i));
								break;
						}
					}
				} else if (l.startsWith("#EXTGRP:")) {
					e.group = trim(l.substring(8));
				} else if (l.startsWith("#PLAYLIST:")) {
					name = trim(l.substring(10));
				} else if (l.startsWith("#EXTALB:")) {
					if (first) album = trim(l.substring(8));
					else e.album = trim(l.substring(8));
				} else if (l.startsWith("#EXTART:")) {
					if (first) artist = trim(l.substring(8));
					else e.artist = trim(l.substring(8));
				} else if (l.startsWith("#EXTGENRE:")) {
					if (first) genre = trim(l.substring(10));
					else e.genre = trim(l.substring(10));
				} else if (l.startsWith("#EXTIMG:")) {
					if (first) cover = trim(l.substring(8));
					else e.logo = trim(l.substring(8));
				} else if (l.startsWith("#EXT-X-MEDIA:")) {
					byte t = 0;
					if (l.contains("TYPE=AUDIO")) t = TYPE_AUDIO;
					else if (l.contains("TYPE=VIDEO")) t = TYPE_VIDEO;

					if (t != 0) {
						if (first) type = t;
						else e.type = t;
					}
				} else if ((e.name != null) && !l.startsWith("#")) {
					if (e.album == null) e.album = album;
					if (e.artist == null) e.artist = artist;
					if (e.genre == null) e.genre = genre;
					if (e.type == 0) e.type = type;
					if (e.logo == null) e.logo = cover;
					else if ((logoUrlBase != null) && !e.logo.contains("://"))
						e.logo = logoUrlBase + '/' + e.logo;
					e.location = l;
					handler.onEntry(e);
					e.reset(catchup, catchupDays, catchupSource);
				}
			}
		}
	}

	/**
	 * The playlist name from #PLAYLIST, or null.
	 */
	@Nullable
	public String getName() {
		return name;
	}

	/**
	 * The playlist cover from the header #EXTIMG, or null.
	 */
	@Nullable
	public String getCover() {
		return cover;
	}

	@Nullable
	public String getLogoUrlBase() {
		return logoUrlBase;
	}

	@Nullable
	public String getCatchup() {
		return catchup;
	}

	@Nullable
	public String getCatchupDays() {
		return catchupDays;
	}

	@Nullable
	public String getCatchupSource() {
		return catchupSource;
	}

	@Nullable
	public String getTvgUrl() {
		return tvgUrl;
	}

	private static String trim(String s) {
		return (s == null) || (s = s.trim()).isEmpty() ? null : s;
	}

	public interface Handler {
		void onEntry(Entry e) throws Exception;

		default void onTvgUrl(String url) {
		}
	}

	/**
	 * A playlist entry, with the header defaults (album, artist, genre, type, logo) applied.
	 */
	public static final class Entry {
		public String location;
		public String name;
		public String group;
		public String album;
		public String artist;
		public String genre;
		public String logo;
		public String tvgId;
		public String tvgName;
		public String catchup;
		public String catchupDays;
		public String catchupSource;
		public long duration;
		public byte type;

		void reset(String catchup, String catchupDays, String catchupSource) {
			location = name = group = album = artist = genre = logo = tvgId = tvgName = null;
			this.catchup = catchup;
			this.catchupDays = catchupDays;
			this.catchupSource = catchupSource;
			duration = 0;
			type = 0;
		}
	}
}
