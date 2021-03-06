package net.osmand.plus.activities;

import java.awt.Cursor;
import java.io.File;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.apache.commons.logging.Log;

import net.osmand.PlatformUtil;
import net.osmand.data.LatLon;
import net.osmand.plus.GPXUtilities;
import net.osmand.plus.GPXUtilities.GPXFile;
import net.osmand.plus.GPXUtilities.GPXTrackAnalysis;
import net.osmand.plus.GPXUtilities.Track;
import net.osmand.plus.GPXUtilities.TrkSegment;
import net.osmand.plus.GPXUtilities.WptPt;
import net.osmand.plus.GpxSelectionHelper.SelectedGpxFile;
import net.osmand.plus.OsmAndLocationProvider;
import net.osmand.plus.OsmandSettings;
import net.osmand.plus.api.SQLiteAPI.SQLiteConnection;
import net.osmand.plus.api.SQLiteAPI.SQLiteCursor;
import net.sourceforge.offroad.OsmWindow;
import net.sourceforge.offroad.data.SQLiteOpenHelper;

public class SavingTrackHelper extends SQLiteOpenHelper {
	
	public final static String DATABASE_NAME = "tracks"; //$NON-NLS-1$
	public final static int DATABASE_VERSION = 5;
	
	public final static String TRACK_NAME = "track"; //$NON-NLS-1$
	public final static String TRACK_COL_DATE = "date"; //$NON-NLS-1$
	public final static String TRACK_COL_LAT = "lat"; //$NON-NLS-1$
	public final static String TRACK_COL_LON = "lon"; //$NON-NLS-1$
	public final static String TRACK_COL_ALTITUDE = "altitude"; //$NON-NLS-1$
	public final static String TRACK_COL_SPEED = "speed"; //$NON-NLS-1$
	public final static String TRACK_COL_HDOP = "hdop"; //$NON-NLS-1$
	
	public final static String POINT_NAME = "point"; //$NON-NLS-1$
	public final static String POINT_COL_DATE = "date"; //$NON-NLS-1$
	public final static String POINT_COL_LAT = "lat"; //$NON-NLS-1$
	public final static String POINT_COL_LON = "lon"; //$NON-NLS-1$
	public final static String POINT_COL_NAME = "pname"; //$NON-NLS-1$
	public final static String POINT_COL_CATEGORY = "category"; //$NON-NLS-1$
	public final static String POINT_COL_DESCRIPTION = "description"; //$NON-NLS-1$
	public final static String POINT_COL_COLOR = "color"; //$NON-NLS-1$

	public final static Log log = PlatformUtil.getLog(SavingTrackHelper.class);

	private String updateScript;
	private String insertPointsScript;

	private long lastTimeUpdated = 0;
	private final OsmWindow ctx;

	private LatLon lastPoint;
	private float distance = 0;
	private SelectedGpxFile currentTrack;
	private int points;
	
	public SavingTrackHelper(OsmWindow ctx){
		super(ctx, DATABASE_NAME, null, DATABASE_VERSION);
		this.ctx = ctx;
		this.currentTrack = new SelectedGpxFile();
		this.currentTrack.setShowCurrentTrack(true);
		GPXFile gx = new GPXFile();
		gx.showCurrentTrack = true;
		this.currentTrack.setGpxFile(gx);
		prepareCurrentTrackForRecording();

		updateScript = "INSERT INTO " + TRACK_NAME + " (" + TRACK_COL_LAT + ", " + TRACK_COL_LON + ", "
				+ TRACK_COL_ALTITUDE + ", " + TRACK_COL_SPEED + ", " + TRACK_COL_HDOP + ", " + TRACK_COL_DATE + ")"
				+ " VALUES (?, ?, ?, ?, ?, ?)"; //$NON-NLS-1$ //$NON-NLS-2$

		insertPointsScript = "INSERT INTO " + POINT_NAME + " VALUES (?, ?, ?, ?, ?, ?, ?)"; //$NON-NLS-1$ //$NON-NLS-2$
	}

	@Override
	public void onCreate(SQLiteConnection db) {
		createTableForTrack(db);
		createTableForPoints(db);
	}
	
