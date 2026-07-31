package com.htht.algorithm.data.controller;


import cn.dev33.satoken.annotation.SaIgnore;
import com.htht.common.core.controller.BaseController;
import com.htht.common.core.domain.R;
import com.htht.algorithm.data.core.service.impl.GridDataService;
import com.htht.algorithm.data.tile.grid.enums.ImageType;
import lombok.RequiredArgsConstructor;
import org.apache.logging.log4j.util.Strings;
import org.springframework.util.Base64Utils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * 栅格切片
 *
 * @author wjx
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/algorithm/grid/tile")
public class GridTileController extends BaseController {

    private final GridDataService gridDataService;

    /**
     * 影像实时切片
     *
     * @param request
     * @param response
     * @param layerId
     * @param type
     * @param tileSize
     * @param zoom
     * @param row
     * @param col
     */
    @SaIgnore
    @GetMapping
    public void tile(
        HttpServletRequest request,
        HttpServletResponse response,
        @RequestParam(value = "id") String layerId,
        @RequestParam(value = "type", defaultValue = "PNG", required = false) ImageType type,
        @RequestParam(value = "tileSize", defaultValue = "256", required = false) int tileSize,
        @RequestParam(value = "zoom") int zoom,
        @RequestParam(value = "row") int row,
        @RequestParam(value = "col") int col,
        @RequestParam(value = "pm", required = false) String pm
    ) throws IOException {

        String params = Strings.isNotBlank(pm) ? new String(Base64Utils.decodeFromString(pm.replaceAll(" ", "+"))) : null;
        gridDataService.tile(request, response, layerId, tileSize, zoom, row, col, params);
    }

    /**
     * 根据图层ID清理切片缓存
     *
     * @param layerId
     */
    @GetMapping(value = "/clear/{layerId}")
    public R<Void> clearCache(@PathVariable(value = "layerId") String layerId) {
        gridDataService.clearCache(layerId);
        return R.ok();
    }
}
