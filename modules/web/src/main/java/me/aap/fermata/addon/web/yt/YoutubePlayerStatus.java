package me.aap.fermata.addon.web.yt;

/**
 * What the fullscreen YouTube view shows over the video: nothing, a loading spinner or the ad skip
 * indicator. Pure Java, so it can be unit tested. The flags follow the page events.
 */
final class YoutubePlayerStatus {
	enum Status {NONE, LOADING, SKIPPING_AD}

	private boolean skipAd;
	private boolean ad;
	// The ad has ended, but the requested video has not started playing yet.
	private boolean awaitingContent;
	private boolean waiting;
	private boolean paused;

	Status get() {
		if (paused) return Status.NONE;
		if (skipAd && (ad || awaitingContent)) return Status.SKIPPING_AD;
		if (waiting) return Status.LOADING;
		return Status.NONE;
	}

	void setSkipAd(boolean skipAd) {
		this.skipAd = skipAd;
	}

	void adState(boolean ad) {
		if (this.ad && !ad) awaitingContent = true;
		this.ad = ad;
	}

	void adPlaying() {
		paused = false;
	}

	void playing() {
		awaitingContent = false;
		waiting = false;
		paused = false;
	}

	void paused() {
		waiting = false;
		paused = true;
	}

	void ended() {
		waiting = false;
	}

	void waiting() {
		waiting = true;
	}

	void progress() {
		waiting = false;
	}
}