	private void createTableForTrack(SQLiteConnection db){
		db.execSQL("CREATE TABLE " + TRACK_NAME + " (" + TRACK_COL_LAT + " double, " + TRACK_COL_LON + " double, " //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
				+ TRACK_COL_ALTITUDE + " double, " + TRACK_COL_SPEED + " double, "  //$NON-NLS-1$ //$NON-NLS-2$
				+ TRACK_COL_HDOP + " double, " + TRACK_COL_DATE + " long )"); //$NON-NLS-1$ //$NON-NLS-2$
	}
	
	private void createTableForPoints(SQLiteConnection db){
		try {
			db.execSQL("CREATE TABLE " + POINT_NAME + " (" + POINT_COL_LAT + " double, " + POINT_COL_LON + " double, " //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
					+ POINT_COL_DATE + " long, " + POINT_COL_DESCRIPTION + " text, " + POINT_COL_NAME + " text, "
					+ POINT_COL_CATEGORY + " text, " + POINT_COL_COLOR + " long" + ")"); //$NON-NLS-1$ //$NON-NLS-2$
		} catch (RuntimeException e) {
			// ignore if already exists
		}
	}

	@Override
	public void onUpgrade(SQLiteConnection db, int oldVersion, int newVersion) {
		if(oldVersion < 2){
			createTableForPoints(db);
		}
		if(oldVersion < 3){
			db.execSQL("ALTER TABLE " + TRACK_NAME + " ADD " + TRACK_COL_HDOP + " double");
		}
		if(oldVersion < 4){
			db.execSQL("ALTER TABLE " + POINT_NAME +  " ADD " + POINT_COL_NAME + " text");
			db.execSQL("ALTER TABLE " + POINT_NAME +  " ADD " + POINT_COL_CATEGORY + " text");
		}
		if(oldVersion < 5){
			db.execSQL("ALTER TABLE " + POINT_NAME +  " ADD " + POINT_COL_COLOR + " long");
		}
	}
	
	
	public long getLastTrackPointTime() {
		long res = 0;
		try {
			SQLiteConnection db = getWritableDatabase();
			if (db != null) {
				try {
					SQLiteCursor query = db.rawQuery("SELECT " + TRACK_COL_DATE + " FROM " + TRACK_NAME + " ORDER BY " + TRACK_COL_DATE + " DESC", null); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
					if(query.moveToFirst()) {
						res = query.getLong(0);
					}
					query.close();
				} finally {
					db.close();
				}
			}
		} catch(RuntimeException e) {
		}
		return res;
	}
		
	public synchronized boolean hasDataToSave() {
		try {
			SQLiteConnection db = getWritableDatabase();
			if (db != null) {
				try {
					SQLiteCursor q = db.rawQuery("select * from " + TRACK_NAME, new String[0]);
					boolean has = q.moveToFirst();
					q.close();
					if (has) {
						return true;
					}
					q = db.rawQuery("select " + POINT_COL_LAT + ", " + POINT_COL_LON + " from " +  POINT_NAME, null);
					has = q.moveToFirst();
					while(has) {
						if(q.getDouble(0) != 0 || q.getDouble(1) != 0) {
							break;
						}
						if(!q.moveToNext()) {
							has = false;
							break;
						}
					}
					q.close();
					if (has) {
						return true;
					}
				} finally {
					db.close();
				}
			}
		} catch(RuntimeException e) {
			return false;
		}

		return false;
	}
	
