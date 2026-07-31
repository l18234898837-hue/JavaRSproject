package com.htcloud.orbit;

import cn.hutool.core.collection.CollUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.htcloud.compress.Compressions;
import com.htcloud.compress.PathUtils;
import com.htcloud.okhttp.core.OkHttpRequester;
import com.htcloud.orbit.bis.bean.OrbitForecastSpecDto;
import com.htcloud.orbit.bis.bean.ProSatelliteOrbitForecastResultItemDTO;
import com.htcloud.orbit.bis.bean.ProSatelliteOrbitForecastStateDto;
import com.htcloud.orbit.config.OrekitConfigProperties;
import com.htcloud.orbit.model.StepHandler;
import com.htcloud.orbit.model.TrackResult;
import com.htcloud.orbit.model.ZoneDetectResult;
import com.htcloud.orbit.utils.GeoToolsUtil;
import com.htht.common.utils.StringUtils;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.geotools.geojson.geom.GeometryJSON;
import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.hipparchus.geometry.spherical.twod.S2Point;
import org.hipparchus.geometry.spherical.twod.SphericalPolygonsSet;
import org.hipparchus.ode.events.Action;
import org.hipparchus.util.FastMath;
import org.locationtech.jts.geom.*;
import org.orekit.bodies.BodyShape;
import org.orekit.bodies.GeodeticPoint;
import org.orekit.bodies.OneAxisEllipsoid;
import org.orekit.data.DataContext;
import org.orekit.data.DataProvidersManager;
import org.orekit.data.DirectoryCrawler;
import org.orekit.errors.OrekitException;
import org.orekit.frames.Frame;
import org.orekit.frames.FramesFactory;
import org.orekit.orbits.KeplerianOrbit;
import org.orekit.orbits.Orbit;
import org.orekit.orbits.PositionAngleType;
import org.orekit.propagation.Propagator;
import org.orekit.propagation.SpacecraftState;
import org.orekit.propagation.analytical.EcksteinHechlerPropagator;
import org.orekit.propagation.analytical.tle.TLE;
import org.orekit.propagation.analytical.tle.TLEPropagator;
import org.orekit.propagation.events.AbstractDetector;
import org.orekit.propagation.events.GeographicZoneDetector;
import org.orekit.time.AbsoluteDate;
import org.orekit.time.TimeScale;
import org.orekit.time.TimeScalesFactory;
import org.orekit.utils.Constants;
import org.orekit.utils.IERSConventions;
import org.orekit.utils.PVCoordinates;
import org.orekit.utils.TimeStampedPVCoordinates;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * orekit-data下载路径： <a href="https://gitlab.orekit.org/orekit/orekit-data/-/archive/master/orekit-data-master.zip">orekit-data-master.zip</a>
 *
 * @author Gersy
 * @date 2024/4/9
 * @since 1.0
 */
@Component
@RequiredArgsConstructor
public class OrbitHelper {

    private final static Logger LOGGER = LoggerFactory.getLogger(OrbitHelper.class);
    private static boolean isInitialized = false;
    private static BodyShape earthShape;
    private static Frame ECFF;

    static {
//        init("F:\\work\\orekit-data-master", "https://gitlab.orekit.org/orekit/orekit-data/-/archive/master/orekit-data-master.zip");
        init("/htht/server/orekit/orekit-data-master", "https://gitlab.orekit.org/orekit/orekit-data/-/archive/master/orekit-data-master.zip");
    }

    public static boolean isInitialized() {
        return isInitialized;
    }

    public static void requireInitialized() {
        if (!isInitialized()) {
            throw new IllegalStateException("invoke init() first");
        }
    }

    synchronized public static void init(String orekitPath, String orekitUrl) {
        if (isInitialized()) {
            return;
        }
        File orekitFile = null;
        try {
            if (!StringUtils.isBlank(orekitPath)) {
                orekitFile = new File(orekitPath);
                if (!orekitFile.exists()) {
                    orekitFile = download(orekitUrl);
                }
            } else {
                orekitFile = download(orekitUrl);
            }
        } catch (Exception e) {
            LOGGER.error("OrbitHelper.init error:", e);
        }

        if (orekitFile == null || !orekitFile.exists()) {
            LOGGER.error("orekit-data-path is blank or directory not found");
            return;
        }

        DataProvidersManager manager = DataContext.getDefault().getDataProvidersManager();
        manager.addProvider(new DirectoryCrawler(orekitFile)); // 存放orekitdata路径
        Frame earthFrame = FramesFactory.getITRF(IERSConventions.IERS_2010, true);
        earthShape = new OneAxisEllipsoid(Constants.WGS84_EARTH_EQUATORIAL_RADIUS, Constants.WGS84_EARTH_FLATTENING, earthFrame);
        //earthShape = ReferenceEllipsoid.getWgs84(earthFrame);
        ECFF = FramesFactory.getITRF(IERSConventions.IERS_2010, true);
        isInitialized = true;
        LOGGER.info("OrbitHelper initialize successfully!");
    }

    private static File download(String orekitUrl) throws IOException {
        if (StringUtils.isBlank(orekitUrl)) return null;
        String jarPath = PathUtils.getJarPath();
        LOGGER.info("jarPath:{}", jarPath);
        File orekitFile = Path.of(jarPath, "orekit-data-master").toFile();
        // 如果已存在则跳过解压
        if (!orekitFile.exists()) {
            // 从 resources 加载 zip 文件
            Path rarPath = Path.of(jarPath, "orekit-data-master.rar");
            OkHttpRequester.get(orekitUrl).download(rarPath.toFile());
            // 解压到jar目录
            File orekitDataTemp = Path.of(jarPath, "orekit-data-master-temp").toFile();
            orekitDataTemp.mkdir();
            Compressions.uncompress(rarPath.toFile(), orekitDataTemp, true);
            boolean b = orekitDataTemp.renameTo(orekitFile);
            if (!b) {
                throw new RuntimeException("rename orekit-data-master-temp to orekit-data-master failed");
            }
        }
        return orekitFile;
    }

    /**
     * 预测某时刻卫星位置
     *
     * @param tle        两行轨道参数
     * @param targetTime 预测时刻
     * @return [经度，纬度，高度（米）]
     */
    public static List<Double> forecastSatellitePosition(TLE tle, AbsoluteDate targetTime) {
        Objects.requireNonNull(tle);
        Objects.requireNonNull(targetTime);

        requireInitialized();
        TLEPropagator tlePropagator = TLEPropagator.selectExtrapolator(tle);
        SpacecraftState spacecraftState = tlePropagator.propagate(targetTime);
        return toLonLatAlt(spacecraftState);
    }

    public static List<Double> forecastSatellitePosition(String tleLine1, String tleLine2, Date targetTime) {
        TLE tle = new TLE(tleLine1, tleLine2);
        AbsoluteDate absoluteDate = buildAbsoluteDate(targetTime);
        return forecastSatellitePosition(tle, absoluteDate);
    }

    /**
     * 根据TLE预测卫星进入/离开地面区域情况
     *
     * @param tle               TLE参数
     * @param zone              检测区域
     * @param forecastStartTime 预测时间段开始
     * @param forecastEndTime   预测时间段结束
     * @return 区域预测结果
     */
    public static List<ZoneDetectResult> forecastSatelliteOverZone(TLE tle, SphericalPolygonsSet zone, AbsoluteDate forecastStartTime, AbsoluteDate forecastEndTime) {
        Objects.requireNonNull(tle);
        Objects.requireNonNull(zone);
        Objects.requireNonNull(forecastStartTime);
        Objects.requireNonNull(forecastEndTime);
        requireInitialized();

        final TLEPropagator tlePropagator = TLEPropagator.selectExtrapolator(tle);
        // 步进时间 s
        double step = 2.0;

        AtomicReference<SpacecraftState> spacecraftState = new AtomicReference<>(null);
        // 获取所有预测的轨迹
//        List<StepHandler> allPoint = setStepHandler(tlePropagator, step,spacecraftState);
// 可能多次进入
        List<ZoneDetectResult> resultList = new ArrayList<>();
        AtomicInteger count = new AtomicInteger(0);
        tlePropagator.setStepHandler(step, state -> {
            SpacecraftState spacecraftState1 = spacecraftState.get();
            if (spacecraftState1 != null) {
                if (spacecraftState1.getDate().compareTo(state.getDate()) > 0) {
                    count.set(-1);
                    return;
                }
            }

            if (count.get() == -1) {
                count.set(0);
                Date entryTime = toDate(state.getDate());
                List<Double> position = toLonLatAlt(state);
                List<List<Double>> footprints = new ArrayList<>();
                footprints.add(position);
                ZoneDetectResult result = new ZoneDetectResult();
                result.setExitTime(entryTime);
                result.setFootprints(footprints);
                if (!resultList.isEmpty()) {
                    resultList.get(resultList.size() - 1).setAfterExit(result);
                }
            }
            spacecraftState.set(state);
        });

        // 卫星轨道经过区域模拟，监听并记录出入境时刻卫星状态信息（触发监听条件：监听到有出入境的才返回卫星位置，即卫星是否在境内的状态转变时）
        final GeographicZoneDetector zoneDetector = createGeographicZoneDetector(step, zone, resultList, spacecraftState);
        tlePropagator.addEventDetector(zoneDetector);
        tlePropagator.propagate(forecastStartTime, forecastEndTime);
//        Date entryTime = resultList.get(0).getEntryTime();
//        Date exitTime = resultList.get(resultList.size() - 1).getExitTime();
//        long l = (exitTime.getTime() - entryTime.getTime()) / 1000;
//        tlePropagator.setStepHandler(l, state -> {
//            Date time = toDate(state.getDate());
//            List<Double> lonLatAlt = toLonLatAlt(state);
//            trackResults.add(new StepHandler(time, lonLatAlt));
//        });
//        tlePropagator.propagate(forecastStartTime, forecastEndTime);
        // 处理入境前 出境后 的点
        return trackProcess(resultList);
    }

    private static List<ZoneDetectResult> trackProcess(List<ZoneDetectResult> resultList) {
//        List<ZoneDetectResult> results = new ArrayList<>();
//        for (ZoneDetectResult zoneDetectResult : resultList) {
//            ZoneDetectResult newZoneDetectResult = zoneDetectResult.clone();
//            List<List<Double>> footprints = newZoneDetectResult.getFootprints();
//
//            ZoneDetectResult beforeEnter = zoneDetectResult.getBeforeEnter();
//            ZoneDetectResult afterExit = zoneDetectResult.getAfterExit();
//
////            if (beforeEnter != null) {
////                newZoneDetectResult.setEntryTime(beforeEnter.getEntryTime());
////                footprints.set(0, beforeEnter.getFootprints().get(0));
////            }
////
////            if (afterExit != null) {
////                newZoneDetectResult.setExitTime(afterExit.getExitTime());
////                footprints.set(1, afterExit.getFootprints().get(0));
////            }
//            results.add(newZoneDetectResult);
//        }
        return resultList;
    }

