package me.aap.fermata.addon.tv.m3u;

import static me.aap.fermata.addon.tv.m3u.TvM3uDb.M_CATCHUP;
import static me.aap.fermata.addon.tv.m3u.TvM3uDb.M_CATCHUP_DAYS;
import static me.aap.fermata.addon.tv.m3u.TvM3uDb.M_CATCHUP_SOURCE;
import static me.aap.fermata.addon.tv.m3u.TvM3uDb.M_CONTENT_CHECKED_AT;
import static me.aap.fermata.addon.tv.m3u.TvM3uDb.M_CONTENT_CRC32;
import static me.aap.fermata.addon.tv.m3u.TvM3uDb.M_CONTENT_LENGTH;
import static me.aap.fermata.addon.tv.m3u.TvM3uDb.M_COVER;
import static me.aap.fermata.addon.tv.m3u.TvM3uDb.M_ENTRY_COUNT;
import static me.aap.fermata.addon.tv.m3u.TvM3uDb.M_FILE_LENGTH;
import static me.aap.fermata.addon.tv.m3u.TvM3uDb.M_FILE_MTIME;
import static me.aap.fermata.addon.tv.m3u.TvM3uDb.M_GENERATION;
import static me.aap.fermata.addon.tv.m3u.TvM3uDb.M_GROUP_COUNT;
import static me.aap.fermata.addon.tv.m3u.TvM3uDb.M_KINDS_PRESENT;
import static me.aap.fermata.addon.tv.m3u.TvM3uDb.M_LOGO_URL_BASE;
import static me.aap.fermata.addon.tv.m3u.TvM3uDb.M_NAME;
import static me.aap.fermata.addon.tv.m3u.TvM3uDb.M_SCHEMA_VERSION;
import static me.aap.fermata.addon.tv.m3u.TvM3uDb.M_TVG_URL;
import static me.aap.fermata.addon.tv.m3u.TvM3uDb.M_UNGROUPED_COUNT;
import static me.aap.fermata.addon.tv.m3u.TvM3uDb.M_VIDEO_FLAG;
import static me.aap.fermata.util.Utils.dynCtx;

import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteStatement;
import android.net.Uri;

import androidx.annotation.Nullable;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeSet;
import java.util.concurrent.CancellationException;
import java.util.zip.CRC32;
import java.util.zip.CheckedInputStream;
import java.util.zip.GZIPInputStream;
import java.util.zip.InflaterInputStream;

import me.aap.fermata.addon.tv.R;
import me.aap.fermata.media.lib.ContentKindClassifier;
import me.aap.fermata.media.lib.LoadProgress;
import me.aap.fermata.media.lib.M3uIdAssigner;
import me.aap.fermata.media.lib.M3uParser;
import me.aap.fermata.vfs.m3u.M3uFile;
import me.aap.utils.app.App;
import me.aap.utils.db.SQLite;
import me.aap.utils.log.Log;
import me.aap.utils.vfs.VfsManager;
import me.aap.utils.vfs.VirtualFolder;
import me.aap.utils.vfs.VirtualResource;

/**
 * Checks a TV source's playlist against its database and, when needed, streams it into new tables
 * and swaps them in. Runs on the import thread, with its own connection.
 */
final class TvM3uImporter {
	private static final int BATCH_SIZE = 5000;
	private final TvM3uFile file;
	private final VfsManager vfs;
	private final File dbFile;
	private final boolean force;
	private final TvM3uImport.State state;
	private final Callback cb;
	private volatile boolean cancelled;

	TvM3uImporter(TvM3uFile file, VfsManager vfs, File dbFile, boolean force,
								TvM3uImport.State state, Callback cb) {
		this.file = file;
		this.vfs = vfs;
		this.dbFile = dbFile;
		this.force = force;
		this.state = state;
		this.cb = cb;
	}

	void cancel() {
		cancelled = true;
	}

	/**
	 * Compares the playlist with the stamps of the last import. Runs on the shared executor, so that
	 * an up to date source does not wait for another source's import.
	 *
	 * @return the current import, or null if the playlist must be imported
	 */
	@Nullable
	Result check() throws IOException {
		return withDb(this::check);
	}

	/**
	 * Imports the playlist. Runs on the import thread.
	 */
	Result importNow() throws IOException {
		return withDb(db -> {
			Map<String, String> meta = readMeta(db);
			if (meta == null) {
				TvM3uDb.dropAll(db);
				meta = new HashMap<>();
			}
			return new Result(true, importPlaylist(db, meta, video(), System.currentTimeMillis()));
		});
	}

