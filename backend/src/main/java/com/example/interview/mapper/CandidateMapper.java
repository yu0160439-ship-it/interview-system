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
     * 先按考生聚合一面评分，再在 WHERE 中筛选；二面评分不参与一面排序。
     */
    @Select("""
            <script>
            SELECT c.*, fs.avg_score
            FROM candidates c
            LEFT JOIN (
              SELECT candidate_id, ROUND(AVG(score), 2) AS avg_score
              FROM interviewer_scores
              WHERE round = 'first'
              GROUP BY candidate_id
            ) fs ON fs.candidate_id = c.id
            <where>
              <if test="keyword != null and keyword != ''">
                AND (c.name LIKE CONCAT('%', #{keyword}, '%')
                     OR c.class_name LIKE CONCAT('%', #{keyword}, '%')
                     OR c.phone LIKE CONCAT('%', #{keyword}, '%'))
              </if>
              <if test="groupName != null and groupName != ''">AND c.group_name = #{groupName}</if>
              <if test="status != null and status != ''">AND c.status = #{status}</if>
              <if test="excludeCompleted">AND c.status != 'first_completed'</if>
              <if test="minScore != null">AND fs.avg_score &gt;= #{minScore}</if>
              <if test="maxScore != null">AND fs.avg_score &lt;= #{maxScore}</if>
            </where>
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
                ORDER BY fs.avg_score ${sortOrderDesc ? 'DESC' : 'ASC'} NULLS LAST, c.global_number ASC
              </otherwise>
            </choose>
            <if test="limit != null">LIMIT #{limit} OFFSET #{offset}</if>
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
            @Param("limit") Integer limit,
            @Param("offset") int offset);

    /** 搜索总数与列表共用一面平均分的筛选口径。 */
    @Select("""
            <script>
            SELECT COUNT(*)
              FROM candidates c
              LEFT JOIN (
                SELECT candidate_id, ROUND(AVG(score), 2) AS avg_score
                FROM interviewer_scores
                WHERE round = 'first'
                GROUP BY candidate_id
              ) fs ON fs.candidate_id = c.id
              <where>
                <if test="keyword != null and keyword != ''">
                  AND (c.name LIKE CONCAT('%', #{keyword}, '%')
                       OR c.class_name LIKE CONCAT('%', #{keyword}, '%')
                       OR c.phone LIKE CONCAT('%', #{keyword}, '%'))
                </if>
                <if test="groupName != null and groupName != ''">AND c.group_name = #{groupName}</if>
                <if test="status != null and status != ''">AND c.status = #{status}</if>
                <if test="excludeCompleted">AND c.status != 'first_completed'</if>
                <if test="minScore != null">AND fs.avg_score &gt;= #{minScore}</if>
                <if test="maxScore != null">AND fs.avg_score &lt;= #{maxScore}</if>
              </where>
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