    private static List<ZoneDetectResult> trackProcess(List<StepHandler> allPoint, List<ZoneDetectResult> resultList) {
        List<ZoneDetectResult> results = new ArrayList<>();
        // 确保 allPoint 已排序
        allPoint.sort(Comparator.comparing(StepHandler::getTime));
        for (ZoneDetectResult zoneDetectResult : resultList) {
            if (zoneDetectResult.getFootprints().size() != 2) continue;

            ZoneDetectResult newZoneDetectResult = zoneDetectResult.clone();
            List<List<Double>> footprints = newZoneDetectResult.getFootprints();

//            StepHandler[] points = findNearestPointsWithSingleBinarySearch(allPoint, zoneDetectResult.getEntryTime(), zoneDetectResult.getExitTime());
            StepHandler beforeEntry = findNearestPointsWithSingleBinarySearch(allPoint, zoneDetectResult.getEntryTime());

//            StepHandler beforeEntry = points[0];
//            StepHandler afterExit = points[1];

            if (beforeEntry != null) {
                newZoneDetectResult.setEntryTime(beforeEntry.getTime());
                footprints.set(0, beforeEntry.getFootprints());
            }

//            if (afterExit != null) {
//                newZoneDetectResult.setExitTime(afterExit.getTime());
//                footprints.set(1, afterExit.getFootprints());
//            }

//            for (StepHandler stepHandler : allPoint) {
//                Date timestamp = stepHandler.getTime();
//                // 入境前
//                if (timestamp.before(entryTime)) {
//                    newZoneDetectResult.setEntryTime(timestamp);
//                    footprints.set(0, stepHandler.getFootprints());
//                } else if (timestamp.after(exitTime)) {
//                    // 出境后
//                    newZoneDetectResult.setExitTime(timestamp);
//                    footprints.set(1, stepHandler.getFootprints());
//                    break;
//                }
//            }
            results.add(newZoneDetectResult);
        }
        return results;
    }

    public static StepHandler[] findNearestPointsWithSingleBinarySearch(List<StepHandler> sortedSteps, Date entryTime, Date exitTime) {
        StepHandler[] result = new StepHandler[2];

        int entryInsertionPoint = Collections.binarySearch(sortedSteps, null, (o1, o2) -> o1.getTime().compareTo(entryTime));
        int exitInsertionPoint = Collections.binarySearch(sortedSteps, null, (o1, o2) -> o1.getTime().compareTo(exitTime));

        // 转换负数为插入点
        if (entryInsertionPoint < 0) {
            entryInsertionPoint = -(entryInsertionPoint + 1);
        }
        if (exitInsertionPoint < 0) {
            exitInsertionPoint = -(exitInsertionPoint + 1);
        }

        // 入境前最后一个点
        if (entryInsertionPoint > 0) {
            result[0] = sortedSteps.get(entryInsertionPoint - 1);
        } else {
            result[0] = null; // 没有入境前点
        }

        // 出境后第一个点
        if (exitInsertionPoint < sortedSteps.size()) {
            result[1] = sortedSteps.get(exitInsertionPoint);
        } else {
            result[1] = null; // 没有出境后点
        }

        return result;
    }

    public static StepHandler findNearestPointsWithSingleBinarySearch(List<StepHandler> sortedSteps, Date entryTime) {
        StepHandler result = null;

        int entryInsertionPoint = Collections.binarySearch(sortedSteps, null, (o1, o2) -> o1.getTime().compareTo(entryTime));

        // 转换负数为插入点
        if (entryInsertionPoint < 0) {
            entryInsertionPoint = -(entryInsertionPoint + 1);
        }

        // 入境前最后一个点
        if (entryInsertionPoint > 0) {
            result = sortedSteps.get(entryInsertionPoint - 1);
        }
        return result;
    }


    private static List<StepHandler> setStepHandler(TLEPropagator propagator, double step, AtomicReference<SpacecraftState> spacecraftState) {
        List<StepHandler> trackResults = new ArrayList<>();
        propagator.setStepHandler(step, state -> {
//            SpacecraftState spacecraftState1 = spacecraftState.get();
//            if (spacecraftState1 != null) {
//                log.info("setStepHandler:{}", toDate(state.getDate()));
//                log.info("进入spacecraftState:{}", toDate(spacecraftState1.getDate()));
//                spacecraftState.set(null);
//            }
            spacecraftState.set(state);
//            if (spacecraftState.get() != null){
//                log.info("spacecraftState:{}", spacecraftState.get());
//                log.info("进入setStepHandler:{}", state);
//                spacecraftState.set(null);
//            }
//            Date time = toDate(state.getDate());
//            List<Double> lonLatAlt = toLonLatAlt(state);
//            trackResults.add(new StepHandler(time, lonLatAlt));
        });
        return trackResults;
    }

    private static GeographicZoneDetector createGeographicZoneDetector(double step, SphericalPolygonsSet zone, List<ZoneDetectResult> resultList, AtomicReference<SpacecraftState> spacecraftState) {
        return new GeographicZoneDetector(step, AbstractDetector.DEFAULT_THRESHOLD, earthShape, zone, 0).withHandler((s, detector, increasing) -> {
            SpacecraftState spacecraftState1 = spacecraftState.get();
            spacecraftState.set(s);
            if (!increasing) {
                // 入境
                Date entryTime = toDate(s.getDate());
                // 特殊处理一下，不规则边界线引起多次入境出境（30分钟内的合并）
                boolean isAdd = true;
                if (!resultList.isEmpty()) {
                    ZoneDetectResult lastResult = resultList.get(resultList.size() - 1);
                    if (entryTime.getTime() < lastResult.getExitTime().getTime() + 1800_000) {
                        isAdd = false;
                    }
                }
                if (isAdd) {
                    List<Double> position = toLonLatAlt(s);
                    List<List<Double>> footprints = new ArrayList<>();
                    footprints.add(position);
                    ZoneDetectResult result = new ZoneDetectResult();
                    result.setEntryTime(entryTime);
                    result.setFootprints(footprints);
                    if (spacecraftState1 != null) {
                        List<Double> position1 = toLonLatAlt(spacecraftState1);
                        List<List<Double>> footprints1 = new ArrayList<>();
                        footprints1.add(position1);
                        ZoneDetectResult result1 = new ZoneDetectResult();
                        result1.setEntryTime(toDate(spacecraftState1.getDate()));
                        result1.setFootprints(footprints1);
                        result.setBeforeEnter(result1);
                    }
                    resultList.add(result);
                }
            } else {
                if (!resultList.isEmpty()) {
                    // 出境
                    Date exitTime = toDate(s.getDate());
                    List<Double> position = toLonLatAlt(s);
                    ZoneDetectResult zoneDetectResult = resultList.get(resultList.size() - 1);
                    zoneDetectResult.setExitTime(exitTime);
                    List<List<Double>> footprints = zoneDetectResult.getFootprints();
                    if (footprints.size() == 1) {
                        footprints.add(position);
                    } else {
                        footprints.set(1, position);
                    }
                }
            }
            return Action.CONTINUE;
        });
    }

    /**
     * 根据TLE预测卫星进入/离开地面区域情况
     *
     * @param tleLine1  tle 第一行
     * @param tleLine2  tle 第二行
     * @param startTime 预测日期时间区间开始
     * @param endTime   预测日期时间区间结束
     * @param zone      检测区域，WGS84坐标系
     * @return
     */
    public static List<ZoneDetectResult> forecastSatelliteOverZone(String tleLine1, String tleLine2, Date startTime, Date endTime, Geometry zone) {
        boolean formatOK = TLE.isFormatOK(tleLine1, tleLine2);
        if (!formatOK) throw new RuntimeException("TLE格式错误");
        Objects.requireNonNull(zone);

        // 在处理前先检查并转换矩形区域
        Geometry processedZone = isRectangle(zone) ?
            convertToSphericalRectangle(zone) : zone;

        if (zone instanceof MultiPolygon) {
            return forecastSatelliteOverZone(tleLine1, tleLine2, startTime, endTime, (MultiPolygon) processedZone);
        }
        if (zone instanceof Polygon) {
            return forecastSatelliteOverZone(tleLine1, tleLine2, startTime, endTime, (Polygon) processedZone);
        }
        throw new IllegalArgumentException("unsupported zone's type: " + zone.getClass().getSimpleName());
    }

    /**
     * 判断几何体是否为矩形
     */
    private static boolean isRectangle(Geometry geometry) {
        if (!(geometry instanceof Polygon)) return false;

        Polygon polygon = (Polygon) geometry;
        Coordinate[] coords = polygon.getCoordinates();

        // 矩形应该有4个顶点（或者5个，首尾相同）
        if (coords.length != 4 && coords.length != 5) return false;

        // 检查角度 - 矩形应该有4个近似90度的角
        return hasRightAngles(polygon);
    }

    /**
     * 检查多边形是否具有直角（近似矩形）
     */
    private static boolean hasRightAngles(Polygon polygon) {
        Coordinate[] coords = polygon.getCoordinates();
        int numPoints = coords.length;

        for (int i = 0; i < numPoints - 1; i++) {
            Coordinate prev = coords[i];
            Coordinate curr = coords[(i + 1) % (numPoints - 1)];
            Coordinate next = coords[(i + 2) % (numPoints - 1)];

            double angle = calculateAngle(prev, curr, next);
            // 允许一定的误差范围（80-100度都认为是直角）
            if (Math.abs(angle - 90) > 10) {
                return false;
            }
        }
        return true;
    }

    /**
     * 计算三个点形成的角度
     */
    private static double calculateAngle(Coordinate a, Coordinate b, Coordinate c) {
        double baX = a.x - b.x;
        double baY = a.y - b.y;
        double bcX = c.x - b.x;
        double bcY = c.y - b.y;

        double dotProduct = baX * bcX + baY * bcY;
        double magBA = Math.sqrt(baX * baX + baY * baY);
        double magBC = Math.sqrt(bcX * bcX + bcY * bcY);

        double cosAngle = dotProduct / (magBA * magBC);
        cosAngle = Math.max(-1, Math.min(1, cosAngle)); // 限制范围

        return Math.toDegrees(Math.acos(cosAngle));
    }

    /**
     * 将平面矩形转换为球面矩形
     */
    private static Geometry convertToSphericalRectangle(Geometry rectangle) {
        Envelope envelope = rectangle.getEnvelopeInternal();

        List<List<Double>> sphericalRect = createAccurateSphericalRectangle(
            envelope.getMinX(), envelope.getMinY(),
            envelope.getMaxX(), envelope.getMaxY(), 50);

        return GeoToolsUtil.createPolygonFromCoordinates(
            sphericalRect.stream()
                .map(point -> new Coordinate(point.get(0), point.get(1)))
                .toArray(Coordinate[]::new));
    }

    /**
     * 根据TLE预测卫星进入/离开地面区域情况
     *
     * @param tleLine1  tle 第一行
     * @param tleLine2  tle 第二行
     * @param startTime 预测日期时间区间开始
     * @param endTime   预测日期时间区间结束
     * @param zone      检测区域，WGS84坐标系
     * @return
     */
    public static List<ZoneDetectResult> forecastSatelliteOverZone(String tleLine1, String tleLine2, Date startTime, Date endTime, Polygon zone) {
        TLE tle = new TLE(tleLine1, tleLine2);
        AbsoluteDate sDate = buildAbsoluteDate(startTime);
        AbsoluteDate eDate = buildAbsoluteDate(endTime);
        SphericalPolygonsSet polygonsSet = SphericalPolygonSetFactory.build(zone);
        return forecastSatelliteOverZone(tle, polygonsSet, sDate, eDate);
    }

