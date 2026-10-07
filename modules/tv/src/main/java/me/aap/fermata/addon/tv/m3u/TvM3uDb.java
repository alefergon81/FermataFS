package me.aap.fermata.addon.tv.m3u;

import static me.aap.fermata.media.lib.ContentKindClassifier.KIND_LIVE;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Schema and queries of a TV source's playlist database.
 */
final class TvM3uDb {
	static final int SCHEMA_VERSION = 1;
	// meta keys
	static final String M_SCHEMA_VERSION = "schema_version";
	static final String M_CONTENT_CRC32 = "content_crc32";
	static final String M_CONTENT_LENGTH = "content_length";
	static final String M_FILE_LENGTH = "file_length";
	static final String M_FILE_MTIME = "file_mtime";
	static final String M_CONTENT_CHECKED_AT = "content_checked_at";
	static final String M_VIDEO_FLAG = "video_flag";
	static final String M_GENERATION = "generation";
	static final String M_NAME = "name";
	static final String M_COVER = "cover";
	static final String M_LOGO_URL_BASE = "logo_url_base";
	static final String M_CATCHUP = "catchup";
	static final String M_CATCHUP_DAYS = "catchup_days";
	static final String M_CATCHUP_SOURCE = "catchup_source";
	static final String M_TVG_URL = "tvg_url";
	static final String M_KINDS_PRESENT = "kinds_present";
	static final String M_ENTRY_COUNT = "entry_count";
	static final String M_GROUP_COUNT = "group_count";
	static final String M_UNGROUPED_COUNT = "ungrouped_count";

	private static final String ENTRY_COLS = "gid, tid, seq, kind, name, rid, logo, tvg_id, " +
			"tvg_name, album, artist, genre, duration, type, catchup, catchup_days, catchup_source";
	private static final String GROUP_COLS = "gid, name, kind, count";

	private TvM3uDb() {
	}

	static Map<String, String> readMeta(SQLiteDatabase db) {
		Map<String, String> meta = new HashMap<>();
		if (!hasTable(db, "meta")) return meta;

		try (Cursor c = db.rawQuery("SELECT key, value FROM meta", null)) {
			while (c.moveToNext()) meta.put(c.getString(0), c.getString(1));
		}

		return meta;
	}

	static void writeMeta(SQLiteDatabase db, Map<String, String> meta) {
		db.execSQL("CREATE TABLE IF NOT EXISTS meta(key TEXT PRIMARY KEY, value TEXT)");
		for (Map.Entry<String, String> e : meta.entrySet()) {
			db.execSQL("INSERT OR REPLACE INTO meta(key, value) VALUES(?, ?)",
					new Object[]{e.getKey(), e.getValue()});
		}
	}

	/**
	 * Whether the database has a complete import of the current schema.
	 */
	static boolean isValid(SQLiteDatabase db, Map<String, String> meta) {
		return String.valueOf(SCHEMA_VERSION).equals(meta.get(M_SCHEMA_VERSION)) &&
				hasTable(db, "entries") && hasTable(db, "groups");
	}

	/**
	 * Drops every table and index, for a schema mismatch or an unreadable database.
	 */
	static void dropAll(SQLiteDatabase db) {
		List<String[]> objects = new ArrayList<>();

		try (Cursor c = db.rawQuery("SELECT type, name FROM sqlite_master WHERE type IN " +
				"('table', 'index') AND name NOT LIKE 'sqlite_%'", null)) {
			while (c.moveToNext()) objects.add(new String[]{c.getString(0), c.getString(1)});
		}

		for (String[] o : objects) {
			if ("table".equals(o[0])) db.execSQL("DROP TABLE IF EXISTS \"" + o[1] + '"');
		}
		for (String[] o : objects) {
			if ("index".equals(o[0])) db.execSQL("DROP INDEX IF EXISTS \"" + o[1] + '"');
		}
	}