	/**
	 * @return warnings
	 */
	public synchronized List<String> saveDataToGpx(File dir ) {
		List<String> warnings = new ArrayList<String>();
		dir.mkdirs();
		if (dir.getParentFile().canWrite()) {
			if (dir.exists()) {
				Map<String, GPXFile> data = collectRecordedData();

				// save file
				for (final String f : data.keySet()) {
					File fout = new File(dir, f + ".gpx"); //$NON-NLS-1$
					if (!data.get(f).isEmpty()) {
						WptPt pt = data.get(f).findPointToShow();
						String fileName = f + "_" + new SimpleDateFormat("HH-mm_EEE", Locale.US).format(new Date(pt.time)); //$NON-NLS-1$
						fout = new File(dir, fileName + ".gpx"); //$NON-NLS-1$
						int ind = 1;
						while (fout.exists()) {
							fout = new File(dir, fileName + "_" + (++ind) + ".gpx"); //$NON-NLS-1$ //$NON-NLS-2$
						}
					}

					String warn = GPXUtilities.writeGpxFile(fout, data.get(f), ctx);
					if (warn != null) {
						warnings.add(warn);
						return warnings;
					}
				}
			}
		}

		SQLiteConnection db = getWritableDatabase();
		if (db != null && warnings.isEmpty() && db.isOpen()) {
			try {
				// remove all from db
				db.execSQL("DELETE FROM " + TRACK_NAME + " WHERE " + TRACK_COL_DATE + " <= ?", new Object[] { System.currentTimeMillis() }); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
				db.execSQL("DELETE FROM " + POINT_NAME + " WHERE " + POINT_COL_DATE + " <= ?", new Object[] { System.currentTimeMillis() }); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
				// delete all
				//			db.execSQL("DELETE FROM " + TRACK_NAME + " WHERE 1 = 1", new Object[] { }); //$NON-NLS-1$ //$NON-NLS-2$
				//			db.execSQL("DELETE FROM " + POINT_NAME + " WHERE 1 = 1", new Object[] { }); //$NON-NLS-1$ //$NON-NLS-2$
			} finally {
				db.close();
			}
		}
		distance = 0;
		points = 0;
		currentTrack.getModifiableGpxFile().points.clear();
		currentTrack.getModifiableGpxFile().tracks.clear();
		currentTrack.getModifiablePointsToDisplay().clear();
		currentTrack.getModifiableGpxFile().modifiedTime = System.currentTimeMillis();
		prepareCurrentTrackForRecording();
		return warnings;
	}

	public Map<String, GPXFile> collectRecordedData() {
		Map<String, GPXFile> data = new LinkedHashMap<String, GPXFile>();
		SQLiteConnection db = getReadableDatabase();
		if (db != null && db.isOpen()) {
			try {
				collectDBPoints(db, data);
				collectDBTracks(db, data);
			} finally {
				db.close();
			}
		}
		return data;
	}

	private void collectDBPoints(SQLiteConnection db, Map<String, GPXFile> dataTracks) {
		SQLiteCursor query = db.rawQuery("SELECT " + POINT_COL_LAT + "," + POINT_COL_LON + "," + POINT_COL_DATE + "," //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
				+ POINT_COL_DESCRIPTION + "," + POINT_COL_NAME + "," + POINT_COL_CATEGORY + "," + POINT_COL_COLOR + " FROM " + POINT_NAME+" ORDER BY " + POINT_COL_DATE +" ASC", null); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		if (query.moveToFirst()) {
			do {
				WptPt pt = new WptPt();
				pt.lat = query.getDouble(0);
				pt.lon = query.getDouble(1);
				long time = query.getLong(2);
				pt.time = time;
				pt.desc = query.getString(3);
				pt.name = query.getString(4);
				pt.category = query.getString(5);
				int color = query.getInt(6);
				if (color != 0) {
					pt.setColor(color);
				}

				// check if name is extension (needed for audio/video plugin & josm integration)
				if(pt.name != null && pt.name.length() > 4 && pt.name.charAt(pt.name.length() - 4) == '.') {
					pt.link = pt.name;
				}
				
				String date = DateFormatformat("yyyy-MM-dd", time).toString(); //$NON-NLS-1$
				GPXFile gpx;
				if (dataTracks.containsKey(date)) {
					gpx = dataTracks.get(date);
				} else {
					gpx  = new GPXFile();
					dataTracks.put(date, gpx);
				}
				gpx.points.add(pt);

			} while (query.moveToNext());
		}
		query.close();
	}
	
	private String DateFormatformat(String pFormat, long pTime) {
		return new SimpleDateFormat(pFormat).format(new Date(pTime));
	}

