package me.aap.fermata.media.lib;

import org.junit.Assert;
import org.junit.Test;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class M3uIdAssignerTest extends Assert {
	private static final String FIXTURE = "#EXTM3U\n" +
			"#EXTINF:-1,Bad first\nunresolved://a\n" +
			"#EXTINF:-1 group-title=\"A\",A1\nhttp://h/a1\n" +
			"#EXTINF:-1,U1\nhttp://h/u1\n" +
			"#EXTINF:-1 group-title=\"B\",Bad B\nunresolved://b\n" +
			"#EXTINF:-1 group-title=\"C\",C1\nhttp://h/c1\n" +
			"#EXTINF:-1 group-title=\"A\",A2\nhttp://h/a2\n" +
			"#EXTINF:-1 group-title=\"B\",B1\nhttp://h/b1\n" +
			"#EXTINF:-1,Bad U\nunresolved://u\n" +
			"#EXTINF:-1,U2\nhttp://h/u2\n" +
			"#EXTINF:-1 group-title=\"A\",Bad A\nunresolved://a3\n" +
			"#EXTINF:-1 group-title=\"C\",C2\nhttp://h/c2\n" +
			"#EXTINF:-1 group-title=\"A\",A3\nhttp://h/a1\n" +
			"#EXTINF:-1,U3\nhttp://h/u1\n";

	@Test
	public void testSameIdsAsOldParse() throws Exception {
		List<String> expected = oldParse();
		List<String> actual = new ArrayList<>();
		M3uIdAssigner ids = new M3uIdAssigner();
		new M3uParser(M3uParser.TYPE_UNKNOWN).parse(new StringReader(FIXTURE), e -> {
			if (!ids.assign(e.group, resolved(e.location))) return;
			actual.add(ids.getGroupId() + ":" + ids.getTrackId() + ":" + e.location);
		});

		assertEquals(List.of(
				"0:0:http://h/a1", "-1:0:http://h/u1", "1:0:http://h/c1", "0:1:http://h/a2",
				"2:0:http://h/b1", "-1:1:http://h/u2", "1:1:http://h/c2", "0:2:http://h/a1",
				"-1:2:http://h/u1"), expected);
		assertEquals(expected, actual);
		assertEquals(3, ids.getGroupCount());
		assertEquals(3, ids.getUngroupedCount());
		assertEquals(9, ids.getEntryCount());
		assertEquals(8, ids.getSeq());
	}

	@Test
	public void testNewGroupAndSeq() {
		M3uIdAssigner ids = new M3uIdAssigner();
		assertFalse(ids.assign("X", false));
		assertTrue(ids.assign("X", true));
		assertTrue(ids.isNewGroup());
		assertEquals(0, ids.getSeq());
		assertTrue(ids.assign(null, true));
		assertFalse(ids.isNewGroup());
		assertTrue(ids.assign("X", true));
		assertFalse(ids.isNewGroup());
		assertEquals(1, ids.getTrackId());
		assertEquals(2, ids.getSeq());
	}

	private static boolean resolved(String location) {
		return !location.startsWith("unresolved:");
	}

	/**
	 * The numbering of M3uItem.parse() before M3uIdAssigner, used as the reference.
	 */
	private static List<String> oldParse() throws Exception {
		Map<String, List<String>> groups = new LinkedHashMap<>();
		Map<String, Integer> groupIds = new LinkedHashMap<>();
		List<String> tracks = new ArrayList<>();
		List<String> ids = new ArrayList<>();
		new M3uParser(M3uParser.TYPE_UNKNOWN).parse(new StringReader(FIXTURE), e -> {
			if (!resolved(e.location)) return;

			if (e.group == null) {
				ids.add(-1 + ":" + tracks.size() + ":" + e.location);
				tracks.add(e.location);
			} else {
				List<String> g = groups.get(e.group);

				if (g == null) {
					groupIds.put(e.group, groups.size());
					groups.put(e.group, g = new ArrayList<>());
				}

				ids.add(groupIds.get(e.group) + ":" + g.size() + ":" + e.location);
				g.add(e.location);
			}
		});
		return ids;
	}
}
