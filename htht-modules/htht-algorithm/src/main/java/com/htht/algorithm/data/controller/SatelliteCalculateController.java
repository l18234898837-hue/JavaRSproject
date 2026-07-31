package com.htht.algorithm.data.controller;

import com.htht.algorithm.data.domain.SnrRequest;
import com.htht.algorithm.data.domain.dto.*;
import com.htht.algorithm.data.domain.vo.*;
import com.htht.algorithm.data.service.SatelliteCalculateService;
import com.htht.algorithm.download.domain.vo.FileTreeVO;
import com.htht.common.core.domain.R;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
    卫星项目，算法总文件

 **/
@RestController
@RequestMapping("/satelliteCalculate")
@Validated
@RequiredArgsConstructor
public class SatelliteCalculateController {
    private final SatelliteCalculateService satelliteCalculateService;

    @GetMapping
    public R<List<SatelliteCalculateFolder>> getSatelliteCalculateFolder() {
        return R.ok(satelliteCalculateService.getSatelliteCalculateFolder(0));
    }

    @PostMapping("/runSNR")
    public R<Void> runSNR(@Validated @RequestBody AddRunSNR addRunSNR) {
        satelliteCalculateService.runSNR(addRunSNR);
        return R.ok();
    }

    @PostMapping("/getSNRData")
    public R<FileTreeVO> getSNRData(@Validated @RequestBody SnrRequest sR){
        return R.ok(satelliteCalculateService.getRunSNRData(sR.getRunFileStr()));
    }

    @PostMapping("/runSensitivity")
    public R<Void> runSensitivity(@Validated @RequestBody AddRunSensitivity addRunSensitivity) {
        String runSensitivity = satelliteCalculateService.checkRunSensitivity(addRunSensitivity.getRunFileStr());
        satelliteCalculateService.runSensitivity(addRunSensitivity.getRunFileStr(),runSensitivity);
        return R.ok();
    }

    @PostMapping("/getSensitivityData")
    public R<FileTreeVO> getSensitivityData(@Validated @RequestBody SnrRequest sR){
        return R.ok(satelliteCalculateService.getSensitivityData(sR.getRunFileStr()));
    }


    @GetMapping("/l2gen")
    public R<List<SatelliteCalculateFolder>> getL2genCalculateFolder(){
        return R.ok(satelliteCalculateService.getSatelliteCalculateFolder(1));
    }

    /**
     * 服务器配置
     * export OCDATAROOT=/env/ocssw/share
     * export OCVARROOT=/env/ocssw/var
     * export OCSSWROOT=/env/ocssw
     * export PATH=/env/ocssw/bin:$PATH
     * @param addRunL2gen
     * @return
     */
    @PostMapping("/runL2gen")
    public R<Void> runL2gen(@Validated @RequestBody AddRunL2gen addRunL2gen){
        satelliteCalculateService.runL2gen(addRunL2gen);
        return R.ok();
    }
    @GetMapping("/getL2genData")
    public R<FileTreeVO> getL2gen(SnrRequest sR){
        return R.ok(satelliteCalculateService.getL2genData());
    }

    @PostMapping("/runSST")
    public R<Void> runSST(@Validated @RequestBody AddRunSST addRunSST){
        satelliteCalculateService.runSST(addRunSST);
        return R.ok();
    }

    @GetMapping("/getSSTData")
    public R<FileTreeVO> getSST(){
        return R.ok(satelliteCalculateService.getSSTData());
    }

    @GetMapping("/ACL2gen")
    public R<List<SatelliteCalculateFolder>> getACL2gen(){
        return R.ok(satelliteCalculateService.getACL2gen());
    }
    @PostMapping("/ACL2gen")
    public R<Void> runACL2gen(@Validated @RequestBody AddRunACL2gen addRunAcL2gen){
        satelliteCalculateService.runACL2gen(addRunAcL2gen);
        return R.ok();
    }
    @GetMapping("/ACL2genData")
    public R<FileTreeVO> getData(){
        return R.ok(satelliteCalculateService.getACL2genData());
    }

