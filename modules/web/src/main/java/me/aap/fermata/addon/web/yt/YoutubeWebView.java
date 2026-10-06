package me.aap.fermata.addon.web.yt;

import static me.aap.fermata.addon.web.yt.YoutubeJsInterface.JS_CAR_LAYOUT;
import static me.aap.fermata.addon.web.yt.YoutubeJsInterface.JS_CAR_NAVIGATE;
import static me.aap.fermata.addon.web.yt.YoutubeJsInterface.JS_ERR;
import static me.aap.fermata.addon.web.yt.YoutubeJsInterface.JS_EVENT;
import static me.aap.fermata.addon.web.yt.YoutubeJsInterface.JS_VIDEO_ENDED;
import static me.aap.fermata.addon.web.yt.YoutubeJsInterface.JS_VIDEO_FOUND;
import static me.aap.fermata.addon.web.yt.YoutubeJsInterface.JS_VIDEO_PAUSED;
import static me.aap.fermata.addon.web.yt.YoutubeJsInterface.JS_VIDEO_PLAYING;
import static me.aap.fermata.addon.web.yt.YoutubeJsInterface.JS_VIDEO_QUALITIES;

import android.content.Context;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.webkit.CookieManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;

import me.aap.fermata.BuildConfig;
import me.aap.fermata.addon.web.FermataChromeClient;
import me.aap.fermata.addon.web.FermataJsInterface;
import me.aap.fermata.addon.web.FermataWebView;
import me.aap.fermata.media.service.MediaSessionCallback;
import me.aap.fermata.ui.activity.MainActivityDelegate;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.async.Promise;
import me.aap.utils.log.Log;
import me.aap.utils.pref.PreferenceStore;

/**
 * @author Andrey Pavlenko
 */
public class YoutubeWebView extends FermataWebView {
	private static final String CLEAR_HIGHEST_VIDEO_QUALITY_JS =
			"function clearFermataQ() {\n" +
					"  if (!window.__fermataQ) return;\n" +
					"  if (window.__fermataQ.timeout) clearTimeout(window.__fermataQ.timeout);\n" +
					"  if (window.__fermataQ.player && window.__fermataQ.handler) {\n" +
					"    try { window.__fermataQ.player.removeEventListener('onStateChange', window.__fermataQ.handler); } catch(e) {}\n" +
					"  }\n" +
					"  window.__fermataQ = null;\n" +
					"}\n";
	// Reports the watch page layout for car drags (CarDragTarget). Rects are view pixels.
	private static final String CAR_LAYOUT_JS = """
			(function() {
			  if (window.__fermataCar) return;
			  window.__fermataCar = true;
			  var timer;
			  function rect(e) {
			    var b = e.getBoundingClientRect(), d = devicePixelRatio;
			    return [b.left * d, b.top * d, b.right * d, b.bottom * d];
			  }
			  function report() {
			    var v = document.querySelector('video'), p = v ? rect(v) : null, t = null, best = 0;
			    if (p) document.querySelectorAll('*').forEach(function(e) {
			      if ((e.scrollHeight <= e.clientHeight) || e.contains(v)) return;
			      var area = e.clientWidth * e.clientHeight;
			      if (area <= best) return;
			      if (!/(auto|scroll)/.test(getComputedStyle(e).overflowY)) return;
			      var r = rect(e);
			      if ((r[0] < p[2]) && (r[2] > p[0])) return;
			      best = area;
			      t = r;
			    });
			    var s = window.visualViewport ? visualViewport.scale : 1;
			    %EVENT%(%LAYOUT%, JSON.stringify({w: location.pathname === '/watch', s: s, p: p, t: t}));
			  }
			  function later(ms) {
			    clearTimeout(timer);
			    timer = setTimeout(report, ms);
			  }
			  window.addEventListener('state-navigatestart', function() { %EVENT%(%NAVIGATE%, null); }, true);
			  window.addEventListener('state-navigateend', function() { later(0); }, true);
			  window.addEventListener('resize', function() { later(150); }, true);
			  document.addEventListener('scroll', function() { later(150); }, true);
			  document.addEventListener('touchend', function() { later(1000); }, true);
			  if (window.visualViewport) visualViewport.addEventListener('resize', function() { later(150); });
			  later(0);
			})()"""
			.replace("%EVENT%", JS_EVENT)
			.replace("%LAYOUT%", String.valueOf(JS_CAR_LAYOUT))
			.replace("%NAVIGATE%", String.valueOf(JS_CAR_NAVIGATE));
	private YoutubeJsInterface js;
	// Car drags: when the page URL last changed, and the layout the page last reported.
	private long urlChangeTime = -1;
	@Nullable
	private CarDragTarget.Snapshot carLayout;

