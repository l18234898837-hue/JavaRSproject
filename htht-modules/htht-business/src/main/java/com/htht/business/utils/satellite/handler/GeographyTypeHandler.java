package com.htht.business.utils.satellite.handler;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.postgis.Geometry;
import org.postgis.PGgeometry;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class GeographyTypeHandler extends BaseTypeHandler<Geometry> { // 使用通用Geometry

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i,
                                    Geometry parameter, JdbcType jdbcType) throws SQLException {
        // 确保你的几何对象带有正确的SRID（通常是4326），这对geography至关重要！
        PGgeometry geom = new PGgeometry(parameter);
        ps.setObject(i, geom);
    }

    @Override
    public Geometry getNullableResult(ResultSet rs, String columnName) throws SQLException {
        PGgeometry geom = (PGgeometry) rs.getObject(columnName);
        return geom != null ? geom.getGeometry() : null; // 返回通用Geometry类型
    }

    @Override
    public Geometry getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        PGgeometry geom = (PGgeometry) rs.getObject(columnIndex);
        return geom != null ? geom.getGeometry() : null;
    }

    @Override
    public Geometry getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        PGgeometry geom = (PGgeometry) cs.getObject(columnIndex);
        return geom != null ? geom.getGeometry() : null;
    }
}