	private void collectDBTracks(SQLiteConnection db, Map<String, GPXFile> dataTracks) {
		SQLiteCursor query = db.rawQuery("SELECT " + TRACK_COL_LAT + "," + TRACK_COL_LON + "," + TRACK_COL_ALTITUDE + "," //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
				+ TRACK_COL_SPEED + "," + TRACK_COL_HDOP + "," + TRACK_COL_DATE + " FROM " + TRACK_NAME +" ORDER BY " + TRACK_COL_DATE +" ASC", null); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
		long previousTime = 0;
		long previousInterval = 0;
		TrkSegment segment = null;
		Track track = null;
		if (query.moveToFirst()) {
			do {
				WptPt pt = new WptPt();
				pt.lat = query.getDouble(0);
				pt.lon = query.getDouble(1);
				pt.ele = query.getDouble(2);
				pt.speed = query.getDouble(3);
				pt.hdop = query.getDouble(4);
				long time = query.getLong(5);
				pt.time = time;
				long currentInterval = Math.abs(time - previousTime);
				boolean newInterval = pt.lat == 0 && pt.lon == 0;
				
				if (track != null && !newInterval && (currentInterval < 6 * 60 * 1000 || currentInterval < 10 * previousInterval)) {
					// 6 minute - same segment
					segment.points.add(pt);
				} else if (track != null && currentInterval < 2 * 60 * 60 * 1000) {
					// 2 hour - same track
					segment = new TrkSegment();
					if(!newInterval) {
						segment.points.add(pt);
					}
					track.segments.add(segment);
				} else {
					// check if date the same - new track otherwise new file  
					track = new Track();
					segment = new TrkSegment();
					track.segments.add(segment);
					if(!newInterval) {
						segment.points.add(pt);
					}
					String date = DateFormatformat("yyyy-MM-dd", time).toString(); //$NON-NLS-1$
					if (dataTracks.containsKey(date)) {
						GPXFile gpx = dataTracks.get(date);
						gpx.tracks.add(track);
					} else {
						GPXFile file = new GPXFile();
						file.tracks.add(track);
						dataTracks.put(date, file);
					}
				}
				previousInterval = currentInterval;
				previousTime = time;
			} while (query.moveToNext());
		}
		query.close();
	}
	
	public void startNewSegment() {
		lastTimeUpdated = 0;
		lastPoint = null;
		execWithClose(updateScript, new Object[] { 0, 0, 0, 0, 0, System.currentTimeMillis()});
		addTrackPoint(null, true, System.currentTimeMillis());
	}
	
	public void updateLocation(net.osmand.Location location) {
		// use because there is a bug on some devices with location.getTime()
		long locationTime = System.currentTimeMillis();
		OsmandSettings settings = ctx.getSettings();
		boolean record = false;
		if(OsmAndLocationProvider.isPointAccurateForRouting(location) && 
				OsmAndLocationProvider.isNotSimulatedLocation(location) ) {
//			if (OsmandPlugin.getEnabledPlugin(OsmandMonitoringPlugin.class) != null) {
//				if (settings.SAVE_TRACK_TO_GPX.get()
//						&& locationTime - lastTimeUpdated > settings.SAVE_TRACK_INTERVAL.get()
//						&& ctx.getRoutingHelper().isFollowingMode()) {
//					record = true;
//				} else if (settings.SAVE_GLOBAL_TRACK_TO_GPX.get()
//						&& locationTime - lastTimeUpdated > settings.SAVE_GLOBAL_TRACK_INTERVAL.get()) {
//					record = true;
//				}
//			}
		}
		if (record) {
			insertData(location.getLatitude(), location.getLongitude(), location.getAltitude(), location.getSpeed(),
					location.getAccuracy(), locationTime, settings);
//			ctx.getNotificationHelper().showNotification();
		}
	}
	