	private Result withDb(DbTask task) throws IOException {
		if (cancelled) throw new CancellationException();
		SQLiteDatabase db = open();

		try {
			return task.run(db);
		} catch (Throwable ex) {
			if (ex instanceof CancellationException) throw (CancellationException) ex;
			Log.e(ex, "Failed to import playlist: ", file);
			String msg = dynCtx(App.get()).getString(R.string.tv_import_failed_reason, file.getName(),
					TvM3uErrors.describe(ex));
			throw new IOException(msg, ex);
		} finally {
			db.close();
		}
	}

	private SQLiteDatabase open() {
		try {
			SQLiteDatabase db = SQLite.open(dbFile, true);
			TvM3uDb.readMeta(db);
			return db;
		} catch (Throwable ex) {
			Log.e(ex, "Unable to open playlist database, deleting it: ", dbFile);
			cb.closeReadHandle();
			SQLite.delete(dbFile);
			return SQLite.open(dbFile, true);
		}
	}

	/**
	 * The meta of a valid import, or null if the database has no valid import.
	 */
	@Nullable
	private Map<String, String> readMeta(SQLiteDatabase db) {
		try {
			Map<String, String> meta = TvM3uDb.readMeta(db);
			return TvM3uDb.isValid(db, meta) ? meta : null;
		} catch (Throwable ex) {
			Log.e(ex, "Unable to read playlist database: ", dbFile);
			return null;
		}
	}

	private String video() {
		return file.isVideo() ? "1" : "0";
	}

	@Nullable
	private Result check(SQLiteDatabase db) throws Throwable {
		Map<String, String> meta = readMeta(db);
		if (meta == null) return null;

		String video = video();
		boolean local = !isContentUri();
		long now = System.currentTimeMillis();

		if (!force && video.equals(meta.get(M_VIDEO_FLAG))) {
			if (local) {
				File f = file.getLocalFile();
				String len = String.valueOf(f.length());
				String mtime = String.valueOf(f.lastModified());

				if (len.equals(meta.get(M_FILE_LENGTH)) && mtime.equals(meta.get(M_FILE_MTIME))) {
					Log.i("Playlist is up to date: ", file.getName());
					return new Result(false, meta);
				}

				Log.i("Playlist file changed, checking content: ", file.getName());
				long[] crc = crc();

				if (sameContent(meta, crc)) {
					Log.i("Playlist content is unchanged, skipping import: ", file.getName());
					Map<String, String> m = new HashMap<>();
					m.put(M_FILE_LENGTH, len);
					m.put(M_FILE_MTIME, mtime);
					TvM3uDb.writeMeta(db, m);
					meta.putAll(m);
					return new Result(false, meta);
				}
			} else {
				long checked = parseLong(meta.get(M_CONTENT_CHECKED_AT));
				int age = file.getMaxAge();
				if (age <= 0) age = M3uFile.M3U_FILE_AGE;

				if ((now - checked) < (age * 1000L)) {
					Log.i("Playlist is up to date: ", file.getName());
					return new Result(false, meta);
				}

				Log.i("Checking playlist content: ", file.getName());
				long[] crc = crc();

				if (sameContent(meta, crc)) {
					Log.i("Playlist content is unchanged, skipping import: ", file.getName());
					Map<String, String> m = new HashMap<>();
					m.put(M_CONTENT_CHECKED_AT, String.valueOf(now));
					TvM3uDb.writeMeta(db, m);
					meta.putAll(m);
					return new Result(false, meta);
				}
			}
		}

		return null;
	}

