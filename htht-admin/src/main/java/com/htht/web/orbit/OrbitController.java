package com.htht.web.orbit;

import com.htcloud.orbit.OrbitHelper;
import com.htcloud.orbit.SatelliteForecastHellper;
import com.htcloud.orbit.bis.bean.OrbitForecastSpecDto;
import com.htcloud.orbit.bis.bean.ProSatelliteOrbitForecastResultItemDTO;
import com.htcloud.orbit.model.ZoneDetectResult;
import com.htcloud.orbit.utils.ShpFileGenerator;
import com.htcloud.orbit.vo.*;
import com.htht.business.domain.vo.regions.satellite.ForecastSatelliteOverZoneResultVO;
import com.htht.common.core.domain.R;
import com.htht.common.core.domain.entity.SysDictData;
import com.htht.common.core.page.TableDataInfo;
import com.htht.common.utils.IDUtil;
import com.htht.system.service.ISysDictDataService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * 卫星轨道 前端控制器
 *
 * @author MSFY
 * @since 2025-09-26
 */

@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/orbit")
public class OrbitController {


    private final ISysDictDataService sysDictDataService;

    /**
     * 根据TLE预测卫星进入/离开地面区域情况(多边形)
     * @param queryVOList
     * @return TableDataInfo<ForecastSatelliteOverZoneResultVO>
     */
    @PostMapping("/forecastSatelliteOverZone")
    public TableDataInfo<ForecastSatelliteOverZoneResultVO> forecastSatelliteOverZone(@RequestBody List<OrbitQueryVO>  queryVOList) {
        List<ForecastSatelliteOverZoneResultVO> resultList = new ArrayList<>();
        for (OrbitQueryVO queryVO : queryVOList) {
            ForecastSatelliteOverZoneResultVO forecastSatelliteOverZoneResultVO = new ForecastSatelliteOverZoneResultVO();
            List<ZoneDetectResult> list =  OrbitHelper.forecastSatelliteOverZone(
                queryVO.getTleLine1(),
                queryVO.getTleLine2(),
                queryVO.getStartTime(),
                queryVO.getEndTime(),
                OrbitHelper.parseGeoJson(queryVO.getZone()));
            BeanUtils.copyProperties(queryVO, forecastSatelliteOverZoneResultVO);
            forecastSatelliteOverZoneResultVO.setZoneDetectResult(list);
            resultList.add(forecastSatelliteOverZoneResultVO);
        }
        return TableDataInfo.build(resultList);
    }

    /**
     * 根据TLE预测卫星进入/离开地面区域情况（圆形）
     * @param queryVOList
     * @return
     */
    @PostMapping("/forecastSatelliteOverCircle")
    public TableDataInfo<ForecastSatelliteOverZoneResultVO> forecastSatelliteOverCircle(@RequestBody List<CircleOrbitQueryVO> queryVOList) {
        List<ForecastSatelliteOverZoneResultVO> resultList = new ArrayList<>();
        for (CircleOrbitQueryVO queryVO : queryVOList) {
            if(queryVO.getSegments() < 3) {
                throw new IllegalArgumentException("多边形边数不能小于3，请修改参数再试！");
            }
            if(queryVO.getRadiusKm() < 0.1) {
                throw new IllegalArgumentException("预测圆的半径不能小于100米，请修改参数再试！");
            }
            ForecastSatelliteOverZoneResultVO resultVO = new ForecastSatelliteOverZoneResultVO();
            List<ZoneDetectResult> list =  OrbitHelper.forecastSatelliteOverCircle(
                queryVO.getTleLine1(),
                queryVO.getTleLine2(),
                queryVO.getStartTime(),
                queryVO.getEndTime(),
                queryVO.getCenterLon(),
                queryVO.getCenterLat(),
                queryVO.getRadiusKm(),
                queryVO.getSegments());
            BeanUtils.copyProperties(queryVO, resultVO);
            resultVO.setZoneDetectResult(list);
            resultList.add(resultVO);
        }
        return TableDataInfo.build(resultList);
    }


    @Value("${orekit.data.zip}")
    private String SHP_ZIP_PATH;

