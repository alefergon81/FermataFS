package me.aap.fermata.addon.tv.m3u;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static me.aap.fermata.addon.tv.m3u.TvM3uFile.CATCHUP_TYPE_APPEND;
import static me.aap.fermata.addon.tv.m3u.TvM3uFile.CATCHUP_TYPE_AUTO;
import static me.aap.fermata.addon.tv.m3u.TvM3uFile.CATCHUP_TYPE_DEFAULT;
import static me.aap.fermata.addon.tv.m3u.TvM3uFile.CATCHUP_TYPE_FLUSSONIC;
import static me.aap.fermata.addon.tv.m3u.TvM3uFile.CATCHUP_TYPE_SHIFT;
import static me.aap.fermata.util.Utils.dynCtx;
import static me.aap.utils.async.Completed.completed;
import static me.aap.utils.async.Completed.completedNull;

import android.content.Context;
import android.content.res.Resources;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import me.aap.fermata.BuildConfig;
import me.aap.fermata.addon.tv.R;
import me.aap.fermata.addon.tv.TvItem;
import me.aap.fermata.addon.tv.TvRootItem;
import me.aap.fermata.addon.tv.m3u.TvM3uDb.Entry;
import me.aap.fermata.addon.tv.m3u.TvM3uDb.Group;
import me.aap.fermata.media.lib.ContentKindClassifier;
import me.aap.fermata.media.lib.DefaultMediaLib;
import me.aap.fermata.media.lib.LoadProgress;
import me.aap.fermata.media.lib.M3uItem;
import me.aap.fermata.media.lib.MediaLib;
import me.aap.fermata.media.lib.MediaLib.BrowsableItem;
import me.aap.fermata.media.lib.MediaLib.Item;
import me.aap.utils.async.FutureRef;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.db.SQLite;
import me.aap.utils.log.Log;
import me.aap.utils.resource.Rid;
import me.aap.utils.text.SharedTextBuilder;
import me.aap.utils.vfs.VirtualFolder;
import me.aap.utils.vfs.VirtualResource;

/**
 * A TV source. Its playlist is imported into a per-source database (see {@link TvM3uImport}), and
 * its folders and channels are created from that database on demand.
 *
 * @author Andrey Pavlenko
 */
public class TvM3uItem extends M3uItem implements TvItem {
	public static final String SCHEME = "tvm3u";
	private static final int[] KIND_ORDER = {ContentKindClassifier.KIND_LIVE,
			ContentKindClassifier.KIND_MOVIE, ContentKindClassifier.KIND_SERIES};
	private final int srcId;
	private String tvgUrl;
	private SQLite sql;
	private volatile Map<String, String> meta;
	private volatile long generation = -1;
	private volatile boolean importFailed;
	private FutureSupplier<Uri> iconUri;
	private final FutureRef<Data> data = new FutureRef<Data>() {
		@Override
		protected FutureSupplier<Data> create() {
			return loadData();
		}
	};
	private final FutureRef<Map<String, String>> metaRef = new FutureRef<Map<String, String>>() {
		@Override
		protected FutureSupplier<Map<String, String>> create() {
			return loadMeta();
		}
	};
	private final FutureRef<XmlTv> xmlTv = new FutureRef<XmlTv>() {
		@Override
		protected FutureSupplier<XmlTv> create() {
			return getData().get()
					.then(d -> XmlTv.create(TvM3uItem.this))
					.ifFail(err -> {
						Log.e(err, "Failed to load XMLTV: ", getEpgUrl());
						return null;
					});
		}
	};

	protected TvM3uItem(String id, BrowsableItem parent, TvM3uFile m3uFile) {
		super(id, parent, m3uFile);
		srcId = Integer.parseInt(id.substring(SCHEME.length() + 1));
	}

	public static TvM3uItem create(TvRootItem root, TvM3uFile m3u, int srcId) {
		String id = SharedTextBuilder.get().append(SCHEME).append(':').append(srcId).releaseString();
		return create(root, m3u, id);
	}

