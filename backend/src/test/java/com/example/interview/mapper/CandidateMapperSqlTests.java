package com.example.interview.mapper;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import org.apache.ibatis.mapping.BoundSql;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CandidateMapperSqlTests {
    private static MybatisConfiguration configuration;

    @BeforeAll
    static void configureMapper() {
        configuration = new MybatisConfiguration();
        configuration.addMapper(CandidateMapper.class);
    }

    @Test
    void exportCanUseTheSameFiltersWithoutPagination() {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("keyword", "Candidate");
        parameters.put("status", "first_completed");
        parameters.put("groupName", "A");
        parameters.put("minScore", 8.0);
        parameters.put("maxScore", 9.0);
        parameters.put("excludeCompleted", false);
        parameters.put("sortBy", "globalNumber");
        parameters.put("sortOrderDesc", false);
        parameters.put("limit", null);
        parameters.put("offset", 0);

        String sql = configuration.getMappedStatement(CandidateMapper.class.getName() + ".searchList")
                .getBoundSql(parameters).getSql().replaceAll("\\s+", " ");

        assertDoesNotThrow(() -> CCJSqlParserUtil.parse(sql));
        assertTrue(sql.contains("fs.avg_score >= ?") && sql.contains("fs.avg_score <= ?"));
        assertFalse(sql.contains("LIMIT"));
        assertFalse(sql.contains("OFFSET"));
    }

    @ParameterizedTest
    @CsvSource({"8.0,,score", ",9.0,score", "8.0,9.0,score", ",,globalNumber", ",,createdAt"})
    void listAndCountUseValidParameterizedFirstRoundFilters(Double min, Double max, String sortBy) {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("keyword", "' OR 1=1 --");
        parameters.put("groupName", "A");
        parameters.put("status", "first_completed");
        parameters.put("excludeCompleted", false);
        parameters.put("minScore", min);
        parameters.put("maxScore", max);
        parameters.put("sortBy", sortBy);
        parameters.put("sortOrderDesc", true);
        parameters.put("limit", 20);
        parameters.put("offset", 0);

        for (String method : new String[]{"searchList", "searchCount"}) {
            BoundSql boundSql = configuration.getMappedStatement(CandidateMapper.class.getName() + "." + method)
                    .getBoundSql(parameters);
            String sql = boundSql.getSql().replaceAll("\\s+", " ");
            assertDoesNotThrow(() -> CCJSqlParserUtil.parse(sql));
            assertFalse(sql.contains("HAVING"), "PostgreSQL cannot filter by a SELECT alias in HAVING");
            assertTrue(sql.contains("WHERE round = 'first'"), "Second-round scores must not affect list ranking or count");
            assertFalse(sql.contains(parameters.get("keyword").toString()), "Search values must stay in bound parameters");
            assertEquals(min != null, sql.contains("fs.avg_score >= ?"));
            assertEquals(max != null, sql.contains("fs.avg_score <= ?"));
            assertEquals(5 + (min != null ? 1 : 0) + (max != null ? 1 : 0) + (method.equals("searchList") ? 2 : 0),
                    boundSql.getParameterMappings().size());
            if (method.equals("searchList") && sortBy.equals("score")) {
                assertTrue(sql.contains("ORDER BY fs.avg_score DESC NULLS LAST"), "Unscored candidates must not lead a descending score list");
            }
        }
    }
}
