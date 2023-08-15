package org.thaspius;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.geotools.feature.FeatureCollection;
import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.hipparchus.util.FastMath;

import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryCollection;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.PrecisionModel;
import org.locationtech.jts.io.ParseException;
import org.locationtech.jts.io.geojson.GeoJsonReader;
import org.locationtech.jts.io.geojson.GeoJsonWriter;
import org.orekit.bodies.GeodeticPoint;
import org.orekit.bodies.OneAxisEllipsoid;
import org.orekit.data.DataContext;
import org.orekit.data.DataProvidersManager;
import org.orekit.data.DirectoryCrawler;
import org.orekit.data.LazyLoadedDataContext;
import org.orekit.frames.Frame;
import org.orekit.frames.FramesFactory;
import org.orekit.frames.Transform;
import org.orekit.geometry.fov.CircularFieldOfView;
import org.orekit.geometry.fov.FieldOfView;
import org.orekit.orbits.KeplerianOrbit;
import org.orekit.orbits.PositionAngle;
import org.orekit.propagation.SpacecraftState;
import org.orekit.propagation.analytical.KeplerianPropagator;
import org.orekit.time.AbsoluteDate;
import org.orekit.time.TimeScale;
import org.orekit.time.TimeScalesFactory;
import org.orekit.utils.Constants;
import org.orekit.utils.IERSConventions;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
public class OreKitTest {

    public static final String WGS84_EPSG4326 = "EPSG:4326";
    public static final String EPSG4326 = "GEOGCS[\"WGS 84\",DATUM[\"WGS_1984\",SPHEROID[\"WGS 84\",6378137,298.257223563,AUTHORITY[\"EPSG\",\"7030\"]],AUTHORITY[\"EPSG\",\"6326\"]],PRIMEM[\"Greenwich\",0,AUTHORITY[\"EPSG\",\"8901\"]],UNIT[\"degree\",0.01745329251994328,AUTHORITY[\"EPSG\",\"9122\"]],AUTHORITY[\"EPSG\",\"4326\"]]";

    private static GeometryFactory factory4326 = null;

    // Ground / Earth frame to use for ground Locations (Terminals & Gateways)
    private static Frame EARTH_CENTERED_FRAME = null;

    // Earth shape
    private static OneAxisEllipsoid EARTH_SHAPE = null;

    public static final int decimalPrecision = 8;
    public static final GeoJsonWriter GEOJSON_WRITER = new GeoJsonWriter(decimalPrecision);
    public static final GeoJsonReader GEOJSON_READER = new GeoJsonReader(getFactory4326());

    private static TimeScale UTC = null;

    // Standard Earth Gravitational Parameter G*M - meters^3 / sec^2
    // G = Universal gravitational Constant = 6.67259 * 10-11 N m2/kg2
    // M = Mass of Earth = 5.98 * 1024 kg
    public static final double GM = 398600441800000.0;

    public static final double GEO_ALT_M = 35786000.0;

    public static void loadOreKitData() {
        String orekitDirectoryStr = "./src/main/resources/orekit-data/";
        LazyLoadedDataContext ctx = new LazyLoadedDataContext();
        DataContext.setDefault(ctx);
        DataProvidersManager manager = ctx.getDataProvidersManager();
        File orekitDataDir = new File(orekitDirectoryStr);
        DirectoryCrawler dc = new DirectoryCrawler(orekitDataDir);
        manager.addProvider(dc);

        ctx.getFrames();
    }

