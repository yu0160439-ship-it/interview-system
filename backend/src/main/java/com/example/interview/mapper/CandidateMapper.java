package com.example.interview.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.interview.entity.Candidate;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface CandidateMapper extends BaseMapper<Candidate> {

    /**
     * 候选搜索列表（含按平均分筛选与多种排序）。
     * avg_score 列会映射到 Candidate.averageScore（@TableField(exist=false)）。
     */
    @Select("""
            <script>
            SELECT c.*, (SELECT AVG(score) FROM interviewer_scores s WHERE s.candidate_id = c.id) AS avg_score
            FROM candidates c
            <where>
              <if test="keyword != null and keyword != ''">
                AND (c.name LIKE CONCAT('%', #{keyword}, '%')
                     OR c.class_name LIKE CONCAT('%', #{keyword}, '%')
                     OR c.phone LIKE CONCAT('%', #{keyword}, '%'))
              </if>
              <if test="groupName != null and groupName != ''">AND c.group_name = #{groupName}</if>
              <if test="status != null and status != ''">AND c.status = #{status}</if>
              <if test="excludeCompleted">AND c.status != 'first_completed'</if>
            </where>
            <if test="minScore != null or maxScore != null">
              HAVING
              <if test="minScore != null">avg_score &gt;= #{minScore}</if>
              <if test="minScore != null and maxScore != null"> AND </if>
              <if test="maxScore != null">avg_score &lt;= #{maxScore}</if>
            </if>
            <choose>
              <when test="sortBy == 'globalNumber'">
                ORDER BY
                  CASE
                    WHEN c.status IN ('waiting','interviewing','pending_approval') THEN 0
                    WHEN c.status = 'pre_registered' THEN 1
                    ELSE 2
                  END ASC,
                  c.global_number ${sortOrderDesc ? 'DESC' : 'ASC'},
                  c._created_at ASC
              </when>
              <when test="sortBy == 'createdAt'">
                ORDER BY c._created_at ${sortOrderDesc ? 'DESC' : 'ASC'}
              </when>
              <otherwise>
                ORDER BY avg_score ${sortOrderDesc ? 'DESC' : 'ASC'}, c.global_number ASC
              </otherwise>
            </choose>
            LIMIT #{limit} OFFSET #{offset}
            </script>
            """)
    List<Candidate> searchList(
            @Param("keyword") String keyword,
            @Param("groupName") String groupName,
            @Param("status") String status,
            @Param("excludeCompleted") boolean excludeCompleted,
            @Param("minScore") Double minScore,
            @Param("maxScore") Double maxScore,
            @Param("sortBy") String sortBy,
            @Param("sortOrderDesc") boolean sortOrderDesc,
            @Param("limit") int limit,
            @Param("offset") int offset);

    /** 搜索总数（含按平均分筛选时用子查询 HAVING）。 */
    @Select("""
            <script>
            SELECT COUNT(*) FROM (
              SELECT c.id, (SELECT AVG(score) FROM interviewer_scores s WHERE s.candidate_id = c.id) AS avg_score
              FROM candidates c
              <where>
                <if test="keyword != null and keyword != ''">
                  AND (c.name LIKE CONCAT('%', #{keyword}, '%')
                       OR c.class_name LIKE CONCAT('%', #{keyword}, '%')
                       OR c.phone LIKE CONCAT('%', #{keyword}, '%'))
                </if>
                <if test="groupName != null and groupName != ''">AND c.group_name = #{groupName}</if>
                <if test="status != null and status != ''">AND c.status = #{status}</if>
                <if test="excludeCompleted">AND c.status != 'first_completed'</if>
              </where>
              <if test="minScore != null or maxScore != null">
                HAVING
                <if test="minScore != null">avg_score &gt;= #{minScore}</if>
                <if test="minScore != null and maxScore != null"> AND </if>
                <if test="maxScore != null">avg_score &lt;= #{maxScore}</if>
              </if>
            ) t
            </script>
            """)
    long searchCount(
            @Param("keyword") String keyword,
            @Param("groupName") String groupName,
            @Param("status") String status,
            @Param("excludeCompleted") boolean excludeCompleted,
            @Param("minScore") Double minScore,
            @Param("maxScore") Double maxScore);
}