	public void insertData(double lat, double lon, double alt, double speed, double hdop, long time,
			OsmandSettings settings) {
		// * 1000 in next line seems to be wrong with new IntervalChooseDialog
		// if (time - lastTimeUpdated > settings.SAVE_TRACK_INTERVAL.get() * 1000) {
		execWithClose(updateScript, new Object[] { lat, lon, alt, speed, hdop, time });
		boolean newSegment = false;
		if (lastPoint == null || (time - lastTimeUpdated) > 180 * 1000) {
			lastPoint = new LatLon(lat, lon);
			newSegment = true;
		} else {
			float[] lastInterval = new float[1];
			net.osmand.Location.distanceBetween(lat, lon, lastPoint.getLatitude(), lastPoint.getLongitude(),
					lastInterval);
			distance += lastInterval[0];
			lastPoint = new LatLon(lat, lon);
		}
		lastTimeUpdated = time;
		WptPt pt = new GPXUtilities.WptPt(lat, lon, time, alt, speed, hdop);
		addTrackPoint(pt, newSegment, time);
	}
	
	private void addTrackPoint(WptPt pt, boolean newSegment, long time) {
		List<TrkSegment> points = currentTrack.getModifiablePointsToDisplay();
		Track track = currentTrack.getGpxFile().tracks.get(0);
		assert track.segments.size() == points.size(); 
		if (points.size() == 0 || newSegment) {
			points.add(new TrkSegment());
		}
		if(track.segments.size() == 0 || newSegment) {
			track.segments.add(new TrkSegment());
		}
		if (pt != null) {
			int ind = points.size() - 1;
			TrkSegment last = points.get(ind);
			last.points.add(pt);
			TrkSegment lt = track.segments.get(track.segments.size() - 1);
			lt.points.add(pt);
		}
		currentTrack.getModifiableGpxFile().modifiedTime = time;
	}
	
	public WptPt insertPointData(double lat, double lon, long time, String description, String name, String category, int color) {
		final WptPt pt = new WptPt(lat, lon, time, Double.NaN, 0, Double.NaN);
		pt.name = name;
		pt.category = category;
		pt.desc = description;
		if (color != 0) {
			pt.setColor(color);
		}
		currentTrack.getModifiableGpxFile().points.add(pt);
		currentTrack.getModifiableGpxFile().modifiedTime = time;
		points++;
		execWithClose(insertPointsScript, new Object[] { lat, lon, time, description, name, category, color });
		return pt;
	}

	public void updatePointData(WptPt pt, double lat, double lon, long time, String description, String name, String category, int color) {
		currentTrack.getModifiableGpxFile().modifiedTime = time;

		List<Object> params = new ArrayList<>();
		params.add(lat);
		params.add(lon);
		params.add(time);
		params.add(description);
		params.add(name);
		params.add(category);
		params.add(color);

		params.add(pt.getLatitude());
		params.add(pt.getLongitude());
		params.add(pt.time);

		StringBuilder sb = new StringBuilder();
		String prefix = "UPDATE " + POINT_NAME
				+ " SET "
				+ POINT_COL_LAT + "=?, "
				+ POINT_COL_LON + "=?, "
				+ POINT_COL_DATE + "=?, "
				+ POINT_COL_DESCRIPTION + "=?, "
				+ POINT_COL_NAME + "=?, "
				+ POINT_COL_CATEGORY + "=?, "
				+ POINT_COL_COLOR + "=? "
				+ "WHERE "
				+ POINT_COL_LAT + "=? AND "
				+ POINT_COL_LON + "=? AND "
				+ POINT_COL_DATE + "=?";

		sb.append(prefix);
		if (pt.desc != null) {
			sb.append(" AND ").append(POINT_COL_DESCRIPTION).append("=?");
			params.add(pt.desc);
		} else {
			sb.append(" AND ").append(POINT_COL_DESCRIPTION).append(" IS NULL");
		}
		if (pt.name != null) {
			sb.append(" AND ").append(POINT_COL_NAME).append("=?");
			params.add(pt.name);
		} else {
			sb.append(" AND ").append(POINT_COL_NAME).append(" IS NULL");
		}
		if (pt.category != null) {
			sb.append(" AND ").append(POINT_COL_CATEGORY).append("=?");
			params.add(pt.category);
		} else {
			sb.append(" AND ").append(POINT_COL_CATEGORY).append(" IS NULL");
		}

		execWithClose(sb.toString(), params.toArray());

		pt.lat = lat;
		pt.lon = lon;
		pt.time = time;
		pt.desc = description;
		pt.name = name;
		pt.category = category;
		if (color != 0) {
			pt.setColor(color);
		}
	}

