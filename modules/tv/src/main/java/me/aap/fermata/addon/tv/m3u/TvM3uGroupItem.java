package me.aap.fermata.addon.tv.m3u;

import static me.aap.fermata.util.Utils.dynCtx;
import static me.aap.utils.async.Completed.completed;
import static me.aap.utils.async.Completed.completedNull;

import android.content.Context;

import java.util.List;

import me.aap.fermata.addon.tv.R;
import me.aap.fermata.addon.tv.TvItem;
import me.aap.fermata.addon.tv.TvRootItem;
import me.aap.fermata.media.lib.M3uGroupItem;
import me.aap.fermata.media.lib.MediaLib.BrowsableItem;
import me.aap.fermata.media.lib.MediaLib.Item;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.text.SharedTextBuilder;

/**
 * A provider group of a TV source. Its channels are loaded from the source's database when the
 * group is opened, and released when it is left.
 *
 * @author Andrey Pavlenko
 */
public class TvM3uGroupItem extends M3uGroupItem implements TvItem {
	public static final String SCHEME = "tvm3ug";
	private final int count;
	private final int kind;

	TvM3uGroupItem(String id, BrowsableItem parent, TvM3uItem owner, String name, int groupId,
								 int count, int kind) {
		super(id, parent, owner, name, groupId);
		this.count = count;
		this.kind = kind;
	}

	public static FutureSupplier<TvM3uGroupItem> create(TvRootItem root, String id) {
		assert id.startsWith(SCHEME);
		int gstart = id.indexOf(':') + 1;
		int gend = id.indexOf(':', gstart);
		int gid = Integer.parseInt(id.substring(gstart, gend));
		int nstart = id.indexOf(':', gend + 1);
		SharedTextBuilder tb = SharedTextBuilder.get().append(TvM3uItem.SCHEME);
		String name;

		if (nstart > 0) {
			name = id.substring(nstart + 1);
			tb.append(id, gend, nstart);
		} else {
			name = null;
			tb.append(id, gend, id.length());
		}

		FutureSupplier<? extends Item> f = root.getItem(TvM3uItem.SCHEME, tb.releaseString());
		return (f == null) ? completedNull() : f.then(i -> {
			TvM3uItem m3u = (TvM3uItem) i;
			return (m3u != null) ? m3u.getGroup(gid, name) : completedNull();
		});
	}

	@Override
	public TvM3uItem getM3uItem() {
		return (TvM3uItem) super.getM3uItem();
	}

	public int getKind() {
		return kind;
	}

	@Override
	public int getIcon() {
		return me.aap.fermata.R.drawable.tv;
	}

	@Override
	protected FutureSupplier<List<Item>> listChildren() {
		return getM3uItem().listGroupEntries(this);
	}

	@Override
	protected FutureSupplier<String> buildSubtitle() {
		Context ctx = dynCtx(getLib().getContext());
		String t = ctx.getResources().getString(R.string.sub_ch, count);
		return completed(t);
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