	public static TvM3uItem create(TvRootItem root, TvM3uFile m3u, String id) {
		DefaultMediaLib lib = root.getLib();

		synchronized (lib.cacheLock()) {
			MediaLib.Item i = lib.getFromCache(id);

			if (i != null) {
				TvM3uItem c = (TvM3uItem) i;
				if (BuildConfig.D && !root.equals(c.getParent())) throw new AssertionError();
				if (BuildConfig.D && !m3u.equals(c.getResource())) throw new AssertionError();
				return c;
			} else {
				return new TvM3uItem(id, root, m3u);
			}
		}
	}

	public static FutureSupplier<TvM3uItem> create(TvRootItem root, int srcId, String m3uId) {
		DefaultMediaLib lib = root.getLib();
		String id = SharedTextBuilder.get().append(SCHEME).append(':').append(srcId).releaseString();

		synchronized (lib.cacheLock()) {
			MediaLib.Item i = lib.getFromCache(id);
			if (i != null) return completed((TvM3uItem) i);
		}

		TvM3uFileSystem fs = TvM3uFileSystem.getInstance();
		Rid rid = fs.toRid(m3uId);
		return fs.getResource(rid).map(m3u -> (m3u != null) ? create(root, m3u, id) : null);
	}

	@Override
	protected FutureRef<Data> getData() {
		return data;
	}

	@Override
	protected String getScheme() {
		return SCHEME;
	}

	public int getSourceId() {
		return srcId;
	}

	@Override
	protected String createSubtitle(int gr, int ch) {
		Context ctx = dynCtx(getLib().getContext());
		Resources res = ctx.getResources();
		if (ch != 0) {
			if (gr == 0) return res.getString(R.string.sub_ch, ch);
		} else if (gr != 0) {
			return res.getString(R.string.sub_gr, gr);
		}
		return res.getString(R.string.sub_ch_gr, ch, gr);
	}

	FutureSupplier<XmlTv> getXmlTv() {
		return xmlTv.get().main();
	}

	@Override
	public int getIcon() {
		return me.aap.fermata.R.drawable.tv;
	}

	@NonNull
	@Override
	public String getName() {
		Data d = data.peek();
		if ((d != null) && (d.name != null)) return d.name;
		Map<String, String> m = meta;
		String name = (m != null) ? m.get(TvM3uDb.M_NAME) : null;
		return (name != null) ? name : getResource().getName();
	}

	@NonNull
	@Override
	public FutureSupplier<Uri> getIconUri() {
		if (iconUri == null) {
			return metaRef.get().then(m -> {
				String cover = m.get(TvM3uDb.M_COVER);
				if (cover == null) {
					return iconUri = completedNull();
				} else if (cover.contains(":/")) {
					return iconUri = completed(Uri.parse(cover));
				} else {
					return getResource().getParent().then(folder -> {
						if (folder == null) return iconUri = completedNull();
						return getLib().getVfsManager().resolve(cover, folder).then(file ->
								iconUri = (file != null) ? completed(file.getRid().toAndroidUri()) : completedNull());
					});
				}
			});
		}

		return iconUri;
	}

	@Override
	protected FutureSupplier<String> buildSubtitle() {
		return metaRef.get().map(m -> m.isEmpty() ? "" :
				createSubtitle((int) TvM3uImporter.parseLong(m.get(TvM3uDb.M_GROUP_COUNT)),
						(int) TvM3uImporter.parseLong(m.get(TvM3uDb.M_UNGROUPED_COUNT))));
	}

	/**
	 * Progress of the TV playlist downloads of every source.
	 */
	public static LoadProgress getDownloadProgress() {
		return TvM3uImport.getDownloadProgress();
	}

	@Nullable
	@Override
	public LoadProgress getLoadProgress() {
		return getImport().getLoadProgress();
	}

	@Override
	public boolean isReleasable() {
		return importFailed;
	}

	/**
	 * After a failed import, leaving the source drops its fallback list, so that the next open
	 * retries the import.
	 */
	@Override
	public void releaseChildren() {
		if (!importFailed) return;
		importFailed = false;
		data.clear();
		clearChildren();
	}

	@Override
	public FutureSupplier<? extends TvM3uTrackItem> getTrack(int id) {
		return getTrack(-1, id, null);
	}