	public void deletePointData(WptPt pt) {
		currentTrack.getModifiableGpxFile().points.remove(pt);
		currentTrack.getModifiableGpxFile().modifiedTime = System.currentTimeMillis();
		points--;

		List<Object> params = new ArrayList<>();
		params.add(pt.getLatitude());
		params.add(pt.getLongitude());
		params.add(pt.time);

		StringBuilder sb = new StringBuilder();
		String prefix = "DELETE FROM "
				+ POINT_NAME
				+ " WHERE "
				+ POINT_COL_LAT + "=? AND "
				+ POINT_COL_LON + "=? AND "
				+ POINT_COL_DATE + "=?";

		sb.append(prefix);
		if (pt.desc != null) {
			sb.append(" AND ").append(POINT_COL_DESCRIPTION).append("=?");
			params.add(pt.desc);
		} else {
			sb.append(" AND ").append(POINT_COL_DESCRIPTION).append(" IS NULL");
		}
		if (pt.name != null) {
			sb.append(" AND ").append(POINT_COL_NAME).append("=?");
			params.add(pt.name);
		} else {
			sb.append(" AND ").append(POINT_COL_NAME).append(" IS NULL");
		}
		if (pt.category != null) {
			sb.append(" AND ").append(POINT_COL_CATEGORY).append("=?");
			params.add(pt.category);
		} else {
			sb.append(" AND ").append(POINT_COL_CATEGORY).append(" IS NULL");
		}

		execWithClose(sb.toString(), params.toArray());
	}

	private synchronized void execWithClose(String script, Object[] objects) {
		SQLiteConnection db = getWritableDatabase();
		try {
			if (db != null) {
				db.execSQL(script, objects);
			}
		} finally {
			if (db != null) {
				db.close();
			}
		}
	}

	public void loadGpxFromDatabase(){
		Map<String, GPXFile> files = collectRecordedData();
		currentTrack.getModifiableGpxFile().tracks.clear();
		for (Map.Entry<String, GPXFile> entry : files.entrySet()){
			currentTrack.getModifiableGpxFile().points.addAll(entry.getValue().points);
			currentTrack.getModifiableGpxFile().tracks.addAll(entry.getValue().tracks);
		}
		currentTrack.processPoints();
		prepareCurrentTrackForRecording();
		GPXTrackAnalysis analysis = currentTrack.getModifiableGpxFile().getAnalysis(System.currentTimeMillis());
		distance = analysis.totalDistance;
		points = analysis.wptPoints;
	}

	private void prepareCurrentTrackForRecording() {
		if(currentTrack.getModifiableGpxFile().tracks.size() == 0) {
			currentTrack.getModifiableGpxFile().tracks.add(new Track());
		}
		while(currentTrack.getPointsToDisplay().size() < currentTrack.getModifiableGpxFile().tracks.size()) {
			TrkSegment trkSegment = new TrkSegment();
			currentTrack.getModifiablePointsToDisplay().add(trkSegment);
		}
	}

	public boolean getIsRecording() {
//		if (OsmandPlugin.getEnabledPlugin(OsmandMonitoringPlugin.class) != null) {
//			if (ctx.getSettings().SAVE_GLOBAL_TRACK_TO_GPX.get() ||
//					(ctx.getSettings().SAVE_TRACK_TO_GPX.get() && ctx.getRoutingHelper().isFollowingMode())) {
//				return true;
//			}
//		}
		return false;
	}

	public float getDistance() {
		return distance;
	}
	
	public int getPoints() {
		return points;
	}
	
	public long getLastTimeUpdated() {
		return lastTimeUpdated;
	}

	public GPXFile getCurrentGpx() {
		return currentTrack.getGpxFile();
	}
	
	public SelectedGpxFile getCurrentTrack() {
		return currentTrack;
	}

}
