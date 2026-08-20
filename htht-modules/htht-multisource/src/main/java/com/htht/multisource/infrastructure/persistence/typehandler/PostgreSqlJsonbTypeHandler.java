package com.htht.multisource.infrastructure.persistence.typehandler;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.postgresql.util.PGobject;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public final class PostgreSqlJsonbTypeHandler extends BaseTypeHandler<String> {
    @Override
    public void setNonNullParameter(PreparedStatement statement, int index, String value, JdbcType jdbcType) throws SQLException {
        PGobject json = new PGobject();
        json.setType("jsonb");
        json.setValue(value);
        statement.setObject(index, json);
    }
    @Override public String getNullableResult(ResultSet resultSet, String columnName) throws SQLException { return resultSet.getString(columnName); }
    @Override public String getNullableResult(ResultSet resultSet, int columnIndex) throws SQLException { return resultSet.getString(columnIndex); }
    @Override public String getNullableResult(CallableStatement statement, int columnIndex) throws SQLException { return statement.getString(columnIndex); }
}