	public YoutubeWebView(Context context) {
		super(context);
	}

	public YoutubeWebView(Context context, AttributeSet attrs) {
		super(context, attrs);
	}

	public YoutubeWebView(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
	}

	@Override
	protected FermataJsInterface createJsInterface() {
		MainActivityDelegate a = MainActivityDelegate.get(getContext());
		return js = new YoutubeJsInterface(this, new YoutubeMediaEngine(this, a));
	}

	@Override
	public YoutubeAddon getAddon() {
		return (YoutubeAddon) super.getAddon();
	}

	@Override
	public void onPreferenceChanged(PreferenceStore store, List<PreferenceStore.Pref<?>> prefs) {
		super.onPreferenceChanged(store, prefs);

		if (getAddon().autoHighestQualityChanged(prefs)) {
			if (getAddon().autoHighestQuality()) setHighestVideoQuality();
			else clearHighestVideoQuality();
		}

		if (YoutubeSponsorBlock.isPreferenceChanged(prefs)) injectSponsorBlock();
	}

	@Override
	public void loadUrl(@NonNull String url) {
		Log.d("Loading URL: " + url);
		super.loadUrl(url);
	}

	@Override
	public void goBack() {
		MediaSessionCallback cb = MainActivityDelegate.get(getContext()).getMediaSessionCallback();
		if ((cb.getEngine() instanceof YoutubeMediaEngine e) && e.isOwnedBy(this)) cb.onStop();
		super.goBack();
	}

	@Override
	protected void pageLoaded(String uri) {
		attachListeners();
		injectSponsorBlock();
		addFocusHighlight();
		if (isCar()) evaluateJavascript(CAR_LAYOUT_JS, null);
		CookieManager.getInstance().flush();
	}

	/** A new page: an earlier tap or layout belongs to the previous one. */
	void urlChanged(String source) {
		urlChangeTime = SystemClock.uptimeMillis();
		carLayout = null;
		Log.d("Car drag: URL changed (", source, ')');
	}

	void setCarLayout(String data) {
		try {
			JSONObject o = new JSONObject(data);
			carLayout = new CarDragTarget.Snapshot(o.optBoolean("w"), (float) o.optDouble("s", 1),
					rect(o.optJSONArray("t")));
			Log.d("Car drag: layout ", data);
		} catch (JSONException ex) {
			Log.e(ex, "Invalid car layout: ", data);
			carLayout = null;
		}
	}

	@Nullable
	private static float[] rect(@Nullable JSONArray a) throws JSONException {
		if ((a == null) || (a.length() != 4)) return null;
		float[] r = new float[4];
		for (int i = 0; i < 4; i++) r[i] = (float) a.getDouble(i);
		return r;
	}

	/** See MainActivityFragment.getCarDragOrigin; x/y are car display pixels. */
	@Nullable
	float[] getCarDragOrigin(float x, float y, long originTime) {
		if (!isCar()) return null;
		int[] loc = new int[2];
		getLocationInWindow(loc);
		float[] visible = {0, 0, getWidth(), getHeight()};
		FermataChromeClient chrome = getWebChromeClient();
		boolean fullScreen = (chrome != null) && chrome.isFullScreen();
		boolean pageOrigin = (originTime >= 0) && (originTime > urlChangeTime);
		float[] p = CarDragTarget.resolve(pageOrigin, carLayout, visible, fullScreen);
		if (p == null) return null;
		p[0] += loc[0];
		p[1] += loc[1];
		Log.d("Car drag: redirected from ", x, ',', y, " to ", p[0], ',', p[1]);
		return p;
	}

