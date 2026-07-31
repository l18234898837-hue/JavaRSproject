//package com.htcloud.orbit.service;
//
//import com.htcloud.orbit.bis.bean.OrbitForecastSpecDto;
//import com.htcloud.orbit.bis.bean.ProSatelliteOrbitForecastResultItemDTO;
//import com.htcloud.orbit.bis.bean.SatelliteTrackDto;
//import com.htcloud.orbit.vo.ProSatelliteOrbitForecastForSideAngleReqDTO;
//
//import java.util.Date;
//import java.util.List;
//
//public interface SatelliteForecastService {
//
//    List<ProSatelliteOrbitForecastResultItemDTO> forecastSatelliteOverZone(List<OrbitForecastSpecDto> orbitForecastSpecs, Date startTime,
//                                                                                  Date endTime, String zone);
//
//    SatelliteTrackDto trackSatellite(String tleLine1, String tleLine2, Date startTime, Date endTime, double stepSeconds);
//
//    List<Double> forecastSatellitePosition(String tleLine1, String tleLine2, Date targetTime);
//
//    ProSatelliteOrbitForecastResultItemDTO getOrbitForecastResultForSideAngle(ProSatelliteOrbitForecastForSideAngleReqDTO reqDTO);
//}
