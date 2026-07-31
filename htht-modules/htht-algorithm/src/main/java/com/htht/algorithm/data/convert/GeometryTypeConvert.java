package com.htht.algorithm.data.convert;

import cn.hutool.core.util.StrUtil;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedTypes;
import org.postgis.Geometry;
import org.postgis.PGgeometry;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * String类型和PostGIS类型转换工具
 *
 * @author wjx
 * @since 2023-5-29
 */

@MappedTypes({String.class})
public class GeometryTypeConvert extends BaseTypeHandler<String> {

    @Override
    public void setNonNullParameter(PreparedStatement preparedStatement, int i, String s, JdbcType jdbcType) throws SQLException {
        // 将WKT字符串转换为PGgeometry对象
        if (StrUtil.isEmpty(s)) {
            preparedStatement.setObject(i, null);
        } else {
            PGgeometry pGgeometry = new PGgeometry(s);
            Geometry geometry = pGgeometry.getGeometry();
            geometry.setSrid(4326);
            preparedStatement.setObject(i, pGgeometry);
        }

    }

    @Override
    public String getNullableResult(ResultSet resultSet, String columnName) throws SQLException {
        // 关键修改：使用getObject而不是getString
        PGgeometry pGgeometry = (PGgeometry) resultSet.getObject(columnName);
        return getResult(pGgeometry);
    }

    @Override
    public String getNullableResult(ResultSet resultSet, int columnIndex) throws SQLException {
        // 关键修改：使用getObject而不是getString
        PGgeometry pGgeometry = (PGgeometry) resultSet.getObject(columnIndex);
        return getResult(pGgeometry);
    }

    @Override
    public String getNullableResult(CallableStatement callableStatement, int columnIndex) throws SQLException {
        // 关键修改：使用getObject而不是getString
        PGgeometry pGgeometry = (PGgeometry) callableStatement.getObject(columnIndex);
        return getResult(pGgeometry);
    }

    private String getResult(PGgeometry pGgeometry) throws SQLException {
        if (pGgeometry == null) {
            return null;
        }
        // 直接获取几何对象的WKT表示
        Geometry geometry = pGgeometry.getGeometry();
        return geometry.toString();
    }

    // 保留原有的getResult方法作为备用
    private String getResult(String string) throws SQLException {
        if (string == null) {
            return null;
        }
        PGgeometry pGgeometry = new PGgeometry(string);
        Geometry geometry = pGgeometry.getGeometry();
        return geometry.toString();
    }
}
