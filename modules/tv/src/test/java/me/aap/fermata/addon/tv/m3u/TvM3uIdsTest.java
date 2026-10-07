package me.aap.fermata.addon.tv.m3u;

import static me.aap.fermata.addon.tv.m3u.TvM3uIds.belongsTo;

import org.junit.Assert;
import org.junit.Test;

public class TvM3uIdsTest extends Assert {

	@Test
	public void testBelongsTo() {
		String[] ids1 = {
				"tvm3ug:0:1:News",
				"tvm3ug:3:1:Group: with colons",
				"tvm3ut:0:5:1:http://h:8080/live/1.ts",
				"tvm3ut:-1:5:1:http://h/12.ts",
				"tvm3uk:2:1",
				"tvm3ue:0:5:1:http://h/1.ts/1700000000000-1700003600000",
		};
		String[] ids12 = {
				"tvm3ug:0:12:News",
				"tvm3ut:1:1:12:http://h/1.ts",
				"tvm3ut:-1:0:12:http://h/1",
				"tvm3uk:0:12",
				"tvm3ue:1:1:12:http://h/1.ts/1-2",
		};

		for (String id : ids1) {
			assertTrue(id, belongsTo(id, 1));
			assertFalse(id, belongsTo(id, 12));
		}

		for (String id : ids12) {
			assertTrue(id, belongsTo(id, 12));
			assertFalse(id, belongsTo(id, 1));
			assertFalse(id, belongsTo(id, 2));
		}
	}

	@Test
	public void testOtherIds() {
		assertFalse(belongsTo("tvm3u:1", 1));
		assertFalse(belongsTo("m3ut:0:0:1:http://h/1", 1));
		assertFalse(belongsTo("m3ug:0:1:G", 1));
		assertFalse(belongsTo("tvm3ug:0", 1));
		assertFalse(belongsTo("tvm3ut:0:1", 1));
		assertFalse(belongsTo("tvm3uk:1:", 1));
	}
}