	/**
	 * The entry with the given URL (the first one with the same tid, otherwise the first one in
	 * playlist order), or the entry at (gid, tid).
	 */
	public FutureSupplier<TvM3uTrackItem> getTrack(int gid, int tid, @Nullable String uri) {
		return getData().get().then(d -> sql().query(db -> {
			Entry e = (uri != null) ? TvM3uDb.queryEntry(db, uri, tid) : null;
			if (e == null) e = TvM3uDb.queryEntry(db, gid, tid);
			if (e == null) return null;
			Group g = (e.gid >= 0) ? TvM3uDb.queryGroup(db, e.gid) : null;
			return trackItem(e, g);
		}));
	}

	/**
	 * The group with the given id or, if its name differs, the group with the given name.
	 */
	public FutureSupplier<TvM3uGroupItem> getGroup(int id, @Nullable String name) {
		return getData().get().then(d -> sql().query(db -> {
			Group g = TvM3uDb.queryGroup(db, id);

			if ((name != null) && ((g == null) || !name.equals(g.name))) {
				Group byName = TvM3uDb.queryGroup(db, name);
				if (byName != null) g = byName;
			}

			return (g == null) ? null : groupItem(g);
		}));
	}

	/**
	 * The kind folder of a mixed source, or null.
	 */
	public FutureSupplier<TvM3uKindItem> getKindItem(int kind) {
		return getData().get().map(d -> isMixed() && getKinds().contains(kind) ? kindItem(kind) : null);
	}

	@Override
	public TvM3uFile getResource() {
		return (TvM3uFile) super.getResource();
	}

	public String getEpgUrl() {
		String url = getResource().getEpgUrl();
		return ((url != null) && !url.isEmpty()) ? url : tvgUrl;
	}

	@Override
	protected void setTvgUrl(String url) {
		if (url == null) return;
		tvgUrl = url = url.trim();
		TvM3uFile f = getResource();
		if (f.getEpgUrl() == null) f.setEpgUrl(url);
	}

	/**
	 * Re-imports the playlist: used when the user refreshes or edits the source.
	 */
	@NonNull
	@Override
	public FutureSupplier<Void> refresh() {
		getImport().setForce();
		return revalidate();
	}

	/**
	 * Drops the loaded data, so that the next request checks the playlist again. The playlist is
	 * re-imported only if it changed.
	 */
	public FutureSupplier<Void> revalidate() {
		invalidate();
		importFailed = false;
		data.clear();
		metaRef.clear();
		clearChildren();
		return updateTitles();
	}

	/**
	 * Called when the source is removed: cancels the import and closes the read handle.
	 */
	public void onRemoved() {
		getImport().cancel();
		closeReadHandle();
	}

	boolean isMixed() {
		return getKinds().size() > 1;
	}

	/**
	 * The entries to match against the EPG, and the groups they belong to.
	 */
	FutureSupplier<Void> getEpgEntries(EntryConsumer consumer) {
		return getData().get().then(d -> sql().query(db -> {
			Map<Integer, Group> groups = new HashMap<>();
			for (Group g : TvM3uDb.queryGroups(db, -1)) groups.put(g.gid, g);
			for (Entry e : TvM3uDb.queryEpgEntries(db, isMixed())) consumer.accept(e, groups.get(e.gid));
			return null;
		}));
	}

	TvM3uTrackItem trackItem(Entry e, @Nullable Group g) {
		return trackItem(e, (g != null) ? groupItem(g) : ungroupedParent(e.kind));
	}

	private TvM3uTrackItem trackItem(Entry e, BrowsableItem parent) {
		String id = SharedTextBuilder.get().append(TvM3uTrackItem.SCHEME).append(':').append(e.gid)
				.append(':').append(e.tid).append(':').append(srcId).append(':').append(e.rid)
				.releaseString();
		DefaultMediaLib lib = getLib();
		Item i = lib.getCachedItem(id);
		if (i instanceof TvM3uTrackItem) return (TvM3uTrackItem) i;
		VirtualResource file = resolve(e.rid);

		if (file == null) {
			Log.w("Unable to resolve ", e.rid);
			return null;
		}

		return (TvM3uTrackItem) lib.getOrCreateCachedItem(id, iid ->
				new TvM3uTrackItem(iid, parent, this, e.tid, file, e.name, e.album, e.artist, e.genre,
						e.logo, e.tvgId, e.tvgName, e.duration, e.type, catchupType(e.catchup),
						catchupDays(e.catchupDays), e.catchupSource, e.kind));
	}