	protected void submitForm() {
		if (!me.aap.fermata.BuildConfig.AUTO) return;
		loadUrl("javascript:\n" +
				"var e = new KeyboardEvent('keydown',\n" +
				"{ code: 'Enter', key: 'Enter', keyCode: 13, view: window, bubbles: true });\n" +
				"document.activeElement.dispatchEvent(e);\n" +
				"e = new KeyboardEvent('keyup',\n" +
				"{ code: 'Enter', key: 'Enter', keyCode: 13, view: window, bubbles: true });\n" +
				"document.activeElement.dispatchEvent(e);");
	}

	private void attachListeners() {
		String debug = BuildConfig.D ? JS_EVENT + "(" + JS_VIDEO_FOUND + ", null);\n" : "";
		String scale = getAddon().getScale().prefName();
		loadUrl("javascript:\n" +
				"function attachVideoListeners(v) {\n" +
				"  if (v.getAttribute('FermataAttached') === 'true') return;\n" +
				"  v.setAttribute('FermataAttached', 'true');\n" +
				"  v.setAttribute('style', 'object-fit:" + scale + "');\n" + debug +
				"  if ((v.currentTime > 0) && !v.paused && !v.ended) " + JS_EVENT + "(" + JS_VIDEO_PLAYING +
				", v.currentSrc);\n" +
				"  v.addEventListener('playing', function(e) {" + JS_EVENT + "(" + JS_VIDEO_PLAYING +
				", v.currentSrc);});\n" +
				"  v.addEventListener('pause', function(e) {" + JS_EVENT + "(" + JS_VIDEO_PAUSED +
				", v.currentSrc);});\n" +
				"  v.addEventListener('ended', function(e) {" + JS_EVENT + "(" + JS_VIDEO_ENDED +
				", null);});\n" +
				"}\n" +
				"function findVideo() {\n" +
				"  var video = document.querySelectorAll('video');" +
				"  video.forEach(attachVideoListeners);\n" +
				"   setTimeout(findVideo, 1000);\n" +
				"}\n" +
				"findVideo();");
	}

	private void injectSponsorBlock() {
		String script = YoutubeSponsorBlock.getScript(getContext(), getAddon().getPreferenceStore());
		if (!script.isEmpty()) evaluateJavascript(script, result -> configureSponsorBlock());
		else configureSponsorBlock();
	}

	private void configureSponsorBlock() {
		evaluateJavascript("if (window.FermataSponsorBlock) window.FermataSponsorBlock.configure(" +
				YoutubeSponsorBlock.getConfigJson(getAddon().getPreferenceStore()) + ");", null);
	}

	protected boolean requestFullScreen() {
		loadUrl("javascript: var v = document.querySelector('video');\n" +
				"if ('webkitRequestFullscreen' in v) v.webkitRequestFullscreen();\n" +
				"else if ('requestFullscreen' in v) v.requestFullscreen();\n" +
				"else " + JS_EVENT + "(" + JS_ERR + ", 'Method requestFullscreen not found in ' + v);");
		return true;
	}

	void play() {
		loadUrl("javascript:var v = document.querySelector('video'); if (v != null) v.play();");
	}

	void pause() {
		loadUrl("javascript:var v = document.querySelector('video'); if (v != null) v.pause();");
	}

	void stop() {
		loadUrl("javascript:var v = document.querySelector('video');\n" +
				"if (v != null) { v.currentTime = 0; v.pause(); }");
	}

	void prev() {
		prevNext(false);
	}

	void next() {
		prevNext(true);
	}

