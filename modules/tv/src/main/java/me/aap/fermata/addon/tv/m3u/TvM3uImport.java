package me.aap.fermata.addon.tv.m3u;

import static me.aap.utils.async.Completed.completed;
import static me.aap.utils.async.Completed.failed;

import androidx.annotation.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import me.aap.fermata.media.lib.LoadProgress;
import me.aap.utils.app.App;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.concurrent.ThreadPool;
import me.aap.utils.log.Log;
import me.aap.utils.net.http.HttpFileDownloader;
import me.aap.utils.vfs.VfsManager;

/**
 * Process-wide registry of playlist imports, keyed by the playlist database file. At most one
 * check or import per file runs at a time. Checks run on the shared executor, and imports run one
 * after another on a dedicated thread.
 */
final class TvM3uImport {
	private static final ThreadPool executor = new ThreadPool(1, 1);
	private static final Map<File, TvM3uImport> registry = new HashMap<>();
	private static final State downloads = new State();
	private static int activeDownloads;
	private final File dbFile;
	private final State state = new State();
	private FutureSupplier<TvM3uImporter.Result> inFlight;
	private TvM3uImporter importer;
	private boolean force;

	private TvM3uImport(File dbFile) {
		this.dbFile = dbFile;
	}

	static TvM3uImport get(File dbFile) {
		synchronized (registry) {
			TvM3uImport i = registry.get(dbFile);
			if (i == null) registry.put(dbFile, i = new TvM3uImport(dbFile));
			return i;
		}
	}

	/**
	 * Checks the playlist and imports it if needed. Returns the in-flight import if there is one.
	 */
	FutureSupplier<TvM3uImporter.Result> ensure(TvM3uFile file, VfsManager vfs,
																							TvM3uImporter.Callback cb) {
		synchronized (this) {
			if (inFlight != null) return inFlight;
			boolean f = force;
			force = false;
			TvM3uImporter imp = importer = new TvM3uImporter(file, vfs, dbFile, f, state, cb);
			state.set(Status.CHECKING, LoadProgress.INDETERMINATE, null);
			// Re-download if the max age is reached or the stamps were cleared. The downloaded file is
			// renamed into place only when complete, so it is never read partially.
			FutureSupplier<TvM3uImporter.Result> p = TvM3uFileSystem.getInstance().reload(file)
					.ifFail(err -> {
						Log.e(err, "Failed to load playlist, using the cached file: ", file);
						return file;
					})
					.then(v -> App.get().execute(imp::check))
					.then(r -> (r != null) ? completed(r) : submit(imp));
			inFlight = p;
			p.onCompletion((r, err) -> {
				synchronized (this) {
					if (inFlight == p) {
						inFlight = null;
						importer = null;
					}
					if ((err != null) && f) force = true; // Retry the forced import
				}

				if (err == null) {
					state.set(Status.DONE, LoadProgress.MAX, null);
				} else {
					Log.e(err, "Playlist import failed: ", dbFile);
					state.set(Status.FAILED, LoadProgress.MAX, err);
				}
			});
			return p;
		}
	}

	private static FutureSupplier<TvM3uImporter.Result> submit(TvM3uImporter imp) {
		try {
			return executor.submitTask(imp::importNow);
		} catch (Throwable ex) {
			return failed(ex);
		}
	}

	/**
	 * Progress of the TV playlist downloads, whichever source they belong to. Used where the source
	 * is not known yet (adding a source) or where several sources load (the list of sources).
	 */
	static LoadProgress getDownloadProgress() {
		return downloads;
	}

