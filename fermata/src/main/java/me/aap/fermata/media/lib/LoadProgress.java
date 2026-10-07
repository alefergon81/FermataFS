package me.aap.fermata.media.lib;

/**
 * Observable progress of a long-running load, such as a playlist import.
 * Listeners are called on the main thread, with the current state on subscribe.
 */
public interface LoadProgress {
	/**
	 * The progress value while the total is not known yet.
	 */
	int INDETERMINATE = -1;
	/**
	 * The progress value at completion.
	 */
	int MAX = 1000;
	/**
	 * The playlist is being downloaded.
	 */
	int STAGE_DOWNLOAD = 1;
	/**
	 * The playlist is being checked or imported.
	 */
	int STAGE_IMPORT = 2;

	void subscribe(Listener l);

	void unsubscribe(Listener l);

	interface Listener {

		/**
		 * @param stage    {@link #STAGE_DOWNLOAD} or {@link #STAGE_IMPORT}
		 * @param progress 0..{@link #MAX}, or {@link #INDETERMINATE}
		 * @param bytes    the number of bytes downloaded, for a download of unknown length
		 */
		void onLoadProgress(int stage, int progress, long bytes);

		void onLoadDone();

		/**
		 * Called once per error: an error delivered to one listener is not delivered again.
		 */
		void onLoadError(Throwable err);
	}
}
