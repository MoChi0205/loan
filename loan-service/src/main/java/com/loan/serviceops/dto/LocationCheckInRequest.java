package com.loan.serviceops.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 出发 / 返回打卡请求：单点定位 + 现场照片，二者缺一不可。
 *
 * <p>照片先经 {@code POST /api/admin/outing/{outingNo}/photo} 上传拿到 fileKey，
 * 再随本请求提交，避免「只提交坐标无凭证」的空打卡。
 */
@Data
public class LocationCheckInRequest {
    /** 纬度（-90 ~ 90）。 */
    private BigDecimal latitude;
    /** 经度（-180 ~ 180）。 */
    private BigDecimal longitude;
    /** 定位精度（米，须 > 0）。 */
    private BigDecimal accuracyMeters;
    /** 定位文本（逆地理结果或人工填写的地址描述）。 */
    private String locationText;
    /** 定位采集时间；与服务器时间相差超过 10 分钟判为过期。 */
    private LocalDateTime collectedAt;
    /** 现场照片 fileKey（必填，由打卡照片上传接口返回）。 */
    private String photoFileKey;
}
