package me.aap.fermata.media.lib;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static me.aap.utils.async.Completed.completed;
import static me.aap.utils.async.Completed.completedNull;

import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import java.util.zip.InflaterInputStream;

import me.aap.fermata.BuildConfig;
import me.aap.fermata.R;
import me.aap.fermata.media.lib.MediaLib.BrowsableItem;
import me.aap.fermata.media.lib.MediaLib.Item;
import me.aap.fermata.vfs.m3u.M3uFile;
import me.aap.fermata.vfs.m3u.M3uFileSystem;
import me.aap.utils.app.App;
import me.aap.utils.async.FutureRef;
import me.aap.utils.async.FutureSupplier;
import me.aap.utils.collection.CollectionUtils;
import me.aap.utils.log.Log;
import me.aap.utils.text.SharedTextBuilder;
import me.aap.utils.vfs.VfsManager;
import me.aap.utils.vfs.VirtualFile;
import me.aap.utils.vfs.VirtualFolder;
import me.aap.utils.vfs.VirtualResource;

/**
 * @author Andrey Pavlenko
 */
public class M3uItem extends BrowsableItemBase {
	public static final String SCHEME = "m3u";
	private FutureSupplier<Uri> iconUri;
	private final FutureRef<Data> data = new FutureRef<Data>() {
		@Override
		protected FutureSupplier<Data> create() {
			return App.get().execute(M3uItem.this::parse)
					.ifFail(err -> {
						Log.e(err, "Failed to load M3U: ", getResource().getName());
						return new Data(getResource().getName(), "", emptyList(), emptyMap(), null);
					});
		}
	};

	protected M3uItem(String id, BrowsableItem parent, VirtualFile m3uFile) {
		super(id, parent, m3uFile);
	}

	private Data parse() {
		String id = getId();
		VirtualFile m3uFile = (VirtualFile) getResource();
		VirtualFolder dir = m3uFile.getParent().peek();
		Map<String, M3uGroupItem> groups = new LinkedHashMap<>();
		List<M3uTrackItem> tracks = new ArrayList<>();
		Map<String, List<M3uTrackItem>> uriToTrack = new HashMap<>();
		String idPath = id.substring(getScheme().length());
		VfsManager vfs = getLib().getVfsManager();
		M3uParser parser = new M3uParser(isVideo(m3uFile) ? M3uTrackItem.TYPE_VIDEO : M3uTrackItem.TYPE_UNKNOWN);
		M3uIdAssigner ids = new M3uIdAssigner();

		try {
			parser.parse(createReader(m3uFile), new M3uParser.Handler() {
				@Override
				public void onEntry(M3uParser.Entry e) {
					VirtualResource file = vfs.resolve(e.location, dir).get(null);
					if (!ids.assign(e.group, file != null)) return;
					M3uTrackItem track;

					if (e.group == null) {
						track = createTrack(M3uItem.this, -1, ids.getTrackId(), idPath, file, e.name, e.album,
								e.artist, e.genre, e.logo, e.tvgId, e.tvgName, e.duration, e.type, e.catchup,
								e.catchupDays, e.catchupSource);
						tracks.add(track);
					} else {
						M3uGroupItem g = groups.get(e.group);

						if (g == null) {
							g = createGroup(idPath, e.group, ids.getGroupId());
							groups.put(e.group, g);
						}

						track = createTrack(g, g.getGroupId(), ids.getTrackId(), idPath, file, e.name,
								e.album, e.artist, e.genre, e.logo, e.tvgId, e.tvgName, e.duration, e.type,
								e.catchup, e.catchupDays, e.catchupSource);
						g.tracks.add(track);
					}

					CollectionUtils.compute(uriToTrack, track.getResource().getRid().toString(), (u, t) -> {
						if (t == null) return Collections.singletonList(track);
						List<M3uTrackItem> values = new ArrayList<>(t.size() + 1);
						values.addAll(t);
						values.add(track);
						return values;
					});
				}

				@Override
				public void onTvgUrl(String url) {
					setTvgUrl(url);
				}
			});
		} catch (Exception ex) {
			Log.e(ex, "Failed to parse m3u file: ", m3uFile);
		}

		int ngroups = groups.size();
		int ntracks = tracks.size();
		List<Item> children = new ArrayList<>(ngroups + ntracks);

		if (ngroups > 0) {
			for (M3uGroupItem g : groups.values()) {
				g.init();
				children.add(g);
			}
		}

		if (ntracks > 0) {
			children.addAll(tracks);
		}

		String m3uName = parser.getName();
		String title = (m3uName == null) ? m3uFile.getName() : m3uName;
		String subtitle = createSubtitle(ngroups, ntracks);
		return new Data(title, subtitle, children, uriToTrack, parser.getCover());
	}

