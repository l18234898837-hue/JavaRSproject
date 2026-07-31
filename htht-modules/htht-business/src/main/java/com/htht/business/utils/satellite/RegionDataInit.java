package com.htht.business.utils.satellite;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.htht.business.domain.SysAdministrativeRegions;
import com.htht.common.utils.IDUtil;
import com.htht.common.utils.OkHttpUtils;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.List;

public class RegionDataInit {


    private static final ObjectMapper objectMapper = new ObjectMapper();


    public static void main(String[] args) {

        List<SysAdministrativeRegions> regions = new ArrayList<>();
        try {
            String provinceResult = OkHttpUtils.builder()
                .url("https://3d.img.net/orbitService/front/api/area/list?type=1")
                .post(true)
                .addParam("type", "1")
                .sync();
           JsonNode rootNode = objectMapper.readTree(provinceResult);
           if (rootNode.has("code") && rootNode.get("code").asInt() == 0) {
               ArrayNode arrayNode = (ArrayNode) rootNode.get("data");
               List<SysAdministrativeRegions> list1 = getRegions(arrayNode);
               int i = 0;
               for (SysAdministrativeRegions region : list1) {
                   i ++;
                   region.setId(IDUtil.getLongID());
                   region.setParentCode("0");
                   region.setRegionsLevel(2);
                   regions.add(region);
                   String cityResult = OkHttpUtils.builder()
                       .url("https://3d.img.net/orbitService/front/api/area/list?type=2&code=" + region.getRegionsCode())
                       .post(true)
                       .addParam("type", "2")
                       .sync();
                   System.out.println("i====" + i );
                   JsonNode rootNode2 = objectMapper.readTree(cityResult);
                   if (rootNode2.has("code") && rootNode2.get("code").asInt() == 0) {
                       ArrayNode arrayNode2 = (ArrayNode) rootNode2.get("data");
                       List<SysAdministrativeRegions> list2 =  getRegions(arrayNode2);
                       for (SysAdministrativeRegions region2 : list2) {
                           region2.setParentCode(region.getRegionsCode());
                           region2.setRegionsLevel(3);
                           region2.setId(IDUtil.getLongID());
                           regions.add(region2);
                           // 构建请求参数
                           String areaResult = OkHttpUtils.builder()
                               .url("https://3d.img.net/orbitService/front/api/area/list?type=3&code=" + region2.getRegionsCode())
                               .post(true)
                               .addParam("type", "3")
                               .addParam("code", region2.getRegionsCode())
                               .sync();
                           JsonNode rootNode3 = objectMapper.readTree(areaResult);
                           if (rootNode3.has("code") && rootNode3.get("code").asInt() == 0) {
                               ArrayNode arrayNode3 = (ArrayNode) rootNode3.get("data");
                               List<SysAdministrativeRegions> list3 =  getRegions(arrayNode3);
                               for (SysAdministrativeRegions region3 : list3) {
                                   region3.setId(IDUtil.getLongID());
                                   region3.setParentCode(region2.getRegionsCode());
                                   region3.setRegionsLevel(4);
                                   regions.add(region3);
                               }

                           }
                       }
                   }
               }

           }


            writeRegionToDatabase(regions);

        } catch (Exception e) {
            throw new RuntimeException(e);
        }


    }

    private static List<SysAdministrativeRegions> getRegions(ArrayNode arrayNode) throws JsonProcessingException {
        List<SysAdministrativeRegions> regions = new ArrayList<>();
        for (JsonNode node : arrayNode) {
            SysAdministrativeRegions region = new SysAdministrativeRegions();
            region.setRegionsCode(node.get("code").asText());
            region.setRegionsName(node.get("name").asText());
            region.setPrettyname(node.get("prettyname").asText());
            region.setLat(Float.parseFloat(node.get("lat").asText()));
            region.setLon(Float.parseFloat(node.get("lon").asText()));
            Object obj = node.get("boundary");
            region.setBoundary(obj.toString());
            JsonNode boundaryNode = objectMapper.readTree(obj.toString());
            region.setBoundaryType(boundaryNode.get("type").asText());
            regions.add(region);
        }

        return regions;
    }

    /**
     * 将 geography 数据写入数据库
     */
    public static void writeRegionToDatabase(List<SysAdministrativeRegions> regions) throws Exception {
        String sql = "INSERT INTO sys_administrative_regions (id,regions_code,parent_code,regions_name,prettyname,regions_level,lon,lat,boundary_type,boundary,del_flag) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ? )";

        try (Connection connection = DBConnectionUtil.getConnection(
            DBConnectionUtil.POSTGRESQL,
            "192.168.2.118",
            65432,
            "shallow_topography",
            "postgres",
            "hqxkj168++");
             PreparedStatement stmt = connection.prepareStatement(sql);) {

            for (SysAdministrativeRegions region : regions) {
                stmt.setLong(1, region.getId());
                stmt.setString(2, region.getRegionsCode());
                stmt.setString(3, region.getParentCode());
                stmt.setString(4, region.getRegionsName());
                stmt.setString(5, region.getPrettyname());
                stmt.setInt(6, region.getRegionsLevel());
                stmt.setFloat(7, region.getLon());
                stmt.setFloat(8, region.getLat());
                stmt.setString(9, region.getBoundaryType());
                stmt.setString(10, region.getBoundary());
                stmt.setString(11, region.getDelFlag());
                stmt.addBatch();
            }

            stmt.executeBatch();
        }
    }


}