    @Test
    public void test1() {
        loadOreKitData();

        // #### Setup date
        int y = 2023;
        int m = 8;
        int d = 14;
        int hour = 0;
        int min = 0;
        int sec = 0;
        AbsoluteDate date = new AbsoluteDate(y, m, d, hour, min, sec, getUTC());
        double angularMarginRad = 0.0;
        double angularStepDeg = 1.0;
        double beamAngleDeg = 1.0;

        // #### Setup ground location
        double gndLatDeg = 22.52306;
        double gndLngDeg = 17.06028;
        double gndLatRad = FastMath.toRadians(gndLatDeg);
        double gndLngRad = FastMath.toRadians(gndLngDeg);
        double gndAltM = 0.0;

        // ##### setup orbit
        Frame inertialFrame = FramesFactory.getEME2000();

        // semi major axis in meters
        double semiMajorAxis = GEO_ALT_M;

        // eccentricity
        double eccentricity = 0.0;

        // inclination
        double inclinationDeg = 0.0;
        double inclinationRad = FastMath.toRadians(inclinationDeg);

        // perigee argument
        double argOfPerigeeDeg = 0.0;
        double argOfPerigeeRad = FastMath.toRadians(argOfPerigeeDeg);

        // right ascension of ascending node
        double raanDeg = 0.0;
        double raanRad = FastMath.toRadians(raanDeg);

        // true anomaly
        double trueAnomalyDeg = 0.0;
        double trueAnomalyRad = FastMath.toRadians(trueAnomalyDeg);
        PositionAngle anomalyType = PositionAngle.TRUE;

        KeplerianOrbit initialOrbit = new KeplerianOrbit(semiMajorAxis, eccentricity, inclinationRad, argOfPerigeeRad,
                raanRad, trueAnomalyRad, anomalyType, inertialFrame, date, GM);
        KeplerianPropagator kepler = new KeplerianPropagator(initialOrbit);

        // must update spacecraft state to timegrain's date to calculate footprint FOV
        SpacecraftState state = kepler.propagate(date);
        GeodeticPoint scLoc = getGeodeticPoint(state);
        double scLatDeg = FastMath.toDegrees(scLoc.getLatitude());
        double scLngDeg = FastMath.toDegrees(scLoc.getLongitude());
        double scAltM = scLoc.getAltitude();

        double angularStepRad = FastMath.toRadians(angularStepDeg);
        double halfBeamAngleRad = FastMath.toRadians(beamAngleDeg / 2.0);

        // Caculate Line of Site in ECF frame
        GeodeticPoint targetGeodeticPoint = new GeodeticPoint(gndLatRad, gndLngRad, gndAltM);

        OneAxisEllipsoid earth = getEarthShape();
        Vector3D targetEarth = earth.transform(targetGeodeticPoint);
        Frame earthFrame = getEarthCenteredFrame();
        Vector3D losSatToTargetEcf = targetEarth.subtract(state.getPVCoordinates(earthFrame).getPosition());

        // Get the transform from J2000 to ECF
        Transform inertToBody = state.getFrame().getTransformTo(earth.getBodyFrame(), date);

        // Get the transform from satellite body from to ECF Frame
        Transform satBodyToEcf = new Transform(date, state.toTransform().getInverse(), inertToBody);

        // Transform the LOS into the satellite body frame
        Vector3D losPointingBody = satBodyToEcf.getInverse().getRotation().applyTo(losSatToTargetEcf.normalize());

        FieldOfView fov = new CircularFieldOfView(losPointingBody, halfBeamAngleRad, angularMarginRad);
        List<List<GeodeticPoint>> pointsList = fov.getFootprint(satBodyToEcf, earth, angularStepRad);

        ArrayList<Geometry> geoms = new ArrayList<>();
        for (List<GeodeticPoint> points : pointsList) {
            // footprint from OreKit does not "close" as a JTS Polygon requires
            // add first point to end of the List to close the polygon
            points.add(points.get(0));
            Polygon poly = toPoly(points);
            poly.normalize();
            geoms.add(poly);
        }

        Point gndPoint = toPoint(new Coordinate(gndLngDeg, gndLatDeg, gndAltM));
        geoms.add(gndPoint);

        Point scCoord = toPoint(new Coordinate(scLngDeg, scLatDeg, scAltM));
        geoms.add(scCoord);

        GeometryCollection collection = getFactory4326().createGeometryCollection(geoms.toArray(new Geometry[] {}));
        String json = toJson(collection);
        System.out.println(json);

        // copy JSON and paste into https://geojson.io/ 
    }

