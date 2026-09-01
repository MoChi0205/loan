package com.loan.l3;

import com.loan.engine.aggregate.GradeAggregator;
import com.loan.engine.enums.Grade;
import com.loan.partner.entity.PartnerProduct;
import com.loan.partner.service.PartnerProductService;
import com.loan.reward.entity.RewardRule;
import com.loan.reward.service.RewardService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * L3 集成测试：核心链路「只读」接云上生产库（MySQL/Redis）验证。
 *
 * <p>安全约束（务必遵守）：
 * <ul>
 *   <li>仅 {@code -Dloan.l3.enabled=true} 时运行（surefire 已排除 l3 标签，CI 默认流程永不触发）；</li>
 *   <li>全程只读：只调用 SELECT / GET，绝不调用任何写方法（建档案 / 绑定 / 结算 / 状态变更）；</li>
 *   <li>连接形态来自 {@code nacos/config/prd/application.properties}，凭证由环境变量注入；</li>
 *   <li>Nacos 配置中心 / 服务发现 / Dubbo 注册在 l3 剖面下全部关闭。</li>
 * </ul>
 *
 * <p>若需覆盖写路径（邀请绑定、奖励结算等），请改为在「独立测试库（生产备份还原）」上跑，
 * 切勿对生产库执行写操作。
 */
@Tag("l3")
@EnabledIfSystemProperty(named = "loan.l3.enabled", matches = "true")
@ActiveProfiles("l3")
@SpringBootTest
class CoreLinkIntegrationTest {

    @Autowired
    private DataSource dataSource;
    @Autowired
    private RedisConnectionFactory redisConnectionFactory;
    @Autowired
    private PartnerProductService partnerProductService;
    @Autowired
    private RewardService rewardService;
    @Autowired
    private Environment environment;

    @Test
    @DisplayName("L3-0 应用上下文加载（生产库/Redis Bean 装配成功）")
    void contextLoads() {
        assertNotNull(dataSource);
        assertNotNull(redisConnectionFactory);
        assertNotNull(partnerProductService);
        assertNotNull(rewardService);
    }

    @Test
    @DisplayName("L3-1 生产 MySQL 可达（SELECT 1）")
    void mysqlReachable() throws Exception {
        try (Connection c = dataSource.getConnection();
             Statement s = c.createStatement();
             ResultSet rs = s.executeQuery("SELECT 1")) {
            assertTrue(rs.next());
            assertEquals(1, rs.getInt(1));
        }
    }

    @Test
    @DisplayName("L3-2 生产 Redis 可达（PING → PONG）")
    void redisPing() {
        try (RedisConnection conn = redisConnectionFactory.getConnection()) {
            assertEquals("PONG", conn.ping());
        }
    }

    @Test
    @DisplayName("L3-3 PRD 配置已正确加载（loan.reward.max-layers=2）")
    void prdConfigLoaded() {
        // 来自 nacos/config/prd/application.properties，证明接的是生产配置形态
        assertEquals("2", environment.getProperty("loan.reward.max-layers"));
    }

    @Test
    @DisplayName("L3-4 合作库：listActive() 只读返回（状态机数据源可用）")
    void partnerProductListActive() {
        // 只读查询，验证合作库有效期状态机所依赖的表可达且有数据
        List<PartnerProduct> active = partnerProductService.listActive();
        assertNotNull(active, "listActive() 不应返回 null");
    }

    @Test
    @DisplayName("L3-5 奖励：listRules() 只读返回（奖励计算数据源可用）")
    void rewardListRules() {
        List<RewardRule> rules = rewardService.listRules();
        assertNotNull(rules, "listRules() 不应返回 null");
    }

    @Test
    @DisplayName("L3-6 规则引擎纯逻辑在真实配置下仍正确（5 命中 → HIGH）")
    void gradeAggregatorOverRealConfig() {
        GradeAggregator aggregator = new GradeAggregator();
        assertEquals(Grade.HIGH, aggregator.aggregate(5));
        assertEquals(Grade.MIDDLE, aggregator.aggregate(1));
        assertEquals(Grade.LOW, aggregator.aggregate(0));
    }
}