    /**
     * 根据TLE预测卫星进入/离开地面区域情况
     *
     * @param tleLine1  tle 第一行
     * @param tleLine2  tle 第二行
     * @param startTime 预测日期时间区间开始
     * @param endTime   预测日期时间区间结束
     * @param zone      检测区域，WGS84坐标系
     * @return List<ZoneDetectResult> 预测结果
     */
    public static List<ZoneDetectResult> forecastSatelliteOverZone(String tleLine1, String tleLine2, Date startTime, Date endTime, MultiPolygon zone) {
        TLE tle = new TLE(tleLine1, tleLine2);
        AbsoluteDate sDate = buildAbsoluteDate(startTime);
        AbsoluteDate eDate = buildAbsoluteDate(endTime);
        SphericalPolygonsSet polygonsSet = SphericalPolygonSetFactory.build(zone);
        return forecastSatelliteOverZone(tle, polygonsSet, sDate, eDate);
    }

    /**
     * 根据TLE预测卫星进入/离开地面区域情况
     *
     * @param tleLine1           tle 第一行
     * @param tleLine2           tle 第二行
     * @param startTime          预测日期时间区间开始
     * @param endTime            预测日期时间区间结束
     * @param polygonCoordinates 简单多边形的坐标点
     * @return
     */
    public static List<ZoneDetectResult> forecastSatelliteOverZone(String tleLine1, String tleLine2, Date startTime, Date endTime, List<List<Double>> polygonCoordinates) {
        TLE tle = new TLE(tleLine1, tleLine2);
        AbsoluteDate sDate = buildAbsoluteDate(startTime);
        AbsoluteDate eDate = buildAbsoluteDate(endTime);
        SphericalPolygonsSet polygonsSet = toSphericalPolygonsSet(polygonCoordinates);
        return forecastSatelliteOverZone(tle, polygonsSet, sDate, eDate);
    }

    /**
     * 追踪卫星位置
     *
     * @param tle         TLE参数
     * @param startTime   追踪时间区间开始
     * @param endTime     追踪时间区间结束
     * @param stepSeconds 多少秒输出一次坐标，时间
     * @return 卫星轨迹
     */
    public static List<TrackResult> trackSatellite(TLE tle, AbsoluteDate startTime, AbsoluteDate endTime, double stepSeconds) {
        Objects.requireNonNull(tle, "tle is null");
        Objects.requireNonNull(startTime, "startTime is null");
        Objects.requireNonNull(endTime, "endTime is null");

        if (stepSeconds <= 1e-6) {
            throw new IllegalArgumentException("stepSeconds <= 0");
        }
        requireInitialized();
        TLEPropagator tlePropagator = TLEPropagator.selectExtrapolator(tle);
        final List<TrackResult> resultList = new ArrayList<>();
        tlePropagator.setStepHandler(stepSeconds, currentState -> {
            Date time = toDate(currentState.getDate());
            List<Double> lonLatAlt = toLonLatAlt(currentState);
            resultList.add(new TrackResult(lonLatAlt.get(0), lonLatAlt.get(1), lonLatAlt.get(2), time));
        });
        tlePropagator.propagate(startTime, endTime);
        return resultList;
    }

    /**
     * 追踪卫星位置
     *
     * @param tleLine1    TLE参数第一行
     * @param tleLine2    TLE参数第二行
     * @param startTime   追踪时间区间开始
     * @param endTime     追踪时间区间结束
     * @param stepSeconds 多少秒输出一次坐标，时间
     * @return 卫星轨迹
     */
    public static List<TrackResult> trackSatellite(String tleLine1, String tleLine2, Date startTime, Date endTime, double stepSeconds) {
        TLE tle = new TLE(tleLine1, tleLine2);
        AbsoluteDate sDate = buildAbsoluteDate(startTime);
        AbsoluteDate eDate = buildAbsoluteDate(endTime);
        return trackSatellite(tle, sDate, eDate, stepSeconds);
    }

    /**
     * 获取轨道交角/倾角
     *
     * @param tleLine2 TLE中的第二行参数
     * @return 轨道交角/倾角，单位：弧度
     */
    public static double getInclinationFromTle(String tleLine1, String tleLine2) {
        TLE tle = new TLE(tleLine1, tleLine2);
        return tle.getI();
    }

    /**
     * 获取卫星速度方向角度
     */
    private static double getSpeedTheta(TLE tle, AbsoluteDate targetDate) {
        TLEPropagator propagator = TLEPropagator.selectExtrapolator(tle);
        // 卫星状态
        SpacecraftState spacecraftState = propagator.propagate(targetDate);
        // 卫星的地理位置
        TimeStampedPVCoordinates pvCoordinates = spacecraftState.getPVCoordinates(earthShape.getBodyFrame());
        Vector3D velocity = pvCoordinates.getVelocity();

        // 总速度的方向（弧度）
        double speedAzimuth = Math.atan2(velocity.getY(), velocity.getX());
        speedAzimuth = (speedAzimuth + FastMath.PI) % (2 * FastMath.PI) - FastMath.PI;

        return speedAzimuth;
    }

    /**
     * 获取卫星速度方向角度
     */
    public static double getSpeedTheta(String tleLine1, String tleLine2, Date date) {
        AbsoluteDate targetDate = buildAbsoluteDate(date);
        TLE tle = new TLE(tleLine1, tleLine2);
        return getSpeedTheta(tle, targetDate);
    }

    /**
     * 转换为经度，维度，高度（米）
     *
     * @param geodeticPoint
     * @return
     */
    public static List<Double> toLonLatAlt(GeodeticPoint geodeticPoint) {
        List<Double> list = new ArrayList<>();
        list.add(FastMath.toDegrees(geodeticPoint.getLongitude()));
        list.add(FastMath.toDegrees(geodeticPoint.getLatitude()));
        list.add(geodeticPoint.getAltitude());
        return list;
    }

    public static List<Double> toLonLatAlt(SpacecraftState spacecraftState) {
        return toLonLatAlt(toGeodeticPoint(spacecraftState));
    }


    /**
     * 构建 SphericalPolygonsSet
     *
     * @param boundaryPoints 边界的点（逆时针） [[经度,维度],...]
     * @return
     */
    public static SphericalPolygonsSet toSphericalPolygonsSet(List<List<Double>> boundaryPoints) {
        final S2Point[] vertices = new S2Point[boundaryPoints.size()];
        for (int i = 0; i < boundaryPoints.size(); ++i) {
            List<Double> point = boundaryPoints.get(i);
            vertices[i] = new S2Point(FastMath.toRadians(point.get(0)), 0.5 * FastMath.PI - FastMath.toRadians(point.get(1)));
        }
        return new SphericalPolygonsSet(1e-10, vertices);
    }

    public static GeodeticPoint toGeodeticPoint(SpacecraftState spacecraftState) {
        return earthShape.transform(spacecraftState.getPosition(), spacecraftState.getFrame(), spacecraftState.getDate());
    }

    public static AbsoluteDate buildAbsoluteDate(Date date) {
        return new AbsoluteDate(date, TimeScalesFactory.getUTC());
    }

    public static Date toDate(AbsoluteDate absoluteDate) {
        return absoluteDate.toDate(TimeScalesFactory.getUTC());
    }