	/**
	 * Drops the tables left by an interrupted import.
	 */
	static void dropNew(SQLiteDatabase db) {
		db.execSQL("DROP TABLE IF EXISTS entries_new");
		db.execSQL("DROP TABLE IF EXISTS groups_new");
	}

	static void createNew(SQLiteDatabase db) {
		dropNew(db);
		db.execSQL("CREATE TABLE groups_new(gid INTEGER PRIMARY KEY, name TEXT, kind INTEGER, " +
				"count INTEGER)");
		db.execSQL("CREATE TABLE entries_new(gid INTEGER, tid INTEGER, seq INTEGER, kind INTEGER, " +
				"name TEXT, rid TEXT, logo TEXT, tvg_id TEXT, tvg_name TEXT, album TEXT, artist TEXT, " +
				"genre TEXT, duration INTEGER, type INTEGER, catchup TEXT, catchup_days TEXT, " +
				"catchup_source TEXT, PRIMARY KEY (gid, tid)) WITHOUT ROWID");
	}

	static String insertEntrySql() {
		return "INSERT INTO entries_new(" + ENTRY_COLS +
				") VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
	}

	static String insertGroupSql() {
		return "INSERT INTO groups_new(" + GROUP_COLS + ") VALUES(?, ?, ?, ?)";
	}

	/**
	 * Replaces the current tables with the new ones. Must be called in a transaction.
	 */
	static void swap(SQLiteDatabase db, Map<String, String> meta) {
		db.execSQL("DROP TABLE IF EXISTS entries");
		db.execSQL("DROP TABLE IF EXISTS groups");
		db.execSQL("ALTER TABLE entries_new RENAME TO entries");
		db.execSQL("ALTER TABLE groups_new RENAME TO groups");
		// The indexes are built once, after the inserts, under their final names
		db.execSQL("DROP INDEX IF EXISTS ix_entries_rid");
		db.execSQL("DROP INDEX IF EXISTS ix_entries_kind");
		db.execSQL("DROP INDEX IF EXISTS ix_groups_name");
		db.execSQL("CREATE INDEX ix_entries_rid ON entries(rid, seq)");
		db.execSQL("CREATE INDEX ix_entries_kind ON entries(kind, gid, tid)");
		db.execSQL("CREATE INDEX ix_groups_name ON groups(name)");
		db.execSQL("CREATE TABLE IF NOT EXISTS meta(key TEXT PRIMARY KEY, value TEXT)");
		db.execSQL("DELETE FROM meta");
		writeMeta(db, meta);
	}

	static List<Group> queryGroups(SQLiteDatabase db, int kind) {
		String sel = (kind < 0) ? "" : " WHERE kind = " + kind;
		try (Cursor c = db.rawQuery("SELECT " + GROUP_COLS + " FROM groups" + sel + " ORDER BY gid",
				null)) {
			List<Group> l = new ArrayList<>(c.getCount());
			while (c.moveToNext()) l.add(new Group(c));
			return l;
		}
	}

	static int countGroups(SQLiteDatabase db, int kind) {
		try (Cursor c = db.rawQuery("SELECT count(*) FROM groups WHERE kind = ?",
				new String[]{String.valueOf(kind)})) {
			return c.moveToFirst() ? c.getInt(0) : 0;
		}
	}

	static int countUngrouped(SQLiteDatabase db, int kind) {
		try (Cursor c = db.rawQuery("SELECT count(*) FROM entries WHERE gid = -1 AND kind = ?",
				new String[]{String.valueOf(kind)})) {
			return c.moveToFirst() ? c.getInt(0) : 0;
		}
	}

	@Nullable
	static Group queryGroup(SQLiteDatabase db, int gid) {
		try (Cursor c = db.rawQuery("SELECT " + GROUP_COLS + " FROM groups WHERE gid = ?",
				new String[]{String.valueOf(gid)})) {
			return c.moveToFirst() ? new Group(c) : null;
		}
	}