    @PostMapping("/runSla")
    public R<Void> runSla(@Validated @RequestBody AddRunSla  addRunSla){
        satelliteCalculateService.runSla(addRunSla);
        return R.ok();
    }

    @GetMapping("/runSlaData")
    public R<FileTreeVO> getSla(){
        return R.ok(satelliteCalculateService.getSlaData());
    }


    // ===================== QA 模块（原有补全） =====================
    @GetMapping("/qaInput")
    public R<List<SatelliteCalculateFolder>> getQaInput(){
        return R.ok(satelliteCalculateService.getQaInputFolder());
    }
    @GetMapping("/qaOutput")
    public R<FileTreeVO> getQaOutput(SnrRequest sR){
        return R.ok(satelliteCalculateService.getQaOutputData(sR.getRunFileStr()));
    }
    @PostMapping("/qaPython1")
    public R<Void> runQaPython1(@Validated  AddRunQaPython addRunQaPython){
        satelliteCalculateService.runQaPython1(addRunQaPython);
        return R.ok();
    }
    @PostMapping("/qaPython2")
    public R<Void> runQaPython2(@Validated  AddRunQaPython addRunQaPython){
        satelliteCalculateService.runQaPython2(addRunQaPython);
        return R.ok();
    }
    @PostMapping("/qaPython3")
    public R<Void> runQaPython3(@Validated  AddRunQaPython addRunQaPython){
        satelliteCalculateService.runQaPython3(addRunQaPython);
        return R.ok();
    }
    @PostMapping("/qaPython4")
    public R<Void> runQaPython4(@Validated  AddRunQaPython addRunQaPython){
        satelliteCalculateService.runQaPython4(addRunQaPython);
        return R.ok();
    }
    @PostMapping("/qaPython5")
    public R<Void> runQaPython5(@Validated  AddRunQaPython addRunQaPython){
        satelliteCalculateService.runQaPython5(addRunQaPython);
        return R.ok();
    }
    @PostMapping("/qaPython6")
    public R<Void> runQaPython6(@Validated  AddRunQaPython addRunQaPython){
        satelliteCalculateService.runQaPython6(addRunQaPython);
        return R.ok();
    }

    // ===================== Wind 风场模块 =====================
    @PostMapping("/windSh")
    public R<Void> runWindSh(@Validated  AddRunWindSh addRunWindSh){
        satelliteCalculateService.runWindSh(addRunWindSh);
        return R.ok();
    }
    @PostMapping("/windPython1")
    public R<Void> runWindPython1(@Validated  AddRunWindPython addRunWindPython){
        satelliteCalculateService.runWindPython1(addRunWindPython);
        return R.ok();
    }
    @PostMapping("/windPython2")
    public R<Void> runWindPython2(@Validated  AddRunWindPython addRunWindPython){
        satelliteCalculateService.runWindPython2(addRunWindPython);
        return R.ok();
    }

    @GetMapping("/windInputShMul")
    public R<List<SatelliteCalculateFolder>> getWindInputShMul(){
        return R.ok(satelliteCalculateService.getWindInputShMulFolder());
    }
    @GetMapping("/windInputShSy3e")
    public R<List<SatelliteCalculateFolder>> getWindInputShSy3e(){
        return R.ok(satelliteCalculateService.getWindInputShSy3eFolder());
    }
    @GetMapping("/windInputShHy2b")
    public R<List<SatelliteCalculateFolder>> getWindInputShHy2b(){
        return R.ok(satelliteCalculateService.getWindInputShHy2bFolder());
    }
    @GetMapping("/windInputShHy2c")
    public R<List<SatelliteCalculateFolder>> getWindInputShHy2c(){
        return R.ok(satelliteCalculateService.getWindInputShHy2cFolder());
    }
    @GetMapping("/windInputPython1ccmp")
    public R<List<SatelliteCalculateFolder>> getWindInputPython1ccmp(){
        return R.ok(satelliteCalculateService.getWindInputPython1ccmpFolder());
    }
    @GetMapping("/windInputPython1fusion")
    public R<List<SatelliteCalculateFolder>> getWindInputPython1fusion(){
        return R.ok(satelliteCalculateService.getWindInputPython1fusionFolder());
    }
    @GetMapping("/windInputPython1land")
    public R<List<SatelliteCalculateFolder>> getWindInputPython1land(){
        return R.ok(satelliteCalculateService.getWindInputPython1landFolder());
    }
    @GetMapping("/windInputPython2Fusion")
    public R<List<SatelliteCalculateFolder>> getWindInputPython2Fusion(){
        return R.ok(satelliteCalculateService.getWindInputPython2FusionFolder());
    }
    @GetMapping("/windInputPython2Buoy")
    public R<List<SatelliteCalculateFolder>> getWindInputPython2Buoy(){
        return R.ok(satelliteCalculateService.getWindInputPython2BuoyFolder());
    }

