package com.loan.serviceops.controller;

import com.loan.api.dto.PageResult;
import com.loan.common.Result;
import com.loan.common.ResultCode;
import com.loan.common.util.PageParams;
import com.loan.context.CurrentUser;
import com.loan.context.LoanUser;
import com.loan.exception.BusinessException;
import com.loan.infrastructure.oss.OssObjectOpenResult;
import com.loan.infrastructure.oss.OssStorageService;
import com.loan.log.annotation.OpLog;
import com.loan.serviceops.dto.LocationCheckInRequest;
import com.loan.serviceops.dto.OutingCreateRequest;
import com.loan.serviceops.dto.ReasonRequest;
import com.loan.serviceops.dto.StaffOutingDTO;
import com.loan.serviceops.service.OutingService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.util.StreamUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Web 员工端外出名单、申请/审核与本人双打卡接口。
 *
 * <p>权限边界：
 * <ul>
 *   <li>创建申请：仅预约的主服务顾问本人（服务层强制，不接受代录）；</li>
 *   <li>审核：部门经理本部门 / 老板 / 运营 / 超管，且一律禁止自审；</li>
 *   <li>出发·返回打卡、照片上传：仅申请人本人；</li>
 *   <li>照片预览：申请人本人，或对该外出有审核权的角色。</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/admin/outing")
@RequiredArgsConstructor
public class AdminOutingController {

    /** 打卡照片大小上限：10MB（手机原图足够，避免大文件拖慢审核）。 */
    private static final long MAX_PHOTO_BYTES = 10L * 1024 * 1024;

    /** 打卡照片允许的扩展名。 */
    private static final List<String> PHOTO_EXTS = Arrays.asList(".jpg", ".jpeg", ".png", ".webp");

    private final OutingService outingService;
    private final OssStorageService ossStorageService;

    @GetMapping("/day")
    public Result<PageResult<StaffOutingDTO>> pageDay(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @CurrentUser LoanUser user) {
        return Result.ok(outingService.day(date, status, user,
                PageParams.page(page), PageParams.size(size)));
    }

    /** 提交外出申请（本人）。 */
    @PostMapping
    @OpLog(bizType = "员工外出", action = "CREATE")
    public Result<String> create(@RequestBody OutingCreateRequest request,
                                 @CurrentUser LoanUser user) {
        return Result.ok(outingService.create(request, user));
    }

    /** 审核通过（主管）。 */
    @PostMapping("/{outingNo}/approve")
    @OpLog(bizType = "员工外出", action = "APPROVE")
    public Result<Void> approve(@PathVariable String outingNo,
                                @RequestBody(required = false) ReasonRequest request,
                                @CurrentUser LoanUser user) {
        outingService.approve(outingNo, request == null ? null : request.getReason(), user);
        return Result.ok();
    }

    /** 审核驳回（主管，原因必填）。 */
    @PostMapping("/{outingNo}/reject")
    @OpLog(bizType = "员工外出", action = "REJECT")
    public Result<Void> reject(@PathVariable String outingNo,
                               @RequestBody(required = false) ReasonRequest request,
                               @CurrentUser LoanUser user) {
        outingService.reject(outingNo, request == null ? null : request.getReason(), user);
        return Result.ok();
    }

    /** 驳回后修改并重新提交（本人）。 */
    @PostMapping("/{outingNo}/resubmit")
    @OpLog(bizType = "员工外出", action = "RESUBMIT")
    public Result<Void> resubmit(@PathVariable String outingNo,
                                 @RequestBody(required = false) OutingCreateRequest request,
                                 @CurrentUser LoanUser user) {
        outingService.resubmit(outingNo, request, user);
        return Result.ok();
    }

    /**
     * 上传打卡照片（本人），返回 fileKey 供出发/返回打卡携带。
     *
     * <p>只落 OSS 文件并把 fileKey 记在 t_staff_outing 上，不写 t_service_attachment：
     * 该表没有外出关联列，写进去反而形成无主附件；打卡照片的权威记录就是外出行本身。
     */
    @PostMapping("/{outingNo}/photo")
    @OpLog(bizType = "员工外出", action = "UPLOAD_PHOTO")
    public Result<Map<String, Object>> uploadPhoto(@PathVariable String outingNo,
                                                   @RequestParam("file") MultipartFile file,
                                                   @CurrentUser LoanUser user) {
        outingService.requireCheckInUploader(outingNo, user);
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "打卡照片为空");
        }
        if (file.getSize() > MAX_PHOTO_BYTES) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "打卡照片不能超过 10MB");
        }
        String contentType = file.getContentType() == null ? "" : file.getContentType().toLowerCase();
        if (!contentType.startsWith("image/")) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "打卡照片必须是图片文件");
        }
        String original = file.getOriginalFilename() == null ? "photo" : file.getOriginalFilename();
        int dot = original.lastIndexOf('.');
        String ext = dot > 0 ? original.substring(dot).toLowerCase() : "";
        if (!PHOTO_EXTS.contains(ext)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "打卡照片仅支持 jpg / jpeg / png / webp");
        }
        String fileKey = "att" + UUID.randomUUID().toString().replace("-", "");
        try {
            ossStorageService.upload(fileKey + ext, file.getInputStream(), file.getSize());
        } catch (IOException e) {
            throw new BusinessException(ResultCode.THIRD_PARTY_ERROR, "打卡照片上传失败");
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("fileKey", fileKey);
        data.put("fileName", original);
        data.put("fileSize", file.getSize());
        return Result.ok(data);
    }

    /**
     * 预览打卡照片（申请人本人或有审核权者）。
     *
     * @param outingNo 外出业务编号
     * @param phase    DEPART 出发 / RETURN 返回
     */
    @GetMapping("/{outingNo}/photo")
    public void photo(@PathVariable String outingNo,
                      @RequestParam(defaultValue = "DEPART") String phase,
                      @CurrentUser LoanUser user,
                      HttpServletResponse response) throws IOException {
        OssObjectOpenResult object = outingService.openCheckInPhoto(outingNo, phase, user);
        response.setContentType(object.getContentType() == null
                ? "application/octet-stream" : object.getContentType());
        response.setContentLengthLong(object.getContentLength());
        response.setHeader("Content-Disposition", "inline");
        try (InputStream in = object.getInputStream()) {
            StreamUtils.copy(in, response.getOutputStream());
        }
    }

    /** 出发打卡（本人，须带定位 + 照片）。 */
    @PostMapping("/{outingNo}/depart")
    @OpLog(bizType = "员工外出", action = "DEPART_CHECK_IN")
    public Result<Void> depart(@PathVariable String outingNo,
                               @RequestBody LocationCheckInRequest request,
                               @CurrentUser LoanUser user) {
        outingService.depart(outingNo, request, user);
        return Result.ok();
    }

    /** 返回打卡（本人，须带定位 + 照片）。 */
    @PostMapping("/{outingNo}/return")
    @OpLog(bizType = "员工外出", action = "RETURN_CHECK_IN")
    public Result<Void> returnFromOuting(@PathVariable String outingNo,
                                         @RequestBody LocationCheckInRequest request,
                                         @CurrentUser LoanUser user) {
        outingService.returnFromOuting(outingNo, request, user);
        return Result.ok();
    }
}