	FutureSupplier<List<Item>> listGroupEntries(TvM3uGroupItem g) {
		return getData().get().then(d -> sql().query(db -> {
			List<Entry> entries = TvM3uDb.queryEntries(db, g.getGroupId(), -1);
			List<Item> items = new ArrayList<>(entries.size());
			for (Entry e : entries) addTrack(items, trackItem(e, g));
			return items;
		}));
	}

	FutureSupplier<List<Item>> listKindChildren(TvM3uKindItem k) {
		int kind = k.getKind();
		return getData().get().then(d -> sql().query(db -> {
			List<Group> groups = TvM3uDb.queryGroups(db, kind);
			List<Entry> entries = TvM3uDb.queryEntries(db, -1, kind);
			List<Item> items = new ArrayList<>(groups.size() + entries.size());
			for (Group g : groups) items.add(groupItem(g, k));
			for (Entry e : entries) addTrack(items, trackItem(e, k));
			return items;
		}));
	}

	FutureSupplier<int[]> countKindChildren(int kind) {
		return getData().get().then(d -> sql().query(db -> new int[]{
				TvM3uDb.countGroups(db, kind), TvM3uDb.countUngrouped(db, kind)}));
	}

	private static void addTrack(List<Item> items, @Nullable TvM3uTrackItem t) {
		if (t != null) items.add(t);
	}

	private TvM3uGroupItem groupItem(Group g) {
		return groupItem(g, isMixed() ? kindItem(g.kind) : this);
	}

	private TvM3uGroupItem groupItem(Group g, BrowsableItem parent) {
		String id = SharedTextBuilder.get().append(TvM3uGroupItem.SCHEME).append(':').append(g.gid)
				.append(':').append(srcId).append(':').append(g.name).releaseString();
		return (TvM3uGroupItem) getLib().getOrCreateCachedItem(id, iid ->
				new TvM3uGroupItem(iid, parent, this, g.name, g.gid, g.count, g.kind));
	}

	private BrowsableItem ungroupedParent(int kind) {
		return isMixed() ? kindItem(kind) : this;
	}

	private TvM3uKindItem kindItem(int kind) {
		String id = TvM3uKindItem.id(kind, srcId);
		return (TvM3uKindItem) getLib().getOrCreateCachedItem(id, iid ->
				new TvM3uKindItem(iid, this, kind));
	}

	@Override
	public DefaultMediaLib getLib() {
		return (DefaultMediaLib) super.getLib();
	}

	private FutureSupplier<Data> loadData() {
		return getImport().ensure(getResource(), getLib().getVfsManager(), this::closeReadHandle)
				.fork()
				.then(r -> buildData(r.meta), err -> {
					// The error is reported through getLoadProgress(). Use the last good import.
					importFailed = true;
					return sql().query(TvM3uDb::readMeta).then(m -> {
						if (TvM3uImporter.parseLong(m.get(TvM3uDb.M_ENTRY_COUNT)) > 0) return buildData(m);
						return completed(emptyData());
					}, e -> completed(emptyData()));
				});
	}

