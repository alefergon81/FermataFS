package me.aap.utils.db;

import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;

import java.io.Closeable;
import java.io.File;
import java.util.HashMap;
import java.util.Map;

import javax.annotation.Nonnull;

import me.aap.utils.async.FutureSupplier;
import me.aap.utils.async.PromiseQueue;
import me.aap.utils.function.CheckedConsumer;
import me.aap.utils.function.CheckedFunction;
import me.aap.utils.log.Log;

/**
 * @author Andrey Pavlenko
 */
public class SQLite implements Closeable {
	private static final Map<File, DB> cache = new HashMap<>();
	private final DB db;
	private volatile boolean closed;

	private SQLite(DB db) {
		this.db = db;
	}

	public static SQLite get(File file) throws SQLiteException {
		return get(file, false);
	}

	/**
	 * @param wal enable write-ahead logging when the database is opened, so that other connections
	 *            to the same file can read while this one writes
	 */
	public static SQLite get(File file, boolean wal) throws SQLiteException {
		synchronized (cache) {
			DB db = cache.get(file);

			if ((db != null) && !db.deletePending) {
				db.refCounter++;
				return new SQLite(db);
			}

			SQLiteDatabase sqlite = open(file, wal);
			if (db != null) cache.remove(file); // The pending deletion of the old handle stays
			db = new DB(file, sqlite);
			cache.put(file, db);
			return new SQLite(db);
		}
	}

	/**
	 * Opens a new connection, not shared with {@link #get(File)}. The caller closes it.
	 */
	public static SQLiteDatabase open(File file, boolean wal) throws SQLiteException {
		SQLiteDatabase sqlite;

		try {
			File dir = file.getParentFile();
			if ((dir != null) && !dir.isDirectory() && !dir.mkdirs()) {
				Log.w("Failed to create directory: ", dir);
			}
			sqlite = SQLiteDatabase.openOrCreateDatabase(file, null);
		} catch (Exception ex) {
			Log.w("Failed to open database: ", file, ": ", ex, ". Retrying ...");
			sqlite = SQLiteDatabase.openOrCreateDatabase(file, null);
		}

		if (wal && (sqlite != null)) {
			try {
				sqlite.enableWriteAheadLogging();
			} catch (Exception ex) {
				Log.w(ex, "Failed to enable write-ahead logging: ", file);
			}
		}

		return sqlite;
	}

	/**
	 * Deletes the database file and its -journal, -wal and -shm files. If the database is open, the
	 * files are deleted when its last reference is closed.
	 */
	public static void delete(File file) throws SQLiteException {
		synchronized (cache) {
			DB db = cache.get(file);

			if (db != null) {
				db.deletePending = true;
				return;
			}
		}

		deleteFiles(file);
	}

	static void deleteFiles(File f) {
		if (f.isFile() && !f.delete()) Log.e("Failed to delete database file ", f);

		for (String sfx : new String[]{"-journal", "-wal", "-shm"}) {
			File j = new File(f.getPath() + sfx);
			if (j.isFile() && !j.delete()) Log.e("Failed to delete database file ", j);
		}
	}

	public <T> FutureSupplier<T> query(CheckedFunction<SQLiteDatabase, T, Throwable> f) {
		return db.queue.enqueue(() -> f.apply(db.sqlite));
	}

	public FutureSupplier<Void> execute(CheckedConsumer<SQLiteDatabase, Throwable> f) {
		return db.queue.enqueue(() -> {
			f.accept(db.sqlite);
			return null;
		});
	}

	public boolean isClosed() {
		return closed || (db.sqlite == null) || !db.sqlite.isOpen();
	}

	@Override
	public void close() {
		synchronized (this) {
			if (closed) return;
			closed = true;
		}

		boolean delete;

		synchronized (cache) {
			if (--db.refCounter != 0) return;
			if (cache.get(db.file) == db) cache.remove(db.file);
			db.close();
			delete = db.deletePending && !cache.containsKey(db.file);
		}

		if (delete) deleteFiles(db.file);
	}

	@Override
	protected void finalize() {
		close();
	}

	@Nonnull
	@Override
	public String toString() {
		return "SQLite{" +
				"db=" + db.sqlite +
				", closed=" + closed +
				'}';
	}

	private static final class DB {
		final PromiseQueue queue = new PromiseQueue();
		final File file;
		final SQLiteDatabase sqlite;
		int refCounter = 1;
		boolean deletePending;

		private DB(File file, SQLiteDatabase sqlite) {
			this.file = file;
			this.sqlite = sqlite;
		}

		void close() {
			try {
				if (sqlite != null) sqlite.close();
			} catch (Throwable ex) {
				Log.e(ex, "Failed to close database ", sqlite);
			}
		}

		@Override
		protected void finalize() {
			close();
		}
	}
}
