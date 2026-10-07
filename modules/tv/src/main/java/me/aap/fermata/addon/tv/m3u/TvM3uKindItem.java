package me.aap.fermata.addon.tv.m3u;

import static me.aap.fermata.util.Utils.dynCtx;
import static me.aap.utils.async.Completed.completedNull;

import android.content.Context;
import android.net.Uri;

import androidx.annotation.NonNull;

import java.util.List;

import me.aap.fermata.addon.tv.R;
import me.aap.fermata.addon.tv.TvItem;
import me.aap.fermata.addon.tv.TvRootItem;
import me.aap.fermata.media.lib.BrowsableItemBase;
import me.aap.fermata.media.lib.ContentKindClassifier;
import me.aap.fermata.media.lib.MediaLib.Item;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.text.SharedTextBuilder;

/**
 * A "TV", "Movies" or "Series" folder of a source that has more than one content kind. It holds
 * the provider groups and the ungrouped entries of its kind.
 */
public class TvM3uKindItem extends BrowsableItemBase implements TvItem {
	public static final String SCHEME = "tvm3uk";
	private final int kind;

	TvM3uKindItem(String id, TvM3uItem parent, int kind) {
		super(id, parent, parent.getResource());
		this.kind = kind;
	}

	static String id(int kind, int srcId) {
		return SharedTextBuilder.get().append(SCHEME).append(':').append(kind).append(':')
				.append(srcId).releaseString();
	}

	public static FutureSupplier<TvM3uKindItem> create(TvRootItem root, String id) {
		assert id.startsWith(SCHEME);
		int kstart = SCHEME.length() + 1;
		int kend = id.indexOf(':', kstart);
		if (kend < 0) return completedNull();
		int kind = Integer.parseInt(id.substring(kstart, kend));
		String srcId = TvM3uItem.SCHEME + id.substring(kend);
		FutureSupplier<? extends Item> f = root.getItem(TvM3uItem.SCHEME, srcId);
		return (f == null) ? completedNull() : f.then(i -> {
			TvM3uItem m3u = (TvM3uItem) i;
			return (m3u != null) ? m3u.getKindItem(kind) : completedNull();
		});
	}

	public int getKind() {
		return kind;
	}

	@NonNull
	@Override
	public TvM3uItem getParent() {
		return (TvM3uItem) super.getParent();
	}

	@NonNull
	@Override
	public String getName() {
		Context ctx = dynCtx(getLib().getContext());
		switch (kind) {
			case ContentKindClassifier.KIND_MOVIE:
				return ctx.getString(R.string.tv_kind_movies);
			case ContentKindClassifier.KIND_SERIES:
				return ctx.getString(R.string.tv_kind_series);
			default:
				return ctx.getString(R.string.tv_kind_live);
		}
	}

	@NonNull
	@Override
	public FutureSupplier<Uri> getIconUri() {
		return getParent().getIconUri();
	}

	@Override
	public int getIcon() {
		return me.aap.fermata.R.drawable.tv;
	}

	@Override
	protected FutureSupplier<List<Item>> listChildren() {
		return getParent().listKindChildren(this);
	}

	@Override
	protected FutureSupplier<String> buildSubtitle() {
		TvM3uItem p = getParent();
		return p.countKindChildren(kind).map(c -> p.createSubtitle(c[0], c[1]));
	}

	@Override
	public boolean isReleasable() {
		return true;
	}

	@Override
	public void releaseChildren() {
		clearChildren();
	}
}
