//package com.htcloud.orbit.service.impl;
//
//import cn.hutool.core.util.StrUtil;
//import com.ht.common.geom.GeoToolsUtil;
//import com.htcloud.orbit.OrbitHelper;
//import com.htcloud.orbit.bis.bean.OrbitForecastSpecDto;
//import com.htcloud.orbit.bis.bean.ProSatelliteOrbitForecastResultItemDTO;
//import com.htcloud.orbit.bis.bean.ProSatelliteOrbitForecastStateDto;
//import com.htcloud.orbit.bis.bean.SatelliteTrackDto;
//import com.htcloud.orbit.model.TrackResult;
//import com.htcloud.orbit.model.ZoneDetectResult;
//import com.htcloud.orbit.service.SatelliteForecastService;
//import com.htcloud.orbit.vo.BoundParam;
//import com.htcloud.orbit.vo.ProSatelliteOrbitForecastForSideAngleReqDTO;
//import lombok.RequiredArgsConstructor;
//import lombok.extern.slf4j.Slf4j;
//import org.locationtech.jts.geom.*;
//import org.locationtech.jts.geom.util.AffineTransformation;
//import org.locationtech.jts.io.ParseException;
//import org.locationtech.jts.io.WKTReader;
//import org.orekit.propagation.analytical.tle.TLE;
//import org.orekit.utils.Constants;
//import org.springframework.stereotype.Service;
//
//import java.util.ArrayList;
//import java.util.Date;
//import java.util.List;
//
//@Slf4j
//@RequiredArgsConstructor
//@Service
//public class SatelliteForecastServiceImpl implements SatelliteForecastService {
//
//    @Override
//    public List<ProSatelliteOrbitForecastResultItemDTO> forecastSatelliteOverZone(List<OrbitForecastSpecDto> orbitForecastSpecs, Date startTime,
//                                                                                  Date endTime, String zone) {
//        Geometry detectZone = GeoToolsUtil.json2Geometry(zone);
//        OrbitForecastSpecDto orbitForecastSpecDto = orbitForecastSpecs.get(0);
//        List<ZoneDetectResult> zoneDetectResults = OrbitHelper.forecastSatelliteOverZone(orbitForecastSpecDto.getTleLine1(), orbitForecastSpecDto.getTleLine2(), startTime, endTime, detectZone);
//        ArrayList<ProSatelliteOrbitForecastResultItemDTO> itemList = new ArrayList<>();
//        if (zoneDetectResults.isEmpty()) {
//            return itemList;
//        }
//        for (ZoneDetectResult zoneDetectResult : zoneDetectResults) {
//            for (OrbitForecastSpecDto orbitForecastSpec : orbitForecastSpecs) {
//                CoordinateSequence coordinateSequence = GeoToolsUtil.createCoordinateSequence2D(zoneDetectResult.getFootprints().size());
//                int i = 0;
//                for (List<Double> footprint : zoneDetectResult.getFootprints()) {
//                    coordinateSequence.setOrdinate(i, CoordinateSequence.X, footprint.get(0));
//                    coordinateSequence.setOrdinate(i, CoordinateSequence.Y, footprint.get(1));
//                    i++;
//                }
//                ProSatelliteOrbitForecastResultItemDTO resultItemDTO = new ProSatelliteOrbitForecastResultItemDTO();
//                LineString lineString = GeoToolsUtil.getGeometryFactory().createLineString(coordinateSequence);
//
//                Geometry geometry = GeoToolsUtil.createPolygonFromLine(orbitForecastSpec.getClothWidth() / 2.0, lineString);
//                // 与指定面相交
//                Geometry intersection = geometry.intersection(detectZone);
//                calcEnterAndExitPoint(zoneDetectResult, orbitForecastSpec.getClothWidth(), lineString, intersection);
//                List<ProSatelliteOrbitForecastStateDto> stateDtos = fillSatelliteOrbitForecastState(zoneDetectResult, Double.valueOf(orbitForecastSpec.getClothWidth()));
//                resultItemDTO.setForecastStateDtos(stateDtos);
//
////                resultItemDTO.fillByOrbitForecastSpecDto(orbitForecastSpec);
//                resultItemDTO.setEntryTime(zoneDetectResult.getEntryTime());
//                resultItemDTO.setExitTime(zoneDetectResult.getExitTime());
//                resultItemDTO.setGeomJson(GeoToolsUtil.geometry2Json(intersection));
//                itemList.add(resultItemDTO);
//            }
//        }
//        return itemList;
//    }
//
//    private List<ProSatelliteOrbitForecastStateDto> fillSatelliteOrbitForecastState(ZoneDetectResult zoneDetectResult, Double drawHeight) {
//        List<List<Double>> footprints = zoneDetectResult.getFootprints();
//        List<Double> entryPosition = footprints.get(0);
//        List<Double> exitPosition = footprints.get(footprints.size() - 1);
//        return fillSatelliteOrbitForecastState(entryPosition, exitPosition, zoneDetectResult.getEntryTime(), zoneDetectResult.getExitTime(), drawHeight);
//    }
//
//
//    private List<ProSatelliteOrbitForecastStateDto> fillSatelliteOrbitForecastState(List<Double> entryPosition, List<Double> exitPosition, Date entryDate, Date exitDate, Double drawHeight) {
//        List<ProSatelliteOrbitForecastStateDto> stateDtos = new ArrayList<>();
//
////        double clothWidth = GeoToolsUtil.meterToWgs84(drawHeight, true);
////        double angleRadians = Math.atan2(exitPosition.get(1) - entryPosition.get(1), exitPosition.get(0) - entryPosition.get(0));
//
//        // 提取经纬度和高度
//        double entryLon = entryPosition.get(0);
//        double entryLat = entryPosition.get(1);
//        Double entryAltitude = (entryPosition.size() > 2) ? entryPosition.get(2) : null;
//
//        double exitLon = exitPosition.get(0);
//        double exitLat = exitPosition.get(1);
//        Double exitAltitude = (exitPosition.size() > 2) ? exitPosition.get(2) : null;
//
//        // 构建入点状态
//        ProSatelliteOrbitForecastStateDto entryState = new ProSatelliteOrbitForecastStateDto();
//        entryState.setForecastStamp(entryDate);
//        entryState.setLon(entryLon);
//        entryState.setLat(entryLat);
//        entryState.setAltitude(entryAltitude);
////        entryState.setGeomJson(GeoToolsUtil.geometry2Json(drawPolygon(clothWidth, angleRadians, entryPosition.get(0), entryPosition.get(1))));
//        stateDtos.add(entryState);
//
//        // 构建出点状态
//        ProSatelliteOrbitForecastStateDto exitState = new ProSatelliteOrbitForecastStateDto();
//        exitState.setForecastStamp(exitDate);
//        exitState.setLon(exitLon);
//        exitState.setLat(exitLat);
//        exitState.setAltitude(exitAltitude);
////        exitState.setGeomJson(GeoToolsUtil.geometry2Json(drawPolygon(clothWidth, angleRadians, exitLon, exitLat)));
//        stateDtos.add(exitState);
//
//        return stateDtos;
//    }
//
//    private void calcEnterAndExitPoint(ZoneDetectResult zoneDetectResult, Integer sensorClothWidth, LineString lineString, Geometry geometry) {
//        ZoneDetectResult beforeEnter = zoneDetectResult.getBeforeEnter();
//        Coordinate[] coords = lineString.getCoordinates();
//        Coordinate start = coords[0];
//        Coordinate end = coords[coords.length - 1];
//
//        double angleRadians = Math.atan2(end.y - start.y, end.x - start.x);
//        double perpendicularAngle = angleRadians + Math.PI / 2;
//        double halfLength = GeoToolsUtil.meterToWgs84(sensorClothWidth, true);
//        double dx = halfLength * Math.cos(perpendicularAngle);
//        double dy = halfLength * Math.sin(perpendicularAngle);
//        if (beforeEnter != null) {
//            List<Double> doubles = beforeEnter.getFootprints().get(0);
//            List<Double> points = zoneDetectResult.getFootprints().get(0);
//            double[] endPoints = new double[]{points.get(0), points.get(1)};
//            double[] startPoints = new double[]{doubles.get(0), doubles.get(1)};
//            double[] midPoints = calculateMidPoint(startPoints, endPoints);
//
//            // 入境前 入境
//            LineString half = GeoToolsUtil.getGeometryFactory().createLineString(new Coordinate[]{new Coordinate(startPoints[0], startPoints[1]), new Coordinate(endPoints[0], endPoints[1])});
//            Geometry startGeometry = GeoToolsUtil.createPolygonFromLine(sensorClothWidth / 2, half);
//            Geometry intersection = startGeometry.intersection(geometry);
//
//            int index = 0;
//            while (index < 4) {
//                boolean b = calcPoint(intersection, dx, dy, midPoints);
//                if (b) {
//                    endPoints = midPoints;
//                } else {
//                    startPoints = midPoints;
//                }
//                midPoints = calculateMidPoint(startPoints, endPoints);
//                index++;
//            }
//            points.set(0, midPoints[0]);
//            points.set(1, midPoints[1]);
//        }
//
//        ZoneDetectResult afterExit = zoneDetectResult.getAfterExit();
//        if (afterExit != null) {
//            List<Double> doubles = afterExit.getFootprints().get(0);
//            List<Double> points = zoneDetectResult.getFootprints().get(zoneDetectResult.getFootprints().size() - 1);
//            double[] startPoints = new double[]{points.get(0), points.get(1)};
//            double[] endPoints = new double[]{doubles.get(0), doubles.get(1)};
//            double[] midPoints = calculateMidPoint(startPoints, endPoints);
//
//            // 入境前 入境
//            LineString half = GeoToolsUtil.getGeometryFactory().createLineString(new Coordinate[]{new Coordinate(startPoints[0], startPoints[1]), new Coordinate(endPoints[0], endPoints[1])});
//            Geometry startGeometry = GeoToolsUtil.createPolygonFromLine(sensorClothWidth / 2, half);
//            Geometry intersection = startGeometry.intersection(geometry);
//
//            int index = 0;
//            while (index < 3) {
//                boolean b = calcPoint(intersection, dx, dy, midPoints);
//                if (b) {
//                    startPoints = midPoints;
//                } else {
//                    endPoints = midPoints;
//                }
//                midPoints = calculateMidPoint(startPoints, endPoints);
//                index++;
//            }
//            points.set(0, midPoints[0]);
//            points.set(1, midPoints[1]);
//        }
//    }
//
//    private boolean calcPoint(Geometry geometry, double dx, double dy, double[] startPoints) {
//        double px = startPoints[0];
//        double py = startPoints[1];
//        Coordinate left = new Coordinate(px - dx, py - dy);
//        Coordinate right = new Coordinate(px + dx, py + dy);
//        GeometryFactory geometryFactory = GeoToolsUtil.getGeometryFactory();
//        LineString perpendicularLine = geometryFactory.createLineString(new Coordinate[]{left, right});
//        Geometry intersection = perpendicularLine.intersection(geometry);
//        return intersection != null && intersection.getNumGeometries() > 0 && !intersection.isEmpty();
//    }
//
//    private double[] calculateMidPoint(double[] startPoints, double[] endPoints) {
//
//        double startX = startPoints[0];
//        double startY = startPoints[1];
//
//        double endX = endPoints[0];       // 经度
//        double endY = endPoints[1];       // 纬度
//
//        // 计算中点
//        double midX = (startX + endX) / 2;
//        double midY = (startY + endY) / 2;
//
//        return new double[]{midX, midY};
//    }
//
//    @Override
//    public SatelliteTrackDto trackSatellite(String tleLine1, String tleLine2, Date startTime, Date endTime, double stepSeconds) {
//        SatelliteTrackDto res = new SatelliteTrackDto();
//        List<TrackResult> results = OrbitHelper.trackSatellite(tleLine1, tleLine2, startTime, endTime, stepSeconds);
//        res.setResults(results);
//        return res;
//    }
//
//    @Override
//    public List<Double> forecastSatellitePosition(String tleLine1, String tleLine2, Date targetTime) {
//        return OrbitHelper.forecastSatellitePosition(tleLine1, tleLine2, targetTime);
//    }
//
//    private static Geometry readWkt(String wkt) {
//        WKTReader reader = new WKTReader();
//        Geometry geometry = null;
//        try {
//            geometry = reader.read(wkt);
//        } catch (ParseException e) {
//            e.printStackTrace();
//        }
//        return geometry;
//    }
//
//    /**
//     * 侧摆角预测
//     *
//     * @param reqDTO
//     * @return
//     */
//    @Override
//    public ProSatelliteOrbitForecastResultItemDTO getOrbitForecastResultForSideAngle(ProSatelliteOrbitForecastForSideAngleReqDTO reqDTO) {
//
//        if (reqDTO.getClothWidth() == null || reqDTO.getClothWidth() <= 0) {
//            throw new IllegalArgumentException("传感器幅宽为空或不是正数");
//        }
//        if (reqDTO.getOrbitalAltitude() == null || reqDTO.getOrbitalAltitude() <= 0) {
//            throw new IllegalArgumentException( "卫星轨道高度为空或不是正数");
//        }
//        // TLE参数校验
//        if(!TLE.isFormatOK(reqDTO.getTleLine1(), reqDTO.getTleLine2())) {
//            throw new IllegalArgumentException("TLE参数格式异常，请修改！");
//        }
//
//        Geometry zoneGeom = getZoneGeom(reqDTO.getBound());
//
//        // 构造轨道线段
//        Coordinate[] coordinates = new Coordinate[reqDTO.getPositionList().size()];
//        int i = 0;
//        for (List<Double> xy : reqDTO.getPositionList()) {
//            coordinates[i] = new CoordinateXY(xy.get(0), xy.get(1));
//            i++;
//        }
//        LineString lineString = GeoToolsUtil.getGeometryFactory().createLineString(coordinates);
//
//        // 估算与x轴夹角
//        Coordinate first = coordinates[0];
//        Coordinate last = coordinates[coordinates.length - 1];
//        double inclination = Math.atan((last.getY() - first.getY()) / (last.getX() - first.getX()));
//
//        // 计算偏移距离，求解x, y方向的偏移，仿射变换
//        double sideAngle = Double.parseDouble(reqDTO.getSideAngle());
//        double offsetDistance = reqDTO.getOrbitalAltitude() * Math.tan(Math.toRadians(sideAngle));
//        offsetDistance = GeoToolsUtil.meterToWgs84(offsetDistance, true);
//
//        AffineTransformation affineTransformation = AffineTransformation.translationInstance(offsetDistance * Math.sin(inclination), -offsetDistance * Math.cos(inclination));
//        lineString.apply(affineTransformation);
//        Coordinate[] coordinates1 = lineString.getCoordinates();
//        List<Double> entryPosition = new ArrayList<>();
//        entryPosition.add(coordinates1[0].x);
//        entryPosition.add(coordinates1[0].y);
//        entryPosition.add(Double.valueOf(reqDTO.getOrbitalAltitude()));
//        List<Double> exitPosition = new ArrayList<>();
//        exitPosition.add(coordinates1[coordinates1.length - 1].x);
//        exitPosition.add(coordinates1[coordinates1.length - 1].y);
//        exitPosition.add(Double.valueOf(reqDTO.getOrbitalAltitude()));
//
//        // 外扩
//        LineString orbitLine = GeoToolsUtil.extendLineStringWithVector(lineString, 10);
//
//        Geometry orbitPolygon = orbitLine.buffer(GeoToolsUtil.meterToWgs84(reqDTO.getOrbitalAltitude() / 2.0, true));
//        Geometry resultGeom = orbitPolygon.intersection(zoneGeom);
//        if (resultGeom == null || resultGeom.isEmpty()) {
//            return null;
//        }
//
//        ProSatelliteOrbitForecastResultItemDTO resultItemDTO = new ProSatelliteOrbitForecastResultItemDTO();
//        resultItemDTO.setSatelliteId(reqDTO.getSatelliteId());
//        resultItemDTO.setOrbitAltitude(reqDTO.getOrbitalAltitude());
//        resultItemDTO.setSatelliteName(reqDTO.getSatelliteName());
//
//        List<ProSatelliteOrbitForecastStateDto> stateDtos = fillSatelliteOrbitForecastState(entryPosition, exitPosition, reqDTO.getForecastStartTime(), reqDTO.getForecastEndTime(), Double.valueOf(reqDTO.getClothWidth()));
//        resultItemDTO.setForecastStateDtos(stateDtos);
//        resultItemDTO.setEntryTime(reqDTO.getForecastStartTime());
//        resultItemDTO.setExitTime(reqDTO.getForecastEndTime());
//        resultItemDTO.setGeomJson(GeoToolsUtil.geometry2Json(resultGeom));
//        return resultItemDTO;
//    }
//
////    private void validateOrbitForecastSpec(OrbitForecastSpecDto orbitForecastSpec) {
////        if (orbitForecastSpec.getSensorClothWidth() == null || orbitForecastSpec.getSensorClothWidth() <= 0) {
////            throw new IllegalArgumentException(orbitForecastSpec.getCode() + "卫星" + orbitForecastSpec.getSensorCode() + "传感器幅宽为空或不是正数");
////        }
////        if (orbitForecastSpec.getOrbitAltitude() == null || orbitForecastSpec.getOrbitAltitude() <= 0) {
////            throw new IllegalArgumentException(orbitForecastSpec.getCode() + "卫星轨道高度为空或不是正数");
////        }
////        // TLE参数校验
////        if(!TLE.isFormatOK(orbitForecastSpec.getTleLine1(), orbitForecastSpec.getTleLine2())) {
////            throw new IllegalArgumentException("TLE参数格式异常，请修改！");
////        }
////    }
//
//    public Geometry getZoneGeom(BoundParam model) {
//        String wkt = "";
//        BoundParam.GeometryQuery geometryQuery = model.getQuery().toJavaObject(BoundParam.GeometryQuery.class);
//        Geometry geometry = GeoToolsUtil.readWkt(geometryQuery.getWkt());
//        // 将点画成面 并外扩
//        if (geometry instanceof Point) {
//            Geometry pointsToPolygon = pointsToPolygon((Point) geometry, 0.1);
//            wkt = pointsToPolygon.toText();
//        } else {
//            wkt = geometryQuery.getWkt();
//        }
//
//        if (StrUtil.isBlank(wkt)) throw new IllegalArgumentException("wkt为空");
//        return GeoToolsUtil.validGeometry(GeoToolsUtil.readWkt(wkt));
//    }
//
//    private Geometry pointsToPolygon(Point point, double radius) {
//        // 将 Point 转换为经度和纬度
//        double longitude = point.getX();
//        double latitude = point.getY();
//
//        // 将半径转换为弧度
//        double radiusInRadians = radius / Constants.WGS84_EARTH_EQUATORIAL_RADIUS;
//
//        // 计算正方形的四个顶点
//        List<Coordinate> coordinates = new ArrayList<>();
//
//        // 计算每个方向的偏移量
//        double x1 = longitude - radiusInRadians;
//        double x2 = longitude + radiusInRadians;
//        double y1 = latitude - radiusInRadians;
//        double y2 = latitude + radiusInRadians;
//
//        // 逆时针顺序添加顶点
//        coordinates.add(new Coordinate(x1, y1)); // 左下
//        coordinates.add(new Coordinate(x2, y1)); // 右下
//        coordinates.add(new Coordinate(x2, y2)); // 右上
//        coordinates.add(new Coordinate(x1, y2)); // 左上
//        coordinates.add(new Coordinate(x1, y1)); // 闭合环
//
//        // 创建 LinearRing
//        GeometryFactory geometryFactory = new GeometryFactory();
//        LinearRing linearRing = geometryFactory.createLinearRing(coordinates.toArray(new Coordinate[0]));
//        // 外扩
//        return linearRing.buffer(radius);
////        Geometry buffer = linearRing.buffer(radius);
////        System.out.println("面积:" + buffer.getArea() + "源wkt" + linearRing.toText() + ";wkt:" + buffer.toText());
////        return buffer;
//    }
//
//
//}