    /**
     * 导出
     * @param response
     * @param geoJson
     */
    @PostMapping("/download")
    public void download(HttpServletResponse response, @RequestBody String geoJson) throws IOException {
        InputStream inputStream = null;
        try {
            String id = IDUtil.getStringSimpleID();
            // 生成ZIP文件
            String zipFilePath = ShpFileGenerator.generateShpZip(
                geoJson,
                SHP_ZIP_PATH + File.separator + id + ".zip"
            );
            // 创建文件资源
            Path path = Paths.get(zipFilePath);
            // 读到流中
            inputStream = new FileInputStream(zipFilePath);// 文件的存放路径
            response.setContentType(MediaType.APPLICATION_OCTET_STREAM.getType());
            response.addHeader("Content-Disposition", "attachment; filename=" + URLEncoder.encode( path.getFileName().toString(), StandardCharsets.UTF_8));
            ServletOutputStream outputStream = response.getOutputStream();
            byte[] b = new byte[1024];
            int len;
            //从输入流中读取一定数量的字节，并将其存储在缓冲区字节数组中，读到末尾返回-1
            while ((len = inputStream.read(b)) > 0) {
                outputStream.write(b, 0, len);
            }
        } catch (Exception e) {
            e.printStackTrace();
            throw new IllegalArgumentException("文件下载失败，错误信息：" + e.getMessage());
        } finally {
            if (inputStream != null) {
                inputStream.close();
            }
        }
    }


    private  void checkForecast(OrbitForecastRequestVO queryVO){
        String count = sysDictDataService.lambdaQuery().eq(SysDictData::getDictCode, 24).one().getDictValue();
        if (queryVO.getOrbitForecastSpecs().size() > Integer.parseInt(count)|| queryVO.getOrbitForecastSpecs().isEmpty()) {
            throw new RuntimeException("所选卫星不能超过"+count+"颗，时间长度不能超过两周");
        }
        long diff = queryVO.getEndTime().getTime() - queryVO.getStartTime().getTime();
        long max = 14L * 24 * 60 * 60 * 1000; // 14天毫秒数
        if (diff > max) {
            throw new RuntimeException("所选卫星不能超过"+count+"颗，时间长度不能超过两周");
        }

    }

    private  void checkForecast(OrbitForecastCircleRequestVO queryVO){
        String count = sysDictDataService.lambdaQuery().eq(SysDictData::getDictCode, 24).one().getDictValue();
        if (queryVO.getOrbitForecastSpecs().size() > Integer.parseInt(count)|| queryVO.getOrbitForecastSpecs().isEmpty()) {
            throw new RuntimeException("所选卫星不能超过"+count+"颗，时间长度不能超过两周");
        }
        long diff = queryVO.getEndTime().getTime() - queryVO.getStartTime().getTime();
        long max = 14L * 24 * 60 * 60 * 1000; // 14天毫秒数
        if (diff > max) {
            throw new RuntimeException("所选卫星不能超过"+count+"颗，时间长度不能超过两周");
        }
    }


    /**
     * 获取卫星预测限制个数
     */
    @GetMapping("/getSatelliteForecastLimitCount")
    public R<String> getSatelliteForecastLimitCount() {
        return R.ok(sysDictDataService.lambdaQuery().eq(SysDictData::getDictCode, 24).one().getDictValue());
    }

    /**
     * 修改卫星预测限制个数
     */
    @PutMapping("/updateSatelliteForecastLimitCount/{count}")
    public R<Void> updateSatelliteForecastLimitCount(@PathVariable Integer count) {
        SysDictData one = sysDictDataService.lambdaQuery().eq(SysDictData::getDictCode, 24).one();
        one.setDictValue(count.toString());
        sysDictDataService.updateById(one);
        return R.ok();
    }