	/**
	 * A listener that publishes the progress of this source's playlist download.
	 */
	HttpFileDownloader.StatusListener createDownloadListener() {
		return new HttpFileDownloader.StatusListener() {
			private boolean started;
			private long lastUpdate;
			private int lastProgress = -2;

			@Override
			public void onProgress(HttpFileDownloader.Status status) {
				long total = status.getLength();
				long bytes = status.bytesDownloaded();
				int p = (total > 0) ? (int) Math.min(LoadProgress.MAX, bytes * LoadProgress.MAX / total)
						: LoadProgress.INDETERMINATE;
				long now = System.currentTimeMillis();

				if (!started) {
					started = true;
					synchronized (registry) {
						activeDownloads++;
					}
				} else if ((p == lastProgress) && ((now - lastUpdate) < 250)) {
					return;
				}

				lastProgress = p;
				lastUpdate = now;
				state.setDownload(p, bytes);
				downloads.setDownload(p, bytes);
			}

			@Override
			public void onSuccess(HttpFileDownloader.Status status) {
				done();
			}

			@Override
			public void onFailure(HttpFileDownloader.Status status) {
				done();
			}

			private void done() {
				if (!started) return;
				started = false;
				boolean last;

				synchronized (registry) {
					last = (--activeDownloads == 0);
				}

				if (last) downloads.set(Status.DONE, LoadProgress.MAX, null);

				synchronized (TvM3uImport.this) {
					// Back to the check that waits for the download, if any
					if (inFlight != null) state.set(Status.CHECKING, LoadProgress.INDETERMINATE, null);
					else state.set(Status.DONE, LoadProgress.MAX, null);
				}
			}
		};
	}

	/**
	 * The next import ignores the stamps and imports the playlist again.
	 */
	synchronized void setForce() {
		force = true;
	}

	/**
	 * Cancels the in-flight import, if any, and drops this file from the registry.
	 */
	void cancel() {
		synchronized (this) {
			if (importer != null) importer.cancel();
		}
		synchronized (registry) {
			registry.remove(dbFile);
		}
	}

	/**
	 * The import progress while the playlist is checked or imported, or while a failure has not been
	 * reported, otherwise null.
	 */
	@Nullable
	LoadProgress getLoadProgress() {
		return state.isActive() ? state : null;
	}

	enum Status {
		DOWNLOADING, CHECKING, IMPORTING, DONE, FAILED
	}

	/**
	 * The observable import state. Listeners are called on the main thread.
	 */
	static final class State implements LoadProgress {
		private final List<Listener> listeners = new ArrayList<>();
		private Status status = Status.DONE;
		private int progress;
		private long bytes;
		private Throwable error;

		synchronized boolean isActive() {
			return (status == Status.DOWNLOADING) || (status == Status.CHECKING) ||
					(status == Status.IMPORTING) || (error != null);
		}

		void setProgress(int progress) {
			set(Status.IMPORTING, progress, null);
		}

		void setDownload(int progress, long bytes) {
			synchronized (this) {
				this.bytes = bytes;
			}
			set(Status.DOWNLOADING, progress, null);
		}

		void set(Status status, int progress, @Nullable Throwable error) {
			synchronized (this) {
				this.status = status;
				this.progress = progress;
				this.error = error;
			}
			App.get().run(this::notifyListeners);
		}

		@Override
		public void subscribe(Listener l) {
			App.get().run(() -> {
				synchronized (this) {
					if (!listeners.contains(l)) listeners.add(l);
				}
				notify(l);
			});
		}

		@Override
		public void unsubscribe(Listener l) {
			synchronized (this) {
				listeners.remove(l);
			}
		}

		private void notifyListeners() {
			List<Listener> list;
			synchronized (this) {
				list = new ArrayList<>(listeners);
			}
			for (Listener l : list) notify(l);
		}

		private void notify(Listener l) {
			Status s;
			int p;
			long b;
			Throwable err;

			synchronized (this) {
				if (!listeners.contains(l)) return;
				s = status;
				p = progress;
				b = bytes;
				err = error;
				if (s == Status.FAILED) error = null; // Delivered once
			}

			switch (s) {
				case DOWNLOADING:
					l.onLoadProgress(STAGE_DOWNLOAD, p, b);
					break;
				case CHECKING:
					l.onLoadProgress(STAGE_IMPORT, INDETERMINATE, 0);
					break;
				case IMPORTING:
					l.onLoadProgress(STAGE_IMPORT, p, 0);
					break;
				case DONE:
					l.onLoadDone();
					break;
				case FAILED:
					if (err != null) l.onLoadError(err);
					else l.onLoadDone();
					break;
			}
		}
	}
}
