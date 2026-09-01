package com.loan.engine;

import com.loan.engine.enums.Grade;
import com.loan.engine.enums.StepResult;
import com.loan.engine.enums.TotalResult;
import com.loan.engine.execute.AdmissionContext;
import com.loan.engine.rule.RuleHandlerResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 规则引擎结果态单测（M3 L2）：枚举 fromCode 往返、RuleHandlerResult 工厂、AdmissionContext 取事实/上下文键。
 */
class EngineResultStateTest {

    @Test
    @DisplayName("TotalResult.fromCode 往返与异常分支")
    void totalResultFromCode() {
        assertEquals(TotalResult.PASS, TotalResult.fromCode("PASS"));
        assertEquals(TotalResult.CONDITION, TotalResult.fromCode("CONDITION"));
        assertEquals(TotalResult.REJECT, TotalResult.fromCode("REJECT"));
        assertEquals(TotalResult.SKIP_SEGMENT_MISMATCH, TotalResult.fromCode("SKIP_SEGMENT_MISMATCH"));
        assertEquals(TotalResult.ERROR, TotalResult.fromCode("ERROR"));
        assertNull(TotalResult.fromCode("UNKNOWN"));
        assertNull(TotalResult.fromCode(null));
        for (TotalResult r : TotalResult.values()) {
            assertSame(r, TotalResult.fromCode(r.getCode()));
        }
    }

    @Test
    @DisplayName("StepResult.fromCode 往返与异常分支")
    void stepResultFromCode() {
        assertEquals(StepResult.PASS, StepResult.fromCode("PASS"));
        assertEquals(StepResult.FAIL, StepResult.fromCode("FAIL"));
        assertEquals(StepResult.SKIP, StepResult.fromCode("SKIP"));
        assertEquals(StepResult.SKIP_SHORT_CIRCUIT, StepResult.fromCode("SKIP_SHORT_CIRCUIT"));
        assertEquals(StepResult.SKIP_SEGMENT_MISMATCH, StepResult.fromCode("SKIP_SEGMENT_MISMATCH"));
        assertEquals(StepResult.ERROR, StepResult.fromCode("ERROR"));
        assertNull(StepResult.fromCode("UNKNOWN"));
        assertNull(StepResult.fromCode(null));
        for (StepResult r : StepResult.values()) {
            assertSame(r, StepResult.fromCode(r.getCode()));
        }
    }

    @Test
    @DisplayName("Grade.fromCode 往返与异常分支")
    void gradeFromCode() {
        assertEquals(Grade.HIGH, Grade.fromCode("HIGH"));
        assertEquals(Grade.MIDDLE, Grade.fromCode("MIDDLE"));
        assertEquals(Grade.LOW, Grade.fromCode("LOW"));
        assertNull(Grade.fromCode("UNKNOWN"));
        assertNull(Grade.fromCode(null));
        for (Grade g : Grade.values()) {
            assertSame(g, Grade.fromCode(g.getCode()));
        }
    }

    @Test
    @DisplayName("RuleHandlerResult 工厂方法语义正确")
    void ruleHandlerResultFactories() {
        RuleHandlerResult pass = RuleHandlerResult.pass();
        assertEquals(StepResult.PASS, pass.getStepResult());
        assertFalse(pass.isChainTerminate());
        assertNull(pass.getRejectReason());

        RuleHandlerResult passSnap = RuleHandlerResult.pass("{\"x\":1}");
        assertEquals(StepResult.PASS, passSnap.getStepResult());
        assertEquals("{\"x\":1}", passSnap.getQuerySnapshotJson());

        RuleHandlerResult fail = RuleHandlerResult.fail("reason");
        assertEquals(StepResult.FAIL, fail.getStepResult());
        assertEquals("reason", fail.getRejectReason());

        assertEquals(StepResult.SKIP, RuleHandlerResult.skip("r").getStepResult());
        assertEquals(StepResult.SKIP_SEGMENT_MISMATCH, RuleHandlerResult.segmentMismatch("r").getStepResult());
        assertEquals(StepResult.ERROR, RuleHandlerResult.error("r").getStepResult());

        RuleHandlerResult chain = RuleHandlerResult.chainPass("white-list");
        assertEquals(StepResult.PASS, chain.getStepResult());
        assertTrue(chain.isChainTerminate());
        assertEquals("white-list", chain.getRejectReason());
    }

    @Test
    @DisplayName("AdmissionContext 取事实与上下文键")
    void admissionContext() {
        java.util.Map<String, Object> facts = new java.util.HashMap<>();
        facts.put("k", "v");
        AdmissionContext byClient = AdmissionContext.builder()
                .clientProfileId(5L)
                .fieldValues(facts)
                .build();
        assertEquals("v", byClient.getFact("k"));
        assertNull(byClient.getFact("missing"));
        assertEquals("5", byClient.contextKey()); // 优先用 clientProfileId

        AdmissionContext byTrace = AdmissionContext.builder()
                .traceUuid("trace-1")
                .fieldValues(facts)
                .build();
        assertEquals("trace-1", byTrace.contextKey()); // 否则用 traceUuid
    }
}