    public static void main(String[] args) {

        TLE tle = getTle();
        TLEPropagator tlePropagator = TLEPropagator.selectExtrapolator(tle);
        Orbit cartesianOrbit = tlePropagator.getInitialState().getOrbit(); // 笛卡尔轨道
        KeplerianOrbit orbit = new KeplerianOrbit(cartesianOrbit);  // 转换为开普勒轨道
        System.out.println("半长轴：" + orbit.getA());
        System.out.println("离心率:" + orbit.getE());
        System.out.println("轨道倾角：" + Math.toDegrees(orbit.getI()));
        System.out.println("升交点赤经：" + Math.toDegrees(orbit.getRightAscensionOfAscendingNode())); // 弧度转化为度
        System.out.println("近地点幅角:" + Math.toDegrees(orbit.getPerigeeArgument()));
        System.out.println("真近点角" + Math.toDegrees(orbit.getTrueAnomaly()));

        String line1 = "1 33331U 08042A   24099.96293083  .00000688  00000-0  13892-3 0  9999";
        String line2 = "2 33331  98.0815 173.4498 0007530  33.2757 326.8918 14.64429237833164";

        Date now = new Date();
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(now);

        // 前一周
        calendar.add(Calendar.WEEK_OF_YEAR, -1);
        Date startTime = calendar.getTime();

        // 后一周
        calendar.setTime(now);
        calendar.add(Calendar.WEEK_OF_YEAR, 1);
        Date endWeek = calendar.getTime();
        String zoneStr = " {\"type\":\"MultiPolygon\",\"coordinates\":[[[[113.300423,22.877056],[113.301304,22.874951],[113.29811,22.873102],[113.301337,22.866117],[113.296325,22.862504],[113.297416,22.860214],[113.29681,22.857824],[113.29985,22.859048],[113.301018,22.855591],[113.303804,22.852077],[113.305622,22.851038],[113.309653,22.851195],[113.310788,22.83445],[113.312098,22.830395],[113.314411,22.828189],[113.318178,22.826254],[113.329369,22.82328],[113.332244,22.821687],[113.335967,22.817944],[113.344977,22.820207],[113.346078,22.818812],[113.350407,22.818072],[113.352555,22.81877],[113.352522,22.821658],[113.364263,22.823494],[113.36803,22.821587],[113.373384,22.822697],[113.375575,22.822227],[113.38398,22.816479],[113.387196,22.812594],[113.39343,22.809847],[113.39191,22.8073],[113.388749,22.804895],[113.383032,22.799302],[113.376071,22.795986],[113.374683,22.794563],[113.371852,22.796883],[113.36336,22.793538],[113.360265,22.792955],[113.357875,22.793923],[113.356542,22.792969],[113.35771,22.787034],[113.361499,22.777014],[113.365156,22.772601],[113.381457,22.761569],[113.391073,22.755704],[113.404279,22.747247],[113.412188,22.742819],[113.417883,22.740413],[113.426143,22.738007],[113.441123,22.737224],[113.447776,22.735857],[113.467977,22.728524],[113.464816,22.720963],[113.471314,22.714997],[113.491912,22.699787],[113.52338,22.679305],[113.540717,22.6662],[113.539506,22.657938],[113.533084,22.656371],[113.532974,22.654989],[113.536884,22.647523],[113.544749,22.635184],[113.561634,22.607511],[113.565137,22.603477],[113.567395,22.605929],[113.571922,22.602808],[113.574719,22.605715],[113.578552,22.604618],[113.580976,22.602095],[113.589986,22.595197],[113.594667,22.594399],[113.599623,22.594399],[113.615011,22.585291],[113.620782,22.579532],[113.63932,22.548283],[113.651612,22.515714],[113.66201,22.514787],[113.682343,22.51436],[113.70598,22.516285],[113.728186,22.521989],[113.737625,22.527665],[113.740467,22.534267],[113.716852,22.645201],[113.685284,22.717731],[113.648132,22.76174],[113.626014,22.786749],[113.612081,22.802291],[113.59763,22.817418],[113.584027,22.831305],[113.577594,22.841194],[113.571228,22.85313],[113.571007,22.856956],[113.571096,22.865876],[113.572616,22.874752],[113.574279,22.881124],[113.575105,22.888334],[113.574279,22.891933],[113.570545,22.898603],[113.564311,22.906922],[113.550498,22.936212],[113.546357,22.946207],[113.541797,22.959385],[113.537347,22.965085],[113.533646,22.971509],[113.529229,22.98261],[113.526189,22.995656],[113.523017,23.01133],[113.521541,23.028337],[113.521695,23.030213],[113.522896,23.037274],[113.526211,23.043084],[113.531994,23.050954],[113.540574,23.057957],[113.543724,23.062289],[113.545993,23.06905],[113.549,23.076024],[113.556292,23.081236],[113.562471,23.082017],[113.570336,23.084204],[113.586417,23.087797],[113.59611,23.092072],[113.601408,23.095395],[113.605395,23.100393],[113.610396,23.103787],[113.628592,23.103404],[113.640278,23.103886],[113.644398,23.10897],[113.642845,23.110872],[113.642702,23.113485],[113.646325,23.11526],[113.64844,23.117744],[113.651249,23.119292],[113.655258,23.117191],[113.657098,23.118596],[113.661228,23.117972],[113.660644,23.112093],[113.66201,23.111455],[113.670701,23.116424],[113.681891,23.117759],[113.687806,23.119789],[113.692267,23.1227],[113.701332,23.130281],[113.705342,23.13207],[113.716929,23.138912],[113.724375,23.141226],[113.7291,23.142049],[113.738595,23.141311],[113.754048,23.129571],[113.757286,23.129401],[113.77513,23.131047],[113.781023,23.13065],[113.785462,23.128421],[113.791266,23.127683],[113.80455,23.12906],[113.814673,23.127768],[113.826106,23.123125],[113.832814,23.119888],[113.837704,23.118483],[113.841075,23.116154],[113.841559,23.122245],[113.843531,23.123012],[113.843255,23.125709],[113.844687,23.12561],[113.844258,23.129812],[113.845778,23.131999],[113.8458,23.135392],[113.849082,23.14151],[113.848069,23.144392],[113.849853,23.148863],[113.856131,23.1521],[113.856726,23.155251],[113.85872,23.157252],[113.867697,23.159083],[113.869834,23.160176],[113.870924,23.16293],[113.874779,23.165357],[113.886356,23.164817],[113.890233,23.162944],[113.895674,23.164548],[113.899121,23.168621],[113.90149,23.173333],[113.90215,23.177179],[113.898494,23.178995],[113.893185,23.177264],[113.888977,23.178626],[113.885397,23.185779],[113.88564,23.188631],[113.883811,23.192647],[113.885276,23.194903],[113.886356,23.199657],[113.888294,23.20258],[113.891819,23.204226],[113.894,23.212654],[113.898174,23.21149],[113.902448,23.211576],[113.90377,23.21254],[113.904442,23.217463],[113.903814,23.219733],[113.900807,23.221195],[113.89867,23.224415],[113.8952,23.231352],[113.893185,23.237665],[113.890112,23.242133],[113.89064,23.246701],[113.894121,23.249949],[113.895167,23.253552],[113.893107,23.256076],[113.888349,23.257821],[113.884439,23.261097],[113.877202,23.264218],[113.876597,23.266132],[113.878084,23.268203],[113.88478,23.274017],[113.890288,23.282682],[113.891158,23.286667],[113.888503,23.290481],[113.891279,23.297287],[113.891984,23.303923],[113.894749,23.309197],[113.895024,23.314698],[113.898207,23.320553],[113.893636,23.321744],[113.892447,23.326309],[113.889572,23.329186],[113.889208,23.333567],[113.892381,23.338471],[113.893669,23.342242],[113.895993,23.345076],[113.901424,23.346919],[113.90497,23.346876],[113.910753,23.343602],[113.927473,23.339945],[113.935536,23.342723],[113.93927,23.34295],[113.94188,23.339889],[113.946583,23.336784],[113.95241,23.335679],[113.958876,23.332617],[113.960098,23.330136],[113.961277,23.324579],[113.958468,23.314953],[113.962202,23.312444],[113.968679,23.309991],[113.975563,23.306376],[113.977435,23.304844],[113.978228,23.301059],[113.982205,23.301229],[113.985134,23.300463],[113.988868,23.298181],[113.994585,23.298507],[113.9967,23.297457],[113.995742,23.303597],[113.99616,23.309339],[113.992933,23.313039],[113.992107,23.316116],[113.98856,23.319858],[113.988924,23.325047],[113.988373,23.327443],[113.984209,23.330491],[113.983614,23.332588],[113.986754,23.331823],[113.989948,23.333425],[113.993913,23.333155],[113.997195,23.338684],[114.001315,23.343191],[114.00094,23.346621],[113.992437,23.352163],[113.990576,23.354828],[113.990212,23.359576],[113.989199,23.362807],[113.98693,23.365854],[113.985917,23.369283],[113.981995,23.374994],[113.98118,23.377658],[113.98205,23.379359],[113.987756,23.383171],[113.992834,23.38582],[113.995984,23.38677],[113.999123,23.390397],[114.000434,23.393401],[113.999861,23.396645],[113.997394,23.399125],[113.986765,23.405614],[113.984275,23.408476],[113.984319,23.412669],[113.986467,23.416466],[113.98422,23.421225],[113.984452,23.423308],[113.987205,23.425971],[113.987954,23.431424],[113.984507,23.433138],[113.979704,23.430504],[113.975354,23.429399],[113.967974,23.431396],[113.96316,23.431028],[113.960021,23.432756],[113.958788,23.434866],[113.954029,23.440022],[113.952807,23.442897],[113.954558,23.449568],[113.953137,23.454553],[113.953468,23.463914],[113.95545,23.466039],[113.961024,23.464566],[113.969934,23.463914],[113.974494,23.464891],[113.981709,23.472156],[113.97586,23.477905],[113.97412,23.478811],[113.969009,23.47765],[113.964416,23.480156],[113.961068,23.480369],[113.955737,23.477877],[113.950306,23.478585],[113.945889,23.478372],[113.940911,23.476701],[113.938719,23.47673],[113.93298,23.479717],[113.931174,23.483059],[113.932617,23.484206],[113.938036,23.481884],[113.940878,23.483597],[113.946683,23.492517],[113.945328,23.493664],[113.939611,23.494938],[113.929973,23.494216],[113.927759,23.496453],[113.92657,23.500304],[113.923078,23.503079],[113.915875,23.502979],[113.911513,23.504169],[113.910081,23.505514],[113.906391,23.511842],[113.900344,23.514928],[113.89748,23.519075],[113.89313,23.520278],[113.890497,23.523605],[113.889098,23.527625],[113.889979,23.532012],[113.88814,23.53507],[113.883789,23.537009],[113.879229,23.537617],[113.874878,23.53899],[113.870142,23.543038],[113.867752,23.552194],[113.867994,23.555039],[113.864525,23.559638],[113.864348,23.562114],[113.86252,23.566317],[113.857574,23.56902],[113.852816,23.570576],[113.853279,23.574566],[113.856065,23.580622],[113.857971,23.58307],[113.864051,23.587385],[113.860141,23.590257],[113.858323,23.593497],[113.859216,23.59784],[113.861804,23.603895],[113.859623,23.609963],[113.84602,23.616909],[113.843542,23.617885],[113.838134,23.616979],[113.834179,23.617588],[113.83256,23.619879],[113.833651,23.622708],[113.832219,23.624235],[113.825147,23.624702],[113.817437,23.623458],[113.815631,23.627842],[113.813769,23.629016],[113.817834,23.636752],[113.823462,23.638011],[113.825566,23.639878],[113.826877,23.644036],[113.828364,23.645916],[113.827769,23.648024],[113.822361,23.648844],[113.81866,23.654019],[113.818781,23.656169],[113.825951,23.656041],[113.834102,23.654811],[113.839951,23.655448],[113.842022,23.660538],[113.843916,23.669474],[113.85275,23.667763],[113.852816,23.670323],[113.844533,23.674847],[113.847837,23.679329],[113.862575,23.681082],[113.867994,23.683245],[113.875782,23.683485],[113.881851,23.685125],[113.887314,23.688094],[113.891312,23.691289],[113.894209,23.696449],[113.897579,23.700167],[113.899276,23.707446],[113.89878,23.711871],[113.900939,23.715433],[113.909993,23.715758],[113.91235,23.716507],[113.915897,23.721637],[113.920049,23.729453],[113.924191,23.729297],[113.932815,23.732689],[113.936417,23.732166],[113.937893,23.733184],[113.937606,23.735431],[113.940294,23.738144],[113.949062,23.737112],[113.952641,23.73365],[113.95588,23.732689],[113.961035,23.738201],[113.966046,23.739727],[113.970441,23.73837],[113.972864,23.739246],[113.9764,23.747259],[113.975673,23.756768],[113.979032,23.759425],[113.982987,23.760555],[113.992338,23.760908],[113.998793,23.763013],[114.000874,23.764554],[114.009356,23.773257],[114.010479,23.776195],[114.013123,23.777961],[114.017716,23.778286],[114.015733,23.772183],[114.011503,23.769823],[114.00995,23.763013],[114.014521,23.762378],[114.018211,23.762844],[114.018927,23.758139],[114.021185,23.75609],[114.023124,23.752247],[114.024897,23.752572],[114.02742,23.760117],[114.035725,23.765288],[114.035956,23.768538],[114.038456,23.771222],[114.044074,23.774288],[114.047609,23.775249],[114.058007,23.776605],[114.059957,23.77587],[114.056344,23.784036],[114.053855,23.786141],[114.046332,23.787596],[114.037201,23.793528],[114.037201,23.798317],[114.04306,23.79997],[114.047752,23.803331],[114.042939,23.80699],[114.037608,23.808035],[114.03535,23.813345],[114.038225,23.821579],[114.040318,23.824446],[114.045428,23.826833],[114.048028,23.828824],[114.052268,23.830024],[114.051916,23.832665],[114.052698,23.839923],[114.054394,23.840615],[114.055231,23.845599],[114.057765,23.846757],[114.054912,23.850372],[114.05109,23.85352],[114.051112,23.855808],[114.048821,23.85866],[114.048887,23.861738],[114.047444,23.865818],[114.047708,23.867512],[114.045032,23.870773],[114.041849,23.871507],[114.041001,23.875375],[114.043743,23.880231],[114.043633,23.884833],[114.042653,23.889872],[114.038269,23.892032],[114.036385,23.895264],[114.036209,23.901771],[114.03318,23.903732],[114.031341,23.906315],[114.031297,23.910225],[114.033665,23.914811],[114.033819,23.918876],[114.032938,23.920386],[114.03003,23.921133],[114.02818,23.922968],[114.026252,23.922897],[114.022199,23.92469],[114.017969,23.92524],[114.012572,23.929826],[114.009994,23.929741],[114.009212,23.932916],[114.005688,23.932535],[114.002295,23.929516],[113.997328,23.929135],[113.994045,23.930263],[113.984518,23.926171],[113.982116,23.929699],[113.969538,23.928542],[113.971146,23.930757],[113.96945,23.932563],[113.963414,23.928189],[113.959404,23.930221],[113.952696,23.929657],[113.946826,23.925282],[113.942519,23.925212],[113.941164,23.923561],[113.935932,23.926016],[113.936417,23.928725],[113.933531,23.929233],[113.929092,23.92785],[113.92635,23.928838],[113.921206,23.927639],[113.914564,23.924238],[113.910235,23.923561],[113.90779,23.924563],[113.904309,23.92905],[113.900697,23.929445],[113.898879,23.928443],[113.894848,23.928584],[113.892524,23.931675],[113.889208,23.928429],[113.888955,23.926058],[113.885827,23.923659],[113.880826,23.926058],[113.879824,23.930376],[113.875319,23.930475],[113.873612,23.929022],[113.865208,23.928951],[113.862377,23.927766],[113.857718,23.923829],[113.853279,23.921218],[113.848653,23.919765],[113.841526,23.91872],[113.833287,23.91522],[113.826513,23.909801],[113.817239,23.906132],[113.81356,23.902702],[113.809452,23.900613],[113.807326,23.900387],[113.803768,23.903408],[113.800243,23.902547],[113.795033,23.902801],[113.791597,23.903902],[113.78761,23.902462],[113.786629,23.899075],[113.781133,23.896294],[113.780869,23.89237],[113.777983,23.883647],[113.775317,23.87793],[113.771727,23.873977],[113.767056,23.871436],[113.764732,23.866961],[113.762078,23.863686],[113.758168,23.857488],[113.755524,23.857629],[113.749059,23.854353],[113.745545,23.858307],[113.742307,23.859154],[113.736821,23.855271],[113.733671,23.855017],[113.731303,23.857742],[113.727822,23.858166],[113.728175,23.855243],[113.724265,23.853873],[113.725509,23.857798],[113.722844,23.86041],[113.720354,23.86082],[113.718845,23.858886],[113.713525,23.8625],[113.710045,23.858688],[113.709758,23.856288],[113.713536,23.854241],[113.718063,23.843905],[113.717215,23.838709],[113.714803,23.834995],[113.716863,23.827299],[113.7202,23.825124],[113.718548,23.82076],[113.715761,23.820901],[113.706388,23.815266],[113.702445,23.81617],[113.700616,23.819531],[113.697907,23.820703],[113.693545,23.821085],[113.689679,23.825618],[113.687366,23.825731],[113.683984,23.817498],[113.684293,23.812964],[113.681396,23.812018],[113.679292,23.813769],[113.670216,23.814546],[113.666394,23.813684],[113.662429,23.817695],[113.659212,23.817512],[113.658166,23.818896],[113.653672,23.816763],[113.653628,23.818966],[113.651667,23.820139],[113.647328,23.818585],[113.640234,23.814263],[113.637789,23.811396],[113.637271,23.806255],[113.63433,23.8048],[113.632755,23.800605],[113.633714,23.797215],[113.620298,23.786819],[113.616751,23.78792],[113.615374,23.785378],[113.618469,23.785858],[113.620827,23.783923],[113.618965,23.781606],[113.615154,23.779812],[113.616145,23.776421],[113.61967,23.771279],[113.626114,23.767393],[113.62977,23.759382],[113.631599,23.752996],[113.63639,23.75024],[113.633967,23.744305],[113.630729,23.738964],[113.634242,23.733438],[113.636522,23.731968],[113.631654,23.727884],[113.63205,23.71833],[113.628052,23.716408],[113.628118,23.711701],[113.630288,23.708945],[113.63357,23.70821],[113.638197,23.704577],[113.636324,23.701792],[113.631202,23.702768],[113.6251,23.701397],[113.622589,23.699432],[113.623811,23.69471],[113.620882,23.691954],[113.61457,23.689409],[113.61413,23.687585],[113.611629,23.686186],[113.612477,23.683443],[113.615727,23.680205],[113.606375,23.673264],[113.599381,23.665487],[113.598985,23.662984],[113.597277,23.664936],[113.587243,23.669008],[113.587287,23.675229],[113.580689,23.67571],[113.573915,23.680714],[113.568045,23.679442],[113.567053,23.68333],[113.56962,23.686087],[113.568067,23.690215],[113.563518,23.691346],[113.564421,23.695502],[113.558759,23.70069],[113.54693,23.702527],[113.542909,23.701806],[113.53836,23.699107],[113.543317,23.699177],[113.545465,23.696392],[113.532677,23.689508],[113.527081,23.686143],[113.524184,23.685408],[113.521255,23.686313],[113.517917,23.685493],[113.516441,23.683104],[113.513699,23.682086],[113.510306,23.682453],[113.506958,23.68439],[113.501693,23.682793],[113.495646,23.682298],[113.491934,23.685323],[113.488541,23.684829],[113.483596,23.686695],[113.481029,23.684037],[113.477141,23.685281],[113.468704,23.690992],[113.469673,23.693],[113.469618,23.69652],[113.465796,23.700294],[113.464309,23.703107],[113.466148,23.705326],[113.464298,23.707969],[113.456962,23.710245],[113.45477,23.709962],[113.44782,23.714839],[113.443855,23.715913],[113.444296,23.720266],[113.442467,23.720224],[113.440319,23.722457],[113.44445,23.723559],[113.444538,23.725029],[113.441905,23.727036],[113.438447,23.727107],[113.430351,23.723107],[113.426044,23.726584],[113.423775,23.726782],[113.419006,23.724535],[113.404313,23.723503],[113.400997,23.724478],[113.397836,23.730569],[113.391403,23.72917],[113.384035,23.729721],[113.378351,23.73153],[113.377007,23.729735],[113.376831,23.726358],[113.37508,23.723107],[113.373284,23.722853],[113.375752,23.716309],[113.375388,23.712818],[113.372381,23.709736],[113.369363,23.709086],[113.366323,23.710288],[113.363713,23.707164],[113.362016,23.702414],[113.360717,23.696322],[113.355661,23.687006],[113.355,23.681096],[113.350594,23.675398],[113.350352,23.671779],[113.349074,23.667976],[113.347411,23.667212],[113.339921,23.666675],[113.338445,23.665727],[113.336518,23.65812],[113.334843,23.656409],[113.327959,23.655476],[113.327541,23.654132],[113.328752,23.649381],[113.328301,23.645379],[113.323708,23.643286],[113.317672,23.64388],[113.315359,23.643131],[113.308904,23.643908],[113.302857,23.64603],[113.289265,23.644361],[113.288835,23.641504],[113.299454,23.637459],[113.296513,23.632905],[113.295235,23.629469],[113.293737,23.621845],[113.290565,23.617093],[113.282821,23.61217],[113.281544,23.609497],[113.280079,23.608931],[113.278239,23.614094],[113.276565,23.616003],[113.274461,23.614292],[113.270287,23.61316],[113.266927,23.609369],[113.261905,23.607672],[113.25773,23.607686],[113.253545,23.605606],[113.248478,23.601589],[113.24296,23.606993],[113.240382,23.606243],[113.244535,23.598208],[113.243863,23.59651],[113.245901,23.591997],[113.245834,23.588234],[113.24318,23.586649],[113.240625,23.590201],[113.23743,23.590512],[113.233113,23.593256],[113.227892,23.594417],[113.227275,23.592818],[113.226988,23.58573],[113.221558,23.584032],[113.214487,23.584258],[113.212207,23.583325],[113.202239,23.576519],[113.203373,23.572515],[113.201523,23.56636],[113.200774,23.561831],[113.208087,23.555704],[113.210158,23.553015],[113.206281,23.548048],[113.211931,23.543915],[113.21268,23.540278],[113.210654,23.537164],[113.206138,23.538849],[113.20529,23.537094],[113.19987,23.531913],[113.197227,23.530583],[113.195817,23.526351],[113.191125,23.523208],[113.194209,23.519174],[113.194396,23.516881],[113.19205,23.514772],[113.175947,23.513215],[113.172103,23.512365],[113.172323,23.520292],[113.170087,23.516683],[113.1711,23.511558],[113.169437,23.513073],[113.165252,23.50884],[113.162487,23.510157],[113.159083,23.509024],[113.158401,23.505075],[113.153543,23.502838],[113.145734,23.506349],[113.147287,23.508203],[113.138101,23.50918],[113.128672,23.51262],[113.123517,23.508486],[113.119277,23.506731],[113.115333,23.510327],[113.115025,23.508076],[113.117757,23.504692],[113.115444,23.501507],[113.109033,23.497968],[113.107403,23.498987],[113.102942,23.497557],[113.101334,23.495363],[113.09304,23.49692],[113.089086,23.493027],[113.083964,23.494244],[113.077675,23.488779],[113.075516,23.484234],[113.069171,23.48439],[113.064413,23.482804],[113.061064,23.47908],[113.060304,23.475951],[113.055844,23.471958],[113.048838,23.469975],[113.042945,23.474124],[113.038683,23.474464],[113.037647,23.47159],[113.028571,23.470273],[113.026357,23.47285],[113.018052,23.469055],[113.019815,23.467639],[113.018879,23.465755],[113.014131,23.465061],[113.016764,23.461818],[113.015938,23.460586],[113.011785,23.464835],[113.004692,23.462045],[113.001343,23.461932],[112.998017,23.463135],[112.99338,23.466832],[112.987509,23.463815],[112.984469,23.464764],[112.978774,23.464792],[112.975118,23.46349],[112.975745,23.459085],[112.974666,23.454313],[112.97167,23.448166],[112.968289,23.445843],[112.965601,23.446608],[112.9619,23.444455],[112.96038,23.437869],[112.958552,23.432614],[112.958508,23.429696],[112.960204,23.426013],[112.963387,23.426424],[112.967936,23.432373],[112.970183,23.433818],[112.97417,23.434073],[112.976109,23.436099],[112.978631,23.44158],[112.98513,23.443336],[112.986419,23.444356],[112.989117,23.443294],[112.988688,23.440036],[112.982795,23.434456],[112.982685,23.431439],[112.985581,23.42896],[112.988666,23.419795],[112.99089,23.417344],[112.992697,23.417401],[112.995473,23.41104],[112.998457,23.409836],[112.998149,23.408348],[113.00109,23.406322],[113.00022,23.40526],[112.995902,23.40543],[112.993732,23.402228],[112.990571,23.402639],[112.989701,23.40057],[112.986826,23.399182],[112.986077,23.395824],[112.988236,23.393287],[112.988346,23.390114],[112.985174,23.389717],[112.983301,23.386784],[112.986198,23.385537],[112.985835,23.383567],[112.982156,23.382575],[112.980658,23.38028],[112.984777,23.377233],[112.980438,23.373804],[112.981517,23.371451],[112.983147,23.371437],[112.985747,23.369042],[112.986771,23.365599],[112.986529,23.357591],[112.989844,23.354728],[112.996926,23.355494],[113.000253,23.355111],[113.005859,23.352432],[113.010232,23.354147],[113.012578,23.352588],[113.012843,23.348733],[113.015299,23.34227],[113.018306,23.341363],[113.019462,23.344127],[113.023637,23.346437],[113.02638,23.349187],[113.027481,23.351653],[113.032702,23.356245],[113.034795,23.357393],[113.040809,23.356061],[113.043397,23.351114],[113.034189,23.339591],[113.029364,23.33582],[113.027569,23.333496],[113.025047,23.327273],[113.023472,23.324905],[113.024893,23.323275],[113.030069,23.32],[113.031215,23.321744],[113.037548,23.320071],[113.038606,23.316243],[113.036072,23.315379],[113.035059,23.313068],[113.032493,23.300889],[113.032636,23.29767],[113.035015,23.297684],[113.038033,23.292821],[113.038672,23.290637],[113.036458,23.289985],[113.042549,23.283448],[113.045809,23.281505],[113.048266,23.281576],[113.051427,23.278399],[113.051911,23.276428],[113.051394,23.269451],[113.053068,23.268231],[113.052715,23.263423],[113.05092,23.263296],[113.050744,23.259495],[113.044895,23.252219],[113.046988,23.250148],[113.053508,23.253438],[113.057584,23.251098],[113.063179,23.252048],[113.07024,23.248786],[113.079778,23.250105],[113.081651,23.25297],[113.081904,23.261736],[113.079029,23.267479],[113.076981,23.270146],[113.075339,23.276258],[113.071429,23.281987],[113.072266,23.28427],[113.081486,23.284951],[113.082609,23.286255],[113.086178,23.285291],[113.089009,23.286582],[113.090551,23.289956],[113.093051,23.291105],[113.097842,23.291275],[113.105552,23.289744],[113.110245,23.295983],[113.108416,23.301527],[113.105751,23.302732],[113.113219,23.309863],[113.114188,23.306673],[113.124696,23.307638],[113.127989,23.314542],[113.134741,23.307992],[113.140975,23.303781],[113.146207,23.30303],[113.150018,23.299131],[113.150591,23.289971],[113.153466,23.287092],[113.16089,23.286667],[113.16274,23.28532],[113.163346,23.280924],[113.164569,23.279009],[113.173446,23.277052],[113.177753,23.271294],[113.181399,23.25697],[113.181994,23.251438],[113.181135,23.245722],[113.179185,23.237537],[113.176542,23.236587],[113.176057,23.230969],[113.178062,23.228132],[113.177478,23.220883],[113.184362,23.21525],[113.191103,23.214966],[113.19357,23.210937],[113.196236,23.208071],[113.202239,23.2038],[113.204904,23.201161],[113.209002,23.192179],[113.208969,23.186645],[113.208032,23.183452],[113.209453,23.177108],[113.207063,23.173532],[113.197899,23.169629],[113.192711,23.165186],[113.187732,23.159111],[113.186884,23.15552],[113.186851,23.148253],[113.188503,23.145187],[113.192006,23.143455],[113.203483,23.143398],[113.211656,23.142617],[113.212669,23.141098],[113.211601,23.12889],[113.209718,23.121848],[113.203715,23.121876],[113.203296,23.119775],[113.205246,23.109509],[113.206788,23.107649],[113.208142,23.099683],[113.213848,23.099797],[113.215809,23.100677],[113.216348,23.096772],[113.213099,23.095665],[113.213264,23.092682],[113.212075,23.090978],[113.212504,23.088323],[113.209585,23.086903],[113.208814,23.083551],[113.195233,23.084105],[113.193658,23.083878],[113.183415,23.077856],[113.177412,23.077558],[113.178866,23.07476],[113.177566,23.069576],[113.180606,23.06317],[113.184836,23.059647],[113.188184,23.058241],[113.195762,23.056167],[113.20334,23.050599],[113.209222,23.044476],[113.211689,23.043311],[113.225744,23.041862],[113.233674,23.042857],[113.24535,23.043368],[113.254932,23.044831],[113.256122,23.043908],[113.254844,23.038495],[113.256959,23.03081],[113.258722,23.022058],[113.263127,23.021134],[113.258391,23.014001],[113.25393,23.012168],[113.250009,23.009042],[113.249458,23.004211],[113.250317,22.999777],[113.251804,22.998456],[113.256695,22.997788],[113.257895,22.99486],[113.251793,22.990213],[113.251011,22.988195],[113.251606,22.982624],[113.249987,22.978914],[113.249932,22.973286],[113.251892,22.970344],[113.257235,22.965739],[113.264097,22.962114],[113.267588,22.958802],[113.267049,22.95495],[113.272919,22.95748],[113.276411,22.954267],[113.281632,22.952704],[113.2878,22.949064],[113.291545,22.944913],[113.29638,22.93789],[113.298198,22.934037],[113.290179,22.930838],[113.288659,22.928549],[113.285002,22.929416],[113.282392,22.927383],[113.283615,22.923786],[113.285068,22.913733],[113.284308,22.91079],[113.284562,22.90678],[113.285961,22.901433],[113.278636,22.901091],[113.277413,22.90011],[113.277028,22.894706],[113.278834,22.891563],[113.282216,22.890084],[113.300423,22.877056]]]]}";

        List<ZoneDetectResult> list = forecastSatelliteOverZone(line1, line2, startTime, endWeek, parseGeoJson(zoneStr));
        for (ZoneDetectResult result : list) {
            System.out.println(result);
            System.out.println(result.toString());
        }


        Frame frame = orbit.getFrame();

        Frame J2000 = FramesFactory.getEME2000();
        Frame ecff = FramesFactory.getITRF(IERSConventions.IERS_2010, true);
//        KeplerianPropagator propagator = new KeplerianPropagator(orbit);
        Propagator propagator = new EcksteinHechlerPropagator(orbit, 2000, Constants.WGS84_EARTH_EQUATORIAL_RADIUS, Constants.WGS84_EARTH_MU, Constants.WGS84_EARTH_C20, 0, 0, 0, 0);
        double stepSize = 60.0d;

        AbsoluteDate date1 = orbit.getDate();
//        AbsoluteDate startDate = new AbsoluteDate(2024, 4, 1, 0, 0, 0, TimeScalesFactory.getUTC());
        AbsoluteDate endDate = new AbsoluteDate(2024, 5, 7, 15, 30, 8, TimeScalesFactory.getUTC());
        AbsoluteDate date = endDate;
//        PVCoordinates pvCoordinates = orbit.getPVCoordinates(J2000);
        PVCoordinates pvCoordinates = propagator.getPVCoordinates(date, frame);
        handleStep(propagator.getInitialState(), pvCoordinates, true);
        toLatLon(frame, date, pvCoordinates);
        Vector3D position = pvCoordinates.getPosition();
        System.out.println("日期：" + date + "位置:" + position);
//        for (AbsoluteDate date = startDate; date.compareTo(endDate) <= 0; date = date.shiftedBy(stepSize)) {
//            PVCoordinates pvCoordinates = propagator.getPVCoordinates(date, frame);
//            handleStep(propagator.getInitialState(),pvCoordinates,true);
////            toLatLon(frame, date, pvCoordinates);
////            Vector3D position = pvCoordinates.getPosition();
////            System.out.println("日期："+date+"位置:"+position);
//        }
    }

