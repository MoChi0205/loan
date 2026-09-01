package com.loan.utils;

/**
 * 数据脱敏工具类（手机号 / 身份证 / 姓名等，读取接口强制脱敏）。
 *
 * @author loan-platform
 */
public final class DesensitizeUtils {

    private DesensitizeUtils() {
    }

    /**
     * 手机号脱敏：138****5678。
     *
     * @param phone 手机号
     * @return 脱敏后手机号，空值原样返回
     */
    public static String phone(String phone) {
        if (phone == null || phone.length() < 7) {
            return phone;
        }
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }

    /**
     * 身份证号脱敏：保留前 6 后 4。
     *
     * @param idCard 身份证号
     * @return 脱敏后身份证号，空值原样返回
     */
    public static String idCard(String idCard) {
        if (idCard == null || idCard.length() < 10) {
            return idCard;
        }
        return idCard.substring(0, 6) + "********" + idCard.substring(idCard.length() - 4);
    }

    /**
     * 姓名脱敏：保留姓，其余用 * 替代（如「张**」）。
     *
     * @param name 姓名
     * @return 脱敏后姓名，空值原样返回
     */
    public static String name(String name) {
        if (name == null || name.isEmpty()) {
            return name;
        }
        if (name.length() == 1) {
            return name;
        }
        StringBuilder sb = new StringBuilder(name.substring(0, 1));
        for (int i = 1; i < name.length(); i++) {
            sb.append('*');
        }
        return sb.toString();
    }

    /**
     * 统一社会信用代码脱敏：保留前 4 后 4。
     *
     * @param creditCode 统一社会信用代码
     * @return 脱敏后信用代码，空值原样返回
     */
    public static String creditCode(String creditCode) {
        if (creditCode == null || creditCode.length() < 8) {
            return creditCode;
        }
        return creditCode.substring(0, 4) + "********" + creditCode.substring(creditCode.length() - 4);
    }

    /**
     * 银行卡号脱敏：保留前 4 后 4（参考 tse SensitiveDataMaskUtil）。
     *
     * @param bankCard 银行卡号
     * @return 脱敏后银行卡号，空值原样返回
     */
    public static String bankCard(String bankCard) {
        if (bankCard == null || bankCard.length() < 8) {
            return bankCard;
        }
        int starCount = Math.max(1, bankCard.length() - 8);
        StringBuilder stars = new StringBuilder(starCount);
        for (int i = 0; i < starCount; i++) {
            stars.append('*');
        }
        return bankCard.substring(0, 4) + stars + bankCard.substring(bankCard.length() - 4);
    }

    /**
     * 邮箱脱敏：保留首字符与 @ 后域名（如 z***@qq.com）。
     *
     * @param email 邮箱
     * @return 脱敏后邮箱，空值原样返回
     */
    public static String email(String email) {
        if (email == null || email.isEmpty()) {
            return email;
        }
        int at = email.indexOf('@');
        if (at <= 0) {
            return "***";
        }
        return email.charAt(0) + "***" + email.substring(at);
    }
}