	@NonNull
	static M3uItem create(String id, BrowsableItem parent, VirtualFile m3uFile,
												DefaultMediaLib lib) {
		synchronized (lib.cacheLock()) {
			Item i = lib.getFromCache(id);

			if (i != null) {
				M3uItem c = (M3uItem) i;
				if (BuildConfig.D && !parent.equals(c.getParent())) throw new AssertionError();
				if (BuildConfig.D && !m3uFile.equals(c.getResource())) throw new AssertionError();
				return c;
			} else {
				return new M3uItem(id, parent, m3uFile);
			}
		}
	}

	static FutureSupplier<Item> create(DefaultMediaLib lib, String id) {
		assert id.startsWith(SCHEME);

		synchronized (lib.cacheLock()) {
			Item i = lib.getFromCache(id);
			if (i != null) return completed(i);
		}

		M3uFileSystem fs = M3uFileSystem.getInstance();
		return fs.getResource(fs.toRid(id.substring(SCHEME.length() + 1)))
				.ifFail(err -> {
					Log.e(err, "Failed to load resource");
					return null;
				}).main().then(r -> {
					if (r != null) return completed(create(id, lib.getFolders(), r, lib));

					SharedTextBuilder tb = SharedTextBuilder.get();
					tb.append(FileItem.SCHEME).append(id, SCHEME.length(), id.length());

					return lib.getItem(tb.releaseString()).map(i -> {
						if (!(i instanceof FileItem)) {
							Log.w("Resource not found: ", id);
							return null;
						}

						FileItem file = (FileItem) i;
						BrowsableItem parent = file.getParent();
						return create(id, parent, (VirtualFile) file.getResource(), lib);
					});
				});
	}

	static boolean isM3uFile(String name) {
		return name.endsWith(".m3u");
	}

	protected FutureRef<Data> getData() {
		return data;
	}

	protected String getScheme() {
		return SCHEME;
	}

	protected M3uGroupItem createGroup(String idPath, String name, int groupId) {
		SharedTextBuilder tb = SharedTextBuilder.get();
		tb.append(M3uGroupItem.SCHEME).append(':').append(groupId).append(idPath).append(':').append(name);
		return new M3uGroupItem(tb.releaseString(), this, name, groupId);
	}

	protected M3uTrackItem createTrack(BrowsableItem parent, int groupNumber, int trackNumber,
																		 String idPath, VirtualResource file, String name, String album,
																		 String artist, String genre, String logo, String tvgId,
																		 String tvgName, long duration, byte type,
																		 String catchup, String catchupDays, String catchupSource) {
		SharedTextBuilder tb = SharedTextBuilder.get();
		tb.append(M3uTrackItem.SCHEME).append(':').append(groupNumber).append(':')
				.append(trackNumber).append(idPath).append(':').append(file.getRid());
		return new M3uTrackItem(tb.releaseString(), parent, trackNumber, file, name, album, artist,
				genre, logo, tvgId, tvgName, duration, type);
	}

	protected String createSubtitle(int ngroups, int ntracks) {
		return getLib().getContext().getResources().getString(R.string.folder_subtitle,
				ntracks, ngroups);
	}

	@NonNull
	@Override
	public String getName() {
		Data d = getData().get().peek();
		return (d == null) || (d.name == null) ? super.getName() : d.name;
	}

	@NonNull
	@Override
	public FutureSupplier<Uri> getIconUri() {
		if (iconUri == null) {
			return getData().get().then(d -> {
				if (d.cover == null) {
					return iconUri = completedNull();
				} else if (d.cover.contains(":/")) {
					return iconUri = completed(Uri.parse(d.cover));
				} else {
					return getResource().getParent().then(folder -> {
						if (folder == null) return iconUri = completedNull();
						return getLib().getVfsManager().resolve(d.cover, folder).then(file ->
								iconUri = (file != null) ? completed(file.getRid().toAndroidUri()) : completedNull());
					});
				}
			});
		}

		return iconUri;
	}