    public static Geometry parseGeoJson(Object zoneObj) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            GeometryJSON gjson = new GeometryJSON();
            if (zoneObj instanceof String s) {
                return gjson.read(s);
            } else {
                // 如果已经是对象，转换为 JSON 字符串再解析
                String geoJsonStr = mapper.writeValueAsString(zoneObj);
                return gjson.read(geoJsonStr);
            }
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid GeoJSON format: " + e.getMessage(), e);
        }
    }

    private static TLE getTle() {
        // GF2
        String tleStr = "1 40118U 14049A   24199.88954803  .00002886  00000-0  38577-3 0  9999\n" + "2 40118  98.1102 250.0495 0007187 330.2078  29.8724 14.80292361535773";
//        String line1 = "1 33331U 08042A   24099.96293083  .00000688  00000-0  13892-3 0  9999";
//        String line2 = "2 33331  98.0815 173.4498 0007530  33.2757 326.8918 14.64429237833164";
        String[] split = tleStr.split("\n");
        String line1 = split[0];
        String line2 = split[1];

        return new TLE(line1, line2);
    }

    public static void handleStep(SpacecraftState currentState, PVCoordinates pvCoordinates, boolean isLast) {
        try {
            GeodeticPoint pt = earthShape.transform(pvCoordinates.getPosition(), currentState.getFrame(), currentState.getDate());

            String str = String.format("%f,%f,%f", FastMath.toDegrees(pt.getLongitude()), FastMath.toDegrees(pt.getLatitude()), pt.getAltitude());
            System.out.println(str);
//            System.out.println(String.format("%f,%f,%f",pt.getLongitude(),pt.getLatitude(),pt.getAltitude()));
        } catch (OrekitException ex) {
            ex.printStackTrace();
        }
    }

    /**
     * 转换为经纬度
     */
    private static void toLatLon(Frame frame, AbsoluteDate date, PVCoordinates pvCoordinates) {
        // 转换到ECFF坐标系
        PVCoordinates pvInECFF = frame.getTransformTo(ECFF, date).transformPVCoordinates(pvCoordinates);


        // 获取地球中心的坐标
        double x = pvInECFF.getPosition().getX();
        double y = pvInECFF.getPosition().getY();
        double z = pvInECFF.getPosition().getZ();

        // 计算经纬度
        double longitude = Math.atan2(y, x);
        double latitude = Math.atan2(z, Math.sqrt(x * x + y * y));
        double altitude = Math.sqrt(x * x + y * y + z * z) - Constants.WGS84_EARTH_EQUATORIAL_RADIUS;
        System.out.println("longitude:" + longitude + " latitude:" + latitude + " altitude:" + altitude);
    }

    public static void getInfo() {
        // 定义J2000坐标系和ECFF坐标系
        // 定义J2000坐标系
        Frame J2000 = FramesFactory.getEME2000();
        // 定义ECFF坐标系
        Frame ecff = FramesFactory.getITRF(IERSConventions.IERS_2010, true);

        // 定义地心引力常数
        // gravitation coefficient
        final double mu = 3.986004415e+14;

        // 定义卫星基本参数并计算卫星的PV坐标
        // 轨道六根数
        double a = 6931700.6; // 半长轴 (m)
        double e = 0.001264070355890773;           // 离心率
        double i = 0.9262838362266536; // 轨道倾角 (弧度)
        double argOfPerigee = -64.04849998651345; // 升交点赤经 (弧度)
        double raan = 52.248882667192056;  // 升交点赤纬 (弧度)
        double meanAnomaly = -0.9119147488086196; // 真近点角 (弧度)
        // 定义时刻
        final TimeScale utc = TimeScalesFactory.getUTC();
        final AbsoluteDate initialDate = new AbsoluteDate(2024, 03, 12, 19, 16, 01.10, utc);
        // 计算PV坐标
        final Orbit initialOrbit = new KeplerianOrbit(a, e, i, argOfPerigee, raan, meanAnomaly, PositionAngleType.MEAN, J2000, initialDate, mu);

        // 转换到J2000坐标系
        // 转换到J2000坐标系
        PVCoordinates pvCoordinates = initialOrbit.getPVCoordinates(J2000);

        // 转换到ECFF坐标系
        PVCoordinates pvInECFF = J2000.getTransformTo(ecff, initialDate).transformPVCoordinates(pvCoordinates);


        // 获取地球中心的坐标
        double x = pvInECFF.getPosition().getX();
        double y = pvInECFF.getPosition().getY();
        double z = pvInECFF.getPosition().getZ();

        // 计算经纬度
        double longitude = Math.atan2(y, x);
        double latitude = Math.atan2(z, Math.sqrt(x * x + y * y));
        double altitude = Math.sqrt(x * x + y * y + z * z) - Constants.WGS84_EARTH_EQUATORIAL_RADIUS;


        System.out.println("经度 (degrees): " + longitude);

        System.out.println("纬度 (degrees): " + latitude);

        System.out.println("高度 (meters): " + altitude);

        getDistanceToSun(longitude, latitude, altitude);
    }


    /**
     * 计算位置与太阳表面的距离
     *
     * @param longitude 经度 单位：弧度
     * @param latitude  纬度 单位：弧度
     * @param altitude  高度 单位：米
     */
    public static void getDistanceToSun(double longitude, double latitude, double altitude) {
        /* 经度，以弧度表示 *//*
        double longitude = Math.toRadians();
        *//* 纬度，以弧度表示 *//*
        double latitude = Math.toRadians();
        *//* 高度，以米为单位 *//*
        double altitude =  ;*/

        double earthRadius = 6371000; // 以米为单位

        // 计算太阳中心到地球中心的距离
        double sunEarthDistance = 149.6e9; // 以米为单位

        // 计算太阳的视直径
        double sunDiameter = 2 * Math.asin(696340e3 / (2 * sunEarthDistance)); // 以弧度为单位

        // 计算从位置到太阳表面的距离
        // 计算了从给定位置到太阳表面的距离。它利用了地球表面的位置与太阳表面的位置之间的直线距离，以及地球和太阳之间的距离，以及给定位置的海拔高度。
        double distanceToSun = Math.sqrt(Math.pow((sunEarthDistance * Math.cos(sunDiameter / 2) - earthRadius * Math.cos(latitude)), 2) + Math.pow((sunEarthDistance * Math.sin(sunDiameter / 2) - earthRadius * Math.sin(latitude)), 2) + Math.pow(altitude, 2));
        // 经度（longitude）并未直接用于计算距离。经度通常用于确定位置的东西方向，但在这种情况下，并不影响计算太阳与地球的距离。相反，纬度（latitude）是在计算中用到的，因为它影响到了太阳在天空中的位置。
        // double distanceToSun = Math.sqrt(Math.pow((sunEarthDistance * Math.cos(sunDiameter / 2) - earthRadius * Math.cos(latitude)), 2) + Math.pow((sunEarthDistance * Math.sin(sunDiameter / 2) - earthRadius * Math.sin(latitude)), 2) + Math.pow(altitude, 2));

        System.out.println("位置与太阳表面的距离：" + distanceToSun);
    }

    /**
     * 三角形三个角的度数
     */
    public static void TriangleAngles() {
        double a = 3;
        double b = 4;
        double c = 5;

        double A = Math.toDegrees(Math.acos((b * b + c * c - a * a) / (2 * b * c)));
        double B = Math.toDegrees(Math.acos((a * a + c * c - b * b) / (2 * a * c)));
        double C = Math.toDegrees(Math.acos((a * a + b * b - c * c) / (2 * a * b)));

        // 角A由边b和边c组成，角B由边a和边c组成，角C由边a和边b组成。
        System.out.println("Angle A: " + A);
        System.out.println("Angle B: " + B);
        System.out.println("Angle C: " + C);
    }


    /**
     * 根据TLE预测卫星进入/离开地面区域情况
     *
     * @param tleLine1  tle 第一行
     * @param tleLine2  tle 第二行
     * @param startTime 预测日期时间区间开始
     * @param endTime   预测日期时间区间结束
     * @param centerLon 圆心经度
     * @param centerLat 圆心纬度
     * @param radiusKm  圆心半径
     * @param segments  多边形边数(越多越接近圆形)
     * @return
     */
    public static List<ZoneDetectResult> forecastSatelliteOverCircle(
        String tleLine1, String tleLine2,
        Date startTime, Date endTime,
        double centerLon, double centerLat, double radiusKm, int segments) {
        // 创建圆形多边形的坐标点
        List<List<Double>> circularPolygon = createCircularPolygon(centerLon, centerLat, radiusKm * 1000, segments);
        String geojson = com.htcloud.orbit.utils.GeoToolsUtil.createGeoJsonPolygonCompact(circularPolygon);
        return forecastSatelliteOverZone(tleLine1, tleLine2, startTime, endTime, parseGeoJson(geojson));
    }

    /**
     * @param tleLine1          tle 第一行
     * @param tleLine2          tle 第二行
     * @param startTime         预测日期时间区间开始
     * @param endTime           预测日期时间区间结束
     * @param centerLon         圆心经度
     * @param centerLat         圆心纬度
     * @param radiusKm          圆心半径
     * @param segments          多边形边数(越多越接近圆形)
     * @param orbitForecastSpec 卫星轨道参数
     * @return
     */
    public static List<ProSatelliteOrbitForecastResultItemDTO> forecastSatelliteOverCircleAndLine(
        String tleLine1, String tleLine2,
        Date startTime, Date endTime,
        double centerLon, double centerLat, double radiusKm, int segments, OrbitForecastSpecDto orbitForecastSpec) {
        // 创建圆形多边形的坐标点
        List<List<Double>> circularPolygon = createCircularPolygon(centerLon, centerLat, radiusKm * 1000, segments);
        for (List<Double> point : circularPolygon) {
            double lon = point.get(0);
            lon = normalizeLon(lon);
            point.set(0, lon);
        }
        String geojson = GeoToolsUtil.createGeoJsonPolygonCompact(circularPolygon);
        List<ZoneDetectResult> zoneDetectResultList = forecastSatelliteOverZone(tleLine1, tleLine2, startTime, endTime, parseGeoJson(geojson));
        List<ProSatelliteOrbitForecastResultItemDTO> list1 = OrbitHelper.forecastSatelliteOverZoneAndLine(
            zoneDetectResultList,
            startTime,
            endTime,
            geojson,
            orbitForecastSpec);
        if (list1.size() > 0) {
            for (ProSatelliteOrbitForecastResultItemDTO item : list1) {
                item.setZoneDetectResultList(zoneDetectResultList);
            }
        }
        return list1;
    }


    private static double normalizeLon(double lon) {
        if (lon > 180) {
            return 180;
        }
        if (lon < -180) {
            return -180;
        }
        return lon;
    }

    /**
     * 创建圆形多边形的坐标点（经纬度列表）
     *
     * @param centerLon    圆心经度
     * @param centerLat    圆心纬度
     * @param radiusMeters 圆心半径
     * @param segments     多边形边数(越多越接近圆形)
     * @return
     */
    public static List<List<Double>> createCircularPolygon(
        double centerLon, double centerLat, double radiusMeters, int segments) {
        List<List<Double>> polygon = new ArrayList<>();
        double earthRadius = Constants.WGS84_EARTH_EQUATORIAL_RADIUS;
        // 将半径转换为角度(弧度)
        double angularRadius = radiusMeters / earthRadius;

        double centerLonRad = FastMath.toRadians(centerLon);
        double centerLatRad = FastMath.toRadians(centerLat);

        for (int i = 0; i <= segments; i++) {
            double angle = 2 * Math.PI * i / segments;
            // 球面三角计算边界点坐标
            double lat = Math.asin(Math.sin(centerLatRad) * Math.cos(angularRadius) +
                Math.cos(centerLatRad) * Math.sin(angularRadius) * Math.cos(angle));
            double lon = centerLonRad + Math.atan2(Math.sin(angle) * Math.sin(angularRadius) * Math.cos(centerLatRad),
                Math.cos(angularRadius) - Math.sin(centerLatRad) * Math.sin(lat));

            List<Double> point = new ArrayList<>();
            point.add(FastMath.toDegrees(lon));
            point.add(FastMath.toDegrees(lat));
            polygon.add(point);
        }
        return polygon;
    }

    /**
     * 预测区域画线
     *
     * @param zoneDetectResults
     * @param startTime         预测日期时间区间开始
     * @param endTime           预测日期时间区间结束
     * @param zone
     * @param orbitForecastSpec
     * @return
     */
    public static List<ProSatelliteOrbitForecastResultItemDTO> forecastSatelliteOverZoneAndLine(List<ZoneDetectResult> zoneDetectResults,
                                                                                                Date startTime,
                                                                                                Date endTime,
                                                                                                Object zone,
                                                                                                OrbitForecastSpecDto orbitForecastSpec) {
        Geometry detectZone = parseGeoJson(zone);
        ArrayList<ProSatelliteOrbitForecastResultItemDTO> itemList = new ArrayList<>();
        if (zoneDetectResults.isEmpty()) {
            return itemList;
        }
        // 检查是否为矩形，如果是则使用球面矩形处理
        Geometry processedZone = processRectangularZone(detectZone);
//        Geometry processedZone = parseGeoJson(zone);
        for (ZoneDetectResult zoneDetectResult : zoneDetectResults) {
            // 安全获取轨迹点集合
            List<List<Double>> footprints = zoneDetectResult.getFootprints();
            CoordinateSequence coordinateSequence;

            // ============== 核心修复：过滤只有1个点的非法情况 ==============
            if (CollUtil.isEmpty(zoneDetectResult.getFootprints()) || zoneDetectResult.getFootprints().size() < 2) {
                continue;
            }

            // 正常构建坐标序列
            coordinateSequence = GeoToolsUtil.createCoordinateSequence2D(footprints.size());
            int i = 0;
            for (List<Double> footprint : footprints) {
                coordinateSequence.setOrdinate(i, CoordinateSequence.X, footprint.get(0));
                coordinateSequence.setOrdinate(i, CoordinateSequence.Y, footprint.get(1));
                i++;
            }


            ProSatelliteOrbitForecastResultItemDTO resultItemDTO = new ProSatelliteOrbitForecastResultItemDTO();
            BeanUtils.copyProperties(orbitForecastSpec, resultItemDTO);
            LineString lineString = GeoToolsUtil.getGeometryFactory().createLineString(coordinateSequence);
            Geometry geometry = GeoToolsUtil.createPolygonFromLine(orbitForecastSpec.getClothWidth() / 2.0, lineString);
            // 与指定面相交
            Geometry intersection;
            try {
                intersection = geometry.intersection(processedZone);
            } catch (TopologyException e) {
                LOGGER.warn("几何相交失败,尝试使用缓冲修复: {}", e.getMessage());
                try {
                    Geometry bufferedGeom = geometry.buffer(0.0001);
                    Geometry bufferedZone = processedZone.buffer(0.0001);
                    intersection = bufferedGeom.intersection(bufferedZone);
                    if (intersection == null || intersection.isEmpty()) {
                        LOGGER.warn("缓冲修复后仍无交集,跳过该结果");
                        continue;
                    }
                } catch (Exception ex) {
                    LOGGER.error("几何相交修复失败,跳过该结果: {}", ex.getMessage());
                    continue;
                }
            }
            calcEnterAndExitPoint(zoneDetectResult, orbitForecastSpec.getClothWidth(), lineString, intersection);
            List<ProSatelliteOrbitForecastStateDto> stateDtos = fillSatelliteOrbitForecastState(zoneDetectResult, Double.valueOf(orbitForecastSpec.getClothWidth()));
            resultItemDTO.setForecastStateDtos(stateDtos);
            resultItemDTO.setEntryTime(zoneDetectResult.getEntryTime());
            resultItemDTO.setExitTime(zoneDetectResult.getExitTime());
            resultItemDTO.setGeomJson(GeoToolsUtil.geometry2Json(intersection));
            itemList.add(resultItemDTO);
        }
        return itemList;
    }

    /**
     * 处理矩形区域，考虑球面变形
     */
    private static Geometry processRectangularZone(Geometry zone) {
        if (zone instanceof Polygon) {
            Polygon polygon = (Polygon) zone;
            Coordinate[] coordinates = polygon.getCoordinates();

            // 如果是矩形（4个顶点）
            if (isRectangle(zone)) { // 5个点（首尾相同）
                // 获取边界框
                Envelope envelope = polygon.getEnvelopeInternal();

                // 创建更密集的球面矩形
//                List<List<Double>> sphericalRect = createSphericalRectangle(
//                    envelope.getMinX(), envelope.getMinY(),
//                    envelope.getMaxX(), envelope.getMaxY(), 100);
                // 创建精确的球面矩形
                List<List<Double>> sphericalRect = createAccurateSphericalRectangle(
                    envelope.getMinX(), envelope.getMinY(),
                    envelope.getMaxX(), envelope.getMaxY(), 50); // 增加分段数

                // 转换为Geometry
                return GeoToolsUtil.createPolygonFromCoordinates(
                    sphericalRect.stream()
                        .map(point -> new Coordinate(point.get(0), point.get(1)))
                        .toArray(Coordinate[]::new));
            }
        }
        return zone;
    }

    /**
     * 创建精确的球面矩形（考虑地球曲率）
     */
    public static List<List<Double>> createAccurateSphericalRectangle(
        double minLon, double minLat, double maxLon, double maxLat, int segmentsPerSide) {

        List<List<Double>> rectangle = new ArrayList<>();
        double earthRadius = Constants.WGS84_EARTH_EQUATORIAL_RADIUS;

        // 下边（从西南到东南）- 使用大圆弧
        for (int i = 0; i <= segmentsPerSide; i++) {
            double fraction = (double) i / segmentsPerSide;
            double lon = minLon + (maxLon - minLon) * fraction;
            // 纬度保持最小纬度
            rectangle.add(Arrays.asList(lon, minLat));
        }

        // 右边（从东南到东北）- 使用大圆弧
        for (int i = 1; i <= segmentsPerSide; i++) {
            double fraction = (double) i / segmentsPerSide;
            double lat = minLat + (maxLat - minLat) * fraction;
            // 经度保持最大经度
            rectangle.add(Arrays.asList(maxLon, lat));
        }

        // 上边（从东北到西北）- 使用大圆弧，反向
        for (int i = segmentsPerSide - 1; i >= 0; i--) {
            double fraction = (double) i / segmentsPerSide;
            double lon = minLon + (maxLon - minLon) * fraction;
            // 纬度保持最大纬度
            rectangle.add(Arrays.asList(lon, maxLat));
        }

        // 左边（从西北到西南）- 使用大圆弧，反向
        for (int i = segmentsPerSide - 1; i > 0; i--) {
            double fraction = (double) i / segmentsPerSide;
            double lat = minLat + (maxLat - minLat) * fraction;
            // 经度保持最小经度
            rectangle.add(Arrays.asList(minLon, lat));
        }

        // 闭合多边形
        if (!rectangle.isEmpty()) {
            rectangle.add(rectangle.get(0));
        }

        return rectangle;
    }

    private static boolean calcPoint(Geometry geometry, double dx, double dy, double[] startPoints) {
        double px = startPoints[0];
        double py = startPoints[1];
        Coordinate left = new Coordinate(px - dx, py - dy);
        Coordinate right = new Coordinate(px + dx, py + dy);
        GeometryFactory geometryFactory = GeoToolsUtil.getGeometryFactory();
        LineString perpendicularLine = geometryFactory.createLineString(new Coordinate[]{left, right});
        Geometry intersection = perpendicularLine.intersection(geometry);
        return intersection != null && intersection.getNumGeometries() > 0 && !intersection.isEmpty();
    }

    private static double[] calculateMidPoint(double[] startPoints, double[] endPoints) {

        double startX = startPoints[0];
        double startY = startPoints[1];

        double endX = endPoints[0];       // 经度
        double endY = endPoints[1];       // 纬度

        // 计算中点
        double midX = (startX + endX) / 2;
        double midY = (startY + endY) / 2;

        return new double[]{midX, midY};
    }

    private static List<ProSatelliteOrbitForecastStateDto> fillSatelliteOrbitForecastState(List<Double> entryPosition, List<Double> exitPosition, Date entryDate, Date exitDate, Double drawHeight) {
        List<ProSatelliteOrbitForecastStateDto> stateDtos = new ArrayList<>();

//        double clothWidth = GeoToolsUtil.meterToWgs84(drawHeight, true);
//        double angleRadians = Math.atan2(exitPosition.get(1) - entryPosition.get(1), exitPosition.get(0) - entryPosition.get(0));

        // 提取经纬度和高度
        double entryLon = entryPosition.get(0);
        double entryLat = entryPosition.get(1);
        Double entryAltitude = (entryPosition.size() > 2) ? entryPosition.get(2) : null;

        double exitLon = exitPosition.get(0);
        double exitLat = exitPosition.get(1);
        Double exitAltitude = (exitPosition.size() > 2) ? exitPosition.get(2) : null;

        // 构建入点状态
        ProSatelliteOrbitForecastStateDto entryState = new ProSatelliteOrbitForecastStateDto();
        entryState.setForecastStamp(entryDate);
        entryState.setLon(entryLon);
        entryState.setLat(entryLat);
        entryState.setAltitude(entryAltitude);
//        entryState.setGeomJson(GeoToolsUtil.geometry2Json(drawPolygon(clothWidth, angleRadians, entryPosition.get(0), entryPosition.get(1))));
        stateDtos.add(entryState);

        // 构建出点状态
        ProSatelliteOrbitForecastStateDto exitState = new ProSatelliteOrbitForecastStateDto();
        exitState.setForecastStamp(exitDate);
        exitState.setLon(exitLon);
        exitState.setLat(exitLat);
        exitState.setAltitude(exitAltitude);
//        exitState.setGeomJson(GeoToolsUtil.geometry2Json(drawPolygon(clothWidth, angleRadians, exitLon, exitLat)));
        stateDtos.add(exitState);

        return stateDtos;
    }

    private static List<ProSatelliteOrbitForecastStateDto> fillSatelliteOrbitForecastState(ZoneDetectResult zoneDetectResult, Double drawHeight) {
        List<List<Double>> footprints = zoneDetectResult.getFootprints();
        List<Double> entryPosition = footprints.get(0);
        List<Double> exitPosition = footprints.get(footprints.size() - 1);
        return fillSatelliteOrbitForecastState(entryPosition, exitPosition, zoneDetectResult.getEntryTime(), zoneDetectResult.getExitTime(), drawHeight);
    }

    private static void calcEnterAndExitPoint(ZoneDetectResult zoneDetectResult, Integer sensorClothWidth, LineString lineString, Geometry geometry) {
        ZoneDetectResult beforeEnter = zoneDetectResult.getBeforeEnter();
        Coordinate[] coords = lineString.getCoordinates();
        Coordinate start = coords[0];
        Coordinate end = coords[coords.length - 1];

        double angleRadians = Math.atan2(end.y - start.y, end.x - start.x);
        double perpendicularAngle = angleRadians + Math.PI / 2;
        double halfLength = GeoToolsUtil.meterToWgs84(sensorClothWidth, true);
        double dx = halfLength * Math.cos(perpendicularAngle);
        double dy = halfLength * Math.sin(perpendicularAngle);
        if (beforeEnter != null) {
            List<Double> doubles = beforeEnter.getFootprints().get(0);
            List<Double> points = zoneDetectResult.getFootprints().get(0);
            double[] endPoints = new double[]{points.get(0), points.get(1)};
            double[] startPoints = new double[]{doubles.get(0), doubles.get(1)};
            double[] midPoints = calculateMidPoint(startPoints, endPoints);

            // 入境前 入境
            LineString half = GeoToolsUtil.getGeometryFactory().createLineString(new Coordinate[]{new Coordinate(startPoints[0], startPoints[1]), new Coordinate(endPoints[0], endPoints[1])});
            Geometry startGeometry = GeoToolsUtil.createPolygonFromLine(sensorClothWidth / 2, half);
            Geometry intersection = startGeometry.intersection(geometry);

            int index = 0;
            while (index < 4) {
                boolean b = calcPoint(intersection, dx, dy, midPoints);
                if (b) {
                    endPoints = midPoints;
                } else {
                    startPoints = midPoints;
                }
                midPoints = calculateMidPoint(startPoints, endPoints);
                index++;
            }
            points.set(0, midPoints[0]);
            points.set(1, midPoints[1]);
        }

        ZoneDetectResult afterExit = zoneDetectResult.getAfterExit();
        if (afterExit != null) {
            List<Double> doubles = afterExit.getFootprints().get(0);
            List<Double> points = zoneDetectResult.getFootprints().get(zoneDetectResult.getFootprints().size() - 1);
            double[] startPoints = new double[]{points.get(0), points.get(1)};
            double[] endPoints = new double[]{doubles.get(0), doubles.get(1)};
            double[] midPoints = calculateMidPoint(startPoints, endPoints);

            // 入境前 入境
            LineString half = GeoToolsUtil.getGeometryFactory().createLineString(new Coordinate[]{new Coordinate(startPoints[0], startPoints[1]), new Coordinate(endPoints[0], endPoints[1])});
            Geometry startGeometry = GeoToolsUtil.createPolygonFromLine(sensorClothWidth / 2, half);
            Geometry intersection = startGeometry.intersection(geometry);

            int index = 0;
            while (index < 3) {
                boolean b = calcPoint(intersection, dx, dy, midPoints);
                if (b) {
                    startPoints = midPoints;
                } else {
                    endPoints = midPoints;
                }
                midPoints = calculateMidPoint(startPoints, endPoints);
                index++;
            }
            points.set(0, midPoints[0]);
            points.set(1, midPoints[1]);
        }
    }


}
