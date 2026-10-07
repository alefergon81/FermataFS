package me.aap.utils.db;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;

/**
 * Tests the file handling of {@link SQLite}. The Android database itself is stubbed on the JVM.
 */
public class SQLiteTest {
	@TempDir
	File tmp;

	@Test
	public void testDeleteClosed() throws IOException {
		File db = createFiles("closed.db");
		SQLite.delete(db);
		assertDeleted(db);
	}

	@Test
	public void testDeleteDeferredWhileReferenced() throws IOException {
		File db = createFiles("open.db");
		SQLite a = SQLite.get(db);
		SQLite b = SQLite.get(db);
		SQLite.delete(db);
		assertTrue(db.isFile());
		a.close();
		assertTrue(db.isFile());
		b.close();
		assertDeleted(db);
	}

	@Test
	public void testReopenAfterPendingDelete() throws IOException {
		File db = createFiles("reopen.db");
		SQLite a = SQLite.get(db);
		SQLite.delete(db);
		SQLite b = SQLite.get(db);
		a.close();
		// A new handle was opened after the deletion was requested, so the files stay
		assertTrue(db.isFile());
		b.close();
		assertTrue(db.isFile());
		SQLite.delete(db);
		assertDeleted(db);
	}

	private File createFiles(String name) throws IOException {
		File db = new File(tmp, name);
		assertTrue(db.createNewFile());
		for (String sfx : new String[]{"-journal", "-wal", "-shm"}) {
			assertTrue(new File(db.getPath() + sfx).createNewFile());
		}
		return db;
	}

	private static void assertDeleted(File db) {
		assertFalse(db.exists());
		for (String sfx : new String[]{"-journal", "-wal", "-shm"}) {
			assertFalse(new File(db.getPath() + sfx).exists());
		}
	}
}