	private void prevNext(boolean next) {
		FermataChromeClient chrome = getWebChromeClient();
		if (chrome == null) return;
		chrome.exitFullScreen().thenRun(() -> evaluateJavascript("""
				function prevNextVideo() {
				  const buttons = document.querySelectorAll('button.player-middle-controls-prev-next-button');
				  console.log('Prev/Next buttons:', buttons);
				  if (buttons) buttons[%d].click();
				}
				setTimeout(prevNextVideo, 600);
				""".formatted(next ? 1 : 0), null));
	}

	FutureSupplier<Long> getDuration() {
		return getMilliseconds("duration");
	}

	FutureSupplier<Long> getPosition() {
		return getMilliseconds("currentTime");
	}

	FutureSupplier<String> getVideoQualities() {
		Promise<String> p = js.getResultPromise();
		loadUrl("javascript:\n" +
				"function retryGetVideoQualities(attempt, openMenu) {\n" +
				"  if (attempt < 10) setTimeout(getVideoQualities, 100, attempt + 1, openMenu);\n" +
				"  else " + JS_EVENT + '(' + JS_VIDEO_QUALITIES + ", null);\n" +
				"  return null;\n" +
				"}\n" +
				"function getVideoQualities(attempt, openMenu) {\n" +
				"  if (openMenu) {\n" +
				"    var b = document.querySelector('.player-settings-icon');\n" +
				"    if (b == null) return retryGetVideoQualities(attempt, true);\n" +
				"    b.click();\n" +
				"  }\n" +
				"  var settings = document.querySelector('.player-quality-settings');\n" +
				"  if (settings == null) return retryGetVideoQualities(attempt, false);\n" +
				"  var select = settings.querySelector('.select');\n" +
				"  if (select == null) return retryGetVideoQualities(attempt, false);\n" +
				"  var options = select.querySelectorAll('.option');\n" +
				"  var result = '';\n" +
				"  for (let i = 0; i < options.length; i++) {\n" +
				"    if (i != 0) result += ';';\n" +
				"    if (i == select.selectedIndex) result += '*';\n" +
				"    result += options[i].innerText;\n" +
				"  }\n" +
				"  " + JS_EVENT + '(' + JS_VIDEO_QUALITIES + ", result);\n" +
				"  setTimeout(()=> {settings.parentNode.parentNode.querySelector('" +
				".c3-material-button-button').click();}, 100);\n" +
				"  return result;\n" +
				"}\n" +
				"getVideoQualities(0, true);");
		return p;
	}

	void setVideoQuality(int idx) {
		loadUrl("javascript:\n" +
				"function retrySetVideoQuality(idx, attempt, openMenu) {\n" +
				"  if (attempt < 10) setTimeout(setVideoQuality, 100, idx, attempt + 1, openMenu);\n" +
				"  return false;\n" +
				"}\n" +
				"function setVideoQuality(idx, attempt, openMenu) {\n" +
				"  if (openMenu) {\n" +
				"    var b = document.querySelector('.player-settings-icon');\n" +
				"    if (b == null) return retrySetVideoQuality(idx, attempt, true);\n" +
				"    b.click();\n" +
				"  }\n" +
				"  var settings = document.querySelector('.player-quality-settings');\n" +
				"  if (settings == null) return retrySetVideoQuality(idx, attempt, false);\n" +
				"  var select = settings.querySelector('.select');\n" +
				"  if (select == null) return retrySetVideoQuality(idx, attempt, false);\n" +
				"  var options = select.querySelectorAll('.option');\n" +
				"  var evt = document.createEvent(\"HTMLEvents\");\n" +
				"  evt.initEvent(\"change\", true, true);\n" +
				"  select.selectedIndex = idx;\n" +
				"  options[idx].selected = true;\n" +
				"  select.dispatchEvent(evt);\n" +
				"  setTimeout(()=> {settings.parentNode.parentNode.querySelector('" +
				".c3-material-button-button').click();}, 100);\n" +
				"  return true;\n" +
				"}\n" +
				"setVideoQuality(" + idx + ", 0, true);");
	}

