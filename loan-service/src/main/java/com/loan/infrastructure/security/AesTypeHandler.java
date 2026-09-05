package com.loan.infrastructure.security;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * AES 字段级加密 TypeHandler（敏感列落库自动加密，读取自动解密）。
 *
 * <p>用法：在实体敏感字段标注
 * {@code @TableField(typeHandler = AesTypeHandler.class)}。
 * 配合 SHA-256 哈希列（如 phone_hash）做查重/等值查询（加密不影响索引）。
 *
 * @author loan-platform
 */
public class AesTypeHandler extends BaseTypeHandler<String> {

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, String parameter, JdbcType jdbcType)
            throws SQLException {
        ps.setString(i, AesUtils.encrypt(parameter));
    }

    @Override
    public String getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return AesUtils.decrypt(rs.getString(columnName));
    }

    @Override
    public String getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return AesUtils.decrypt(rs.getString(columnIndex));
    }

    @Override
    public String getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return AesUtils.decrypt(cs.getString(columnIndex));
    }
}