	private Map<String, String> importPlaylist(SQLiteDatabase db, Map<String, String> oldMeta,
																						 String video, long now) throws Throwable {
		Log.i("Importing playlist: ", file.getName(), force ? " (forced)" : "");
		long start = System.currentTimeMillis();
		long total = isContentUri() ? -1 : file.getLocalFile().length();
		state.setProgress((total > 0) ? 0 : LoadProgress.INDETERMINATE);
		VirtualFolder dir = file.getParent().peek();
		M3uParser parser = new M3uParser(file.isVideo() ? M3uParser.TYPE_VIDEO : M3uParser.TYPE_UNKNOWN);
		M3uIdAssigner ids = new M3uIdAssigner();
		Map<String, int[]> groups = new HashMap<>(); // name -> {gid, kind, count}
		TreeSet<Integer> kinds = new TreeSet<>();
		CRC32 crc = new CRC32();
		CountingInputStream raw = null;
		boolean inTx = false;

		try {
			TvM3uDb.createNew(db);
			SQLiteStatement insert = db.compileStatement(TvM3uDb.insertEntrySql());
			raw = new CountingInputStream(openRaw());
			CountingInputStream counter = raw;
			int[] lastProgress = {-2};
			db.beginTransactionNonExclusive();
			inTx = true;

			parser.parse(createReader(new CheckedInputStream(counter, crc)), new M3uParser.Handler() {
				@Override
				public void onEntry(M3uParser.Entry e) {
					if (cancelled) throw new CancellationException();
					VirtualResource res = vfs.resolve(e.location, dir).get(null);
					if (!ids.assign(e.group, res != null)) return;
					int kind = ContentKindClassifier.classify(e.location, e.name, e.duration);
					int gid = ids.getGroupId();

					if (e.group == null) {
						kinds.add(kind);
					} else {
						int[] g = groups.get(e.group);
						if (g == null) groups.put(e.group, g = new int[]{gid, kind, 0});
						g[2]++;
					}

					insert.clearBindings();
					insert.bindLong(1, gid);
					insert.bindLong(2, ids.getTrackId());
					insert.bindLong(3, ids.getSeq());
					insert.bindLong(4, kind);
					bind(insert, 5, e.name);
					bind(insert, 6, res.getRid().toString());
					bind(insert, 7, e.logo);
					bind(insert, 8, e.tvgId);
					bind(insert, 9, e.tvgName);
					bind(insert, 10, e.album);
					bind(insert, 11, e.artist);
					bind(insert, 12, e.genre);
					insert.bindLong(13, e.duration);
					insert.bindLong(14, e.type);
					bind(insert, 15, e.catchup);
					bind(insert, 16, e.catchupDays);
					bind(insert, 17, e.catchupSource);
					insert.executeInsert();

					if ((ids.getEntryCount() % BATCH_SIZE) == 0) {
						db.setTransactionSuccessful();
						db.endTransaction();
						db.beginTransactionNonExclusive();

						if (total > 0) {
							int p = (int) Math.min(LoadProgress.MAX - 1,
									counter.count * LoadProgress.MAX / total);
							if (p != lastProgress[0]) state.setProgress(lastProgress[0] = p);
						}
					}
				}
			});

			if (cancelled) throw new CancellationException();
			int count = ids.getEntryCount();
			if (count == 0) {
				throw new IOException(dynCtx(App.get()).getString((counter.count == 0)
						? R.string.tv_err_empty_playlist : R.string.tv_err_no_entries));
			}

			SQLiteStatement insertGroup = db.compileStatement(TvM3uDb.insertGroupSql());
			for (Map.Entry<String, int[]> e : groups.entrySet()) {
				int[] g = e.getValue();
				kinds.add(g[1]);
				insertGroup.clearBindings();
				insertGroup.bindLong(1, g[0]);
				insertGroup.bindString(2, e.getKey());
				insertGroup.bindLong(3, g[1]);
				insertGroup.bindLong(4, g[2]);
				insertGroup.executeInsert();
			}

			Map<String, String> meta = new HashMap<>();
			meta.put(M_SCHEMA_VERSION, String.valueOf(TvM3uDb.SCHEMA_VERSION));
			meta.put(M_CONTENT_CRC32, String.valueOf(crc.getValue()));
			meta.put(M_CONTENT_LENGTH, String.valueOf(counter.count));
			meta.put(M_VIDEO_FLAG, video);
			meta.put(M_CONTENT_CHECKED_AT, String.valueOf(now));
			meta.put(M_GENERATION, String.valueOf(parseLong(oldMeta.get(M_GENERATION)) + 1));
			meta.put(M_ENTRY_COUNT, String.valueOf(count));
			meta.put(M_GROUP_COUNT, String.valueOf(ids.getGroupCount()));
			meta.put(M_UNGROUPED_COUNT, String.valueOf(ids.getUngroupedCount()));
			meta.put(M_KINDS_PRESENT, joinKinds(kinds));
			putIfNotNull(meta, M_NAME, parser.getName());
			putIfNotNull(meta, M_COVER, parser.getCover());
			putIfNotNull(meta, M_LOGO_URL_BASE, parser.getLogoUrlBase());
			putIfNotNull(meta, M_CATCHUP, parser.getCatchup());
			putIfNotNull(meta, M_CATCHUP_DAYS, parser.getCatchupDays());
			putIfNotNull(meta, M_CATCHUP_SOURCE, parser.getCatchupSource());
			putIfNotNull(meta, M_TVG_URL, parser.getTvgUrl());

			if (!isContentUri()) {
				File f = file.getLocalFile();
				meta.put(M_FILE_LENGTH, String.valueOf(f.length()));
				meta.put(M_FILE_MTIME, String.valueOf(f.lastModified()));
			}

			// Commit the inserts, then swap in one transaction
			db.setTransactionSuccessful();
			db.endTransaction();
			inTx = false;
			state.setProgress(LoadProgress.MAX - 1);
			db.beginTransaction();
			inTx = true;
			TvM3uDb.swap(db, meta);
			db.setTransactionSuccessful();
			db.endTransaction();
			inTx = false;

			Log.i("Playlist imported in ", System.currentTimeMillis() - start, " ms: ",
					file.getName(), ": entries=", count, ", groups=", ids.getGroupCount(),
					", kinds=", meta.get(M_KINDS_PRESENT), ", bytes=", counter.count);
			return meta;
		} catch (Throwable ex) {
			if (inTx) {
				try {
					db.endTransaction(); // Roll back
				} catch (Throwable err) {
					Log.e(err, "Failed to roll back the import");
				}
			}

			try {
				TvM3uDb.dropNew(db);
			} catch (Throwable err) {
				Log.e(err, "Failed to drop the import tables");
			}

			throw ex;
		} finally {
			if (raw != null) {
				try {
					raw.close();
				} catch (IOException ignore) {
				}
			}
		}
	}