    @GetMapping("/windMiddleShFy3e")
    public R<FileTreeVO> getWindMiddleShFy3e(SnrRequest sR){
        return R.ok(satelliteCalculateService.getWindMiddleShFy3eData(sR.getRunFileStr()));
    }
    @GetMapping("/windMiddleShHy2b")
    public R<FileTreeVO> getWindMiddleShHy2b(SnrRequest sR){
        return R.ok(satelliteCalculateService.getWindMiddleShHy2bData(sR.getRunFileStr()));
    }
    @GetMapping("/windMiddleShHy2c")
    public R<FileTreeVO> getWindMiddleShHy2c(SnrRequest sR){
        return R.ok(satelliteCalculateService.getWindMiddleShHy2cData(sR.getRunFileStr()));
    }

    @GetMapping("/windOutputSh")
    public R<FileTreeVO> getWindOutputSh(SnrRequest sR){
        return R.ok(satelliteCalculateService.getWindOutputShData(sR.getRunFileStr()));
    }
    @GetMapping("/windOutputPython1")
    public R<FileTreeVO> getWindOutputPython1(SnrRequest sR){
        return R.ok(satelliteCalculateService.getWindOutputPython1Data(sR.getRunFileStr()));
    }
    @GetMapping("/windOutputPython2")
    public R<FileTreeVO> getWindOutputPython2(SnrRequest sR){
        return R.ok(satelliteCalculateService.getWindOutputPython2Data(sR.getRunFileStr()));
    }

    // ===================== Sarwind SAR风场模块 =====================
    @GetMapping("/sarwindInput")
    public R<List<SatelliteCalculateFolder>> getSarwindInput(){
        return R.ok(satelliteCalculateService.getSarwindInputFolder());
    }
    @GetMapping("/sarwindOutput")
    public R<FileTreeVO> getSarwindOutput(SnrRequest sR){
        return R.ok(satelliteCalculateService.getSarwindOutputData(sR.getRunFileStr()));
    }
    @PostMapping("/sarwindPython")
    public R<Void> runSarwindPython(@Validated  AddRunSarwindPython addRunSarwindPython){
        satelliteCalculateService.runSarwindPython(addRunSarwindPython);
        return R.ok();
    }

    // ===================== SWH 有效波高模块 =====================
    @GetMapping("/swhInput")
    public R<List<SatelliteCalculateFolder>> getSwhInput(){
        return R.ok(satelliteCalculateService.getSwhInputFolder());
    }
    @GetMapping("/swhOutput")
    public R<FileTreeVO> getSwhOutput(SnrRequest sR){
        return R.ok(satelliteCalculateService.getSwhOutputData(sR.getRunFileStr()));
    }
    @PostMapping("/swhSh")
    public R<Void> runSwhSh(@Validated  AddRunSwhSh addRunSwhSh){
        satelliteCalculateService.runSwhSh(addRunSwhSh);
        return R.ok();
    }
    @PostMapping("/swhPython")
    public R<Void> runSwhPython(@Validated  AddRunSwhPython addRunSwhPython){
        satelliteCalculateService.runSwhPython(addRunSwhPython);
        return R.ok();
    }

}
