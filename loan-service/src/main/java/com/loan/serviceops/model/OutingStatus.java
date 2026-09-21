package com.loan.serviceops.model;

/**
 * 员工上门外出状态。
 *
 * <p>履约链路：本人提交申请（{@link #PENDING_REVIEW}）→ 主管审核
 * （{@link #READY} / {@link #REJECTED}）→ 出发打卡（{@link #IN_PROGRESS}）
 * → 返回打卡（{@link #COMPLETED}）。
 *
 * <p>「不允许他人代录」由服务层保证（只有预约的主服务顾问本人可创建），
 * 状态机只负责流转合法性。
 */
public enum OutingStatus {
    /** 草稿：仅保留给未来「先存后提」场景，当前创建即进入待审核。 */
    DRAFT,
    /** 已提交申请，待主管审核。 */
    PENDING_REVIEW,
    /** 审核驳回，可修改后重新提交。 */
    REJECTED,
    /** 审核通过，待出发打卡。 */
    READY,
    /** 已出发打卡，在外进行中。 */
    IN_PROGRESS,
    /** 已返回打卡，履约完成。 */
    COMPLETED,
    /** 已取消。 */
    CANCELLED;

    /** 终态：不可再流转。 */
    public boolean isTerminal() {
        return this == COMPLETED || this == CANCELLED;
    }
}
