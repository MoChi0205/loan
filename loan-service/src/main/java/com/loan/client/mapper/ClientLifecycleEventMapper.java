package com.loan.client.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.loan.client.entity.ClientLifecycleEvent;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Insert;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Mapper
public interface ClientLifecycleEventMapper extends BaseMapper<ClientLifecycleEvent> {

    /** 依赖 uk_client_event_episode 原子去重；并发首跟时后到请求不会失败或重复计数。 */
    @Insert("INSERT IGNORE INTO t_client_lifecycle_event "
            + "(client_code,event_type,staff_code,episode_no,event_at,operator_staff_code) "
            + "VALUES (#{clientCode},'FIRST_FOLLOW',#{staffCode},#{episodeNo},#{eventAt},#{operatorStaffCode})")
    int insertFirstFollowIgnore(@Param("clientCode") String clientCode,
                                @Param("staffCode") String staffCode,
                                @Param("episodeNo") Integer episodeNo,
                                @Param("eventAt") LocalDateTime eventAt,
                                @Param("operatorStaffCode") String operatorStaffCode);

    @Select({"<script>",
            "SELECT a.client_code AS clientCode, a.episode_no AS episodeNo,",
            "TIMESTAMPDIFF(SECOND, a.event_at, MIN(f.event_at)) / 3600.0 AS hours",
            "FROM t_client_lifecycle_event a JOIN t_client_lifecycle_event f",
            "ON f.client_code = a.client_code AND f.episode_no = a.episode_no",
            "AND f.event_type = 'FIRST_FOLLOW' AND f.event_at &gt;= a.event_at",
            "WHERE a.event_type = 'OWNER_ASSIGNED' AND a.event_at &gt;= #{from} AND a.event_at &lt; #{to}",
            "<if test='staffCodes != null and staffCodes.size() &gt; 0'>AND a.staff_code IN <foreach collection='staffCodes' item='code' open='(' separator=',' close=')'>#{code}</foreach></if>",
            "GROUP BY a.client_code, a.episode_no, a.event_at</script>"})
    List<Map<String, Object>> firstFollowDurations(@Param("from") LocalDateTime from,
                                                   @Param("to") LocalDateTime to,
                                                   @Param("staffCodes") java.util.Collection<String> staffCodes);

    @Select({"<script>",
            "SELECT e.client_code AS clientCode, e.episode_no AS episodeNo, e.sea_level AS seaLevel,",
            "TIMESTAMPDIFF(SECOND, e.event_at, MIN(x.event_at)) / 3600.0 AS hours",
            "FROM t_client_lifecycle_event e JOIN t_client_lifecycle_event x",
            "ON x.client_code = e.client_code AND x.episode_no = e.episode_no",
            "AND x.event_type = 'EXIT_SEA' AND x.event_at &gt;= e.event_at",
            "WHERE e.event_type IN ('ENTER_COMPANY_SEA','ENTER_TEAM_SEA')",
            "AND e.event_at &gt;= #{from} AND e.event_at &lt; #{to}",
            "<if test='staffCodes != null and staffCodes.size() &gt; 0'>AND (x.staff_code IN <foreach collection='staffCodes' item='code' open='(' separator=',' close=')'>#{code}</foreach> OR e.staff_code IN <foreach collection='staffCodes' item='code' open='(' separator=',' close=')'>#{code}</foreach>)</if>",
            "GROUP BY e.client_code, e.episode_no, e.sea_level, e.event_at</script>"})
    List<Map<String, Object>> seaStayDurations(@Param("from") LocalDateTime from,
                                               @Param("to") LocalDateTime to,
                                               @Param("staffCodes") java.util.Collection<String> staffCodes);

    @Select({"<script>",
            "SELECT AVG(TIMESTAMPDIFF(SECOND, e.event_at, NOW()) / 3600.0) AS hours",
            "FROM t_client_lifecycle_event e JOIN t_client_profile cp ON cp.client_code = e.client_code",
            "WHERE e.event_type IN ('ENTER_COMPANY_SEA','ENTER_TEAM_SEA')",
            "AND cp.owner_staff_code IS NULL",
            "AND NOT EXISTS (SELECT 1 FROM t_client_lifecycle_event x WHERE x.client_code=e.client_code",
            " AND x.episode_no=e.episode_no AND x.event_type='EXIT_SEA' AND x.event_at &gt;= e.event_at)",
            "<if test=\"seaLevel != null and seaLevel != ''\">AND e.sea_level = #{seaLevel}</if>",
            "<if test=\"seaDeptCode != null and seaDeptCode != ''\">AND e.sea_dept_code = #{seaDeptCode}</if>",
            "</script>"})
    Map<String, Object> averageCurrentPoolAgeHours(@Param("seaLevel") String seaLevel,
                                                   @Param("seaDeptCode") String seaDeptCode);
}