	void setHighestVideoQuality() {
		loadUrl("javascript:\n" +
				"(function() {\n" +
				CLEAR_HIGHEST_VIDEO_QUALITY_JS +
				"  clearFermataQ();\n" +
				"  var state = window.__fermataQ = { player: null, handler: null, timeout: null, attempts: 0 };\n" +
				"  function getPlayer() {\n" +
				"    return document.querySelector('#movie_player') || document.querySelector('.html5-video-player');\n" +
				"  }\n" +
				"  function applyHighest(p) {\n" +
				"    if (!p || typeof p.getAvailableQualityLevels !== 'function') return false;\n" +
				"    var levels = p.getAvailableQualityLevels();\n" +
				"    if (!levels || levels.length === 0) return false;\n" +
				"    var best = null;\n" +
				"    for (var i = 0; i < levels.length; i++) {\n" +
				"      if (levels[i] !== 'auto') { best = levels[i]; break; }\n" +
				"    }\n" +
				"    if (!best) return false;\n" +
				"    if (p.getPlaybackQuality && p.getPlaybackQuality() === best) return true;\n" +
				"    if (typeof p.setPlaybackQualityRange === 'function') p.setPlaybackQualityRange(best, best);\n" +
				"    else if (typeof p.setPlaybackQuality === 'function') p.setPlaybackQuality(best);\n" +
				"    else return false;\n" +
				"    return true;\n" +
				"  }\n" +
				"  function install() {\n" +
				"    var p = getPlayer();\n" +
				"    if (!p || typeof p.addEventListener !== 'function') {\n" +
				"      if (++state.attempts < 50) state.timeout = setTimeout(install, 200);\n" +
				"      return;\n" +
				"    }\n" +
				"    state.player = p;\n" +
				"    state.handler = function(s) {\n" +
				"      if ((s === 1) || (s === 3)) applyHighest(getPlayer() || p);\n" +
				"    };\n" +
				"    p.addEventListener('onStateChange', state.handler);\n" +
				"    applyHighest(p);\n" +
				"  }\n" +
				"  install();\n" +
				"})();");
	}

	void clearHighestVideoQuality() {
		loadUrl("javascript:\n" +
				"(function() {\n" +
				CLEAR_HIGHEST_VIDEO_QUALITY_JS +
				"  clearFermataQ();\n" +
				"})();");
	}

	private FutureSupplier<Long> getMilliseconds(String value) {
		Promise<Long> p = new Promise<>();
		evaluateJavascript(
				"(function(){var v = document.querySelector('video'); return (v != null) ? v." + value +
						" : 0})();",
				v -> {
					try {
						p.complete((long) (Double.parseDouble(v) * 1000));
					} catch (NumberFormatException ex) {
						Log.d(ex);
						p.complete(0L);
					}
				});
		return p;
	}

	void setPosition(long position) {
		double pos = position / 1000f;
		loadUrl("javascript:var v = document.querySelector('video'); if (v != null) v.currentTime = " +
				pos + ";");
	}

	FutureSupplier<Float> getSpeed() {
		Promise<Float> p = new Promise<>();
		evaluateJavascript(
				"(function(){var v = document.querySelector('video'); return (v != null) ? v" +
						".playbackRate" +
						" " +
						": 0})();",
				v -> {
					try {
						p.complete(Float.parseFloat(v));
					} catch (NumberFormatException ex) {
						Log.d(ex);
						p.complete(1f);
					}
				});
		return p;
	}

	void setSpeed(float speed) {
		loadUrl("javascript:var v = document.querySelector('video'); if (v != null) v.playbackRate =" +
				" " +
				speed + ";");
	}

	FutureSupplier<String> getVideoTitle() {
		Promise<String> p = new Promise<>();
		evaluateJavascript("document.title", p::complete);
		return p;
	}

	void setScale(YoutubeAddon.VideoScale scale) {
		getAddon().setScale(scale);
		String p = scale.prefName();
		loadUrl("javascript:" +
				"document.querySelectorAll('video')" +
				".forEach(v=> v.setAttribute('style', 'object-fit:" + p + "'));");
	}
}
