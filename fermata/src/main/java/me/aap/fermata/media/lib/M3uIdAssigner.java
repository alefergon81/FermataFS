package me.aap.fermata.media.lib;

import androidx.annotation.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * Numbers playlist entries the way M3U item ids expect: only resolved entries get a number,
 * a group gets its id at its first resolved entry, and an entry's id is its position within
 * its group, or among the ungrouped entries.
 */
public final class M3uIdAssigner {
	private final Map<String, int[]> groups = new HashMap<>();
	private int ungrouped;
	private int seq = -1;
	private int gid;
	private int tid;
	private boolean newGroup;

	/**
	 * @param group    the entry's group name, or null if the entry is ungrouped
	 * @param resolved whether the entry's location was resolved
	 * @return false if the entry is skipped, in which case it consumes no number
	 */
	public boolean assign(@Nullable String group, boolean resolved) {
		if (!resolved) return false;
		seq++;

		if (group == null) {
			gid = -1;
			tid = ungrouped++;
			newGroup = false;
		} else {
			int[] g = groups.get(group);
			if (newGroup = (g == null)) groups.put(group, g = new int[]{groups.size(), 0});
			gid = g[0];
			tid = g[1]++;
		}

		return true;
	}

	/**
	 * The group id of the last assigned entry, or -1 if it is ungrouped.
	 */
	public int getGroupId() {
		return gid;
	}

	/**
	 * The track id of the last assigned entry.
	 */
	public int getTrackId() {
		return tid;
	}

	/**
	 * The position of the last assigned entry among all resolved entries.
	 */
	public int getSeq() {
		return seq;
	}

	/**
	 * Whether the last assigned entry was the first resolved entry of its group.
	 */
	public boolean isNewGroup() {
		return newGroup;
	}

	public int getGroupCount() {
		return groups.size();
	}

	public int getUngroupedCount() {
		return ungrouped;
	}

	public int getEntryCount() {
		return seq + 1;
	}
}