    public static GeometryFactory getFactory4326() {
        if (factory4326 == null) {
            factory4326 = new GeometryFactory(new PrecisionModel(PrecisionModel.FLOATING), 4326);
        }
        return factory4326;
    }

    public static Frame getEarthCenteredFrame() {
        if (EARTH_CENTERED_FRAME == null) {
            EARTH_CENTERED_FRAME = FramesFactory.getITRF(IERSConventions.IERS_2010, true);
        }
        return EARTH_CENTERED_FRAME;
    }

    public static OneAxisEllipsoid getEarthShape() {
        if (EARTH_SHAPE == null) {
            EARTH_SHAPE = new OneAxisEllipsoid(Constants.WGS84_EARTH_EQUATORIAL_RADIUS,
                    Constants.WGS84_EARTH_FLATTENING, getEarthCenteredFrame());
        }
        return EARTH_SHAPE;
    }

    public static TimeScale getUTC() {
        if (UTC == null) {
            UTC = TimeScalesFactory.getUTC();
        }
        return UTC;
    }

    public static Polygon toPoly(Coordinate[] coords) {
        return getFactory4326().createPolygon(coords);
    }

    public static Point toPoint(Coordinate coord) {
        return getFactory4326().createPoint(coord);
    }

    public static String toJson(Geometry geom) {
        return GEOJSON_WRITER.write(geom);
    }

    public static Geometry fromJson(String json) throws ParseException {
        return GEOJSON_READER.read(json);
    }

    public static GeodeticPoint getGeodeticPoint(SpacecraftState state) {
        Vector3D pos = state.getPVCoordinates().getPosition();
        GeodeticPoint scPoint = getEarthShape().transform(pos, state.getFrame(), state.getDate());

        return scPoint;
    }

    /**
     * Coordinates are in Deg where x=lat and y=lng
     * 
     * @param coords
     * @return
     */
    public static String toGeoJsonPoly(Coordinate[] coords) {
        String json = "{\"coordinates\": [[";

        for (int i = 0; i < coords.length; i++) {
            Coordinate coord = coords[i];
            double latDeg = coord.x;
            double lngDeg = coord.y;

            json = json + "[" + lngDeg + ", " + latDeg + "],";
        }
        json = json.substring(0, json.length() - 1);

        json = json + "]],\"type\": \"Polygon\"}";

        return json;
    }

    /**
     * Reminder: Geodetic point lat/lng are in RADIANS!
     * 
     * @param points
     * @return
     */
    public static String toGeoJsonPoly(List<GeodeticPoint> points) {
        Coordinate[] coordArr = new Coordinate[points.size()];
        for (int i = 0; i < points.size(); i++) {
            GeodeticPoint point = points.get(i);
            double latDeg = FastMath.toDegrees(point.getLatitude());
            double lngDeg = FastMath.toDegrees(point.getLongitude());
            Coordinate coord = new Coordinate(latDeg, lngDeg);
            coordArr[i] = coord;
        }
        return toGeoJsonPoly(coordArr);
    }

    public static Polygon toPoly(List<GeodeticPoint> points) {
        Polygon poly = null;
        Coordinate[] coordArr = new Coordinate[points.size()];

        for (int i = 0; i < points.size(); i++) {
            GeodeticPoint point = points.get(i);
            double latDeg = FastMath.toDegrees(point.getLatitude());
            double lngDeg = FastMath.toDegrees(point.getLongitude());
            Coordinate coord = new Coordinate(lngDeg, latDeg);
            coordArr[i] = coord;
        }
        poly = toPoly(coordArr);
        return poly;
    }
}
