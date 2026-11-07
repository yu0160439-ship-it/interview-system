package com.example.interview.service.support;

import com.example.interview.entity.Candidate;
import lombok.Data;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 内存状态：候场大屏显示模式、大屏缓存（8 秒 TTL，减少数据库读取）。
 * 重启后恢复默认。
 */
@Service
public class ScreenStateService {

    /** 候场大屏显示模式：first = 一面 A-D 组；second = 二面 A-D 组 */
    public static final String MODE_FIRST = "first";
    public static final String MODE_SECOND = "second";

    private static final long CACHE_TTL_MS = 8000;

    private volatile String displayMode = MODE_FIRST;

    private volatile Object screenCacheData;
    private volatile long screenCacheTimestamp;

    @Data
    public static class BigScreenData {
        private List<GroupItem> groups;
        private int totalSignedIn;
        private int totalInterviewing;
        private int totalWaiting;
        private int totalCompleted;
        private String mode;
    }

    @Data
    public static class GroupItem {
        private String groupName;
        private CurrentItem current;
        private int completedCount;
        private int waitingCount;
        private List<CurrentItem> waitingList;
    }

    @Data
    public static class CurrentItem {
        private Integer globalNumber;
        private Integer groupNumber;
        private String status;
    }

    @Data
    public static class GroupCount {
        private String groupName;
        private long total;
        private long waiting;
        private long interviewing;
        private long pendingApproval;
        private long completed;
        private long absent;
        private NextWaiting nextWaiting;
    }

    @Data
    public static class NextWaiting {
        private String id;
        private String name;
        private Integer globalNumber;
        private Integer groupNumber;
    }

    /** 大屏缓存载体：bigScreen 数据 + 全量 rows + 组统计 */
    @Data
    public static class ScreenCache {
        private BigScreenData bigScreen;
        private List<Candidate> allRows;
        private List<GroupCount> groupCounts;
    }

    public String getDisplayMode() {
        return displayMode;
    }

    public void setDisplayMode(String mode) {
        this.displayMode = mode;
        clearScreenCache();
    }

    public synchronized ScreenCache getCache() {
        if (screenCacheData != null && System.currentTimeMillis() - screenCacheTimestamp < CACHE_TTL_MS) {
            return (ScreenCache) screenCacheData;
        }
        return null;
    }

    public synchronized void putCache(ScreenCache cache) {
        this.screenCacheData = cache;
        this.screenCacheTimestamp = System.currentTimeMillis();
    }

    public synchronized void clearScreenCache() {
        this.screenCacheData = null;
        this.screenCacheTimestamp = 0;
    }
}
