package me.aap.fermata.addon.web.yt;

import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static android.view.ViewGroup.LayoutParams.WRAP_CONTENT;
import static me.aap.fermata.util.Utils.dynCtx;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.SurfaceView;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.res.ResourcesCompat;

import com.google.android.play.core.splitcompat.SplitCompat;

import me.aap.fermata.addon.web.R;
import me.aap.fermata.addon.web.yt.YoutubePlayerStatus.Status;
import me.aap.fermata.ui.view.VideoInfoView;
import me.aap.fermata.ui.view.VideoView;

/**
 * @author Andrey Pavlenko
 */
public class YoutubeVideoView extends VideoView {
	// Assigned in init(), which runs from the super constructor: no field initializers.
	private FrameLayout statusView;
	private LinearLayout adView;
	private ImageView adIcon;
	private TextView adText;
	private ProgressBar progress;

	public YoutubeVideoView(Context context, AttributeSet attrs) {
		super(context, attrs);
	}

	@Override
	protected void init(Context context) {
		addView(new FrameLayout(context), new FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT));
		addInfoView(context);
		addStatusView(context);
	}

	@NonNull
	@Override
	public VideoInfoView getVideoInfoView() {
		return (VideoInfoView) getChildAt(1);
	}

	@Nullable
	@Override
	public SurfaceView getSubtitleSurface() {
		return null;
	}

	void setStatus(Status status) {
		switch (status) {
			case SKIPPING_AD -> {
				adView.setVisibility(VISIBLE);
				progress.setVisibility(GONE);
				statusView.setVisibility(VISIBLE);
			}
			case LOADING -> {
				adView.setVisibility(GONE);
				progress.setVisibility(VISIBLE);
				statusView.setVisibility(VISIBLE);
			}
			default -> statusView.setVisibility(GONE);
		}
	}

	@Override
	protected void onSizeChanged(int w, int h, int oldw, int oldh) {
		super.onSizeChanged(w, h, oldw, oldh);
		int icon = Math.max(1, Math.min(w, h) / 5);
		int pad = icon / 4;
		setSize(adIcon, icon);
		setSize(progress, icon);
		adText.setTextSize(TypedValue.COMPLEX_UNIT_PX, icon / 4f);
		adText.setPadding(0, pad / 2, 0, 0);
		adView.setPadding(pad, pad, pad, pad);
		((GradientDrawable) adView.getBackground()).setCornerRadius(pad);
	}

	private static void setSize(View v, int size) {
		LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) v.getLayoutParams();
		lp.width = lp.height = size;
		v.setLayoutParams(lp);
	}

	/** The ad skip indicator and the loading spinner, centered over the video. */
	private void addStatusView(Context context) {
		Context ctx = dynCtx(context);
		SplitCompat.install(ctx);

		GradientDrawable scrim = new GradientDrawable();
		scrim.setColor(0x99000000);
		adIcon = new ImageView(ctx);
		adIcon.setImageDrawable(ResourcesCompat.getDrawable(ctx.getResources(), R.drawable.ic_ad_blocked,
				ctx.getTheme()));
		adIcon.setLayoutParams(new LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT));
		adText = new TextView(ctx);
		adText.setText(ctx.getResources().getString(R.string.skipping_ad));
		adText.setTextColor(Color.WHITE);
		adText.setGravity(Gravity.CENTER);
		adView = new LinearLayout(ctx);
		adView.setOrientation(LinearLayout.VERTICAL);
		adView.setGravity(Gravity.CENTER_HORIZONTAL);
		adView.setBackground(scrim);
		adView.addView(adIcon);
		adView.addView(adText, new LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT));

		progress = new ProgressBar(ctx);
		progress.setIndeterminate(true);
		progress.setLayoutParams(new LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT));
		LinearLayout box = new LinearLayout(ctx);
		box.setOrientation(LinearLayout.VERTICAL);
		box.setGravity(Gravity.CENTER);
		box.addView(adView, new LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT));
		box.addView(progress);

		statusView = new FrameLayout(ctx);
		statusView.setClickable(false);
		statusView.setFocusable(false);
		statusView.setVisibility(GONE);
		statusView.addView(box, new FrameLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT, Gravity.CENTER));
		addView(statusView, new FrameLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT, Gravity.CENTER));
	}
}