    /**
     * 根据TLE预测卫星进入/离开地面区域情况(多边形) + 画线
     * @param queryVO
     * @return
     */
    @PostMapping("/forecastSatelliteOverZoneAndLine")
    public TableDataInfo<ProSatelliteOrbitForecastResultItemDTO> forecastSatelliteOverZoneAndLine(@Validated @RequestBody OrbitForecastRequestVO queryVO) {
        // 2. 判断时间差不能超过 14 天（2周）
        checkForecast(queryVO);

        List<ProSatelliteOrbitForecastResultItemDTO> resultList = new ArrayList<>();
        List<OrbitForecastSpecDto> orbitForecastSpecs = queryVO.getOrbitForecastSpecs();
        for (OrbitForecastSpecDto orbitForecastSpec : orbitForecastSpecs) {
            List<ZoneDetectResult> list =  OrbitHelper.forecastSatelliteOverZone(
                orbitForecastSpec.getTleLine1(),
                orbitForecastSpec.getTleLine2(),
                queryVO.getStartTime(),
                queryVO.getEndTime(),
                OrbitHelper.parseGeoJson(queryVO.getZone()));
            List<ProSatelliteOrbitForecastResultItemDTO> list1 = OrbitHelper.forecastSatelliteOverZoneAndLine(
                list,
                queryVO.getStartTime(),
                queryVO.getEndTime(),
                queryVO.getZone(),
                orbitForecastSpec);
             if(list1.size() > 0) {
                 for (ProSatelliteOrbitForecastResultItemDTO item : list1) {
                     item.setZoneDetectResultList(list);
                 }
             }
            resultList.addAll(list1);
        }
        return TableDataInfo.build(resultList);
    }

    /**
     * 根据TLE预测卫星进入/离开地面区域情况(多边形) + 画线 周期运算
     */
    @PostMapping("/forecastSatelliteOverZoneAndLinePeriod")
    public TableDataInfo<ProSatelliteOrbitForecastResultItemDTO> forecastSatelliteOverZoneAndLinePeriod(@Validated @RequestBody OrbitForecastRequestVO queryVO,
                                                                                                        @Validated @RequestBody OrbitForecastPeriod period) {
        // 2. 判断时间差不能超过 14 天（2周）
        checkForecast(queryVO);
        List<OrbitForecastRequestVO> listData = OrbitForecastPeriod.splitForecastByDailyPeriodOnZone(queryVO, period);

        List<ProSatelliteOrbitForecastResultItemDTO> resultList = new ArrayList<>();
        for (OrbitForecastRequestVO listDatum : listData) {
            List<OrbitForecastSpecDto> orbitForecastSpecs = listDatum.getOrbitForecastSpecs();
            for (OrbitForecastSpecDto orbitForecastSpec : orbitForecastSpecs) {
                List<ZoneDetectResult> list =  OrbitHelper.forecastSatelliteOverZone(
                    orbitForecastSpec.getTleLine1(),
                    orbitForecastSpec.getTleLine2(),
                    listDatum.getStartTime(),
                    listDatum.getEndTime(),
                    OrbitHelper.parseGeoJson(listDatum.getZone()));
                List<ProSatelliteOrbitForecastResultItemDTO> list1 = OrbitHelper.forecastSatelliteOverZoneAndLine(
                    list,
                    listDatum.getStartTime(),
                    listDatum.getEndTime(),
                    listDatum.getZone(),
                    orbitForecastSpec);
                if(list1.size() > 0) {
                    for (ProSatelliteOrbitForecastResultItemDTO item : list1) {
                        item.setZoneDetectResultList(list);
                    }
                }
                resultList.addAll(list1);
            }
        }
        return TableDataInfo.build(resultList);
    }