	private static boolean sameContent(Map<String, String> meta, long[] crc) {
		return String.valueOf(crc[0]).equals(meta.get(M_CONTENT_CRC32)) &&
				String.valueOf(crc[1]).equals(meta.get(M_CONTENT_LENGTH));
	}

	/**
	 * CRC32 and length of the raw content.
	 */
	private long[] crc() throws IOException {
		CRC32 crc = new CRC32();
		long len = 0;
		byte[] buf = new byte[65536];

		try (InputStream in = openRaw()) {
			for (int n = in.read(buf); n != -1; n = in.read(buf)) {
				if (cancelled) throw new CancellationException();
				crc.update(buf, 0, n);
				len += n;
			}
		}

		return new long[]{crc.getValue(), len};
	}

	private boolean isContentUri() {
		String url = file.getUrl();
		return (url != null) && url.startsWith("content://");
	}

	private InputStream openRaw() throws IOException {
		if (isContentUri()) {
			InputStream in = App.get().getContentResolver().openInputStream(Uri.parse(file.getUrl()));
			if (in == null) throw new IOException("Unable to open " + file.getUrl());
			return new BufferedInputStream(in, 65536);
		}

		return new BufferedInputStream(new FileInputStream(file.getLocalFile()), 65536);
	}

	private Reader createReader(InputStream in) throws IOException {
		String enc = file.getContentEncoding();
		String cs = file.getCharacterEncoding();

		if (enc != null) {
			if ("gzip".equals(enc)) {
				in = new GZIPInputStream(in, 65536);
			} else if ("deflate".equals(enc)) {
				in = new InflaterInputStream(in);
			} else {
				throw new IOException("Unsupported content encoding: " + enc);
			}
		}

		return new InputStreamReader(in, (cs == null) ? "UTF-8" : cs);
	}

	private static String joinKinds(TreeSet<Integer> kinds) {
		StringBuilder sb = new StringBuilder();
		for (int k : kinds) {
			if (sb.length() != 0) sb.append(',');
			sb.append(k);
		}
		return sb.toString();
	}

	private static void putIfNotNull(Map<String, String> m, String k, @Nullable String v) {
		if (v != null) m.put(k, v);
	}

	private static void bind(SQLiteStatement s, int idx, @Nullable String v) {
		if (v == null) s.bindNull(idx);
		else s.bindString(idx, v);
	}

	static long parseLong(@Nullable String s) {
		if (s == null) return 0;
		try {
			return Long.parseLong(s);
		} catch (NumberFormatException ex) {
			return 0;
		}
	}

	private interface DbTask {
		Result run(SQLiteDatabase db) throws Throwable;
	}

	interface Callback {
		/**
		 * Closes the source's read handle, before an unopenable database file is deleted.
		 */
		void closeReadHandle();
	}

	static final class Result {
		/**
		 * Whether new tables were swapped in.
		 */
		final boolean imported;
		final Map<String, String> meta;

		Result(boolean imported, Map<String, String> meta) {
			this.imported = imported;
			this.meta = meta;
		}
	}

	private static final class CountingInputStream extends FilterInputStream {
		volatile long count;

		CountingInputStream(InputStream in) {
			super(in);
		}

		@Override
		public int read() throws IOException {
			int b = super.read();
			if (b != -1) count++;
			return b;
		}

		@Override
		public int read(byte[] b, int off, int len) throws IOException {
			int n = super.read(b, off, len);
			if (n > 0) count += n;
			return n;
		}

		@Override
		public long skip(long n) throws IOException {
			long s = super.skip(n);
			count += s;
			return s;
		}
	}
}
