package me.aap.fermata.media.lib;

import androidx.annotation.Nullable;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Classifies a playlist entry as live TV, movie or series. The first matching rule wins:
 * Xtream URL path, VOD file extension, season/episode marker in the title, positive duration,
 * and live TV otherwise.
 */
public final class ContentKindClassifier {
	public static final int KIND_LIVE = 0;
	public static final int KIND_MOVIE = 1;
	public static final int KIND_SERIES = 2;
	private static final Set<String> VOD_EXT = Set.of("mp4", "mkv", "avi", "mov", "wmv", "flv",
			"m4v", "mpg", "mpeg", "webm");
	private static final Pattern EPISODE =
			Pattern.compile("(?i)\\bS\\d{1,3}\\s*E\\d{1,4}\\b|\\b\\d{1,2}x\\d{2,3}\\b");

	private ContentKindClassifier() {
	}

	public static int classify(@Nullable String url, @Nullable String title, long duration) {
		String path = (url == null) ? "" : path(url).toLowerCase(Locale.ROOT);
		if (path.contains("/movie/")) return KIND_MOVIE;
		if (path.contains("/series/")) return KIND_SERIES;
		if (path.contains("/live/")) return KIND_LIVE;

		boolean episode = (title != null) && EPISODE.matcher(title).find();
		if (VOD_EXT.contains(extension(path))) return episode ? KIND_SERIES : KIND_MOVIE;
		if (episode) return KIND_SERIES;
		if (duration > 0) return KIND_MOVIE;
		return KIND_LIVE;
	}

	private static String path(String url) {
		int start = url.indexOf("://");

		if (start != -1) {
			start = url.indexOf('/', start + 3);
			if (start == -1) return "";
		} else {
			start = 0;
		}

		int end = url.length();
		int q = url.indexOf('?', start);
		if (q != -1) end = q;
		int h = url.indexOf('#', start);
		if ((h != -1) && (h < end)) end = h;
		return url.substring(start, end);
	}

	private static String extension(String path) {
		int slash = path.lastIndexOf('/');
		int dot = path.lastIndexOf('.');
		return (dot > slash) ? path.substring(dot + 1) : "";
	}
}