	@Nullable
	static Group queryGroup(SQLiteDatabase db, String name) {
		try (Cursor c = db.rawQuery("SELECT " + GROUP_COLS + " FROM groups WHERE name = ? " +
				"ORDER BY gid LIMIT 1", new String[]{name})) {
			return c.moveToFirst() ? new Group(c) : null;
		}
	}

	/**
	 * The entries of a group, or the ungrouped entries (gid = -1) of the given kind (-1 for all).
	 */
	static List<Entry> queryEntries(SQLiteDatabase db, int gid, int kind) {
		String sql = "SELECT " + ENTRY_COLS + " FROM entries WHERE gid = ?" +
				((kind < 0) ? "" : " AND kind = " + kind) + " ORDER BY tid";
		try (Cursor c = db.rawQuery(sql, new String[]{String.valueOf(gid)})) {
			List<Entry> l = new ArrayList<>(c.getCount());
			while (c.moveToNext()) l.add(new Entry(c));
			return l;
		}
	}

	@Nullable
	static Entry queryEntry(SQLiteDatabase db, int gid, int tid) {
		try (Cursor c = db.rawQuery("SELECT " + ENTRY_COLS + " FROM entries WHERE gid = ? AND tid = ?",
				new String[]{String.valueOf(gid), String.valueOf(tid)})) {
			return c.moveToFirst() ? new Entry(c) : null;
		}
	}

	/**
	 * The entry with the given rid: the first one with the same tid, otherwise the first one in
	 * playlist order.
	 */
	@Nullable
	static Entry queryEntry(SQLiteDatabase db, String rid, int tid) {
		try (Cursor c = db.rawQuery("SELECT " + ENTRY_COLS + " FROM entries WHERE rid = ? " +
				"ORDER BY (tid = ?) DESC, seq LIMIT 1", new String[]{rid, String.valueOf(tid)})) {
			return c.moveToFirst() ? new Entry(c) : null;
		}
	}

	/**
	 * The entries to match against the EPG: every entry of a single-kind source, otherwise the live
	 * ones.
	 */
	static List<Entry> queryEpgEntries(SQLiteDatabase db, boolean mixed) {
		String sql = "SELECT " + ENTRY_COLS + " FROM entries" +
				(mixed ? " WHERE kind = " + KIND_LIVE : "") + " ORDER BY seq";
		try (Cursor c = db.rawQuery(sql, null)) {
			List<Entry> l = new ArrayList<>(c.getCount());
			while (c.moveToNext()) l.add(new Entry(c));
			return l;
		}
	}

	private static boolean hasTable(SQLiteDatabase db, String name) {
		try (Cursor c = db.rawQuery("SELECT count(*) FROM sqlite_master WHERE type = 'table' " +
				"AND name = ?", new String[]{name})) {
			return c.moveToFirst() && (c.getInt(0) != 0);
		}
	}

	static final class Group {
		final int gid;
		final String name;
		final int kind;
		final int count;

		Group(Cursor c) {
			gid = c.getInt(0);
			name = c.getString(1);
			kind = c.getInt(2);
			count = c.getInt(3);
		}
	}

	static final class Entry {
		final int gid;
		final int tid;
		final int seq;
		final int kind;
		final String name;
		final String rid;
		final String logo;
		final String tvgId;
		final String tvgName;
		final String album;
		final String artist;
		final String genre;
		final long duration;
		final byte type;
		final String catchup;
		final String catchupDays;
		final String catchupSource;

		Entry(Cursor c) {
			gid = c.getInt(0);
			tid = c.getInt(1);
			seq = c.getInt(2);
			kind = c.getInt(3);
			name = c.getString(4);
			rid = c.getString(5);
			logo = c.getString(6);
			tvgId = c.getString(7);
			tvgName = c.getString(8);
			album = c.getString(9);
			artist = c.getString(10);
			genre = c.getString(11);
			duration = c.getLong(12);
			type = (byte) c.getInt(13);
			catchup = c.getString(14);
			catchupDays = c.getString(15);
			catchupSource = c.getString(16);
		}
	}
}