	public FutureSupplier<? extends M3uTrackItem> getTrack(int id) {
		return getData().get().map(d -> {
			for (Item c : d.items) {
				if (c instanceof M3uTrackItem) {
					M3uTrackItem t = (M3uTrackItem) c;
					if (t.getTrackNumber() == id) return t;
				}
			}
			return null;
		});
	}

	public FutureSupplier<? extends M3uTrackItem> getTrack(int gid, int tid, @Nullable String uri) {
		if (uri != null) {
			return getData().get().then(d -> {
				List<M3uTrackItem> tracks = d.uriToTrack.get(uri);
				if (tracks == null) return getTrack(gid, tid, null).cast();
				for (M3uTrackItem t : tracks) {
					if (t.getTrackNumber() == tid) return completed(t);
				}
				return completed(tracks.get(0));
			});
		}
		if (gid == -1) return getTrack(tid);
		return getGroup(gid, null).map(g -> (g != null) ? g.getTrack(tid) : null);
	}

	public FutureSupplier<? extends M3uGroupItem> getGroup(int id, @Nullable String name) {
		return getData().get().map(d -> {
			M3uGroupItem group = null;

			for (Item c : d.items) {
				if (c instanceof M3uGroupItem) {
					M3uGroupItem g = (M3uGroupItem) c;
					if (g.getGroupId() == id) {
						if ((name == null) || name.equals(g.getName())) return g;
						group = g;
						break;
					}
				}
			}

			if (name == null) return null;

			for (Item c : d.items) {
				if (c instanceof M3uGroupItem) {
					M3uGroupItem g = (M3uGroupItem) c;
					if (name.equals(g.getName())) return g;
				}
			}

			return group;
		});
	}

	@Override
	public int getIcon() {
		return R.drawable.m3u;
	}

	@Override
	public FutureSupplier<List<Item>> listChildren() {
		return getData().get().map(d -> d.items);
	}

	@Override
	protected FutureSupplier<String> buildSubtitle() {
		return getData().get().map(d -> d.subtitle);
	}

	@NonNull
	@Override
	public FutureSupplier<Void> refresh() {
		FutureRef<Data> ref = getData();
		if (ref.peek() == null) return super.refresh();

		return ref.get().main().then(d -> {
			ref.clear();
			DefaultMediaLib lib = (DefaultMediaLib) getLib();

			synchronized (lib.cacheLock()) {
				lib.removeFromCache(this);

				for (Item i : d.items) {
					if (i instanceof M3uGroupItem) {
						for (M3uTrackItem t : ((M3uGroupItem) i).tracks) {
							lib.removeFromCache(t);
						}
					}

					lib.removeFromCache(i);
				}
			}

			return super.refresh();
		});
	}

	protected Reader createReader(VirtualFile f) throws IOException {
		InputStream in = f.getInputStream().asInputStream();
		String enc = null;
		String cs = null;

		if (f instanceof M3uFile) {
			M3uFile m3u = (M3uFile) f;
			enc = m3u.getContentEncoding();
			cs = m3u.getCharacterEncoding();
		}

		if (enc != null) {
			if ("gzip".equals(enc)) {
				in = new GZIPInputStream(in);
			} else if ("deflate".equals(enc)) {
				in = new InflaterInputStream(in);
			} else {
				throw new IOException("Unsupported content encoding: " + enc);
			}
		}

		return new InputStreamReader(in, (cs == null) ? "UTF-8" : cs);
	}

	protected void setTvgUrl(String url) {
	}

	private boolean isVideo(VirtualFile f) {
		return (f instanceof M3uFile) && ((M3uFile) f).isVideo();
	}

	protected static final class Data {
		public final String name;
		public final String subtitle;
		public final List<Item> items;
		public final Map<String, List<M3uTrackItem>> uriToTrack;
		public final String cover;

		public Data(String name, String subtitle, List<Item> items, Map<String, List<M3uTrackItem>> uriToTrack, String cover) {
			this.name = name;
			this.subtitle = subtitle;
			this.items = items;
			this.uriToTrack = uriToTrack;
			this.cover = cover;
		}
	}
}