	private FutureSupplier<Data> buildData(Map<String, String> m) {
		long gen = TvM3uImporter.parseLong(m.get(TvM3uDb.M_GENERATION));

		if (gen != generation) {
			if (generation != -1) xmlTv.clear();
			generation = gen;
			invalidate();
		}

		meta = m;
		metaRef.set(completed(m));
		String url = m.get(TvM3uDb.M_TVG_URL);
		if (url != null) setTvgUrl(url);
		String name = m.get(TvM3uDb.M_NAME);
		String title = (name != null) ? name : getResource().getName();
		String subtitle = createSubtitle((int) TvM3uImporter.parseLong(m.get(TvM3uDb.M_GROUP_COUNT)),
				(int) TvM3uImporter.parseLong(m.get(TvM3uDb.M_UNGROUPED_COUNT)));
		String cover = m.get(TvM3uDb.M_COVER);

		if (isMixed()) {
			List<Item> children = new ArrayList<>(KIND_ORDER.length);
			List<Integer> kinds = getKinds();
			for (int k : KIND_ORDER) {
				if (kinds.contains(k)) children.add(kindItem(k));
			}
			return completed(new Data(title, subtitle, children, emptyMap(), cover));
		}

		return sql().query(db -> {
			List<Group> groups = TvM3uDb.queryGroups(db, -1);
			List<Entry> entries = TvM3uDb.queryEntries(db, -1, -1);
			List<Item> children = new ArrayList<>(groups.size() + entries.size());
			for (Group g : groups) children.add(groupItem(g, this));
			for (Entry e : entries) addTrack(children, trackItem(e, (BrowsableItem) this));
			return new Data(title, subtitle, children, emptyMap(), cover);
		});
	}

	private Data emptyData() {
		return new Data(getResource().getName(), "", emptyList(), emptyMap(), null);
	}

	private FutureSupplier<Map<String, String>> loadMeta() {
		Map<String, String> m = meta;
		if (m != null) return completed(m);
		File f = getResource().getPlaylistDbFile();
		if (!f.isFile()) return completed(emptyMap());
		FutureSupplier<Map<String, String>> q = sql().query(db -> {
			Map<String, String> mm = TvM3uDb.readMeta(db);
			return TvM3uDb.isValid(db, mm) ? mm : Collections.<String, String>emptyMap();
		});
		return q.ifFail(err -> {
			Log.e(err, "Failed to read playlist database: ", f);
			return Collections.<String, String>emptyMap();
		}).onSuccess(mm -> {
			if (!mm.isEmpty() && (meta == null)) meta = mm;
		});
	}

	private List<Integer> getKinds() {
		Map<String, String> m = meta;
		String kinds = (m != null) ? m.get(TvM3uDb.M_KINDS_PRESENT) : null;
		if ((kinds == null) || kinds.isEmpty()) return emptyList();
		List<Integer> l = new ArrayList<>(3);
		for (String k : kinds.split(",")) l.add(Integer.parseInt(k));
		return l;
	}

	/**
	 * Removes the cached groups, kind folders, channels and EPG items of this source, so that they
	 * are created again from the current import. Items still referenced elsewhere keep working.
	 */
	private void invalidate() {
		getLib().removeFromCacheIf(id -> TvM3uIds.belongsTo(id, srcId));
		iconUri = null;
	}

	private VirtualResource resolve(String rid) {
		VirtualFolder dir = getResource().getParent().peek();
		return getLib().getVfsManager().resolve(rid, dir).get(null);
	}

	private TvM3uImport getImport() {
		return TvM3uImport.get(getResource().getPlaylistDbFile());
	}

	private synchronized SQLite sql() {
		if ((sql == null) || sql.isClosed()) sql = SQLite.get(getResource().getPlaylistDbFile(), true);
		return sql;
	}

	private synchronized void closeReadHandle() {
		if (sql != null) {
			sql.close();
			sql = null;
		}
	}

	private static int catchupType(@Nullable String catchup) {
		if (catchup == null) return CATCHUP_TYPE_AUTO;
		switch (catchup) {
			case "default":
				return CATCHUP_TYPE_DEFAULT;
			case "append":
				return CATCHUP_TYPE_APPEND;
			case "shift":
				return CATCHUP_TYPE_SHIFT;
			case "flussonic":
				return CATCHUP_TYPE_FLUSSONIC;
			default:
				return CATCHUP_TYPE_AUTO;
		}
	}

	private static int catchupDays(@Nullable String catchupDays) {
		if (catchupDays == null) return -1;
		try {
			return Integer.parseInt(catchupDays);
		} catch (NumberFormatException ex) {
			Log.e(ex, "Invalid catchup days: ", catchupDays);
			return -1;
		}
	}

	interface EntryConsumer {
		void accept(Entry e, @Nullable Group g);
	}
}
