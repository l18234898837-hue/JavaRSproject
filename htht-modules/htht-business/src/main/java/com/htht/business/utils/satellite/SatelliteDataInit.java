package com.htht.business.utils.satellite;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.htht.business.domain.SysSatelliteInfo;
import com.htht.common.utils.OkHttpUtils;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.List;

public class SatelliteDataInit {

    private static String SATELLITE_DATA_API_URL = "https://3d.img.net/orbitService/front/api/satellite/list?resolution=&country=&satelliteType=";

    private static final ObjectMapper objectMapper = new ObjectMapper();

    public static void main(String[] args) {
        //天仪卫星 雷达 光学 高光谱
        String result = OkHttpUtils.builder()
            .url(SATELLITE_DATA_API_URL)
            .post(true)
            .sync();
        try {
            List<SysSatelliteInfo> infoList = new ArrayList<>();
            JsonNode rootNode = objectMapper.readTree(result);
            if (rootNode.has("code") && rootNode.get("code").asInt() == 0) {
                ArrayNode arrayNode = (ArrayNode) rootNode.get("data");
                for (JsonNode node : arrayNode) {
                    SysSatelliteInfo info = new SysSatelliteInfo();
                    info.setId(node.path("id").asLong());
                    info.setSatelliteName(node.path("dataSource").asText());
                    info.setSatelliteCode(node.path("code").asText());
                    info.setResolution(node.path("resolution").asDouble());
                    info.setNoradNumber(node.path("noradNumber").asText());
                    info.setClothWidth(node.path("clothWidth").asLong());
                    info.setOrbitalAltitude(node.path("orbitalAltitude").asInt());
                    info.setSatelliteType("");
                    info.setCountry(node.path("country").asText());
                    info.setSatelliteCategory(node.path("type").asText());
                    info.setSideAngle(node.path("sideAngle").asText());

                    infoList.add(info);
                }
            }
            writeToDatabase(infoList);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

    }

    public static void writeToDatabase(List<SysSatelliteInfo> infos) throws Exception {

        String sql = "INSERT INTO sys_satellite_info (id,satellite_name,satellite_code,resolution," +
            "country,norad_number,cloth_width,side_angle,satellite_type,orbital_altitude,altitude,satellite_identity_line," +
            "satellite_source,satellite_series,launch_date,satellite_status,remarks,del_flag, satellite_category) " +
            "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";

        try (Connection connection = DBConnectionUtil.getConnection(
            DBConnectionUtil.POSTGRESQL,
            "192.168.2.118",
            65432,
            "shallow_topography",
            "postgres",
            "hqxkj168++");
             PreparedStatement stmt = connection.prepareStatement(sql);) {
            for (SysSatelliteInfo info : infos) {
                stmt.setLong(1, info.getId());
                stmt.setString(2, info.getSatelliteName());
                stmt.setString(3, info.getSatelliteCode());
                stmt.setDouble(4, info.getResolution());
                stmt.setString(5, info.getCountry());
                stmt.setString(6, info.getNoradNumber());
                stmt.setLong(7, info.getClothWidth());
                stmt.setString(8, info.getSideAngle());
                stmt.setString(9, info.getSatelliteType());
                stmt.setInt(10, info.getOrbitalAltitude());
                stmt.setInt(11, 0);
                stmt.setString(12, info.getSatelliteIdentityLine());
                stmt.setString(13, info.getSatelliteSource());
                stmt.setString(14, info.getSatelliteSeries());
                stmt.setDate(15, null);
                stmt.setString(16, info.getSatelliteStatus());
                stmt.setString(17, info.getRemarks());
                stmt.setString(18, info.getDelFlag());
                stmt.setString(19, info.getSatelliteCategory());
                stmt.addBatch();
            }
            stmt.executeBatch();
        }
    }
}