    /**
     * 根据TLE预测卫星进入/离开地面区域情况（圆形）+ 画线
     * @param
     * @return
     */
    @PostMapping("/forecastSatelliteOverCircleAndLine")
    public TableDataInfo<ProSatelliteOrbitForecastResultItemDTO> forecastSatelliteOverCircleAndLine(@Validated @RequestBody OrbitForecastCircleRequestVO queryVO) {
        // 2. 判断时间差不能超过 14 天（2周）
        checkForecast(queryVO);


        assert queryVO.getSegments() >= 3 : "多边形边数不能小于3，请修改参数再试！";
        assert !(queryVO.getRadiusKm() < 0.1) : "预测圆的半径不能小于100米，请修改参数再试！";
        List<ProSatelliteOrbitForecastResultItemDTO> resultList = new ArrayList<>();
        List<OrbitForecastSpecDto> orbitForecastSpecs = queryVO.getOrbitForecastSpecs();
        for (OrbitForecastSpecDto orbitForecastSpec : orbitForecastSpecs) {
            ForecastSatelliteOverZoneResultVO resultVO = new ForecastSatelliteOverZoneResultVO();
            List<ProSatelliteOrbitForecastResultItemDTO> list =  OrbitHelper.forecastSatelliteOverCircleAndLine(
                orbitForecastSpec.getTleLine1(),
                orbitForecastSpec.getTleLine2(),
                queryVO.getStartTime(),
                queryVO.getEndTime(),
                queryVO.getCenterLon(),
                queryVO.getCenterLat(),
                queryVO.getRadiusKm(),
                queryVO.getSegments(),
                orbitForecastSpec);
            resultList.addAll(list);
        }
        return TableDataInfo.build(resultList);
    }

    /**
     * 根据TLE预测卫星进入/离开地面区域情况（圆形）+ 画线（周期运算）
     */
    @PostMapping("/forecastSatelliteOverCircleAndLinePeriod")
    public TableDataInfo<ProSatelliteOrbitForecastResultItemDTO> forecastSatelliteOverCircleAndLinePeriod(@Validated @RequestBody OrbitForecastCircleRequestVO queryVO,
                                                                                                          @Validated @RequestBody OrbitForecastPeriod period) {
        checkForecast(queryVO);

        assert queryVO.getSegments() >= 3 : "多边形边数不能小于3，请修改参数再试！";
        assert !(queryVO.getRadiusKm() < 0.1) : "预测圆的半径不能小于100米，请修改参数再试！";

        List<OrbitForecastCircleRequestVO> listData = OrbitForecastPeriod.splitForecastByDailyPeriodOnCircle(queryVO, period);
        List<ProSatelliteOrbitForecastResultItemDTO> resultList = new ArrayList<>();
        for (OrbitForecastCircleRequestVO listDatum : listData) {
            List<OrbitForecastSpecDto> orbitForecastSpecs = listDatum.getOrbitForecastSpecs();
            for (OrbitForecastSpecDto orbitForecastSpec : orbitForecastSpecs) {
                ForecastSatelliteOverZoneResultVO resultVO = new ForecastSatelliteOverZoneResultVO();
                List<ProSatelliteOrbitForecastResultItemDTO> list =  OrbitHelper.forecastSatelliteOverCircleAndLine(
                    orbitForecastSpec.getTleLine1(),
                    orbitForecastSpec.getTleLine2(),
                    listDatum.getStartTime(),
                    listDatum.getEndTime(),
                    listDatum.getCenterLon(),
                    listDatum.getCenterLat(),
                    listDatum.getRadiusKm(),
                    listDatum.getSegments(),
                    orbitForecastSpec);
                resultList.addAll(list);
            }
        }
        return TableDataInfo.build(resultList);
    }



    /**
     * 侧摆角预测（多边形）
     * @param queryVO
     * @return
     */
    @PostMapping("/getOrbitForecastResultForSideAngle")
    public R<ProSatelliteOrbitForecastResultItemDTO> getOrbitForecastResultForSideAngle(@RequestBody ProSatelliteOrbitForecastForSideAngleReqDTO queryVO) {
        ProSatelliteOrbitForecastResultItemDTO dto = SatelliteForecastHellper.getOrbitForecastResultForSideAngle(queryVO);
        return R.ok(dto);
    }

    /**
     * 侧摆角预测（圆形）
     * @param queryVO
     * @return
     */
    @PostMapping("/getOrbitForecastResultForSideAngleCircle")
    public R<ProSatelliteOrbitForecastResultItemDTO> getOrbitForecastResultForSideAngleCircle(@RequestBody ProSatelliteOrbitForecastForSideAngleCircleDTO queryVO) {
        ProSatelliteOrbitForecastResultItemDTO dto = SatelliteForecastHellper.getOrbitForecastResultForSideAngleCircle(queryVO);
        return R.ok(dto);
    }

}
